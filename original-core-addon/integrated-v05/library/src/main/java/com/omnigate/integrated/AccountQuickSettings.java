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
                "打开完整账号管理",
                "重置当前空间的网页权限（不清登录）",
                AccountBrowserStore.trackingProtection(a,provider,slot)
                    ?"跟踪保护：开启（逐空间）":"跟踪保护：兼容模式（逐空间）",
                "当前账号空间 · 下载管理 / 删除文件",
                "本地上下文预警 / 复制交接摘要",
                "账号隔离安全检查（只读）",
                AccountBrowserStore.strictNavigation(a,provider,slot)
                    ?"严格外部导航：已启用":"严格外部导航：未启用（兼容优先）",
                "当前账号 · 存储 MB / 运行内存 PSS（只读）",
                AccountBrowserStore.lowMemoryOnFold(a,provider,slot)
                    ?"收起悬浮窗省内存：开启（逐账号）"
                    :"收起悬浮窗省内存：关闭（兼容优先）"
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
                if(which==4)GeckoScopedPermissionGate.offerReset(a,provider,slot);
                if(which==5)toggleTracking(a,provider,slot);
                if(which==6){
                    android.content.Intent intent=new android.content.Intent(a,
                        GeckoDownloadManagerActivity.class);
                    intent.putExtra(GeckoDownloadManagerActivity.EXTRA_CONTEXT,
                        AccountBrowserStore.contextId(a,provider,slot));
                    a.startActivity(intent);
                }
                if(which==7)LocalContextUi.show(a,provider,slot);
                if(which==8)GeckoAccountIsolationAudit.show(a,provider,slot);
                if(which==9)toggleStrictNavigation(a,provider,slot);
                if(which==10)GeckoStorageMemoryUi.show(a,provider,slot);
                if(which==11)toggleFoldMemory(a,provider,slot);
            }).setNegativeButton("取消",null).show();
    }
    private static void toggleFoldMemory(Activity a,String provider,int slot){
        boolean on=AccountBrowserStore.lowMemoryOnFold(a,provider,slot);
        new AlertDialog.Builder(a)
            .setTitle(on?"关闭收起省内存模式？":"给这个账号开启收起省内存模式？")
            .setMessage(on
                ?"关闭后，悬浮窗收起也会继续保持 GeckoSession 活跃。"+
                  "已有账号数据不改变，不需要重新登录。"
                :"仅当你主动把这个账号的悬浮窗收成小圆点时，"+
                  "调用 GeckoSession.setActive(false) 降低隐藏页面内存。"+
                  "再次展开会恢复同一个 GeckoSession，不调用刷新。"+
                  "\n注意：可能暂停正在输出的 AI 回答、音频和网页计时器。"+
                  "不要在模型生成中收起；默认关闭更适合连续运行。"+
                  "\n不会清 Cookie、缓存、文件或另一个账号的数据。")
            .setNegativeButton("取消",null)
            .setPositiveButton("保存此账号设置",(d,w)->{
                try{
                    AccountBrowserStore.setLowMemoryOnFold(
                        a,provider,slot,!on);
                    feedback(a,"已保存；下次收起/展开本账号悬浮窗时应用");
                }catch(RuntimeException rejected){
                    feedback(a,"保存失败，未修改原账号");
                }
            }).show();
    }

    private static void toggleStrictNavigation(Activity a,String provider,int slot){
        boolean current=AccountBrowserStore.strictNavigation(a,provider,slot);
        new AlertDialog.Builder(a)
            .setTitle(current?"关闭严格外部导航？":"仅此账号启用严格外部导航？")
            .setMessage(current
                ?"恢复 Gecko 默认的网站导航和第三方网页登录兼容模式。"+
                  "不会自动刷新网页，也不清除账号 Cookie。"
                :"下一次打开此账号空间后，拦截网页新窗口及应用外部链接协议，"+
                  "阻止它们绕过当前独立 GeckoSession 的归属验证。"+
                  "第三方 OAuth 弹窗或外部应用登录可能无法完成。"+
                  "\n该保护只作用于此账号，不改变其他空间、浏览器指纹或系统权限。"+
                  "\n保存不会立即刷新/关闭当前网页；手动重新打开后生效。")
            .setNegativeButton("取消",null)
            .setPositiveButton("保存此账号设置",(d,w)->{
                try{
                    AccountBrowserStore.setStrictNavigation(a,provider,slot,!current);
                    feedback(a,"仅该空间设置已保存，重新打开当前网页后生效");
                }catch(RuntimeException failure){
                    feedback(a,"保存失败，原有账号未修改");
                }
            }).show();
    }
    private static void toggleTracking(Activity a,String provider,int slot){
        boolean enabled=AccountBrowserStore.trackingProtection(a,provider,slot);
        new AlertDialog.Builder(a)
            .setTitle(enabled?"关闭此空间的跟踪保护？":"启用此空间的跟踪保护？")
            .setMessage(enabled
                ?"仅修改这个 Gecko 账号空间的下次浏览设置，可能提高网站兼容性但降低跟踪防护。"+
                  "不伪造设备、不自动切换账号、不删除登录信息。"
                :"下一次重新打开此账号空间时将启用 Gecko 原生跟踪防护。"+
                  "部分网站的单点登录或第三方资源可能受影响。"+
                  "当前已打开的网页不会自动关闭、刷新或改变权限。")
            .setNegativeButton("取消",null)
            .setPositiveButton("保存设置",(d,w)->{
                try{
                    AccountBrowserStore.setTrackingProtection(a,provider,slot,!enabled);
                    feedback(a,"已保存该空间的跟踪保护偏好；关闭并手动重新打开网页后生效");
                }catch(RuntimeException failure){feedback(a,"保存失败，设置未改变");}
            }).show();
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
