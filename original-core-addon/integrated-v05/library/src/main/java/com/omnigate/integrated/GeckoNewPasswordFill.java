package com.omnigate.integrated;

import android.app.Activity;
import android.util.SparseArray;
import android.widget.Toast;
import java.net.URI;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Only writes a newly generated password to a user-selected native new-password node.
 * Never reads node values, saved credentials, DOM, or the shared runtime password store.
 */
final class GeckoNewPasswordFill {
    private GeckoNewPasswordFill(){}
    static String pageOrigin(String raw)throws Exception{
        URI u=new URI(raw);
        if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||
                u.getRawUserInfo()!=null||u.getHost().endsWith("."))
            throw new IllegalArgumentException("https_required");
        return new URI("https",null,u.getHost().toLowerCase(java.util.Locale.ROOT),
            u.getPort()==443?-1:u.getPort(),null,null,null).toASCIIString();
    }
    private static final class Target {
        Object structure,node;
        int id;
        String origin;
    }
    private static Target focused(Object session,String page)throws Exception{
        if(session==null||!Boolean.TRUE.equals(session.getClass().getMethod("isOpen").invoke(session)))
            throw new IllegalStateException("closed");
        String origin=pageOrigin(page);
        Object tree=session.getClass().getMethod("getAutofillSession").invoke(session);
        Object node=tree.getClass().getMethod("getFocused").invoke(tree);
        if(node==null)throw new IllegalStateException("no_focus");
        Class<?> n=node.getClass();
        if(!"input".equalsIgnoreCase((String)n.getMethod("getTag").invoke(node))||
            !Boolean.TRUE.equals(n.getMethod("getEnabled").invoke(node))||
            !Boolean.TRUE.equals(n.getMethod("getFocusable").invoke(node))||
            !Boolean.TRUE.equals(tree.getClass().getMethod("isVisible",n).invoke(tree,node)))
            throw new IllegalStateException("not_editable");
        String domain=(String)n.getMethod("getDomain").invoke(node);
        // Demand an actual HTTPS origin supplied by Gecko, not a guessed hostname.
        if(!origin.equals(LocalPasswordCenter.origin(domain)))
            throw new IllegalStateException("origin_mismatch");
        int hint=((Number)n.getMethod("getHint").invoke(node)).intValue();
        int passwordHint=Class.forName("org.mozilla.geckoview.Autofill$Hint").getField("PASSWORD").getInt(null);
        String ac=(String)n.getMethod("getAttribute",String.class).invoke(node,"autocomplete");
        if(hint!=passwordHint||ac==null||
            !ac.toLowerCase(java.util.Locale.ROOT).trim().matches("(?:[a-z0-9_-]+\\s+)*new-password"))
            throw new IllegalStateException("not_explicit_new_password");
        Object data=tree.getClass().getMethod("dataFor",n).invoke(tree,node);
        Target t=new Target();t.structure=tree;t.node=node;t.origin=origin;
        t.id=((Number)data.getClass().getMethod("getId").invoke(data)).intValue();
        return t;
    }
    static void show(Activity host,Object session,Supplier<String> committedPage,BooleanSupplier owner){
        if(host==null||host.isFinishing()||host.isDestroyed()||!owner.getAsBoolean())return;
        final Target original;
        try{original=focused(session,committedPage.get());}
        catch(Exception unsupported){
            Toast.makeText(host,"请先点击网站明确标记的新密码框；无法确认时只提供手动生成",Toast.LENGTH_LONG).show();
            PasswordGeneratorUi.show(host,null);return;
        }
        PasswordGeneratorUi.show(host,original.origin,password->{
            try{
                if(host.isFinishing()||host.isDestroyed()||!owner.getAsBoolean())
                    throw new IllegalStateException("owner_changed");
                Target now=focused(session,committedPage.get());
                if(now.structure!=original.structure||now.node!=original.node||
                    now.id!=original.id||!now.origin.equals(original.origin))
                    throw new IllegalStateException("field_changed");
                SparseArray<CharSequence> values=new SparseArray<>();values.put(now.id,password);
                now.structure.getClass().getMethod("autofill",SparseArray.class)
                    .invoke(now.structure,values);
                Toast.makeText(host,"已请求原生填入新密码；请检查表单后自行提交",Toast.LENGTH_LONG).show();
            }catch(Exception rejected){
                Toast.makeText(host,"页面或输入框已变化，已取消填入；请重新选择密码框",Toast.LENGTH_LONG).show();
            }
        });
    }
}
