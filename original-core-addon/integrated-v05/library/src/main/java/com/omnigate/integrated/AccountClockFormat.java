package com.omnigate.integrated;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/** Native-display clock only: NEVER changes Gecko, site JS, system timezone,
 * network requests or browser fingerprinting behavior.
 * Android API 26+ supports java.time and IANA timezone rules.
 */
final class AccountClockFormat {
    static final String SYSTEM="SYSTEM";
    private static final DateTimeFormatter CLOCK=
        DateTimeFormatter.ofPattern("MM-dd EEE HH:mm:ss",Locale.SIMPLIFIED_CHINESE);
    private AccountClockFormat(){}

    static String validate(String raw){
        if(SYSTEM.equals(raw))return SYSTEM;
        if(raw==null||raw.length()<3||raw.length()>64||
            !ZoneId.getAvailableZoneIds().contains(raw))
            throw new IllegalArgumentException("clock_invalid_iana_timezone");
        return raw;
    }
    static ZoneId zone(String preference){
        return SYSTEM.equals(validate(preference))?
            ZoneId.systemDefault():ZoneId.of(preference);
    }
    static String display(String preference,long epochMillis){
        ZoneId id=zone(preference);
        ZonedDateTime time=Instant.ofEpochMilli(epochMillis).atZone(id);
        int offset=time.getOffset().getTotalSeconds();
        int abs=Math.abs(offset);
        String utc=String.format(Locale.ROOT,"UTC%s%02d:%02d",
            offset>=0?"+":"-",abs/3600,(abs%3600)/60);
        return time.format(CLOCK)+" · "+utc+" · "+id.getId()+
            (SYSTEM.equals(preference)?"（跟随系统）":"");
    }
}
