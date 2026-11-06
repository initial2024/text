package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
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
        private final EditText search, accountTagFilter;
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
            statusFilter=spinner(new String[]{"全部额度状态","未标记","已标记"});
            filters.addView(providerFilter,new LinearLayout.LayoutParams(0,dp(47),1.1f));
            filters.addView(statusFilter,new LinearLayout.LayoutParams(0,dp(47),1f));
            accountTagFilter=new EditText(host);
            accountTagFilter.setSingleLine(true);
            accountTagFilter.setTextSize(13);
            accountTagFilter.setHint("按账号标签筛选（例如：主力；留空=全部）");
            accountTagFilter.setHintTextColor(0xFF9BAACC);
            accountTagFilter.setTextColor(Color.WHITE);
            add(shell,accountTagFilter,4);
            Button tags=button("管理额度状态预设（状态与恢复时间一体）");
            add(shell,tags,4);
            tags.setOnClickListener(v->manageTags());
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
            TextWatcher filtersWatcher=new TextWatcher(){
                @Override public void beforeTextChanged(CharSequence s,int a,int c,int d){}
                @Override public void onTextChanged(CharSequence s,int a,int b,int c){render();}
                @Override public void afterTextChanged(Editable e){}
            };
            search.addTextChangedListener(filtersWatcher);
            accountTagFilter.addTextChangedListener(filtersWatcher);
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
            // Android's stock spinner uses dark text, unreadable over the
            // OmniGate dark account manager. Style both selected and popup.
            ArrayAdapter<String> adapter=new ArrayAdapter<String>(host,
                    android.R.layout.simple_spinner_item,values){
                @Override public View getView(int position,View convert,ViewGroup parent){
                    TextView t=(TextView)super.getView(position,convert,parent);
                    t.setTextColor(0xFFF0F5FF);t.setTextSize(13);
                    t.setBackgroundColor(0xFF202D4D);
                    t.setPadding(dp(8),dp(7),dp(7),dp(7));
                    return t;
                }
                @Override public View getDropDownView(int position,View convert,ViewGroup parent){
                    TextView t=(TextView)super.getDropDownView(position,convert,parent);
                    t.setTextColor(0xFFF0F5FF);t.setTextSize(14);
                    t.setBackgroundColor(0xFF202D4D);
                    t.setPadding(dp(10),dp(10),dp(8),dp(10));
                    return t;
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            s.setAdapter(adapter);
            return s;
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
            String tagFilter=accountTagFilter.getText().toString().trim();
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
                    String accountTags=AccountMetadataStore.accountTags(host,provider,slot);
                    long until=AccountMetadataStore.quotaUntil(host,provider,slot,now);
                    if(until>0)earliest=Math.min(earliest,until);
                    if(availability==1&&until!=0 || availability==2&&until==0)continue;
                    if(!AccountMetadataStore.hasAccountTag(host,provider,slot,tagFilter))
                        continue;
                    String quotaStatus=AccountMetadataStore.quotaLabel(host,provider,slot,now);
                    String hay=(AccountBrowserSlots.LABELS[p]+" "+provider+" "+name+" "+note+
                        " "+accountTags+" "+quotaStatus+" 账号"+(slot+1))
                        .toLowerCase(Locale.ROOT);
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
            check.setText(AccountMetadataStore.name(host,provider,slot));
            check.setTextSize(13);
            check.setTextColor(Color.WHITE);
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
            String tags=AccountMetadataStore.accountTags(host,provider,slot);
            if(!tags.isEmpty())add(block,text("账号标签："+tags,12,0xFF8FD6E8),2);
            if(!state.isEmpty())add(block,text("额度状态："+state,12,0xFFFFD299),2);
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
            EditText name=new EditText(host),note=new EditText(host),tags=new EditText(host);
            name.setSingleLine(true);name.setTextColor(Color.WHITE);
            name.setHint("自定义账号名（最多28字）");name.setHintTextColor(0xFFB0BDE0);
            name.setText(AccountMetadataStore.name(host,target.provider,target.slot));
            note.setTextColor(Color.WHITE);note.setHint("备注（最多140字）");
            note.setHintTextColor(0xFFB0BDE0);
            note.setText(AccountMetadataStore.note(host,target.provider,target.slot));
            tags.setSingleLine(true);
            tags.setTextColor(Color.WHITE);
            tags.setHint("账号标签（如 主力、备用；最多4个）");
            tags.setHintTextColor(0xFFB0BDE0);
            tags.setText(AccountMetadataStore.accountTags(host,target.provider,target.slot));
            add(form,name,3);add(form,tags,3);add(form,note,3);
            new AlertDialog.Builder(host).setTitle("编辑 "+target.description())
                .setView(form).setNegativeButton("取消",null)
                .setPositiveButton("保存",(d,w)->{
                    try{AccountMetadataStore.setAccountInfo(host,target.provider,target.slot,
                        name.getText().toString(),note.getText().toString(),
                        tags.getText().toString());
                        render();
                    }catch(RuntimeException ex){toast("保存失败：账号标签最多4个，每个16字；请检查名称和备注");}
                }).show();
        }


        /**
         * A quota preset is ONE choice: status label plus recovery duration.
         * Account tags are a separate persistent field in the Edit dialog.
         */
        private void manageTags(){
            AccountMetadataStore.QuotaPreset[] presets=AccountMetadataStore.quotaPresets(host);
            String[] names=new String[presets.length];
            for(int i=0;i<presets.length;i++)names[i]=presets[i].title();
            new AlertDialog.Builder(host)
                .setTitle("额度状态预设 · 状态与恢复时间一起保存")
                .setItems(names,(d,index)->{
                    if(index<AccountMetadataStore.builtInQuotaPresetCount()){
                        toast("这是内置预设，不能删除");
                        return;
                    }
                    AccountMetadataStore.QuotaPreset preset=presets[index];
                    new AlertDialog.Builder(host).setTitle("删除自定义预设？")
                        .setMessage("移除「"+preset.title()+"」的快捷选项。"
                            +"不会改变任何账号已保存的额度状态，也不影响账号标签。")
                        .setNegativeButton("取消",null)
                        .setPositiveButton("删除",(x,y)->{
                            try{AccountMetadataStore.deleteQuotaPreset(host,preset);
                                toast("预设已删除");
                                render();
                            }catch(RuntimeException ex){toast("删除预设失败");}
                        }).show();
                })
                .setNegativeButton("关闭",null)
                .setPositiveButton("新增组合预设",(d,w)->customQuota(null,true))
                .show();
        }
        private void mark(Target target){
            AccountMetadataStore.QuotaPreset[] presets=AccountMetadataStore.quotaPresets(host);
            String[] choices=new String[presets.length+2];
            for(int i=0;i<presets.length;i++)choices[i]=presets[i].title();
            choices[presets.length]="自定义状态及恢复时间…";
            choices[presets.length+1]="解除本账号额度标记";
            new AlertDialog.Builder(host)
                .setTitle("额度状态 · "+target.description())
                // Android AlertDialog renders the message INSTEAD OF the list
                // when setMessage() and setItems() are combined. Keep a visible
                // title and an actual selectable list of duration/status pairs.
                .setItems(choices,(d,index)->{
                    if(index<presets.length){
                        AccountMetadataStore.QuotaPreset preset=presets[index];
                        try{
                            AccountMetadataStore.setQuotaMark(host,target.provider,
                                target.slot,preset.label,preset.hours,System.currentTimeMillis());
                            render();
                        }catch(RuntimeException ex){toast("设置额度状态失败");}
                    }else if(index==presets.length)customQuota(target,false);
                    else{
                        try{
                            AccountMetadataStore.setQuotaMark(host,target.provider,target.slot,
                                "",-1,System.currentTimeMillis());
                            render();
                        }catch(RuntimeException ex){toast("解除标记失败");}
                    }
                }).show();
        }
        private void customQuota(Target target,boolean saveOnly){
            LinearLayout form=new LinearLayout(host);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(dp(15),dp(8),dp(15),dp(8));
            add(form,text("状态名称（不是账号标签）",13,0xFFBFCDF0),2);
            EditText label=new EditText(host);
            label.setSingleLine(true);
            label.setTextColor(Color.WHITE);
            label.setHintTextColor(0xFF9CAFCB);
            label.setHint("如：额度已无 / 等待恢复");
            String current=target==null?"":AccountMetadataStore.quotaLabel(host,
                target.provider,target.slot,System.currentTimeMillis());
            label.setText(current.isEmpty()?"额度已无":current);
            add(form,label,5);
            add(form,text("恢复时间（与状态一起保存）",13,0xFFBFCDF0),2);
            Spinner duration=spinner(new String[]{
                "手动解除（不限时）","1 小时","3 小时","5 小时",
                "12 小时","24 小时","自定义时长…"
            });
            duration.setSelection(3);
            add(form,duration,4);
            EditText customHours=new EditText(host);
            customHours.setSingleLine(true);
            customHours.setInputType(InputType.TYPE_CLASS_NUMBER);
            customHours.setTextColor(Color.WHITE);
            customHours.setHintTextColor(0xFF9CAFCB);
            customHours.setHint("恢复时间：1–72 小时");
            customHours.setText("5");
            customHours.setVisibility(View.GONE);
            add(form,customHours,5);
            duration.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(
                        android.widget.AdapterView<?> parent,View view,int i,long id){
                    customHours.setVisibility(i==6?View.VISIBLE:View.GONE);
                }
                @Override public void onNothingSelected(
                        android.widget.AdapterView<?> parent){}
            });
            CheckBox remember=new CheckBox(host);
            remember.setText("保存为常用组合预设（以后一次选择）");
            remember.setTextColor(0xFFBECBE6);
            remember.setChecked(saveOnly);
            remember.setVisibility(saveOnly?View.GONE:View.VISIBLE);
            add(form,remember,4);
            AlertDialog editor=new AlertDialog.Builder(host)
                .setTitle(saveOnly?"创建额度状态组合预设":"自定义额度状态")
                .setView(form)
                .setNegativeButton("取消",null)
                .setPositiveButton("保存",null)
                .create();
            editor.setOnShowListener(x->
                editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                    try{
                        int[] values={0,1,3,5,12,24,-2};
                        int index=duration.getSelectedItemPosition();
                        if(index<0||index>=values.length)
                            throw new IllegalArgumentException("duration_missing");
                        int hours=values[index];
                        if(hours==-2)hours=Integer.parseInt(
                            customHours.getText().toString().trim());
                        if(hours<0||hours>72)
                            throw new IllegalArgumentException("invalid_hours");
                        String name=label.getText().toString().trim();
                        if(saveOnly){
                            AccountMetadataStore.saveQuotaPreset(host,name,hours);
                        }else{
                            AccountMetadataStore.setQuotaMark(host,target.provider,
                                target.slot,name,hours,System.currentTimeMillis());
                            if(remember.isChecked()){
                                try{AccountMetadataStore.saveQuotaPreset(host,name,hours);}
                                catch(RuntimeException ignored){
                                    toast("额度状态已保存，但快捷预设未保存");
                                }
                            }
                        }
                        editor.dismiss();
                        toast(saveOnly?"组合预设已保存":"本账号额度状态已设置");
                        render();
                    }catch(NumberFormatException error){
                        customHours.setError("请输入 1–72 小时的整数");
                    }catch(RuntimeException error){
                        label.setError("请填写最多24字的状态名称，并核对恢复时间");
                    }
                }));
            editor.show();
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
                ?"逐个关闭网页并请求清理对应 Gecko 账号空间，随后轮换空间标识。此操作会使登录失效，但无法证明底层磁盘已经完成物理擦除；如果会话关闭失败，将拒绝清理并提示失败。不会清理其他账号或平台。"
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
                    toast(erase
                        ?"已接受空间清理请求 "+done+" 项，失败 "+failed+" 项；非物理擦除证明"
                        :"已处理网页关闭请求 "+done+" 项，失败 "+failed+" 项");
                    render();
                }).show();
        }
    }
}
