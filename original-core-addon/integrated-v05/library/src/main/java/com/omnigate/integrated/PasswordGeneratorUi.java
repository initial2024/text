package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.PersistableBundle;
import android.view.WindowManager;
import android.widget.*;
import java.util.function.Consumer;

/** Generates new passwords only, never reads any webpage or existing password. */
final class PasswordGeneratorUi {
    private PasswordGeneratorUi(){}
    static void show(Activity a,Consumer<String> use){
        show(a,null,use);
    }
    static void show(Activity a,String target,Consumer<String> use){
        if(a==null||a.isFinishing()||a.isDestroyed())return;
        LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*a.getResources().getDisplayMetrics().density);
        box.setPadding(pad,pad,pad,pad);
        TextView length=new TextView(a);length.setText("长度：24");
        box.addView(length);
        SeekBar slider=new SeekBar(a);slider.setMax(52);slider.setProgress(12);box.addView(slider);
        CheckBox lower=option(a,box,"小写字母",true),upper=option(a,box,"大写字母",true),
            digits=option(a,box,"数字",true),symbols=option(a,box,"特殊符号",true),
            ambiguous=option(a,box,"排除易混淆字符",true);
        TextView result=new TextView(a);result.setTextIsSelectable(false);box.addView(result);
        TextView strength=new TextView(a);box.addView(strength);
        Button regenerate=new Button(a);regenerate.setText("重新生成");box.addView(regenerate);
        final String[] candidate={null};
        Runnable generate=()->{
            candidate[0]=null;result.setText("");
            int n=slider.getProgress()+12;length.setText("长度："+n);
            try{
                String password=LocalPasswordAdvice.generateCustom(n,lower.isChecked(),upper.isChecked(),
                    digits.isChecked(),symbols.isChecked(),ambiguous.isChecked());
                candidate[0]=password;result.setText(password);
                int score=LocalPasswordAdvice.score(password,"",false);
                strength.setText("本地强度 "+score+"/100 · "+LocalPasswordAdvice.label(score)+
                    (n<15?" · 建议至少15字符":""));
            }catch(RuntimeException e){strength.setText("至少选择一种字符类别");}
        };
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){generate.run();}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        for(CheckBox c:new CheckBox[]{lower,upper,digits,symbols,ambiguous})
            c.setOnCheckedChangeListener((v,checked)->generate.run());
        regenerate.setOnClickListener(v->generate.run());
        ScrollView scroll=new ScrollView(a);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(target==null?"生成新密码":"为 "+target+" 生成新密码")
            .setView(scroll).setNegativeButton("取消",null)
            .setPositiveButton(use!=null?"使用此密码":"复制新密码",null).create();
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnDismissListener(v->{candidate[0]=null;result.setText("");});
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String password=candidate[0];if(password==null)return;
            if(use!=null){use.accept(password);dialog.dismiss();return;}
            new AlertDialog.Builder(a).setTitle("复制到设备剪贴板？")
                .setMessage("仅复制刚生成的新密码。设备剪贴板可能被其他账号空间或应用访问；这不是隔离自动填充。请手动粘贴到目标注册表单，使用后清除剪贴板。不会自动保存或提交。")
                .setNegativeButton("取消",null).setPositiveButton("确认复制",(d,w)->{
                    if(!dialog.isShowing()||a.isFinishing()||a.isDestroyed())return;
                    ClipboardManager clipboard=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);
                    if(clipboard==null)return;
                    ClipData clip=ClipData.newPlainText("新生成的密码",password);
                    PersistableBundle extras=new PersistableBundle();
                    extras.putBoolean("android.content.extra.IS_SENSITIVE",true);
                    clip.getDescription().setExtras(extras);
                    clipboard.setPrimaryClip(clip);dialog.dismiss();
                }).show();
        });
        generate.run();
    }
    private static CheckBox option(Activity a,LinearLayout box,String name,boolean checked){
        CheckBox c=new CheckBox(a);c.setText(name);c.setChecked(checked);box.addView(c);return c;
    }
}
