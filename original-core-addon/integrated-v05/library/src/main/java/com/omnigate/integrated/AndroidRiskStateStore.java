package com.omnigate.integrated;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Private Android persistence for upstream risk states. The only persisted
 * fields are fixed channel, fixed reason code, manual hold, and epoch millis;
 * never tokens, credentials, site content, cookies, prompts or IP addresses.
 */
final class AndroidRiskStateStore implements UpstreamRiskCircuit.StateStore {
    private final String channel;
    AndroidRiskStateStore(String channel){
        if(!ProviderRiskScopes.validChannel(channel))
            throw new IllegalArgumentException("channel");
        this.channel=channel;
    }
    private SharedPreferences prefs(){
        Context ctx=GatewayApp.applicationContext();
        if(ctx==null)throw new IllegalStateException("risk_store_context_unavailable");
        return ctx.getSharedPreferences("omnigate_risk_circuit_v1",Context.MODE_PRIVATE);
    }
    @Override public UpstreamRiskCircuit.State load(){
        SharedPreferences p=prefs();
        String key=channel+"_";
        try{
            return new UpstreamRiskCircuit.State(
                p.getBoolean(key+"manual",false),
                Math.max(0,p.getLong(key+"until",0)),
                p.getString(key+"code","none"));
        }catch(ClassCastException corrupt){
            return new UpstreamRiskCircuit.State(true,0,"risk_state_invalid");
        }
    }
    @Override public void save(UpstreamRiskCircuit.State state){
        String key=channel+"_";
        boolean success=prefs().edit()
            .putBoolean(key+"manual",state.manual)
            .putLong(key+"until",state.until)
            .putString(key+"code",state.code)
            .commit();
        if(!success)throw new IllegalStateException("risk_store_write_failed");
    }
}
