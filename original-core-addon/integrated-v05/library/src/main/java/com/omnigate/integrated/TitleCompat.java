package com.omnigate.integrated;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Narrow non-streaming auto-title adapter; never fabricates a title. */
final class TitleCompat {
    static final String MARKER="<<<OMNIGATE_TITLE_V1>>>";
    private static final String[] CATEGORIES={"code","writing","research",
        "analysis","creative","chat","math","translation","health","finance",
        "travel","education","design","productivity","support","other"};
    private TitleCompat(){}
    static final class Prepared {
        final byte[] body;
        Prepared(byte[] data){body=data;}
    }
    static Prepared prepare(byte[] input) throws JSONException {
        if(input==null || input.length==0 || input.length>64*1024)return null;
        JSONObject root;
        try {root=new JSONObject(new String(input,StandardCharsets.UTF_8));}
        catch(JSONException malformed){return null;}
        JSONArray messages=root.optJSONArray("messages");
        if(messages==null || messages.length()<2)return null;
        StringBuilder system=new StringBuilder();
        for(int i=0;i<messages.length();i++){
            JSONObject msg=messages.optJSONObject(i);
            if(msg==null)return null;
            String role=msg.optString("role","");
            if(("system".equals(role)||"developer".equals(role)) &&
                msg.opt("content") instanceof String)
                system.append(msg.optString("content",""));
        }
        String lower=system.toString().toLowerCase(Locale.ROOT);
        if(!lower.contains("generate concise titles for conversations")
            || !lower.contains("\"title\"") || !lower.contains("\"category\""))
            return null;
        JSONObject last=messages.optJSONObject(messages.length()-1);
        if(last==null || !"user".equals(last.optString("role",""))
            || !(last.opt("content") instanceof String))return null;
        String user=last.optString("content","");
        if(!user.contains("Conversation:") && !user.contains("对话："))return null;
        if(root.optJSONArray("tools")!=null && root.optJSONArray("tools").length()>0)
            throw new JSONException("title_tools_not_supported");
        if(root.optBoolean("stream",false))
            throw new JSONException("title_stream_not_supported");
        if(user.length()>12000)throw new JSONException("title_context_too_long");
        int ix=user.indexOf("Conversation:");
        String conversation=(ix>=0?user.substring(ix+13):user).trim();
        if(conversation.isEmpty())throw new JSONException("title_conversation_empty");
        String prompt="请根据下列对话给出简体中文短标题（最多6个词）与分类。"+
            "只输出严格合法的JSON对象，不要Markdown代码块、角色前缀或其他文字。"+
            "必须恰好包含字符串字段 title 和 category。category仅从下列选项选择："+
            String.join(", ",CATEGORIES)+"。"+
            "\n\n对话：\n"+conversation;
        root.put("messages",new JSONArray().put(new JSONObject()
            .put("role","user").put("content",MARKER+prompt)));
        root.put("stream",false);
        return new Prepared(root.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String stripSpeakerPrefix(String input) {
        if(input==null)return "";
        return input.replaceFirst(
            "^\\s*(?:ChatGPT\\s*(?:说|says)?|Assistant\\s*(?:说|says)?|AI\\s*(?:说|says)?)\\s*[:：]\\s*","");
    }
    static String normalizeTitle(String input) {
        String str=stripSpeakerPrefix(input).trim();
        if(str.startsWith("`)) {
            str=str.replaceFirst("^``(?:json)?\\s*","")
                .replaceFirst("\\s*``\\s*$","").trim();
        }
        JSONObject json=null;
        try {json=new JSONObject(str);}
        catch(JSONException e){
            int start=str.indexOf('{'),end=str.lastIndexOf('}');
            if(start>=0 && end>start){
                try {json=new JSONObject(str.substring(start,end+1));}
                catch(JSONException ignored){}
            }
        }
        if(json==null)return str;
        String title=json.optString("title","").trim();
        String category=json.optString("category","").trim().toLowerCase(Locale.ROOT);
        if(title.isEmpty()||title.length()>100)return str;
        boolean valid=false;
        for(String cat:CATEGORIES)if(cat.equals(category))valid=true;
        if(!valid)return str;
        try{return new JSONObject().put("title",title)
            .put("category",category).toString();}
        catch(JSONException e){return str;}
    }
}
