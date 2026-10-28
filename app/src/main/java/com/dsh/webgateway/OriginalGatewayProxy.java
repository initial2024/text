package com.dsh.webgateway;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OriginalGatewayProxy {
    private static final String TAG = "OmniGate-OriginalProxy";
    public static final String ORIGINAL_BASE = "http://127.0.0.1:1601";
    public static final int HARD_READ_TIMEOUT_MS = 3_660_000;

    private OriginalGatewayProxy() {}

    public static boolean probe() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(
                    ORIGINAL_BASE + "/").openConnection();
            conn.setConnectTimeout(900);
            conn.setReadTimeout(900);
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");
            // Any HTTP status proves the original local listener exists.
            conn.getResponseCode();
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static void proxy(
            String method,
            String path,
            Map<String, String> requestHeaders,
            byte[] requestBody,
            OutputStream downstream) throws IOException {
        HttpURLConnection conn = null;
        boolean downstreamAttached = true;

        try {
            String safePath = path == null || path.isEmpty()
                    ? "/"
                    : (path.startsWith("/") ? path : "/" + path);

            conn = (HttpURLConnection) new URL(
                    ORIGINAL_BASE + safePath).openConnection();
            conn.setConnectTimeout(4_000);
            conn.setReadTimeout(HARD_READ_TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod(method);

            for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                String key = entry.getKey();
                String lower = key.toLowerCase(Locale.ROOT);
                if ("host".equals(lower)
                        || "content-length".equals(lower)
                        || "connection".equals(lower)
                        || "authorization".equals(lower)
                        || "x-api-key".equals(lower)
                        || "x-dsh-gateway-token".equals(lower)
                        || "x-omnigate-public-relay".equals(lower)) {
                    continue;
                }
                conn.setRequestProperty(key, entry.getValue());
            }

            // Authentication terminates at OmniGate Companion. Never forward
            // the external/public credential into the frozen original core.
            // The original loopback contract only needs a non-empty local
            // compatibility credential.
            conn.setRequestProperty(
                    "Authorization",
                    "Bearer omnigate-local");

            if (requestBody != null
                    && requestBody.length > 0
                    && allowsBody(method)) {
                conn.setDoOutput(true);
                try (OutputStream upstreamOut = conn.getOutputStream()) {
                    upstreamOut.write(requestBody);
                    upstreamOut.flush();
                }
            }

            int status = conn.getResponseCode();
            String reason = conn.getResponseMessage();
            if (reason == null || reason.trim().isEmpty()) {
                reason = status >= 200 && status < 300 ? "OK" : "Upstream";
            }

            StringBuilder responseHead = new StringBuilder()
                    .append("HTTP/1.1 ")
                    .append(status)
                    .append(' ')
                    .append(reason)
                    .append("\r\n");

            String contentType = conn.getHeaderField("Content-Type");
            if (contentType != null && !contentType.isEmpty()) {
                responseHead.append("Content-Type: ")
                        .append(contentType)
                        .append("\r\n");
            } else {
                responseHead.append(
                        "Content-Type: application/octet-stream\r\n");
            }

            responseHead.append("Cache-Control: no-cache\r\n")
                    .append("Connection: close\r\n")
                    .append("Access-Control-Allow-Origin: *\r\n")
                    .append("Access-Control-Allow-Headers: Authorization, X-Api-Key, X-DSH-Gateway-Token, Content-Type\r\n")
                    .append("Access-Control-Allow-Methods: GET,POST,OPTIONS\r\n")
                    .append("\r\n");

            try {
                downstream.write(
                        responseHead.toString().getBytes(
                                StandardCharsets.ISO_8859_1));
                downstream.flush();
            } catch (IOException clientGone) {
                // Do not turn a transient client/UI disconnect into
                // client_disconnected inside the frozen original Gateway.
                downstreamAttached = false;
                Log.w(TAG,
                        "downstream disconnected before response body; "
                                + "continuing to drain original Gateway");
            }

            InputStream raw = status >= 400
                    ? conn.getErrorStream()
                    : conn.getInputStream();
            if (raw == null) return;

            try (InputStream upstream = new BufferedInputStream(raw)) {
                byte[] buffer = new byte[16 * 1024];
                int n;
                while ((n = upstream.read(buffer)) >= 0) {
                    if (n == 0) continue;

                    if (downstreamAttached) {
                        try {
                            downstream.write(buffer, 0, n);
                            downstream.flush();
                        } catch (IOException clientGone) {
                            downstreamAttached = false;
                            Log.w(TAG,
                                    "downstream disconnected during reply; "
                                            + "continuing background drain");
                        }
                    }
                    // If downstreamAttached is false, intentionally keep
                    // consuming upstream until EOF/timeout. This allows the
                    // original webpage generation to finish normally.
                }
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean allowsBody(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

}
