package com.omnigate.integrated;

import java.time.Instant;
public final class AccountBudgetAdvisorTest {
    static int checks;
    static void ok(boolean x){checks++;if(!x)throw new AssertionError("recommendation "+checks);}
    static UpstreamRiskCircuit gate(){return new UpstreamRiskCircuit("deepseek",1,null);}
    public static void main(String[] args) {
        ok(AccountBudgetAdvisor.PERCENT==80);
        ok(AccountBudgetAdvisor.conservativeUnverifiedStart()==8);
        ok(AccountBudgetAdvisor.recommendFromUserEnteredDailyReference(60)==48);
        ok(AccountBudgetAdvisor.recommendFromUserEnteredDailyReference(100)==80);
        ok(AccountBudgetAdvisor.recommendFromUserEnteredDailyReference(10)==8);
        ok(AccountBudgetAdvisor.recommendFromUserEnteredDailyReference(2)==1);
        ok(AccountBudgetAdvisor.recommendFromUserEnteredDailyReference(200)==100);
        boolean bad=false;
        try{AccountBudgetAdvisor.recommendFromUserEnteredDailyReference(1);}
        catch(IllegalArgumentException expected){bad=true;}
        ok(bad);
        bad=false;
        try{AccountBudgetAdvisor.recommendFromUserEnteredDailyReference(100001);}
        catch(IllegalArgumentException expected){bad=true;}
        ok(bad);
        long now=1700000000000L;
        UpstreamRiskCircuit request=gate();
        request.observeOfficialHeadroom("13","60","1m",false,now);
        ok(request.permission(now).allowed); // 47/60, not at 80%
        request.observeOfficialHeadroom("12","60","1m",false,now);
        ok(!request.permission(now).allowed); // 48/60 = 80%
        ok("none".equals(request.holdCode())); // cooldown, not manual ban
        ok(request.permission(now+59999).retryAfterSeconds==1);
        ok(request.permission(now+60000).allowed);
        UpstreamRiskCircuit tokens=gate();
        tokens.observeOfficialHeadroom("201","1000","2m",true,now);
        ok(tokens.permission(now).allowed);
        tokens.observeOfficialHeadroom("200","1000","2m",true,now);
        ok(!tokens.permission(now).allowed);
        ok(tokens.permission(now+120000).allowed);
        UpstreamRiskCircuit invalid=gate();
        invalid.observeOfficialHeadroom("12",null,"1m",false,now);
        invalid.observeOfficialHeadroom("61","60","1m",false,now);
        invalid.observeOfficialHeadroom("12","60",null,false,now);
        invalid.observeOfficialHeadroom("garbage","60","1m",false,now);
        ok(invalid.permission(now).allowed);
        UpstreamRiskCircuit tiny=gate();
        tiny.observeOfficialHeadroom("1","2","1m",false,now);
        ok(tiny.permission(now).allowed);
        tiny.observeOfficialHeadroom("0","2","1m",false,now);
        ok(!tiny.permission(now).allowed);
        UpstreamRiskCircuit auth=gate();
        auth.observe(403,null,null,null,null,null,now);
        auth.observeOfficialHeadroom("1","60","1m",false,now);
        ok(!auth.permission(now+90000).allowed);
        ok("upstream_access_denied".equals(auth.holdCode()));
        UpstreamRiskCircuit quota=gate();
        quota.observe(429,"60",null,"insufficient_quota",null,null,now);
        quota.observeOfficialHeadroom("1","60","1m",false,now);
        ok(!quota.permission(now+90000).allowed);
        ok("upstream_quota_requires_review".equals(quota.holdCode()));
        // A shared browser platform with no API headers must NOT invent 80%.
        UpstreamRiskCircuit web=gate();
        web.observeOfficialHeadroom(null,null,null,false,now);
        ok(web.permission(now).allowed);
        System.out.println("OGBUDGET05_OFFICIAL_API_80_PERCENT_HEADROOM=PASS");
        System.out.println("OGBUDGET05_UNKNOWN_WEB_UNVERIFIED_EIGHT_PER_DAY=PASS");
        System.out.println("OGBUDGET05_429_AUTH_QUOTA_OVERRIDES_RECOMMENDATION=PASS");
        System.out.println("OGBUDGET05_ASSERTIONS="+checks);
    }
}
