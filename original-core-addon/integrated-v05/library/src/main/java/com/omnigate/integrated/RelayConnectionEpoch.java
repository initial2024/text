package com.omnigate.integrated;

/** A single transport generation. Never authorizes or retries model calls. */
final class RelayConnectionEpoch {
    private long generation;
    private boolean running;
    private boolean connecting;
    private boolean connected;
    private boolean reconnectPending;
    private Object active;

    synchronized void start() { running = true; }

    synchronized long begin() {
        if (!running || connecting || active != null || reconnectPending) return -1;
        connecting = true;
        return ++generation;
    }

    synchronized boolean attach(long id, Object socket) {
        if (!running || id != generation || !connecting || active != null || socket == null)
            return false;
        active = socket;
        return true;
    }

    synchronized boolean opened(long id, Object socket) {
        if (!isCurrent(id, socket)) return false;
        connected = true;
        return true;
    }

    synchronized boolean ended(long id, Object socket) {
        if (!isCurrent(id, socket)) return false;
        active = null;
        connecting = false;
        connected = false;
        reconnectPending = true;
        return true;
    }

    synchronized boolean failedBeforeAttach(long id) {
        if (!running || id != generation || !connecting || active != null) return false;
        connecting = false;
        connected = false;
        reconnectPending = true;
        return true;
    }

    synchronized boolean claimReconnect(long id) {
        if (!running || id != generation || !reconnectPending || active != null) return false;
        reconnectPending = false;
        return true;
    }

    synchronized boolean isCurrent(long id, Object socket) {
        return running && id == generation && socket != null && active == socket;
    }

    synchronized boolean isConnected() { return running && connected; }

    synchronized void stop() {
        running = false;
        generation++;
        active = null;
        connecting = false;
        connected = false;
        reconnectPending = false;
    }
}
