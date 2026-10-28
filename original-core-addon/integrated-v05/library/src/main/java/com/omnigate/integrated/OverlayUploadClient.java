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

    public static String start() {
        if (!SENDING.compareAndSet(false,true)) return "已有发送任务在进行，未重复提交";
        byte[] bytes = OverlayAttachmentDraft.bytes();
        final String file = OverlayAttachmentDraft.name();
        final String mime = OverlayAttachmentDraft.mime();
        final String question = OverlayAttachmentDraft.prompt();
        final String model = OriginalCoreBridge.activeProviderModel();
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
        OverlayAttachmentDraft.report("正在通过 " + model + " 的原版 V2 注入链路发送，等待网页回执…");
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject attachment = new JSONObject()
                    .put("id",UUID.randomUUID().toString())
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
                    OverlayAttachmentDraft.report(
                        "HTTP 502 / no reply：网页注入未得到模型响应。附件未确认送达；请检查网页是否已加载、登录及上传完成。");
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
                        OverlayAttachmentDraft.report("收到模型回复（仍需网页附件迹象确认）：" +
                            responseText.substring(0,Math.min(responseText.length(),180)));
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
