package com.omnigate.integrated;

/** Zero-trust policy; model name and isolated-browser selection never prove a main login. */
final class MainAccountRoutePolicy {
    enum Outcome { LEGACY_UNBOUND, VERIFIED, PROVIDER_MISMATCH,
        OWNER_UNVERIFIED, CONTEXT_UNVERIFIED }
    static final class Decision {
        final boolean allowed;
        final Outcome outcome;
        Decision(boolean allowed,Outcome outcome){
            this.allowed=allowed;this.outcome=outcome;
        }
    }
    private MainAccountRoutePolicy(){}
    static Decision decide(boolean armed,String desiredProvider,String nativeProvider,
            boolean exactMainOwner,boolean exactGeckoContext){
        if(!armed)return new Decision(true,Outcome.LEGACY_UNBOUND);
        if(desiredProvider==null||!desiredProvider.equals(nativeProvider))
            return new Decision(false,Outcome.PROVIDER_MISMATCH);
        if(!exactMainOwner)return new Decision(false,Outcome.OWNER_UNVERIFIED);
        if(!exactGeckoContext)return new Decision(false,Outcome.CONTEXT_UNVERIFIED);
        return new Decision(true,Outcome.VERIFIED);
    }
}
