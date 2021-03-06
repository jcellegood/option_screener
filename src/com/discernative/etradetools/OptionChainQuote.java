/*
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */
package com.discernative.etradetools;
import java.util.Calendar;
import java.text.SimpleDateFormat;

abstract class OptionChainQuote {
    protected String symbol;
    protected Calendar date;
    protected Double strike;

    protected Integer openInterest = 0;
    protected Double  bid = 0.0;
    protected Double  ask = 0.0;
    protected Integer bidSize = 0;
    protected Integer askSize = 0;
    protected Double  lastTrade = 0.0;
    protected SimpleDateFormat dateFormatter = new SimpleDateFormat ( "yyyy-MM-dd" );
    
    // Accessor methods
    public String   getSymbol () { return this.symbol; };
    public Calendar getDate () { return this.date; };
    public Double   getStrikePrice () { return this.strike; };

    // accessor methods
    public Integer getOpenInterest() { return this.openInterest; };
    public Double  getBid() { return this.bid; };
    public Double  getAsk() { return this.ask; };
    public Integer getBidSize() { return this.bidSize; };
    public Integer getAskSize() { return this.askSize; };
    public Double  getLastTrade() { return this.lastTrade; };

    public void setOpenInterest (Integer i) { this.openInterest = i; };
    public void setBid ( Double bid ) { this.bid = bid; };
    public void setAsk ( Double ask ) { this.ask = ask; };
    public void setBidSize ( Integer i ) { this.bidSize = i; };
    public void setAskSize ( Integer i ) { this.askSize = i; };
    public void setLastTrade ( Double price ) { this.lastTrade = price; };

    public String toString() {
            return String.format( "%s, %s, %f %s (bid: %f, ask %f)", 
            this.symbol, 
            this.getDateString(), 
            this.strike, 
            getType(), 
            this.bid, 
            this.ask );
    }

    public String getDateString() {
        return dateFormatter.format( this.date.getTime() ); 
    }
    
    public String getType() {
        return "unknown";
    }
}
