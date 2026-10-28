package com.dsh.webgateway;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class WebRelay {
    private static final String PREFIX = "<<<DSH_WEB_RELAY_V2_ATTACHMENT_ENVELOPE>>>";
    private static final String SUFFIX = "<<<END_DSH_WEB_RELAY_V2_ATTACHMENT_ENVELOPE>>>";
    private static final long HARD_TIMEOUT_MS = 600_000L;
    private static final long POLL_MS = 400L;
    private static final long END_GRACE_MS = 5_000L;
    private static final int MAX_ROLLOVER = 3;
    private static final int ROLLOVER_CONTEXT_MAX_CHARS = 30_000;
    private static final int ROLLOVER_SYSTEM_MAX_CHARS = 8_000;
    private static final int ROLLOVER_RECENT_MESSAGES = 10;
    private static final int MAX_ATTACHMENT_BYTES = 15 * 1024 * 1024;
    private static final int MAX_ATTACHMENTS_TOTAL_BYTES = 24 * 1024 * 1024;

    public interface StreamSink {
        void reasoning(String delta) throws IOException;
        void text(String delta) throws IOException;
        void heartbeat() throws IOException;
    }

    public static final class Result {
        public final String answer;
        public final String reasoning;
        public final String error;

        private Result(String answer, String reasoning, String error) {
            this.answer = answer == null ? "" : answer;
            this.reasoning = reasoning == null ? "" : reasoning;
            this.error = error;
        }

        public static Result ok(String answer, String reasoning) {
            return new Result(answer, reasoning, null);
        }

        public static Result fail(String error) {
            return new Result("", "", error);
        }
    }

    private static final class Envelope {
        final String prompt;
        final String rolloverPrompt;
        final List<Attachment> attachments;

        Envelope(
                String prompt,
                String rolloverPrompt,
                List<Attachment> attachments) {
            this.prompt = prompt == null ? "" : prompt;
            this.rolloverPrompt = rolloverPrompt == null ? "" : rolloverPrompt;
            this.attachments = attachments;
        }
    }

    private static final class Attachment {
        final String id;
        final String name;
        final String mime;
        final String data;
        final boolean current;

        Attachment(String id, String name, String mime, String data, boolean current) {
            this.id = id;
            this.name = name;
            this.mime = mime;
            this.data = data;
            this.current = current;
        }
    }

    private static final class PageState {
        final String answer;
        final String thinking;
        final String body;
        final boolean generating;
        final boolean limit;

        PageState(String answer, String thinking, String body, boolean generating, boolean limit) {
            this.answer = answer;
            this.thinking = thinking;
            this.body = body;
            this.generating = generating;
            this.limit = limit;
        }
    }

    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object submitLock = new Object();
    private volatile AiProvider activeProvider = GatewayApp.selectedProvider();

    public WebRelay(WebView webView) {
        this.webView = webView;
    }

    public AiProvider activeProvider() {
        return activeProvider;
    }

    public void notePageUrl(String url) {
        activeProvider = AiProvider.fromUrl(url, activeProvider);
        GatewayApp.setSelectedProvider(activeProvider);
    }

    public void openProvider(AiProvider provider) {
        if (provider == null) provider = AiProvider.DEEPSEEK;
        final AiProvider target = provider;
        activeProvider = target;
        GatewayApp.setSelectedProvider(target);
        main.post(() -> webView.loadUrl(target.url));
    }

    public Result submit(JSONObject request, StreamSink sink) {
        synchronized (submitLock) {
            try {
                AiProvider target = AiProvider.fromModel(
                        request.optString("model", ""),
                        GatewayApp.selectedProvider());
                ensureProvider(target);
                Envelope envelope = parseEnvelope(request);
                return runSubmission(target, envelope, sink, 0);
            } catch (Throwable e) {
                return Result.fail(e.getClass().getSimpleName() + ": "
                        + (e.getMessage() == null ? "relay error" : e.getMessage()));
            }
        }
    }

    private void ensureProvider(AiProvider target) throws Exception {
        if (target == null) target = AiProvider.DEEPSEEK;
        if (activeProvider != target) {
            openProvider(target);
            waitForProviderHost(target, 30_000L);
        }
        GatewayApp.setSelectedProvider(target);
        waitForComposer(30_000L);
    }

    private void waitForProviderHost(AiProvider target, long timeoutMs) throws Exception {
        long until = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < until) {
            String raw = eval("location.href", 5_000L);
            String href = unwrapJavascriptString(raw);
            AiProvider observed = AiProvider.fromUrl(href, null);
            if (observed == target) {
                activeProvider = target;
                return;
            }
            Thread.sleep(250L);
        }
        throw new IOException("provider_navigation_timeout: " + target.displayName);
    }

    private Result runSubmission(
            AiProvider provider,
            Envelope env,
            StreamSink sink,
            int rolloverCount) throws Exception {
        waitForComposer(30_000L);

        PageState beforeSend = readState(provider);
        if (beforeSend.limit) {
            if (rolloverCount >= MAX_ROLLOVER) {
                throw new IOException(
                        "AUTO_ROLLOVER_FAILED: exceeded "
                                + MAX_ROLLOVER
                                + " automatic continuation chats");
            }

            String nextPrompt = buildContinuationPrompt(
                    env,
                    rolloverCount + 1,
                    beforeSend.answer,
                    beforeSend.thinking);

            if (!startFreshChat(provider)) {
                throw new IOException(
                        "AUTO_ROLLOVER_FAILED: new-chat control unavailable before send");
            }

            waitForComposer(30_000L);
            return runSubmission(
                    provider,
                    new Envelope(
                            nextPrompt,
                            env.rolloverPrompt,
                            env.attachments),
                    sink,
                    rolloverCount + 1);
        }

        List<Attachment> toUpload = new ArrayList<>();
        for (Attachment a : env.attachments) {
            if (a.current) toUpload.add(a);
        }
        for (Attachment a : toUpload) uploadAttachment(provider, a);

        String prompt = rolloverCount > 0
                ? env.rolloverPrompt.trim()
                : env.prompt.trim();
        if (prompt.isEmpty() && !toUpload.isEmpty()) {
            prompt = "请查看并分析我刚刚上传的附件。";
        }
        try {
            sendPrompt(provider, prompt);
        } catch (IOException sendError) {
            PageState failedState = readState(provider);
            if (!failedState.limit) throw sendError;

            if (rolloverCount >= MAX_ROLLOVER) {
                throw new IOException(
                        "AUTO_ROLLOVER_FAILED: exceeded "
                                + MAX_ROLLOVER
                                + " automatic continuation chats",
                        sendError);
            }

            String nextPrompt = buildContinuationPrompt(
                    env,
                    rolloverCount + 1,
                    failedState.answer,
                    failedState.thinking);

            if (!startFreshChat(provider)) {
                throw new IOException(
                        "AUTO_ROLLOVER_FAILED: new-chat control unavailable after send failure",
                        sendError);
            }

            waitForComposer(30_000L);
            return runSubmission(
                    provider,
                    new Envelope(
                            nextPrompt,
                            env.rolloverPrompt,
                            env.attachments),
                    sink,
                    rolloverCount + 1);
        }

        long start = System.currentTimeMillis();
        long lastGeneratingAt = start;
        long lastHeartbeat = start;
        String sentAnswer = "";
        String sentThinking = "";
        String lastAnswer = "";
        String lastThinking = "";
        int stable = 0;

        while (System.currentTimeMillis() - start < HARD_TIMEOUT_MS) {
            PageState state = readState(provider);

            if (state.limit) {
                if (rolloverCount >= MAX_ROLLOVER) {
                    throw new IOException(
                            "AUTO_ROLLOVER_FAILED: exceeded "
                                    + MAX_ROLLOVER
                                    + " automatic continuation chats");
                }

                String nextPrompt = buildContinuationPrompt(
                        env,
                        rolloverCount + 1,
                        state.answer,
                        state.thinking);

                if (sink != null) {
                    sink.heartbeat();
                }

                if (!startFreshChat(provider)) {
                    throw new IOException(
                            "AUTO_ROLLOVER_FAILED: new-chat control unavailable");
                }

                waitForComposer(30_000L);
                return runSubmission(
                        provider,
                        new Envelope(
                                nextPrompt,
                                env.rolloverPrompt,
                                env.attachments),
                        sink,
                        rolloverCount + 1);
            }

            if (state.generating) lastGeneratingAt = System.currentTimeMillis();

            if (sink != null) {
                String thinkDelta = suffixDelta(sentThinking, state.thinking);
                if (!thinkDelta.isEmpty()) {
                    sink.reasoning(thinkDelta);
                    sentThinking = state.thinking;
                }

                String answerDelta = suffixDelta(sentAnswer, state.answer);
                if (!answerDelta.isEmpty()) {
                    sink.text(answerDelta);
                    sentAnswer = state.answer;
                }

                if (System.currentTimeMillis() - lastHeartbeat > 15_000L) {
                    sink.heartbeat();
                    lastHeartbeat = System.currentTimeMillis();
                }
            }

            if (state.answer.equals(lastAnswer) && state.thinking.equals(lastThinking)) {
                stable++;
            } else {
                stable = 0;
            }
            lastAnswer = state.answer;
            lastThinking = state.thinking;

            boolean graceExpired = System.currentTimeMillis() - lastGeneratingAt >= END_GRACE_MS;
            if (!state.generating
                    && graceExpired
                    && stable >= 3
                    && (!state.answer.isEmpty() || !state.thinking.isEmpty())) {
                return Result.ok(state.answer, state.thinking);
            }

            Thread.sleep(POLL_MS);
        }

        throw new IOException("timeout after 600s");
    }

    private Envelope parseEnvelope(JSONObject request) throws Exception {
        JSONArray messages = request.optJSONArray("messages");
        if (messages == null || messages.length() == 0) {
            throw new IOException("messages is empty");
        }

        String envelopeRaw = null;
        for (int i = messages.length() - 1; i >= 0; i--) {
            JSONObject m = messages.optJSONObject(i);
            if (m == null) continue;
            Object content = m.opt("content");
            if (content instanceof String) {
                String s = (String) content;
                if (s.contains(PREFIX) && s.contains(SUFFIX)) {
                    envelopeRaw = s;
                    break;
                }
            }
        }

        if (envelopeRaw != null) {
            int a = envelopeRaw.indexOf(PREFIX);
            int b = envelopeRaw.indexOf(SUFFIX, a + PREFIX.length());
            String encoded = envelopeRaw.substring(
                    a + PREFIX.length(),
                    b).trim();
            byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
            JSONObject payload = new JSONObject(
                    new String(decoded, StandardCharsets.UTF_8));

            String prompt = payload.optString("prompt", "");
            if (prompt.isEmpty()) prompt = buildPrompt(messages);

            List<Attachment> attachments = new ArrayList<>();
            int totalBytes = 0;
            JSONArray arr = payload.optJSONArray("attachments");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject x = arr.optJSONObject(i);
                    if (x == null) continue;
                    Attachment attachment = checkedAttachment(
                            x.optString("id", "env-" + i),
                            x.optString("name", "attachment-" + (i + 1)),
                            x.optString("mime", "application/octet-stream"),
                            x.optString("data"),
                            x.optBoolean("current", false));
                    totalBytes += decodedAttachmentBytes(attachment.data);
                    if (totalBytes > MAX_ATTACHMENTS_TOTAL_BYTES) {
                        throw new IOException(
                                "ATTACHMENT_TOO_LARGE: total attachments exceed 24 MiB");
                    }
                    attachments.add(attachment);
                }
            }
            return new Envelope(
                    prompt,
                    buildRolloverPrompt(messages, attachments),
                    attachments);
        }

        List<Attachment> standardAttachments =
                parseStandardAttachments(messages);
        return new Envelope(
                buildPrompt(messages),
                buildRolloverPrompt(messages, standardAttachments),
                standardAttachments);
    }

    private List<Attachment> parseStandardAttachments(
            JSONArray messages) throws Exception {
        List<Attachment> out = new ArrayList<>();
        int lastUser = -1;
        for (int i = messages.length() - 1; i >= 0; i--) {
            JSONObject m = messages.optJSONObject(i);
            if (m != null && "user".equals(m.optString("role"))) {
                lastUser = i;
                break;
            }
        }

        int totalBytes = 0;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            Object content = message.opt("content");
            if (!(content instanceof JSONArray)) continue;

            JSONArray parts = (JSONArray) content;
            for (int j = 0; j < parts.length(); j++) {
                JSONObject part = parts.optJSONObject(j);
                if (part == null) continue;

                Attachment attachment = attachmentFromPart(
                        part,
                        "msg-" + i + "-part-" + j,
                        i == lastUser);
                if (attachment == null) continue;

                totalBytes += decodedAttachmentBytes(attachment.data);
                if (totalBytes > MAX_ATTACHMENTS_TOTAL_BYTES) {
                    throw new IOException(
                            "ATTACHMENT_TOO_LARGE: total attachments exceed 24 MiB");
                }
                out.add(attachment);
            }
        }

        return out;
    }

    private Attachment attachmentFromPart(
            JSONObject part,
            String id,
            boolean current) throws Exception {
        String type = part.optString("type", "");

        if ("image_url".equals(type) || "input_image".equals(type)) {
            Object imageValue = part.opt("image_url");
            String url = "";
            if (imageValue instanceof JSONObject) {
                url = ((JSONObject) imageValue).optString("url", "");
            } else if (imageValue instanceof String) {
                url = (String) imageValue;
            }
            if (url.isEmpty()) {
                url = part.optString("data", "");
            }
            if (!url.startsWith("data:")) {
                return null;
            }
            String name = part.optString(
                    "filename",
                    "image-" + System.currentTimeMillis());
            return attachmentFromDataUrl(
                    id,
                    name,
                    url,
                    current,
                    "image/jpeg");
        }

        if ("file".equals(type)
                || "input_file".equals(type)
                || "attachment".equals(type)) {
            JSONObject file = part.optJSONObject("file");
            String name = part.optString("filename", "");
            String data = part.optString("file_data", "");
            String mime = part.optString(
                    "mime_type",
                    "application/octet-stream");

            if (file != null) {
                if (name.isEmpty()) {
                    name = file.optString(
                            "filename",
                            file.optString("name", ""));
                }
                if (data.isEmpty()) {
                    data = file.optString(
                            "file_data",
                            file.optString("data", ""));
                }
                mime = file.optString(
                        "mime_type",
                        file.optString("mime", mime));
            }

            if (name.isEmpty()) {
                name = "attachment-" + System.currentTimeMillis();
            }

            if (data.startsWith("data:")) {
                return attachmentFromDataUrl(
                        id,
                        name,
                        data,
                        current,
                        mime);
            }

            if (!data.isEmpty()) {
                return checkedAttachment(
                        id,
                        name,
                        mime,
                        data,
                        current);
            }
        }

        return null;
    }

    private Attachment attachmentFromDataUrl(
            String id,
            String name,
            String dataUrl,
            boolean current,
            String fallbackMime) throws Exception {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new IOException("ATTACHMENT_INVALID_DATA_URL: " + name);
        }

        String meta = dataUrl.substring(5, comma);
        String data = dataUrl.substring(comma + 1);
        if (!meta.toLowerCase().contains(";base64")) {
            throw new IOException(
                    "ATTACHMENT_UNSUPPORTED_ENCODING: " + name);
        }

        String mime = fallbackMime;
        int semi = meta.indexOf(';');
        String declared = semi >= 0
                ? meta.substring(0, semi)
                : meta;
        if (!declared.trim().isEmpty()) mime = declared.trim();

        return checkedAttachment(id, name, mime, data, current);
    }

    private Attachment checkedAttachment(
            String id,
            String name,
            String mime,
            String data,
            boolean current) throws Exception {
        if (data == null || data.trim().isEmpty()) {
            throw new IOException(
                    "ATTACHMENT_EMPTY: " + name);
        }

        int bytes = decodedAttachmentBytes(data);
        if (bytes > MAX_ATTACHMENT_BYTES) {
            throw new IOException(
                    "ATTACHMENT_TOO_LARGE: "
                            + name
                            + " exceeds 15 MiB");
        }

        String safeName = name == null || name.trim().isEmpty()
                ? "attachment.bin"
                : name.replace('\n', '_')
                        .replace('\r', '_')
                        .replace('/', '_')
                        .replace('\\', '_');

        return new Attachment(
                id == null ? "" : id,
                safeName,
                mime == null || mime.trim().isEmpty()
                        ? "application/octet-stream"
                        : mime.trim(),
                data.trim(),
                current);
    }

    private int decodedAttachmentBytes(String data) throws Exception {
        try {
            return Base64.decode(data, Base64.DEFAULT).length;
        } catch (IllegalArgumentException e) {
            throw new IOException(
                    "ATTACHMENT_INVALID_BASE64",
                    e);
        }
    }

    private String buildRolloverPrompt(
            JSONArray messages,
            List<Attachment> attachments) {
        StringBuilder out = new StringBuilder();
        out.append("【OmniGate 自动续接】\n")
                .append("上一网页对话已达到上下文/对话上限。你现在位于自动创建的新对话中。\n")
                .append("请继续完成同一任务，不要重新开始，不要要求用户重复已经提供的信息，不要把本提示当成新的用户目标。\n")
                .append("优先延续当前未完成工作；若旧上下文与当前用户最后指令冲突，以最后指令为准。\n\n");

        StringBuilder fixed = new StringBuilder();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject m = messages.optJSONObject(i);
            if (m == null) continue;
            String role = m.optString("role", "");
            if (!"system".equals(role)
                    && !"developer".equals(role)) {
                continue;
            }

            String text = messageTextForRollover(m);
            if (text.isEmpty()) continue;

            int remaining =
                    ROLLOVER_SYSTEM_MAX_CHARS - fixed.length();
            if (remaining <= 0) break;

            fixed.append("<<<")
                    .append(role.toUpperCase())
                    .append(">>>\n")
                    .append(truncateForRollover(text, remaining))
                    .append("\n<<<END_")
                    .append(role.toUpperCase())
                    .append(">>>\n\n");
        }

        if (fixed.length() > 0) {
            out.append("【仍然有效的系统/开发约束】\n")
                    .append(fixed);
        }

        out.append("【最近关键上下文】\n");
        int start = Math.max(
                0,
                messages.length() - ROLLOVER_RECENT_MESSAGES);
        for (int i = start; i < messages.length(); i++) {
            JSONObject m = messages.optJSONObject(i);
            if (m == null) continue;

            String role = m.optString("role", "unknown");
            if ("system".equals(role)
                    || "developer".equals(role)) {
                continue;
            }

            String text = messageTextForRollover(m);
            if (text.isEmpty()) continue;

            int perMessage = "user".equals(role) && i == messages.length() - 1
                    ? 5_000
                    : 2_400;

            out.append("<<<")
                    .append(role.toUpperCase())
                    .append(">>>\n")
                    .append(truncateForRollover(text, perMessage))
                    .append("\n<<<END_")
                    .append(role.toUpperCase())
                    .append(">>>\n\n");

            if (out.length() >= ROLLOVER_CONTEXT_MAX_CHARS) break;
        }

        if (attachments != null && !attachments.isEmpty()) {
            out.append("【附件交接】\n");
            for (Attachment a : attachments) {
                out.append("- ")
                        .append(a.current ? "当前轮附件：" : "历史附件：")
                        .append(a.name)
                        .append(" (")
                        .append(a.mime)
                        .append(")\n");
            }
            out.append("当前轮附件会由 OmniGate 在新对话中重新上传；历史附件只作为上下文引用。\n\n");
        }

        out.append("【继续执行规则】\n")
                .append("1. 直接接着未完成任务继续，不要先复述整段交接。\n")
                .append("2. 已完成的步骤不要重做，除非继续工作确实需要。\n")
                .append("3. 若上一对话已有部分答案，避免从头重复，直接延续。\n")
                .append("4. 保留工具调用、文件处理、代码修改等任务状态的连续性。\n");

        return truncateForRollover(
                out.toString(),
                ROLLOVER_CONTEXT_MAX_CHARS);
    }

    private String buildContinuationPrompt(
            Envelope env,
            int rolloverNumber,
            String partialAnswer,
            String partialThinking) {
        StringBuilder out = new StringBuilder();
        out.append(env.rolloverPrompt);

        String answer = truncateForRollover(
                partialAnswer == null ? "" : partialAnswer.trim(),
                4_000);
        if (!answer.isEmpty()) {
            out.append("\n\n【上一对话在触顶前已经产生的部分回答】\n")
                    .append(answer)
                    .append("\n【要求】不要从头重复这部分内容，直接继续。");
        }

        String thinking = truncateForRollover(
                partialThinking == null ? "" : partialThinking.trim(),
                1_200);
        if (!thinking.isEmpty()) {
            out.append("\n\n【触顶前可见的处理中状态】\n")
                    .append(thinking);
        }

        out.append("\n\n当前为自动续接第 ")
                .append(rolloverNumber)
                .append(" 次；继续完成原任务。");

        return truncateForRollover(
                out.toString(),
                ROLLOVER_CONTEXT_MAX_CHARS);
    }

    private String messageTextForRollover(JSONObject message) {
        StringBuilder out = new StringBuilder();
        Object content = message.opt("content");

        if (content instanceof String) {
            String value = (String) content;
            int a = value.indexOf(PREFIX);
            int b = value.indexOf(SUFFIX);
            if (a >= 0 && b > a) {
                value = value.substring(0, a)
                        + "[attachment envelope omitted]"
                        + value.substring(b + SUFFIX.length());
            }
            out.append(value);
        } else if (content instanceof JSONArray) {
            JSONArray parts = (JSONArray) content;
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part == null) continue;

                String type = part.optString("type", "");
                if ("text".equals(type)
                        || "input_text".equals(type)) {
                    out.append(part.optString("text"));
                } else if ("image_url".equals(type)
                        || "input_image".equals(type)) {
                    out.append("\n[image attachment]");
                } else if ("file".equals(type)
                        || "input_file".equals(type)
                        || "attachment".equals(type)) {
                    JSONObject file = part.optJSONObject("file");
                    String name = part.optString("filename", "");
                    if (name.isEmpty() && file != null) {
                        name = file.optString(
                                "filename",
                                file.optString("name", "file"));
                    }
                    out.append("\n[file attachment: ")
                            .append(name.isEmpty() ? "file" : name)
                            .append("]");
                }
            }
        } else if (content != null && content != JSONObject.NULL) {
            out.append(String.valueOf(content));
        }

        JSONArray toolCalls = message.optJSONArray("tool_calls");
        if (toolCalls != null && toolCalls.length() > 0) {
            out.append("\nTOOL_CALLS=")
                    .append(truncateForRollover(
                            toolCalls.toString(),
                            2_000));
        }

        String toolCallId = message.optString("tool_call_id", "");
        if (!toolCallId.isEmpty()) {
            out.append("\nTOOL_CALL_ID=")
                    .append(toolCallId);
        }

        return out.toString().trim();
    }

    private String truncateForRollover(
            String value,
            int maxChars) {
        if (value == null) return "";
        String v = value.trim();
        if (maxChars <= 0 || v.length() <= maxChars) return v;

        int head = Math.max(0, (int) (maxChars * 0.68));
        int tail = Math.max(0, maxChars - head - 36);
        return v.substring(0, head)
                + "\n...[自动续接压缩了较早内容]...\n"
                + v.substring(v.length() - tail);
    }

    private String buildPrompt(JSONArray messages) {
        StringBuilder out = new StringBuilder();
        out.append("You are continuing a conversation relayed from DSH. ")
                .append("Preserve the roles, tool context, prior decisions, and current task below.\n\n");

        for (int i = 0; i < messages.length(); i++) {
            JSONObject m = messages.optJSONObject(i);
            if (m == null) continue;

            String role = m.optString("role", "unknown");
            out.append("<<<").append(role.toUpperCase()).append(">>>\n");

            Object content = m.opt("content");
            if (content instanceof String) {
                out.append((String) content);
            } else if (content instanceof JSONArray) {
                JSONArray parts = (JSONArray) content;
                for (int j = 0; j < parts.length(); j++) {
                    JSONObject part = parts.optJSONObject(j);
                    if (part == null) continue;
                    String type = part.optString("type");
                    if ("text".equals(type) || "input_text".equals(type)) {
                        out.append(part.optString("text"));
                    } else if ("image_url".equals(type)
                            || "input_image".equals(type)) {
                        out.append("[image attachment]");
                    } else if ("file".equals(type)
                            || "input_file".equals(type)
                            || "attachment".equals(type)) {
                        JSONObject file = part.optJSONObject("file");
                        String name = part.optString("filename", "");
                        if (name.isEmpty() && file != null) {
                            name = file.optString(
                                    "filename",
                                    file.optString("name", "file"));
                        }
                        out.append("[file attachment: ")
                                .append(name.isEmpty() ? "file" : name)
                                .append("]");
                    } else {
                        out.append("[content part: ")
                                .append(type.isEmpty() ? "unknown" : type)
                                .append("]");
                    }
                }
            } else if (content != null && content != JSONObject.NULL) {
                out.append(String.valueOf(content));
            }

            if (m.has("tool_calls")) {
                out.append("\nTOOL_CALLS=").append(m.optJSONArray("tool_calls"));
            }
            if (m.has("tool_call_id")) {
                out.append("\nTOOL_CALL_ID=").append(m.optString("tool_call_id"));
            }

            out.append("\n<<<END_").append(role.toUpperCase()).append(">>>\n\n");
        }

        return out.toString();
    }

    private void waitForComposer(long timeoutMs) throws Exception {
        long until = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < until) {
            String selector = composerSelector(activeProvider);
            Boolean ok = evalBoolean(
                    "(() => !!document.querySelector(" + JSONObject.quote(selector) + "))()");
            if (Boolean.TRUE.equals(ok)) return;
            Thread.sleep(300L);
        }
        throw new IOException(
                "composer_not_ready_or_login_required: " + activeProvider.displayName);
    }

    private String composerSelector(AiProvider provider) {
        if (provider == AiProvider.DEEPSEEK) return "textarea";
        if (provider == AiProvider.CHATGPT) return "#prompt-textarea";
        if (provider == AiProvider.GROK) {
            return "[contenteditable='true'][aria-label='Ask Grok anything']";
        }
        return "#search-input-box textarea.scroll-display-none";
    }

    private String sendButtonSelector(AiProvider provider) {
        if (provider == AiProvider.DEEPSEEK) {
            return "[data-testid='chat-send-button'],button[aria-label*='Send'],button[aria-label*='发送']";
        }
        if (provider == AiProvider.CHATGPT) {
            return "button[data-testid='send-button'],button[aria-label='Send prompt']";
        }
        if (provider == AiProvider.GROK) {
            return "button[data-testid='chat-submit']";
        }
        return "#search-input-box .enter";
    }

    private String generatingSelector(AiProvider provider) {
        if (provider == AiProvider.DEEPSEEK) {
            return "[data-testid='chat-stop-button'],.ds-stop-button,button[aria-label*='Stop']";
        }
        if (provider == AiProvider.CHATGPT) {
            return "button[data-testid='stop-button'],button[aria-label='Stop answering']";
        }
        if (provider == AiProvider.GROK) {
            return "button[aria-label='Stop model response']";
        }
        return ".searching,.glmsChatAvatar.isLoading,.markdown-body .loadingGif";
    }

    private void uploadAttachment(
            AiProvider provider,
            Attachment a) throws Exception {
        if (a.data == null || a.data.isEmpty()) {
            throw new IOException(
                    "ATTACHMENT_UPLOAD_FAILED: empty data for " + a.name);
        }

        String js = "(() => {" +
                "const b64=" + JSONObject.quote(a.data) + ";" +
                "const name=" + JSONObject.quote(a.name) + ";" +
                "const mime=" + JSONObject.quote(a.mime) + ";" +
                "const choose=()=>{" +
                "const all=[...document.querySelectorAll('input[type=file]')].filter(x=>!x.disabled);" +
                "if(!all.length)return null;" +
                "const image=mime.startsWith('image/');" +
                "return all.find(x=>{const a=(x.accept||'').toLowerCase();return !a||(image&&(a.includes('image')||a.includes('*/*')))||(!image&&!a.includes('image/'));})||all[0];" +
                "};" +
                "let input=choose();" +
                "if(!input){" +
                "const btn=[...document.querySelectorAll('button,[role=button]')].find(e=>/上传|附件|添加文件|照片|图片|upload|attach|add files|files|photo|image/i.test((e.innerText||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')));" +
                "if(btn)btn.click();" +
                "input=choose();" +
                "}" +
                "if(!input)return 'NO_FILE_INPUT';" +
                "const bin=atob(b64);" +
                "const u8=new Uint8Array(bin.length);" +
                "for(let i=0;i<bin.length;i++)u8[i]=bin.charCodeAt(i);" +
                "const file=new File([u8],name,{type:mime});" +
                "const dt=new DataTransfer();" +
                "dt.items.add(file);" +
                "try{input.files=dt.files;}catch(_){return 'FILES_ASSIGN_FAILED';}" +
                "input.dispatchEvent(new Event('input',{bubbles:true}));" +
                "input.dispatchEvent(new Event('change',{bubbles:true}));" +
                "return 'OK:'+input.files.length;" +
                "})()";

        String result = eval(js, 25_000L);
        if (result == null || !result.contains("OK:")) {
            throw new IOException(
                    "ATTACHMENT_UPLOAD_FAILED: "
                            + provider.displayName
                            + " · "
                            + a.name
                            + " ("
                            + result
                            + ")");
        }

        long until = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < until) {
            String evidenceJs = "(() => {" +
                    "const n=" + JSONObject.quote(a.name) + ";" +
                    "const body=(document.body&&document.body.innerText)||'';" +
                    "if(n&&body.includes(n))return 'NAME';" +
                    "const nodes=[...document.querySelectorAll('[class*=upload],[class*=file],[class*=attachment],[class*=preview],[data-testid*=attachment],[data-testid*=file],[aria-label*=attachment]')];" +
                    "const active=nodes.filter(e=>{const r=e.getBoundingClientRect();return r.width>0&&r.height>0;});" +
                    "return active.length>0?'DOM':'WAIT';" +
                    "})()";
            String evidence = eval(evidenceJs, 5_000L);
            if (evidence != null
                    && (evidence.contains("NAME")
                    || evidence.contains("DOM"))) {
                return;
            }
            Thread.sleep(350L);
        }

        throw new IOException(
                "ATTACHMENT_UPLOAD_FAILED: no DOM evidence for "
                        + a.name);
    }

    private void sendPrompt(
            AiProvider provider,
            String prompt) throws Exception {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IOException("send_failed: empty prompt");
        }

        int baselineUserMessages = userMessageCount(provider);
        String quoted = JSONObject.quote(prompt);
        String composer = JSONObject.quote(composerSelector(provider));
        boolean contentEditable =
                provider == AiProvider.CHATGPT
                        || provider == AiProvider.GROK;

        String fillJs;
        if (contentEditable) {
            fillJs = "(() => {" +
                    "const e=document.querySelector(" + composer + ");" +
                    "if(!e)return 'NO_COMPOSER';" +
                    "e.focus();" +
                    "const s=window.getSelection();const r=document.createRange();" +
                    "r.selectNodeContents(e);s.removeAllRanges();s.addRange(r);" +
                    "let ok=false;" +
                    "try{ok=document.execCommand('insertText',false," + quoted + ");}catch(_){}" +
                    "if(!ok||!((e.innerText||e.textContent||'').trim())){" +
                    "e.innerHTML='';const p=document.createElement('p');p.textContent=" + quoted + ";e.appendChild(p);" +
                    "}" +
                    "try{e.dispatchEvent(new InputEvent('beforeinput',{bubbles:true,inputType:'insertText',data:" + quoted + "}));}catch(_){}" +
                    "try{e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:" + quoted + "}));}catch(_){e.dispatchEvent(new Event('input',{bubbles:true}));}" +
                    "e.dispatchEvent(new Event('change',{bubbles:true}));" +
                    "const t=(e.innerText||e.textContent||'').trim();" +
                    "return t.length>0?'FILLED:'+t.length:'EMPTY';" +
                    "})()";
        } else {
            fillJs = "(() => {" +
                    "const e=document.querySelector(" + composer + ");" +
                    "if(!e)return 'NO_COMPOSER';" +
                    "const proto=Object.getPrototypeOf(e);" +
                    "const d=Object.getOwnPropertyDescriptor(proto,'value')||Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value')||Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');" +
                    "if(d&&d.set)d.set.call(e," + quoted + ");else e.value=" + quoted + ";" +
                    "e.focus();" +
                    "try{e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:" + quoted + "}));}catch(_){e.dispatchEvent(new Event('input',{bubbles:true}));}" +
                    "e.dispatchEvent(new Event('change',{bubbles:true}));" +
                    "return (e.value||'').length>0?'FILLED:'+e.value.length:'EMPTY';" +
                    "})()";
        }

        String fillResult = eval(fillJs, 10_000L);
        if (fillResult == null || !fillResult.contains("FILLED:")) {
            throw new IOException(
                    "send_failed: composer_not_filled · "
                            + provider.displayName
                            + " · "
                            + fillResult);
        }

        boolean clicked = false;
        String sendSelector = JSONObject.quote(
                sendButtonSelector(provider));
        for (int i = 0; i < 60; i++) {
            String clickResult = eval(
                    "(() => {const b=document.querySelector("
                            + sendSelector
                            + ");if(!b)return 'NO_BUTTON';"
                            + "if(b.disabled||b.getAttribute('aria-disabled')==='true')return 'DISABLED';"
                            + "b.click();return 'CLICKED';})()",
                    5_000L);
            if (clickResult != null
                    && clickResult.contains("CLICKED")) {
                clicked = true;
                break;
            }
            Thread.sleep(100L);
        }

        if (!clicked) {
            String enterResult = eval(
                    "(() => {const e=document.querySelector("
                            + composer
                            + ");if(!e)return false;"
                            + "e.focus();"
                            + "e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));"
                            + "e.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));"
                            + "return true;})()",
                    5_000L);
            if (enterResult == null
                    || (!enterResult.contains("true")
                    && !"true".equalsIgnoreCase(enterResult))) {
                throw new IOException(
                        "send_failed: no_send_control · "
                                + provider.displayName);
            }
        }

        waitForSubmissionConfirmation(
                provider,
                baselineUserMessages);
    }

    private int userMessageCount(AiProvider provider) throws Exception {
        String selector;
        if (provider == AiProvider.CHATGPT) {
            selector = "[data-message-author-role='user']";
        } else if (provider == AiProvider.GROK) {
            selector = "[data-testid='user-message'],[data-role='user']";
        } else if (provider == AiProvider.DEEPSEEK) {
            selector = "[data-role='user'],[class*=user][class*=message]";
        } else {
            selector = "[data-role='user'],[class*=question],[class*=user][class*=message]";
        }

        String raw = eval(
                "document.querySelectorAll("
                        + JSONObject.quote(selector)
                        + ").length",
                5_000L);
        if (raw == null) return 0;
        try {
            return Integer.parseInt(
                    raw.replace("\"", "").trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void waitForSubmissionConfirmation(
            AiProvider provider,
            int baselineUserMessages) throws Exception {
        String composer = JSONObject.quote(
                composerSelector(provider));
        String generating = JSONObject.quote(
                generatingSelector(provider));

        long until = System.currentTimeMillis() + 12_000L;
        while (System.currentTimeMillis() < until) {
            int count = userMessageCount(provider);
            if (count > baselineUserMessages) return;

            String state = eval(
                    "(() => {"
                            + "const g=[...document.querySelectorAll("
                            + generating
                            + ")].some(e=>{const r=e.getBoundingClientRect();return r.width>0&&r.height>0;});"
                            + "const e=document.querySelector("
                            + composer
                            + ");"
                            + "const t=e?((e.value!==undefined?e.value:(e.innerText||e.textContent||''))||'').trim():'';"
                            + "return JSON.stringify({g,empty:t.length===0});"
                            + "})()",
                    5_000L);
            String decoded = unwrapJavascriptString(state);
            try {
                JSONObject o = new JSONObject(decoded);
                if (o.optBoolean("g")
                        || o.optBoolean("empty")) {
                    return;
                }
            } catch (Exception ignored) {}

            Thread.sleep(200L);
        }

        throw new IOException(
                "send_not_confirmed: "
                        + provider.displayName);
    }

    private boolean startFreshChat(AiProvider provider) throws Exception {
        String js = "(() => {" +
                "const selectors=['[data-testid=new-chat]','[data-testid*=new-chat]','.new-session','[class*=new-session]'];" +
                "for(const s of selectors){const e=document.querySelector(s);if(e){e.click();return true;}}" +
                "const e=[...document.querySelectorAll('button,a,[role=button]')].find(x=>/新建对话|新对话|开始新对话|new chat|new conversation|start new/i.test((x.innerText||'')+' '+(x.getAttribute('aria-label')||'')+' '+(x.getAttribute('title')||'')));" +
                "if(e){e.click();return true;}" +
                "return false;" +
                "})()";

        if (Boolean.TRUE.equals(evalBoolean(js))) {
            Thread.sleep(500L);
            return true;
        }

        final AiProvider target = provider;
        CountDownLatch latch = new CountDownLatch(1);
        main.post(() -> {
            webView.loadUrl(target.url);
            latch.countDown();
        });
        if (!latch.await(5, TimeUnit.SECONDS)) return false;
        waitForProviderHost(target, 30_000L);
        return true;
    }

    private PageState readState(AiProvider provider) throws Exception {
        List<String> answerSelectors = answerSelectors(provider);
        List<String> thinkingSelectors = thinkingSelectors(provider);
        String generating = JSONObject.quote(generatingSelector(provider));

        String js = "(() => {" +
                "const vis=e=>{" +
                "if(!e||!e.isConnected)return false;" +
                "const r=e.getBoundingClientRect();const s=getComputedStyle(e);" +
                "return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';};" +
                "const last=sels=>{" +
                "for(const sel of sels){let a=[];try{a=[...document.querySelectorAll(sel)].filter(vis);}catch(_){continue;}" +
                "for(let i=a.length-1;i>=0;i--){const t=(a[i].innerText||a[i].textContent||'').trim();if(t)return t;}}" +
                "return '';};" +
                "const answer=last(" + new JSONArray(answerSelectors).toString() + ");" +
                "const thinking=last(" + new JSONArray(thinkingSelectors).toString() + ");" +
                "const generating=[...document.querySelectorAll(" + generating + ")].some(vis);" +
                "const body=(document.body&&document.body.innerText)||'';" +
                "const limit=/对话.{0,18}(达到|已达|超过|最大).{0,18}(上限|限制|长度)|上下文.{0,18}(达到|超过|已满|限制|最大)|请.{0,18}(新建|开启|开始).{0,18}对话|conversation.{0,40}(too long|reached.{0,16}limit|limit reached|maximum length)|you.{0,20}reached.{0,30}(maximum|limit)|maximum context|context.{0,24}(window|length).{0,24}(limit|exceed|full|maximum)|start a new chat|new chat to continue/i.test(body);" +
                "return JSON.stringify({answer,thinking,generating,limit,body:body.slice(-5000)});})()";

        String encoded = eval(js, 10_000L);
        if (encoded == null) throw new IOException("state_read_failed");

        String jsonText = unwrapJavascriptString(encoded);
        JSONObject o = new JSONObject(jsonText);
        return new PageState(
                o.optString("answer"),
                o.optString("thinking"),
                o.optString("body"),
                o.optBoolean("generating"),
                o.optBoolean("limit"));
    }

    private List<String> answerSelectors(AiProvider provider) {
        if (provider == AiProvider.DEEPSEEK) {
            return Arrays.asList(
                    ".ds-markdown",
                    ".answer-content-wrap:not(.text-advance-thinking-content) .markdown-body");
        }
        if (provider == AiProvider.CHATGPT) {
            return Arrays.asList(
                    "[data-message-author-role='assistant']",
                    "[data-message-author-role='assistant'] .markdown");
        }
        if (provider == AiProvider.GROK) {
            return Arrays.asList(
                    "[data-testid='assistant-message'] .response-content-markdown",
                    "[data-testid='assistant-message']");
        }
        return Arrays.asList(
                ".answer-content-wrap:not(.text-advance-thinking-content) .markdown-body",
                ".markdown-body");
    }

    private List<String> thinkingSelectors(AiProvider provider) {
        if (provider == AiProvider.DEEPSEEK) {
            return Arrays.asList(
                    ".ds-think-content",
                    ".thinking-content .markdown-body");
        }
        if (provider == AiProvider.CHATGPT) {
            return Arrays.asList(
                    "[data-testid*='reasoning']",
                    "[class*='reasoning'] [class*='markdown']");
        }
        if (provider == AiProvider.GROK) {
            return Arrays.asList(
                    "[data-testid='assistant-message'] .thinking-container",
                    "[class*='thinking']");
        }
        return Arrays.asList(
                ".thinking-content .markdown-body",
                "[class*='thinking']");
    }

    private String suffixDelta(String previous, String current) {
        if (current == null || current.isEmpty()) return "";
        if (previous == null || previous.isEmpty()) return current;
        if (current.startsWith(previous)) return current.substring(previous.length());
        return "";
    }

    private Boolean evalBoolean(String js) throws Exception {
        String result = eval(js, 10_000L);
        if (result == null) return false;
        if ("true".equalsIgnoreCase(result)) return true;
        return result.length() == 6
                && result.charAt(0) == 34
                && result.charAt(5) == 34
                && "true".equalsIgnoreCase(result.substring(1, 5));
    }

    private String eval(String js, long timeoutMs) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> ref = new AtomicReference<>();
        main.post(() -> webView.evaluateJavascript(js, value -> {
            ref.set(value);
            latch.countDown();
        }));
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IOException("javascript_timeout");
        }
        return ref.get();
    }

    private String unwrapJavascriptString(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.length() >= 2
                && v.charAt(0) == 34
                && v.charAt(v.length() - 1) == 34) {
            try {
                return new JSONArray("[" + v + "]").getString(0);
            } catch (Exception ignored) {}
        }
        return v;
    }
}
