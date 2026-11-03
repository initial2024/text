package com.omnigate.integrated;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
public final class UpstreamRiskCircuitTest {
    private static int assertions;
    private static void ok(boolean cond){
        assertions++;if(!cond)throw new AssertionError("risk case "+assertions);
    }
    private static UpstreamRiskCircuit gate(){
        return new UpstreamRiskCircuit("native",1,null);
    }
    private static void observe(UpstreamRiskCircuit gate,int status,
                                String retry,String cf,String code,
                                String remaining,String reset,long now){
        gate.observe(status,retry,cf,code,remaining,reset,now);
    }
    public static void main(String[] args) throws Exception {
        long t=1700000000000L;
        UpstreamRiskCircuit g=gate();
        ok(g.enter(t).allowed);
        ok(!g.enter(t+10).allowed);
        ok("local_concurrency_busy".equals(g.enter(t+20).code));
        g.exit();ok(g.inFlight()==0);
        ok(g.enter(t+30).allowed);g.exit();
        observe(g,429,"120",null,null,null,null,t+100);
        ok(!g.enter(t+101).allowed);
        ok(g.permission(t+120_099).retryAfterSeconds==1);
        ok(g.enter(t+120_100).allowed);g.exit();
        UpstreamRiskCircuit noHint=gate();
        observe(noHint,429,null,null,null,null,null,t);
        ok(!noHint.enter(t+1000000000L).allowed);
        ok("upstream_rate_limit_no_retry_hint".equals(noHint.holdCode()));
        noHint.clearAfterExplicitUserReview(t+1);
        ok(noHint.enter(t+2).allowed);noHint.exit();
        UpstreamRiskCircuit auth=gate();
        observe(auth,401,null,null,null,null,null,t);
        ok("upstream_auth_required".equals(auth.holdCode()));
        ok(!auth.enter(t+100000).allowed);
        observe(auth,200,null,null,null,null,null,t+100001);
        ok(!auth.enter(t+100002).allowed);
        UpstreamRiskCircuit denied=gate();
        observe(denied,403,null,null,null,null,null,t);
        ok("upstream_access_denied".equals(denied.holdCode()));
        UpstreamRiskCircuit challenged=gate();
        observe(challenged,200,null,"challenge",null,null,null,t);
        ok("provider_challenge".equals(challenged.holdCode()));
        UpstreamRiskCircuit quota=gate();
        observe(quota,429,"3",null,"enforced_spend_limit_reached",null,null,t);
        ok("upstream_quota_requires_review".equals(quota.holdCode()));
        UpstreamRiskCircuit overload=gate();
        observe(overload,503,"9",null,null,null,null,t);
        ok(!overload.enter(t+8999).allowed);
        ok(overload.enter(t+9000).allowed);overload.exit();
        UpstreamRiskCircuit unsupported=gate();
        observe(unsupported,503,null,null,null,null,null,t);
        ok(unsupported.enter(t+1).allowed);unsupported.exit();
        ok(unsupported.auditCodes().length==1);
        UpstreamRiskCircuit apiBudget=gate();
        observe(apiBudget,200,null,null,null,"0","1m30s",t);
        ok(apiBudget.permission(t+89_999).retryAfterSeconds==1);
        ok(apiBudget.enter(t+90_000).allowed);apiBudget.exit();
        UpstreamRiskCircuit claudeBudget=gate();
        observe(claudeBudget,200,null,null,null,"0",
            Instant.ofEpochMilli(t+11_000).toString(),t);
        ok(!claudeBudget.enter(t+10_999).allowed);
        ok(claudeBudget.enter(t+11_000).allowed);claudeBudget.exit();
        UpstreamRiskCircuit unknown=gate();
        observe(unknown,200,null,null,null,"0","unknown",t);
        ok(unknown.enter(t).allowed);unknown.exit();
        ok(UpstreamRiskCircuit.retryAt("12",t)==t+12_000);
        ok(UpstreamRiskCircuit.retryAt("bogus",t)==-1);
        ok(UpstreamRiskCircuit.retryAt("999999999999999999999",t)==-1);
        String rfc=ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(t+180_000),ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        ok(UpstreamRiskCircuit.retryAt(rfc,t)==t+180_000);
        ok(UpstreamRiskCircuit.providerResetAt("500ms",t)==t+500);
        ok(UpstreamRiskCircuit.providerResetAt("2h3m4s",t)==t+7_384_000);
        ok(UpstreamRiskCircuit.providerResetAt("invalid",t)==-1);
        ok(UpstreamRiskCircuit.quotaCode("credit_balance_exhausted"));
        ok(!UpstreamRiskCircuit.quotaCode("rate_limit_error"));
        UpstreamRiskCircuit privacy=new UpstreamRiskCircuit("native",1,
            (channel,code,status,until)->{
                ok(channel.equals("native"));
                ok(!code.contains("sk-"));
            });
        observe(privacy,401,"text-with-sk-secret",null,null,null,null,t);
        ok(privacy.auditCodes()[0].contains("upstream_auth_required"));
        ok(!privacy.auditCodes()[0].contains("text-with-sk-secret"));
        final UpstreamRiskCircuit.State[] durable=new UpstreamRiskCircuit.State[1];
        UpstreamRiskCircuit.StateStore mock=new UpstreamRiskCircuit.StateStore(){
            public UpstreamRiskCircuit.State load(){
                return durable[0]==null?
                    new UpstreamRiskCircuit.State(false,0,"none"):durable[0];
            }
            public void save(UpstreamRiskCircuit.State state){durable[0]=state;}
        };
        UpstreamRiskCircuit saved=new UpstreamRiskCircuit("native",1,null,mock);
        observe(saved,429,"120",null,null,null,null,t);
        ok(durable[0]!=null && durable[0].until==t+120_000);
        UpstreamRiskCircuit restarted=new UpstreamRiskCircuit("native",1,null,mock);
        ok(!restarted.permission(t+10).allowed);
        observe(restarted,401,null,null,null,null,null,t+20);
        ok(durable[0].manual);
        UpstreamRiskCircuit again=new UpstreamRiskCircuit("native",1,null,mock);
        ok(!again.permission(t+100).allowed);
        again.clearAfterExplicitUserReview(t+200);
        ok(!again.permission(t+500).allowed); // do not shorten original 120s
        ok(again.enter(t+120_000).allowed);again.exit();
        ok(!durable[0].manual);
        UpstreamRiskCircuit concurrent=gate();
        final CountDownLatch latch=new CountDownLatch(1);
        final AtomicInteger admitted=new AtomicInteger();
        Thread[] workers=new Thread[16];
        for(int i=0;i<workers.length;i++){
            workers[i]=new Thread(()->{
                try{latch.await();}catch(Exception ex){throw new RuntimeException(ex);}
                if(concurrent.enter(t).allowed)admitted.incrementAndGet();
            });
            workers[i].start();
        }
        latch.countDown();for(Thread worker:workers)worker.join();
        ok(admitted.get()==1);concurrent.exit();
        ok(concurrent.inFlight()==0);
        System.out.println("OGRISK17_SERVER_HINTS_AND_AUTH_BREAKERS=PASS");
        System.out.println("OGRISK17_ZERO_RETRY_CONCURRENCY_AND_PRIVACY=PASS");
        System.out.println("OGRISK17_ASSERTIONS="+assertions);
    }
}
