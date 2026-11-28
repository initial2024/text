package com.omnigate.integrated;
public final class AccountQuickSwitchRowsTest {
    static int checks;
    static void ok(boolean condition){
        checks++;
        if(!condition)throw new AssertionError("quick switch "+checks);
    }
    public static void main(String[] args){
        AccountQuickSwitchRows.Row[] standard=
            AccountQuickSwitchRows.build(new int[]{2,2,2,2,2},new int[]{0,1,0,1,0});
        ok(standard.length==10);
        ok("deepseek".equals(standard[0].provider)&&standard[0].slot==0);
        ok("deepseek".equals(standard[1].provider)&&standard[1].slot==1);
        ok("glm".equals(standard[2].provider)&&standard[2].slot==0);
        ok("glm".equals(standard[3].provider)&&standard[3].slot==1);
        ok("chatgpt".equals(standard[4].provider)&&standard[4].slot==0);
        ok("grok".equals(standard[7].provider)&&standard[7].slot==1);
        ok("claude".equals(standard[8].provider)&&standard[8].slot==0);
        ok("claude".equals(standard[9].provider)&&standard[9].slot==1);
        ok(standard[0].label.contains("上次选择"));
        ok(standard[3].label.contains("上次选择"));
        ok(!standard[2].label.contains("上次选择"));
        AccountQuickSwitchRows.Row[] mixed=
            AccountQuickSwitchRows.build(new int[]{1,5,2,3,4},new int[]{0,4,1,0,0});
        ok(mixed.length==15);
        ok("glm".equals(mixed[5].provider)&&mixed[5].slot==4);
        ok("grok".equals(mixed[10].provider)&&mixed[10].slot==2);
        ok("claude".equals(mixed[14].provider)&&mixed[14].slot==3);
        AccountQuickSwitchRows.Row[] max=
            AccountQuickSwitchRows.build(new int[]{12,12,12,12,12},new int[]{11,11,11,11,11});
        ok(max.length==60);
        for(int p=0;p<5;p++){
            for(int i=0;i<12;i++){
                AccountQuickSwitchRows.Row row=max[p*12+i];
                ok(AccountBrowserSlots.PROVIDERS[p].equals(row.provider)&&row.slot==i);
            }
        }
        ok(max[59].label.contains("账号 12"));
        boolean overLimit=false;
        try{AccountQuickSwitchRows.build(new int[]{1,1,1,1,13},new int[]{0,0,0,0,0});}
        catch(IllegalArgumentException expected){overLimit=true;}
        ok(overLimit);
        boolean failed=false;
        try{AccountQuickSwitchRows.build(new int[]{0,2,2,2,2},new int[]{0,0,0,0,0});}
        catch(IllegalArgumentException expected){failed=true;}
        ok(failed);
        failed=false;
        try{AccountQuickSwitchRows.build(new int[]{2,2,2,2,2},new int[]{2,0,0,0,0});}
        catch(IllegalArgumentException expected){failed=true;}
        ok(failed);
        failed=false;
        try{AccountQuickSwitchRows.build(new int[]{2,2},new int[]{0,0});}
        catch(IllegalArgumentException expected){failed=true;}
        ok(failed);
        System.out.println("OGPROFILE04_DIRECT_PROVIDER_ACCOUNT_ROWS=PASS");
        System.out.println("OGSLOTS09_SIXTY_VISIBLE_CHOICES=PASS");
        System.out.println("OGPROFILE04_UNKNOWN_OR_CORRUPT_SLOT_REJECTED=PASS");
        System.out.println("OGPROFILE04_ASSERTIONS="+checks);
    }
}
