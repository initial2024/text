package com.omnigate.integrated;

import java.util.Arrays;

/** Ephemeral, bounded attachment. Never writes image bytes or API credentials to disk. */
public final class OverlayAttachmentDraft {
    private static byte[] content;
    private static String fileName;
    private static String mime;
    private static String status = "未选择文件";
    private static String prompt = "请描述附件内容。";
    private static int revision;
    private static String reply="";
    private static int replySourceChars=0;
    private static boolean replyTruncated=false;
    private static final int MAX_REPLY_CHARS=65536;
    private OverlayAttachmentDraft() {}

    public static synchronized void select(byte[] data, String name, String type) {
        if (data == null || data.length == 0 || data.length > 8*1024*1024)
            throw new IllegalArgumentException("Only nonempty files up to 8 MiB");
        clearBytes();
        content = Arrays.copyOf(data, data.length);
        fileName = name == null || name.trim().isEmpty() ? "attachment" : name;
        mime = type == null || type.trim().isEmpty() ? "application/octet-stream" : type;
        reply=""; replySourceChars=0; replyTruncated=false;
        status = "已选：" + fileName + " · " + content.length + " 字节";
        revision++;
    }

    private static void clearBytes() {
        if (content != null) Arrays.fill(content, (byte)0);
        content = null;
    }
    public static synchronized void clear() {
        clearBytes();
        fileName=null; mime=null; status="未选择文件";
        reply=""; replySourceChars=0; replyTruncated=false; revision++;
    }
    public static synchronized byte[] bytes() {
        return content == null ? null : Arrays.copyOf(content, content.length);
    }
    public static synchronized String name() { return fileName; }
    public static synchronized String mime() { return mime; }
    public static synchronized String status() { return status; }
    public static synchronized int revision() { return revision; }
    public static synchronized void report(String s) {
        status = s; revision++;
    }
    public static synchronized String reply() { return reply; }
    public static synchronized int replySourceChars() { return replySourceChars; }
    public static synchronized int replyStoredChars() { return reply.length(); }
    public static synchronized boolean replyTruncated() { return replyTruncated; }
    /** Keep complete recoverable text up to a clearly reported safety bound;
     * never quietly cut a reply at 16k or 8k characters.
     */
    public static synchronized void setReply(String answer) {
        String source=answer==null?"":answer;
        replySourceChars=source.length();
        int length=Math.min(replySourceChars,MAX_REPLY_CHARS);
        if(length>0 && length<replySourceChars &&
                Character.isHighSurrogate(source.charAt(length-1)))length--;
        reply=source.substring(0,length);
        replyTruncated=length<replySourceChars;
        revision++;
    }
    public static synchronized String prompt() { return prompt; }
    public static synchronized void setPrompt(String s) {
        prompt = s == null ? "" : s.substring(0, Math.min(s.length(), 1200));
    }
}
