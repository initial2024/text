package com.omnigate.integrated;

/**
 * Byte-bounded, framing-only audit for a single OpenAI-compatible SSE body.
 * No prompt, assistant text, tool arguments or identifiers are stored.
 * A [DONE] event is accepted only after its complete blank-line delimiter.
 * It does NOT acknowledge WebSocket/Relay/client delivery.
 */
final class RelaySseCompletionGate {
    private static final byte[] DONE="data: [DONE]".getBytes(
        java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] COMPACT_DONE="data:[DONE]".getBytes(
        java.nio.charset.StandardCharsets.US_ASCII);
    private static final int MAX_LINE=8192;
    private final byte[] line=new byte[MAX_LINE];
    private int len;
    private boolean overflow;
    private boolean candidateDone;
    private boolean nonDoneData;
    private boolean eventHasData;
    private int doneCount;
    private boolean invalidAfterDone;
    private boolean invalidDoneEvent;
    private boolean sawDataBeforeDone;

    void observe(byte[] buffer,int n){
        if(buffer==null || n<0 || n>buffer.length)
            throw new IllegalArgumentException("invalid body bytes");
        for(int i=0;i<n;i++){
            byte b=buffer[i];
            if(b=='\n'){
                onLine();
                len=0;overflow=false;
            }else if(!overflow){
                if(len<MAX_LINE)line[len++]=b;
                else {overflow=true;len=0;}
            }
        }
    }

    private void onLine(){
        int count=overflow?0:len;
        if(count>0 && line[count-1]=='\r')count--;
        if(!overflow && count==0){
            if(candidateDone){
                if(nonDoneData)invalidDoneEvent=true;
                else {
                    doneCount++;
                    if(doneCount>1)invalidAfterDone=true;
                }
            }else if(eventHasData && doneCount>0){
                invalidAfterDone=true;
            }
            candidateDone=false;nonDoneData=false;eventHasData=false;
            return;
        }
        if(overflow){
            eventHasData=true;
            nonDoneData=true;
            if(doneCount>0)invalidAfterDone=true;
            return;
        }
        if(startsWithData(count)){
            eventHasData=true;
            boolean isDone=equalsBytes(DONE,count)||
                equalsBytes(COMPACT_DONE,count);
            if(isDone){
                if(candidateDone || nonDoneData)invalidDoneEvent=true;
                candidateDone=true;
            }else{
                nonDoneData=true;
                // A terminal marker by itself cannot establish even the
                // presence of a response data event. Semantic content/tool
                // verification remains the native Sidecar's responsibility.
                if(doneCount==0 && count>5)sawDataBeforeDone=true;
                if(doneCount>0)invalidAfterDone=true;
            }
        }
    }

    private boolean startsWithData(int count) {
        if(count<5)return false;
        return line[0]=='d'&&line[1]=='a'&&line[2]=='t'&&
            line[3]=='a'&&line[4]==':';
    }

    private boolean equalsBytes(byte[] target,int count){
        if(count!=target.length)return false;
        for(int i=0;i<count;i++)if(line[i]!=target[i])return false;
        return true;
    }

    boolean complete(){
        return sawDataBeforeDone && doneCount==1 && !invalidAfterDone &&
            !invalidDoneEvent && !overflow && len==0 &&
            !candidateDone && !eventHasData;
    }
}
