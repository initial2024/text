package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OGTZ04: implement native HTML <select> menus for the separate add-on
 * configuration GeckoSession ONLY. Does not intercept provider account
 * sessions, JS navigation, cookies, uploads, passwords or permissions.
 *
 * GeckoSession.PromptDelegate.onChoicePrompt must return a GeckoResult.
 * Returning null causes <select> to silently fail on embedded GeckoView.
 */
final class GeckoAddonChoicePrompts {
    private GeckoAddonChoicePrompts(){}

    private static final class ChoiceRow {
        final String id;
        final String label;
        ChoiceRow(String id,String label){this.id=id;this.label=label;}
    }

    private static void flatten(Object[] values,String parent,
            List<ChoiceRow> target,int depth)throws Exception{
        if(values==null||depth>5||target.size()>2000)return;
        for(Object v:values){
            if(v==null)continue;
            Class<?> c=v.getClass();
            boolean disabled=c.getField("disabled").getBoolean(v);
            String label=(String)c.getField("label").get(v);
            String id=(String)c.getField("id").get(v);
            Object children=c.getField("items").get(v);
            String readable=(parent.isEmpty()?"":parent+" / ")+
                (label==null?"":label.trim());
            if(children!=null){
                flatten((Object[])children,readable,target,depth+1);
            }else if(!disabled&&id!=null&&!id.isEmpty()&&
                    label!=null&&!label.trim().isEmpty()){
                target.add(new ChoiceRow(id,readable));
            }
        }
    }

    private static void settle(AtomicBoolean finished,Object result,Object prompt,
            Object selectedId){
        if(!finished.compareAndSet(false,true))return;
        try{
            Object response;
            if(selectedId instanceof String){
                response=prompt.getClass().getMethod("confirm",String.class)
                    .invoke(prompt,(String)selectedId);
            }else{
                response=prompt.getClass().getMethod("dismiss").invoke(prompt);
            }
            result.getClass().getMethod("complete",Object.class).invoke(result,response);
        }catch(Exception e){
            try{result.getClass().getMethod("completeExceptionally",Throwable.class)
                .invoke(result,new IllegalStateException("OGTZ04_CHOICE_RESPONSE_FAILED",e));
            }catch(Exception ignored){}
        }
    }

