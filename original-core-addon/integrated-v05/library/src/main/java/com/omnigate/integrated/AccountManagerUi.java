package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Manual account controls. No provider network traffic, background login, or auto-rotation. */
final class AccountManagerUi {
    private AccountManagerUi(){}
    static void show(Activity activity){
        if(activity!=null&&!activity.isFinishing()&&!activity.isDestroyed())
            new Panel(activity).open();
    }
    private static final class Panel {
        private final Activity host;
        private final Dialog dialog;
        private final LinearLayout rows;
        private final EditText search;
        private final Spinner providerFilter,statusFilter;
        private final TextView countLabel;
        private final Set<String> selected=new LinkedHashSet<>();
        private final Handler handler=new Handler(Looper.getMainLooper());
        private final Runnable timer=()->render();
        private final ArrayList<Target> visible=new ArrayList<>();
        private Panel(Activity host){
            this.host=host;
            dialog=new Dialog(host,android.R.style.Theme_Material_NoActionBar);
            LinearLayout shell=new LinearLayout(host);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.setPadding(dp(12),dp(14),dp(12),dp(8));
            shell.setBackgroundColor(0xFF0B1024);
            add(shell,text("独立账号管理",22,Color.WHITE),2);
            add(shell,text("本机名称、备注及人工额度标记 · 不会因额度到期自动切换或发送消息",12,0xFFBEC8E1),7);
            search=new EditText(host);
            search.setSingleLine(true);
            search.setTextSize(14);
            search.setHint("搜索账号名称 / 备注 / 服务商");
            search.setHintTextColor(0xFF9BAACC);
            search.setTextColor(Color.WHITE);
            add(shell,search,5);
            LinearLayout filters=new LinearLayout(host);
            shell.addView(filters);
            providerFilter=spinner(new String[]{"全部平台","DeepSeek","GLM","ChatGPT","Grok","Claude"});
            statusFilter=spinner(new String[]{"全部状态","可用","额度已无"});
            filters.addView(providerFilter,new LinearLayout.LayoutParams(0,dp(47),1.1f));
            filters.addView(statusFilter,new LinearLayout.LayoutParams(0,dp(47),1f));
            LinearLayout selectBar=new LinearLayout(host);
            shell.addView(selectBar);
            action(selectBar,"全选当前结果",()->{
                for(Target target:visible)selected.add(target.key());
                render();
            });
            action(selectBar,"反选当前结果",()->{
                for(Target target:visible){
                    if(!selected.remove(target.key()))selected.add(target.key());
                }
                render();
            });
            action(selectBar,"清除选择",()->{selected.clear();render();});
            ScrollView scroll=new ScrollView(host);
            rows=new LinearLayout(host);rows.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(rows);
            shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            countLabel=text("",12,0xFFB7C6E5);
            add(shell,countLabel,4);
            LinearLayout bulk=new LinearLayout(host);
            shell.addView(bulk);
            action(bulk,"关闭选中网页",()->confirm(false));
            action(bulk,"清空选中存档",()->confirm(true));
            Button done=button("返回");
            shell.addView(done,new LinearLayout.LayoutParams(-1,dp(42)));
            done.setOnClickListener(v->dialog.dismiss());
            dialog.setContentView(shell);
            dialog.setOnDismissListener(x->handler.removeCallbacks(timer));
            search.addTextChangedListener(new TextWatcher(){
                @Override public void beforeTextChanged(CharSequence s,int a,int c,int d){}
                @Override public void onTextChanged(CharSequence s,int a,int b,int c){render();}
                @Override public void afterTextChanged(Editable e){}
            });
            android.widget.AdapterView.OnItemSelectedListener filterListener=
                new android.widget.AdapterView.OnItemSelectedListener(){
                    @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int i,long id){render();}
                    @Override public void onNothingSelected(android.widget.AdapterView<?> p){render();}
                };
            providerFilter.setOnItemSelectedListener(filterListener);
            statusFilter.setOnItemSelectedListener(filterListener);
        }
        private int dp(int n){return Math.round(host.getResources().getDisplayMetrics().density*n);}
        private String key(String provider,int slot){return provider+"#"+slot;}
        private final class Target {
            final String provider;final int slot;
            Target(String provider,int slot){this.provider=provider;this.slot=slot;}
            String key(){return Panel.this.key(provider,slot);}
            String description(){return AccountMetadataStore.name(host,provider,slot)+" · "+provider;}
        }
        private TextView text(String value,int size,int color){
            TextView t=new TextView(host);
            t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;
        }
        private Button button(String label){
            Button b=new Button(host);
            b.setAllCaps(false);b.setText(label);b.setTextSize(12);
            b.setTextColor(Color.WHITE);
            b.setBackgroundTintList(ColorStateList.valueOf(0xFF2A365C));
            b.setMinHeight(0);b.setMinimumHeight(0);
            return b;
        }
        private Spinner spinner(String[] values){
            Spinner s=new Spinner(host);
            ArrayAdapter<String> adapter=new ArrayAdapter<>(host,
                android.R.layout.simple_spinner_item,values);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            s.setAdapter(adapter);return s;
        }
        private void add(LinearLayout to,View v,int bottom){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin=dp(bottom);to.addView(v,lp);
        }
        private void action(LinearLayout bar,String label,Runnable f){
            Button b=button(label);
            bar.addView(b,new LinearLayout.LayoutParams(0,dp(43),1));
            b.setOnClickListener(v->f.run());
        }
        private void toast(String value){Toast.makeText(host,value,Toast.LENGTH_SHORT).show();}
        private void open(){
            dialog.show();
            Window w=dialog.getWindow();
            if(w!=null)w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
            render();
        }
        private void render(){
            if(host.isFinishing()||host.isDestroyed()||rows==null)return;
            rows.removeAllViews();
            visible.clear();
            long now=System.currentTimeMillis();
            String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int filter=providerFilter.getSelectedItemPosition();
            int availability=statusFilter.getSelectedItemPosition();
            long earliest=Long.MAX_VALUE;
            for(int p=0;p<AccountBrowserSlots.PROVIDERS.length;p++){
                if(filter>0&&filter!=p+1)continue;
                String provider=AccountBrowserSlots.PROVIDERS[p];
                List<Target> group=new ArrayList<>();
                for(int slot:AccountMetadataStore.sorted(host,provider,now)){
                    String name=AccountMetadataStore.name(host,provider,slot);
                    String note=AccountMetadataStore.note(host,provider,slot);
                    long until=AccountMetadataStore.quotaUntil(host,provider,slot,now);
                    if(until>0)earliest=Math.min(earliest,until);
                    if(availability==1&&until>0 || availability==2&&until==0)continue;
                    String hay=(AccountBrowserSlots.LABELS[p]+" "+provider+" "+name+" "+note+
                        " 账号"+(slot+1)).toLowerCase(Locale.ROOT);
                    if(!hay.contains(q))continue;
                    group.add(new Target(provider,slot));
                }
                if(group.isEmpty())continue;
                add(rows,text(AccountBrowserSlots.LABELS[p],16,0xFF8BE7F0),5);
                for(Target target:group){
                    visible.add(target);
                    addRow(target,now);
                }
            }
            // Selected rows that become hidden by search/filter must never
            // be accidentally erased during a bulk operation.
            Set<String> visibleKeys=new LinkedHashSet<>();
            for(Target t:visible)visibleKeys.add(t.key());
            selected.retainAll(visibleKeys);
            countLabel.setText("当前结果 "+visible.size()+" 个，已选 "+selected.size()+
                " 个 · 全选/反选/清理仅作用于当前可见账号");
            handler.removeCallbacks(timer);
            if(dialog.isShowing()){
                long delay=earliest==Long.MAX_VALUE?60_000L:
                    Math.min(60_000L,Math.max(500L,earliest-now+250L));
                handler.postDelayed(timer,delay);
            }
        }
        private void addRow(Target target,long now){
            String provider=target.provider;int slot=target.slot;
            LinearLayout block=new LinearLayout(host);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(dp(4),dp(3),dp(4),dp(3));
            block.setBackgroundColor(0xFF151F39);
            LinearLayout line=new LinearLayout(host);
            line.setGravity(Gravity.CENTER_VERTICAL);
            block.addView(line);
            String state=AccountMetadataStore.status(host,provider,slot,now);
            CheckBox check=new CheckBox(host);
            check.setText(AccountMetadataStore.name(host,provider,slot)+
                (state.isEmpty()?"":"\n"+state));
            check.setTextSize(13);
            check.setTextColor(state.isEmpty()?Color.WHITE:0xFFFFD299);
            check.setChecked(selected.contains(target.key()));
            check.setOnCheckedChangeListener((view,yes)->{
                if(yes)selected.add(target.key());else selected.remove(target.key());
                countLabel.setText("当前结果 "+visible.size()+" 个，已选 "+selected.size()+" 个");
            });
            line.addView(check,new LinearLayout.LayoutParams(0,-2,1));
            Button rename=button("编辑");
            line.addView(rename,new LinearLayout.LayoutParams(dp(56),dp(40)));
            rename.setOnClickListener(v->edit(target));
            Button mark=button("额度");
            line.addView(mark,new LinearLayout.LayoutParams(dp(56),dp(40)));
            mark.setOnClickListener(v->mark(target));
            String note=AccountMetadataStore.note(host,provider,slot);
            if(!note.isEmpty())add(block,text("备注："+note,12,0xFFBBC7E0),2);
            LinearLayout links=new LinearLayout(host);
            block.addView(links);
            action(links,"打开网页",()->{
                dialog.dismiss();IsolatedGeckoBrowser.show(host,provider,slot);
            });
            action(links,"悬浮窗",()->{
                dialog.dismiss();ClaudeFloatingOverlay.show(host,provider,slot);
            });
            add(rows,block,7);
        }
        private void edit(Target target){
            LinearLayout form=new LinearLayout(host);
            form.setPadding(dp(13),dp(8),dp(13),dp(8));
            form.setOrientation(LinearLayout.VERTICAL);
            EditText name=new EditText(host),note=new EditText(host);
            name.setSingleLine(true);name.setTextColor(Color.WHITE);
            name.setHint("自定义账号名（最多28字）");name.setHintTextColor(0xFFB0BDE0);
            name.setText(AccountMetadataStore.name(host,target.provider,target.slot));
            note.setTextColor(Color.WHITE);note.setHint("备注（最多140字）");
            note.setHintTextColor(0xFFB0BDE0);
            note.setText(AccountMetadataStore.note(host,target.provider,target.slot));
            add(form,name,3);add(form,note,3);
            new AlertDialog.Builder(host).setTitle("编辑 "+target.description())
                .setView(form).setNegativeButton("取消",null)
                .setPositiveButton("保存",(d,w)->{
                    try{AccountMetadataStore.setNameAndNote(host,target.provider,target.slot,
                        name.getText().toString(),note.getText().toString());
                        render();
                    }catch(RuntimeException ex){toast("保存失败：名称最多28字，备注最多140字");}
                }).show();
        }
        private void mark(Target target){
            String[] choices={"解除标记","1 小时","3 小时","5 小时","12 小时","24 小时","自定义小时"};
            new AlertDialog.Builder(host).setTitle("人工标记额度状态 · "+target.description())
                .setItems(choices,(d,which)->{
                    int[] hours={0,1,3,5,12,24,-1};
                    if(hours[which]==-1){
                        EditText input=new EditText(host);
                        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                        input.setHint("1–72 小时");input.setText("5");
                        new AlertDialog.Builder(host).setTitle("设置恢复时间（小时）")
                            .setView(input).setNegativeButton("取消",null)
                            .setPositiveButton("保存",(a,b)->{
                                try{saveHours(target,Integer.parseInt(input.getText().toString()));}
                                catch(RuntimeException ex){toast("请输入 1–72 小时");}
                            }).show();
                    }else saveHours(target,hours[which]);
                }).show();
        }
        private void saveHours(Target t,int hours){
            AccountMetadataStore.markQuota(host,t.provider,t.slot,hours,System.currentTimeMillis());
            render();
        }
        private ArrayList<Target> targets(){
            // Resolve from the explicit selection, not from filtered or sorted indexes.
            ArrayList<Target> out=new ArrayList<>();
            for(String p:AccountBrowserSlots.PROVIDERS)
                for(int i=0;i<AccountBrowserStore.count(host,p);i++)
                    if(selected.contains(key(p,i)))out.add(new Target(p,i));
            return out;
        }
        private void confirm(boolean erase){
            ArrayList<Target> list=targets();
            if(list.isEmpty()){toast("先勾选账号；可使用全选当前结果或反选");return;}
            String title=erase?"清空 "+list.size()+" 个账号存档？":"关闭 "+list.size()+" 个当前网页？";
            String detail=erase
                ?"将关闭这些账号的活动网页，并分别清理各自 Gecko 存储（Cookie、LocalStorage 等）。可能需要重新登录。保留账号槽位、名称和备注。不会清理其他账号或平台。"
                :"只关闭这些账号当前打开的网页会话，不清除 Cookie、账号存储或其他网页；并不会自动打开其他账号。";
            new AlertDialog.Builder(host).setTitle(title).setMessage(detail)
                .setNegativeButton("取消",null)
                .setPositiveButton(erase?"确认删除存档":"确认关闭网页",(d,w)->{
                    int done=0,failed=0;
                    for(Target t:list){
                        try{
                            if(erase)AccountStorageCleaner.clearWholeContext(host,t.provider,t.slot);
                            else AccountStorageCleaner.closeCurrentPage(t.provider,t.slot);
                            done++;
                        }catch(Exception ex){failed++;}
                    }
                    selected.clear();
                    toast("成功 "+done+" 项，失败 "+failed+" 项");
                    render();
                }).show();
        }
    }
}
