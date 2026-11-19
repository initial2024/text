package com.omnigate.integrated;
public final class RelayResourceBoundsTest {
    static int n;
    static void ok(boolean x){n++;if(!x)throw new AssertionError("check "+n);}
    public static void main(String[] args){
        RelayQueuePacer q=new RelayQueuePacer();
        ok(q.observe(0,100,true)==RelayQueuePacer.Decision.READY);
        ok(q.observe(RelayQueuePacer.HIGH_WATER_BYTES-1,101,true)==RelayQueuePacer.Decision.READY);
        ok(q.observe(RelayQueuePacer.HIGH_WATER_BYTES,102,true)==RelayQueuePacer.Decision.WAIT);
        ok(q.observe(RelayQueuePacer.LOW_WATER_BYTES+1,300,true)==RelayQueuePacer.Decision.WAIT);
        ok(q.observe(RelayQueuePacer.LOW_WATER_BYTES,301,true)==RelayQueuePacer.Decision.READY);
        ok(q.observe(RelayQueuePacer.HIGH_WATER_BYTES,1000,true)==RelayQueuePacer.Decision.WAIT);
        ok(q.observe(RelayQueuePacer.HIGH_WATER_BYTES,31000,true)==RelayQueuePacer.Decision.STALLED);
        ok(q.observe(0,31001,true)==RelayQueuePacer.Decision.STALLED);
        RelayQueuePacer stale=new RelayQueuePacer();
        ok(stale.observe(0,10,false)==RelayQueuePacer.Decision.STALE);
        ok(stale.observe(0,11,true)==RelayQueuePacer.Decision.STALLED);
        boolean denied=false;
        try{new RelayQueuePacer().observe(-1,1,true);}catch(IllegalArgumentException e){denied=true;}
        ok(denied);
        RelayRequestLimiter limit=new RelayRequestLimiter(6);
        for(int i=0;i<6;i++)ok(limit.tryEnter());
        ok(!limit.tryEnter());
        ok(limit.available()==0);
        limit.leave();
        ok(limit.tryEnter());
        ok(!limit.tryEnter());
        for(int i=0;i<6;i++)limit.leave();
        ok(limit.available()==6);
        ok(limit.tryEnter());
        limit.leave();
        System.out.println("OGRELAY13_HYSTERESIS_QUEUE_BOUNDED=PASS");
        System.out.println("OGRELAY13_CONCURRENT_REQUEST_LIMIT=PASS");
        System.out.println("OGRELAY13_STALL_AND_STALE_FAIL_CLOSED=PASS");
        System.out.println("OGRELAY13_ASSERTIONS="+n);
    }
}
