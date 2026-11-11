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
        intro.setText("本机粗略 token 统计 · 只处理你手动粘贴的文本。"+
            "无法自动获知网页模型的真实上下文窗口、剩余额度或自动压缩。"+
            "网站未显示的系统提示词、附件和多模态内容不包含在估算内。"+
            "\n不会保存聊天原文、不自动向网页发送摘要请求。");
        body.addView(intro);
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
        Runnable redraw=()->{
            long used=LocalContextLedger.used(host,provider,slot);
            int max=LocalContextLedger.window(host,provider,slot);
            result.setText("当前仅人工记录的估算："+used+" tokens"+
                "\n保守提醒线（×1.5，不代表统计误差上界）："+
                LocalContextEstimator.upperBound(used)+" tokens"+
                "\n你填写的窗口："+(max==0?"未知":max+" tokens")+
                "\n"+LocalContextEstimator.warning(used,max)+
                "\n达到阈值仅提醒，不会自动发送消息或打开其他账号。");
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
                    LocalContextLedger.setWindow(host,provider,slot,maximum);
                    if(input.length()>0){
                        LocalContextLedger.record(host,provider,slot,
                            input.getText(),snapshot.isChecked());
                        input.setText("");
                    }
                    redraw.run();
                    if(LocalContextEstimator.warningPercent(
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
