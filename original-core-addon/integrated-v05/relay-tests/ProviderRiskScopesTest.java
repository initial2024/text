package com.omnigate.integrated;
import java.nio.charset.StandardCharsets;
public final class ProviderRiskScopesTest {
    private static int checks=0;
    static void ok(boolean truth){
        checks++;if(!truth)throw new AssertionError("provider scope "+checks);
    }
    static ProviderRiskScopes.Selection test(String active,String model){
        return ProviderRiskScopes.select(active,model);
    }
    public static void main(String[] args){
        String[][] cases={
            {"deepseek-chat","deepseek-reasoner","deepseek"},
            {"deepseek-web","deepseek-chat","deepseek"},
            {"chatgpt-web","chatgpt","chatgpt"},
            {"grok-web-fast","grok-web","grok"},
            {"glm-web-flash","chatglm-web","glm"}
        };
        for(String[] c:cases){
            ProviderRiskScopes.Selection x=test(c[0],c[1]);
            ok(x.allowed);ok(x.channel.equals(c[2]));
        }
        ok(!test("glm-web-flash","deepseek-chat").allowed);
        ok("requested_provider_not_active".equals(
            test("chatgpt-web","grok-web-fast").reason));
        ok("provider_selection_unverified".equals(
            test("unknown-platform","deepseek-chat").reason));
        ok("request_model_unrecognized".equals(
            test("deepseek-chat","untrusted-model").reason));
        ok(ProviderRiskScopes.validChannel("deepseek"));
        ok(ProviderRiskScopes.validChannel("glm"));
        ok(ProviderRiskScopes.validChannel("chatgpt"));
        ok(ProviderRiskScopes.validChannel("grok"));
        ok(ProviderRiskScopes.validChannel("native"));
        ok(!ProviderRiskScopes.validChannel("deepseek-account-99"));
        ok(!ProviderRiskScopes.validChannel("claude-web"));
        ok(!ProviderRiskScopes.fromModel("deepseek-chatscam").equals("deepseek"));
        ok(!ProviderRiskScopes.fromModel("myglm").equals("glm"));
        // Provider gate per account type is logically independent.
        UpstreamRiskCircuit a=new UpstreamRiskCircuit("glm",1,null);
        UpstreamRiskCircuit b=new UpstreamRiskCircuit("deepseek",1,null);
        long now=1000000L;
        a.observe(429,"40",null,null,null,null,now);
        ok(!a.enter(now+1).allowed);
        ok(b.enter(now+1).allowed);b.exit();
        UpstreamRiskCircuit migrated=new UpstreamRiskCircuit("native",1,null);
        migrated.observe(403,null,null,null,null,null,now);
        ok(!migrated.permission(now+100).allowed);
        System.out.println("OGRISK23_FOUR_PROVIDER_FIXED_SCOPES=PASS");
        System.out.println("OGRISK23_SELECTED_REQUESTED_MODEL_MATCH=PASS");
        System.out.println("OGRISK23_NO_CROSS_PROVIDER_CIRCUIT_ROTATION=PASS");
        System.out.println("OGRISK23_ASSERTIONS="+checks);
    }
}
