package com.omnigate.integrated;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Optional USER-REVIEWED summary, never raw chat / cookies / API credentials.
 * Each Android user/work profile has a different AndroidKeyStore namespace.
 * AES-GCM AAD cryptographically binds ciphertext to provider + Gecko context.
 * No plaintext persisted, logged or used by background jobs.
 */
final class LocalReviewedSummaryStore {
    private static final String ALIAS="omnigate.local_reviewed_summary.aes.v1";
    private static final String FILE="omnigate_manual_summary_ciphertext_v1";
    static final int MAX_CHARS=8000;
    private LocalReviewedSummaryStore(){}
    private static String context(Context c,String provider,int slot){
        if(!AccountBrowserSlots.supported(provider)||slot<0||
            slot>=AccountBrowserStore.count(c,provider))
            throw new IllegalArgumentException("invalid_summary_owner");
        return AccountBrowserStore.contextId(c,provider,slot);
    }
    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(FILE,Context.MODE_PRIVATE);
    }
    private static String storageKey(String id){return "enc."+id;}
    private static byte[] associatedData(String provider,String id){
        return ("omnigate-reviewed-summary:v1:"+provider+":"+id)
            .getBytes(StandardCharsets.UTF_8);
    }
    private static synchronized SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        java.security.Key saved=ks.getKey(ALIAS,null);
        if(saved instanceof SecretKey)return (SecretKey)saved;
        if(saved!=null)throw new IllegalStateException("unexpected_summary_keystore_key");
        KeyGenerator gen=KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256).build());
        return gen.generateKey();
    }
    static synchronized boolean hasSaved(Context c,String provider,int slot){
        return prefs(c).contains(storageKey(context(c,provider,slot)));
    }
    static synchronized void save(Context c,String provider,int slot,String reviewed)
            throws Exception{
        if(!LocalContextLedger.enabled(c,provider,slot))
            throw new IllegalStateException("context_statistics_not_enabled");
        if(reviewed==null||reviewed.trim().length()==0||
            reviewed.length()>MAX_CHARS)
            throw new IllegalArgumentException("manual_reviewed_summary_length");
        String id=context(c,provider,slot);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        cipher.updateAAD(associatedData(provider,id));
        byte[] ciphertext=cipher.doFinal(
            reviewed.getBytes(StandardCharsets.UTF_8));
        byte[] iv=cipher.getIV();
        if(iv==null||iv.length!=12)
            throw new IllegalStateException("invalid_aes_gcm_nonce");
        byte[] record=new byte[iv.length+ciphertext.length];
        System.arraycopy(iv,0,record,0,iv.length);
        System.arraycopy(ciphertext,0,record,iv.length,ciphertext.length);
        // A single atomic update; previous summary stays intact on failure.
        if(!prefs(c).edit().putString(storageKey(id),
                Base64.encodeToString(record,Base64.NO_WRAP)).commit())
            throw new IllegalStateException("encrypted_summary_save_failed");
    }
    static synchronized String read(Context c,String provider,int slot)throws Exception{
        String id=context(c,provider,slot);
        String saved=prefs(c).getString(storageKey(id),null);
        if(saved==null)return "";
        byte[] record=Base64.decode(saved,Base64.DEFAULT);
        if(record.length<29||record.length>MAX_CHARS*4+64)
            throw new IllegalStateException("invalid_ciphertext_size");
        byte[] iv=java.util.Arrays.copyOfRange(record,0,12);
        byte[] ciphertext=java.util.Arrays.copyOfRange(record,12,record.length);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        cipher.updateAAD(associatedData(provider,id));
        String plain=new String(cipher.doFinal(ciphertext),StandardCharsets.UTF_8);
        if(plain.length()>MAX_CHARS)
            throw new IllegalStateException("decrypted_summary_length");
        return plain;
    }
    static synchronized void erase(Context c,String provider,int slot){
        String id=context(c,provider,slot);
        if(!prefs(c).edit().remove(storageKey(id)).commit())
            throw new IllegalStateException("encrypted_summary_delete_failed");
    }
}
