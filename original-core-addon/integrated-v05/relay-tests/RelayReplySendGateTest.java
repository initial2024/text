package com.omnigate.integrated;
public final class RelayReplySendGateTest {
    static int count;
    static void ok(boolean v){count++;if(!v)throw new AssertionError("queue gate "+count);}
    static void denied(Runnable r) {
        boolean failed=false;
        try{r.run();}catch(IllegalStateException e){failed=true;}
        ok(failed);
    }
    public static void main(String[] args) {
        RelayReplySendGate normal=new RelayReplySendGate();
        ok(normal.canSendError());
        normal.recordStart(true);
        for(int i=0;i<1000;i++){ok(normal.canSendChunk());normal.recordChunk(true);}
        ok(normal.canSendEnd());
        normal.recordEnd(true);
        ok(normal.terminated());
        ok(!normal.canSendError());ok(!normal.canSendChunk());
        denied(()->normal.recordChunk(true));
        denied(()->normal.recordError(true));
        RelayReplySendGate noStart=new RelayReplySendGate();
        noStart.recordStart(false);
        ok(noStart.failed());ok(!noStart.canSendChunk());ok(!noStart.canSendEnd());
        ok(!noStart.canSendError());denied(()->noStart.recordEnd(true));
        RelayReplySendGate mid=new RelayReplySendGate();
        mid.recordStart(true);mid.recordChunk(true);mid.recordChunk(false);
        ok(mid.failed());ok(!mid.canSendEnd());ok(!mid.canSendChunk());
        ok(!mid.canSendError());denied(()->mid.recordEnd(true));
        RelayReplySendGate rejectedEnd=new RelayReplySendGate();
        rejectedEnd.recordStart(true);rejectedEnd.recordEnd(false);
        ok(rejectedEnd.failed());ok(!rejectedEnd.terminated());
        RelayReplySendGate error=new RelayReplySendGate();
        error.recordError(true);ok(error.terminated());denied(()->error.recordStart(true));
        RelayReplySendGate lateError=new RelayReplySendGate();
        lateError.recordStart(true);lateError.recordChunk(true);lateError.recordError(false);
        ok(lateError.failed());ok(!lateError.canSendEnd());
        System.out.println("OGRELAY07_WEBSOCKET_QUEUE_REJECTION_NO_RESEND=PASS");
        System.out.println("OGRELAY07_START_CHUNK_END_ERROR_STATE_GATES=PASS");
        System.out.println("OGRELAY07_REAL_PUBLIC_DELIVERY=NOT_TESTED");
        System.out.println("OGRELAY07_ASSERTIONS="+count);
    }
}
