package com.omnigate.integrated;

public final class AutoTitleIntentTest {
    private static void check(boolean value,String name){
        if(!value)throw new AssertionError(name);
    }
    public static void main(String[]args){
        String coding="Create a concise title for an AI coding-assistant "+
            "session from the supplied human messages. Return only the title "+
            "on one line, in plain text of natural language.";
        check(AutoTitleIntent.systemInstruction(coding),"actual coding title");
        check(AutoTitleIntent.systemInstruction(
            "Generate concise titles for conversations. Return JSON with "+
            "\"title\" and \"category\"."),"known category title");
        check(!AutoTitleIntent.systemInstruction(
            "Create a concise title for a work project."),"ordinary user ask");
        check(!AutoTitleIntent.systemInstruction(
            "Return only the title on one line."),"broad false positive");
        check(!AutoTitleIntent.systemInstruction(
            "Create a concise title for an AI coding-assistant session "+
            "as a historical quote."),"quoted fragment");
        check(!AutoTitleIntent.systemInstruction(null),"null");
        check(!AutoTitleIntent.systemInstruction(""),"empty");
        check(!AutoTitleIntent.systemInstruction(
            "System: how do I send a message on DeepSeek?"),"user chat");
        System.out.println("OGMAIN13_STRICT_BACKGROUND_TITLE_SIGNATURES=PASS");
    }
}
