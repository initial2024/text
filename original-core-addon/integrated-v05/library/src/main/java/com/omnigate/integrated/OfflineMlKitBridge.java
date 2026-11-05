package com.omnigate.integrated;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional, account-independent, on-device translation adapter.
 *
 * The ML Kit SDK lives in a SEPARATELY PACKAGED MODULE. This class must not
 * pull Google SDK classes into the frozen original core's runtime by accident.
 * The original GeckoSession is NEVER sent to the runtime, only an extracted
 * bounded String. No web model/API/Relay call, provider navigation, or cookies.
 *
 * The first model download is always an explicit user choice. Subsequent
 * requests can run automatically using the previously consented language pair.
 * Do not label a compile-only AAR as an installed working translator.
 */
final class OfflineMlKitBridge {
    private OfflineMlKitBridge(){}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String RUNTIME =
        "com.omnigate.integrated.offlinemlkit.MlKitTranslationRuntime";
    private static final String PREF="omnigate_offline_translate_optin_v1";
    private static String code(String language){
        if(language==null)return null;
        switch(language.toLowerCase(java.util.Locale.ROOT)){
            case "en":case "en-us":return "en";
            case "ja":case "ja-jp":return "ja";
            case "ko":case "ko-kr":return "ko";
            case "zh":case "zh-cn":case "zh-tw":return "zh";
            default:return null;
        }
    }
    static boolean isPackaged(){
        try{
            Class<?> type=Class.forName(RUNTIME,false,
                OfflineMlKitBridge.class.getClassLoader());
            type.getMethod("translate",Context.class,String.class,
                String.class,String.class,boolean.class,
                Consumer.class,Consumer.class);
            return true;
        }catch(ReflectiveOperationException | LinkageError error){
            return false;
        }
    }
    static boolean isAuthorized(Context ctx,String from,String to){
        String source=code(from),target=code(to);
        if(ctx==null||source==null||target==null)return false;
        return ctx.getApplicationContext()
            .getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .getBoolean("pair_"+source+"_"+target,false);
    }
    static String unavailableReason(){
        return isPackaged()
            ? "首次使用离线翻译，请点翻译菜单中的「下载离线模型并翻译」；仅经确认才下载语言模型（优先 Wi-Fi），之后自动离线翻译"
            : "当前安装包未集成 ML Kit 离线引擎；需重新打包且验证模型依赖后才可离线翻译";
    }
    static void translate(Context context,String raw,String from,String to,
            boolean userConsentedToDownload,SafePageTranslation.Callback callback){
        if(context==null||raw==null||raw.isEmpty()){
            callback.onFailure("正文为空，无法离线翻译");return;
        }
        String source=code(from),target=code(to);
        if(source==null||target==null||source.equals(target)){
            callback.onFailure("不支持的离线语言对");return;
        }
        if(!isPackaged()){
            callback.onFailure(unavailableReason());return;
        }
        if(!userConsentedToDownload && !isAuthorized(context,source,target)){
            callback.onFailure(unavailableReason());return;
        }
        final AtomicBoolean delivered=new AtomicBoolean(false);
        final Consumer<String> ok=value->{
            if(!delivered.compareAndSet(false,true))return;
            if(value==null||value.isEmpty()){
                MAIN.post(()->callback.onFailure("离线引擎没有返回译文"));return;
            }
            if(userConsentedToDownload){
                context.getApplicationContext()
                    .getSharedPreferences(PREF,Context.MODE_PRIVATE)
                    .edit().putBoolean("pair_"+source+"_"+target,true).apply();
            }
            MAIN.post(()->callback.onSuccess(value));
        };
        final Consumer<String> fail=why->{
            if(delivered.compareAndSet(false,true))
                MAIN.post(()->callback.onFailure(why));
        };
        // Downloading an SDK model on the first explicit attempt may be slow.
        // Timeout bounds UI display; ML Kit downloads are not cancellable here.
        MAIN.postDelayed(()->fail.accept("本机离线翻译超过 150 秒；请检查模型下载、网络或设备可用空间"),
            150_000);
        try{
            Class<?> type=Class.forName(RUNTIME);
            Method run=type.getMethod("translate",Context.class,String.class,
                String.class,String.class,boolean.class,
                Consumer.class,Consumer.class);
            run.invoke(null,context.getApplicationContext(),raw,source,target,
                userConsentedToDownload,ok,fail);
        }catch(Exception | LinkageError e){
            fail.accept("ML Kit 离线引擎初始化失败（"+e.getClass().getSimpleName()+
                "）；请检查 APK 是否打包完整依赖");
        }
    }
}
