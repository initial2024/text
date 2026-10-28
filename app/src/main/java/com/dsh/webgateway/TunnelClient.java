package com.dsh.webgateway;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class TunnelClient {
    public interface StatusListener {
        void onStatus(String status);
    }

    private static final String TUNNEL_HOST = "57.183.5.186";
    private static final int TUNNEL_PORT = 9000;
    private static final String PINNED_CERT_SHA256 =
            "AA:14:DC:75:AE:54:3E:19:3C:25:6B:46:F1:1E:A6:B9:70:D3:A7:D6:79:C4:3E:74:74:79:67:E1:41:DE:D4:71";

    private final String token;
    private final String prefix;
    private final int localPort;
    private final StatusListener listener;

    private volatile boolean running;
    private volatile SSLSocket socket;
    private Thread thread;

    public TunnelClient(String token, String prefix, int localPort, StatusListener listener) {
        this.token = token;
        this.prefix = prefix;
        this.localPort = localPort;
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::runLoop, "tunnel-client");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (Throwable ignored) {}
        if (thread != null) thread.interrupt();
        thread = null;
    }

    public boolean isRunning() {
        return running;
    }

    private void runLoop() {
        while (running) {
            try {
                connectAndServe();
            } catch (Throwable e) {
                if (running) status("连接失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
            if (!running) break;
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        status("已断开");
    }

    private void connectAndServe() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        TrustManager[] trustManagers = new TrustManager[]{new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (chain == null || chain.length == 0) throw new CertificateException("无证书");
                try {
                    String actual = sha256Fingerprint(chain[0].getEncoded());
                    if (!PINNED_CERT_SHA256.equals(actual)) {
                        throw new CertificateException("证书指纹不匹配（疑似中间人）");
                    }
                } catch (CertificateException e) {
                    throw e;
                } catch (Exception e) {
                    throw new CertificateException(e);
                }
            }

            @Override public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        context.init(null, trustManagers, new SecureRandom());

        status("连接远端 " + TUNNEL_HOST + ":" + TUNNEL_PORT);
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(TUNNEL_HOST, TUNNEL_PORT), 10_000);

        status("TLS 握手中");
        SSLSocketFactory factory = context.getSocketFactory();
        SSLSocket s = (SSLSocket) factory.createSocket(
                raw,
                "deepseeks.dpdns.org",
                TUNNEL_PORT,
                true);
        try {
            SSLParameters params = s.getSSLParameters();
            params.setServerNames(java.util.Collections.singletonList(
                    new SNIHostName("deepseeks.dpdns.org")));
            s.setSSLParameters(params);
        } catch (Throwable ignored) {}
        s.setSoTimeout(15_000);
        s.startHandshake();
        status("TLS/证书验证通过");

        s.setSoTimeout(0);
        socket = s;

        try (BufferedInputStream in = new BufferedInputStream(s.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(s.getOutputStream())) {
            status("正在注册 " + prefix);
            writeLine(out, "REGISTER " + token + " " + prefix);
            String response = readLine(in);
            if (response == null) {
                throw new IOException("REGISTER 无响应");
            }
            if (!response.startsWith("OK")) {
                throw new IOException("REGISTER 被拒绝：" + sanitizeStatus(response));
            }

            status("隧道已注册 · 正在验证公网");
            startPublicProbe();
            while (running && !s.isClosed()) {
                Request req = readRequest(in);
                if (req == null) break;
                handleRequest(req, out);
            }
        } finally {
            try { s.close(); } catch (Throwable ignored) {}
            if (socket == s) socket = null;
        }
    }

    private void handleRequest(Request request, BufferedOutputStream tunnelOut) throws Exception {
        URL url = new URL("http://127.0.0.1:" + localPort + request.path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod(request.method);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(600_000);
            conn.setInstanceFollowRedirects(false);

            for (Map.Entry<String, String> entry : request.headers.entrySet()) {
                String key = entry.getKey();
                if ("authorization".equals(key) || "content-type".equals(key)) {
                    conn.setRequestProperty(key, entry.getValue());
                }
            }

            if (request.body.length > 0 &&
                    ("POST".equals(request.method) || "PUT".equals(request.method) || "PATCH".equals(request.method))) {
                conn.setDoOutput(true);
                try (OutputStream bodyOut = conn.getOutputStream()) {
                    bodyOut.write(request.body);
                }
            }

            int code = conn.getResponseCode();
            String contentType = conn.getHeaderField("Content-Type");
            if (contentType == null || contentType.isEmpty()) contentType = "application/json";

            writeLine(tunnelOut, "RESP " + request.id + " " + code + " " + contentType);

            InputStream responseIn = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (responseIn != null) {
                try (InputStream stream = responseIn) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = stream.read(buffer)) >= 0) {
                        if (n == 0) continue;
                        writeLine(tunnelOut, "DATA " + request.id + " " + n);
                        tunnelOut.write(buffer, 0, n);
                        tunnelOut.flush();
                    }
                }
            }
            writeLine(tunnelOut, "END " + request.id);
        } finally {
            conn.disconnect();
        }
    }

    private Request readRequest(InputStream in) throws IOException {
        String first = readLine(in);
        if (first == null) return null;
        String[] parts = first.split(" ", 4);
        if (parts.length < 4 || !"REQ".equals(parts[0])) return null;

        int id;
        try {
            id = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        String method = parts[2];
        String path = parts[3];
        Map<String, String> headers = new HashMap<>();

        while (true) {
            String line = readLine(in);
            if (line == null) return null;
            if (line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            headers.put(name, value);
        }

        int length = 0;
        try {
            length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        } catch (NumberFormatException ignored) {}
        if (length < 0 || length > 32 * 1024 * 1024) throw new IOException("tunnel request body too large");

        byte[] body = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(body, off, length - off);
            if (n < 0) throw new IOException("unexpected EOF in tunnel body");
            off += n;
        }
        return new Request(id, method, path, headers, body);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) return buffer.size() == 0 ? null : buffer.toString(StandardCharsets.UTF_8.name());
            if (b == '\n') break;
            if (b != '\r') buffer.write(b);
            if (buffer.size() > 64 * 1024) throw new IOException("tunnel line too long");
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String sha256Fingerprint(byte[] encoded) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(encoded);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) out.append(':');
            out.append(String.format(Locale.ROOT, "%02X", digest[i] & 0xff));
        }
        return out.toString();
    }

    private static final String TUNNEL_SUFFIX_HINT = ".deepseeks.dpdns.org";

    private void startPublicProbe() {
        Thread probeThread = new Thread(() -> {
            String url = "https://" + prefix + TUNNEL_SUFFIX_HINT + "/__tunnel_probe";
            String lastError = "未知错误";
            for (int attempt = 1; running && attempt <= 3; attempt++) {
                HttpURLConnection conn = null;
                try {
                    if (attempt > 1) Thread.sleep(1800L);
                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(8_000);
                    conn.setReadTimeout(8_000);
                    conn.setUseCaches(false);
                    conn.setRequestMethod("GET");
                    int code = conn.getResponseCode();
                    InputStream body = code >= 400
                            ? conn.getErrorStream()
                            : conn.getInputStream();
                    String text = body == null ? "" : readSmallText(body);
                    if (code == 200
                            && text.contains(LocalHttpServer.PROBE_VALUE)) {
                        status("公网可用 · "
                                + prefix
                                + TUNNEL_SUFFIX_HINT);
                        return;
                    }
                    lastError = "HTTP " + code;
                } catch (Throwable e) {
                    lastError = e.getClass().getSimpleName()
                            + (e.getMessage() == null
                            ? ""
                            : "：" + sanitizeStatus(e.getMessage()));
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
            if (running) {
                status("隧道已注册，但公网探针失败 · " + lastError);
            }
        }, "tunnel-public-probe");
        probeThread.setDaemon(true);
        probeThread.start();
    }

    private static String readSmallText(InputStream in) throws IOException {
        try (InputStream body = in;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int n;
            while ((n = body.read(buffer)) >= 0 && out.size() < 8192) {
                if (n > 0) out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String sanitizeStatus(String value) {
        if (value == null) return "null";
        String cleaned = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (cleaned.length() > 160) cleaned = cleaned.substring(0, 160) + "…";
        return cleaned;
    }

    private void status(String value) {
        if (listener != null) listener.onStatus(value);
    }

    private static final class Request {
        final int id;
        final String method;
        final String path;
        final Map<String, String> headers;
        final byte[] body;

        Request(int id, String method, String path, Map<String, String> headers, byte[] body) {
            this.id = id;
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }
    }
}
