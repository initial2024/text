package com.omnigate.integrated;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class CloudflareRelayClient {
    public interface StatusListener {
        void onStatus(String status);
    }

    private final String wsUrl;
    private final String publicBase;
    private final String relayKey;
    private final int localPort;
    private final StatusListener listener;
    private final ExecutorService requestPool = Executors.newCachedThreadPool();
    private final OkHttpClient client;

    private volatile boolean running;
    private volatile WebSocket socket;
    private volatile int reconnectAttempt;

    public CloudflareRelayClient(
            String wsUrl,
            String publicBase,
            String relayKey,
            int localPort,
            StatusListener listener) {
        this.wsUrl = wsUrl;
        this.publicBase = publicBase;
        this.relayKey = relayKey;
        this.localPort = localPort;
        this.listener = listener;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        reconnectAttempt = 0;
        connect();
    }

    public synchronized void stop() {
        running = false;
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            try { current.close(1000, "stopped"); } catch (Throwable ignored) {}
        }
        requestPool.shutdownNow();
        client.dispatcher().cancelAll();
    }

    private void connect() {
        if (!running) return;
        if (relayKey == null || relayKey.length() < 24) {
            status("需要配置 Relay Key");
            return;
        }

        status("连接 OmniGate Cloudflare Relay");
        Request request = new Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer " + relayKey)
                .header("User-Agent", "OmniGate-Android/2.8")
                .build();

        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                socket = webSocket;
                reconnectAttempt = 0;
                status("自有穿透已连接 · 正在验证公网");
                probePublicHealth();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                handleMessage(bytes.utf8());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                status("Relay 正在断开 · " + code);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (socket == webSocket) socket = null;
                if (running) scheduleReconnect("连接关闭 " + code);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (socket == webSocket) socket = null;
                String detail = t == null
                        ? "unknown"
                        : t.getClass().getSimpleName()
                            + (t.getMessage() == null ? "" : "：" + t.getMessage());
                if (response != null) {
                    detail += " · HTTP " + response.code();
                }
                if (running) scheduleReconnect(detail);
            }
        });
    }

    private void scheduleReconnect(String reason) {
        reconnectAttempt++;
        long delay = Math.min(15_000L, 1500L * reconnectAttempt);
        status("自有穿透断开 · " + sanitize(reason) + " · 重连中");
        new Thread(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (running) connect();
        }, "omnigate-relay-reconnect").start();
    }

    private void probePublicHealth() {
        requestPool.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(
                        publicBase + "/__relay_health").openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setUseCaches(false);
                if (conn.getResponseCode() != 200) {
                    status("Relay 已连，但公网探针失败 · HTTP " + conn.getResponseCode());
                    return;
                }

                String body;
                try (InputStream in = conn.getInputStream()) {
                    body = readSmall(in);
                }
                JSONObject json = new JSONObject(body);
                if (json.optBoolean("ok")
                        && json.optBoolean("deviceConnected")) {
                    status("公网可用 · gw-33e850180248.claua.de");
                } else {
                    status("Relay 已连，但公网尚未识别设备");
                }
            } catch (Throwable e) {
                status("Relay 已连，但公网探针失败 · "
                        + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : "：" + sanitize(e.getMessage())));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void handleMessage(String text) {
        try {
            JSONObject msg = new JSONObject(text);
            if (!"req".equals(msg.optString("type"))) return;
            requestPool.execute(() -> handleRequest(msg));
        } catch (Throwable ignored) {}
    }

    private void handleRequest(JSONObject msg) {
        String id = msg.optString("id", "");
        if (id.isEmpty()) return;

        HttpURLConnection conn = null;
        try {
            String method = msg.optString("method", "GET");
            String path = msg.optString("path", "/");
            if (!path.startsWith("/")) path = "/" + path;

            conn = (HttpURLConnection) new URL(
                    "http://127.0.0.1:" + localPort + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10_000);
            // Do not end a still-running native model response at 620s.
            // Relay/edge disconnects may still occur independently.
            conn.setReadTimeout(0);
            conn.setInstanceFollowRedirects(false);

            JSONObject headers = msg.optJSONObject("headers");
            if (headers != null) {
                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String lower = key.toLowerCase();
                    if ("host".equals(lower)
                            || "content-length".equals(lower)
                            || "connection".equals(lower)
                            || "x-omnigate-public-relay".equals(lower)) {
                        continue;
                    }
                    conn.setRequestProperty(key, headers.optString(key, ""));
                }
            }
            conn.setRequestProperty("X-OmniGate-Public-Relay", "1");

            String body = msg.optString("body", "");
            if (!body.isEmpty()
                    && ("POST".equals(method)
                    || "PUT".equals(method)
                    || "PATCH".equals(method))) {
                conn.setDoOutput(true);
                byte[] bytes = Base64.decode(body, Base64.DEFAULT);
                conn.getOutputStream().write(bytes);
                conn.getOutputStream().flush();
                conn.getOutputStream().close();
            }

            int code = conn.getResponseCode();
            JSONObject responseHeaders = new JSONObject();
            for (Map.Entry<String, java.util.List<String>> entry
                    : conn.getHeaderFields().entrySet()) {
                String key = entry.getKey();
                if (key == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                    continue;
                }
                String lower = key.toLowerCase();
                if ("content-length".equals(lower)
                        || "connection".equals(lower)
                        || "transfer-encoding".equals(lower)) {
                    continue;
                }
                responseHeaders.put(key, entry.getValue().get(0));
            }

            JSONObject start = new JSONObject()
                    .put("type", "resStart")
                    .put("id", id)
                    .put("status", code)
                    .put("headers", responseHeaders);
            send(start);

            InputStream in = code >= 400
                    ? conn.getErrorStream()
                    : conn.getInputStream();
            if (in != null) {
                try (InputStream response = in) {
                    byte[] buffer = new byte[12 * 1024];
                    int n;
                    while ((n = response.read(buffer)) >= 0) {
                        if (n == 0) continue;
                        byte[] chunk = new byte[n];
                        System.arraycopy(buffer, 0, chunk, 0, n);
                        JSONObject frame = new JSONObject()
                                .put("type", "resChunk")
                                .put("id", id)
                                .put("data", Base64.encodeToString(
                                        chunk,
                                        Base64.NO_WRAP));
                        send(frame);
                    }
                }
            }

            send(new JSONObject()
                    .put("type", "resEnd")
                    .put("id", id));
        } catch (Throwable e) {
            try {
                send(new JSONObject()
                        .put("type", "resError")
                        .put("id", id)
                        .put("status", 502)
                        .put("message",
                                e.getClass().getSimpleName()
                                        + (e.getMessage() == null
                                        ? ""
                                        : "：" + sanitize(e.getMessage()))));
            } catch (Throwable ignored) {}
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void send(JSONObject json) {
        WebSocket current = socket;
        if (current == null) return;
        current.send(json.toString());
    }

    private static String readSmall(InputStream in) throws Exception {
        byte[] buffer = new byte[2048];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = in.read(buffer)) >= 0 && out.size() < 16 * 1024) {
            if (n > 0) out.write(buffer, 0, n);
        }
        return out.toString("UTF-8");
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String cleaned = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (cleaned.length() > 180) {
            cleaned = cleaned.substring(0, 180) + "…";
        }
        return cleaned;
    }

    private void status(String value) {
        if (listener != null) listener.onStatus(value);
    }
}
