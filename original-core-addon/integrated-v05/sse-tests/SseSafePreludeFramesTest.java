package com.omnigate.integrated;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
public final class SseSafePreludeFramesTest {
    static int checks;
    static void yes(boolean value) {if(!value)throw new AssertionError("check "+(++checks));checks++;}
    static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static boolean fakeSafe(byte[] src,int off,int len){
        String s=new String(src,off,len,StandardCharsets.UTF_8);
        return (s.startsWith(":") || s.contains("reasoning_content") ||
            s.contains("\"role\":\"assistant\"")) &&
            !s.contains("[DONE]") && !s.contains("finish_reason") &&
            !s.contains("\"content\":\"A\"");
    }
    static int safe(byte[] src){
        return SseSafePreludeFrames.safePrefixLength(src,SseSafePreludeFramesTest::fakeSafe);
    }
    public static void main(String[] args) {
        String good=": keepalive\n\n"+"data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"想\"}}]}\n\n";
        byte[] bytes=bytes(good+"data: [DONE]\n\n");
        yes(safe(bytes)==bytes(good).length);
        yes(safe(bytes("data: [DONE]\n\n"))==0);
        yes(safe(bytes("data: {\"choices\":[{\"delta\":{\"content\":\"A\"}}]}\n\n"))==0);
        yes(safe(bytes("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"ok\"}}]}\n"))==0);
        String crlf=": ping\r\n\r\n"+"data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"ok\"}}]}\r\n";
        yes(safe(bytes(crlf))==bytes(": ping\r\n\r\n").length);
        yes(safe(bytes(crlf+"\r\n"))==bytes(crlf+"\r\n").length);
        String unit="data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"reasoning\"}}]}\n\n";
        StringBuilder reason=new StringBuilder();
        for(int i=0;i<1500;i++)reason.append(unit);
        byte[] longStream=bytes(reason+"data: [DONE]\n\n");
        yes(longStream.length>65536);
        yes(safe(longStream)==bytes(reason.toString()).length);
        SsePrecontentBound guard=new SsePrecontentBound();
        yes(guard.observe(65536,false)==SsePrecontentBound.Decision.BUFFER);
        guard.replaceBufferedAfterVerifiedDrain(100);
        yes(guard.observe(8192,false)==SsePrecontentBound.Decision.BUFFER);
        yes(!guard.failed());
        yes(guard.observe(62000,false)==SsePrecontentBound.Decision.FAIL_CLOSED_DRAIN);
        boolean blocked=false;
        try{guard.replaceBufferedAfterVerifiedDrain(0);}catch(IllegalStateException e){blocked=true;}
        yes(blocked);
        System.out.println("OGSSE06_LONG_REASONING_BOUNDED_VERIFIED_RELEASE=PASS");
        System.out.println("OGSSE06_TERMINAL_FRAGMENT_HELD_WITHOUT_FALSE_DONE=PASS");
        System.out.println("OGSSE06_UNKNOWN_OVERSIZE_FAIL_CLOSED=PASS");
        System.out.println("OGSSE06_ASSERTIONS="+checks);
    }
}
