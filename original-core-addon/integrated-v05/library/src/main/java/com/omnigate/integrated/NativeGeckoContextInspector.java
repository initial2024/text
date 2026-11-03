package com.omnigate.integrated;

import android.app.Activity;
import java.lang.reflect.Field;

/**
 * Exact v1.0.5 native DEX contract (read-only):
 * MainActivity.{browser,driver,server};
 * ProviderBrowser.{bridge,session,view,provider};
 * WebDriver.{bridge,provider};
 * GeckoBridge.session; ApiServer.{driver,provider}.
 *
 * No heuristic object graph scans, no cookie access. Context is immutable
 * after GeckoSession.open(). Unknown/different references FAIL CLOSED.
 */
final class NativeGeckoContextInspector {
    private NativeGeckoContextInspector(){}
    private static Object field(Object object,String name)throws Exception{
        if(object==null)throw new IllegalStateException("native_owner_null");
        Field f=object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object v=f.get(object);
        if(v==null)throw new IllegalStateException("native_field_null");
        return v;
    }
    static boolean exactMainMatch(Activity main,Object originalDriver,
            String desiredProvider,String desiredContext){
        if(main==null||originalDriver==null||
            !AccountBrowserSlots.supported(desiredProvider)||
            !AccountBrowserSlots.validContextId(desiredContext))return false;
        try{
            Object currentDriver=field(main,"driver");
            if(currentDriver!=originalDriver)return false;
            Object server=field(main,"server");
            if(field(server,"driver")!=originalDriver)return false;

            Object browser=field(main,"browser");
            Object webBridge=field(originalDriver,"bridge");
            if(field(browser,"bridge")!=webBridge)return false;

            Object session=field(webBridge,"session");
            if(field(browser,"session")!=session)return false;
            Object view=field(browser,"view");
            Class<?> cls=Class.forName("org.mozilla.geckoview.GeckoView");
            if(!cls.isInstance(view)||cls.getMethod("getSession").invoke(view)!=session)
                return false;

            // The actual native API server, WebDriver and browser must
            // share the SAME provider enum object, not merely Prefs text.
            Object prov=field(originalDriver,"provider");
            if(prov!=field(browser,"provider")||prov!=field(server,"provider"))
                return false;
            Object model=prov.getClass().getMethod("getModel").invoke(prov);
            if(!(model instanceof String)||
                !desiredProvider.equals(ProviderRiskScopes.fromModel((String)model)))
                return false;

            Class<?> gecko=Class.forName("org.mozilla.geckoview.GeckoSession");
            if(!gecko.isInstance(session))return false;
            Object settings=gecko.getMethod("getSettings").invoke(session);
            if(settings==null)return false;
            Object actual=settings.getClass().getMethod("getContextId").invoke(settings);
            return desiredContext.equals(actual);
        }catch(Throwable unknown){
            return false;
        }
    }
}
