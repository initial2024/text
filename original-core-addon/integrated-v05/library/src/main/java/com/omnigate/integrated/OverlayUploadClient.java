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
 * Original V2 attachment envelope, sent through the 8787 proxy to original
 * native API and browser bridge. HTTP 200 alone is NOT an upload receipt.
 * Never triggers provider Stop; no finite response-read timeout.
 */
public final class OverlayUploadClient {
    private static final AtomicBoolean SENDING = new AtomicBoolean();
    private OverlayUploadClient() {}
    public static boolean isSending() { return SENDING.get(); }

    /** Recover only a specifically matched and completed original page answer.
     * Never invent a result, retry a sent image, or expose an older turn.
     */
    private static String correlatedRecovery(String requestId, String baseline) {
        if(baseline==null) return null;
        final long deadline=android.os.SystemClock.uptimeMillis()+12000L;
        String previous=""; int stable=0;
        while(android.os.SystemClock.uptimeMillis()<deadline) {
            try {
                JSONObject owner=OriginalCoreBridge.diagnoseVisibleHostSocket();
                if(!owner.optBoolean("visibleHostOwnsPort",false))return null;
                JSONObject page=OriginalCoreBridge.diagnoseCurrentPage();
                if(!requestId.equals(page.optString("attachmentRequestId","")))return null;
                if(!"new_explicit_user_turn".equals(
                        page.optString("attachmentSendEvidence","")))return null;
                if(!page.optBoolean("assistantReplyChangedSinceSend",false)
                        || page.optBoolean("generationRaw",true)
                        || page.optBoolean("autoRolloverActive",false)){
                    previous="";stable=0;
                } else {
                    JSONObject nativeState=OriginalCoreBridge.inspectNativeAnswerForUserRequest();
                    String answer=nativeState.optString("answer","");
                    if(!nativeState.optBoolean("generating",true)
                            && !answer.isEmpty() && !answer.equals(baseline)
                            && answer.length()==page.optInt("assistantReplyChars",-1)){
                        stable=answer.equals(previous)?stable+1:1;
                        previous=answer;
                        if(stable>=2)return answer;
                    } else {previous="";stable=0;}
                }
            } catch(Throwable ignored) {return null;}
            android.os.SystemClock.sleep(500L);
        }
        return null;
    }

