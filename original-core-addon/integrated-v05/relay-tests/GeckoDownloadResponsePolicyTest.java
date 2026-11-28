package com.omnigate.integrated;
public final class GeckoDownloadResponsePolicyTest {
    private static int checks;
    private static void expect(String expected,int status,String source,boolean body){
        checks++;
        if(!java.util.Objects.equals(expected,GeckoDownloadResponsePolicy.reject(status,source,body)))
            throw new AssertionError("download policy case "+checks);
    }
    public static void main(String[] args){
        String http="https://example.com/export.json";
        String blob="blob:https://example.com/12345678";
        expect(null,200,http,true);
        expect(null,206,http,true);
        expect("FAILED_HTTP",0,http,true);
        expect("FAILED_HTTP",302,http,true);
        expect("FAILED_AUTH",401,http,true);
        expect("FAILED_AUTH",403,http,true);
        expect("FAILED_429",429,http,true);
        expect("FAILED_HTTP",500,http,true);
        expect("FAILED_EMPTY",200,http,false);
        expect(null,0,blob,true);
        expect(null,200,blob,true);
        expect("FAILED_EMPTY",0,blob,false);
        expect("FAILED_HTTP",404,blob,true);
        expect("FAILED_AUTH",403,blob,true);
        expect("FAILED_SCHEME",0,"blob:null/abc",true);
        expect("FAILED_SCHEME",0,"blob:file:///tmp/abc",true);
        expect("FAILED_SCHEME",200,"file:///etc/passwd",true);
        expect("FAILED_SCHEME",200,"data:text/plain,example",true);
        expect("FAILED_SCHEME",0,"blob:https://user@example.com/abc",true);
        expect("FAILED_SCHEME",0,null,true);
        System.out.println("OGDOWNLOAD14_RESPONSE_POLICY_"+checks+"_CASES=PASS");
    }
}
