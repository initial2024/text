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
    // The account virtual clock changes local presentation only, NEVER
    // Android's wall clock or Gecko JavaScript Date/Intl/timers.
    static final long MAX_VIRTUAL_SHIFT_MS=10L*366L*24*60*60*1000;
    static long validateVirtualShift(long deltaMs){
        if(deltaMs==Long.MIN_VALUE||
            deltaMs < -MAX_VIRTUAL_SHIFT_MS ||
            deltaMs > MAX_VIRTUAL_SHIFT_MS)
            throw new IllegalArgumentException("virtual_shift_out_of_bounds");
        return deltaMs;
    }
    static long simulatedNow(long realNowMs,long deltaMs){
        return Math.addExact(realNowMs,validateVirtualShift(deltaMs));
    }
    static long shiftToLocalTime(String iana,int year,int month,int day,
            int hour,int minute,long realNowMs){
        ZoneId zone=zone(iana);
        java.time.LocalDateTime requested=java.time.LocalDateTime.of(
            year,month,day,hour,minute);
        // Refuse nonexistent wall times during a DST spring-forward gap.
        java.time.ZonedDateTime selected=requested.atZone(zone);
        if(!selected.toLocalDateTime().equals(requested))
            throw new IllegalArgumentException("virtual_dst_gap");
        long target=selected.toInstant().toEpochMilli();
        return validateVirtualShift(Math.subtractExact(target,realNowMs));
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
