/*
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */
package com.discernative.etradetools;
import com.etrade.etws.oauth.sdk.client.IOAuthClient;
import com.etrade.etws.oauth.sdk.client.OAuthClientImpl;
import com.etrade.etws.oauth.sdk.common.Token;
import com.etrade.etws.sdk.client.ClientRequest;
import com.etrade.etws.sdk.client.Environment;
import com.etrade.etws.sdk.common.ETWSException;

import com.etrade.etws.market.DetailFlag;
import com.etrade.etws.market.QuoteData;
import com.etrade.etws.sdk.client.MarketClient;
import com.etrade.etws.market.QuoteResponse;

import com.etrade.etws.market.OptionChainPair;
import com.etrade.etws.market.OptionChainRequest;
import com.etrade.etws.market.OptionChainResponse;
import com.etrade.etws.market.CallOptionChain;
import com.etrade.etws.market.PutOptionChain;
import com.etrade.etws.market.ExpirationDate;
import com.etrade.etws.market.OptionExpireDateGetRequest;
import com.etrade.etws.market.OptionExpireDateGetResponse;
import java.io.IOException;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import java.util.Date;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ObjectInputStream;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.regex.*;
import java.util.Properties;

class EtradeTools {

    public static final int LIVE = 1;
    public static final int SANDBOX = 0;
    public static final int ITM = 1;
    public static final int OTM = 2;
    public static final int ALL = ITM | OTM;

    public static int MAX_BATCH_SIZE = 25;

    public static AuthToken getAuthToken ( String key, String secret, int env ) throws IOException, ETWSException {

        IOAuthClient authClient = OAuthClientImpl.getInstance();

        // Create the client request
        ClientRequest clientRequest = new ClientRequest();

        if ( env == LIVE ) {
            clientRequest.setEnv( Environment.LIVE );
        } else {
            clientRequest.setEnv( Environment.SANDBOX );
        }

        clientRequest.setConsumerKey( key );
        clientRequest.setConsumerSecret( secret );

        Token requestToken = authClient.getRequestToken ( clientRequest );

        clientRequest.setToken( requestToken.getToken() );
        clientRequest.setTokenSecret( requestToken.getSecret() );

        // Get the verifier code
        String authorizeURL = authClient.getAuthorizeUrl(clientRequest);
        String verificationCode = getVerificationCode ( authorizeURL );
        clientRequest.setVerifierCode(verificationCode);

        // get an access token
        Token accessToken = authClient.getAccessToken ( clientRequest );

        AuthToken a = new AuthToken ( key, secret, accessToken.getToken(), accessToken.getSecret(), env );
        return a;

    }

    public static AuthToken getAuthToken ( String filename ) {
        AuthToken authToken = null;
        try {
            FileInputStream fileIn = new FileInputStream( filename );
            ObjectInputStream in = new ObjectInputStream(fileIn);
            authToken = (AuthToken) in.readObject();
            in.close();
            fileIn.close();
        } catch (IOException i) {
            i.printStackTrace();
            System.exit(1);
        } catch (ClassNotFoundException c) {
            c.printStackTrace();
            System.exit(1);
        }

        return authToken;
    }

    public static ClientRequest getAccessRequest ( AuthToken authToken ) {
        // Create an access request
        ClientRequest accessRequest = new ClientRequest();
        if ( authToken.getEnv() == LIVE ) {
            accessRequest.setEnv( Environment.LIVE );
        } else {
            accessRequest.setEnv( Environment.SANDBOX );
        }

        // Setup the access request with the access token bits 
        accessRequest.setConsumerKey( authToken.getKey() );
        accessRequest.setConsumerSecret( authToken.getSecret() );
        accessRequest.setToken( authToken.getAccessToken() );
        accessRequest.setTokenSecret( authToken.getAccessSecret() );

        return accessRequest;
    }

    public static String getVerificationCode ( String url ) {
        Scanner inputScanner = new Scanner ( System.in );

        System.out.println ( "\n\n\nEnter the following URL into your browser, follow the prompts and copy the verification code\n\n" );
        System.out.println ( "URL = '" + url + "'" );

        System.out.print ( "\n\nEnter verification code: " );
        
        String verificationCode = inputScanner.next();
    
        inputScanner.close();
        return verificationCode;
    }

    public static QuoteData getQuote ( AuthToken authToken, String symbol ) { 
        ArrayList<String> symbols = new ArrayList<String>();
        symbols.add(symbol);
        return getQuote ( authToken, symbols ).get(0);
    }

