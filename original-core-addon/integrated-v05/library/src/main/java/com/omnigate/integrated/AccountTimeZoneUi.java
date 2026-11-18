package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Native per-Gecko-context clock. Never changes website Date/Intl or IP. */
final class AccountTimeZoneUi {
    static final String TEST_URL="https://browserleaks.com/javascript";
    private static final String[] ZONES={
        "SYSTEM","UTC","Asia/Tokyo","Asia/Shanghai","Asia/Singapore",
        "America/New_York","America/Los_Angeles","Europe/London",
        "Europe/Berlin","Australia/Sydney"};
    private static final String[] NAMES={
        "跟随系统","UTC","东京","上海","新加坡",
        "纽约","洛杉矶","伦敦","柏林","悉尼"};
    private AccountTimeZoneUi(){}

    static final class LiveClock {
        private final Context context;
        private final TextView text;
        private final Supplier<String> provider;
        private final IntSupplier slot;
        private final boolean compact;
        private final Handler handler=new Handler(Looper.getMainLooper());
        private boolean active;
        private final Runnable tick=new Runnable(){
            @Override public void run(){
                if(!active)return;
                if(!text.isAttachedToWindow()){
                    stop();return; // No orphan UI ticker after detach or rotation.
                }
                refresh();
                handler.postDelayed(this,1000L);
            }
        };
        LiveClock(Context c,TextView label,Supplier<String> p,IntSupplier s){
            this(c,label,p,s,false);
        }
        LiveClock(Context c,TextView label,Supplier<String> p,IntSupplier s,
                boolean compact){
            context=c.getApplicationContext();text=label;provider=p;slot=s;
            this.compact=compact;
            text.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener(){
                @Override public void onViewAttachedToWindow(android.view.View v){}
                @Override public void onViewDetachedFromWindow(android.view.View v){
                    stop();
                }
            });
        }
        void refresh(){
            try{
                String p=provider.get();
                int s=slot.getAsInt();
                if(!AccountBrowserSlots.supported(p)||s<0||
                    s>=AccountBrowserStore.count(context,p)){
                    text.setText("空间时钟 · 未选择账号");return;
                }
                String id=AccountBrowserStore.clockTimeZone(context,p,s);
                long now=System.currentTimeMillis();
                String full=AccountClockFormat.display(id,now);
                text.setText("空间时钟 · "+(compact
                    ?AccountClockFormat.compact(id,now):full));
                text.setContentDescription("当前账号空间时间："+full+
                    "。点击可修改此账号显示时区，网页时区不会改变。");
            }catch(RuntimeException failed){
                text.setText("空间时钟不可读取；不会修改账号或网页");
            }
        }
        void start(){stop();active=true;tick.run();}
        void stop(){active=false;handler.removeCallbacks(tick);}
    }

    /** Overlay uses a PopupMenu anchored inside the existing WindowManager
     * view. Unlike an Activity dialog it remains available over other apps.
     * Custom IANA input remains in full-page native account settings.
     */
    static void showOverlayPicker(Context c,android.view.View anchor,
            String provider,int slot,Runnable afterSave){
        if(c==null||anchor==null||!AccountBrowserSlots.supported(provider)||
            slot<0||slot>=AccountBrowserStore.count(c,provider))return;
        AccountBrowserStore.contextId(c,provider,slot);
        android.widget.PopupMenu picker=new android.widget.PopupMenu(c,anchor);
        for(int i=0;i<NAMES.length;i++){
            picker.getMenu().add(0,i+1,i,NAMES[i]+" · "+ZONES[i]);
        }
        picker.setOnMenuItemClickListener(item->{
            int i=item.getItemId()-1;
            if(i<0||i>=ZONES.length)return false;
            try{
                AccountBrowserStore.setClockTimeZone(c,provider,slot,ZONES[i]);
                if(afterSave!=null)afterSave.run();
                Toast.makeText(c,"已保存此空间显示时区；网页时区不变",
                    Toast.LENGTH_SHORT).show();
            }catch(RuntimeException invalid){
                Toast.makeText(c,"时区设置保存失败",Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        picker.show();
    }
    private static Button button(Activity a,LinearLayout root,String label){
        Button b=new Button(a);b.setAllCaps(false);b.setText(label);
        root.addView(b);return b;
    }
    static void show(Activity a,String provider,int slot){
        if(a==null||a.isFinishing()||a.isDestroyed()||
            !AccountBrowserSlots.supported(provider)||slot<0||
            slot>=AccountBrowserStore.count(a,provider))return;
        AccountBrowserStore.contextId(a,provider,slot);
        LinearLayout root=new LinearLayout(a);
        root.setPadding(24,16,24,16);root.setOrientation(1);
        TextView liveText=new TextView(a);
        liveText.setTextSize(16);root.addView(liveText);
        TextView note=new TextView(a);
        note.setTextSize(12);
        note.setText("只改变 OmniGate 本地显示时间；不改网页 JS 时区、"+
            "浏览器指纹、IP、定位或账号 Cookie。自动使用 IANA 夏令时规则。"+
            "\n检测网站看到的时区：在此账号的 Gecko 网页打开 "+
            TEST_URL+"，看 timeZone 和 UTC 偏移。第三方检测站能接收正常网页访问数据。");
        root.addView(note);
        LiveClock clock=new LiveClock(a,liveText,()->provider,()->slot);
        button(a,root,"选择常用地区").setOnClickListener(v->
            new AlertDialog.Builder(a).setTitle("当前空间 · 显示时区")
                .setItems(NAMES,(dialog,which)->{
                    try{
                        AccountBrowserStore.setClockTimeZone(a,provider,slot,ZONES[which]);
                        clock.refresh();
                    }catch(RuntimeException failed){
                        Toast.makeText(a,"时区保存失败",Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("取消",null).show());
        button(a,root,"自定义 IANA 时区").setOnClickListener(v->{
            EditText input=new EditText(a);
            input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setText(AccountBrowserStore.clockTimeZone(a,provider,slot));
            input.setHint("America/New_York");
            AlertDialog edit=new AlertDialog.Builder(a)
                .setTitle("准确的 IANA 时区 ID").setView(input)
                .setNegativeButton("取消",null).setPositiveButton("保存",null).create();
            edit.setOnShowListener(x->edit.getButton(
                AlertDialog.BUTTON_POSITIVE).setOnClickListener(y->{
                try{
                    String zone=AccountClockFormat.validate(
                        input.getText().toString().trim());
                    AccountBrowserStore.setClockTimeZone(a,provider,slot,zone);
                    edit.dismiss();clock.refresh();
                }catch(RuntimeException bad){
                    input.setError("无效时区，例如 Europe/Paris");
                }
            }));
            edit.show();
        });
        button(a,root,"复制网站实际时区检测地址").setOnClickListener(v->{
            ClipboardManager clip=(ClipboardManager)a.getSystemService(
                Context.CLIPBOARD_SERVICE);
            if(clip!=null){
                clip.setPrimaryClip(ClipData.newPlainText("timezone",TEST_URL));
                Toast.makeText(a,"请在同一个账号网页内打开检测网址",
                    Toast.LENGTH_LONG).show();
            }
        });
        ScrollView scroll=new ScrollView(a);scroll.addView(root);
        AlertDialog dialog=new AlertDialog.Builder(a)
            .setTitle(AccountMetadataStore.name(a,provider,slot)+" · 独立时钟")
            .setView(scroll).setPositiveButton("完成",null).create();
        dialog.setOnDismissListener(d->clock.stop());
        dialog.show();clock.start();
    }
}
