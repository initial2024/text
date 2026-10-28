package com.dsh.webgateway;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum AiProvider {
    DEEPSEEK(
            "deepseek",
            "DeepSeek",
            "DeepSeek 网页版 · 深度思考与联网搜索",
            "https://chat.deepseek.com/",
            "deepseek-chat",
            Arrays.asList("deepseek-chat", "deepseek-web", "deepseek")),
    CHATGPT(
            "chatgpt",
            "ChatGPT",
            "ChatGPT 网页版 · GPT 对话能力",
            "https://chatgpt.com/",
            "chatgpt-web",
            Arrays.asList("chatgpt-web", "chatgpt", "gpt-web", "gpt")),
    GROK(
            "grok",
            "Grok",
            "Grok 网页版 · Fast 模型与实时搜索",
            "https://grok.com/",
            "grok-web-fast",
            Arrays.asList("grok-web-fast", "grok-web", "grok")),
    GLM(
            "glm",
            "GLM",
            "智谱清言网页版 · GLM-Flash 与联网模式",
            "https://chatglm.cn/",
            "glm-web-flash",
            Arrays.asList("glm-web-flash", "glm-web", "glm", "chatglm", "chatglm-web"));

    public final String id;
    public final String displayName;
    public final String description;
    public final String url;
    public final String model;
    private final List<String> aliases;

    AiProvider(
            String id,
            String displayName,
            String description,
            String url,
            String model,
            List<String> aliases) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.url = url;
        this.model = model;
        this.aliases = aliases;
    }

    public static AiProvider fromId(String value) {
        if (value == null) return DEEPSEEK;
        for (AiProvider p : values()) {
            if (p.id.equalsIgnoreCase(value) || p.name().equalsIgnoreCase(value)) return p;
        }
        return DEEPSEEK;
    }

    public static AiProvider fromModel(String model, AiProvider fallback) {
        if (model == null || model.trim().isEmpty()) return fallback == null ? DEEPSEEK : fallback;
        String m = model.trim().toLowerCase(Locale.ROOT);
        for (AiProvider p : values()) {
            if (p.model.equals(m)) return p;
            for (String alias : p.aliases) {
                if (alias.equals(m)) return p;
            }
        }
        return fallback == null ? DEEPSEEK : fallback;
    }

    public static AiProvider fromUrl(String url, AiProvider fallback) {
        if (url == null) return fallback == null ? DEEPSEEK : fallback;
        String u = url.toLowerCase(Locale.ROOT);
        if (u.contains("deepseek.com")) return DEEPSEEK;
        if (u.contains("chatgpt.com") || u.contains("openai.com")) return CHATGPT;
        if (u.contains("grok.com") || u.contains("x.com/i/grok")) return GROK;
        if (u.contains("chatglm.cn") || u.contains("z.ai")) return GLM;
        return fallback == null ? DEEPSEEK : fallback;
    }

    public static String modelsJsonOwner(AiProvider p) {
        return p.id + "-web";
    }
}