    public static List<QuoteData> getQuote ( AuthToken authToken, ArrayList<String> symbols ) { 
        ClientRequest clientRequest = getAccessRequest ( authToken );
        MarketClient marketClient = new MarketClient(clientRequest);

        QuoteResponse quoteResponse = new QuoteResponse();
        ArrayList<QuoteData> allResponses = new ArrayList<QuoteData>();

        int count = 0;

        int batchSize = MAX_BATCH_SIZE;

        if ( authToken.getEnv() == SANDBOX ) {
            batchSize = 1;
        }

        ArrayList<String> batch = new ArrayList<String>();
        while ( count < symbols.size() ) {

            batch.add ( symbols.get ( count ) );
            count++;

            if ( count % batchSize == 0 || count == symbols.size() ) {
                try {
                    System.out.println ( "Fetching batch of size " + batch.size() );
                    quoteResponse = marketClient.getQuote( batch, new Boolean(true), DetailFlag.ALL);
                } catch (IOException ex) {
                    System.out.println ( "caught exception: " + ex );
                    ex.printStackTrace();
                    return allResponses;
                } catch (ETWSException ex) {
                    System.out.println ( "caught exception: " + ex );
                    ex.printStackTrace();
                    return allResponses;
                }

                // Now clear out the batch for the next run
                batch.clear();

                for ( QuoteData q : quoteResponse.getQuoteData() ) {
                    allResponses.add ( q );
                }
            }
        }

        return allResponses;
    }
    
    
    public static ArrayList<Calendar> getOptionExpirationDates ( AuthToken authToken, String symbol ) {
        ClientRequest accessRequest = getAccessRequest ( authToken );
        MarketClient marketClient = new MarketClient( accessRequest );

        OptionExpireDateGetRequest req = new OptionExpireDateGetRequest();
        req.setUnderlier( symbol );
        OptionExpireDateGetResponse optionExpireResponse = new OptionExpireDateGetResponse();
        try {
            optionExpireResponse = marketClient.getExpiryDates(req);
        } catch (IOException ex) {
            System.out.println ( "caught exception: " + ex );
            ex.printStackTrace();
        } catch (ETWSException ex) {
            System.out.println ( "caught exception: " + ex );
            ex.printStackTrace();
        }

        ArrayList<Calendar> dateList = new ArrayList<Calendar>();
        for ( ExpirationDate eDate : optionExpireResponse.getExpireDates() ) {
            Integer day = eDate.getDay();
            Integer year = eDate.getYear();
            Integer month = new Integer ( eDate.getMonth() );

            Calendar c = Calendar.getInstance();
            c.set ( year, month -1, day, 0, 0, 0 );
            dateList.add ( c );
         }
        return dateList;
    }
    
    public static ArrayList<OptionChainQuote> getOptionChainQuote ( AuthToken authToken, String symbol, Calendar date ) {
        Double price = 0.0;
        int scope = EtradeTools.ALL;
        return getOptionChainQuote ( authToken, symbol, date, "CALLPUT", price, scope );
    }
    
    public static ArrayList<OptionChainQuote> getCallOptionChainQuote ( AuthToken authToken, String symbol, Calendar date ) {
        Double price = 0.0;
        int scope = EtradeTools.ALL;
        return getOptionChainQuote ( authToken, symbol, date, "CALL", price, scope );
    }
    
    public static ArrayList<OptionChainQuote> getPutOptionChainQuote ( AuthToken authToken, String symbol, Calendar date ) {
        Double price = 0.0;
        int scope = EtradeTools.ALL;
        return getOptionChainQuote ( authToken, symbol, date, "PUT", price, scope );
    }
    
    public static ArrayList<OptionChainQuote> getOptionChainQuote ( AuthToken authToken, String symbol, Calendar date, Double price, int scope ) {
        return getOptionChainQuote ( authToken, symbol, date, "CALLPUT", price, scope );
    }
    
    public static ArrayList<OptionChainQuote> getCallOptionChainQuote ( AuthToken authToken, String symbol, Calendar date, Double price, int scope ) {
        return getOptionChainQuote ( authToken, symbol, date, "CALL", price, scope );
    }
    
    public static ArrayList<OptionChainQuote> getPutOptionChainQuote ( AuthToken authToken, String symbol, Calendar date, Double price, int scope ) {
        return getOptionChainQuote ( authToken, symbol, date, "PUT", price, scope );
    }
    