    static void install(Activity a,Object session,
            AtomicReference<AlertDialog> currentPicker)throws Exception{
        Class<?> iface=Class.forName("org.mozilla.geckoview.GeckoSession$PromptDelegate");
        Object delegate=Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},
            (proxy,method,args)->{
                String name=method.getName();
                if("hashCode".equals(name))return System.identityHashCode(proxy);
                if("equals".equals(name))return proxy==args[0];
                if("toString".equals(name))return "OGTZ04_Addon_Select_Only";
                if(!"onChoicePrompt".equals(name)||args==null||args.length!=2||
                   args[0]!=session)return null;

                final Object prompt=args[1];
                final Object result=Class.forName("org.mozilla.geckoview.GeckoResult")
                    .getConstructor().newInstance();
                final AtomicBoolean finished=new AtomicBoolean(false);
                try{
                    Class<?> typeCls=Class.forName(
                        "org.mozilla.geckoview.GeckoSession$PromptDelegate$ChoicePrompt$Type");
                    int type=prompt.getClass().getField("type").getInt(prompt);
                    int multiple=typeCls.getField("MULTIPLE").getInt(null);
                    if(type==multiple){
                        settle(finished,result,prompt,null);return result;
                    }
                    List<ChoiceRow> choices=new ArrayList<>();
                    flatten((Object[])prompt.getClass().getField("choices")
                        .get(prompt),"",choices,0);
                    if(choices.isEmpty()||a.isDestroyed()||a.isFinishing()){
                        settle(finished,result,prompt,null);return result;
                    }
                    AlertDialog previous=currentPicker.getAndSet(null);
                    if(previous!=null)previous.dismiss();

                    LinearLayout root=new LinearLayout(a);
                    root.setOrientation(LinearLayout.VERTICAL);
                    int dp=Math.round(12*a.getResources().getDisplayMetrics().density);
                    root.setPadding(dp,dp,dp,dp);
                    TextView info=new TextView(a);
                    info.setText("支持输入英文地名搜索，例如 New_York、Tokyo、London。选择后请回到插件页面点 Save & Apply。");
                    root.addView(info);
                    EditText filter=new EditText(a);
                    filter.setSingleLine(true);
                    filter.setFocusable(true);
                    filter.setFocusableInTouchMode(true);
                    filter.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
                    filter.setHint("搜索时区 / Timezone");
                    root.addView(filter);
                    ListView list=new ListView(a);
                    root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
                    ArrayList<String> names=new ArrayList<>();
                    ArrayList<ChoiceRow> shown=new ArrayList<>();
                    ArrayAdapter<String> adapter=new ArrayAdapter<>(a,
                        android.R.layout.simple_list_item_1,names);
                    list.setAdapter(adapter);
                    Runnable refresh=()->{
                        String q=filter.getText().toString().trim()
                            .toLowerCase(Locale.ROOT).replace(' ','_');
                        shown.clear();names.clear();
                        for(ChoiceRow row:choices){
                            if(q.isEmpty()||row.label.toLowerCase(Locale.ROOT)
                                .replace(' ','_').contains(q)){
                                shown.add(row);names.add(row.label);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    };
                    filter.addTextChangedListener(new TextWatcher(){
                        @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                        @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                            refresh.run();
                        }
                        @Override public void afterTextChanged(Editable e){}
                    });
                    AlertDialog picker=new AlertDialog.Builder(a)
                        .setTitle("选择网页时区")
                        .setView(root)
                        .setNegativeButton("取消",(d,w)->settle(finished,result,prompt,null))
                        .create();
                    picker.setOnDismissListener(d->{
                        currentPicker.compareAndSet(picker,null);
                        settle(finished,result,prompt,null);
                    });
                    list.setOnItemClickListener((parent,view,pos,id)->{
                        if(pos<0||pos>=shown.size())return;
                        String selected=shown.get(pos).id;
                        settle(finished,result,prompt,selected);
                        picker.dismiss();
                    });
                    currentPicker.set(picker);
                    picker.show();
                    if(picker.getWindow()!=null){
                        picker.getWindow().clearFlags(
                            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                        int width=a.getResources().getDisplayMetrics().widthPixels;
                        int height=a.getResources().getDisplayMetrics().heightPixels;
                        picker.getWindow().setLayout((int)(width*0.96f),(int)(height*0.76f));
                        picker.getWindow().setSoftInputMode(
                            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    }
                    // Android may not give a newly-shown Dialog IME ownership
                    // until the window is focused; this user-invoked picker
                    // owns its own native EditText, independent of GeckoView.
                    filter.requestFocus();
                    filter.setOnTouchListener((v,event)->{
                        if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP){
                            filter.post(()->requestNativeSearchIme(a,filter,picker));
                        }
                        return false; // preserve cursor movement and selection
                    });
                    filter.post(()->requestNativeSearchIme(a,filter,picker));
                    refresh.run();
                }catch(Exception failed){
                    settle(finished,result,prompt,null);
                }
                return result;
            });
        session.getClass().getMethod("setPromptDelegate",iface).invoke(session,delegate);
    }

    private static void requestNativeSearchIme(Activity a,EditText input,
            AlertDialog picker){
        if(!picker.isShowing()||!input.isAttachedToWindow()||
            !input.hasWindowFocus())return;
        input.requestFocus();
        android.view.inputmethod.InputMethodManager imm=
            (android.view.inputmethod.InputMethodManager)
                a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if(imm==null)return;
        imm.restartInput(input);
        imm.showSoftInput(input,
            android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    static void release(AtomicReference<AlertDialog> picker){
        AlertDialog open=picker.getAndSet(null);
        if(open!=null)open.dismiss();
    }
}
