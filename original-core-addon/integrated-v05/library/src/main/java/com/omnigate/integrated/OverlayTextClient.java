package com.omnigate.integrated;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal ordinary-text E2E gate: no attachment envelope, upload, or injection hacks.
 * A successful /health or socket connection alone is never shown as an AI reply.
 * In-memory draft/last result only. No Stop/cancel on panel close or tab changes.
 */
public final class OverlayTextClient {
    private static final AtomicBoolean SENDING = new AtomicBoolean(false);
    private static volatile String draft = "你好，请只回复：文本接口测试成功。";
    private static volatile String status = "未发送。先在当前模型网页确认文字对话可用。";
    private static volatile String response = "";
    private static volatile String lastModel = "";
    private static volatile int lastHttp = 0;
    private static volatile int revision = 0;
    private static volatile boolean lastSuccess = false;
    private OverlayTextClient() {}

    public static boolean isSending() { return SENDING.get(); }
    public static String draft() { return draft; }
    public static String status() { return status; }
    public static String response() { return response; }
    public static String lastModel() { return lastModel; }
    public static int lastHttp() { return lastHttp; }
    public static boolean lastSuccess() { return lastSuccess; }
    public static int revision() { return revision; }

    public static void setDraft(String input) {
        if (input != null) draft = input.substring(0, Math.min(input.length(), 3000));
    }
    private static void report(String state, String body, int http, boolean ok) {
        status = state;
        response = body;
        lastHttp = http;
        lastSuccess = ok;
        revision++;
    }

    public static String send(String input) {
        setDraft(input);
        if (draft.trim().isEmpty()) return "请输入非空文字；本次未发送";
        if (draft.length() > 3000) return "单次测试最多 3000 字";
        if (!SENDING.compareAndSet(false, true)) return "已有文字请求正在等待，未重复发送";

        if (!OriginalCoreBridge.originalHostAlive()) {
            SENDING.set(false);
            report("原模型网页宿主不存在；未发送", "", 0, false);
            return status;
        }
        // A working webpage does not automatically mean the original ApiServer is listening.
        if (!OriginalCoreBridge.nativeOwnerRunning()
                || !OriginalCoreBridge.isListening()) {
            SENDING.set(false);
            report("原核心 API 尚未运行；请到「API」页手动恢复服务后再试。网页聊天可独立正常工作。",
                "", 0, false);
            return status;
        }
        if (OriginalCoreBridge.isPortConflicted()) {
            SENDING.set(false);
            report("原核心与本地代理同用 8787，禁止自循环请求", "", 0, false);
            return status;
        }
        String model = OriginalCoreBridge.activeProviderModel();
        if (model == null || model.trim().isEmpty()) {
            SENDING.set(false);
            report("无法读取原模型提供商，请先在原网页选定模型；未发送", "", 0, false);
            return status;
        }
        if (!LocalProxyRuntime.isListening() && !LocalProxyRuntime.start()) {
            SENDING.set(false);
            report("本地 8787 启动失败，请检查端口是否被占用；未发送", "", 0, false);
            return status;
        }

        final String prompt = draft;
        lastModel = model;
        report("请求已发起：" + model + "。正在等待真实文本回复，不主动截停。",
            "", 0, false);
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject payload = new JSONObject()
                    .put("model", model)
                    .put("stream", false)
                    .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user").put("content", prompt)));
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection = (HttpURLConnection)new URL(
                    "http://127.0.0.1:8787/v1/chat/completions").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(0);
                connection.setRequestProperty("Authorization", "Bearer test");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }

                int code = connection.getResponseCode();
                InputStream input = code >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
                ByteArrayOutputStream raw = new ByteArrayOutputStream();
                if (input != null) {
                    try (InputStream in = input) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n=in.read(buf))!=-1) {
                            if (raw.size() < 65536)
                                raw.write(buf, 0, Math.min(n, 65536 - raw.size()));
                            // Continue draining the upstream even beyond displayed length.
                            // This process never clicks provider Stop on disconnect.
                        }
                    }
                }
                String rawText = new String(raw.toByteArray(), StandardCharsets.UTF_8);
                if (code != 200) {
                    String detail = rawText.length() > 350
                        ? rawText.substring(0,350) : rawText;
                    report("HTTP " + code + "：未收到有效模型回复。" +
                        (code==502 ? " 原网页可能未登录、未准备就绪或没有产生回执。" : ""),
                        detail, code, false);
                } else {
                    JSONObject json = new JSONObject(rawText);
                    JSONArray choices = json.optJSONArray("choices");
                    String answer = "";
                    if (choices != null && choices.length() > 0) {
                        JSONObject choice=choices.optJSONObject(0);
                        JSONObject msg=choice==null ? null : choice.optJSONObject("message");
                        if (msg!=null) answer=msg.optString("content","");
                    }
                    if (answer == null || answer.trim().isEmpty()) {
                        report("HTTP 200，但没有可用的文本内容；不计为通过",
                            rawText.substring(0,Math.min(350,rawText.length())),code,false);
                    } else {
                        report("HTTP 200 · 已收到模型文本（" + model + "）",
                            answer.substring(0,Math.min(answer.length(),8000)), code, true);
                    }
                }
            } catch (Exception ex) {
                String detail=ex.getMessage();
                report("文字请求失败：" + ex.getClass().getSimpleName() +
                    (detail==null?"":" · "+detail.substring(0,Math.min(180,detail.length()))),
                    "",0,false);
            } finally {
                if (connection!=null) connection.disconnect();
                SENDING.set(false);
                revision++;
            }
        },"omnigate-text-e2e").start();
        return status;
    }
}
