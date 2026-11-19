package com.omnigate.integrated;

/**
 * User-defined local budget, not platform RPM and not a model retry policy.
 * A finished-turn acknowledgment is REQUIRED before choosing another
 * auxiliary account browser slot. A provider hold always wins.
 */
final class VoluntaryAccountBudget {
    enum Action {
        NOT_CONFIRMED, PLATFORM_HOLD, RECORD_ONLY, CONTINUE,
        SWITCH_BROWSER, ALL_SLOTS_AT_BUDGET
    }
    static final int MAX_USER_BUDGET=100;
    static final class Decision {
        final Action action;
        final int nextSlot;
        Decision(Action action,int nextSlot){
            this.action=action;this.nextSlot=nextSlot;
        }
    }
    /**
     * Both user-defined windows are independently enforced at selection time.
     * This never authorizes submitting an API or web request; it only selects
     * an auxiliary isolated browser following an explicit completion click.
     */
    static Decision evaluateWithHour(boolean userConfirmedFinished,
            boolean platformAllowed,boolean autoEnabled,
            int dailyBudget,int hourlyBudget,int current,
            int[] today,int[] lastHour){
        if(!userConfirmedFinished)return new Decision(Action.NOT_CONFIRMED,-1);
        if(!platformAllowed)return new Decision(Action.PLATFORM_HOLD,-1);
        if(today==null||lastHour==null||today.length!=lastHour.length||
            today.length<1||today.length>AccountBrowserSlots.MAX_SLOTS||
            current<0||current>=today.length||
            dailyBudget<0||dailyBudget>MAX_USER_BUDGET||
            hourlyBudget<0||hourlyBudget>1000)
            throw new IllegalArgumentException("budget_or_slots");
        for(int i=0;i<today.length;i++)
            if(today[i]<0||lastHour[i]<0)throw new IllegalArgumentException("usage");
        if(!autoEnabled||(dailyBudget==0&&hourlyBudget==0))
            return new Decision(Action.RECORD_ONLY,-1);
        if(!atLimit(dailyBudget,hourlyBudget,today[current],lastHour[current]))
            return new Decision(Action.CONTINUE,-1);
        for(int n=1;n<today.length;n++){
            int next=(current+n)%today.length;
            if(!atLimit(dailyBudget,hourlyBudget,today[next],lastHour[next]))
                return new Decision(Action.SWITCH_BROWSER,next);
        }
        return new Decision(Action.ALL_SLOTS_AT_BUDGET,-1);
    }
    private static boolean atLimit(int daily,int hourly,int usedDay,int usedHour){
        return (daily>0&&usedDay>=daily)||(hourly>0&&usedHour>=hourly);
    }
    static Decision evaluate(boolean userConfirmedFinished,
            boolean platformAllowed,boolean autoEnabled,int userBudget,
            int current,int[] usageToday) {
        if(!userConfirmedFinished)
            return new Decision(Action.NOT_CONFIRMED,-1);
        if(!platformAllowed)
            return new Decision(Action.PLATFORM_HOLD,-1);
        if(usageToday==null || usageToday.length<1 ||
           usageToday.length>AccountBrowserSlots.MAX_SLOTS ||
           current<0||current>=usageToday.length)
            throw new IllegalArgumentException("slot");
        for(int used:usageToday)if(used<0)
            throw new IllegalArgumentException("usage");
        if(!autoEnabled||userBudget==0)
            return new Decision(Action.RECORD_ONLY,-1);
        if(userBudget<1||userBudget>MAX_USER_BUDGET)
            throw new IllegalArgumentException("budget");
        if(usageToday[current]<userBudget)
            return new Decision(Action.CONTINUE,-1);
        for(int offset=1;offset<usageToday.length;offset++){
            int next=(current+offset)%usageToday.length;
            if(usageToday[next]<userBudget)
                return new Decision(Action.SWITCH_BROWSER,next);
        }
        return new Decision(Action.ALL_SLOTS_AT_BUDGET,-1);
    }
}
