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
        public static Result ok(String answer, String reasoning) { return new Result(answer, reasoning, null); }
        public static Result fail(String error) { return new Result("", "", error); }
    }

    private static final class Envelope {
        final String prompt;
        final List<Attachment> attachments;
        Envelope(String prompt, List<Attachment> attachments) { this.prompt = prompt; this.attachments = attachments; }
    }

    private static final class Attachment {
        final String id, name, mime, data;
        final boolean current;
        Attachment(String id, String name, String mime, String data, boolean current) {
            this.id = id; this.name = name; this.mime = mime; this.data = data; this.current = current;
        }
    }

    private static final class PageState {
        final String answer, thinking, body;
        final boolean generating, limit;
        PageState(String answer, String thinking, String body, boolean generating, boolean limit) {
            this.answer = answer; this.thinking = thinking; this.body = body; this.generating = generating; this.limit = limit;
        }
    }

    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object submitLock = new Object();

    public WebRelay(WebView webView) {
        this.webView = webView;
    }

    public Result submit(JSONObject request, StreamSink sink) {
        synchronized (submitLock) {
            try {
                Envelope envelope = parseEnvelope(request);
                return runSubmission(envelope, sink, 0);
            } catch (Throwable e) {
                return Result.fail(e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "relay error" : e.getMessage()));
            }
        }
    }

    private Result runSubmission(Envelope env, StreamSink sink, int rolloverCount) throws Exception {
        waitForComposer(25_000L);
        List<Attachment> toUpload = new ArrayList<>();
        for (Attachment a : env.attachments) if (rolloverCount > 0 || a.current) toUpload.add(a);
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
            PageState state = readState();
            if (state.limit) {
                if (rolloverCount >= MAX_ROLLOVER) throw new IOException("AUTO_ROLLOVER_FAILED: limit detected again after rollover");
                if (!startFreshChat()) throw new IOException("AUTO_ROLLOVER_FAILED: new-chat control not found");
                waitForComposer(25_000L);
                return runSubmission(env, sink, rolloverCount + 1);
            }

            if (state.generating) lastGeneratingAt = System.currentTimeMillis();

            if (sink != null) {
                String thinkDelta = suffixDelta(sentThinking, state.thinking);
                if (!thinkDelta.isEmpty()) { sink.reasoning(thinkDelta); sentThinking = state.thinking; }
                String answerDelta = suffixDelta(sentAnswer, state.answer);
                if (!answerDelta.isEmpty()) { sink.text(answerDelta); sentAnswer = state.answer; }
                if (System.currentTimeMillis() - lastHeartbeat > 15_000L) { sink.heartbeat(); lastHeartbeat = System.currentTimeMillis(); }
            }

            if (state.answer.equals(lastAnswer) && state.thinking.equals(lastThinking)) stable++;
            else stable = 0;
            lastAnswer = state.answer;
            lastThinking = state.thinking;

            boolean graceExpired = System.currentTimeMillis() - lastGeneratingAt >= END_GRACE_MS;
            if (!state.generating && graceExpired && stable >= 3 && (!state.answer.isEmpty() || !state.thinking.isEmpty())) {
                return Result.ok(state.answer, state.thinking);
            }
            Thread.sleep(POLL_MS);
        }
        throw new IOException("timeout after 600s");
    }

    private Envelope parseEnvelope(JSONObject request) throws Exception {
        JSONArray messages = request.optJSONArray("messages");
        String raw = "";
        if (messages != null) {
            for (int i = messages.length() - 1; i >= 0; i--) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                Object content = m.opt("content");
                if (content instanceof String) { raw = (String) content; break; }
            }
        }
        if (raw.isEmpty()) throw new IOException("no textual message content");
        int a = raw.indexOf(PREFIX);
        int b = raw.indexOf(SUFFIX, a < 0 ? 0 : a + PREFIX.length());
        if (a < 0 || b < 0) return new Envelope(raw, new ArrayList<>());
        String encoded = raw.substring(a + PREFIX.length(), b).trim();
        byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
        JSONObject payload = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
        String prompt = payload.optString("prompt", "");
        List<Attachment> attachments = new ArrayList<>();
        JSONArray arr = payload.optJSONArray("attachments");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.optJSONObject(i);
                if (x == null) continue;
                attachments.add(new Attachment(x.optString("id"), x.optString("name", "attachment.bin"), x.optString("mime", "application/octet-stream"), x.optString("data"), x.optBoolean("current", false)));
            }
        }
        return new Envelope(prompt, attachments);
    }

    private void waitForComposer(long timeout) throws Exception {
        long until = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < until) {
            Boolean ok = evalBoolean("(() => !!document.querySelector('textarea, [contenteditable=\\"true\\"]'))()");
            if (Boolean.TRUE.equals(ok)) return;
            Thread.sleep(300L);
        }
        throw new IOException("composer_not_ready");
    }

    private void uploadAttachment(Attachment a) throws Exception {
        if (a.data == null || a.data.isEmpty()) throw new IOException("ATTACHMENT_UPLOAD_FAILED: empty data for " + a.name);
        String js = "(() => {" +
                "const b64=" + JSONObject.quote(a.data) + ";" +
                "const name=" + JSONObject.quote(a.name) + ";" +
                "const mime=" + JSONObject.quote(a.mime) + ";" +
                "let input=document.querySelector('input[type=file]');" +
                "if(!input){const btn=[...document.querySelectorAll('button,[role=button]')].find(e=>/上传|附件|upload|attach/i.test((e.innerText||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||''))); if(btn) btn.click(); input=document.querySelector('input[type=file]');}" +
                "if(!input) return 'NO_FILE_INPUT';" +
                "const bin=atob(b64); const u8=new Uint8Array(bin.length); for(let i=0;i<bin.length;i++)u8[i]=bin.charCodeAt(i);" +
                "const file=new File([u8],name,{type:mime}); const dt=new DataTransfer(); dt.items.add(file); input.files=dt.files;" +
                "input.dispatchEvent(new Event('input',{bubbles:true})); input.dispatchEvent(new Event('change',{bubbles:true})); return 'OK:'+input.files.length;" +
                "})()";
        String result = eval(js, 25_000L);
        if (result == null || !result.contains("OK:")) throw new IOException("ATTACHMENT_UPLOAD_FAILED: " + a.name + " (" + result + ")");
        long until = System.currentTimeMillis() + 20_000L;
        while (System.currentTimeMillis() < until) {
            String evidenceJs = "(() => {const n=" + JSONObject.quote(a.name) + "; const t=(document.body&&document.body.innerText)||''; if(t.includes(n)) return 'NAME'; const els=[...document.querySelectorAll('[class*=upload],[class*=file],[class*=attachment],[class*=preview]')]; return els.length>0?'DOM':'WAIT';})()";
            String evidence = eval(evidenceJs, 5_000L);
            if (evidence != null && (evidence.contains("NAME") || evidence.contains("DOM"))) return;
            Thread.sleep(350L);
        }
        throw new IOException("ATTACHMENT_UPLOAD_FAILED: no DOM evidence for " + a.name);
    }

    private void sendPrompt(String prompt) throws Exception {
        String js = "(() => {" +
                "let e=document.querySelector('textarea');" +
                "if(e){const s=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set; s.call(e," + JSONObject.quote(prompt) + "); e.focus(); e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:" + JSONObject.quote(prompt) + "}));" +
                "setTimeout(()=>{let b=[...document.querySelectorAll('button,[role=button]')].find(x=>/发送|send/i.test((x.getAttribute('aria-label')||'')+' '+(x.getAttribute('title')||''))); if(b&&!b.disabled)b.click(); else e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));},120); return 'OK';}" +
                "e=document.querySelector('[contenteditable=\\"true\\"]'); if(!e)return 'NO_COMPOSER'; e.focus(); e.innerHTML=''; const p=document.createElement('p'); p.textContent=" + JSONObject.quote(prompt) + "; e.appendChild(p); e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:" + JSONObject.quote(prompt) + "})); setTimeout(()=>e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true})),120); return 'OK';" +
                "})()";
        String result = eval(js, 10_000L);
        if (result == null || !result.contains("OK")) throw new IOException("send_failed: " + result);
    }

    private boolean startFreshChat() throws Exception {
        String js = "(() => {const sels=['.new-session','[data-testid=new-chat]','[class*=new-session]']; for(const s of sels){const e=document.querySelector(s);if(e){e.click();return true;}} const e=[...document.querySelectorAll('button,a,[role=button]')].find(x=>/新建对话|新对话|new chat/i.test((x.innerText||'')+' '+(x.getAttribute('aria-label')||'')+' '+(x.getAttribute('title')||''))); if(e){e.click();return true;} return false;})()";
        return Boolean.TRUE.equals(evalBoolean(js));
    }

    private PageState readState() throws Exception {
        String js = "(() => {" +
                "const vis=e=>{if(!e||!e.isConnected)return false;const r=e.getBoundingClientRect();const s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';};" +
                "const last=(sels)=>{for(const sel of sels){const a=[...document.querySelectorAll(sel)].filter(vis);for(let i=a.length-1;i>=0;i--){const t=(a[i].innerText||a[i].textContent||'').trim();if(t)return t;}}return '';};" +
                "const answer=last(['.answer-content-wrap:not(.text-advance-thinking-content) .markdown-body','.ds-markdown','[class*=answer] .markdown-body']);" +
                "const thinking=last(['.thinking-content .markdown-body','.text-advance-thinking-content .markdown-body','[class*=thinking] .markdown-body']);" +
                "const generating=[...document.querySelectorAll('button,[role=button]')].some(e=>vis(e)&&/停止|stop/i.test((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '+(e.innerText||''))) || !!document.querySelector('.ds-stop-button,[data-testid=chat-stop-button]');" +
                "const body=(document.body&&document.body.innerText)||''; const limit=/对话.{0,8}(达到|已达).{0,8}上限|超过.{0,8}(上下文|长度).{0,8}限制|请.{0,8}(新建|开启).{0,8}对话|conversation.{0,20}(reached|limit)|context.{0,20}(limit|length)/i.test(body);" +
                "return JSON.stringify({answer,thinking,generating,limit,body:body.slice(-4000)});})()";
        String encoded = eval(js, 10_000L);
        if (encoded == null) throw new IOException("state_read_failed");
        String jsonText = unwrapJavascriptString(encoded);
        JSONObject o = new JSONObject(jsonText);
        return new PageState(o.optString("answer"), o.optString("thinking"), o.optString("body"), o.optBoolean("generating"), o.optBoolean("limit"));
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
        return "true".equalsIgnoreCase(result) || "\"true\"".equalsIgnoreCase(result);
    }

    private String eval(String js, long timeoutMs) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> ref = new AtomicReference<>();
        main.post(() -> webView.evaluateJavascript(js, value -> { ref.set(value); latch.countDown(); }));
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) throw new IOException("javascript_timeout");
        return ref.get();
    }

    private String unwrapJavascriptString(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.startsWith("\"") && v.endsWith("\"")) {
            try { return new JSONArray("[" + v + "]").getString(0); } catch (Exception ignored) {}
        }
        return v;
    }
}
