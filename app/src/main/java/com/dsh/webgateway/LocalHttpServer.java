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
    private static final int MAX_BODY = 32 * 1024 * 1024;
    public static final String PROBE_VALUE = "392125113473d61e8aaba950e010d41453ceb63597a7b047";

    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public LocalHttpServer(int port) {
        this.port = port;
    }

    public void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(this::acceptLoop, "gateway-http");
        t.setDaemon(true);
        t.start();
    }

    public void shutdown() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
    }

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"));
            while (running) {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(620_000);
                pool.execute(() -> handle(socket));
            }
        } catch (IOException e) {
            if (running) Log.e(TAG, "HTTP server stopped", e);
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
                JSONObject health = new JSONObject()
                        .put("ok", true)
                        .put("port", port)
                        .put("browserReady", GatewayApp.relay() != null)
                        .put("tunnelEnabled", GatewayApp.tunnelEnabled())
                        .put("publicUrl", GatewayApp.generatedPublicUrl());
                writeJson(out, 200, health);
                return;
            }

            if (req.path.startsWith("/v1/") && !authorized(req)) {
                writeJson(out, 401, new JSONObject()
                        .put("error", "unauthorized")
                        .put("message", "Use the Gateway API key as Bearer token."));
                return;
            }

            if ("GET".equals(req.method) && "/v1/models".equals(req.path)) {
                JSONObject model = new JSONObject()
                        .put("id", "deepseek-chat")
                        .put("object", "model")
                        .put("owned_by", "deepseek-web");
                writeJson(out, 200, new JSONObject().put("object", "list").put("data", new JSONArray().put(model)));
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

    private boolean authorized(Request req) {
        String expected = GatewayApp.apiKey();
        String auth = req.headers.getOrDefault("authorization", "");
        if (("Bearer " + expected).equals(auth)) return true;
        return expected.equals(req.headers.get("x-api-key")) ||
                expected.equals(req.headers.get("x-dsh-gateway-token"));
    }

    private void handleChat(Request req, OutputStream out) throws Exception {
        WebRelay relay = GatewayApp.relay();
        if (relay == null) {
            writeJson(out, 503, new JSONObject()
                    .put("error", "browser_not_ready")
                    .put("message", "Open DSH Web Gateway and load DeepSeek first."));
            return;
        }

        JSONObject body;
        try {
            body = new JSONObject(new String(req.body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            writeJson(out, 400, new JSONObject().put("error", "invalid_json"));
            return;
        }

        boolean stream = body.optBoolean("stream", false);
        if (stream) {
            writeSseHeaders(out);
            WebRelay.Result result = relay.submit(body, new WebRelay.StreamSink() {
                @Override public void reasoning(String delta) throws IOException {
                    sendSseDelta(out, "reasoning_content", delta);
                }
                @Override public void text(String delta) throws IOException {
                    sendSseDelta(out, "content", delta);
                }
                @Override public void heartbeat() throws IOException {
                    out.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            });

            if (result.error != null) {
                JSONObject err = new JSONObject().put("error", result.error);
                out.write(("data: " + err + "\n\n").getBytes(StandardCharsets.UTF_8));
            } else {
                JSONObject done = new JSONObject()
                        .put("id", "web-gateway")
                        .put("object", "chat.completion.chunk")
                        .put("choices", new JSONArray().put(
                                new JSONObject()
                                        .put("index", 0)
                                        .put("delta", new JSONObject())
                                        .put("finish_reason", "stop")));
                out.write(("data: " + done + "\n\n").getBytes(StandardCharsets.UTF_8));
            }
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } else {
            WebRelay.Result result = relay.submit(body, null);
            if (result.error != null) {
                writeJson(out, 502, new JSONObject()
                        .put("error", "relay_failed")
                        .put("message", result.error));
            } else {
                JSONObject message = new JSONObject()
                        .put("role", "assistant")
                        .put("content", result.answer);
                if (!result.reasoning.isEmpty()) {
                    message.put("reasoning_content", result.reasoning);
                }
                JSONObject choice = new JSONObject()
                        .put("index", 0)
                        .put("message", message)
                        .put("finish_reason", "stop");
                JSONObject response = new JSONObject()
                        .put("id", "web-gateway")
                        .put("object", "chat.completion")
                        .put("model", "deepseek-chat")
                        .put("choices", new JSONArray().put(choice));
                writeJson(out, 200, response);
            }
        }
    }

    private void sendSseDelta(OutputStream out, String key, String value) throws IOException {
        if (value == null || value.isEmpty()) return;
        try {
            JSONObject delta = new JSONObject().put(key, value);
            JSONObject choice = new JSONObject()
                    .put("index", 0)
                    .put("delta", delta)
                    .put("finish_reason", JSONObject.NULL);
            JSONObject event = new JSONObject()
                    .put("id", "web-gateway")
                    .put("object", "chat.completion.chunk")
                    .put("model", "deepseek-chat")
                    .put("choices", new JSONArray().put(choice));
            out.write(("data: " + event + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            throw new IOException(e);
        }
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
        try { length = Integer.parseInt(headers.getOrDefault("content-length", "0")); }
        catch (NumberFormatException ignored) {}
        if (length < 0 || length > MAX_BODY) throw new IOException("request body too large");

        byte[] body = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(body, off, length - off);
            if (n < 0) throw new IOException("unexpected eof");
            off += n;
        }
        return new Request(first[0], first[1].split("\\?",2)[0], headers, body);
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
        String headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/event-stream; charset=utf-8\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: close\r\n" +
                corsHeaders() + "\r\n";
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

        String headers = "HTTP/1.1 " + code + " " + status + "\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n" +
                corsHeaders() + "\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }

    private void writeEmpty(OutputStream out, int code, String status) throws IOException {
        String headers = "HTTP/1.1 " + code + " " + status + "\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                corsHeaders() + "\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private String corsHeaders() {
        return "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: Authorization, X-Api-Key, X-DSH-Gateway-Token, Content-Type\r\n" +
                "Access-Control-Allow-Methods: GET,POST,OPTIONS\r\n";
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
