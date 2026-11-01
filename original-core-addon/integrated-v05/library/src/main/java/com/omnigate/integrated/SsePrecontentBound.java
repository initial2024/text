package com.omnigate.integrated;

/**
 * Bounded native SSE state before the first verified assistant text or
 * actual tool-call delta. Only byte counts and state are kept; no content.
 */
final class SsePrecontentBound {
    static final int MAX_PRECONTENT_BYTES = 64 * 1024;
    enum Decision { BUFFER, START_FORWARD, FORWARD, FAIL_CLOSED_DRAIN, DRAIN }
    private int pendingBytes;
    private boolean forwarding;
    private boolean failed;

    Decision observe(int incomingBytes, boolean meaningfulDelta) {
        if (incomingBytes < 0) throw new IllegalArgumentException("negative bytes");
        if (failed) return Decision.DRAIN;
        if (forwarding) return Decision.FORWARD;
        // A real content/tool signal is authoritative even if this read
        // crosses the buffer bound. Preserve the full pending native bytes.
        if (meaningfulDelta) {
            forwarding = true;
            return Decision.START_FORWARD;
        }
        if ((long) pendingBytes + incomingBytes > MAX_PRECONTENT_BYTES) {
            failed = true;
            pendingBytes = 0;
            return Decision.FAIL_CLOSED_DRAIN;
        }
        pendingBytes += incomingBytes;
        return Decision.BUFFER;
    }

    boolean failed(){return failed;}
    boolean forwarding(){return forwarding;}
}
