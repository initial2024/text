package com.omnigate.integrated;

/** Split raw SSE at full blank-line boundaries only. Preserve original bytes. */
final class SseSafePreludeFrames {
    interface Verifier {boolean safe(byte[] bytes,int offset,int length);}
    private SseSafePreludeFrames(){}
    static int safePrefixLength(byte[] bytes,Verifier verifier){
        if(bytes==null || verifier==null)throw new IllegalArgumentException();
        int lineStart=0,frameStart=0,safeEnd=0;
        for(int i=0;i<bytes.length;i++){
            if(bytes[i]!='\n')continue;
            int lineEnd=i;
            if(lineEnd>lineStart && bytes[lineEnd-1]=='\r')lineEnd--;
            if(lineEnd==lineStart){
                int end=i+1;
                if(!verifier.safe(bytes,frameStart,end-frameStart))break;
                safeEnd=end;frameStart=end;
            }
            lineStart=i+1;
        }
        return safeEnd;
    }
}
