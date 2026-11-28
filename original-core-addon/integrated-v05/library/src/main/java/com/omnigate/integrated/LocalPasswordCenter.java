package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * User-authorized credentials only. No DOM access, network, shared runtime autofill,
 * global password store, clipboard, export, logs or background collection.
 * Android 11+ and secure device lock required; fail closed otherwise.
 */
final class LocalPasswordCenter {
    private static final int MAX_BYTES=1024*1024;
    private static final int MAX_ENTRIES=100;
    private LocalPasswordCenter(){}
    private static void msg(Activity a,String s){
        Toast.makeText(a,s,Toast.LENGTH_LONG).show();
    }
    private static boolean ready(Activity a){
        return a!=null&&!a.isFinishing()&&!a.isDestroyed();
    }
    private static String existing(Context c,String p,int slot){
        if(!AccountBrowserSlots.supported(p)||slot<0||slot>=AccountBrowserStore.count(c,p))
            throw new IllegalArgumentException("invalid_owner");
        String id=c.getApplicationContext().getSharedPreferences(
            "omnigate_isolated_accounts_v1",Context.MODE_PRIVATE).getString(p+"."+slot,null);
        if(!AccountBrowserSlots.validContextId(id))throw new IllegalStateException("uninitialized");
        // Read-only lookup first; existing value must pass the registry uniqueness check.
        if(!id.equals(AccountBrowserStore.contextId(c,p,slot)))throw new IllegalStateException("owner_changed");
        return id;
    }
    private static boolean same(Activity a,String p,int slot,String id){
        try{return ready(a)&&id.equals(existing(a,p,slot));}
        catch(Exception e){return false;}
    }
    private static String alias(String id){return "omnigate.password.v1."+id;}
    private static AtomicFile file(Context c,String id){
        if(!AccountBrowserSlots.validContextId(id))throw new IllegalArgumentException();
        return new AtomicFile(new File(c.getNoBackupFilesDir(),"password-v1-"+id+".bin"));
    }
    private static KeyStore keys()throws Exception{
        KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);return k;
    }
    private static SecretKey key(Context c,String id,boolean create)throws Exception{
        KeyStore k=keys();
        if(k.containsAlias(alias(id)))return (SecretKey)k.getKey(alias(id),null);
        if(!create||file(c,id).getBaseFile().exists()||new File(file(c,id).getBaseFile().getPath()+".bak").exists())throw new IllegalStateException("key_missing");
        KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(alias(id),
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(30,KeyProperties.AUTH_DEVICE_CREDENTIAL)
            .build());
        return g.generateKey();
    }
    private static byte[] aad(String id){
        return ("omnigate.password.v1:"+id).getBytes(StandardCharsets.UTF_8);
    }
    private static synchronized JSONArray load(Context c,String id)throws Exception{
        AtomicFile f=file(c,id);
        if(!f.getBaseFile().exists()&&!new File(f.getBaseFile().getPath()+".bak").exists())
            return new JSONArray();
        if(f.getBaseFile().length()>MAX_BYTES)throw new IllegalStateException("vault_size");
        byte[] bytes=f.readFully();
        if(bytes.length<29||bytes.length>MAX_BYTES||bytes[0]!=1)throw new IllegalStateException("vault_format");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(c,id,false),new GCMParameterSpec(128,bytes,1,12));
        cipher.updateAAD(aad(id));
        byte[] plain=cipher.doFinal(bytes,13,bytes.length-13);
        try{
            JSONArray rows=new JSONArray(new String(plain,StandardCharsets.UTF_8));
            if(rows.length()>MAX_ENTRIES)throw new IllegalStateException("vault_count");
            return rows;
        }finally{java.util.Arrays.fill(plain,(byte)0);}
    }
    private static synchronized void save(Context c,String id,JSONArray rows)throws Exception{
        if(rows.length()>MAX_ENTRIES)throw new IllegalArgumentException("vault_count");
        byte[] plain=rows.toString().getBytes(StandardCharsets.UTF_8);
        try{
            if(plain.length>MAX_BYTES-29)throw new IllegalArgumentException("vault_size");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key(c,id,false));cipher.updateAAD(aad(id));
            byte[] iv=cipher.getIV();
            if(iv.length!=12)throw new IllegalStateException("iv_length");
            byte[] encrypted=cipher.doFinal(plain);
            AtomicFile f=file(c,id);FileOutputStream out=f.startWrite();
            try{out.write(1);out.write(iv);out.write(encrypted);f.finishWrite(out);}
            catch(Exception failure){f.failWrite(out);throw failure;}
        }finally{java.util.Arrays.fill(plain,(byte)0);}
    }
    static synchronized void erase(Context c,String id)throws Exception{
        // Existing explicit account-erasure operation only. Do not touch other aliases.
        if(!AccountBrowserSlots.validContextId(id))throw new IllegalArgumentException();
        KeyStore k=keys();if(k.containsAlias(alias(id)))k.deleteEntry(alias(id));
        file(c,id).delete();
    }
    static String origin(String raw)throws Exception{
        URI u=new URI(raw.trim());
        if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||
           u.getRawQuery()!=null||u.getRawFragment()!=null||
           (u.getRawPath()!=null&&!u.getRawPath().isEmpty()&&!"/".equals(u.getRawPath())))
            throw new IllegalArgumentException("https_origin_required");
        if(u.getHost().endsWith("."))throw new IllegalArgumentException("origin_host");
        int port=u.getPort();
        if(port==0||port>65535)throw new IllegalArgumentException("port");
        return new URI("https",null,u.getHost().toLowerCase(java.util.Locale.ROOT),
            port==443?-1:port,null,null,null).toASCIIString();
    }
    private static void auth(Activity a,String p,int slot,String id,Runnable action){
        if(!same(a,p,slot,id))return;
        if(Build.VERSION.SDK_INT<30){msg(a,"密码中心需要 Android 11 或更高版本");return;}
        KeyguardManager lock=a.getSystemService(KeyguardManager.class);
        if(lock==null||!lock.isDeviceSecure()){msg(a,"请先设置设备锁屏密码");return;}
        try{
            // Generate the auth-bound key before authenticating. Existing encrypted
            // files never get silently re-keyed if the prior key is unavailable.
            key(a,id,true);
            if(!(a instanceof CompatSettingsActivity))throw new IllegalStateException("private_host_required");
            ((CompatSettingsActivity)a).requestVaultAuthentication(()->{
                if(same(a,p,slot,id))action.run();
            });
        }catch(Exception rejected){msg(a,"安全密钥不可用；未读取或覆盖密码库");}
    }
    // One explicitly approved save offer, in memory only. No credentials
    // in Intent, disk, logs or the shared GeckoRuntime storage delegate.
    private static final android.os.Handler SAVE_HANDLER=new android.os.Handler(android.os.Looper.getMainLooper());
    private static PendingSave pendingSave;
    private static final class PendingSave{
        final String token=UUID.randomUUID().toString();
        final String provider,id,origin,user;
        final int slot;
        final long until=android.os.SystemClock.elapsedRealtime()+60000;
        final java.util.function.BooleanSupplier owner;
        Object entry;
        PendingSave(String p,int s,String i,String o,String u,Object e,java.util.function.BooleanSupplier gate){
            provider=p;slot=s;id=i;origin=o;user=u;entry=e;owner=gate;
        }
        boolean live(){
            try{return entry!=null&&android.os.SystemClock.elapsedRealtime()<until&&owner.getAsBoolean();}
            catch(RuntimeException e){return false;}
        }
        void clear(){entry=null;}
    }
    static void offerNativeSave(Activity host,String p,int slot,String id,String page,
            Object prompt,java.util.function.BooleanSupplier gate){
        if(!ready(host)||!same(host,p,slot,id)||!gate.getAsBoolean())return;
        if(Build.VERSION.SDK_INT<30)return;
        try{
            String verified=GeckoNewPasswordFill.pageOrigin(page);
            Object options=prompt.getClass().getField("options").get(prompt);
            if(java.lang.reflect.Array.getLength(options)!=1)return;
            Object option=java.lang.reflect.Array.get(options,0);
            if(!Class.forName("org.mozilla.geckoview.Autocomplete$LoginSaveOption").isInstance(option))return;
            Object entry=option.getClass().getField("value").get(option);
            if(!Class.forName("org.mozilla.geckoview.Autocomplete$LoginEntry").isInstance(entry))return;
            Class<?> type=entry.getClass();
            String site=origin((String)type.getField("origin").get(entry));
            String action=(String)type.getField("formActionOrigin").get(entry);
            if(!verified.equals(site)||action==null||!site.equals(origin(action))||
                type.getField("httpRealm").get(entry)!=null)return;
            String user=(String)type.getField("username").get(entry);
            if(user==null||user.length()>512||user.chars().anyMatch(Character::isISOControl))return;
            // Native browser event is evidence of form submission, not proof
            // of successful server-side authentication.
            PendingSave offer=new PendingSave(p,slot,id,site,user,entry,gate);
            AlertDialog d=new AlertDialog.Builder(host).setTitle("保存刚提交的登录密码？")
                .setMessage(site+"\n"+user+"\n仅保存到 "+p+" / 账号 "+(slot+1)+
                    "。登录是否成功由你确认；不会写入共享密码库。")
                .setNegativeButton("不保存",null).setPositiveButton("验证并保存",null).create();
            d.setOnDismissListener(x->{if(pendingSave!=offer)offer.clear();});
            secure(d);
            d.getButton(AlertDialog.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(!offer.live()||!same(host,p,slot,id)){offer.clear();d.dismiss();return;}
                if(pendingSave!=null)pendingSave.clear();
                pendingSave=offer;
                Intent i=new Intent(host,CompatSettingsActivity.class);
                i.putExtra(CompatSettingsActivity.EXTRA_PASSWORD_MODE,true);
                i.putExtra("vault_save_token",offer.token);
                try{host.startActivity(i);}catch(RuntimeException failure){
                    pendingSave=null;offer.clear();msg(host,"无法打开独立密码库");
                }
                d.dismiss();
            });
            SAVE_HANDLER.postDelayed(()->{
                offer.clear();if(pendingSave==offer)pendingSave=null;
                if(d.isShowing())d.dismiss();
            },60000);
        }catch(Exception invalid){/* Unknown or cross-origin form: no save. */}
    }
    static boolean consumeNativeSave(Activity a,String token){
        if(token==null)return false;
        PendingSave offer=pendingSave;
        if(offer==null||!offer.token.equals(token)){msg(a,"保存请求已失效");return true;}
        pendingSave=null;
        if(!offer.live()||!same(a,offer.provider,offer.slot,offer.id)){
            offer.clear();msg(a,"网页或账号空间已变化，已取消保存");return true;
        }
        auth(a,offer.provider,offer.slot,offer.id,()->{
            try{
                if(!offer.live()||!same(a,offer.provider,offer.slot,offer.id))
                    throw new IllegalStateException("owner_changed");
                // Only read the password after explicit approval AND device authentication.
                String pw=(String)offer.entry.getClass().getField("password").get(offer.entry);
                if(pw==null||pw.isEmpty()||pw.length()>4096)throw new IllegalArgumentException("invalid_password");
                JSONArray rows=load(a,offer.id);
                for(int j=0;j<rows.length();j++){
                    JSONObject row=rows.getJSONObject(j);
                    if(offer.origin.equals(row.getString("origin"))&&offer.user.equals(row.getString("user"))){
                        if(pw.equals(row.getString("password")))msg(a,"该密码已保存");
                        else msg(a,"已有同名登录，请先在独立密码库删除旧记录再保存");
                        return;
                    }
                }
                boolean repeat=reused(rows,pw,null);
                if(rows.length()>=MAX_ENTRIES)throw new IllegalStateException("capacity");
                rows.put(new JSONObject().put("id",UUID.randomUUID().toString())
                    .put("origin",offer.origin).put("user",offer.user).put("password",pw));
                save(a,offer.id,rows);
                msg(a,repeat?"已保存；本空间有重复密码":"已加密保存到当前账号空间");
            }catch(Exception failure){msg(a,"未保存：请求过期、账号变化或密码库不可用");}
            finally{offer.clear();a.finish();}
        });
        return true;
    }

    // One explicit request to use a saved password in a specific focused
    // native form. No global login lookup or automatic submission.
    private static PendingFill pendingFill;
    private static final class PendingFill{
        final String token=UUID.randomUUID().toString();
        final String provider,id;
        final int slot;
        final Object session;
        final GeckoNewPasswordFill.Target target;
        final java.util.function.Supplier<String> page;
        final java.util.function.BooleanSupplier owner,foreground;
        final long until=android.os.SystemClock.elapsedRealtime()+60000;
        PendingFill(String p,int s,String i,Object browser,GeckoNewPasswordFill.Target t,
            java.util.function.Supplier<String> source,java.util.function.BooleanSupplier gate,
            java.util.function.BooleanSupplier visible){
            provider=p;slot=s;id=i;session=browser;target=t;page=source;owner=gate;foreground=visible;
        }
        boolean live(){
            try{return android.os.SystemClock.elapsedRealtime()<until&&owner.getAsBoolean();}
            catch(RuntimeException e){return false;}
        }
    }
    static void requestNativeFill(Activity host,String p,int slot,String id,Object session,
            java.util.function.Supplier<String> page,java.util.function.BooleanSupplier owner,
            java.util.function.BooleanSupplier foreground){
        if(!ready(host)||!same(host,p,slot,id)||!owner.getAsBoolean())return;
        try{
            GeckoNewPasswordFill.Target target=GeckoNewPasswordFill.focused(session,page.get(),"current-password");
            PendingFill request=new PendingFill(p,slot,id,session,target,page,owner,foreground);
            pendingFill=request;
            Intent i=new Intent(host,CompatSettingsActivity.class);
            i.putExtra(CompatSettingsActivity.EXTRA_PASSWORD_MODE,true);
            i.putExtra("vault_fill_token",request.token);
            try{host.startActivity(i);}catch(RuntimeException failure){
                if(pendingFill==request)pendingFill=null;throw failure;
            }
            SAVE_HANDLER.postDelayed(()->{if(pendingFill==request)pendingFill=null;},60000);
        }catch(Exception invalid){msg(host,"先点击网站明确标记的登录密码框；未确认来源时不填充");}
    }
    static boolean consumeNativeFill(Activity a,String token){
        if(token==null)return false;
        PendingFill request=pendingFill;
        if(request==null||!request.token.equals(token)){msg(a,"填充请求已失效");return true;}
        pendingFill=null;
        if(!request.live()||!same(a,request.provider,request.slot,request.id)){
            msg(a,"账号或网页已变化，已取消");return true;
        }
        auth(a,request.provider,request.slot,request.id,()->{
            if(!request.live()||!same(a,request.provider,request.slot,request.id))return;
            try{
                JSONArray rows=load(a,request.id);
                ArrayList<String> names=new ArrayList<>(),ids=new ArrayList<>();
                for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.getJSONObject(i);
                    if(request.target.origin.equals(row.getString("origin"))){
                        names.add(row.getString("user"));ids.add(row.getString("id"));
                    }
                }
                if(names.isEmpty()){msg(a,"此账号空间没有该网站的已保存密码");a.finish();return;}
                AlertDialog choice=new AlertDialog.Builder(a)
                    .setTitle("选择 "+request.target.origin+" 的密码")
                    .setItems(names.toArray(new String[0]),(x,j)->auth(a,request.provider,request.slot,request.id,()->{
                        if(!request.live()||!same(a,request.provider,request.slot,request.id))return;
                        try{
                            JSONArray fresh=load(a,request.id);
                            for(int k=0;k<fresh.length();k++){
                                JSONObject row=fresh.getJSONObject(k);
                                if(ids.get(j).equals(row.getString("id"))&&request.target.origin.equals(row.getString("origin"))){
                                    String pw=row.getString("password");
                                    if(pw.isEmpty()||pw.length()>4096)throw new IllegalStateException("invalid_password");
                                    a.finish();
                                    // Wait for the original browser window to return, bounded.
                                    // Never fill while another Activity is covering the target.
                                    deliverNativeFill(a.getApplicationContext(),request,new String[]{pw},0);
                                    return;
                                }
                            }
                            msg(a,"记录已不存在");
                        }catch(Exception failure){msg(a,"密码库未解锁或记录不可用");}
                    })).setNegativeButton("取消",(x,w)->a.finish()).create();
                secure(choice);
            }catch(Exception failure){msg(a,"密码库读取失败");}
        });
        return true;
    }
    private static void deliverNativeFill(Context c,PendingFill r,String[] value,int attempt){
        SAVE_HANDLER.postDelayed(()->{
            try{
                if(value[0]==null||!r.live()||!r.id.equals(existing(c,r.provider,r.slot)))
                    throw new IllegalStateException("owner_changed");
                if(!r.foreground.getAsBoolean()){
                    if(attempt<9){deliverNativeFill(c,r,value,attempt+1);return;}
                    throw new IllegalStateException("window_not_visible");
                }
                GeckoNewPasswordFill.Target now=GeckoNewPasswordFill.focused(r.session,r.page.get(),"current-password");
                if(!GeckoNewPasswordFill.sameTarget(r.target,now))
                    throw new IllegalStateException("field_changed");
                GeckoNewPasswordFill.fill(now,value[0]);
                value[0]=null;
                Toast.makeText(c,"已请求填入所选密码；请检查用户名后自行提交",Toast.LENGTH_SHORT).show();
            }catch(Exception rejected){
                value[0]=null;Toast.makeText(c,"原输入框或账号已变化，已取消填充",Toast.LENGTH_SHORT).show();
            }
        },150);
    }

    static void launch(Activity a,String provider,int slot){
        if(!ready(a))return;
        android.content.Intent i=new android.content.Intent(a,CompatSettingsActivity.class);
        i.putExtra(CompatSettingsActivity.EXTRA_PASSWORD_MODE,true);
        if(provider!=null){i.putExtra("vault_provider",provider);i.putExtra("vault_slot",slot);}
        a.startActivity(i);
    }
    static void pick(Activity a){
        if(!ready(a))return;
        ArrayList<String> labels=new ArrayList<>(),providers=new ArrayList<>();
        ArrayList<Integer> slots=new ArrayList<>();
        try{
            for(String p:AccountBrowserSlots.PROVIDERS)
                for(int i=0;i<AccountBrowserStore.count(a,p);i++){
                    // Never initialize unused slots merely by opening this menu.
                    try{existing(a,p,i);}catch(Exception invalid){continue;}
                    labels.add(p+" · "+AccountMetadataStore.name(a,p,i));providers.add(p);slots.add(i);
                }
        }catch(Exception e){msg(a,"账号登记不可用");return;}
        if(labels.isEmpty()){msg(a,"请先打开一个正式账号空间；访客不保存密码");return;}
        new AlertDialog.Builder(a).setTitle("密码管理中心 · 选择账号空间")
            .setItems(labels.toArray(new String[0]),(d,i)->show(a,providers.get(i),slots.get(i)))
            .setNegativeButton("取消",null).show();
    }
    static void show(Activity a,String p,int slot){
        try{
            String id=existing(a,p,slot);
            auth(a,p,slot,id,()->list(a,p,slot,id));
        }catch(Exception e){if(ready(a))msg(a,"请先初始化此账号空间");}
    }
    private static void secure(AlertDialog dialog){
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnShowListener(x->{
            if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        });
        dialog.show();
    }
    private static boolean reused(JSONArray rows,String password,String exceptRecord)throws Exception{
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);
            if(!row.getString("id").equals(exceptRecord)&&password.equals(row.getString("password")))return true;
        }
        return false;
    }
    private static void list(Activity a,String p,int slot,String id){
        if(!same(a,p,slot,id))return;
        try{
            JSONArray rows=load(a,id);
            String[] labels=new String[rows.length()],recordIds=new String[rows.length()];
            for(int i=0;i<labels.length;i++){
                JSONObject row=rows.getJSONObject(i);
                int score=LocalPasswordAdvice.score(row.getString("password"),row.getString("user"),
                    reused(rows,row.getString("password"),row.getString("id")));
                labels[i]=row.getString("origin")+" · "+row.getString("user")+
                    "\n本地强度 "+score+"/100 · "+LocalPasswordAdvice.label(score)+
                    (reused(rows,row.getString("password"),row.getString("id"))?" · 库内重复":"");
                recordIds[i]=row.getString("id");
            }
            AlertDialog d=new AlertDialog.Builder(a)
                .setTitle("独立密码库 · "+p+" / "+(slot+1))
                .setItems(labels,(x,i)->auth(a,p,slot,id,()->reveal(a,p,slot,id,recordIds[i])))
                .setNeutralButton("安全体检",(x,w)->auth(a,p,slot,id,()->audit(a,p,slot,id)))
                .setPositiveButton("手动添加",(x,w)->editor(a,p,slot,id))
                .setNegativeButton("关闭",null).create();
            secure(d);
        }catch(Exception e){msg(a,"密码库读取失败；未重置密钥或覆盖文件");}
    }
    private static void audit(Activity a,String p,int slot,String id){
        if(!same(a,p,slot,id))return;
        try{
            JSONArray rows=load(a,id);
            int weak=0,repeated=0,shortCount=0,personal=0,flagged=0;
            StringBuilder findings=new StringBuilder();
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);
                String pw=row.getString("password"),user=row.getString("user");
                boolean repeat=reused(rows,pw,row.getString("id"));
                int risks=LocalPasswordAdvice.risks(pw,user,repeat);
                boolean shortPassword=(risks&4)!=0;
                boolean personalMatch=(risks&8)!=0;
                boolean weakPassword=(risks&1)!=0;
                if(weakPassword)weak++;
                if(repeat)repeated++;
                if(shortPassword)shortCount++;
                if(personalMatch)personal++;
                if(weakPassword||repeat||shortPassword||personalMatch){
                    flagged++;
                    findings.append("\n").append(row.getString("origin")).append(" · ").append(user)
                        .append("\n")
                        .append(weakPassword?"弱或一般强度；":"")
                        .append(repeat?"本空间重复使用；":"")
                        .append(shortPassword?"少于15字符；":"")
                        .append(personalMatch?"包含用户名；":"").append("\n");
                }
            }
            String summary="已检查 "+rows.length()+" 条密码；需关注 "+flagged+" 条。"+
                "\n弱或一般强度："+weak+"\n重复使用："+repeated+
                "\n少于15字符："+shortCount+"\n包含用户名："+personal+
                "\n（分类可能重叠，不应相加）"+
                "\n\n建议优先替换重复和明显弱密码，并开启网站支持的多因素认证。"+
                "\n仅检查此空间；未检测泄露数据库，不代表未泄露。"+
                (rows.length()==0?"\n\n密码库暂无记录。":flagged==0?"\n\n未发现这些规则覆盖的风险。": "\n\n需关注记录："+findings);
            TextView report=new TextView(a);report.setText(summary);report.setPadding(24,16,24,16);
            report.setTextIsSelectable(false);
            android.widget.ScrollView scroll=new android.widget.ScrollView(a);scroll.addView(report);
            AlertDialog d=new AlertDialog.Builder(a).setTitle("当前空间 · 密码安全体检")
                .setView(scroll).setNegativeButton("关闭",null).create();
            d.setOnDismissListener(x->report.setText(""));
            secure(d);
            report.getViewTreeObserver().addOnWindowFocusChangeListener(focused->{
                if(!focused){report.setText("");if(d.isShowing())d.dismiss();}
            });
        }catch(Exception failure){msg(a,"体检失败；未改变密码库");}
    }
    private static void editor(Activity a,String p,int slot,String id){
        if(!same(a,p,slot,id))return;
        LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,12,24,12);
        EditText site=new EditText(a),user=new EditText(a),pass=new EditText(a);
        site.setHint("https://example.com");site.setSingleLine(true);
        user.setHint("用户名 / 邮箱");user.setSingleLine(true);
        pass.setHint("密码");pass.setSingleLine(true);
        pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        for(EditText field:new EditText[]{site,user,pass}){
            field.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            field.setSaveEnabled(false);root.addView(field);
        }
        TextView strength=new TextView(a);
        strength.setText("密码强度 0/100（本地估计，非泄露检测）");
        root.addView(strength);
        android.text.TextWatcher watcher=new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                int score=LocalPasswordAdvice.score(pass.getText().toString(),user.getText().toString(),false);
                strength.setText("密码强度 "+score+"/100 · "+LocalPasswordAdvice.label(score)+
                    "\n本地估计，保存时检查本空间重复密码");
            }
            public void afterTextChanged(android.text.Editable s){}
        };
        pass.addTextChangedListener(watcher);user.addTextChangedListener(watcher);
        android.widget.Button generate=new android.widget.Button(a);
        generate.setText("推荐随机强密码");
        generate.setFilterTouchesWhenObscured(true);
        root.addView(generate);
        generate.setOnClickListener(v->PasswordGeneratorUi.show(a,password->{
            if(same(a,p,slot,id))pass.setText(password);
        }));
        AlertDialog d=new AlertDialog.Builder(a).setTitle("手动添加此空间的密码")
            .setView(root).setNegativeButton("取消",null)
            .setPositiveButton("验证并保存",null).create();
        d.setOnDismissListener(x->{pass.setText("");user.setText("");site.setText("");});
        secure(d);
        for(EditText field:new EditText[]{site,user,pass})field.setFilterTouchesWhenObscured(true);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!same(a,p,slot,id)){d.dismiss();return;}
            final String o,u,pw;
            try{
                o=origin(site.getText().toString());u=user.getText().toString().trim();
                pw=pass.getText().toString();
                if(u.length()>512||pw.isEmpty()||pw.length()>4096)throw new IllegalArgumentException();
            }catch(Exception invalid){msg(a,"请输入有效 HTTPS 网站来源和密码");return;}
            auth(a,p,slot,id,()->{
                if(!d.isShowing()||!same(a,p,slot,id))return;
                try{
                    JSONArray rows=load(a,id);
                    for(int i=0;i<rows.length();i++){
                        JSONObject row=rows.getJSONObject(i);
                        if(o.equals(row.getString("origin"))&&u.equals(row.getString("user"))){
                            msg(a,"已有此网站和用户名；请先在密码库中删除旧记录");return;
                        }
                    }
                    if(rows.length()>=MAX_ENTRIES){msg(a,"此空间已达到100条记录");return;}
                    boolean duplicate=reused(rows,pw,null);
                    rows.put(new JSONObject().put("id",UUID.randomUUID().toString())
                        .put("origin",o).put("user",u).put("password",pw));
                    save(a,id,rows);d.dismiss();msg(a,duplicate?"已加密保存；警告：本空间内存在重复密码":"已加密保存到此账号空间");
                }catch(Exception failure){msg(a,"保存失败；未覆盖原密码库");}
            });
        });
    }
    private static void reveal(Activity a,String p,int slot,String id,String targetRecord){
        if(!same(a,p,slot,id))return;
        try{
            JSONArray all=load(a,id);JSONObject row=null;
            for(int j=0;j<all.length();j++)if(targetRecord.equals(all.getJSONObject(j).getString("id"))){row=all.getJSONObject(j);break;}
            if(row==null){msg(a,"记录已不存在");return;}
            String recordId=row.getString("id");
            TextView value=new TextView(a);value.setPadding(24,16,24,16);
            boolean duplicate=reused(all,row.getString("password"),recordId);
            int score=LocalPasswordAdvice.score(row.getString("password"),row.getString("user"),duplicate);
            value.setText(row.getString("origin")+"\n"+row.getString("user")+
                "\n本地强度 "+score+"/100 · "+LocalPasswordAdvice.label(score)+
                (duplicate?" · 本空间内重复":"")+"\n\n"+row.getString("password"));
            value.setTextIsSelectable(false);
            AlertDialog d=new AlertDialog.Builder(a).setTitle("此空间的密码 · 15秒后隐藏")
                .setView(value).setNegativeButton("关闭",null)
                .setNeutralButton("删除",(x,w)->new AlertDialog.Builder(a)
                    .setTitle("删除这一条密码？").setNegativeButton("取消",null)
                    .setPositiveButton("确认删除",(y,z)->auth(a,p,slot,id,()->{
                        try{
                            JSONArray rows=load(a,id);
                            for(int j=0;j<rows.length();j++)
                                if(recordId.equals(rows.getJSONObject(j).getString("id"))){
                                    rows.remove(j);save(a,id,rows);msg(a,"已删除此条记录");return;
                                }
                        }catch(Exception failure){msg(a,"删除失败");}
                    })).show()).create();
            d.setOnDismissListener(x->value.setText(""));
            secure(d);
            value.getViewTreeObserver().addOnWindowFocusChangeListener(focused->{
                if(!focused){value.setText("");if(d.isShowing())d.dismiss();}
            });
            value.postDelayed(()->{value.setText("");if(d.isShowing())d.dismiss();},15000);
        }catch(Exception e){msg(a,"密码读取失败");}
    }
}
