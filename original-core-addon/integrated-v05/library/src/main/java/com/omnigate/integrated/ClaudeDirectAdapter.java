package com.omnigate.integrated;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

/** Official Claude Messages API -> OpenAI Chat Completions, local-only. */
final class ClaudeDirectAdapter {
    private ClaudeDirectAdapter() {}
    static final class Result {
        final int status; final JSONObject json;
        Result(int s, JSONObject j){status=s;json=j;}
    }
    static boolean isClaude(byte[] body) {
        try { return new JSONObject(new String(body,StandardCharsets.UTF_8))
            .optString("model","").startsWith("claude-"); }
        catch(Exception ex){return false;}
    }
    static Result invoke(byte[] raw, String authorization) {
        String key=authorization==null?"":authorization.trim();
        if(key.regionMatches(true,0,"Bearer ",0,7)) key=key.substring(7).trim();
        if(!key.matches("sk-ant-[A-Za-z0-9_-]{12,240}"))
            return error(401,"claude_api_key_required");
        HttpURLConnection connection=null;
        try{
            JSONObject openai=new JSONObject(new String(raw,StandardCharsets.UTF_8));
            JSONObject payload=convert(openai);
            URL endpoint=new URL("https://api.anthropic.com/v1/messages");
            connection=(HttpURLConnection)endpoint.openConnection();
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(0);
            connection.setRequestProperty("Authorization","Bearer "+key);
            connection.setRequestProperty("anthropic-version","2023-06-01");
            connection.setRequestProperty("Content-Type","application/json");
            connection.setDoOutput(true);
            byte[] data=payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(data.length);
            try(OutputStream out=connection.getOutputStream()){out.write(data);}
            int status=connection.getResponseCode();
            try(InputStream input=status>=400?connection.getErrorStream():connection.getInputStream()){
                if(input==null)return error(502,"claude_upstream_empty");
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1){
                    if(out.size()+n>2*1024*1024)return error(502,"claude_response_too_large");
                    out.write(buffer,0,n);
                }
                JSONObject res=new JSONObject(out.toString("UTF-8"));
                if(status!=200)return error(status,"claude_upstream_http_"+status);
                JSONArray blocks=res.optJSONArray("content");
                if(blocks==null)return error(502,"claude_content_missing");
                StringBuilder text=new StringBuilder();
                for(int i=0;i<blocks.length();i++){
                    JSONObject b=blocks.optJSONObject(i);
                    if(b==null)continue;
                    if(!"text".equals(b.optString("type","")))
                        return error(422,"claude_non_text_result_unsupported");
                    text.append(b.optString("text",""));
                }
                if(text.length()==0)return error(502,"claude_no_text_reply");
                JSONObject usage=res.optJSONObject("usage");
                int in=usage==null?0:Math.max(0,usage.optInt("input_tokens",0));
                int outTokens=usage==null?0:Math.max(0,usage.optInt("output_tokens",0));
                return new Result(200,new JSONObject()
                    .put("id",res.optString("id","claude-response"))
                    .put("object","chat.completion")
                    .put("created",System.currentTimeMillis()/1000L)
                    .put("model",openai.optString("model",""))
                    .put("choices",new JSONArray().put(new JSONObject()
                        .put("index",0)
                        .put("message",new JSONObject().put("role","assistant")
                            .put("content",text.toString()))
                        .put("finish_reason","max_tokens".equals(res.optString("stop_reason",""))?
                            "length":"stop")))
                    .put("usage",new JSONObject().put("prompt_tokens",in)
                        .put("completion_tokens",outTokens).put("total_tokens",in+outTokens)));
            }
        }catch(IllegalArgumentException bad){return error(422,bad.getMessage());}
         catch(Exception bad){return error(502,"claude_transport_or_parse_error");}
        finally{if(connection!=null)connection.disconnect();}
    }
    static JSONObject convert(JSONObject req) throws Exception {
        String model=req.optString("model","");
        if(!model.matches("claude-[A-Za-z0-9._-]{3,96}"))
            throw new IllegalArgumentException("claude_model_invalid");
        if(req.has("tools")||req.has("tool_choice")||req.has("functions"))
            throw new IllegalArgumentException("claude_tools_not_implemented");
        JSONArray original=req.optJSONArray("messages");
        if(original==null||original.length()==0||original.length()>100)
            throw new IllegalArgumentException("claude_messages_required");
        JSONArray messages=new JSONArray();StringBuilder sys=new StringBuilder();
        for(int i=0;i<original.length();i++){
            JSONObject msg=original.optJSONObject(i);
            if(msg==null)throw new IllegalArgumentException("claude_message_invalid");
            String role=msg.optString("role","");
            Object content=msg.opt("content");
            if("system".equals(role)){
                if(!(content instanceof String))
                    throw new IllegalArgumentException("claude_system_text_only");
                sys.append((String)content).append('\n');continue;
            }
            if(!"user".equals(role)&&!"assistant".equals(role))
                throw new IllegalArgumentException("claude_role_unsupported");
            Object mapped=content;
            if(content instanceof JSONArray){
                JSONArray arr=(JSONArray)content;
                JSONArray parts=new JSONArray();
                for(int j=0;j<arr.length();j++){
                    JSONObject part=arr.optJSONObject(j);
                    if(part==null)throw new IllegalArgumentException("claude_part_invalid");
                    String type=part.optString("type","");
                    if("text".equals(type))parts.put(new JSONObject().put("type","text")
                        .put("text",part.optString("text","")));
                    else if("image_url".equals(type)&&"user".equals(role)){
                        Object image=part.opt("image_url");
                        String uri=image instanceof String?(String)image:
                            image instanceof JSONObject?((JSONObject)image).optString("url",""):"";
                        int comma=uri.indexOf(',');
                        if(!uri.startsWith("data:image/")||comma<0||
                            !uri.substring(0,comma).endsWith(";base64"))
                            throw new IllegalArgumentException("claude_inline_image_required");
                        String mime=uri.substring(5,comma-7);
                        if(!("image/png".equals(mime)||"image/jpeg".equals(mime)||
                            "image/webp".equals(mime)||"image/gif".equals(mime)))
                            throw new IllegalArgumentException("claude_image_mime_unsupported");
                        String encoded=uri.substring(comma+1);
                        if(encoded.isEmpty()||encoded.length()>8*1024*1024)
                            throw new IllegalArgumentException("claude_image_too_large");
                        parts.put(new JSONObject().put("type","image").put("source",
                            new JSONObject().put("type","base64").put("media_type",mime)
                                .put("data",encoded)));
                    }else throw new IllegalArgumentException("claude_part_unsupported");
                }
                mapped=parts;
            }else if(!(content instanceof String))
                throw new IllegalArgumentException("claude_content_invalid");
            messages.put(new JSONObject().put("role",role).put("content",mapped));
        }
        if(messages.length()==0)throw new IllegalArgumentException("claude_no_user_messages");
        int limit=req.optInt("max_tokens",1024);
        if(limit<1||limit>8192)limit=1024;
        JSONObject body=new JSONObject().put("model",model).put("messages",messages)
            .put("max_tokens",limit).put("stream",false);
        if(sys.length()>0)body.put("system",sys.toString());
        return body;
    }
    private static Result error(int status,String code){
        try{return new Result(status,new JSONObject().put("error",
            new JSONObject().put("type","claude_adapter_error").put("code",code)));}
        catch(Exception ignored){return new Result(500,new JSONObject());}
    }
}
