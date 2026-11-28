package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
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
 * User-entered credentials only. No DOM access, network, runtime autofill,
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
        if(!create||file(c,id).getBaseFile().exists())throw new IllegalStateException("key_missing");
        KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(alias(id),
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
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
    private static String origin(String raw)throws Exception{
        URI u=new URI(raw.trim());
        if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||
           u.getRawQuery()!=null||u.getRawFragment()!=null||
           (u.getRawPath()!=null&&!u.getRawPath().isEmpty()&&!"/".equals(u.getRawPath())))
            throw new IllegalArgumentException("https_origin_required");
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
    private static void list(Activity a,String p,int slot,String id){
        if(!same(a,p,slot,id))return;
        try{
            JSONArray rows=load(a,id);
            String[] labels=new String[rows.length()],recordIds=new String[rows.length()];
            for(int i=0;i<labels.length;i++){
                JSONObject row=rows.getJSONObject(i);
                labels[i]=row.getString("origin")+" · "+row.getString("user");
                recordIds[i]=row.getString("id");
            }
            AlertDialog d=new AlertDialog.Builder(a)
                .setTitle("独立密码库 · "+p+" / "+(slot+1))
                .setItems(labels,(x,i)->auth(a,p,slot,id,()->reveal(a,p,slot,id,recordIds[i])))
                .setPositiveButton("手动添加",(x,w)->editor(a,p,slot,id))
                .setNegativeButton("关闭",null).create();
            secure(d);
        }catch(Exception e){msg(a,"密码库读取失败；未重置密钥或覆盖文件");}
    }
    private static void editor(Activity a,String p,int slot,String id){
        if(!same(a,p,slot,id))return;
        LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,12,24,12);
        EditText site=new EditText(a),user=new EditText(a),pass=new EditText(a);
        site.setHint("https://example.com");site.setSingleLine(true);
        user.setHint("用户名 / 邮箱");user.setSingleLine(true);
        pass.setHint("密码");pass.setSingleLine(true);
        pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        for(EditText field:new EditText[]{site,user,pass}){
            field.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            field.setSaveEnabled(false);root.addView(field);
        }
        AlertDialog d=new AlertDialog.Builder(a).setTitle("手动添加此空间的密码")
            .setView(root).setNegativeButton("取消",null)
            .setPositiveButton("验证并保存",null).create();
        d.setOnDismissListener(x->pass.setText(""));
        secure(d);
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
                    rows.put(new JSONObject().put("id",UUID.randomUUID().toString())
                        .put("origin",o).put("user",u).put("password",pw));
                    save(a,id,rows);d.dismiss();msg(a,"已加密保存到此账号空间");
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
            value.setText(row.getString("origin")+"\n"+row.getString("user")+"\n\n"+row.getString("password"));
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
