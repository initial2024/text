package com.omnigate.integrated;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opt-in, non-streaming OpenAI-compatible image request adapter for /v1/chat/completions.
 * Never starts another native server, never fetches remote image URLs, and never
 * retries a prompt after an uncertain send. Native 1601 remains the only owner.
 * Result HTTP 200 means a verified response was delivered by the sidecar, not
 * necessarily that the original 1601 HTTP status was 200; provenance is explicit.
 */
public final class AttachmentApiBridge {
    private static final String START="<<<DSH_WEB_RELAY_V2_ATTACHMENT_ENVELOPE>>>";
    private static final String END="<<<END_DSH_WEB_RELAY_V2_ATTACHMENT_ENVELOPE>>>";
    private static final int MAX_IMAGE_BYTES=8*1024*1024;
    private static final AtomicBoolean ACTIVE=new AtomicBoolean(false);
    private AttachmentApiBridge(){}

    static final class Prepared {
        final byte[] requestBody;
        final String requestId;
        final String model;
        final boolean clientRequestedStream;
        Prepared(byte[] body,String id,String model,boolean stream){
            this.requestBody=body;this.requestId=id;this.model=model;
            this.clientRequestedStream=stream;
        }
    }
    static final class Result {
        final int http;
        final JSONObject body;
        Result(int status,JSONObject response){http=status;body=response;}
    }