    public static ArrayList<OptionChainQuote> getOptionChainQuote ( AuthToken authToken, String symbol, Calendar date, String optionType, Double price, int scope ) {
        ClientRequest accessRequest = getAccessRequest ( authToken );
        MarketClient marketClient = new MarketClient( accessRequest );
        ArrayList<OptionChainQuote> chain = new ArrayList<OptionChainQuote>();

        String month = new Integer( date.get ( Calendar.MONTH ) + 1 ).toString();
        String year = new Integer( date.get ( Calendar.YEAR ) ).toString();

        OptionChainRequest ocReq = new OptionChainRequest();

        if ( authToken.getEnv() == SANDBOX ) {
            // If on sandbox use current year
            year = "2018";
        }

        ocReq.setExpirationMonth( month );
        ocReq.setExpirationYear( year );
        ocReq.setChainType(optionType);
        ocReq.setSkipAdjusted("FALSE");
        ocReq.setUnderlier( symbol );

        System.out.println ( String.format( "Fetching option chain for %s (year=%s/month=%s)", symbol, year, month ) );

        OptionChainResponse optionChainResponse = new OptionChainResponse();
        try {
            optionChainResponse = marketClient.getOptionChain( ocReq );
        } catch (IOException ex) {
            System.out.println ( "caught exception: " + ex );
            ex.printStackTrace();
            return chain;
        } catch (ETWSException ex) {
            System.out.println ( "caught exception in getOptionChainQuote: " + ex );
            ex.printStackTrace();
            return chain;
        }

        ArrayList<String> symbolBatch = new ArrayList<String>();

        for( OptionChainPair optionPair : optionChainResponse.getOptionPairs() ) {
            if ( optionPair.getCallCount() > 0 ) {
                // Batch the call options
                for ( CallOptionChain callChain : optionPair.getCall() ) {
                    BigDecimal strike = callChain.getStrikePrice();
                    ExpirationDate expDate = callChain.getExpireDate();
                    
                    Integer theDay = expDate.getDay(); 
                    Integer theMonth = new Integer ( expDate.getMonth() ); 
                    Integer theYear = expDate.getYear(); 

                    // Fetch the call option quote
                    //          underlier:year:month:day:optiontype:strikePrice
                    String chainSymbol = new String ( String.format ( "%s:%d:%d:%d:%s:%f", symbol, theYear, theMonth, theDay, "CALL", strike ) );
    
                    // See if we want to fetch the call quote here
                    if ( strike.doubleValue() < price ) {
                        // In the money call
                        if ( ( scope & ITM ) == 0 ) {
                            System.out.println( String.format( "skipping %s, it's in the money (price=%.2f)", chainSymbol, price ) );
                            continue;
                        }
                    } else {
                        // Out of the money call
                        if ( ( scope & OTM ) == 0 ) {
                            System.out.println( String.format( "skipping %s, it's out of the money (price=%.2f)", chainSymbol, price ) );
                            continue;
                        }
                    }
                    symbolBatch.add ( chainSymbol );
                }
            }

            if ( optionPair.getPutCount() > 0 ) {
                
                // Process the put options
                for ( PutOptionChain putChain : optionPair.getPut() ) {
                    BigDecimal strike = putChain.getStrikePrice();
                    ExpirationDate expDate = putChain.getExpireDate();
                    
                    Integer theDay = expDate.getDay(); 
                    Integer theMonth = new Integer ( expDate.getMonth() ); 
                    Integer theYear = expDate.getYear(); 
                    // Fetch the put option quote
                    //          underlier:year:month:day:optiontype:strikePrice
                    String chainSymbol = new String ( String.format ( "%s:%d:%d:%d:%s:%f", symbol, theYear, theMonth, theDay, "PUT", strike ) );

                    // See if we want to fetch the put quote here
                    if ( strike.doubleValue() > price ) {
                        // In the money put
                        if ( ( scope & ITM ) == 0 ) {
                            System.out.println( String.format( "skipping %s, it's in the money (price=%.2f)", chainSymbol, price ) );
                            continue;
                        }
                    } else {
                        // Out of the money put
                        if ( ( scope & OTM ) == 0 ) {
                            System.out.println( String.format( "skipping %s, it's out of the money (price=%.2f)", chainSymbol, price ) );
                            continue;
                        }
                    }

                    symbolBatch.add ( chainSymbol );
                }
            }
        }

        Pattern regexPattern = Pattern.compile("\\$(\\d\\S*) (Call|Put)");

        for ( QuoteData quoteData : getQuote ( authToken, symbolBatch ) ) {

            if ( quoteData == null ) {
                continue;
            }
            if ( quoteData.getAll() == null ) {
                continue;
            }
            String symbolDesc = quoteData.getAll().getSymbolDesc();
            String underlier = quoteData.getProduct().getSymbol();
            Double strike = new Double ( 0.0 );
            
            String type;

            // GOOG Apr 16 '11 $350 Put
            Matcher match = regexPattern.matcher(symbolDesc);
            if ( match.find() ) {
                strike = new Double ( match.group(1) );
                type = match.group(2);
            } else {
                continue;
            }

            OptionChainQuote quote;
            if ( type.equals ( "Call" ) ) {
                quote = new CallOptionQuote ( underlier, date, strike.doubleValue() );
            } else {
                quote = new PutOptionQuote ( underlier, date, strike.doubleValue() );
            }

            quote.setBid ( quoteData.getAll().getBid() );
            quote.setAsk ( quoteData.getAll().getAsk() );

            quote.setBidSize ( (int) quoteData.getAll().getBidSize() );
            quote.setAskSize ( (int) quoteData.getAll().getAskSize() );

            quote.setLastTrade ( quoteData.getAll().getLastTrade() );
            quote.setOpenInterest ( (int) quoteData.getAll().getOpenInterest() );

            chain.add ( quote );
        }

        return chain;
    }

