package com.omnigate.integrated;

/**
 * No network observation: a bounded rolling 60-minute counter for explicit
 * user completion acknowledgments. Stored as millisecond epochs, not cookies,
 * user prompts, tokens or platform authorization state.
 */
final class SlidingHourAcknowledgements {
    static final long WINDOW_MS=3_600_000L;
    static final int MAX_EVENTS=512;
    private SlidingHourAcknowledgements(){}
    static int count(String prior,long now){
        return clean(prior,now,null);
    }
    static String append(String prior,long now){
        StringBuilder out=new StringBuilder();
        int count=clean(prior,now,out);
        if(count>=MAX_EVENTS)throw new IllegalStateException("hour_counter_capacity");
        if(out.length()!=0)out.append(',');
        out.append(now);
        return out.toString();
    }
    private static int clean(String prior,long now,StringBuilder out){
        if(now<0||prior==null||prior.length()>16000)
            throw new IllegalArgumentException("hour_counter_corrupt");
        if(prior.isEmpty())return 0;
        int n=0;
        String[] entries=prior.split(",",-1);
        if(entries.length>MAX_EVENTS)throw new IllegalStateException("hour_counter_capacity");
        for(String item:entries){
            if(item.isEmpty())throw new IllegalArgumentException("hour_counter_corrupt");
            long at;
            try{at=Long.parseLong(item);}
            catch(NumberFormatException e){throw new IllegalArgumentException("hour_counter_corrupt",e);}
            // Device clock rollback means unknown usage; fail closed.
            if(at<0||at>now)throw new IllegalStateException("clock_rollback");
            if(now-at>=WINDOW_MS)continue;
            n++;
            if(out!=null){
                if(out.length()!=0)out.append(',');
                out.append(at);
            }
        }
        return n;
    }
}
