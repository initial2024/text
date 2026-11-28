package com.omnigate.integrated;

import java.net.URI;

/** Classify native download response metadata. Never fetch or replay URLs. */
final class GeckoDownloadResponsePolicy {
    private GeckoDownloadResponsePolicy(){}
    static boolean localBlob(String source){
        if(source==null||!source.startsWith("blob:"))return false;
        try{
            URI origin=new URI(source.substring(5));
            return ("https".equalsIgnoreCase(origin.getScheme())||
                "http".equalsIgnoreCase(origin.getScheme()))&&origin.getHost()!=null&&
                origin.getRawUserInfo()==null&&origin.getRawQuery()==null&&
                origin.getRawFragment()==null&&origin.getRawPath()!=null&&
                origin.getRawPath().length()>1;
        }catch(Exception malformed){return false;}
    }
    static String reject(int status,String source,boolean hasBody){
        // Gecko's native blob stream can have no HTTP status at all.
        // Do not interpret an HTTP error or a missing stream as success.
        boolean blob=localBlob(source);
        boolean http=false;
        try{
            URI u=new URI(source);
            http=("https".equalsIgnoreCase(u.getScheme())||
                "http".equalsIgnoreCase(u.getScheme()))&&u.getHost()!=null;
        }catch(Exception malformed){}
        if(!(http||blob))return "FAILED_SCHEME";
        if(status==401||status==403)return "FAILED_AUTH";
        if(status==429)return "FAILED_429";
        if(!(status>=200&&status<300)&&!(status==0&&blob))return "FAILED_HTTP";
        if(!hasBody)return "FAILED_EMPTY";
        return null;
    }
}