    static Prepared prepare(byte[] input) throws Exception {
        if(input==null || input.length==0)return null;
        JSONObject root;
        try {root=new JSONObject(new String(input,StandardCharsets.UTF_8));}
        catch(Exception malformed){return null;} // Preserve legacy routing for non-JSON
        JSONArray messages=root.optJSONArray("messages");
        if(messages==null || messages.length()==0)return null;
        JSONObject last=messages.optJSONObject(messages.length()-1);
        if(last==null || !"user".equals(last.optString("role","")))return null;
        Object content=last.opt("content");
        String model=root.optString("model","");
        if(model.isEmpty())throw new IllegalArgumentException("model_required");
        final boolean wantedStream=root.optBoolean("stream",false);

        if(content instanceof String) {
            String plain=(String)content;
            int begin=plain.indexOf(START),end=plain.indexOf(END);
            if(begin<0 || end<=begin)return null;
            // The native bridge requires non-streaming input. When a client
            // requests streaming, emit verified SSE only AFTER the original
            // full reply has been received; never synthesize completion early.
            root.put("stream",false);
            String encoded=plain.substring(begin+START.length(),end);
            if(encoded.length()>20*1024*1024)
                throw new IllegalArgumentException("attachment_envelope_too_large");
            byte[] bytes=Base64.decode(encoded,Base64.DEFAULT);
            JSONObject envelope=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            JSONArray attachments=envelope.optJSONArray("attachments");
            if(envelope.optInt("v")!=2 || attachments==null ||
                    attachments.length()!=1)
                throw new IllegalArgumentException("v2_single_attachment_required");
            JSONObject a=attachments.optJSONObject(0);
            String id=a==null?"":a.optString("id","");
            if(!validId(id))
                throw new IllegalArgumentException("invalid_attachment_request_id");
            String data=a.optString("data","");
            if(data.isEmpty() || data.length()>12*1024*1024)
                throw new IllegalArgumentException("attachment_bytes_too_large");
            if(Base64.decode(data,Base64.DEFAULT).length>MAX_IMAGE_BYTES)
                throw new IllegalArgumentException("attachment_bytes_too_large");
            return new Prepared(root.toString().getBytes(StandardCharsets.UTF_8),
                id,model,wantedStream);
        }
        if(!(content instanceof JSONArray))return null;
        JSONArray segments=(JSONArray)content;
        StringBuilder prompt=new StringBuilder();
        String encodedImage=null;
        String mime=null;
        String filename=null;
        boolean seenImage=false;
        for(int i=0;i<segments.length();i++){
            JSONObject part=segments.optJSONObject(i);
            if(part==null)throw new IllegalArgumentException("invalid_content_part");
            String type=part.optString("type","");
            if("text".equals(type)){
                String text=part.optString("text","");
                if(prompt.length()+text.length()>1200)
                    throw new IllegalArgumentException("image_prompt_too_long");
                prompt.append(text);
            } else if("image_url".equals(type)){
                if(seenImage)throw new IllegalArgumentException("only_one_image_supported");
                seenImage=true;
                Object u=part.opt("image_url");
                String raw=u instanceof String?(String)u:
                    u instanceof JSONObject?((JSONObject)u).optString("url",""):"";
                int comma=raw.indexOf(',');
                if(!raw.startsWith("data:image/") || comma<0 ||
                        !raw.substring(0,comma).endsWith(";base64"))
                    throw new IllegalArgumentException("image_must_be_base64_data_url");
                mime=raw.substring(5,comma-7);
                if(!("image/png".equals(mime)||"image/jpeg".equals(mime)||
                        "image/webp".equals(mime)))
                    throw new IllegalArgumentException("unsupported_image_mime");
                encodedImage=raw.substring(comma+1);
                filename="attachment."+
                    ("image/jpeg".equals(mime)?"jpg":"image/webp".equals(mime)?"webp":"png");
                if(encodedImage.length()>12*1024*1024)
                    throw new IllegalArgumentException("image_too_large");
            } else if("file".equals(type)){
                if(seenImage)throw new IllegalArgumentException("only_one_attachment_supported");
                seenImage=true;
                JSONObject file=part.optJSONObject("file");
                if(file==null)throw new IllegalArgumentException("file_object_required");
                if(file.has("file_id"))
                    throw new IllegalArgumentException("external_file_id_not_available");
                filename=file.optString("filename","").trim();
                if(filename.isEmpty() || filename.length()>160 ||
                    filename.contains("/") || filename.contains("\\") ||
                    filename.indexOf((char)0)>=0)
                    throw new IllegalArgumentException("invalid_attachment_filename");
                String data=file.optString("file_data","");
                if(data.length()>12*1024*1024)
                    throw new IllegalArgumentException("file_too_large");
                int comma=data.indexOf(',');
                if(data.startsWith("data:") && comma>5 &&
                    data.substring(5,comma).endsWith(";base64")){
                    mime=data.substring(5,comma-7);
                    encodedImage=data.substring(comma+1);
                }else{
                    throw new IllegalArgumentException("file_must_be_base64_data_url");
                }
                if(!("application/pdf".equals(mime) ||
                    "text/plain".equals(mime) ||
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime)))
                    throw new IllegalArgumentException("unsupported_file_mime");
            } else throw new IllegalArgumentException("unsupported_content_part");
        }
        if(!seenImage)return null;
        byte[] image;
        try{image=Base64.decode(encodedImage,Base64.DEFAULT);}
        catch(IllegalArgumentException bad){throw new IllegalArgumentException("attachment_base64_invalid");}
        if(image.length==0 || image.length>MAX_IMAGE_BYTES)
            throw new IllegalArgumentException("attachment_size_out_of_range");
        if("application/pdf".equals(mime) &&
            (image.length<5 || image[0]!='%' || image[1]!='P' ||
             image[2]!='D' || image[3]!='F' || image[4]!='-'))
            throw new IllegalArgumentException("pdf_header_invalid");
        if("image/png".equals(mime) &&
            (image.length<8 || (image[0]&0xff)!=137 || image[1]!=80 ||
             image[2]!=78 || image[3]!=71))
            throw new IllegalArgumentException("png_header_invalid");
        if("image/jpeg".equals(mime) &&
            (image.length<3 || (image[0]&0xff)!=255 || (image[1]&0xff)!=216))
            throw new IllegalArgumentException("jpeg_header_invalid");
        if("image/webp".equals(mime) &&
            (image.length<12 || image[0]!='R' || image[1]!='I' ||
             image[2]!='F' || image[3]!='F'))
            throw new IllegalArgumentException("webp_header_invalid");
        String id=UUID.randomUUID().toString();
        JSONObject attachment=new JSONObject()
            .put("id",id).put("name",filename).put("mime",mime)
            .put("current",true)
            .put("data",Base64.encodeToString(image,Base64.NO_WRAP));
        JSONObject inner=new JSONObject().put("v",2)
            .put("prompt",prompt.length()==0?"请描述附件内容":prompt.toString())
            .put("rolloverPrompt",prompt.toString())
            .put("attachments",new JSONArray().put(attachment));
        String wrap=START+Base64.encodeToString(
            inner.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP)+END;
        last.put("content",wrap);
        root.put("stream",false);
        byte[] body=root.toString().getBytes(StandardCharsets.UTF_8);
        return new Prepared(body,id,model,wantedStream);
    }

    private static boolean validId(String id){
        if(id==null || id.length()<8 || id.length()>128)return false;
        for(int i=0;i<id.length();i++){
            char c=id.charAt(i);
            if(!((c>='0'&&c<='9')||(c>='a'&&c<='z')||
                 (c>='A'&&c<='Z')||c=='-'||c=='_'))return false;
        }
        return true;
    }

    static Result execute(Prepared req){
        if(!ACTIVE.compareAndSet(false,true))
            return failure(409,"attachment_request_in_progress",false,"not_submitted");
        HttpURLConnection c=null;
        String baseline=null;
        boolean submitted=false;
        try {
            JSONObject owner=OriginalCoreBridge.diagnoseActiveNativeOwner();
            if(!owner.optBoolean("verified",false))
                return failure(409,"native_api_driver_not_verified",false,"not_submitted");
            baseline=OriginalCoreBridge.inspectVerifiedApiAnswer().optString("answer","");
            URL url=new URL("http://127.0.0.1:"+OriginalCoreBridge.configuredPort()
                +"/v1/chat/completions");
            c=(HttpURLConnection)url.openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(10000);
            c.setReadTimeout(0); // Never cancel a still-generating model reply
            c.setInstanceFollowRedirects(false);
            c.setUseCaches(false);
            c.setRequestProperty("Connection","close");
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            String key=GatewayApp.upstreamApiKey();
            if(key.isEmpty())key=OriginalCoreBridge.originalApiKey();
            if(!key.isEmpty())c.setRequestProperty("Authorization","Bearer "+key);
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(req.requestBody.length);
            try(OutputStream out=c.getOutputStream()){
                submitted=true; // An I/O failure may mean unknown delivery, never retry.
                out.write(req.requestBody);
                out.flush();
            }
            int code=c.getResponseCode();
            ByteArrayOutputStream raw=new ByteArrayOutputStream();
            InputStream response=code>=400?c.getErrorStream():c.getInputStream();
            if(response!=null)try(InputStream in=response){
                byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))!=-1){
                    if(raw.size()+n>1024*1024)
                        return failure(502,"upstream_response_oversize",true,"unknown");
                    raw.write(buf,0,n);
                }
            }
            if(code==200){
                try {
                    JSONObject upstream=new JSONObject(
                        new String(raw.toByteArray(),StandardCharsets.UTF_8));
                    JSONArray choices=upstream.optJSONArray("choices");
                    JSONObject choice=choices==null?null:choices.optJSONObject(0);
                    JSONObject msg=choice==null?null:choice.optJSONObject("message");
                    if(msg!=null && !msg.optString("content","").trim().isEmpty()){
                        upstream.put("omnigate",new JSONObject()
                            .put("delivery","native_http_200")
                            .put("native_http_status",200)
                            .put("attachment_request_id",req.requestId));
                        return new Result(200,upstream);
                    }
                } catch(Exception ignored){}
            }
            if(code!=200 && code!=502)
                return failure(code>=400?code:502,
                    "original_native_http_"+code,true,"not_retried");
            // Original 502 (or an empty HTTP200) is a transport failure,
            // NOT evidence that the image was never submitted.
            String answer=OverlayUploadClient.correlatedRecovery(req.requestId,baseline);
            if(answer!=null)
                return recovered(req,answer,code);
            return failure(502,"original_api_reply_unavailable",true,
                "unverified_no_retry_"+OverlayUploadClient.recoveryStage());
        } catch(Exception transport){
            if(!submitted)
                return failure(503,"original_native_transport_failed",false,"not_submitted");
            String recovered=OverlayUploadClient.correlatedRecovery(req.requestId,baseline);
            if(recovered!=null)return recovered(req,recovered,-1);
            return failure(502,"original_native_stream_ended",true,
                "unverified_no_retry_"+OverlayUploadClient.recoveryStage());
        } finally {
            if(c!=null)c.disconnect();
            ACTIVE.set(false);
        }
    }
    private static Result recovered(Prepared req,String raw,int nativeHttp){
        try {
        String answer=raw.replaceFirst(
            "^(?:ChatGPT|AI|助手|Assistant)\\s*(?:说|says)?\\s*[:：]\\s*","");
        JSONArray choices=new JSONArray().put(new JSONObject()
            .put("index",0)
            .put("message",new JSONObject().put("role","assistant")
                .put("content",answer))
            .put("finish_reason","stop"));
        JSONObject result=new JSONObject()
            .put("id","chatcmpl-omnigate-"+UUID.randomUUID().toString().replace("-",""))
            .put("object","chat.completion")
            .put("created",System.currentTimeMillis()/1000L)
            .put("model",req.model)
            .put("choices",choices)
            .put("omnigate",new JSONObject()
                .put("delivery","correlated_native_recovery")
                .put("native_http_status",
                    nativeHttp<0?JSONObject.NULL:nativeHttp)
                .put("attachment_request_id",req.requestId)
                .put("browser_reply_verified",true));
        return new Result(200,result);
        } catch(Exception invalidJson) {
            return failure(502,"recovery_response_encoding_failed",true,"unverified_no_retry");
        }
    }
    private static String safeBrowserPhase(String field){
        try{
            JSONObject owner=OriginalCoreBridge.diagnoseActiveNativeOwner();
            if(!owner.optBoolean("verified",false))return "owner_not_verified";
            String value=OriginalCoreBridge.diagnoseVerifiedApiPage()
                .optString(field,"unknown");
            return value.matches("[A-Za-z0-9_-]{1,96}")?value:"redacted";
        }catch(Throwable ex){return "diagnostic_unavailable";}
    }
    private static Result failure(int code,String error,boolean maybeSent,String stage){
        try {
        JSONObject result=new JSONObject().put("error",new JSONObject()
            .put("type","omnigate_attachment_error")
            .put("code",error)
            .put("message","Attachment result not confirmed; do not automatically retry"))
            .put("omnigate",new JSONObject()
                .put("possible_provider_submission",maybeSent)
                .put("recovery_stage",stage)
                .put("attachment_browser_phase",safeBrowserPhase("attachmentSendPhase"))
                .put("attachment_browser_evidence",safeBrowserPhase("attachmentSendEvidence")));
        return new Result(code,result);
        } catch(Exception invalidJson) {
            return new Result(code,new JSONObject());
        }
    }
}
