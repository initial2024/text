package com.omnigate.integrated;
public final class VoluntaryAccountBudgetTest {
    private static int checks;
    private static void require(boolean yes){
        checks++;if(!yes)throw new AssertionError("local budget "+checks);
    }
    static VoluntaryAccountBudget.Decision check(boolean finished,boolean allowed,
            boolean enabled,int budget,int slot,int... uses){
        return VoluntaryAccountBudget.evaluate(
            finished,allowed,enabled,budget,slot,uses);
    }
    public static void main(String[] args){
        require(check(false,true,true,2,0,2,0).action==
            VoluntaryAccountBudget.Action.NOT_CONFIRMED);
        require(check(true,false,true,2,0,2,0).action==
            VoluntaryAccountBudget.Action.PLATFORM_HOLD);
        require(check(true,true,false,2,0,2,0).action==
            VoluntaryAccountBudget.Action.RECORD_ONLY);
        require(check(true,true,true,0,0,2,0).action==
            VoluntaryAccountBudget.Action.RECORD_ONLY);
        require(check(true,true,true,3,0,2,0).action==
            VoluntaryAccountBudget.Action.CONTINUE);
        require(check(true,true,true,2,0,2,0).nextSlot==1);
        require(check(true,true,true,2,0,2,0).action==
            VoluntaryAccountBudget.Action.SWITCH_BROWSER);
        require(check(true,true,true,2,0,2,2,1).nextSlot==3);
        require(check(true,true,true,1,2,1,1,1).action==
            VoluntaryAccountBudget.Action.ALL_SLOTS_AT_BUDGET);
        require(check(true,true,true,1,0,1).action==
            VoluntaryAccountBudget.Action.ALL_SLOTS_AT_BUDGET);
        require(check(true,true,true,3,4,1,2,0,1,3).nextSlot==0);
        require(check(true,true,true,3,4,0,0,0,0,3).nextSlot==0);
        require(VoluntaryAccountBudget.MAX_USER_BUDGET==100);
        boolean invalid=false;
        try{check(true,true,true,101,0,101,0);}
        catch(IllegalArgumentException expected){invalid=true;}
        require(invalid);
        invalid=false;
        try{check(true,true,true,2,0,-1,0);}
        catch(IllegalArgumentException expected){invalid=true;}
        require(invalid);
        invalid=false;
        try{check(true,true,true,2,2,0,0);}
        catch(IllegalArgumentException expected){invalid=true;}
        require(invalid);
        // Blocked/challenged/auth sessions may NEVER rotate, regardless of
        // previously counted local usage or whether auto mode is enabled.
        for(int attempts=0;attempts<20;attempts++){
            require(check(true,false,true,1,0,1,0).nextSlot==-1);
        }
        System.out.println("OGPROFILE02_USER_ACK_REQUIRED=PASS");
        System.out.println("OGPROFILE02_PROVIDER_HOLD_ALWAYS_WINS=PASS");
        System.out.println("OGPROFILE02_SELF_BUDGET_ROUND_ROBIN=PASS");
        System.out.println("OGPROFILE02_ALL_SLOTS_BUDGETED_STOPS=PASS");
        System.out.println("OGPROFILE02_ASSERTIONS="+checks);
    }
}
