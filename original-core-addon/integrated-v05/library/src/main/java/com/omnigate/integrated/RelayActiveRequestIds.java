package com.omnigate.integrated;
import java.util.HashSet;

/**
 * A response id is reserved until its original native HTTP stream exits.
 * It deliberately spans WebSocket reconnect generations. No body, tool
 * output, auth token or prompt content is retained.
 */
final class RelayActiveRequestIds {
    private final HashSet<String> active=new HashSet<>();
    synchronized boolean reserve(String id) {
        if(id==null||id.isEmpty()||id.length()>256)return false;
        // A bounded set: the separate RelayRequestLimiter caps active
        // native requests at six; this guard prevents same-id replays.
        return active.add(id);
    }
    synchronized void release(String id) {
        if(id!=null)active.remove(id);
    }
    synchronized int count(){return active.size();}
}
