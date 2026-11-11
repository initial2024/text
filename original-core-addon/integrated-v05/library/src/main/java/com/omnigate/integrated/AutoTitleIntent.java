package com.omnigate.integrated;

import java.util.Locale;

/**
 * Recognize only narrow, trusted-client background naming instructions.
 * The text must have arrived in a SYSTEM or DEVELOPER role; a human asking
 * for a title in a user message must never be classified as a background job.
 */
final class AutoTitleIntent {
    private AutoTitleIntent(){}
    static boolean systemInstruction(String source){
        if(source==null||source.length()>16000)return false;
        String s=source.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        boolean codingTitle=s.contains(
            "create a concise title for an ai coding-assistant session") &&
            s.contains("return only the title on one line");
        boolean categoryTitle=s.contains(
            "generate concise titles for conversations") &&
            s.contains("\"title\"") && s.contains("\"category\"");
        return codingTitle||categoryTitle;
    }
}
