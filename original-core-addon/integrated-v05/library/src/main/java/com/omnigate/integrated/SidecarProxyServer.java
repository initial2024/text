package com.omnigate.integrated;

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
             BufferedOutputStream out = new BufferedOutputStream(client.getOutputStream())) {

            Request request = readRequest(in);
            if (request == null) return;

            if ("GET".equals(request.method)
                    && ("/health".equals(request.path)
                    || "/__sidecar_health".equals(request.path))) {
                JSONObject observedOwner=OriginalCoreBridge.diagnoseVisibleHostSocket();
                writeJson(out, 200, new JSONObject()
                        .put("ok", true)
                        .put("service", "OmniGate Sidecar")
                        .put("listen", "127.0.0.1:8787")
                        .put("upstream", "127.0.0.1:" + OriginalCoreBridge.configuredPort())
                        .put("upstreamReachable", isUpstreamReachable())
                        .put("originalOwnerAlive", OriginalCoreBridge.originalHostAlive())
                        .put("originalServerRunning", OriginalCoreBridge.nativeOwnerRunning())
                        .put("originalLifecycle", OriginalCoreBridge.nativeLifecycleState())
                        .put("observedRuntimeRoute",
                            OriginalCoreBridge.observedRouteState(observedOwner))
                        .put("originalSocketOwner",
                            observedOwner.optString("socketOwner", "unknown"))
                        .put("originalOverlayActive",
                            observedOwner.opt("originalOverlayServiceActive"))
                        .put("originalOverlaySocketOwnerVerified", false)
                        .put("originalOwnerHandoffStatus",
                            OriginalCoreBridge.handoffStatus())
                        .put("originalSocketBindScope",
                            observedOwner.optString("bindAddressScope", "unknown"))
                        .put("originalWebDriverIdentity",
                            observedOwner.optString("webDriverIdentity", "unknown"))
                        .put("portConflict", OriginalCoreBridge.isPortConflicted())
                        .put("originalApiStartStatus", OriginalCoreBridge.lastStartStatus())
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

            // Never silently dispatch a chat request into an independently
            // bound overlay runtime when the original MainActivity is alive
            // but demonstrably does not own the configured 1601 listener.
            // Unknown or reflection-unsupported versions retain legacy
            // routing for compatibility; they are NOT reported as verified.
            if ("POST".equals(request.method)
                    && request.path.startsWith("/v1/chat/completions")) {
                JSONObject observed=OriginalCoreBridge.diagnoseVisibleHostSocket();
                String route=OriginalCoreBridge.observedRouteState(observed);
                if ("port_not_owned_by_main".equals(route)
                        || "main_socket_driver_mismatch".equals(route)) {
                    writeJson(out, 409, new JSONObject()
                        .put("error", "original_runtime_owner_mismatch")
                        .put("message", "The native API listener does not map to the visible MainActivity WebDriver; request not sent")
                        .put("observedRuntimeRoute", route)
                        .put("originalSocketOwner",
                            observed.optString("socketOwner", "unknown"))
                        .put("originalWebDriverIdentity",
                            observed.optString("webDriverIdentity", "unknown"))
                        .put("hint", "Inspect which original runtime owns the WebDriver; do not restart or stop an active chat"));
                    return;
                }
            }

            // Only the explicit single-image, non-streaming chat route
            // uses the correlated sidecar recovery. All text, models and
            // other original API paths remain on the frozen proxy.
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
                            .put("message","Unsupported or oversized image request")));
                    return;
                } catch(Exception malformed) {
                    writeJson(out,422,new JSONObject()
                        .put("error",new JSONObject()
                            .put("type","invalid_request_error")
                            .put("code","malformed_attachment_envelope")));
                    return;
                }
                if(prepared!=null) {
                    AttachmentApiBridge.Result result=
                        AttachmentApiBridge.execute(prepared);
                    writeJson(out,result.http,result.body);
                    return;
                }
            }

            proxyToFrozenCore(request, out);
        } catch (Throwable e) {
            status("Sidecar 请求异常 · " + shortError(e));
        }
    }

    private void proxyToFrozenCore(Request request, BufferedOutputStream clientOut)
            throws IOException {
        HttpURLConnection upstream = null;
        InputStream upstreamBody = null;

        try {
            if (OriginalCoreBridge.isPortConflicted())
                throw new IOException("native_proxy_port_conflict: 8787 -> 8787 prohibited");
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

            if (request.body.length > 0) {
                upstream.setDoOutput(true);
                upstream.setFixedLengthStreamingMode(request.body.length);
                try (OutputStream bodyOut = upstream.getOutputStream()) {
                    bodyOut.write(request.body);
                    bodyOut.flush();
                }
            }

            int statusCode = upstream.getResponseCode();
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

            boolean downstreamOpen = true;
            try {
                writeChunkedResponseHead(
                        clientOut,
                        statusCode,
                        responseHeaders);
            } catch (IOException downstreamClosed) {
                // Critical continuity rule: do NOT abort the upstream request merely
                // because Minis/DSH disconnected. Keep draining the original response
                // so the native original-core connection is allowed to finish naturally.
                downstreamOpen = false;
                status("客户端已断开 · 原 1601 回答继续后台完成");
            }

            upstreamBody = statusCode >= 400
                    ? upstream.getErrorStream()
                    : upstream.getInputStream();

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

    private void writeJson(
            BufferedOutputStream out,
            int status,
            JSONObject body) throws IOException {
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

    private void writeChunkedResponseHead(
            BufferedOutputStream out,
            int status,
            Map<String, String> headers) throws IOException {
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
