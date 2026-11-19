package com.omnigate.integrated;

/** Pure Java contract: Claude is a manually selected browser, not a native API. */
public final class ClaudeBrowserOnlyTest {
    private static int checks;
    private static void yes(boolean v,String message){
        checks++;if(!v)throw new AssertionError(message);
    }
    public static void main(String[] args){
        yes(AccountBrowserSlots.supported("claude"),"Claude selectable in account picker");
        yes(AccountBrowserSlots.browserOnly("claude"),"browser only");
        yes(!AccountBrowserSlots.nativeExecutorSupported("claude"),"no native 8787");
        yes("https://claude.ai/".equals(AccountBrowserSlots.home("claude")),"official login origin");
        yes(AccountBrowserSlots.PROVIDERS.length==AccountBrowserSlots.LABELS.length,
            "all provider labels match");
        for(String p:new String[]{"deepseek","glm","chatgpt","grok"}){
            yes(AccountBrowserSlots.nativeExecutorSupported(p),p+" native unchanged");
            yes(!AccountBrowserSlots.browserOnly(p),p+" not browser-only");
        }
        yes(!AccountBrowserSlots.supported("https://claude.ai"),"no URL provider spoof");
        yes(!AccountBrowserSlots.supported("CLAUDE"),"only exact allowlist");
        yes(AccountBrowserSlots.validContextId(AccountBrowserSlots.newContextId()),
            "valid random Gecko context");
        yes(!AccountBrowserSlots.validContextId("og-ctx-v1-"+"0".repeat(35)),
            "malformed Gecko context rejected");
        System.out.println("OGCLAUDE_BROWSER_ONLY_AND_ISOLATION_CONTRACTS=PASS "+checks);
    }
}
