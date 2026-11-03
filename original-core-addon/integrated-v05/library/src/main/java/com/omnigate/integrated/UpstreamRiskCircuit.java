package com.omnigate.integrated;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fail-closed per-upstream admission/circuit policy, with zero automatic model
 * retries. Local concurrency is resource control, never a claimed platform RPM.
 * Rate windows are learned only from server-supplied standard/provider headers.
 */
final class UpstreamRiskCircuit {
    interface AuditSink {
        void record(String channel, String eventCode, int httpStatus, long untilEpochMs);
    }
    static final class Decision {
        final boolean allowed;
        final int status;
        final String code;
        final long retryAfterSeconds;
        private Decision(boolean allowed, int status, String code, long retryAfterSeconds) {
            this.allowed=allowed;this.status=status;
            this.code=code;this.retryAfterSeconds=retryAfterSeconds;
        }
    }
    private static final Pattern RESET_UNITS =
        Pattern.compile("(?i)([0-9]+)(ms|s|m|h|d)");
    private static final int LOG_CAPACITY=32;
    private final String channel;
    private final int localMaxInFlight;
    private final AuditSink sink;
    private int inFlight;
    private boolean manualHold;
    private long blockedUntilMs;
    private String blockingCode="none";
    private final ArrayDeque<String> audit=new ArrayDeque<>();

    UpstreamRiskCircuit(String channel, int localMaxInFlight, AuditSink sink) {
        if(!("native".equals(channel)||"claude".equals(channel)||
             "relay".equals(channel))||localMaxInFlight<1)
            throw new IllegalArgumentException("fixed channel or concurrency");
        this.channel=channel;
        this.localMaxInFlight=localMaxInFlight;
        this.sink=sink;
    }

    synchronized Decision enter(long nowMs) {
        Decision blocked=blockReason(nowMs);
        if(blocked!=null)return blocked;
        if(inFlight>=localMaxInFlight)
            return new Decision(false,503,"local_concurrency_busy",0);
        inFlight++;
        return new Decision(true,200,"admitted",0);
    }

    synchronized Decision permission(long nowMs) {
        Decision blocked=blockReason(nowMs);
        return blocked==null
            ?new Decision(true,200,"admitted",0):blocked;
    }

    private Decision blockReason(long nowMs) {
        if(manualHold)
            return new Decision(false,403,blockingCode,0);
        if(blockedUntilMs>nowMs){
            long remaining=blockedUntilMs-nowMs;
            long seconds=(remaining/1000)+((remaining%1000)==0?0:1);
            return new Decision(false,429,blockingCode,seconds);
        }
        return null;
    }

    synchronized void exit(){
        if(inFlight<=0)throw new IllegalStateException("unbalanced exit");
        inFlight--;
    }

    /** Observe only the status and fixed protocol headers; never log bodies. */
    synchronized void observe(
            int status, String retryAfter, String cfMitigated,
            String errorCode, String requestsRemaining, String requestsReset,
            long nowMs) {
        if("challenge".equalsIgnoreCase(trim(cfMitigated))){
            hold("provider_challenge",status,nowMs);return;
        }
        if(status==401){hold("upstream_auth_required",401,nowMs);return;}
        if(status==403){hold("upstream_access_denied",403,nowMs);return;}
        if(quotaCode(errorCode)){
            hold("upstream_quota_requires_review",status,nowMs);return;
        }
        if(status==429){
            long retryMs=retryAt(retryAfter,nowMs);
            if(retryMs<0)hold("upstream_rate_limit_no_retry_hint",429,nowMs);
            else cool("upstream_rate_limited",429,retryMs,nowMs);
            return;
        }
        if(status==503||status==529){
            long retryMs=retryAt(retryAfter,nowMs);
            if(retryMs>=0)cool("upstream_overloaded",status,retryMs,nowMs);
            else log("upstream_transient_failure_no_auto_retry",status,0,nowMs);
            return;
        }
        // Only authoritative response headers establish request pacing.
        if("0".equals(trim(requestsRemaining))){
            long until=providerResetAt(requestsReset,nowMs);
            if(until>nowMs)cool("provider_request_budget_exhausted",
                status,until,nowMs);
        }
    }

    synchronized int inFlight(){return inFlight;}
    synchronized String[] auditCodes(){return audit.toArray(new String[0]);}
    synchronized String holdCode(){return manualHold?blockingCode:"none";}

    /** Only a reviewed, explicit user action may release a permanent hold. */
    synchronized void clearAfterExplicitUserReview(long nowMs) {
        manualHold=false;
        blockedUntilMs=0;
        blockingCode="none";
        log("manual_review_reset",0,0,nowMs);
    }

    private void hold(String code,int status,long nowMs){
        manualHold=true;blockedUntilMs=0;blockingCode=code;
        log(code,status,0,nowMs);
    }
    private void cool(String code,int status,long until,long nowMs){
        if(manualHold)return;
        if(until>blockedUntilMs){blockedUntilMs=until;blockingCode=code;}
        log(code,status,until,nowMs);
    }
    private void log(String code,int status,long until,long nowMs){
        if(audit.size()==LOG_CAPACITY)audit.removeFirst();
        audit.addLast(nowMs+"|"+code+"|"+status);
        if(sink!=null)sink.record(channel,code,status,until);
    }
    static boolean quotaCode(String code){
        String c=trim(code).toLowerCase(Locale.ROOT);
        return "credit_balance_exhausted".equals(c)
            || "organization_usage_limit_exceeded".equals(c)
            || "organization_spend_limit_exceeded".equals(c)
            || "project_spend_limit_exceeded".equals(c)
            || "enforced_spend_limit_reached".equals(c)
            || "insufficient_quota".equals(c)
            || "billing_hard_limit_reached".equals(c);
    }
    private static String trim(String str){
        return str==null?"":str.trim();
    }

    /** Retry-After: delta-seconds or RFC 9110 HTTP date. No shortening. */
    static long retryAt(String raw,long nowMs){
        String s=trim(raw);
        if(s.isEmpty()||s.length()>128)return -1;
        try {
            if(s.matches("[0-9]+")){
                long seconds=Long.parseLong(s);
                return Math.addExact(nowMs,Math.multiplyExact(seconds,1000L));
            }
            long at=ZonedDateTime.parse(s,DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli();
            return Math.max(nowMs,at);
        }catch(Exception invalid){return -1;}
    }

    /** Official resets: OpenAI e.g. 6m0s, Anthropic RFC3339 UTC timestamp. */
    static long providerResetAt(String raw,long nowMs){
        String s=trim(raw);
        if(s.isEmpty()||s.length()>128)return -1;
        try {
            if(s.indexOf('T')>0)return Instant.parse(s).toEpochMilli();
            Matcher m=RESET_UNITS.matcher(s);
            long sum=0;int pos=0;
            while(m.find()){
                if(m.start()!=pos)return -1;
                long n=Long.parseLong(m.group(1));
                String unit=m.group(2).toLowerCase(Locale.ROOT);
                long factor="ms".equals(unit)?1L:"s".equals(unit)?1000L:
                    "m".equals(unit)?60_000L:"h".equals(unit)?3_600_000L:
                    86_400_000L;
                sum=Math.addExact(sum,Math.multiplyExact(n,factor));
                pos=m.end();
            }
            return pos==s.length()?Math.addExact(nowMs,sum):-1;
        }catch(Exception invalid){return -1;}
    }
}
