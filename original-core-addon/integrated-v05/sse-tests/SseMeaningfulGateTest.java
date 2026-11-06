package com.omnigate.integrated;

public final class SseMeaningfulGateTest {
    private static int checks;
    private static void ok(boolean v) {
        checks++;
        if(!v)throw new AssertionError("SSE transport decision "+checks);
    }
    public static void main(String[] args) {
        SseMeaningfulGate a=new SseMeaningfulGate();
        // No dependency on ProxyTrace / Android ThreadLocal.
        ok(!a.isReady());
        for(int i=0;i<100;i++)a.observe(false,false);
        ok(!a.isReady());
        ok(!a.hasText());
        ok(!a.hasTool());
        // Nonempty content first (possibly after long reasoning).
        a.observe(true,false);
        ok(a.isReady());
        ok(a.hasText());
        ok(!a.hasTool());
        a.observe(false,false);
        ok(a.isReady());
        // Tool-only completions are not mistaken for empty answers.
        SseMeaningfulGate b=new SseMeaningfulGate();
        b.observe(false,true);
        ok(b.isReady());
        ok(!b.hasText());
        ok(b.hasTool());
        // A second independent response has its own flags.
        SseMeaningfulGate c=new SseMeaningfulGate();
        ok(!c.isReady());
        c.observe(false,false);
        ok(!c.isReady());
        c.observe(true,true);
        ok(c.hasText());
        ok(c.hasTool());
        System.out.println("OGSSE04_SIGNAL_INDEPENDENT_OF_OPTIONAL_TRACE=PASS");
        System.out.println("OGSSE04_CONTROL_ONLY_NOT_FAKE_DONE=PASS");
        System.out.println("OGSSE04_TEXT_OR_TOOL_WITHOUT_EOF=PASS");
        System.out.println("OGSSE04_CHECKS="+checks);
    }
}
