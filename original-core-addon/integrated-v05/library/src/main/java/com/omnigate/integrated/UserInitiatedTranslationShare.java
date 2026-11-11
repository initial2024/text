package com.omnigate.integrated;

import android.content.Context;
import android.content.Intent;

/**
 * External text sharing is deliberately separated from the default
 * no-network/no-navigation translation reader. Only an explicit menu action
 * invokes this chooser. No application is preselected or silently launched.
 */
final class UserInitiatedTranslationShare {
    private UserInitiatedTranslationShare(){}
    static void showChooser(Context context,String text,
            SafePageTranslation.Callback status){
        try{
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,text);
            Intent picker=Intent.createChooser(send,
                "选择翻译应用（仅在你选择后分享网页文字）");
            picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(picker);
            status.onSuccess("已打开系统选择器：由你决定是否分享网页文字。原网页未刷新。");
        }catch(RuntimeException error){
            status.onFailure("未能打开系统分享选择器："+error.getClass().getSimpleName());
        }
    }
}
