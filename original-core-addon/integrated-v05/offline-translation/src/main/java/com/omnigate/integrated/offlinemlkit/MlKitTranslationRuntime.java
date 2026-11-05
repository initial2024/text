package com.omnigate.integrated.offlinemlkit;

import android.content.Context;
import com.google.mlkit.nl.translate.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A separate optional model runtime, NEVER a webpage DOM translator.
 *
 * Only model files are downloaded with explicit user consent (Wi-Fi required).
 * Text inference uses ML Kit on-device; no request to any OmniGate model API,
 * web provider, external translation endpoint, or proxy. Main compatibility
 * DEX uses reflection to avoid accidentally depending on this module before
 * all SDK transitive code AND Android resources have been merged and verified.
 *
 * Do NOT assert that this code is usable merely because its AAR compiles:
 * the private final original APK must bundle all non-conflicting dependencies,
 * merge resources, sign with the locked certificate, and pass device E2E.
 */
public final class MlKitTranslationRuntime {
    private MlKitTranslationRuntime(){}
    private static final int MAX_TEXT=12_000;
    private static final int CHUNK=1_500;

    public static void translate(Context context,String text,String from,String to,
            boolean firstRunDownloadConsent,Consumer<String> onSuccess,
            Consumer<String> onFailure){
        if(context==null||text==null||text.trim().isEmpty() ||
                text.length()>MAX_TEXT){
            onFailure.accept("离线翻译正文不合法或超过 12000 字符限制");return;
        }
        String src=TranslateLanguage.fromLanguageTag(from);
        String dst=TranslateLanguage.fromLanguageTag(to);
        if(src==null||dst==null||src.equals(dst)){
            onFailure.accept("离线翻译不支持该语言对");return;
        }
        // The bridge enforces pair-consent for all non-first-run calls.
        // A first-run opt-in is a distinct explicit menu action.
        final Translator translator;
        try{
            TranslatorOptions options=new TranslatorOptions.Builder()
                .setSourceLanguage(src).setTargetLanguage(dst).build();
            translator=Translation.getClient(options);
        }catch(RuntimeException | LinkageError e){
            onFailure.accept("无法初始化 ML Kit（"+e.getClass().getSimpleName()+"）");return;
        }
        AtomicBoolean finished=new AtomicBoolean(false);
        Consumer<String> fail=reason->{
            if(finished.compareAndSet(false,true)){
                translator.close();
                onFailure.accept(reason);
            }
        };
        Consumer<String> ok=value->{
            if(finished.compareAndSet(false,true)){
                translator.close();
                onSuccess.accept(value);
            }
        };
        try{
            // Model download runs only after a distinct first-use opt-in or
            // a prior consent recorded by the host. Wi-Fi is always required.
            DownloadConditions conditions=new DownloadConditions.Builder()
                .requireWifi().build();
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused->{
                    try{
                        List<String> parts=chunks(text);
                        translateNext(translator,parts,0,new StringBuilder(),ok,fail);
                    }catch(RuntimeException | LinkageError e){
                        fail.accept("离线分段处理失败（"+e.getClass().getSimpleName()+"）");
                    }
                })
                .addOnFailureListener(e->fail.accept(
                    "语言模型不可用或 Wi-Fi 下载失败（"+
                    e.getClass().getSimpleName()+"）。请检查存储空间与网络。"));
        }catch(RuntimeException | LinkageError e){
            fail.accept("离线模型加载失败（"+e.getClass().getSimpleName()+"）");
        }
    }

    private static List<String> chunks(String text){
        List<String> out=new ArrayList<>();
        for(int from=0;from<text.length();){
            int end=Math.min(from+CHUNK,text.length());
            if(end<text.length() && Character.isHighSurrogate(text.charAt(end-1)))
                end--;
            int cut=-1;
            // Prefer sentence/newline boundaries without risking zero length.
            if(end<text.length() && end-from>CHUNK/2){
                for(int at=end-1;at>from+CHUNK/2;at--){
                    char c=text.charAt(at);
                    if(c=='\n'||c=='。'||c=='.'||c=='！'||c=='!'||c=='?'){
                        cut=at+1;break;
                    }
                }
            }
            if(cut>from)end=cut;
            if(end<=from)end=Math.min(from+CHUNK,text.length());
            out.add(text.substring(from,end));
            from=end;
        }
        return out;
    }
    private static void translateNext(Translator translator,List<String> parts,
            int index,StringBuilder answer,Consumer<String> onSuccess,
            Consumer<String> onFailure){
        if(index>=parts.size()){
            onSuccess.accept(answer.toString());return;
        }
        try{
            translator.translate(parts.get(index))
                .addOnSuccessListener(value->{
                    if(value==null||value.trim().isEmpty()){
                        onFailure.accept("第 "+(index+1)+" 段没有可用译文");
                        return;
                    }
                    if(answer.length()>0)answer.append("\n\n");
                    answer.append(value);
                    translateNext(translator,parts,index+1,answer,onSuccess,onFailure);
                })
                .addOnFailureListener(error->onFailure.accept(
                    "第 "+(index+1)+" 段离线翻译失败（"+
                    error.getClass().getSimpleName()+"）"));
        }catch(RuntimeException | LinkageError e){
            onFailure.accept("离线翻译异常（"+e.getClass().getSimpleName()+"）");
        }
    }
}
