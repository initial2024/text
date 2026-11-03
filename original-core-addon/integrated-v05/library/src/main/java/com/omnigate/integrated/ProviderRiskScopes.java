package com.omnigate.integrated;

import java.util.Locale;
import org.json.JSONObject;

/**
 * Fixed provider identity derived from the actual user-selected native
 * provider AND the requested model. Never infer from text, URL substrings,
 * prompts, account names, or an arbitrary user-defined model prefix.
 */
final class ProviderRiskScopes {
    static final String[] WEB=new String[]{"deepseek","chatgpt","grok","glm"};
    static final String[] ALL=new String[]{
        "native","deepseek","chatgpt","grok","glm","claude","relay"
    };
    static boolean validChannel(String channel){
        if(channel==null)return false;
        for(String known:ALL)if(known.equals(channel))return true;
        return false;
    }
    static String fromModel(String model){
        if(model==null)return "";
        String m=model.trim().toLowerCase(Locale.ROOT);
        if("deepseek-chat".equals(m)||"deepseek-reasoner".equals(m)||
           "deepseek-web".equals(m)||"deepseek".equals(m)||
           m.startsWith("deepseek-chat-"))return "deepseek";
        if("chatgpt-web".equals(m)||"chatgpt".equals(m)||
           "gpt-web".equals(m)||"gpt".equals(m))return "chatgpt";
        if("grok-web-fast".equals(m)||"grok-web".equals(m)||
           "grok".equals(m))return "grok";
        if("glm-web-flash".equals(m)||"glm-web".equals(m)||
           "glm".equals(m)||"chatglm".equals(m)||
           "chatglm-web".equals(m))return "glm";
        return "";
    }
    static final class Selection {
        final boolean allowed;
        final String channel,reason;
        Selection(boolean allowed,String channel,String reason){
            this.allowed=allowed;this.channel=channel;this.reason=reason;
        }
    }
    static Selection select(String activeModel,byte[] body){
        String selected=fromModel(activeModel);
        if(selected.isEmpty())
            return new Selection(false,"","provider_selection_unverified");
        String requested="";
        try{
            JSONObject obj=new JSONObject(
                new String(body,java.nio.charset.StandardCharsets.UTF_8));
            requested=fromModel(obj.optString("model",""));
        }catch(Exception malformed){
            return new Selection(false,"","request_model_unrecognized");
        }
        if(requested.isEmpty())
            return new Selection(false,"","request_model_unrecognized");
        if(!selected.equals(requested))
            return new Selection(false,"","requested_provider_not_active");
        return new Selection(true,selected,"matched");
    }
}
