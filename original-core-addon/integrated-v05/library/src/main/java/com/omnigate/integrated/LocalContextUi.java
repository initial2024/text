package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Voluntary local text-size notes, with zero web DOM / cookie inspection.
 * Models have different effective windows and server-side summarization;
 * no numeric threshold here is an official provider limit or an exact count.
 */
final class LocalContextUi {
    private LocalContextUi(){}
    private static int dp(Activity a,int n){
        return Math.round(a.getResources().getDisplayMetrics().density*n);
    }
    static void show(Activity host,String provider,int slot){
        if(host==null||host.isFinishing()||host.isDestroyed()||
            !AccountBrowserSlots.supported(provider)||slot<0||
            slot>=AccountBrowserStore.count(host,provider))return;
        LinearLayout body=new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(host,16),dp(host,8),dp(host,16),dp(host,8));
        TextView intro=new TextView(host);
        intro.setTextSize(13);
        intro.setText("默认关闭：只有你手动启用并粘贴文字后才估算 token。"+
            "不提供服务商真实剩余额度，不自动阅读网页，也不保存聊天原文。"+
            "\n跨账号统计还需要单独开启服务商总开关并逐账号同意。"+
            "交接内容只能由你手动粘贴并确认，不会混用 Cookie。");
        body.addView(intro);
        CheckBox enabled=new CheckBox(host);
        enabled.setText("开启此账号空间的本地统计（默认关闭）");
        enabled.setChecked(LocalContextLedger.enabled(host,provider,slot));
        body.addView(enabled);
        CheckBox providerEnabled=new CheckBox(host);
        providerEnabled.setText("允许此服务商跨账号统计汇总（默认关闭）");
        providerEnabled.setChecked(
            LocalContextLedger.providerAggregationEnabled(host,provider));
        body.addView(providerEnabled);
        CheckBox contribute=new CheckBox(host);
        contribute.setText("允许当前账号参与同服务商汇总（逐账号授权）");
        contribute.setChecked(LocalContextLedger.participates(host,provider,slot));
        body.addView(contribute);
        TextView label=new TextView(host);
        label.setText("当前模型上下文上限（由你核实并填写，未知填 0）");
        body.addView(label);
        EditText limit=new EditText(host);
        limit.setSingleLine(true);
        limit.setInputType(InputType.TYPE_CLASS_NUMBER);
        limit.setText(Integer.toString(LocalContextLedger.window(host,provider,slot)));
        body.addView(limit);
        CheckBox snapshot=new CheckBox(host);
        snapshot.setText("粘贴的是完整对话快照（替换此前估算）");
        snapshot.setChecked(true);
        body.addView(snapshot);
        EditText input=new EditText(host);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("手动粘贴文本 · 最多20万字符 · 用完不保存原文");
        body.addView(input);
        TextView result=new TextView(host);
        result.setTextSize(13);
        body.addView(result);
        android.widget.Button providerSummary=new android.widget.Button(host);
        providerSummary.setAllCaps(false);
        providerSummary.setText("同服务商 · 汇总本地数字 / 合并手动摘要");
        body.addView(providerSummary);
        providerSummary.setOnClickListener(v->
            LocalProviderSummaryUi.show(host,provider));
        Runnable redraw=()->{
            long used=LocalContextLedger.used(host,provider,slot);
            int max=LocalContextLedger.window(host,provider,slot);
            boolean on=LocalContextLedger.enabled(host,provider,slot);
            boolean joined=LocalContextLedger.participates(host,provider,slot);
            boolean group=LocalContextLedger.providerAggregationEnabled(host,provider);
            result.setText("此账号统计："+(on?"手动开启":"关闭，保留已有数字但不会新增")+
                "\n人工记录的估算："+(on?used+" tokens":"隐藏（未启用）")+
                "\n误差提醒线（×1.5，不是可信的上界）："+
                (on?LocalContextEstimator.upperBound(used)+" tokens":"未计算")+
                "\n由你填写的上下文窗口："+(max==0?"未知":max+" tokens")+
                "\n"+(on?LocalContextEstimator.warning(used,max):"统计关闭，不发出容量提醒")+
                "\n跨账号汇总："+(group&&joined&&on?"当前账号同意纳入":"当前账号未加入")+
                "\n不会自动发送摘要，不会自动切换登录空间。");
        };
        redraw.run();
        ScrollView scroll=new ScrollView(host);
        scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(host)
            .setTitle(provider.toUpperCase(java.util.Locale.ROOT)+" · 上下文预警")
            .setView(scroll)
            .setNegativeButton("关闭",null)
            .setNeutralButton("复制交接摘要指令",null)
            .setPositiveButton("保存数字估算",null)
            .create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                try{
                    ClipboardManager cb=(ClipboardManager)
                        host.getSystemService(Context.CLIPBOARD_SERVICE);
                    if(cb==null)throw new IllegalStateException("clipboard_unavailable");
                    cb.setPrimaryClip(ClipData.newPlainText("上下文交接摘要指令",
                        LocalContextLedger.handoffPrompt()));
                    Toast.makeText(host,
                        "仅复制指令到本资料剪贴板；请你手动粘贴并发送",
                        Toast.LENGTH_LONG).show();
                }catch(RuntimeException denied){
                    Toast.makeText(host,"系统禁止复制："+
                        denied.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{
                    String raw=limit.getText().toString().trim();
                    int maximum=raw.length()==0?0:Integer.parseInt(raw);
                    if(maximum!=0&&(maximum<4096||maximum>2000000)){
                        limit.setError("仅允许 0 或 4096–2000000");
                        return;
                    }
                    if(input.length()>LocalContextEstimator.MAX_CHARS){
                        input.setError("超出20万字符上限，请缩短文本");
                        return;
                    }
                    if(input.length()>0){
                        // Validate input before any persistence.
                        LocalContextEstimator.estimate(input.getText());
                    }
                    if(input.length()>0&&!enabled.isChecked()){
                        input.setError("先开启此账号统计，才能记录估算文本");
                        return;
                    }
                    if(contribute.isChecked()&&!enabled.isChecked()){
                        contribute.setError("此账号统计必须先开启");
                        return;
                    }
                    if(contribute.isChecked()&&!providerEnabled.isChecked()){
                        providerEnabled.setError("先开启服务商级汇总");
                        return;
                    }
                    // No browser session, storage ID or cookie changes.
                    LocalContextLedger.setEnabled(host,provider,slot,enabled.isChecked());
                    LocalContextLedger.setProviderAggregationEnabled(
                        host,provider,providerEnabled.isChecked());
                    LocalContextLedger.setParticipates(host,provider,slot,
                        enabled.isChecked()&&providerEnabled.isChecked()&&contribute.isChecked());
                    LocalContextLedger.setWindow(host,provider,slot,maximum);
                    if(input.length()>0){
                        LocalContextLedger.record(host,provider,slot,
                            input.getText(),snapshot.isChecked());
                        input.setText(""); // release plaintext from UI
                    }
                    redraw.run();
                    if(enabled.isChecked()&&LocalContextEstimator.warningPercent(
                        LocalContextLedger.used(host,provider,slot),
                        LocalContextLedger.window(host,provider,slot))>=70)
                        Toast.makeText(host,"已到本地预警线，请主动准备交接摘要",
                            Toast.LENGTH_LONG).show();
                }catch(NumberFormatException bad){
                    limit.setError("上下文窗口不是有效整数");
                }catch(RuntimeException failed){
                    Toast.makeText(host,
                        "保存失败："+failed.getClass().getSimpleName(),
                        Toast.LENGTH_LONG).show();
                }
            });
        });
        dialog.setOnDismissListener(d->input.setText(""));
        dialog.show();
    }
}
