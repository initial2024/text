package com.omnigate.integrated;

import java.time.Instant;
import java.util.TimeZone;

/** Pure Java safety tests: per-account displayed clock uses IANA DST. */
public final class AccountClockFormatTest {
    private static int passed=0;
    private static void pass(boolean ok){
        if(!ok)throw new AssertionError("clock case "+passed);
        passed++;
    }
    private static void rejects(String s){
        try{AccountClockFormat.validate(s);
            throw new AssertionError("invalid zone accepted: "+s);
        }catch(IllegalArgumentException expected){passed++;}
    }
    public static void main(String[] args){
        pass("UTC".equals(AccountClockFormat.validate("UTC")));
        pass("SYSTEM".equals(AccountClockFormat.validate("SYSTEM")));
        pass("America/New_York".equals(
            AccountClockFormat.validate("America/New_York")));
        rejects(null);rejects("");rejects("America/Fake_City");
        rejects("<script>");rejects("UTC+25:00");
        long jan=Instant.parse("2026-01-15T00:00:00Z").toEpochMilli();
        pass(AccountClockFormat.display("Asia/Tokyo",jan).contains("09:00:00"));
        pass(AccountClockFormat.display("UTC",jan).contains("00:00:00"));
        pass(AccountClockFormat.display("America/New_York",jan).contains("UTC-05:00"));
        long before=Instant.parse("2026-03-08T06:59:00Z").toEpochMilli();
        long after=Instant.parse("2026-03-08T07:01:00Z").toEpochMilli();
        pass(AccountClockFormat.display("America/New_York",before).contains("UTC-05:00"));
        pass(AccountClockFormat.display("America/New_York",after).contains("UTC-04:00"));
        TimeZone previous=TimeZone.getDefault();
        try{
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            pass(AccountClockFormat.display("SYSTEM",jan).contains("UTC+09:00"));
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            pass(AccountClockFormat.display("SYSTEM",jan).contains("UTC-08:00"));
        }finally{TimeZone.setDefault(previous);}
        pass(!AccountClockFormat.display("UTC",jan).contains("Japan"));
        System.out.println("OGTIME01_IANA_CLOCK_DAILY_DST_AND_INVALID_"+passed+"_OF_"+passed+"=PASS");
    }
}
