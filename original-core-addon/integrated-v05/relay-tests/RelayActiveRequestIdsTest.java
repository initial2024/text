package com.omnigate.integrated;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
public final class RelayActiveRequestIdsTest {
    private static int checks;
    static void ok(boolean v){checks++;if(!v)throw new AssertionError("request id guard "+checks);}
    public static void main(String[] args)throws Exception{
        RelayActiveRequestIds a=new RelayActiveRequestIds();
        ok(!a.reserve(""));ok(!a.reserve(null));ok(!a.reserve("x".repeat(257)));
        ok(a.reserve("R1"));ok(!a.reserve("R1"));ok(a.count()==1);
        // A reconnect cannot replay an id while its original stream drains.
        ok(!a.reserve("R1"));
        ok(a.reserve("R2"));ok(a.count()==2);
        a.release("R1");
        ok(a.reserve("R1"));ok(a.count()==2);
        a.release("R1");a.release("R2");ok(a.count()==0);
        // 12 concurrent callers may admit exactly one copy of the same id.
        final AtomicInteger admitted=new AtomicInteger(0);
        final CountDownLatch start=new CountDownLatch(1);
        Thread[] t=new Thread[12];
        for(int i=0;i<t.length;i++){
            t[i]=new Thread(()->{try{start.await();}catch(Exception e){throw new RuntimeException(e);}
                if(a.reserve("race"))admitted.incrementAndGet();});
            t[i].start();
        }
        start.countDown();
        for(Thread th:t)th.join();
        ok(admitted.get()==1);ok(a.count()==1);
        a.release("race");ok(a.reserve("race"));
        a.release("race");ok(a.count()==0);
        System.out.println("OGRELAY14_DUPLICATE_RELAY_REQUEST_NO_REPOST=PASS");
        System.out.println("OGRELAY14_CROSS_GENERATION_IN_FLIGHT_GUARD=PASS");
        System.out.println("OGRELAY14_CONCURRENT_SINGLE_ADMISSION=PASS");
        System.out.println("OGRELAY14_ASSERTIONS="+checks);
    }
}
