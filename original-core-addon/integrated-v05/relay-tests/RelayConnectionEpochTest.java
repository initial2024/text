package com.omnigate.integrated;

public final class RelayConnectionEpochTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("epoch check " + checks);
    }
    public static void main(String[] args) throws Exception {
        RelayConnectionEpoch gate = new RelayConnectionEpoch();
        Object first = new Object(), second = new Object();
        check(gate.begin() == -1);
        gate.start();
        long old = gate.begin();
        check(old > 0);
        check(gate.begin() == -1);
        check(gate.attach(old, first));
        check(!gate.attach(old, second));
        check(gate.opened(old, first));
        check(gate.isConnected());
        check(!gate.opened(old, second));
        check(gate.ended(old, first));
        check(!gate.ended(old, first));
        check(!gate.isConnected());
        check(gate.begin() == -1);
        check(gate.claimReconnect(old));
        check(!gate.claimReconnect(old));
        long newer = gate.begin();
        check(newer > old);
        check(gate.attach(newer, second));
        check(gate.opened(newer, second));
        check(gate.isConnected());
        check(!gate.ended(old, first));
        check(gate.isCurrent(newer, second));
        check(!gate.isCurrent(old, first));
        gate.stop();
        check(!gate.opened(newer, second));
        check(!gate.isCurrent(newer, second));
        check(!gate.claimReconnect(newer));
        gate.start();
        long broken = gate.begin();
        check(broken > newer);
        check(gate.failedBeforeAttach(broken));
        check(!gate.failedBeforeAttach(broken));
        check(gate.claimReconnect(broken));
        check(gate.begin() > broken);
        System.out.println("OGRELAY03_STALE_SOCKET_RESPONSE_ISOLATION=PASS");
        System.out.println("OGRELAY03_RECONNECT_SINGLE_OWNER_STOP_FAIL_CLOSED=PASS");
        System.out.println("OGRELAY03_CHECKS=" + checks);
    }
}
