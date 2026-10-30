package com.omnigate.integrated;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes only the initial, mechanically added "ChatGPT 说：" speaker label
 * from streaming chat content. No tools, reasoning, later content or any
 * non-chat endpoint is rewritten. SSE frames remain ordered and are bounded.
 *
 * A "data:" frame is normally one JSON object; if parsing is uncertain,
 * it is forwarded byte-for-byte and rewriting is disabled.
 */
final class SseSpeakerPrefixFilter {
    private static final Pattern PREFIX=Pattern.compile(
        "^\\s*(?:(?:ChatGPT|Assistant|AI)\\s*(?:说|says)?\\s*)[:：]\\s*",
        Pattern.CASE_INSENSITIVE);
    private static final int MAX_EVENTS=32;
    private static final int MAX_BYTES=64*1024;
    private static final int MAX_PREFIX_CHARS=80;
    private static final class Event {
        final byte[] raw;
        final String original;
        final JSONObject json;
        final JSONObject delta;
        final String content;
        final int firstLineEnd;
        final int jsonOffset;
        boolean changed;
        String edited;
        Event(byte[] bytes) {
            raw=bytes;
            String line=new String(bytes,StandardCharsets.UTF_8);
            original=line;
            int end=line.indexOf('\n');
            firstLineEnd=end;
            int off=line.startsWith("data:") ? 5 : -1;
            jsonOffset=off;
            JSONObject parsed=null, d=null;
            String c=null;
            if(off>=0 && end>off) {
                String text=line.substring(off,end).trim();
                try {
                    parsed=new JSONObject(text);
                    JSONArray choices=parsed.optJSONArray("choices");
                    JSONObject choice=choices==null?null:choices.optJSONObject(0);
                    d=choice==null?null:choice.optJSONObject("delta");
                    if(d!=null && d.opt("content") instanceof String)
                        c=d.optString("content","");
                }catch(Exception ignored){}
            }
            json=parsed;delta=d;content=c;
        }
        byte[] output() {
            if(!changed || json==null || firstLineEnd<0)return raw;
            String header=original.substring(0,jsonOffset);
            String tail=original.substring(firstLineEnd);
            return (header+" "+json.toString()+tail)
                .getBytes(StandardCharsets.UTF_8);
        }
    }

    private final List<Event> pending=new ArrayList<>();
    private final StringBuilder prefix=new StringBuilder();
    private int bytes;
    private boolean resolved;

    boolean resolved(){return resolved;}

    List<byte[]> accept(byte[] raw){
        if(resolved)return single(raw);
        Event event=new Event(raw);
        pending.add(event);
        bytes+=raw.length;
        if(event.content!=null)prefix.append(event.content);
        if(event.content!=null && prefix.length()>0) {
            Matcher matcher=PREFIX.matcher(prefix);
            if(matcher.find()){
                int remove=matcher.end();
                for(Event e:pending) {
                    if(e.content==null || remove<=0)continue;
                    int consumed=Math.min(remove,e.content.length());
                    if(consumed>0) {
                        e.edited=e.content.substring(consumed);
                        try{e.delta.put("content",e.edited);e.changed=true;}
                        catch(Exception ignored){e.changed=false;}
                    }
                    remove-=consumed;
                }
                return flush();
            }
            if(!plausiblePrefix(prefix.toString())){
                return flush();
            }
        }
        if(pending.size()>=MAX_EVENTS || bytes>MAX_BYTES ||
            prefix.length()>MAX_PREFIX_CHARS ||
            new String(raw,StandardCharsets.UTF_8).contains("[DONE]"))
            return flush();
        return new ArrayList<>();
    }

    List<byte[]> finish(){return flush();}

    private List<byte[]> flush(){
        resolved=true;
        List<byte[]> out=new ArrayList<>(pending.size());
        for(Event e:pending)out.add(e.output());
        pending.clear();
        prefix.setLength(0);
        bytes=0;
        return out;
    }

    private static List<byte[]> single(byte[] raw){
        List<byte[]> out=new ArrayList<>(1);out.add(raw);return out;
    }

    private static boolean plausiblePrefix(String s) {
        String q=s.trim().toLowerCase(java.util.Locale.ROOT);
        if(q.isEmpty())return true;
        for(String name:new String[]{"chatgpt","assistant","ai"}) {
            if(name.startsWith(q))return true;
            if(q.startsWith(name)){
                String suffix=q.substring(name.length());
                if(suffix.matches("\\s*(?:说\\s*|says?\\s*)?"))
                    return true;
            }
        }
        return false;
    }
}
