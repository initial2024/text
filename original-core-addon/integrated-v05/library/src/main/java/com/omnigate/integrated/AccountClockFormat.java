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
    private static final Set<String> IANA_ZONES=ZoneId.getAvailableZoneIds();
    private static final DateTimeFormatter CLOCK=
        DateTimeFormatter.ofPattern("yyyy-MM-dd EEE HH:mm:ss",Locale.SIMPLIFIED_CHINESE);
    private static final DateTimeFormatter SHORT_CLOCK=
        DateTimeFormatter.ofPattern("HH:mm:ss",Locale.ROOT);
    private AccountClockFormat(){}

    static String validate(String raw){
        if(SYSTEM.equals(raw))return SYSTEM;
        if(raw==null||raw.length()<3||raw.length()>64||
            !IANA_ZONES.contains(raw))
            throw new IllegalArgumentException("clock_invalid_iana_timezone");
        return raw;
    }
    static ZoneId zone(String preference){
        return SYSTEM.equals(validate(preference))?
            ZoneId.systemDefault():ZoneId.of(preference);
    }
    private static String utcOffset(ZonedDateTime time){
        int offset=time.getOffset().getTotalSeconds();
        int abs=Math.abs(offset);
        return String.format(Locale.ROOT,"UTC%s%02d:%02d",
            offset>=0?"+":"-",abs/3600,(abs%3600)/60);
    }
    static String display(String preference,long epochMillis){
        ZoneId id=zone(preference);
        ZonedDateTime time=Instant.ofEpochMilli(epochMillis).atZone(id);
        return time.format(CLOCK)+" · "+utcOffset(time)+" · "+id.getId()+
            (SYSTEM.equals(preference)?"（跟随系统）":"");
    }
    /** Bounded overlay label, with the full IANA zone kept in accessibility
     * text and native settings. Timezone remains DISPLAY-ONLY.
     */
    static String compact(String preference,long epochMillis){
        String choice=validate(preference);
        ZoneId id=zone(choice);
        ZonedDateTime time=Instant.ofEpochMilli(epochMillis).atZone(id);
        String label=SYSTEM.equals(choice)?"系统":id.getId();
        int slash=label.lastIndexOf('/');
        if(slash>=0)label=label.substring(slash+1);
        label=label.replace('_',' ');
        if(label.length()>15)label=label.substring(0,15);
        return time.format(SHORT_CLOCK)+" · "+utcOffset(time)+" · "+label;
    }
}
