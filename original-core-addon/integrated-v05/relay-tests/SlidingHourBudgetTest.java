package com.omnigate.integrated;

public final class SlidingHourBudgetTest {
    private static int checks;
    static void ok(boolean b){checks++;if(!b)throw new AssertionError("hour budget "+checks);}
    public static void main(String[] args){
        long t=1_800_000_000_000L;
        String s=SlidingHourAcknowledgements.append("",t);
        ok(SlidingHourAcknowledgements.count(s,t)==1);
        s=SlidingHourAcknowledgements.append(s,t+59*60_000L);
        ok(SlidingHourAcknowledgements.count(s,t+59*60_000L)==2);
        ok(SlidingHourAcknowledgements.count(s,t+60*60_000L)==1);
        ok(SlidingHourAcknowledgements.count(s,t+119*60_000L)==0);
        ok(AccountBudgetAdvisor.conservativeUnverifiedHourlyStart()==2);
        ok(AccountBudgetAdvisor.recommendFromUserEnteredHourlyReference(60)==48);
        ok(AccountBudgetAdvisor.recommendFromUserEnteredHourlyReference(2)==1);
        ok(AccountBudgetAdvisor.recommendFromUserEnteredHourlyReference(2000)==1000);
        VoluntaryAccountBudget.Decision d=VoluntaryAccountBudget.evaluateWithHour(
            true,true,true,8,2,0,new int[]{3,3},new int[]{2,1});
        ok(d.action==VoluntaryAccountBudget.Action.SWITCH_BROWSER&&d.nextSlot==1);
        d=VoluntaryAccountBudget.evaluateWithHour(
            true,true,true,8,2,0,new int[]{3,3},new int[]{2,2});
        ok(d.action==VoluntaryAccountBudget.Action.ALL_SLOTS_AT_BUDGET);
        d=VoluntaryAccountBudget.evaluateWithHour(
            true,false,true,8,2,0,new int[]{3,3},new int[]{2,1});
        ok(d.action==VoluntaryAccountBudget.Action.PLATFORM_HOLD);
        d=VoluntaryAccountBudget.evaluateWithHour(
            false,true,true,8,2,0,new int[]{3,3},new int[]{2,1});
        ok(d.action==VoluntaryAccountBudget.Action.NOT_CONFIRMED);
        System.out.println("OGHOUR01_SLIDING_HOUR_ACK_AND_RISK_PRIORITY=PASS");
        System.out.println("OGHOUR01_ASSERTIONS="+checks);
    }
}
