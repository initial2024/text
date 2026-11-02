package com.omnigate.integrated;
final class RelayQueuePacer {
    static final long HIGH_WATER_BYTES=2L*1024*1024;
    static final long LOW_WATER_BYTES=512L*1024;
    static final long MAX_WAIT_MS=30_000L;
    enum Decision { READY, WAIT, STALE, STALLED }
    private long waitStartedAt=-1;
    private boolean stopped;
    Decision observe(long queuedBytes,long elapsedMs,boolean current){
        if(queuedBytes<0||elapsedMs<0)throw new IllegalArgumentException();
        if(!current){stopped=true;return Decision.STALE;}
        if(stopped)return Decision.STALLED;
        if(waitStartedAt<0){
            if(queuedBytes<HIGH_WATER_BYTES)return Decision.READY;
            waitStartedAt=elapsedMs;
        }
        if(queuedBytes<=LOW_WATER_BYTES){
            waitStartedAt=-1;
            return Decision.READY;
        }
        if(elapsedMs-waitStartedAt>=MAX_WAIT_MS){
            stopped=true;return Decision.STALLED;
        }
        return Decision.WAIT;
    }
}
