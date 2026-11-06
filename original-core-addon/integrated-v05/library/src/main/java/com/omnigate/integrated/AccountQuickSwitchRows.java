package com.omnigate.integrated;

/**
 * Fixed source-of-truth rows for direct manual provider+account selection.
 * Slots refer only to isolated Gecko contexts. No password, login, cookies,
 * provider platform authorization state or model requests are touched.
 */
final class AccountQuickSwitchRows {
    static final class Row {
        final String provider;
        final int slot;
        final String label;
        Row(String provider,int slot,String label){
            this.provider=provider;this.slot=slot;this.label=label;
        }
    }
    private AccountQuickSwitchRows(){}
    static Row[] build(int[] counts,int[] selected){
        int n=AccountBrowserSlots.PROVIDERS.length;
        if(counts==null||selected==null||counts.length!=n||selected.length!=n)
            throw new IllegalArgumentException("provider_count_mismatch");
        int total=0;
        for(int i=0;i<n;i++){
            if(counts[i]<1||counts[i]>AccountBrowserSlots.MAX_SLOTS||
                selected[i]<0||selected[i]>=counts[i])
                throw new IllegalArgumentException("invalid_account_slot");
            total+=counts[i];
        }
        Row[] rows=new Row[total];
        int k=0;
        for(int i=0;i<n;i++){
            for(int slot=0;slot<counts[i];slot++){
                String name=AccountBrowserSlots.LABELS[i]+" · 账号 "+(slot+1);
                if(slot==selected[i])name+="（上次选择）";
                rows[k++]=new Row(AccountBrowserSlots.PROVIDERS[i],slot,name);
            }
        }
        return rows;
    }
}
