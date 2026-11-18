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
import java.util.function.BooleanSupplier;
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
                long offset=AccountBrowserStore.virtualClockShift(context,p,s);
                long now=AccountClockFormat.simulatedNow(
                    System.currentTimeMillis(),offset);
                String full=AccountClockFormat.display(id,now);
                String label=offset==0?"空间时钟 · ":"本地虚拟时钟 · ";
                text.setText(label+(compact
                    ?AccountClockFormat.compact(id,now):full));
                text.setContentDescription("当前账号"+label+full+
                    "。仅用于 OmniGate 原生显示，不会改变网站或系统时间。");
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
            String provider,int slot,Runnable afterSave,
            BooleanSupplier currentSessionOwner){
        if(c==null||anchor==null||!AccountBrowserSlots.supported(provider)||
            slot<0||slot>=AccountBrowserStore.count(c,provider))return;
        final String expectedContext=AccountBrowserStore.contextId(c,provider,slot);
        android.widget.PopupMenu picker=new android.widget.PopupMenu(c,anchor);
        for(int i=0;i<NAMES.length;i++){
            picker.getMenu().add(0,i+1,i,NAMES[i]+" · "+ZONES[i]);
        }
        picker.setOnMenuItemClickListener(item->{
            int i=item.getItemId()-1;
            if(i<0||i>=ZONES.length)return false;
            try{
                // A PopupMenu may remain open while the overlay switches
                // accounts. Never commit a choice into the wrong context.
                if(currentSessionOwner==null||
                    !currentSessionOwner.getAsBoolean()||
                    !expectedContext.equals(
                        AccountBrowserStore.contextId(c,provider,slot))){
                    Toast.makeText(c,"账号空间已切换，时区修改已取消",
                        Toast.LENGTH_SHORT).show();
                    return true;
                }
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
        final String expectedContext=AccountBrowserStore.contextId(
            a,provider,slot);
        LinearLayout root=new LinearLayout(a);
        root.setPadding(24,16,24,16);root.setOrientation(1);
        TextView liveText=new TextView(a);
        liveText.setTextSize(16);root.addView(liveText);
        TextView note=new TextView(a);
        note.setTextSize(12);
        note.setText("此设置可以单独指定空间显示时区与虚拟年月日时间，"+
            "但仅改变 OmniGate 的本地时间标签。不会改变手机系统时钟、"+
            "网站 JavaScript 的 Date/Intl、真实时间戳、IP、Cookie 或账号登录。"+
            "使用 IANA 时区规则自动计算夏令时。"+
            "\n检测网站实际时区：在此账号的 Gecko 网页打开 "+
            TEST_URL+"，看 Date/Time、Intl 和 UTC 偏移；"+
            "第三方检测站会收到正常网页请求信息。");
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
        button(a,root,"设置此空间的本地虚拟日期和时间").setOnClickListener(v->{
            try{
                if(!expectedContext.equals(AccountBrowserStore.contextId(
                    a,provider,slot)))return;
                final String iana=AccountBrowserStore.clockTimeZone(a,provider,slot);
                final long current=AccountClockFormat.simulatedNow(
                    System.currentTimeMillis(),
                    AccountBrowserStore.virtualClockShift(a,provider,slot));
                java.time.ZonedDateTime z=java.time.Instant.ofEpochMilli(current)
                    .atZone(AccountClockFormat.zone(iana));
                android.app.DatePickerDialog date=new android.app.DatePickerDialog(
                    a,(picker,year,month,day)->{
                        new android.app.TimePickerDialog(a,
                            (time,hour,minute)->{
                                try{
                                    if(!expectedContext.equals(
                                        AccountBrowserStore.contextId(
                                            a,provider,slot)))return;
                                    // Preserve current time as the origin.
                                    // The local offset walks forward normally
                                    // after the user selects a virtual date.
                                    long shift=AccountClockFormat.shiftToLocalTime(
                                        iana,year,month+1,day,hour,minute,
                                        System.currentTimeMillis());
                                    AccountBrowserStore.setVirtualClockShift(
                                        a,provider,slot,shift);
                                    clock.refresh();
                                    Toast.makeText(a,
                                        "本空间虚拟时间已保存；不修改网页或系统时间",
                                        Toast.LENGTH_LONG).show();
                                }catch(RuntimeException invalid){
                                    Toast.makeText(a,
                                        "日期无效、处于夏令时跳时或超出前后十年范围",
                                        Toast.LENGTH_LONG).show();
                                }
                            },z.getHour(),z.getMinute(),true).show();
                    },z.getYear(),z.getMonthValue()-1,z.getDayOfMonth());
                date.setTitle("仅此空间的本地虚拟日期（不修改网站）");
                date.show();
            }catch(RuntimeException invalid){
                Toast.makeText(a,"本地虚拟时钟不可设置",
                    Toast.LENGTH_SHORT).show();
            }
        });
        button(a,root,"恢复真实时间（本空间偏移归零）").setOnClickListener(v->{
            try{
                if(!expectedContext.equals(AccountBrowserStore.contextId(
                    a,provider,slot)))return;
                AccountBrowserStore.setVirtualClockShift(a,provider,slot,0L);
                clock.refresh();
                Toast.makeText(a,"当前空间已恢复真实时间",Toast.LENGTH_SHORT).show();
            }catch(RuntimeException invalid){
                Toast.makeText(a,"恢复失败，原设置保持不变",Toast.LENGTH_SHORT).show();
            }
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
