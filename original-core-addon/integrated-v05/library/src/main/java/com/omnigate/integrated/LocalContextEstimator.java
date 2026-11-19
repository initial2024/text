package com.omnigate.integrated;

/**
 * Coarse, local text-size estimation for manually supplied conversation text.
 * NOT any provider's official tokenizer, rate limit, quota or context usage.
 * The text is never sent to an external tokenizer.
 */
final class LocalContextEstimator {
    static final int MAX_CHARS=200000;
    private LocalContextEstimator(){}
    static long estimate(CharSequence text){
        if(text==null)return 0;
        if(text.length()>MAX_CHARS)
            throw new IllegalArgumentException("transcript_input_too_large");
        long sum=0,asciiRun=0;
        for(int i=0;i<text.length();){
            int cp=Character.codePointAt(text,i);
            i+=Character.charCount(cp);
            if(cp==' '||cp=='\n'||cp=='\r'||cp=='\t'){
                if(asciiRun>0){sum+=(asciiRun+2)/3;asciiRun=0;}
            }else if(cp<128&&Character.isLetterOrDigit(cp)){
                asciiRun++;
            }else{
                if(asciiRun>0){sum+=(asciiRun+2)/3;asciiRun=0;}
                // Deliberately conservative for CJK/emoji; includes a broad
                // uncertainty margin in the caller, not false exact tokens.
                if(Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN
                    ||Character.UnicodeScript.of(cp)==Character.UnicodeScript.HIRAGANA
                    ||Character.UnicodeScript.of(cp)==Character.UnicodeScript.KATAKANA
                    ||Character.UnicodeScript.of(cp)==Character.UnicodeScript.HANGUL)
                    sum+=2;
                else sum+=1;
            }
        }
        if(asciiRun>0)sum+=(asciiRun+2)/3;
        return Math.max(1,sum);
    }
    static long upperBound(long estimate){
        return estimate<=0?0:estimate>Long.MAX_VALUE/3
            ?Long.MAX_VALUE:(estimate*3+1)/2; // 1.5x warning band
    }
    static int warningPercent(long estimate,long userDefinedContext){
        if(userDefinedContext<=0||estimate<=0)return 0;
        long upper=upperBound(estimate);
        if(upper>=userDefinedContext)return 100;
        return (int)((upper*100)/userDefinedContext);
    }
    static String warning(long estimate,long userDefinedContext){
        if(userDefinedContext<=0)return "未配置模型上下文窗口，无法预测剩余容量";
        int pct=warningPercent(estimate,userDefinedContext);
        if(pct>=95)return "极高风险：建议立即手动请求交接摘要";
        if(pct>=85)return "高风险：建议准备前文交接摘要";
        if(pct>=70)return "接近预警线：建议整理重要决策和待办";
        return "估算使用量尚未达到本地提醒阈值";
    }
}
