package com.dsh.webgateway;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalHttpServer {
    private static final String TAG = "DSH-Gateway";
    private static final int MAX_BODY = 48 * 1024 * 1024;
    public static final String PROBE_VALUE = "392125113473d61e8aaba950e010d41453ceb63597a7b047";

    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public LocalHttpServer(int port) {
        this.port = port;
    }

    public synchronized boolean start() {
        if (isListening()) return true;

        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(
                    new InetSocketAddress(
                            InetAddress.getByName("127.0.0.1"),
                            port),
                    16);
            serverSocket = socket;
            running = true;

            Thread t = new Thread(this::acceptLoop, "gateway-http");
            t.setDaemon(true);
            t.start();
            Log.i(TAG, "HTTP server listening on 127.0.0.1:" + port);
            return true;
        } catch (IOException e) {
            running = false;
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (IOException ignored) {}
            serverSocket = null;
            Log.e(TAG, "HTTP server bind failed", e);
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
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        serverSocket = null;
        pool.shutdownNow();
    }

    private void acceptLoop() {
        try {
            while (running) {
                ServerSocket listening = serverSocket;
                if (listening == null || listening.isClosed()) break;
                Socket socket = listening.accept();
                socket.setSoTimeout(3_720_000);
                pool.execute(() -> handle(socket));
            }
        } catch (IOException e) {
            if (running) Log.e(TAG, "HTTP server stopped", e);
        } finally {
            synchronized (this) {
                running = false;
                try {
                    if (serverSocket != null) serverSocket.close();
                } catch (IOException ignored) {}
                serverSocket = null;
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket;
             InputStream rawIn = new BufferedInputStream(s.getInputStream());
             OutputStream out = new BufferedOutputStream(s.getOutputStream())) {
            Request req = readRequest(rawIn);
            if (req == null) return;

            if ("OPTIONS".equals(req.method)) {
                writeEmpty(out, 204, "No Content");
                return;
            }

            if ("GET".equals(req.method) && "/__tunnel_probe".equals(req.path)) {
                writeJson(out, 200, new JSONObject().put("probe", PROBE_VALUE));
                return;
            }

            if ("GET".equals(req.method) && "/health".equals(req.path)) {
                AiProvider selected = GatewayApp.selectedProvider();
                boolean originalReady = OriginalGatewayProxy.probe();
                JSONObject health = new JSONObject()
                        .put("ok", true)
                        .put("port", port)
                        .put("mode", "companion-proxy")
                        .put("originalCoreReady", originalReady)
                        .put("originalCoreBase", OriginalGatewayProxy.ORIGINAL_BASE)
                        .put("selectedProvider", selected.id)
                        .put("selectedModel", selected.model)
                        .put("tunnelEnabled", GatewayApp.tunnelEnabled())
                        .put("publicUrl", GatewayApp.effectivePublicUrl());
                writeJson(out, 200, health);
                return;
            }

            if (req.path.startsWith("/v1/")) {
                boolean publicRequest = requiresPublicAuth(req);
                boolean allowed = publicRequest
                        ? authorized(req)
                        : hasAnyApiKey(req);

                if (!allowed) {
                    writeJson(out, 401, new JSONObject()
                            .put("error", "unauthorized")
                            .put("message", publicRequest
                                    ? "Invalid API key"
                                    : "API key required")
                            .put("authMode", publicRequest
                                    ? "public-exact"
                                    : "localhost-any-nonempty")
                            .put("accepted", "Bearer / X-API-Key / X-DSH-Gateway-Token"));
                    return;
                }
            }

            if ("GET".equals(req.method) && "/v1/models".equals(req.path)) {
                JSONArray models = new JSONArray();
                for (AiProvider p : AiProvider.values()) {
                    models.put(new JSONObject()
                            .put("id", p.model)
                            .put("object", "model")
                            .put("owned_by", AiProvider.modelsJsonOwner(p)));
                }
                writeJson(out, 200, new JSONObject()
                        .put("object", "list")
                        .put("data", models));
                return;
            }

            if ("POST".equals(req.method) && "/v1/chat/completions".equals(req.path)) {
                handleChat(req, out);
                return;
            }

            writeJson(out, 404, new JSONObject().put("error", "not_found"));
        } catch (Throwable e) {
            Log.e(TAG, "request failed", e);
        }
    }

    private boolean requiresPublicAuth(Request req) {
        return "1".equals(req.headers.get("x-omnigate-public-relay"));
    }

    private boolean hasAnyApiKey(Request req) {
        String auth = cleanHeaderValue(
                req.headers.getOrDefault("authorization", ""));
        if (!auth.isEmpty()) {
            int space = auth.indexOf(' ');
            if (space > 0) {
                String scheme = auth.substring(0, space).trim();
                String token = cleanHeaderValue(auth.substring(space + 1));
                if ("bearer".equalsIgnoreCase(scheme) && !token.isEmpty()) {
                    return true;
                }
            } else {
                return true;
            }
        }

        return !cleanHeaderValue(req.headers.get("x-api-key")).isEmpty()
                || !cleanHeaderValue(req.headers.get("x-dsh-gateway-token")).isEmpty();
    }

    private boolean authorized(Request req) {
        String expected = GatewayApp.apiKey();
        if (expected == null || expected.isEmpty()) return false;

        String auth = cleanHeaderValue(
                req.headers.getOrDefault("authorization", ""));
        if (!auth.isEmpty()) {
            if (expected.equals(auth)) return true;

            int space = auth.indexOf(' ');
            if (space > 0) {
                String scheme = auth.substring(0, space).trim();
                String token = cleanHeaderValue(auth.substring(space + 1));
                if ("bearer".equalsIgnoreCase(scheme)
                        && expected.equals(token)) {
                    return true;
                }
            }
        }

        String xApiKey = cleanHeaderValue(req.headers.get("x-api-key"));
        String xDsh = cleanHeaderValue(req.headers.get("x-dsh-gateway-token"));
        return expected.equals(xApiKey) || expected.equals(xDsh);
    }

    private String cleanHeaderValue(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.length() >= 2
                && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            v = v.substring(1, v.length() - 1).trim();
        }
        return v;
    }

    private void handleChat(Request req, OutputStream out) throws Exception {
        if (!OriginalGatewayProxy.probe()) {
            writeJson(out, 503, new JSONObject()
                    .put("error", "original_gateway_not_ready")
                    .put("message",
                            "Start the original AI Gateway first. OmniGate Companion does not reimplement browser automation.")
                    .put("originalBase", OriginalGatewayProxy.ORIGINAL_BASE));
            return;
        }

        // Preserve the exact OpenAI-compatible request body and let the
        // original 169 MB AI Gateway own provider selection, page input,
        // attachments, login state, streaming and browser automation.
        //
        // OriginalGatewayProxy intentionally keeps draining the upstream
        // response if the downstream client disappears, so a transient
        // Minis/DSH/UI disconnect does not become client_disconnected inside
        // the frozen original Gateway.
        OriginalGatewayProxy.proxy(
                req.method,
                req.path,
                req.headers,
                req.body,
                out);
    }

    private Request readRequest(InputStream in) throws IOException {
        String head = readHeader(in);
        if (head == null || head.isEmpty()) return null;

        String[] lines = head.split("\\r?\\n");
        String[] first = lines[0].split(" ");
        if (first.length < 2) throw new IOException("bad request line");

        Map<String,String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) {
                headers.put(
                        lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT),
                        lines[i].substring(c + 1).trim());
            }
        }

        int length = 0;
        try {
            length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        } catch (NumberFormatException ignored) {}

        if (length < 0 || length > MAX_BODY) {
            throw new IOException("request body too large");
        }

        byte[] body = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(body, off, length - off);
            if (n < 0) throw new IOException("unexpected eof");
            off += n;
        }

        return new Request(first[0], first[1].split("\\?", 2)[0], headers, body);
    }

    private String readHeader(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int state = 0;

        while (b.size() < 64 * 1024) {
            int x = in.read();
            if (x < 0) break;
            b.write(x);

            if ((state == 0 || state == 2) && x == '\r') state++;
            else if ((state == 1 || state == 3) && x == '\n') state++;
            else state = 0;

            if (state == 4) break;
        }

        if (b.size() == 0) return null;
        return b.toString(StandardCharsets.ISO_8859_1.name())
                .replaceFirst("\\r?\\n\\r?\\n$", "");
    }

    private void writeSseHeaders(OutputStream out) throws IOException {
        String headers = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/event-stream; charset=utf-8\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Connection: close\r\n"
                + corsHeaders()
                + "\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private void writeJson(OutputStream out, int code, JSONObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);

        String status;
        if (code == 200) status = "OK";
        else if (code == 400) status = "Bad Request";
        else if (code == 401) status = "Unauthorized";
        else if (code == 404) status = "Not Found";
        else if (code == 503) status = "Service Unavailable";
        else status = "Bad Gateway";

        String headers = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n"
                + corsHeaders()
                + "\r\n";

        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }

    private void writeEmpty(OutputStream out, int code, String status) throws IOException {
        String headers = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Length: 0\r\n"
                + "Connection: close\r\n"
                + corsHeaders()
                + "\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private String corsHeaders() {
        return "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Headers: Authorization, X-Api-Key, X-DSH-Gateway-Token, Content-Type\r\n"
                + "Access-Control-Allow-Methods: GET,POST,OPTIONS\r\n";
    }

    private static final class Request {
        final String method;
        final String path;
        final Map<String,String> headers;
        final byte[] body;

        Request(String method, String path, Map<String,String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }
    }
}
