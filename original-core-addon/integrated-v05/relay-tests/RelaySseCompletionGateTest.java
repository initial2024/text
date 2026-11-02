package com.omnigate.integrated;
import java.nio.charset.StandardCharsets;
public final class RelaySseCompletionGateTest {
    static int checks;
    static void ok(boolean pass){
        checks++;
        if(!pass)throw new AssertionError("SSE completion "+checks);
    }
    static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static RelaySseCompletionGate feed(String... chunks){
        RelaySseCompletionGate gate=new RelaySseCompletionGate();
        for(String chunk:chunks){byte[] b=bytes(chunk);gate.observe(b,b.length);}
        return gate;
    }
    static RelaySseCompletionGate eachByte(String stream){
        RelaySseCompletionGate g=new RelaySseCompletionGate();
        byte[] bytes=bytes(stream);
        for(int i=0;i<bytes.length;i++)g.observe(new byte[]{bytes[i]},1);
        return g;
    }
    public static void main(String[] args) {
        String c="data: {\"choices\":[{\"delta\":{\"content\":\"你好🙂\"}}]}\r\n\r\n";
        String f="data: {\"choices\":[{\"finish_reason\":\"stop\",\"delta\":{}}]}\r\n\r\n";
        String done="data: [DONE]\r\n\r\n";
        ok(feed(c,f,done).complete());
        ok(eachByte(": keepalive\r\n\r\n"+c+f+done).complete());
        ok(!feed("data:[DONE]\n\n").complete()); // no prior SSE data
        ok(!feed(": heartbeat\n\ndata: [DONE]\n\n").complete());
        ok(feed("data: {\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}\n\ndata:[DONE]\n\n").complete());
        ok(!feed(c,f).complete());                       // EOF without done
        ok(!feed(c,"data: [DONE]\r\n").complete());      // unfinished delimiter
        ok(!feed("data: [DONE]").complete());           // truncated line
        ok(!feed("data: [DONE]\n\n",c).complete());      // data after completion
        ok(!feed(done,done).complete());                // duplicate DONE
        ok(!feed("data: {\"content\":\"[DONE]\"}\n\n").complete());
        ok(!feed("data: [DONE]\ndata: {\"content\":\"x\"}\n\n").complete());
        ok(!feed("data: {\"content\":\"x\"}\ndata: [DONE]\n\n").complete());
        ok(!feed("data: [DONE]\n\n","data: x\n").complete());
        ok(!feed("data: [DONE]\n\n","data: x\n\n").complete());
        // Valid SSE comments after the marker are not a duplicated answer.
        ok(feed("data: [DONE]\n\n",": heartbeat\n\n").complete());
        // An overlong untrusted data line does not require retaining the body.
        StringBuilder longline=new StringBuilder("data: ");
        for(int i=0;i<9000;i++)longline.append('x');
        ok(!feed(longline+"\n\n").complete());
        boolean invalid=false;
        try{new RelaySseCompletionGate().observe(new byte[2],3);}
        catch(IllegalArgumentException expected){invalid=true;}
        ok(invalid);
        System.out.println("OGRELAY12_SSE_EXACTLY_ONE_FULL_DONE=PASS");
        System.out.println("OGRELAY12_SPLIT_UTF8_CRLF_AND_TRUNCATION=PASS");
        System.out.println("OGRELAY12_NO_FALSE_RESEND_OR_DELIVERY_ACK=PASS");
        System.out.println("OGRELAY12_ASSERTIONS="+checks);
    }
}
