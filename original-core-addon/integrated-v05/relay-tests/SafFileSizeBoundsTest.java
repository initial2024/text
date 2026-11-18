package com.omnigate.integrated;

/** Plain JVM regression harness; does not access a document provider. */
public final class SafFileSizeBoundsTest {
    private static final long MI=1024L*1024L;
    private static void equal(long expected,long value,String caseName) {
        if(expected!=value)throw new AssertionError(caseName+": "+value+" expected "+expected);
    }
    private static void reject(long used,long file,String caseName){
        try{
            SafFileSizeBounds.checkedTotal(used,file,64*MI,128*MI);
            throw new AssertionError("accepted "+caseName);
        }catch(IllegalArgumentException expected){}
    }
    public static void main(String[] args){
        equal(20, SafFileSizeBounds.reportedSize(20,-1),"provider-only size");
        equal(22, SafFileSizeBounds.reportedSize(-1,22),"fd-only fallback");
        equal(-1, SafFileSizeBounds.reportedSize(-1,-1),"unknown remains unknown");
        equal(30, SafFileSizeBounds.reportedSize(12,30),"cannot trust smaller provider");
        equal(30, SafFileSizeBounds.reportedSize(30,12),"cannot trust smaller descriptor");
        equal(0, SafFileSizeBounds.reportedSize(0,0),"empty file");
        equal(64*MI,SafFileSizeBounds.checkedTotal(0,64*MI,64*MI,128*MI),"item boundary");
        equal(128*MI,SafFileSizeBounds.checkedTotal(64*MI,64*MI,64*MI,128*MI),"aggregate boundary");
        reject(0,-1,"unknown size");
        reject(0,64*MI+1,"oversize item");
        reject(65*MI,64*MI,"aggregate overflow");
        reject(Long.MAX_VALUE,1,"integer overflow");
        reject(-1,1,"corrupted aggregate");
        System.out.println("OGUPLOAD03_METADATA_BOUNDS_13_OF_13=PASS");
    }
}
