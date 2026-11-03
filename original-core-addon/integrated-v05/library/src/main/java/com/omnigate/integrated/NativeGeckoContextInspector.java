package com.omnigate.integrated;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * READ-ONLY proof of the GeckoSession actually referenced by the verified
 * native API WebDriver; never changes a GeckoSession, cookies or owner fields.
 * A missing or ambiguous session fails closed. No generated session ID is logged.
 */
final class NativeGeckoContextInspector {
    private static final int MAX_DEPTH=3;
    private static final int MAX_OBJECTS=64;
    private NativeGeckoContextInspector(){}
    static boolean exactMatch(Object actualNativeDriver,String desiredContextId){
        if(actualNativeDriver==null||!AccountBrowserSlots.validContextId(desiredContextId))
            return false;
        try{
            Class<?> sessionType=Class.forName("org.mozilla.geckoview.GeckoSession");
            Class<?> viewType=Class.forName("org.mozilla.geckoview.GeckoView");
            IdentityHashMap<Object,Boolean> visited=new IdentityHashMap<>();
            List<Object> sessions=new ArrayList<>(2);
            walk(actualNativeDriver,0,visited,sessions,sessionType,viewType);
            if(sessions.size()!=1)return false;
            Object settings=sessionType.getMethod("getSettings").invoke(sessions.get(0));
            if(settings==null)return false;
            Object actual=settings.getClass().getMethod("getContextId").invoke(settings);
            return desiredContextId.equals(actual);
        }catch(Throwable unavailable){
            return false;
        }
    }
    private static void walk(Object node,int depth,
            IdentityHashMap<Object,Boolean> visited,List<Object> sessions,
            Class<?> sessionType,Class<?> viewType){
        if(node==null||visited.containsKey(node)||visited.size()>=MAX_OBJECTS)return;
        visited.put(node,true);
        if(sessionType.isInstance(node)){
            sessions.add(node);
            return;
        }
        if(viewType.isInstance(node)){
            try{
                Object s=viewType.getMethod("getSession").invoke(node);
                walk(s,depth+1,visited,sessions,sessionType,viewType);
            }catch(ReflectiveOperationException ignored){}
            return;
        }
        if(depth>=MAX_DEPTH)return;
        String type=node.getClass().getName();
        if(!type.startsWith("com.deepseek2.app."))return;
        Class<?> cls=node.getClass();
        while(cls!=null&&cls!=Object.class){
            for(Field f:cls.getDeclaredFields()){
                if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive())continue;
                String fieldType=f.getType().getName();
                if(!sessionType.isAssignableFrom(f.getType()) &&
                   !viewType.isAssignableFrom(f.getType()) &&
                   !fieldType.startsWith("com.deepseek2.app."))continue;
                try{
                    f.setAccessible(true);
                    walk(f.get(node),depth+1,visited,sessions,sessionType,viewType);
                }catch(IllegalAccessException|SecurityException ignored){}
            }
            cls=cls.getSuperclass();
        }
    }
}
