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
    private static final int MAX_ROLLOVER = 1;

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
        final List<Attachment> attachments;

        Envelope(String prompt, List<Attachment> attachments) {
            this.prompt = prompt;
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

        List<Attachment> toUpload = new ArrayList<>();
        for (Attachment a : env.attachments) {
            if (rolloverCount > 0 || a.current) toUpload.add(a);
        }
        for (Attachment a : toUpload) uploadAttachment(a);

        sendPrompt(env.prompt);

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
                    throw new IOException("AUTO_ROLLOVER_FAILED: limit detected again after rollover");
                }
                if (!startFreshChat(provider)) {
                    throw new IOException("AUTO_ROLLOVER_FAILED: new-chat control unavailable");
                }
                waitForComposer(30_000L);
                return runSubmission(provider, env, sink, rolloverCount + 1);
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
            String encoded = envelopeRaw.substring(a + PREFIX.length(), b).trim();
            byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
            JSONObject payload = new JSONObject(new String(decoded, StandardCharsets.UTF_8));

            String prompt = payload.optString("prompt", "");
            if (prompt.isEmpty()) prompt = buildPrompt(messages);

            List<Attachment> attachments = new ArrayList<>();
            JSONArray arr = payload.optJSONArray("attachments");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject x = arr.optJSONObject(i);
                    if (x == null) continue;
                    attachments.add(new Attachment(
                            x.optString("id"),
                            x.optString("name", "attachment.bin"),
                            x.optString("mime", "application/octet-stream"),
                            x.optString("data"),
                            x.optBoolean("current", false)));
                }
            }
            return new Envelope(prompt, attachments);
        }

        return new Envelope(buildPrompt(messages), new ArrayList<>());
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
                    } else {
                        out.append("[content part: ").append(part.toString()).append("]");
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
            Boolean ok = evalBoolean(
                    "(() => !!document.querySelector('textarea,[contenteditable=true],[contenteditable=plaintext-only]'))()");
            if (Boolean.TRUE.equals(ok)) return;
            Thread.sleep(300L);
        }
        throw new IOException("composer_not_ready_or_login_required: " + activeProvider.displayName);
    }

    private void uploadAttachment(Attachment a) throws Exception {
        if (a.data == null || a.data.isEmpty()) {
            throw new IOException("ATTACHMENT_UPLOAD_FAILED: empty data for " + a.name);
        }

        String js = "(() => {" +
                "const b64=" + JSONObject.quote(a.data) + ";" +
                "const name=" + JSONObject.quote(a.name) + ";" +
                "const mime=" + JSONObject.quote(a.mime) + ";" +
                "let input=document.querySelector('input[type=file]');" +
                "if(!input){" +
                "const btn=[...document.querySelectorAll('button,[role=button]')].find(e=>/上传|附件|添加文件|照片|upload|attach|add files|files|photo/i.test((e.innerText||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')));" +
                "if(btn)btn.click();" +
                "input=document.querySelector('input[type=file]');" +
                "}" +
                "if(!input)return 'NO_FILE_INPUT';" +
                "const bin=atob(b64);" +
                "const u8=new Uint8Array(bin.length);" +
                "for(let i=0;i<bin.length;i++)u8[i]=bin.charCodeAt(i);" +
                "const file=new File([u8],name,{type:mime});" +
                "const dt=new DataTransfer();" +
                "dt.items.add(file);" +
                "input.files=dt.files;" +
                "input.dispatchEvent(new Event('input',{bubbles:true}));" +
                "input.dispatchEvent(new Event('change',{bubbles:true}));" +
                "return 'OK:'+input.files.length;" +
                "})()";

        String result = eval(js, 25_000L);
        if (result == null || !result.contains("OK:")) {
            throw new IOException("ATTACHMENT_UPLOAD_FAILED: " + a.name + " (" + result + ")");
        }

        long until = System.currentTimeMillis() + 25_000L;
        while (System.currentTimeMillis() < until) {
            String evidenceJs = "(() => {" +
                    "const n=" + JSONObject.quote(a.name) + ";" +
                    "const t=(document.body&&document.body.innerText)||'';" +
                    "if(t.includes(n))return 'NAME';" +
                    "const els=[...document.querySelectorAll('[class*=upload],[class*=file],[class*=attachment],[class*=preview],[data-testid*=attachment],[data-testid*=file]')];" +
                    "return els.length>0?'DOM':'WAIT';" +
                    "})()";
            String evidence = eval(evidenceJs, 5_000L);
            if (evidence != null && (evidence.contains("NAME") || evidence.contains("DOM"))) return;
            Thread.sleep(350L);
        }

        throw new IOException("ATTACHMENT_UPLOAD_FAILED: no DOM evidence for " + a.name);
    }

    private void sendPrompt(String prompt) throws Exception {
        String quoted = JSONObject.quote(prompt);
        String js = "(() => {" +
                "let e=document.querySelector('textarea');" +
                "if(e){" +
                "const d=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');" +
                "if(d&&d.set)d.set.call(e," + quoted + ");else e.value=" + quoted + ";" +
                "e.focus();" +
                "e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:" + quoted + "}));" +
                "setTimeout(()=>{" +
                "let b=[...document.querySelectorAll('button,[role=button]')].find(x=>/发送|send|提交|submit/i.test((x.getAttribute('aria-label')||'')+' '+(x.getAttribute('title')||'')+' '+(x.innerText||''))&&!/stop|停止/i.test((x.getAttribute('aria-label')||'')+' '+(x.innerText||'')));" +
                "if(b&&!b.disabled)b.click();" +
                "else e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));" +
                "},160);" +
                "return 'OK';" +
                "}" +
                "e=document.querySelector('[contenteditable=true],[contenteditable=plaintext-only]');" +
                "if(!e)return 'NO_COMPOSER';" +
                "e.focus();" +
                "e.innerHTML='';" +
                "const p=document.createElement('p');" +
                "p.textContent=" + quoted + ";" +
                "e.appendChild(p);" +
                "e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:" + quoted + "}));" +
                "setTimeout(()=>{" +
                "let b=[...document.querySelectorAll('button,[role=button]')].find(x=>/发送|send|提交|submit/i.test((x.getAttribute('aria-label')||'')+' '+(x.getAttribute('title')||'')+' '+(x.innerText||''))&&!/stop|停止/i.test((x.getAttribute('aria-label')||'')+' '+(x.innerText||'')));" +
                "if(b&&!b.disabled)b.click();" +
                "else e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));" +
                "},160);" +
                "return 'OK';" +
                "})()";

        String result = eval(js, 10_000L);
        if (result == null || !result.contains("OK")) {
            throw new IOException("send_failed: " + result);
        }
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

        String js = "(() => {" +
                "const vis=e=>{" +
                "if(!e||!e.isConnected)return false;" +
                "const r=e.getBoundingClientRect();" +
                "const s=getComputedStyle(e);" +
                "return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';" +
                "};" +
                "const last=sels=>{" +
                "for(const sel of sels){" +
                "let a=[];try{a=[...document.querySelectorAll(sel)].filter(vis);}catch(_){continue;}" +
                "for(let i=a.length-1;i>=0;i--){" +
                "const t=(a[i].innerText||a[i].textContent||'').trim();" +
                "if(t)return t;" +
                "}" +
                "}" +
                "return '';" +
                "};" +
                "const answer=last(" + new JSONArray(answerSelectors).toString() + ");" +
                "const thinking=last(" + new JSONArray(thinkingSelectors).toString() + ");" +
                "const stopText=e=>(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '+(e.innerText||'');" +
                "const generating=[...document.querySelectorAll('button,[role=button]')].some(e=>vis(e)&&/停止|停止生成|stop generating|stop response|cancel generation|stop/i.test(stopText(e)))" +
                "||!!document.querySelector('[data-testid=stop-button],[data-testid*=stop],.ds-stop-button');" +
                "const body=(document.body&&document.body.innerText)||'';" +
                "const limit=/对话.{0,12}(达到|已达|超过).{0,12}(上限|限制)|上下文.{0,12}(达到|超过|已满|限制)|请.{0,12}(新建|开启).{0,12}对话|conversation.{0,30}(too long|reached.{0,10}limit|limit reached)|maximum context|context.{0,20}(window|length).{0,20}(limit|exceed|full)|start a new chat|new chat to continue/i.test(body);" +
                "return JSON.stringify({answer,thinking,generating,limit,body:body.slice(-5000)});" +
                "})()";

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
                    ".answer-content-wrap:not(.text-advance-thinking-content) .markdown-body",
                    ".ds-markdown",
                    "[class*=answer] .markdown-body",
                    "[data-message-author-role='assistant']");
        }
        if (provider == AiProvider.CHATGPT) {
            return Arrays.asList(
                    "[data-message-author-role='assistant']",
                    "[data-message-author-role='assistant'] .markdown",
                    "[data-message-author-role='assistant'] [class*=markdown]",
                    "[data-role='assistant']");
        }
        if (provider == AiProvider.GROK) {
            return Arrays.asList(
                    "[data-testid*=assistant]",
                    "[data-role='assistant']",
                    "[class*=assistant][class*=message]",
                    "[class*=response] [class*=markdown]",
                    "[class*=response]");
        }
        return Arrays.asList(
                "[data-role='assistant']",
                "[class*=assistant] .markdown-body",
                "[class*=assistant] [class*=content]",
                "[class*=message-content] .markdown-body",
                ".markdown-body");
    }

    private List<String> thinkingSelectors(AiProvider provider) {
        if (provider == AiProvider.DEEPSEEK) {
            return Arrays.asList(
                    ".thinking-content .markdown-body",
                    ".text-advance-thinking-content .markdown-body",
                    "[class*=thinking] .markdown-body");
        }
        if (provider == AiProvider.CHATGPT) {
            return Arrays.asList(
                    "[data-testid*=reasoning]",
                    "[class*=reasoning] [class*=markdown]",
                    "[class*=thinking] [class*=markdown]");
        }
        return Arrays.asList(
                "[data-testid*=reasoning]",
                "[class*=reasoning]",
                "[class*=thinking]");
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
        return "true".equalsIgnoreCase(result) || ""true"".equalsIgnoreCase(result);
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
        if (v.startsWith(""") && v.endsWith(""")) {
            try {
                return new JSONArray("[" + v + "]").getString(0);
            } catch (Exception ignored) {}
        }
        return v;
    }
}
