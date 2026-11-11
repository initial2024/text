package com.omnigate.integrated;

public final class LocalApiHourContractTest {
    static void expect(boolean truth){if(!truth)throw new AssertionError();}
    public static void main(String[] args){
        long now=1_800_000_000_000L;
        String events="";
        for(int i=0;i<48;i++)events=SlidingHourAcknowledgements.append(events,now+i*1000L);
        expect(SlidingHourAcknowledgements.count(events,now+47_000L)==48);
        expect(SlidingHourAcknowledgements.retryAfterSeconds(events,now+47_000L)==3553);
        expect(SlidingHourAcknowledgements.count(events,now+3_600_000L)==47);
        expect(SlidingHourAcknowledgements.count(events,now+3_647_000L)==0);
        System.out.println("OGAPIHOUR01_ROLLING_60MIN_RESERVATION_BOUNDARIES=PASS");
    }
}
