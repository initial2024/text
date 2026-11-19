package com.omnigate.integrated;

/** Per-response WebSocket queue-admission state, holding no request data. */
final class RelayReplySendGate {
    private enum Phase { NEW, STREAMING, FAILED, TERMINAL }
    private Phase phase=Phase.NEW;
    void recordStart(boolean queued) {
        require(Phase.NEW);
        phase=queued?Phase.STREAMING:Phase.FAILED;
    }
    boolean canSendChunk(){return phase==Phase.STREAMING;}
    void recordChunk(boolean queued) {
        require(Phase.STREAMING);
        if(!queued)phase=Phase.FAILED;
    }
    boolean canSendEnd(){return phase==Phase.STREAMING;}
    void recordEnd(boolean queued){
        require(Phase.STREAMING);
        phase=queued?Phase.TERMINAL:Phase.FAILED;
    }
    boolean canSendError(){return phase==Phase.NEW || phase==Phase.STREAMING;}
    void recordError(boolean queued){
        if(!canSendError())throw new IllegalStateException("error after failure/end");
        phase=queued?Phase.TERMINAL:Phase.FAILED;
    }
    boolean failed(){return phase==Phase.FAILED;}
    boolean terminated(){return phase==Phase.TERMINAL;}
    private void require(Phase expected){
        if(phase!=expected)throw new IllegalStateException("invalid response state");
    }
}
