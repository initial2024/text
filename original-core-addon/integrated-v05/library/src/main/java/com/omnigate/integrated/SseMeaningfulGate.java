package com.omnigate.integrated;

/**
 * Per-upstream-response signal. Diagnostic metrics are advisory only:
 * control, role, reasoning, usage, heartbeat and [DONE] must not trigger
 * transport success; genuine content or a tool-call delta may be forwarded.
 * No content, identifiers, credentials or payload bytes are retained.
 */
final class SseMeaningfulGate {
    private boolean textSeen;
    private boolean toolSeen;
    void observe(boolean nonemptyContent, boolean toolCallOrFinish) {
        if (nonemptyContent) textSeen=true;
        if (toolCallOrFinish) toolSeen=true;
    }
    boolean isReady() {return textSeen || toolSeen;}
    boolean hasText() {return textSeen;}
    boolean hasTool() {return toolSeen;}
}
