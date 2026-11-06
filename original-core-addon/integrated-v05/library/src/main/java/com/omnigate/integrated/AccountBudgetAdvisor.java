package com.omnigate.integrated;

/**
 * Local UI advice only. This is NOT a published safe web usage threshold.
 * Official per-minute API RPM/TPM must never be converted to daily web turns.
 */
final class AccountBudgetAdvisor {
    static final int PERCENT=80;
    // A deliberately small, illustrative local starting point, not derived
    // from any platform account ceiling. Always label it unverified.
    static final int UNVERIFIED_WEB_START=8;
    static final int UNVERIFIED_HOURLY_START=2;
    static final int MAX_LOCAL_HOURLY=1000;
    private AccountBudgetAdvisor(){}
    static int recommendFromUserEnteredHourlyReference(int hourlyLimit){
        if(hourlyLimit<2||hourlyLimit>100000)
            throw new IllegalArgumentException("hourly_limit_out_of_range");
        return (int)Math.min(MAX_LOCAL_HOURLY,
            ((long)hourlyLimit*PERCENT)/100L);
    }
    static int conservativeUnverifiedHourlyStart(){
        return UNVERIFIED_HOURLY_START;
    }
    static int recommendFromUserEnteredDailyReference(int dailyLimit){
        if(dailyLimit<2||dailyLimit>100000)
            throw new IllegalArgumentException("daily_limit_out_of_range");
        long adjusted=((long)dailyLimit*PERCENT)/100L;
        return (int)Math.min((long)VoluntaryAccountBudget.MAX_USER_BUDGET,
                             adjusted);
    }
    static int conservativeUnverifiedStart(){
        return Math.min(VoluntaryAccountBudget.MAX_USER_BUDGET,
                        UNVERIFIED_WEB_START);
    }
}
