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
    // Keep public Relay request concurrency bounded, independent of streams.
    private final RelayRequestLimiter requestLimit = new RelayRequestLimiter(6);
    private final OkHttpClient client;

    private volatile boolean running;
    private final RelayConnectionEpoch epochs = new RelayConnectionEpoch();
    public boolean isConnected(){return epochs.isConnected();}
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
        epochs.start();
        reconnectAttempt = 0;
        connect();
    }

    public synchronized void stop() {
        running = false;
        epochs.stop(); // Invalidate all callbacks and in-flight relay writes first.
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
        // Include newWebSocket and socket binding in the same lock: callbacks
        // cannot observe an unbound socket, even on very fast failures.
        synchronized (epochs) {
            final long generation = epochs.begin();
            if (generation < 0) return;
            status("连接 OmniGate Cloudflare Relay");
            try {
                Request request = new Request.Builder()
                        .url(wsUrl)
                        .header("Authorization", "Bearer " + relayKey)
                        .header("User-Agent", "OmniGate-Android/2.8")
                        .build();
                WebSocket created = client.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        if (!epochs.opened(generation, webSocket)) return;
                        reconnectAttempt = 0;
                        status("自有穿透已连接 · 正在验证公网");
                        probePublicHealth(generation, webSocket);
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        if (!epochs.isCurrent(generation, webSocket)) return;
                        handleMessage(text, generation, webSocket);
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, ByteString bytes) {
                        if (!epochs.isCurrent(generation, webSocket)) return;
                        handleMessage(bytes.utf8(), generation, webSocket);
                    }

                    @Override
                    public void onClosing(WebSocket webSocket, int code, String reason) {
                        if (epochs.isCurrent(generation, webSocket))
                            status("Relay 正在断开 · " + code);
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        if (!epochs.ended(generation, webSocket)) return;
                        if (socket == webSocket) socket = null;
                        if (running) scheduleReconnect(generation, "连接关闭 " + code);
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        if (!epochs.ended(generation, webSocket)) return;
                        if (socket == webSocket) socket = null;
                        String detail = t == null ? "unknown" : t.getClass().getSimpleName();
                        if (response != null) detail += " · HTTP " + response.code();
                        if (running) scheduleReconnect(generation, detail);
                    }
                });
                if (!epochs.attach(generation, created)) {
                    created.close(1000, "obsolete");
                    return;
                }
                socket = created;
            } catch (Throwable failure) {
                if (epochs.failedBeforeAttach(generation))
                    scheduleReconnect(generation, "连接初始化失败");
            }
        }
    }

    private void scheduleReconnect(long generation, String reason) {
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
            // A stale timeout or a deliberate stop can never open another socket.
            if (epochs.claimReconnect(generation)) connect();
        }, "omnigate-relay-reconnect").start();
    }

    private void probePublicHealth(long generation, WebSocket owner) {
        requestPool.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(
                        publicBase + "/__relay_health").openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setUseCaches(false);
                if (conn.getResponseCode() != 200) {
                    statusIfCurrent(generation, owner, "Relay 已连，但公网探针失败 · HTTP " + conn.getResponseCode());
                    return;
                }

                String body;
                try (InputStream in = conn.getInputStream()) {
                    body = readSmall(in);
                }
                JSONObject json = new JSONObject(body);
                if (json.optBoolean("ok")
                        && json.optBoolean("deviceConnected")) {
                    statusIfCurrent(generation, owner, "公网可用 · gw-33e850180248.claua.de");
                } else {
                    statusIfCurrent(generation, owner, "Relay 已连，但公网尚未识别设备");
                }
            } catch (Throwable e) {
                statusIfCurrent(generation, owner,
                        "Relay 公网探针失败 · " + e.getClass().getSimpleName());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void statusIfCurrent(long generation, WebSocket owner, String value) {
        if (epochs.isCurrent(generation, owner)) status(value);
    }

    private void handleMessage(String text, long generation, WebSocket owner) {
        try {
            JSONObject msg = new JSONObject(text);
            if (!"req".equals(msg.optString("type"))) return;
            if (!epochs.isCurrent(generation, owner)) return;
            String requestId=msg.optString("id", "");
            if(requestId.isEmpty())return;
            if(!requestLimit.tryEnter()){
                send(new JSONObject().put("type","resError")
                    .put("id",requestId).put("status",503)
                    .put("message","relay_local_capacity_exceeded"),generation,owner);
                return;
            }
            try {
                requestPool.execute(() -> {
                    try {
                        if(epochs.isCurrent(generation, owner))
                            handleRequest(msg, generation, owner);
                    } finally {requestLimit.leave();}
                });
            } catch(java.util.concurrent.RejectedExecutionException stopped){
                requestLimit.leave();
                send(new JSONObject().put("type","resError")
                    .put("id",requestId).put("status",503)
                    .put("message","relay_local_executor_unavailable"),generation,owner);
            }
        } catch (Throwable ignored) {}
    }

    private void handleRequest(JSONObject msg, long generation, WebSocket owner) {
        String id = msg.optString("id", "");
        if (id.isEmpty()) return;

        // Track queue admission per response. Rejection cannot be followed
        // by a successful resEnd, regardless of original upstream status.
        RelayReplySendGate delivery=new RelayReplySendGate();
        RelayQueuePacer queuePacer=new RelayQueuePacer();
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
            delivery.recordStart(send(start, generation, owner));

            // The native SSE adapter can end unexpectedly without the final
            // OpenAI-compatible [DONE] marker. An HTTP EOF alone is not
            // evidence of a complete streaming assistant reply.
            RelaySseCompletionGate completion =
                    isTrackedChatSse(method, path, code, conn)
                    ? new RelaySseCompletionGate() : null;

            InputStream in = code >= 400
                    ? conn.getErrorStream()
                    : conn.getInputStream();
            if (in != null) {
                try (InputStream response = in) {
                    byte[] buffer = new byte[12 * 1024];
                    int n;
                    while ((n = response.read(buffer)) >= 0) {
                        if (n == 0) continue;
                        if (completion != null) completion.observe(buffer, n);
                        // On rejection, drain original stream without allocating
                        // further Relay chunks or generating a false terminal.
                        if(!delivery.canSendChunk())continue;
                        // Pause this worker before allocating another Base64
                        // frame; fail closed after prolonged queue congestion.
                        if(!awaitRelayQueueRoom(generation, owner, queuePacer)){
                            delivery.recordChunk(false);
                            continue;
                        }
                        byte[] chunk = new byte[n];
                        System.arraycopy(buffer, 0, chunk, 0, n);
                        JSONObject frame = new JSONObject()
                                .put("type", "resChunk")
                                .put("id", id)
                                .put("data", Base64.encodeToString(
                                        chunk,
                                        Base64.NO_WRAP));
                        delivery.recordChunk(send(frame, generation, owner));
                    }
                }
            }

            // A truncated native SSE stream must not be represented as a
            // successful resEnd. Preserve all prior chunks; do not retry,
            // cancel the original generation, or replay via a new socket.
            if(completion!=null && !completion.complete()){
                if(delivery.canSendError()){
                    delivery.recordError(send(new JSONObject()
                        .put("type","resError")
                        .put("id",id)
                        .put("status",502)
                        .put("message","upstream_sse_incomplete_or_invalid"),
                        generation,owner));
                }
                statusIfCurrent(generation,owner,
                    "Relay SSE结束校验失败 · 原请求未自动重发");
            }
            if(delivery.canSendEnd()){
                delivery.recordEnd(send(new JSONObject()
                    .put("type", "resEnd")
                    .put("id", id), generation, owner));
            }
            if(delivery.failed())
                statusIfCurrent(generation,owner,
                    "Relay 回复未完整入队 · 原模型已自然完成，未自动重发");
        } catch (Throwable e) {
            try {
                if(delivery.canSendError()){
                    delivery.recordError(send(new JSONObject()
                        .put("type", "resError")
                        .put("id", id)
                        .put("status", 502)
                        .put("message", "local_upstream_transport_failed"),
                            generation, owner));
                }
            } catch (Throwable ignored) {}
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Narrowly protect OpenAI-compatible chat SSE. Other HTTP endpoints
     * (including JSON and event streams without a required [DONE]) keep their
     * original transport behavior. Compressed upstream bodies cannot be
     * byte-inspected safely and are never assumed complete by this gate.
     */
    private static boolean isTrackedChatSse(
            String method, String path, int code, HttpURLConnection conn) {
        if (!"POST".equals(method) || code != 200 ||
                !("/v1/chat/completions".equals(path) ||
                  path.startsWith("/v1/chat/completions?"))) return false;
        String type=conn.getHeaderField("Content-Type");
        if(type==null || !type.toLowerCase(java.util.Locale.ROOT)
                .contains("text/event-stream"))return false;
        String coding=conn.getHeaderField("Content-Encoding");
        return coding==null || coding.trim().isEmpty() ||
            "identity".equalsIgnoreCase(coding.trim());
    }

    private boolean awaitRelayQueueRoom(
            long generation, WebSocket owner, RelayQueuePacer pacer) {
        for(;;){
            RelayQueuePacer.Decision decision=pacer.observe(
                owner.queueSize(),
                android.os.SystemClock.elapsedRealtime(),
                epochs.isCurrent(generation,owner));
            if(decision==RelayQueuePacer.Decision.READY)return true;
            if(decision!=RelayQueuePacer.Decision.WAIT)return false;
            try{Thread.sleep(75L);}
            catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private boolean send(JSONObject json, long generation, WebSocket owner) {
        // true means locally enqueued, not remotely delivered. Fail closed
        // if old socket is stale, or if OkHttp rejects this outgoing frame.
        if(!epochs.isCurrent(generation,owner))return false;
        return owner.send(json.toString());
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
