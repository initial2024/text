package com.omnigate.integrated;

public final class SsePrecontentBoundTest {
    private static int checks;
    private static void ok(boolean v) {
        checks++;
        if (!v) throw new AssertionError("SSE precontent gate "+checks);
    }
    public static void main(String[] args) {
        SsePrecontentBound g = new SsePrecontentBound();
        ok(g.observe(100,false)==SsePrecontentBound.Decision.BUFFER);
        ok(g.observe(65436,false)==SsePrecontentBound.Decision.BUFFER);
        ok(!g.failed());
        ok(g.observe(1,false)==SsePrecontentBound.Decision.FAIL_CLOSED_DRAIN);
        ok(g.failed());
        ok(!g.forwarding());
        ok(g.observe(100,true)==SsePrecontentBound.Decision.DRAIN);
        ok(g.observe(0,false)==SsePrecontentBound.Decision.DRAIN);

        SsePrecontentBound h = new SsePrecontentBound();
        ok(h.observe(65536,false)==SsePrecontentBound.Decision.BUFFER);
        ok(h.observe(5000,true)==SsePrecontentBound.Decision.START_FORWARD);
        ok(h.forwarding());
        ok(!h.failed());
        ok(h.observe(50000,false)==SsePrecontentBound.Decision.FORWARD);

        SsePrecontentBound tool = new SsePrecontentBound();
        ok(tool.observe(1,true)==SsePrecontentBound.Decision.START_FORWARD);
        ok(tool.observe(10000,false)==SsePrecontentBound.Decision.FORWARD);
        ok(!tool.failed());

        SseMeaningfulGate real = new SseMeaningfulGate();
        ok(!real.isReady());
        real.observe(false,false);  // finish_reason "tool_calls" alone: not payload
        ok(!real.isReady());
        real.observe(false,true);   // actual delta.tool_calls
        ok(real.isReady());
        SseMeaningfulGate controlOnly = new SseMeaningfulGate();
        for(int i=0;i<300;i++) controlOnly.observe(false,false);
        ok(!controlOnly.isReady());

        boolean threw=false;
        try{new SsePrecontentBound().observe(-1,false);}catch(IllegalArgumentException e){threw=true;}
        ok(threw);
        System.out.println("OGSSE05_PRECONTENT_BOUND_FAIL_CLOSED_NO_DONE=PASS");
        System.out.println("OGSSE05_REAL_TEXT_TOOL_REMAINS_LIVE=PASS");
        System.out.println("OGSSE05_TERMINAL_ONLY_NOT_TOOL_SUCCESS=PASS");
        System.out.println("OGSSE05_ASSERTIONS="+checks);
    }
}
