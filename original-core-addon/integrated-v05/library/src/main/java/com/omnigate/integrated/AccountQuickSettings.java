package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Single-slot manual controls, usable directly from the compact account
 * picker or the active full-page browser. No Gecko navigation or API calls.
 */
final class AccountQuickSettings {
    private AccountQuickSettings(){}
    private static int dp(Activity a,int n){
        return Math.round(a.getResources().getDisplayMetrics().density*n);
    }
    private static void feedback(Activity a,String message){
        Toast.makeText(a,message,Toast.LENGTH_SHORT).show();
    }
    static void show(Activity a,String provider,int slot){
        if(a==null||a.isFinishing()||a.isDestroyed()||
            !AccountBrowserSlots.supported(provider)||
            slot<0||slot>=AccountBrowserStore.count(a,provider))return;
        String title=AccountMetadataStore.name(a,provider,slot);
        String status=AccountMetadataStore.status(a,provider,slot,
            System.currentTimeMillis());
        new AlertDialog.Builder(a)
            .setTitle(title+" · "+provider)
            .setItems(new String[]{
                "修改名称 / 账号标签 / 备注",
                "额度状态与恢复时间",
                status.isEmpty()?"当前未标记额度":"解除额度状态 · "+status,
                "打开完整账号管理"
            },(d,which)->{
                if(which==0)edit(a,provider,slot);
                if(which==1)quota(a,provider,slot);
                if(which==2){
                    try{
                        AccountMetadataStore.setQuotaMark(a,provider,slot,"",-1,
                            System.currentTimeMillis());
                        feedback(a,"额度标记已解除；账号标签与备注保持不变");
                    }catch(RuntimeException ex){feedback(a,"解除额度标记失败");}
                }
                if(which==3)AccountManagerUi.show(a);
            }).setNegativeButton("取消",null).show();
    }
    private static LinearLayout form(Activity a){
        LinearLayout content=new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a,18),dp(a,10),dp(a,18),dp(a,6));
        return content;
    }
    private static EditText field(Activity a,LinearLayout content,
            String hint,String value,boolean single){
        EditText e=new EditText(a);
        e.setSingleLine(single);
        e.setTextSize(15);e.setTextColor(0xFF222222);
        e.setHint(hint);e.setText(value);
        content.addView(e,new LinearLayout.LayoutParams(-1,-2));
        return e;
    }
    private static void edit(Activity a,String provider,int slot){
        LinearLayout layout=form(a);
        EditText name=field(a,layout,"账号名称 · 最多28字",
            AccountMetadataStore.name(a,provider,slot),true);
        EditText tags=field(a,layout,"账号标签 · 主力、备用（最多4个）",
            AccountMetadataStore.accountTags(a,provider,slot),true);
        EditText note=field(a,layout,"备注 · 最多140字",
            AccountMetadataStore.note(a,provider,slot),false);
        TextView noteText=new TextView(a);
        noteText.setText("这些是永久账号信息，与额度状态独立。");
        noteText.setTextSize(12);noteText.setTextColor(0xFF606B80);
        layout.addView(noteText);
        AlertDialog dialog=new AlertDialog.Builder(a)
            .setTitle("编辑 · "+AccountMetadataStore.name(a,provider,slot))
            .setView(layout)
            .setNegativeButton("取消",null)
            .setPositiveButton("保存",null).create();
        dialog.setOnShowListener(ignored->dialog
            .getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{
                    AccountMetadataStore.setAccountInfo(a,provider,slot,
                        name.getText().toString(),note.getText().toString(),
                        tags.getText().toString());
                    dialog.dismiss();feedback(a,"账号名称、标签和备注已保存");
                }catch(RuntimeException ex){
                    tags.setError("最多4个账号标签，每个16字；请检查名称及备注长度");
                }
            }));
        dialog.show();
    }
    private static void quota(Activity a,String provider,int slot){
        AccountMetadataStore.QuotaPreset[] presets=AccountMetadataStore.quotaPresets(a);
        String[] choices=new String[presets.length+2];
        for(int i=0;i<presets.length;i++)choices[i]=presets[i].title();
        choices[presets.length]="自定义状态与时长…";
        choices[presets.length+1]="清除额度状态";
        // IMPORTANT: setMessage() and setItems() must NOT be combined.
        // A previous dialog did that and hid every selectable quota row.
        new AlertDialog.Builder(a)
            .setTitle("手动额度记录 · "+AccountMetadataStore.name(a,provider,slot))
            .setItems(choices,(d,which)->{
                try{
                    if(which<presets.length){
                        AccountMetadataStore.QuotaPreset preset=presets[which];
                        AccountMetadataStore.setQuotaMark(a,provider,slot,
                            preset.label,preset.hours,System.currentTimeMillis());
                        feedback(a,"已设置："+preset.title());
                    }else if(which==presets.length){
                        customQuota(a,provider,slot);
                    }else{
                        AccountMetadataStore.setQuotaMark(a,provider,slot,
                            "",-1,System.currentTimeMillis());
                        feedback(a,"已解除额度标记");
                    }
                }catch(RuntimeException ex){feedback(a,"额度记录保存失败");}
            }).setNegativeButton("取消",null).show();
    }
    private static void customQuota(Activity a,String provider,int slot){
        LinearLayout layout=form(a);
        String prev=AccountMetadataStore.quotaLabel(a,provider,slot,
            System.currentTimeMillis());
        EditText label=field(a,layout,"额度状态名称 · 最多24字",
            prev.isEmpty()?"额度已无":prev,true);
        EditText hours=field(a,layout,"恢复小时数（1–72）","5",true);
        hours.setInputType(InputType.TYPE_CLASS_NUMBER);
        CheckBox indefinite=new CheckBox(a);
        indefinite.setText("保持标记，直到手动解除");
        layout.addView(indefinite);
        CheckBox savePreset=new CheckBox(a);
        savePreset.setText("保存为常用额度预设");
        layout.addView(savePreset);
        TextView description=new TextView(a);
        description.setText("仅本机手动标记；不会读平台剩余额度，也不会自动切换账号。");
        description.setTextColor(0xFF606B80);description.setTextSize(12);
        layout.addView(description);
        AlertDialog dialog=new AlertDialog.Builder(a)
            .setTitle("自定义额度状态 + 恢复时间")
            .setView(layout).setNegativeButton("取消",null)
            .setPositiveButton("保存",null).create();
        dialog.setOnShowListener(x->dialog
            .getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{
                    int duration=indefinite.isChecked()?0:
                        Integer.parseInt(hours.getText().toString().trim());
                    if(duration<0||duration>72||
                        (!indefinite.isChecked()&&duration==0))
                        throw new IllegalArgumentException("hours");
                    String value=label.getText().toString().trim();
                    AccountMetadataStore.setQuotaMark(a,provider,slot,
                        value,duration,System.currentTimeMillis());
                    if(savePreset.isChecked())try{
                        AccountMetadataStore.saveQuotaPreset(a,value,duration);
                    }catch(RuntimeException badPreset){
                        feedback(a,"额度记录已保存，常用预设未保存");
                    }
                    dialog.dismiss();feedback(a,"额度状态已保存，不影响账号标签");
                }catch(RuntimeException ex){
                    hours.setError("恢复时间请填1–72小时；状态名称最多24字");
                }
            }));
        dialog.show();
    }
}
