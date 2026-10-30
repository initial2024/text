package com.omnigate.integrated;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SidecarProxyServer {
    public static final int LISTEN_PORT = 8787;
    public static final int UPSTREAM_PORT = 1601; // fallback only; live port from Prefs
    public static final int MAX_BODY_BYTES = 64 * 1024 * 1024;
    public static final String PROBE_VALUE = "omnigate-sidecar-8787-v1";

    public interface StatusListener {
        void onStatus(String status);
    }

    private final ExecutorService pool = Executors.newCachedThreadPool();
    // One foreground native chat at a time can be safely correlated to a
    // verified DeepSeek editor. A competing request keeps the old proxy path.
    private static final java.util.concurrent.atomic.AtomicBoolean LIVE_DEEPSEEK_BUSY =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicLong NEXT_TRACE =
        new java.util.concurrent.atomic.AtomicLong();
    private static final ThreadLocal<ProxyTrace> CURRENT_TRACE=new ThreadLocal<>();
    private static final ThreadLocal<TrackedOutput> CURRENT_OUTPUT=new ThreadLocal<>();
    private static volatile ProxyTrace LAST_TRACE;

    /** Debug metadata only: no prompts, file names, tokens or response text. */
    private static final class ProxyTrace {
        final long id=NEXT_TRACE.incrementAndGet();
        final long started=android.os.SystemClock.elapsedRealtime();
        volatile String stage="request_received", upstreamKind="unknown",
            responseMode="not_sent", error="none";
        volatile int upstreamCode=-1, downstreamCode=-1;
        volatile boolean clientStream;
        JSONObject snapshot() throws org.json.JSONException {
            return new JSONObject().put("id",id).put("stage",stage)
                .put("upstreamKind",upstreamKind)
                .put("responseMode",responseMode).put("error",error)
                .put("upstreamHttp",upstreamCode).put("downstreamHttp",downstreamCode)
                .put("clientRequestedStream",clientStream)
                .put("ageMs",Math.max(0,
                    android.os.SystemClock.elapsedRealtime()-started));
        }
    }
    private static final class TrackedOutput extends BufferedOutputStream {
        private long logicalBytes;
        TrackedOutput(OutputStream raw){super(raw);}
        @Override public synchronized void write(int b)throws IOException{
            super.write(b);logicalBytes++;
        }
        @Override public synchronized void write(byte[] b,int off,int len)throws IOException{
            super.write(b,off,len);logicalBytes+=len;
        }
        boolean started(){return logicalBytes>0;}
    }
    private static void traceStage(String stage){
        ProxyTrace t=CURRENT_TRACE.get();if(t!=null)t.stage=stage;
    }
    private static void traceDownstream(int code,String mode){
        ProxyTrace t=CURRENT_TRACE.get();
        if(t!=null){t.downstreamCode=code;t.responseMode=mode;t.stage="response_started";}
    }
    private static JSONObject lastProxyTrace() throws org.json.JSONException {
        ProxyTrace t=LAST_TRACE;
        return t==null?new JSONObject().put("stage","none"):t.snapshot();
    }

    private final StatusListener listener;
    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public SidecarProxyServer(StatusListener listener) {
        this.listener = listener;
    }

    public synchronized boolean start() {
        if (isListening()) return true;

        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(
                    new InetSocketAddress(
                            InetAddress.getByName("127.0.0.1"),
                            LISTEN_PORT),
                    24);
            serverSocket = socket;
            running = true;

            Thread accept = new Thread(this::acceptLoop, "omnigate-sidecar-http");
            accept.setDaemon(true);
            accept.start();

            status("8787 已监听 · 上游 127.0.0.1:1601");
            return true;
        } catch (IOException e) {
            running = false;
            closeServerSocket();
            status("8787 启动失败 · " + shortError(e));
            return false;
        }
    }

    public synchronized boolean isListening() {
        return running
                && serverSocket != null
                && serverSocket.isBound()
                && !serverSocket.isClosed();
    }

    public synchronized void shutdown() {
        running = false;
        closeServerSocket();
        pool.shutdownNow();
    }

    private void closeServerSocket() {
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        serverSocket = null;
    }

    private void acceptLoop() {
        try {
            while (running) {
                ServerSocket listening = serverSocket;
                if (listening == null || listening.isClosed()) break;

                Socket socket = listening.accept();
                socket.setSoTimeout(30_000);
                pool.execute(() -> handle(socket));
            }
        } catch (IOException e) {
            if (running) status("8787 accept 异常 · " + shortError(e));
        } finally {
            synchronized (this) {
                running = false;
                closeServerSocket();
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket client = socket;
             BufferedInputStream in = new BufferedInputStream(client.getInputStream());
             TrackedOutput out = new TrackedOutput(client.getOutputStream())) {

            CURRENT_OUTPUT.set(out);
            Request request = readRequest(in);
            if (request == null) return;
            if("POST".equals(request.method) &&
                "/v1/chat/completions".equals(request.path)){
                ProxyTrace trace=new ProxyTrace();
                trace.clientStream=isStreamingChatRequest(request);
                LAST_TRACE=trace;
                CURRENT_TRACE.set(trace);
            }

            if ("GET".equals(request.method)
                    && ("/health".equals(request.path)
                    || "/__sidecar_health".equals(request.path))) {
                JSONObject observedOwner=OriginalCoreBridge.diagnoseVisibleHostSocket();
                JSONObject verifiedOwner=OriginalCoreBridge.diagnoseActiveNativeOwner();
                JSONObject overlayOwner=verifiedOwner.optJSONObject("overlayOwner");
                JSONObject ownerContinuity=
                    OriginalCoreBridge.inspectOwnerContinuity(observedOwner);
                writeJson(out, 200, new JSONObject()
                        .put("ok", true)
                        .put("service", "OmniGate Sidecar")
                        .put("listen", "127.0.0.1:8787")
                        .put("upstream", "127.0.0.1:" + OriginalCoreBridge.configuredPort())
                        .put("upstreamReachable", isUpstreamReachable())
                        .put("originalOwnerAlive", OriginalCoreBridge.originalHostAlive())
                        .put("lastAttachmentClientShape", AttachmentApiBridge.lastAttachmentShape())
                        .put("lastAttachmentClientPhase", AttachmentApiBridge.lastAttachmentPhase())
                        .put("lastProxyRequest",lastProxyTrace())
                        .put("originalServerRunning", OriginalCoreBridge.nativeOwnerRunning())
                        .put("originalLifecycle", OriginalCoreBridge.nativeLifecycleState())
                        .put("observedRuntimeRoute",
                            verifiedOwner.optString("route", "unknown"))
                        .put("verifiedNativeOwner",
                            verifiedOwner.optString("ownerKind", "unknown"))
                        .put("originalSocketOwner",
                            observedOwner.optString("socketOwner", "unknown"))
                        .put("originalOverlayActive",
                            observedOwner.opt("originalOverlayServiceActive"))
                        .put("originalOverlaySocketOwnerVerified",
                            overlayOwner != null && overlayOwner.optBoolean("verified", false))
                        .put("originalOverlaySocketOwnerReason",
                            overlayOwner == null ? "not_inspected" :
                            overlayOwner.optString("reason", "unknown"))
                        .put("originalOverlayCapturedInstance",
                            overlayOwner != null && overlayOwner.optBoolean("capturedInstance",false))
                        .put("originalOwnerHandoffStatus",
                            OriginalCoreBridge.handoffStatus())
                        .put("ownerContinuity",ownerContinuity)
                        .put("originalApiStartStatusHistorical",true)
                        .put("originalSocketBindScope",
                            observedOwner.optString("bindAddressScope", "unknown"))
                        .put("originalWebDriverIdentity",
                            observedOwner.optString("webDriverIdentity", "unknown"))
                        .put("portConflict", OriginalCoreBridge.isPortConflicted())
                        .put("originalApiStartStatus", OriginalCoreBridge.lastStartStatus())
                        .put("autoStartEnabled", GatewayApp.autoStartEnabled())
                        .put("backgroundKeepAliveEnabled",
                            GatewayApp.backgroundKeepAliveEnabled())
                        .put("autoStartStatus",AutoStartRuntime.status())
                        .put("sidecarForegroundServiceRunning",
                            SidecarService.isServiceAlive())
                        .put("publicRelay", GatewayApp.tunnelEnabled())
                        .put("publicRelayClientStarted", RelayRuntime.isStarted())
                        .put("publicRelayWebSocketConnected", RelayRuntime.isConnected())
                        .put("publicRelayStatus", GatewayApp.tunnelStatus()));
                return;
            }

            if ("GET".equals(request.method)
                    && "/__tunnel_probe".equals(request.path)) {
                writeJson(out, 200, new JSONObject()
                        .put("probe", PROBE_VALUE)
                        .put("upstreamReachable", isUpstreamReachable()));
                return;
            }

            if (!request.path.startsWith("/v1/")) {
                writeJson(out, 404, new JSONObject()
                        .put("error", "not_found")
                        .put("message", "OmniGate Sidecar only proxies /v1/* to the frozen original core."));
                return;
            }

            boolean publicRequest =
                    "1".equals(cleanHeader(request.headers.get("x-omnigate-public-relay")));

            if (publicRequest) {
                if (!exactPublicKey(request)) {
                    writeJson(out, 401, new JSONObject()
                            .put("error", "unauthorized")
                            .put("message", "Invalid OmniGate public API key")
                            .put("authMode", "public-exact"));
                    return;
                }
            } else if (!hasAnyLocalKey(request)) {
                writeJson(out, 401, new JSONObject()
                        .put("error", "unauthorized")
                        .put("message", "API key required")
                        .put("authMode", "localhost-any-nonempty"));
                return;
            }

            // Independently routed official Claude API. It does NOT impersonate
            // claude.ai or share the original Gecko driver. Never expose the
            // caller's Anthropic credential through the public relay path.
            if("POST".equals(request.method) &&
               "/v1/chat/completions".equals(request.path) &&
               ClaudeDirectAdapter.isClaude(request.body)) {
                if(publicRequest){
                    writeJson(out,403,new JSONObject()
                        .put("error","claude_public_relay_disabled")
                        .put("message","Claude API calls are local-only"));
                    return;
                }
                ClaudeDirectAdapter.Result answer=ClaudeDirectAdapter.invoke(
                    request.body,cleanHeader(request.headers.get("authorization")));
                boolean stream=false;
                try{stream=new JSONObject(new String(request.body,
                    StandardCharsets.UTF_8)).optBoolean("stream",false);}
                catch(Exception ignored){}
                if(stream && answer.status==200)
                    writeVerifiedAttachmentSse(out,answer.json);
                else writeJson(out,answer.status,answer.json);
                return;
            }

            if (OriginalCoreBridge.isPortConflicted()) {
                writeJson(out, 409, new JSONObject()
                    .put("error", "native_proxy_port_conflict")
                    .put("message", "Native API is configured to the same port 8787 as the proxy")
                    .put("nativePort", OriginalCoreBridge.configuredPort())
                    .put("proxyPort", LISTEN_PORT)
                    .put("hint", "Tap Repair conflict and start native API in OmniGate Settings"));
                return;
            }
            if (!isUpstreamReachable()) {
                writeJson(out, 503, new JSONObject()
                    .put("error", "native_api_unavailable")
                    .put("message", "Original AI Gateway native API is not listening")
                    .put("expectedPort", OriginalCoreBridge.configuredPort())
                    .put("hint", "Open OmniGate API settings and explicitly start native API"));
                return;
            }

            // Both the visible MainActivity and the original overlay can
            // legitimately own their own API and WebDriver. The overlay is
            // accepted ONLY if Android's captured exact Service instance
            // holds the live loopback socket and the same driver as ApiServer.
            // Static isRunning or TCP connectivity is NEVER enough.
            if ("POST".equals(request.method)
                    && request.path.startsWith("/v1/chat/completions")) {
                JSONObject currentOwner=OriginalCoreBridge.diagnoseActiveNativeOwner();
                if (!currentOwner.optBoolean("verified", false)
                        && !OriginalCoreBridge.originalOverlayActive()) {
                    // Preserve the previous narrow, verified same-MainActivity
                    // reference recovery; never recover an overlay by guessing.
                    String recovered=OriginalCoreBridge
                        .recoverTrackedMainServerReference();
                    if(recovered.startsWith("PASS"))
                        currentOwner=OriginalCoreBridge.diagnoseActiveNativeOwner();
                }
                if(!currentOwner.optBoolean("verified", false)){
                    JSONObject overlay=currentOwner.optJSONObject("overlayOwner");
                    JSONObject main=OriginalCoreBridge.diagnoseVisibleHostSocket();
                    writeJson(out,409,new JSONObject()
                        .put("error","original_runtime_owner_mismatch")
                        .put("message","Neither the original main nor overlay runtime "+
                            "has a verified live API socket with the same WebDriver; "+
                            "request not sent")
                        .put("observedRuntimeRoute",
                            currentOwner.optString("route", "unknown"))
                        .put("originalSocketOwner",
                            main.optString("socketOwner","unknown"))
                        .put("originalWebDriverIdentity",
                            main.optString("webDriverIdentity","unknown"))
                        .put("originalOverlayServiceActive",
                            OriginalCoreBridge.originalOverlayActive())
                        .put("originalOverlaySocketOwnerVerified",
                            overlay != null && overlay.optBoolean("verified", false))
                        .put("originalOverlayOwnerReason",
                            overlay == null ? "not_inspected" :
                            overlay.optString("reason","unknown"))
                        .put("hint","Keep the generating browser alive. "+
                            "Inspect /health; never force handoff or blindly "+
                            "route to an unknown listener."));
                    return;
                }
            }

            // Only validated in-band image/document content enters the
            // attachment bridge. Other requests remain on the frozen core.
            if ("POST".equals(request.method)
                    && "/v1/chat/completions".equals(request.path)) {
                AttachmentApiBridge.Prepared prepared;
                try {
                    prepared=AttachmentApiBridge.prepare(request.body);
                } catch(IllegalArgumentException invalid) {
                    writeJson(out,422,new JSONObject()
                        .put("error",new JSONObject()
                            .put("type","invalid_request_error")
                            .put("code",invalid.getMessage())
                            .put("message","Unsupported or oversized inline attachment request")));
                    AttachmentApiBridge.noteFailure("parse_rejected");
                    status("附件解析拒绝 · 未发送文本回退");
                    return;
                } catch(Exception malformed) {
                    AttachmentApiBridge.noteFailure("malformed_attachment_envelope");
                    writeJson(out,422,new JSONObject()
                        .put("error",new JSONObject()
                            .put("type","invalid_request_error")
                            .put("code","malformed_attachment_envelope")));
                    return;
                }
                if(prepared!=null) {
                    // Older media must not block a newly supported attachment
                    // upload. Redact only historical binaries; preserve the
                    // actual current-turn attachment and explicit provenance.
                    try {
                        byte[] filtered=AttachmentApiBridge.sanitizeHistoricalMedia(
                            prepared.requestBody);
                        if(filtered!=null)
                            prepared=new AttachmentApiBridge.Prepared(filtered,
                                prepared.requestId,prepared.model,
                                prepared.clientRequestedStream);
                    }catch(IllegalArgumentException unsafeHistory){
                        AttachmentApiBridge.noteFailure("historical_media_ambiguous");
                        writeJson(out,422,new JSONObject()
                            .put("error",new JSONObject()
                                .put("type","invalid_request_error")
                                .put("code","historical_media_ambiguous")
                                .put("message","Prior attachment data contains mixed "+
                                    "unrecognized content. No current media or text was "+
                                    "sent; retry with a fresh text-only context.")));
                        return;
                    }
                    AttachmentApiBridge.Result result=
                        AttachmentApiBridge.execute(prepared);
                    if(result.http==200 && prepared.clientRequestedStream) {
                        writeVerifiedAttachmentSse(out,result.body);
                    } else {
                        writeJson(out,result.http,result.body);
                    }
                    return;
                }
                if(AttachmentApiBridge.hasAttachmentSignal(request.body)){
                    AttachmentApiBridge.noteFailure("unsupported_client_attachment_schema");
                    writeJson(out,422,new JSONObject()
                        .put("error",new JSONObject()
                            .put("type","invalid_request_error")
                            .put("code","unsupported_client_attachment_schema")
                            .put("message","Image/file is present in request but does not match a "+
                                "supported inline format. Nothing was sent to the website.")));
                    return;
                }
            }
            if("POST".equals(request.method) &&
                "/v1/responses".equals(request.path) &&
                AttachmentApiBridge.hasAttachmentSignal(request.body)){
                AttachmentApiBridge.noteFailure("responses_attachment_not_supported");
                writeJson(out,422,new JSONObject()
                    .put("error",new JSONObject()
                        .put("type","invalid_request_error")
                        .put("code","responses_attachment_not_supported")
                        .put("message","Multimodal Responses API payloads require a "+
                            "separate adapter. No text-only fallback was sent.")));
                return;
            }

            // Old attachments are not fresh upload attempts. A client may
            // send old media again as history while asking a completely
            // independent text question. Remove only opaque historical media,
            // mark it unavailable, and continue the current text task.
            // Current unsupported media has already been rejected above.
            if("POST".equals(request.method) &&
                    "/v1/chat/completions".equals(request.path)){
                try {
                    byte[] textSafe=AttachmentApiBridge.sanitizeHistoricalMedia(
                        request.body);
                    if(textSafe!=null)
                        request=new Request(request.method,request.path,
                            request.version,request.headers,textSafe);
                }catch(IllegalArgumentException unsafeHistory){
                    AttachmentApiBridge.noteFailure("historical_media_ambiguous");
                    writeJson(out,422,new JSONObject()
                        .put("error",new JSONObject()
                            .put("type","invalid_request_error")
                            .put("code","historical_media_ambiguous")
                            .put("message","Unsupported mixed prior attachment "+
                                "content; this text request was not dispatched.")));
                    return;
                }catch(Exception malformedHistory){
                    AttachmentApiBridge.noteFailure("historical_media_parse_failed");
                    writeJson(out,422,new JSONObject()
                        .put("error",new JSONObject()
                            .put("type","invalid_request_error")
                            .put("code","historical_media_parse_failed")));
                    return;
                }
            }

            // Keep ordinary requests unchanged. Minis' known non-streaming
            // auto-title system instruction is translated into ONE explicit
            // user message, so the webpage can really submit it. No fake
            // title is generated locally and no automatic retry is allowed.
            boolean titleRequest=false;
            if("POST".equals(request.method) &&
                    "/v1/chat/completions".equals(request.path)){
                try{
                    TitleCompat.Prepared title=TitleCompat.prepare(request.body);
                    if(title!=null){
                        request=new Request(request.method,request.path,
                            request.version,request.headers,title.body);
                        titleRequest=true;
                    }
                }catch(org.json.JSONException invalid){
                    writeJson(out,422,new JSONObject()
                        .put("error","title_request_rejected")
                        .put("message",invalid.getMessage()));
                    return;
                }
            }
            proxyToFrozenCore(request, out, titleRequest);
        } catch (Throwable e) {
            ProxyTrace trace=CURRENT_TRACE.get();
            if(trace!=null){
                trace.error=e.getClass().getSimpleName();
                trace.stage="transport_exception";
            }
            status("Sidecar 请求异常 · " + shortError(e));
            // Previously an exception before the first HTTP header could
            // silently close the socket, producing DSH [0] no reply.
            // Never write a second response if bytes have already started.
            try{
                TrackedOutput active=CURRENT_OUTPUT.get();
                if(active!=null && !active.started()){
                    writeJson(active,502,new JSONObject().put("error",
                        new JSONObject().put("type","omnigate_transport_error")
                            .put("code","native_response_transport_failed")
                            .put("cause",e.getClass().getSimpleName())));
                }
            }catch(Throwable ignored){}
        } finally {
            ProxyTrace trace=CURRENT_TRACE.get();
            if(trace!=null && "response_started".equals(trace.stage))
                trace.stage="response_returned";
            CURRENT_TRACE.remove();
            CURRENT_OUTPUT.remove();
        }
    }

    private void proxyToFrozenCore(Request request, BufferedOutputStream clientOut,
            boolean titleRequest) throws IOException, org.json.JSONException {
        HttpURLConnection upstream = null;
        InputStream upstreamBody = null;
        LiveDeepSeekAnchor live=null;

        try {
            if (OriginalCoreBridge.isPortConflicted())
                throw new IOException("native_proxy_port_conflict: 8787 -> 8787 prohibited");
            live = maybeCaptureLiveDeepSeek(request);
            URL url = new URL(
                    "http://127.0.0.1:"
                            + OriginalCoreBridge.configuredPort()
                            + request.path);
            upstream = (HttpURLConnection) url.openConnection();
            upstream.setRequestMethod(request.method);
            upstream.setConnectTimeout(10_000);

            // Deliberately no response/read timeout. The original provider page owns
            // generation completion. Sidecar must not cut a long/thinking answer short.
            upstream.setReadTimeout(0);
            upstream.setInstanceFollowRedirects(false);
            upstream.setUseCaches(false);

            for (Map.Entry<String, String> entry : request.headers.entrySet()) {
                String name = entry.getKey();
                String lower = name.toLowerCase(Locale.ROOT);
                if (isHopByHop(lower)
                        || "host".equals(lower)
                        || "content-length".equals(lower)
                        || "authorization".equals(lower)
                        || "x-api-key".equals(lower)
                        || "x-dsh-gateway-token".equals(lower)
                        || "x-omnigate-public-relay".equals(lower)) {
                    continue;
                }
                upstream.setRequestProperty(name, entry.getValue());
            }

            String upstreamKey = GatewayApp.upstreamApiKey();
            if (upstreamKey.isEmpty()) upstreamKey = OriginalCoreBridge.originalApiKey();
            if (!upstreamKey.isEmpty()) {
                upstream.setRequestProperty(
                        "Authorization",
                        "Bearer " + upstreamKey);
            }

            byte[] nativeBody=request.body;
            if(live!=null) {
                try {
                    // Native ApiServer generally waits for completion and
                    // returns JSON. DSH still receives stream:true SSE here.
                    JSONObject nativeRequest=new JSONObject(new String(
                        request.body,StandardCharsets.UTF_8));
                    nativeRequest.put("stream",false);
                    nativeBody=nativeRequest.toString().getBytes(StandardCharsets.UTF_8);
                }catch(Exception bad){
                    LIVE_DEEPSEEK_BUSY.set(false);
                    live=null;
                    nativeBody=request.body;
                }
            }
            if (nativeBody.length > 0) {
                upstream.setDoOutput(true);
                upstream.setFixedLengthStreamingMode(nativeBody.length);
                try (OutputStream bodyOut = upstream.getOutputStream()) {
                    bodyOut.write(nativeBody);
                    bodyOut.flush();
                }
            }
            if(live!=null){
                relayVerifiedDeepSeekLive(upstream,clientOut,live);
                return;
            }

            traceStage("waiting_native_response_headers");
            int statusCode = upstream.getResponseCode();
            ProxyTrace trace=CURRENT_TRACE.get();
            if(trace!=null)trace.upstreamCode=statusCode;
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            for (Map.Entry<String, java.util.List<String>> e
                    : upstream.getHeaderFields().entrySet()) {
                if (e.getKey() == null
                        || e.getValue() == null
                        || e.getValue().isEmpty()) {
                    continue;
                }
                String lower = e.getKey().toLowerCase(Locale.ROOT);
                if (isHopByHop(lower)
                        || "content-length".equals(lower)) {
                    continue;
                }
                responseHeaders.put(e.getKey(), e.getValue().get(0));
            }

            upstreamBody = statusCode >= 400
                    ? upstream.getErrorStream()
                    : upstream.getInputStream();
            if(trace!=null){
                trace.upstreamKind=isPlainSseResponse(responseHeaders)?
                    "sse":isJsonResponse(responseHeaders)?"json":"other";
                trace.stage="upstream_headers_received";
            }

            // The original native core may return a completed JSON object
            // even when the OpenAI-compatible client asked for stream:true.
            // Passing that JSON to an SSE parser yields a UI-level "no reply".
            // Convert only a VERIFIED completed JSON message, including any
            // tool calls, into well-formed Chat Completions SSE events.
            if (upstreamBody != null && statusCode == 200 &&
                "POST".equals(request.method) &&
                "/v1/chat/completions".equals(request.path) &&
                isStreamingChatRequest(request) &&
                isJsonResponse(responseHeaders)) {
                proxyCompletedJsonAsSse(upstreamBody, clientOut);
                return;
            }

            // Streaming replies can have an artificial "ChatGPT 说：" label
            // split across SSE delta frames. Remove ONLY that leading label.
            // Never change reasoning, tool calls, later content or stream timing
            // after the first speaker prefix has been decided.
            if (upstreamBody != null && statusCode == 200 &&
                "POST".equals(request.method) &&
                "/v1/chat/completions".equals(request.path) &&
                isStreamingChatRequest(request) &&
                isPlainSseResponse(responseHeaders)) {
                proxyNormalizedSseReply(upstreamBody, clientOut, statusCode,
                    responseHeaders);
                return;
            }

            // The normalized JSON branch owns the complete downstream response,
            // including exactly one HTTP status/header block. Previously a
            // chunked 200 head was written here first and the JSON branch wrote
            // a second head into the body, corrupting non-streaming replies.
            // Keep the original upstream draining even if the client disconnects.
            if (upstreamBody != null && statusCode == 200 &&
                "POST".equals(request.method) &&
                "/v1/chat/completions".equals(request.path) &&
                isNonStreamingChatRequest(request) &&
                isJsonResponse(responseHeaders)) {
                proxyNormalizedJsonReply(upstreamBody, clientOut, statusCode,
                        responseHeaders, titleRequest);
                return;
            }

            boolean downstreamOpen = true;
            try {
                writeChunkedResponseHead(clientOut, statusCode, responseHeaders);
            } catch (IOException downstreamClosed) {
                // A disconnected Minis/DSH client must never cancel provider work.
                downstreamOpen = false;
                status("客户端已断开 · 原版网页回答继续后台完成");
            }

            if (upstreamBody == null) {
                if (downstreamOpen) {
                    try {
                        writeFinalChunk(clientOut);
                    } catch (IOException ignored) {
                    }
                }
                return;
            }

            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = upstreamBody.read(buffer)) >= 0) {
                if (n == 0) continue;

                if (downstreamOpen) {
                    try {
                        writeChunk(clientOut, buffer, n);
                    } catch (IOException downstreamClosed) {
                        downstreamOpen = false;
                        status("客户端已断开 · 原 1601 回答继续后台完成");
                    }
                }
                // If downstream is gone, intentionally continue reading/discarding
                // until upstream EOF. This protects the original app from an avoidable
                // client_disconnected cancellation cascade.
            }

            if (downstreamOpen) {
                try {
                    writeFinalChunk(clientOut);
                } catch (IOException ignored) {
                }
            }
        } finally {
            if (upstreamBody != null) {
                try {
                    upstreamBody.close();
                } catch (IOException ignored) {
                }
            }
            if (upstream != null) {
                upstream.disconnect();
            }
            if(live!=null)LIVE_DEEPSEEK_BUSY.set(false);
        }
    }

    public static boolean isUpstreamReachable() {
        // Reject loopback self-proxy even though 127.0.0.1:8787 is listening.
        return OriginalCoreBridge.isListening();
    }

    private boolean hasAnyLocalKey(Request request) {
        String auth = cleanHeader(request.headers.get("authorization"));
        if (!auth.isEmpty()) {
            int space = auth.indexOf(' ');
            if (space > 0) {
                String scheme = auth.substring(0, space).trim();
                String token = cleanHeader(auth.substring(space + 1));
                if ("bearer".equalsIgnoreCase(scheme) && !token.isEmpty()) {
                    return true;
                }
            } else {
                return true;
            }
        }

        return !cleanHeader(request.headers.get("x-api-key")).isEmpty()
                || !cleanHeader(request.headers.get("x-dsh-gateway-token")).isEmpty();
    }

    private boolean exactPublicKey(Request request) {
        String expected = GatewayApp.apiKey();
        if (expected.isEmpty()) return false;

        String auth = cleanHeader(request.headers.get("authorization"));
        if (expected.equals(auth)) return true;

        int space = auth.indexOf(' ');
        if (space > 0
                && "bearer".equalsIgnoreCase(auth.substring(0, space).trim())
                && expected.equals(cleanHeader(auth.substring(space + 1)))) {
            return true;
        }

        return expected.equals(cleanHeader(request.headers.get("x-api-key")))
                || expected.equals(
                        cleanHeader(request.headers.get("x-dsh-gateway-token")));
    }

    private Request readRequest(BufferedInputStream in) throws IOException {
        String requestLine = readLine(in, 16 * 1024);
        if (requestLine == null || requestLine.trim().isEmpty()) return null;

        String[] first = requestLine.split(" ", 3);
        if (first.length < 2) {
            throw new IOException("invalid HTTP request line");
        }

        String method = first[0].trim().toUpperCase(Locale.ROOT);
        String path = first[1].trim();
        String version = first.length >= 3 ? first[2].trim() : "HTTP/1.1";

        Map<String, String> headers = new LinkedHashMap<>();
        int headerBytes = requestLine.length();

        while (true) {
            String line = readLine(in, 32 * 1024);
            if (line == null) throw new EOFException("unexpected EOF in headers");
            headerBytes += line.length();
            if (headerBytes > 96 * 1024) {
                throw new IOException("headers too large");
            }
            if (line.isEmpty()) break;

            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            headers.put(name, value);
        }

        byte[] body = new byte[0];
        String transferEncoding =
                cleanHeader(headers.get("transfer-encoding")).toLowerCase(Locale.ROOT);

        if (transferEncoding.contains("chunked")) {
            body = readChunkedBody(in);
        } else {
            int contentLength = parseContentLength(headers.get("content-length"));
            if (contentLength > MAX_BODY_BYTES) {
                throw new IOException("request body exceeds 64 MiB");
            }
            if (contentLength > 0) {
                body = readExact(in, contentLength);
            }
        }

        return new Request(method, path, version, headers, body);
    }

    private byte[] readChunkedBody(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        while (true) {
            String sizeLine = readLine(in, 1024);
            if (sizeLine == null) throw new EOFException("chunk size EOF");
            int semi = sizeLine.indexOf(';');
            String sizeHex = (semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim();

            int size;
            try {
                size = Integer.parseInt(sizeHex, 16);
            } catch (NumberFormatException e) {
                throw new IOException("invalid chunk size", e);
            }

            if (size == 0) {
                while (true) {
                    String trailer = readLine(in, 32 * 1024);
                    if (trailer == null || trailer.isEmpty()) break;
                }
                break;
            }

            if (size < 0 || out.size() + size > MAX_BODY_BYTES) {
                throw new IOException("chunked request body exceeds 64 MiB");
            }

            out.write(readExact(in, size));
            String after = readLine(in, 4);
            if (after == null) throw new EOFException("chunk terminator EOF");
        }

        return out.toByteArray();
    }

    private byte[] readExact(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(data, offset, length - offset);
            if (n < 0) throw new EOFException("unexpected body EOF");
            offset += n;
        }
        return data;
    }

    private String readLine(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int previous = -1;

        while (out.size() <= maxBytes) {
            int b = in.read();
            if (b < 0) {
                if (out.size() == 0) return null;
                break;
            }

            if (previous == '\r' && b == '\n') {
                byte[] raw = out.toByteArray();
                int len = raw.length > 0 && raw[raw.length - 1] == '\r'
                        ? raw.length - 1
                        : raw.length;
                return new String(raw, 0, len, StandardCharsets.ISO_8859_1);
            }

            out.write(b);
            previous = b;
        }

        if (out.size() > maxBytes) {
            throw new IOException("HTTP line too large");
        }

        return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private int parseContentLength(String value) throws IOException {
        String v = cleanHeader(value);
        if (v.isEmpty()) return 0;
        try {
            long length = Long.parseLong(v);
            if (length < 0 || length > Integer.MAX_VALUE) {
                throw new IOException("invalid content-length");
            }
            return (int) length;
        } catch (NumberFormatException e) {
            throw new IOException("invalid content-length", e);
        }
    }

    private static boolean isStreamingChatRequest(Request request) {
        if(request.body==null || request.body.length>512*1024)return false;
        try {
            return new JSONObject(new String(request.body,
                StandardCharsets.UTF_8)).optBoolean("stream",false);
        } catch (Exception ignored) { return false; }
    }
    private static boolean isPlainSseResponse(Map<String,String> headers) {
        boolean sse=false;
        for(Map.Entry<String,String> entry:headers.entrySet()){
            if("content-type".equalsIgnoreCase(entry.getKey()))
                sse=entry.getValue().toLowerCase(Locale.ROOT)
                    .contains("text/event-stream");
            if("content-encoding".equalsIgnoreCase(entry.getKey()) &&
                !"identity".equalsIgnoreCase(entry.getValue()))return false;
        }
        return sse;
    }

    /** Stream SSE in event order. Hold at most the first 32 events/64 KiB
     * solely while deciding whether the first text delta is an artificial
     * speaker label. Once decided, all remaining bytes pass unchanged.
     * Client disappearance is NOT a signal to stop the original model.
     */
    private void proxyNormalizedSseReply(InputStream upstream,
            BufferedOutputStream clientOut,int statusCode,
            Map<String,String> headers) throws IOException {
        boolean clientOpen=true;
        try { writeChunkedResponseHead(clientOut,statusCode,headers); }
        catch(IOException closed){clientOpen=false;}
        SseSpeakerPrefixFilter filter=new SseSpeakerPrefixFilter();
        ByteArrayOutputStream frame=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];
        int n;
        boolean previousNewline=false;
        boolean passthrough=false;
        while((n=upstream.read(buf))!=-1){
            if(n==0)continue;
            if(!clientOpen)continue; // drain provider to natural EOF
            if(passthrough){
                try{writeChunk(clientOut,buf,n);}
                catch(IOException closed){clientOpen=false;}
                continue;
            }
            for(int i=0;i<n;i++){
                int ch=buf[i]&0xff;
                frame.write(ch);
                if(ch=='\n'){
                    if(previousNewline){
                        byte[] event=frame.toByteArray();
                        frame.reset();
                        previousNewline=false;
                        for(byte[] ready:filter.accept(event)){
                            if(!clientOpen)break;
                            try{writeChunk(clientOut,ready,ready.length);}
                            catch(IOException closed){clientOpen=false;}
                        }
                        if(filter.resolved()){
                            passthrough=true;
                            if(clientOpen && i+1<n)
                                try{writeChunk(clientOut,
                                    java.util.Arrays.copyOfRange(buf,i+1,n),n-i-1);}
                                catch(IOException closed){clientOpen=false;}
                            break;
                        }
                    }else previousNewline=true;
                }else if(ch!='\r')previousNewline=false;
                if(frame.size()>64*1024){
                    for(byte[] ready:filter.finish()){
                        if(clientOpen)try{writeChunk(clientOut,ready,ready.length);}
                        catch(IOException closed){clientOpen=false;}
                    }
                    if(clientOpen){
                        byte[] raw=frame.toByteArray();
                        try{writeChunk(clientOut,raw,raw.length);}
                        catch(IOException closed){clientOpen=false;}
                    }
                    frame.reset();
                    passthrough=true;
                    if(clientOpen && i+1<n)
                        try{writeChunk(clientOut,
                            java.util.Arrays.copyOfRange(buf,i+1,n),n-i-1);}
                        catch(IOException closed){clientOpen=false;}
                    break;
                }
            }
        }
        if(!passthrough && clientOpen){
            for(byte[] ready:filter.finish())
                try{writeChunk(clientOut,ready,ready.length);}
                catch(IOException closed){clientOpen=false;break;}
            if(clientOpen && frame.size()>0){
                byte[] tail=frame.toByteArray();
                try{writeChunk(clientOut,tail,tail.length);}
                catch(IOException closed){clientOpen=false;}
            }
        }
        if(clientOpen)try{writeFinalChunk(clientOut);}
        catch(IOException ignored){}
    }

    /**
     * A non-streaming original response must not be sent as raw JSON to
     * a stream:true Minis/DSH client. Read the real upstream response first;
     * never invent text, a tool call, or a premature [DONE].
     * Limit retained content and drain oversize responses to EOF.
     */
    private void proxyCompletedJsonAsSse(InputStream upstream,
            BufferedOutputStream downstream)
            throws IOException, org.json.JSONException {
        ByteArrayOutputStream captured=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];int n;boolean oversized=false;
        while((n=upstream.read(buf))!=-1){
            if(n<=0)continue;
            if(captured.size()+n>2*1024*1024)oversized=true;
            if(!oversized)captured.write(buf,0,n);
        }
        if(oversized){
            writeJson(downstream,502,new JSONObject()
                .put("error",new JSONObject().put("type","omnigate_upstream_error")
                    .put("code","original_json_reply_exceeds_safe_limit")));
            return;
        }
        JSONObject completed;
        try {
            completed=new JSONObject(new String(captured.toByteArray(),
                StandardCharsets.UTF_8));
        }catch(Exception malformed){
            writeJson(downstream,502,new JSONObject()
                .put("error",new JSONObject().put("type","omnigate_upstream_error")
                    .put("code","original_json_reply_invalid")));
            return;
        }
        JSONArray choices=completed.optJSONArray("choices");
        JSONObject choice=choices==null?null:choices.optJSONObject(0);
        JSONObject message=choice==null?null:choice.optJSONObject("message");
        Object content=message==null?null:message.opt("content");
        JSONArray calls=message==null?null:message.optJSONArray("tool_calls");
        if((!(content instanceof String) ||
            ((String)content).trim().isEmpty()) &&
            (calls==null||calls.length()==0)){
            writeJson(downstream,502,new JSONObject()
                .put("error",new JSONObject().put("type","omnigate_upstream_error")
                    .put("code","original_json_no_assistant_content")
                    .put("message","The original API returned no assistant text or tool calls.")));
            return;
        }
        String id=completed.optString("id","chatcmpl-omnigate-completed");
        String model=completed.optString("model","chatgpt-web");
        long created=completed.optLong("created",System.currentTimeMillis()/1000L);
        String reason=choice.optString("finish_reason","stop");
        if(!("stop".equals(reason)||"length".equals(reason)||
            "tool_calls".equals(reason)))reason="stop";
        Map<String,String> headers=new LinkedHashMap<>();
        headers.put("Content-Type","text/event-stream; charset=utf-8");
        headers.put("Cache-Control","no-cache");
        headers.put("X-OmniGate-Stream-Mode","completed-original-json-adapted");
        writeChunkedResponseHead(downstream,200,headers);

        JSONObject firstDelta=new JSONObject().put("role","assistant");
        if(content instanceof String && !((String)content).isEmpty()){
            firstDelta.put("content",TitleCompat.stripSpeakerPrefix((String)content));
        }
        if(calls!=null && calls.length()>0) {
            // OpenAI permits tool_calls deltas with function names/arguments.
            // They are copied verbatim; never synthesize a tool invocation.
            firstDelta.put("tool_calls",calls);
        }
        JSONObject first=new JSONObject()
            .put("id",id).put("object","chat.completion.chunk")
            .put("created",created).put("model",model)
            .put("choices",new JSONArray().put(new JSONObject()
                .put("index",0).put("delta",firstDelta)
                .put("finish_reason",JSONObject.NULL)));
        writeSseData(downstream,first.toString());
        JSONObject finalChunk=new JSONObject()
            .put("id",id).put("object","chat.completion.chunk")
            .put("created",created).put("model",model)
            .put("choices",new JSONArray().put(new JSONObject()
                .put("index",0).put("delta",new JSONObject())
                .put("finish_reason",reason)));
        writeSseData(downstream,finalChunk.toString());
        writeSseData(downstream,"[DONE]");
        writeFinalChunk(downstream);
    }


    private static final class LiveDeepSeekAnchor {
        final int submittedBefore;
        final String oldAnswer;
        final String host;
        LiveDeepSeekAnchor(int seq,String old,String h){
            submittedBefore=seq;oldAnswer=old;host=h;
        }
    }
    /** Never use the speculative DOM stream for attachment requests,
     * tool-calls, other providers, non-stream clients or an active generation.
     * The original native response route remains the default.
     */
    private static LiveDeepSeekAnchor maybeCaptureLiveDeepSeek(Request req){
        if(!"POST".equals(req.method) ||
            !"/v1/chat/completions".equals(req.path) ||
            !isStreamingChatRequest(req) ||
            req.body.length>256*1024 || req.body.length<5 ||
            !LIVE_DEEPSEEK_BUSY.compareAndSet(false,true))return null;
        try {
            JSONObject root=new JSONObject(new String(req.body,StandardCharsets.UTF_8));
            if(root.has("functions")||
               root.has("attachments")||root.has("files")||root.has("images") ||
               AttachmentApiBridge.hasAttachmentSignal(req.body) ||
               !"deepseek-chat".equals(root.optString("model",""))) {
                LIVE_DEEPSEEK_BUSY.set(false);return null;
            }
            JSONArray messages=root.optJSONArray("messages");
            JSONObject last=messages==null||messages.length()==0?null:
                messages.optJSONObject(messages.length()-1);
            if(last==null||!"user".equals(last.optString("role","")) ||
               !(last.opt("content") instanceof String)){
                LIVE_DEEPSEEK_BUSY.set(false);return null;
            }
            JSONObject route=OriginalCoreBridge.diagnoseActiveNativeOwner();
            if(!route.optBoolean("verified",false) ||
               !("main".equals(route.optString("ownerKind","")) ||
                 "overlay".equals(route.optString("ownerKind","")))){
                LIVE_DEEPSEEK_BUSY.set(false);return null;
            }
            JSONObject page=OriginalCoreBridge.diagnoseVerifiedApiPage();
            JSONObject state=OriginalCoreBridge.inspectVerifiedApiAnswer();
            if(!"deepseek".equalsIgnoreCase(page.optString("provider","")) ||
               state.optBoolean("generating",true) ||
               page.optBoolean("generationRaw",true) ||
               page.optBoolean("autoRolloverActive",false) ||
               page.optInt("lastSubmissionSequence",-1)<0) {
                LIVE_DEEPSEEK_BUSY.set(false);return null;
            }
            return new LiveDeepSeekAnchor(
                page.optInt("lastSubmissionSequence",0),
                state.optString("answer",""),
                route.optString("ownerKind",""));
        }catch(Throwable unavailable){
            LIVE_DEEPSEEK_BUSY.set(false);return null;
        }
    }

    private static final class LiveOriginalResult {
        int httpCode=-1;
        String mime="";
        byte[] payload=new byte[0];
        boolean oversized;
    }

    /**
     * Native ApiServer may return its JSON only after Gecko finishes a
     * generation. Poll the original verified WebDriver in the SAME session
     * while the native request continues in a separate worker. Publish only
     * newly confirmed, append-only DeepSeek answer characters, never thought
     * blocks, fabricated tokens or previous-session text.
     *
     * If no safe progress is observable, deliver the original completed JSON
     * through the known-good JSON -> SSE adapter. This path does not change
     * non-stream requests or the upstream model's lifecycle.
     */
    private void relayVerifiedDeepSeekLive(HttpURLConnection upstream,
            BufferedOutputStream downstream,LiveDeepSeekAnchor anchor)
            throws IOException,org.json.JSONException {
        final java.util.concurrent.Future<LiveOriginalResult> nativeReply=pool.submit(()->{
            LiveOriginalResult result=new LiveOriginalResult();
            result.httpCode=upstream.getResponseCode();
            String ct=upstream.getContentType();
            result.mime=ct==null?"":ct.toLowerCase(Locale.ROOT);
            try(InputStream input=result.httpCode>=400?
                    upstream.getErrorStream():upstream.getInputStream()){
                if(input==null)return result;
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                byte[] block=new byte[8192];int n;
                while((n=input.read(block))!=-1){
                    if(n<=0)continue;
                    if(bytes.size()+n>2*1024*1024)result.oversized=true;
                    if(!result.oversized)bytes.write(block,0,n);
                }
                result.payload=bytes.toByteArray();
            }
            return result;
        });

        boolean sentHeader=false,clientOpen=true,sessionMismatch=false;
        String delivered="";
        int iterations=0;
        // The native worker always drains to EOF, even after the DSH client
        // disappears. No request abort, port rotation, or Stop click.
        while(!nativeReply.isDone()){
            if(++iterations<3600 && !sessionMismatch){
                try{
                    JSONObject route=OriginalCoreBridge.diagnoseActiveNativeOwner();
                    JSONObject page=OriginalCoreBridge.diagnoseVerifiedApiPage();
                    if(!route.optBoolean("verified",false) ||
                       !anchor.host.equals(route.optString("ownerKind","")) ||
                       !"deepseek".equalsIgnoreCase(page.optString("provider","")) ||
                       page.optInt("lastSubmissionSequence",-1)!=anchor.submittedBefore+1){
                        if(page.optInt("lastSubmissionSequence",-1)>
                            anchor.submittedBefore+1 || !route.optBoolean("verified",false))
                            sessionMismatch=true;
                    } else if("submitted_confirmed".equals(
                                page.optString("deepseekSubmissionStatus","")) &&
                              page.optBoolean("assistantReplyChangedSinceSend",false)){
                        String next=OriginalCoreBridge.inspectVerifiedApiAnswer()
                            .optString("answer","");
                        if(!next.equals(anchor.oldAnswer)){
                            next=TitleCompat.stripSpeakerPrefix(next);
                            // Hold a small mutable tail to avoid streaming
                            // transient markdown/word reflow as settled text.
                            int stable=Math.max(0,next.length()-12);
                            if(stable>0&&stable<next.length() &&
                                Character.isHighSurrogate(next.charAt(stable-1)))
                                stable--;
                            String prefix=next.substring(0,stable);
                            if(!prefix.startsWith(delivered)){
                                if(!delivered.isEmpty())sessionMismatch=true;
                            }else if(prefix.length()>delivered.length()){
                                if(!sentHeader){
                                    Map<String,String> headers=new LinkedHashMap<>();
                                    headers.put("Content-Type","text/event-stream; charset=utf-8");
                                    headers.put("Cache-Control","no-cache");
                                    headers.put("X-OmniGate-Stream-Mode",
                                        "verified-live-deepseek-page");
                                    writeChunkedResponseHead(downstream,200,headers);
                                    sentHeader=true;
                                    traceStage("verified_live_tokens");
                                }
                                JSONObject delta=new JSONObject()
                                    .put("id","chatcmpl-omnigate-live")
                                    .put("object","chat.completion.chunk")
                                    .put("created",System.currentTimeMillis()/1000L)
                                    .put("model","deepseek-chat")
                                    .put("choices",new JSONArray().put(new JSONObject()
                                        .put("index",0)
                                        .put("delta",new JSONObject()
                                            .put("role","assistant")
                                            .put("content",prefix.substring(
                                                delivered.length())))
                                        .put("finish_reason",JSONObject.NULL)));
                                if(clientOpen)try{writeSseData(downstream,delta.toString());}
                                catch(IOException closed){clientOpen=false;}
                                delivered=prefix;
                            }
                        }
                    }
                }catch(Throwable ignored){
                    // A temporarily inaccessible native cached state is NOT
                    // evidence of completion, cancellation or a new session.
                }
            }
            try{Thread.sleep(180);}
            catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                // Do not interrupt/cancel the native worker. Wait for EOF.
                while(!nativeReply.isDone())
                    try{Thread.sleep(300);}catch(InterruptedException ignored){}
            }
        }
        LiveOriginalResult result;
        try { result=nativeReply.get(); }
        catch(Exception error){
            if(!sentHeader){
                writeJson(downstream,502,new JSONObject().put("error",
                    new JSONObject().put("type","omnigate_native_stream_error")
                        .put("code","native_stream_transport_failed")));
            }else if(clientOpen)
                sendLiveSseError(downstream,"native_stream_transport_failed");
            return;
        }
        ProxyTrace trace=CURRENT_TRACE.get();
        if(trace!=null){
            trace.upstreamCode=result.httpCode;
            trace.upstreamKind=result.mime.contains("json")?"json":
                result.mime.contains("event-stream")?"sse":"other";
        }
        if(result.httpCode!=200 || result.oversized ||
            !result.mime.contains("json")){
            if(!sentHeader){
                if(result.httpCode!=200)writeJson(downstream,
                    result.httpCode>=400?result.httpCode:502,new JSONObject()
                        .put("error","native_http_"+result.httpCode));
                else writeJson(downstream,502,new JSONObject()
                    .put("error","native_stream_response_not_json"));
            }else if(clientOpen)
                sendLiveSseError(downstream,"native_stream_response_incompatible");
            return;
        }
        JSONObject completed;
        try{completed=new JSONObject(new String(result.payload,
            StandardCharsets.UTF_8));}
        catch(Exception malformed){
            if(!sentHeader)writeJson(downstream,502,new JSONObject()
                .put("error","native_completed_json_invalid"));
            else if(clientOpen)sendLiveSseError(downstream,
                "native_completed_json_invalid");
            return;
        }
        JSONArray choices=completed.optJSONArray("choices");
        JSONObject choice=choices==null?null:choices.optJSONObject(0);
        JSONObject message=choice==null?null:choice.optJSONObject("message");
        String finalText=message==null?"":message.optString("content","");
        JSONArray finalTools=message==null?null:message.optJSONArray("tool_calls");
        finalText=TitleCompat.stripSpeakerPrefix(finalText);
        if(sessionMismatch || !finalText.startsWith(delivered) ||
            (finalText.trim().isEmpty() &&
             (finalTools==null || finalTools.length()==0))){
            if(!sentHeader)writeJson(downstream,409,new JSONObject()
                .put("error","live_stream_correlation_or_final_mismatch"));
            else if(clientOpen)sendLiveSseError(downstream,
                "live_stream_correlation_or_final_mismatch");
            return;
        }
        if(!sentHeader){
            // Not enough confirmed intermediate content, but a complete
            // native result exists. Preserve the previously working path.
            proxyCompletedJsonAsSse(
                new java.io.ByteArrayInputStream(result.payload),downstream);
            return;
        }
        if(!clientOpen)return;
        try{
            if(finalText.length()>delivered.length() ||
               (finalTools!=null && finalTools.length()>0)){
                JSONObject deltaBody=new JSONObject();
                if(finalText.length()>delivered.length())
                    deltaBody.put("content",finalText.substring(delivered.length()));
                if(finalTools!=null && finalTools.length()>0)
                    deltaBody.put("tool_calls",finalTools);
                JSONObject delta=new JSONObject()
                    .put("id","chatcmpl-omnigate-live")
                    .put("object","chat.completion.chunk")
                    .put("created",System.currentTimeMillis()/1000L)
                    .put("model","deepseek-chat")
                    .put("choices",new JSONArray().put(new JSONObject()
                        .put("index",0).put("delta",deltaBody)
                        .put("finish_reason",JSONObject.NULL)));
                writeSseData(downstream,delta.toString());
            }
            String reason=choice.optString("finish_reason","stop");
            if(finalTools!=null && finalTools.length()>0)
                reason="tool_calls";
            else if(!"length".equals(reason))reason="stop";
            JSONObject last=new JSONObject()
                .put("id","chatcmpl-omnigate-live")
                .put("object","chat.completion.chunk")
                .put("created",System.currentTimeMillis()/1000L)
                .put("model","deepseek-chat")
                .put("choices",new JSONArray().put(new JSONObject()
                    .put("index",0).put("delta",new JSONObject())
                    .put("finish_reason",reason)));
            writeSseData(downstream,last.toString());
            writeSseData(downstream,"[DONE]");
            writeFinalChunk(downstream);
        }catch(IOException disconnected){
            // Original native result is already complete; no hidden retry.
        }
    }
    private void sendLiveSseError(BufferedOutputStream out,String code)
        throws IOException,org.json.JSONException{
        writeSseData(out,new JSONObject().put("error",
            new JSONObject().put("type","omnigate_stream_error")
                .put("code",code)).toString());
        writeFinalChunk(out); // Intentionally NOT [DONE] or finish_reason=stop.
    }

    private static boolean isNonStreamingChatRequest(Request request){
        if(request.body==null || request.body.length>512*1024)return false;
        try{
            return !new JSONObject(new String(request.body,
                StandardCharsets.UTF_8)).optBoolean("stream",false);
        }catch(Exception ignored){return false;}
    }
    private static boolean isJsonResponse(Map<String,String> headers){
        for(Map.Entry<String,String> entry:headers.entrySet())
            if("content-type".equalsIgnoreCase(entry.getKey()))
                return entry.getValue().toLowerCase(Locale.ROOT).contains("json");
        return false;
    }

    /** No buffering or mutation of SSE. On JSON overflow / invalid JSON,
     * stream captured bytes unmodified and keep draining the original.
     * A downstream disconnect must never cancel in-flight model generation.
     */
    private boolean proxyNormalizedJsonReply(InputStream upstream,
            BufferedOutputStream clientOut,int statusCode,
            Map<String,String> headers,boolean title) throws IOException {
        ByteArrayOutputStream captured=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192];
        int n;
        while((n=upstream.read(buffer))!=-1){
            if(n==0)continue;
            if(captured.size()+n>1024*1024){
                boolean open=true;
                try{
                    writeChunkedResponseHead(clientOut,statusCode,headers);
                    byte[] initial=captured.toByteArray();
                    if(initial.length>0)writeChunk(clientOut,initial,initial.length);
                    writeChunk(clientOut,buffer,n);
                }catch(IOException closed){open=false;}
                while((n=upstream.read(buffer))!=-1)if(open && n>0){
                    try{writeChunk(clientOut,buffer,n);}
                    catch(IOException e){open=false;}
                }
                if(open)try{writeFinalChunk(clientOut);}
                catch(IOException ignored){}
                return true;
            }
            captured.write(buffer,0,n);
        }
        byte[] raw=captured.toByteArray();
        try{
            JSONObject json=new JSONObject(new String(raw,StandardCharsets.UTF_8));
            JSONArray choices=json.optJSONArray("choices");
            JSONObject choice=choices==null?null:choices.optJSONObject(0);
            JSONObject msg=choice==null?null:choice.optJSONObject("message");
            if(msg!=null && msg.opt("content") instanceof String){
                String original=msg.optString("content","");
                if(original.startsWith("AUTO_ROLLOVER_FAILED:")){
                    String failure=original.substring("AUTO_ROLLOVER_FAILED:".length());
                    int newline=failure.indexOf((char)10);
                    if(newline>=0)failure=failure.substring(0,newline);
                    String code=failure.trim().replaceAll("[^a-zA-Z0-9_-]","");
                    if(code.length()>100)code=code.substring(0,100);
                    if(code.isEmpty())code="continuation_unconfirmed";
                    writeJson(clientOut,409,new JSONObject()
                        .put("error",new JSONObject()
                            .put("type","omnigate_continuation_error")
                            .put("code",code)
                            .put("message","The original provider reached a chat limit, "+
                                "but continuation was not fully verified; "+
                                "do not automatically retry this request"))
                        .put("omnigate",new JSONObject()
                            .put("auto_rollover","blocked_no_retry")
                            .put("possible_provider_submission",true)));
                    return true;
                }
                String clean=title?TitleCompat.normalizeTitle(original)
                    :TitleCompat.stripSpeakerPrefix(original);
                if(!clean.equals(original)){
                    msg.put("content",clean);
                    if(title)json.put("omnigate",new JSONObject()
                        .put("delivery","original_native_http_200")
                        .put("auto_title_adapted",true));
                    writeJson(clientOut,statusCode,json);
                    return true;
                }
            }
        }catch(Exception ignored){
            // Cannot confidently parse; retain the exact original bytes.
        }
        boolean open=true;
        try{
            writeChunkedResponseHead(clientOut,statusCode,headers);
            if(raw.length>0)writeChunk(clientOut,raw,raw.length);
            writeFinalChunk(clientOut);
        }catch(IOException disconnected){open=false;}
        return true;
    }

    private void writeJson(
            BufferedOutputStream out,
            int status,
            JSONObject body) throws IOException {
        traceDownstream(status,"json");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);

        String head = "HTTP/1.1 "
                + status
                + " "
                + reason(status)
                + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: "
                + bytes.length
                + "\r\n"
                + "Connection: close\r\n\r\n";

        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }

    /** This is a protocol adapter, NOT live token generation. The original
     * web provider must return a verified completed answer before ANY SSE
     * chunk or [DONE] is sent. No fake progress or premature stop markers.
     */
    private void writeVerifiedAttachmentSse(BufferedOutputStream out,
            JSONObject verified) throws IOException {
        try {
            JSONArray choices=verified.optJSONArray("choices");
            JSONObject choice=choices==null?null:choices.optJSONObject(0);
            JSONObject message=choice==null?null:choice.optJSONObject("message");
            String content=message==null?"":message.optString("content","");
            if(content.isEmpty()){
                writeJson(out,502,new JSONObject()
                    .put("error","verified_attachment_reply_missing"));
                return;
            }
            String id=verified.optString("id","chatcmpl-omnigate-attachment");
            String model=verified.optString("model","chatgpt-web");
            long created=verified.optLong("created",System.currentTimeMillis()/1000L);
            Map<String,String> headers=new LinkedHashMap<>();
            headers.put("Content-Type","text/event-stream; charset=utf-8");
            headers.put("Cache-Control","no-cache");
            headers.put("X-OmniGate-Stream-Mode","complete-response-after-verification");
            writeChunkedResponseHead(out,200,headers);
            JSONObject first=new JSONObject()
                .put("id",id).put("object","chat.completion.chunk")
                .put("created",created).put("model",model)
                .put("choices",new JSONArray().put(new JSONObject()
                    .put("index",0)
                    .put("delta",new JSONObject().put("role","assistant")
                        .put("content",content))
                    .put("finish_reason",JSONObject.NULL)));
            writeSseData(out,first.toString());
            JSONObject last=new JSONObject()
                .put("id",id).put("object","chat.completion.chunk")
                .put("created",created).put("model",model)
                .put("choices",new JSONArray().put(new JSONObject()
                    .put("index",0).put("delta",new JSONObject())
                    .put("finish_reason","stop")));
            writeSseData(out,last.toString());
            writeSseData(out,"[DONE]");
            writeFinalChunk(out);
        }catch(org.json.JSONException ex){
            // No HTTP status may have been sent before validation above.
            throw new IOException("attachment_sse_encoding_failed",ex);
        }
    }

    private void writeSseData(BufferedOutputStream out,String data)
            throws IOException {
        byte[] raw=("data: "+data+"\n\n").getBytes(StandardCharsets.UTF_8);
        writeChunk(out,raw,raw.length);
    }

    private void writeChunkedResponseHead(
            BufferedOutputStream out,
            int status,
            Map<String, String> headers) throws IOException {
        boolean sse=isPlainSseResponse(headers);
        traceDownstream(status,sse?"sse":"chunked");
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ")
                .append(status)
                .append(' ')
                .append(reason(status))
                .append("\r\n");

        boolean contentTypeSeen = false;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            String lower = e.getKey().toLowerCase(Locale.ROOT);
            if (isHopByHop(lower)
                    || "content-length".equals(lower)) {
                continue;
            }
            if ("content-type".equals(lower)) contentTypeSeen = true;
            head.append(e.getKey())
                    .append(": ")
                    .append(e.getValue())
                    .append("\r\n");
        }

        if (!contentTypeSeen) {
            head.append("Content-Type: application/octet-stream\r\n");
        }
        head.append("Transfer-Encoding: chunked\r\n");
        head.append("Connection: close\r\n\r\n");

        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private void writeChunk(
            BufferedOutputStream out,
            byte[] data,
            int length) throws IOException {
        out.write(Integer.toHexString(length)
                .getBytes(StandardCharsets.US_ASCII));
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        out.write(data, 0, length);
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private void writeFinalChunk(BufferedOutputStream out) throws IOException {
        out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static boolean isHopByHop(String lower) {
        return "connection".equals(lower)
                || "keep-alive".equals(lower)
                || "proxy-authenticate".equals(lower)
                || "proxy-authorization".equals(lower)
                || "te".equals(lower)
                || "trailer".equals(lower)
                || "transfer-encoding".equals(lower)
                || "upgrade".equals(lower);
    }

    private static String cleanHeader(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.length() >= 2
                && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1).trim();
        }
        return v;
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 201: return "Created";
            case 202: return "Accepted";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 408: return "Request Timeout";
            case 409: return "Conflict";
            case 422: return "Unprocessable Entity";
            case 413: return "Payload Too Large";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            default: return "HTTP";
        }
    }

    private static String shortError(Throwable e) {
        if (e == null) return "unknown";
        String message = e.getMessage();
        String value = e.getClass().getSimpleName()
                + (message == null || message.trim().isEmpty()
                ? ""
                : "：" + message.trim());
        return value.length() > 180
                ? value.substring(0, 180) + "…"
                : value;
    }

    private void status(String value) {
        if (listener != null) listener.onStatus(value);
    }

    private static final class Request {
        final String method;
        final String path;
        final String version;
        final Map<String, String> headers;
        final byte[] body;

        Request(
                String method,
                String path,
                String version,
                Map<String, String> headers,
                byte[] body) {
            this.method = method;
            this.path = path;
            this.version = version;
            this.headers = headers;
            this.body = body;
        }
    }
}