    public static String start() {
        if (!SENDING.compareAndSet(false,true)) return "已有发送任务在进行，未重复提交";
        byte[] bytes = OverlayAttachmentDraft.bytes();
        final String file = OverlayAttachmentDraft.name();
        final String mime = OverlayAttachmentDraft.mime();
        final String question = OverlayAttachmentDraft.prompt();
        final String model = OriginalCoreBridge.activeProviderModel();
        final String requestId = UUID.randomUUID().toString();
        if (bytes == null || file == null) {
            SENDING.set(false); return "请先通过系统文件选择器选择文件";
        }
        if (!LocalProxyRuntime.isListening() || !OriginalCoreBridge.isListening()) {
            SENDING.set(false); return "8787 或原核心 API 未监听，未提交";
        }
        if (!OriginalCoreBridge.originalHostAlive()) {
            SENDING.set(false); return "原模型网页宿主不存在，未提交";
        }
        if (model == null || model.isEmpty()) {
            SENDING.set(false); return "无法确认原版模型，会话未通过验收，未提交";
        }
        // The website must be open and signed in; this can only be proven by
        // the original bridge receiving a successful provider-side reply.
        OverlayAttachmentDraft.setReply("");
        OverlayAttachmentDraft.report("正在通过 " + model + " 的原版 V2 注入链路发送，等待网页回执…");
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String baselineAnswer=null;
                try {
                    baselineAnswer=OriginalCoreBridge.inspectNativeAnswerForUserRequest()
                        .optString("answer","");
                } catch(Throwable ignored){}
                JSONObject attachment = new JSONObject()
                    .put("id",requestId)
                    .put("name",file).put("mime",mime)
                    .put("current",true)
                    .put("data",Base64.encodeToString(bytes,Base64.NO_WRAP));
                JSONObject payload = new JSONObject()
                    .put("v",2)
                    .put("prompt",question.isEmpty() ? "请查看附件" : question)
                    .put("rolloverPrompt",question)
                    .put("attachments",new JSONArray().put(attachment));
                String envelope = "<<<DSH_WEB_RELAY_V2_ATTACHMENT_ENVELOPE>>>"
                    + Base64.encodeToString(payload.toString()
                        .getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP)
                    + "<<<END_DSH_WEB_RELAY_V2_ATTACHMENT_ENVELOPE>>>";
                JSONObject req = new JSONObject()
                    .put("model",model)
                    .put("stream",false)
                    .put("messages",new JSONArray().put(
                        new JSONObject().put("role","user").put("content",envelope)));
                byte[] body = req.toString().getBytes(StandardCharsets.UTF_8);
                conn=(HttpURLConnection)new URL(
                    "http://127.0.0.1:8787/v1/chat/completions").openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(0);
                conn.setRequestProperty("Authorization","Bearer test");
                conn.setRequestProperty("Content-Type","application/json");
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(body.length);
                try(OutputStream out=conn.getOutputStream()) {out.write(body);}
                int code=conn.getResponseCode();
                InputStream stream=code>=400 ? conn.getErrorStream() : conn.getInputStream();
                ByteArrayOutputStream response=new ByteArrayOutputStream();
                if(stream!=null)try(InputStream in=stream) {
                    byte[] chunk=new byte[4096];int n;
                    while((n=in.read(chunk))!=-1) {
                        if(response.size()<16000)
                            response.write(chunk,0,Math.min(n,16000-response.size()));
                    }
                }
                String raw=response.toString("UTF-8");
                if(code==502 && raw.contains("no reply")) {
                    String recovered=correlatedRecovery(requestId,baselineAnswer);
                    if(recovered!=null){
                        OverlayAttachmentDraft.setReply(recovered);
                        OverlayAttachmentDraft.report(
                            "原核心 HTTP 502；已用本次附件 ID、新用户消息和稳定原生回答恢复"
                            +recovered.length()+" 字。API 状态仍为 502。回复摘录："+
                            recovered.substring(0,Math.min(recovered.length(),160)));
                    } else {
                        OverlayAttachmentDraft.report(
                            "原核心 HTTP 502；无法安全确认本次图片的回复，未重复发送。");
                    }
                } else if(code>=400) {
                    OverlayAttachmentDraft.report("HTTP "+code+"：上游拒绝或未应答（未确认上传）。"+
                        raw.substring(0,Math.min(raw.length(),180)));
                } else {
                    JSONObject json=new JSONObject(raw);
                    JSONArray choices=json.optJSONArray("choices");
                    String responseText="";
                    if(choices!=null && choices.length()>0) {
                        JSONObject msg=choices.optJSONObject(0).optJSONObject("message");
                        if(msg!=null)responseText=msg.optString("content","");
                    }
                    if(responseText.isEmpty()) {
                        OverlayAttachmentDraft.report("HTTP "+code+
                            "：没有有效模型文本回复，附件未确认送达");
                    } else {
                        OverlayAttachmentDraft.setReply(responseText);
                        OverlayAttachmentDraft.report("HTTP "+code+
                            "：收到模型回答 "+responseText.length()+" 字，请核对网页。");
                    }
                }
            } catch(Exception e) {
                OverlayAttachmentDraft.report("附件注入失败："+e.getClass().getSimpleName()+
                    (e.getMessage()==null?"":" · "+
                        e.getMessage().substring(0,Math.min(100,e.getMessage().length()))));
            } finally {
                if(conn!=null)conn.disconnect();
                java.util.Arrays.fill(bytes,(byte)0);
                SENDING.set(false);
            }
        },"omnigate-overlay-inject").start();
        return "发送请求已提交到本地代理，等待原网页确认";
    }
}