    public static Properties getProperties ( String filename ) {
        Properties props = new Properties();
        FileInputStream propInputStream = null;

        try {
            propInputStream = new FileInputStream ( filename );
            props = new Properties();
            props.load ( propInputStream );
        } catch ( Exception e ) {
            e.printStackTrace();
        } finally {
            try { 
                 propInputStream.close();
            } catch ( Exception e ) {
                e.printStackTrace();
            }
        }

        return props;
    }

    public static ArrayList<StockQuote> getStockQuotes ( AuthToken authToken, ArrayList<String> symbols ) {
        ArrayList<StockQuote> quoteList = new ArrayList<StockQuote>();

        // Example date from getExDivDate(): 11/17/2009
        Pattern regexPattern = Pattern.compile("^(\\d\\d)/(\\d\\d)/(\\d\\d\\d\\d)$");
        for ( QuoteData quoteData : getQuote ( authToken, symbols ) ) {

            String symbol = quoteData.getProduct().getSymbol();
            
            if ( quoteData.getAll() == null ) {
                continue;
            }
            Double lastTrade = quoteData.getAll().getLastTrade();
            StockQuote q = new StockQuote ( symbol, lastTrade );

            q.setAnnualDividend ( quoteData.getAll().getAnnualDividend() );
            q.setDividend ( quoteData.getAll().getDividend() );
            q.setEPS ( quoteData.getAll().getEps() );
            q.setForwardEarnings ( quoteData.getAll().getEstEarnings() );
            q.setHigh52 ( quoteData.getAll().getHigh52() );
            q.setLow52 ( quoteData.getAll().getLow52() );

            String exDateString = quoteData.getAll().getExDivDate();

            Matcher match = regexPattern.matcher( exDateString );
            if ( match.find() ) {
                Integer month = new Integer ( match.group(1) );
                Integer day = new Integer ( match.group(2) );
                Integer year = new Integer ( match.group(3) );
                Calendar exDate = Calendar.getInstance();
                
                exDate.set ( year, month -1, day, 0, 0, 0 );
                q.setExDividendDate ( exDate );
            } else {
                q.setExDividendDate ( null );
            }

          
            quoteList.add ( q );
        }

        return quoteList;
    }

    public static ArrayList<String> readSymbols ( String filename ) { 
        ArrayList<String> symbolList = new ArrayList<String>();
    
        try {
            BufferedReader fileReader = new BufferedReader ( new FileReader ( filename ) );
    
            String line;
            while ( ( line = fileReader.readLine() ) != null ) {
                symbolList.add ( line );
            }
            fileReader.close();
        } catch ( Exception e ) {
            e.printStackTrace();
            System.exit ( 1 );
        }
    
        return symbolList;
    }

    public static void writeFile ( String formatString, ArrayList<String> csv ) {
        SimpleDateFormat df = new SimpleDateFormat ( "yyyy-MM-dd-HH-mm" );
        String dateString = df.format( new Date() );
        
        String outputFile = String.format( formatString, dateString );
        if ( outputFile != null ) {
            BufferedWriter w = null;
            try {
                w = new BufferedWriter ( new FileWriter ( outputFile ) );    
                
                for ( String line : csv ) {
                    w.write( line  );
                    w.newLine();
                }
                w.close();
            } catch ( IOException e ) {
                e.printStackTrace();
                System.exit( 1 );
            }
           
            
           
        }
    }

    static final long DAY_IN_SECONDS = 60 * 60 * 24;
    static final long DAY_IN_MILLIS = DAY_IN_SECONDS * 1000;

    public static String formatDate ( Calendar date ) {
        return ITMScreener.dateFormatter.format( date.getTime() );
    }
}
