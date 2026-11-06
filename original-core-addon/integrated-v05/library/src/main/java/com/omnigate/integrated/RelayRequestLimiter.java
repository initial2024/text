package com.omnigate.integrated;
import java.util.concurrent.Semaphore;
final class RelayRequestLimiter {
    private final Semaphore permits;
    RelayRequestLimiter(int max){
        if(max<1)throw new IllegalArgumentException("max");
        permits=new Semaphore(max,true);
    }
    boolean tryEnter(){return permits.tryAcquire();}
    void leave(){permits.release();}
    int available(){return permits.availablePermits();}
}
