package com.omnigate.integrated;
import java.util.UUID;

/** Fixed, allowlisted login origins; no arbitrary URL or token copying. */
final class AccountBrowserSlots {
    static final String[] PROVIDERS={"deepseek","glm","chatgpt","grok","claude"};
    static final String[] LABELS={"DeepSeek","GLM / 智谱清言","ChatGPT","Grok","Claude · 仅网页登录"};
    static final int MAX_SLOTS=5;
    private AccountBrowserSlots(){}
    static boolean supported(String provider){
        for(String fixed:PROVIDERS)if(fixed.equals(provider))return true;
        return false;
    }
    /** Browser account identity is intentionally broader than native API providers. */
    static boolean browserOnly(String provider){return "claude".equals(provider);}
    static boolean nativeExecutorSupported(String provider){
        return supported(provider)&&!browserOnly(provider);
    }
    static String home(String provider){
        if("deepseek".equals(provider))return "https://chat.deepseek.com/";
        if("glm".equals(provider))return "https://chatglm.cn/";
        if("chatgpt".equals(provider))return "https://chatgpt.com/";
        if("grok".equals(provider))return "https://grok.com/";
        if("claude".equals(provider))return "https://claude.ai/";
        throw new IllegalArgumentException("unknown_provider");
    }
    static String newContextId(){return "og-ctx-v1-"+UUID.randomUUID();}
    static boolean validContextId(String id){
        if(id==null||!id.startsWith("og-ctx-v1-")||id.length()!=46)return false;
        try{UUID.fromString(id.substring(10));return true;}
        catch(IllegalArgumentException invalid){return false;}
    }
}
