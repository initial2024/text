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
    private AccountBudgetAdvisor(){}
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
