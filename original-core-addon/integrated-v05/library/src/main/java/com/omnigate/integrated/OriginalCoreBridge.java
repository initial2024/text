package com.omnigate.integrated;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Narrow bridge to the original, untouched native API owner.
 *
 * Does not reimplement GeckoView, bypass website login, or automatically
 * start the native server at app boot. The user explicitly clicks "Start
 * native API"; only then can a resumed original MainActivity start its
 * original ApiServer via its own startServer() method.
 */
public final class OriginalCoreBridge {
    public static final int FALLBACK_PORT = 1601;
    public static final String REQUEST_START =
        "com.omnigate.integrated.REQUEST_START_NATIVE_API";
    private static volatile String lastStartStatus = "尚未请求启动原 API";
    public static final String REQUEST_NATIVE_OVERLAY =
        "com.omnigate.integrated.REQUEST_ORIGINAL_OVERLAY";
    private static final String ORIGINAL_RETURN_FROM_OVERLAY =
        "com.deepseek2.app.action.RETURN_FROM_OVERLAY";
    private static final java.util.concurrent.atomic.AtomicBoolean handoffPending =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean handoffChecking =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    private static volatile int handoffOldPort = -1;
    private static volatile int handoffAttempts = 0;
    private static volatile String handoffState = "未请求主界面接管";
    public static String handoffStatus(){return handoffState;}

    private static volatile WeakReference<Activity> originalMain =
        new WeakReference<>(null);
    // Observation only. Do NOT use this cache to route a new chat to a
    // previous WebDriver or silently revive a socket.
    private static volatile WeakReference<Activity> lastVerifiedMain =
        new WeakReference<>(null);
    private static volatile WeakReference<Object> lastVerifiedServer =
        new WeakReference<>(null);
    private static volatile long lastVerifiedAt = 0L;
    private static volatile int lastVerifiedPort = -1;
    private static volatile String lastOwnerTransition = "no_verified_owner_yet";
    private static volatile String lastLifecycleEvent = "not_observed";
    private static volatile int mainInstanceEpoch = 0;
    private static final AtomicBoolean pendingNativeStart = new AtomicBoolean(false);

    private OriginalCoreBridge() {}

    private static Object preferences() throws ReflectiveOperationException {
        Class<?> cls = Class.forName("com.deepseek2.app.gateway.Prefs");
        Field field = cls.getField("INSTANCE");
        return field.get(null);
    }

    public static int configuredPort() {
        try {
            Context ctx = GatewayApp.applicationContext();
            if (ctx == null) return FALLBACK_PORT;
            Object value = preferences().getClass()
                .getMethod("port", Context.class).invoke(preferences(), ctx);
            int port = ((Number)value).intValue();
            if (port > 0 && port <= 65535) return port;
        } catch (Throwable ignored) {
            // Safe diagnostic fallback; original port is never modified.
        }
        return FALLBACK_PORT;
    }

    public static String originalApiKey() {
        try {
            Context ctx = GatewayApp.applicationContext();
            if (ctx == null) return "";
            Object p = preferences();
            Object value = p.getClass()
                .getMethod("key", Context.class).invoke(p, ctx);
            return value instanceof String ? (String)value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Provider selected in the original third-party application, not the UI spinner. */
    public static String activeProviderModel() {
        try {
            Context ctx = GatewayApp.applicationContext();
            if (ctx == null) return "";
            Object prefs = preferences();
            Object provider = prefs.getClass().getMethod("provider", Context.class)
                .invoke(prefs, ctx);
            if (provider == null) return "";
            Object model = provider.getClass().getMethod("getModel").invoke(provider);
            return model instanceof String ? (String)model : "";
        } catch (Throwable ex) {
            return "";
        }
    }

    public static boolean originalHostAlive() {
        Activity a = originalMain.get();
        return a != null && !a.isFinishing() && !a.isDestroyed();
    }

    public static void onOriginalMainDestroyed(Activity a) {
        if(a!=null)lastLifecycleEvent="main_destroyed_"+
            System.identityHashCode(a);
        if (originalMain.get() == a) originalMain.clear();
    }

    /** Read-only owner state epoch and prior socket evidence. All IDs are
     * process-local object identity hashes, not external identifiers.
     * A previous socket can remain connected after its MainActivity field
     * has been cleared; that alone MUST NOT establish routing authority.
     */
    public static int installedMainLaunchMode(){
        try{
            Context ctx=GatewayApp.applicationContext();
            if(ctx==null)return -1;
            android.content.pm.ActivityInfo info=ctx.getPackageManager()
                .getActivityInfo(new android.content.ComponentName(
                    ctx.getPackageName(),"com.deepseek2.app.ui.MainActivity"),0);
            return info.launchMode;
        }catch(Throwable e){return -1;}
    }

    public static synchronized JSONObject inspectOwnerContinuity(
        JSONObject observed) {
        JSONObject out=new JSONObject();
        Activity main=originalMain.get();
        boolean good="main_socket_and_driver_match".equals(
            observedRouteState(observed));
        Object previous=lastVerifiedServer.get();
        Activity oldMain=lastVerifiedMain.get();
        String previousSocket="unknown";
        if(previous!=null){
            try{
                Field sf=previous.getClass().getDeclaredField("serverSocket");
                sf.setAccessible(true);
                Object raw=sf.get(previous);
                if(raw instanceof java.net.ServerSocket){
                    java.net.ServerSocket socket=(java.net.ServerSocket)raw;
                    previousSocket=socket.isBound() && !socket.isClosed()
                        ? "live" : "not_live";
                }else previousSocket="no_socket";
            }catch(Throwable e){
                previousSocket="inspect_failed_"+e.getClass().getSimpleName();
            }
        }else if(lastVerifiedAt>0)previousSocket="collected_or_unavailable";
        if(good && main!=null){
            try{
                Field sf=main.getClass().getDeclaredField("server");
                sf.setAccessible(true);
                Object server=sf.get(main);
                if(server!=null){
                    lastVerifiedMain=new WeakReference<>(main);
                    lastVerifiedServer=new WeakReference<>(server);
                    lastVerifiedPort=configuredPort();
                    lastVerifiedAt=android.os.SystemClock.elapsedRealtime();
                    lastOwnerTransition="current_main_verified";
                }
            }catch(Throwable ignored){lastOwnerTransition="owner_capture_failed";}
        }else if(lastVerifiedAt>0){
            if(main==null){
                lastOwnerTransition="current_main_missing_after_prior_verification";
            }else if(main==oldMain){
                lastOwnerTransition="same_main_lost_verified_server_reference";
            }else{
                lastOwnerTransition="different_main_resumed_after_prior_verification";
            }
            if("live".equals(previousSocket))
                lastOwnerTransition+="_previous_socket_still_live";
        }
        try{
            int mode=installedMainLaunchMode();
            out.put("installedMainLaunchMode",mode)
                .put("mainSingleTaskDeclared",mode==2)
                .put("currentMainTaskId",main==null?-1:main.getTaskId())
                .put("currentMainInstance",
                    main==null?-1:System.identityHashCode(main))
                .put("mainInstanceEpoch",mainInstanceEpoch)
                .put("lastLifecycleEvent",lastLifecycleEvent)
                .put("currentOwnerVerified",good)
                .put("lastOwnerTransition",lastOwnerTransition)
                .put("lastVerifiedPort",lastVerifiedPort)
                .put("previousVerifiedSocketState",previousSocket)
                .put("lastVerifiedAgeMs",lastVerifiedAt==0?-1:
                    Math.max(0,android.os.SystemClock.elapsedRealtime()-
                        lastVerifiedAt));
        }catch(org.json.JSONException ignored){}
        return out;
    }

    public static String requestOriginalOverlay(Activity settings) {
        try {
            Intent intent = new Intent();
            intent.setClassName(settings.getPackageName(),
                "com.deepseek2.app.ui.MainActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.putExtra(REQUEST_NATIVE_OVERLAY, true);
            settings.startActivity(intent);
            return "已打开原核心；正在请求原版悬浮窗权限或悬浮入口";
        } catch (Throwable ex) {
            return "原版悬浮窗入口不可用：" + ex.getClass().getSimpleName();
        }
    }

    /** Read only. Caller must run on a worker thread, not the UI thread.
     * The original WebDriver.diagnose() requests the built-in Gecko bridge
     * "diagnose" operation. Do NOT log page text, cookies or chat history.
     */
    public static JSONObject diagnoseCurrentPage() throws Exception {
        Activity owner=originalMain.get();
        if(owner==null || owner.isFinishing() || owner.isDestroyed())
            throw new IllegalStateException("original_activity_missing");
        Field field=owner.getClass().getDeclaredField("driver");
        field.setAccessible(true);
        Object driver=field.get(owner);
        if(driver==null) throw new IllegalStateException("original_driver_null");
        Method method=driver.getClass().getMethod("diagnose");
        Object value=method.invoke(driver);
        if(!(value instanceof JSONObject))
            throw new IllegalStateException("gecko_bridge_no_diagnosis");
        return (JSONObject)value;
    }

    /** Read-only scoped snapshot for one user-authorized attachment request.
     * Caller MUST compare the pre-request answer, validate the explicit user
     * turn in Gecko diagnostics and observe completion before using this.
     * Never log or persist the returned private assistant text.
     */
    public static JSONObject inspectNativeAnswerForUserRequest() throws Exception {
        Activity owner=originalMain.get();
        if(owner==null || owner.isFinishing() || owner.isDestroyed())
            throw new IllegalStateException("original_activity_missing");
        Field field=owner.getClass().getDeclaredField("driver");
        field.setAccessible(true);
        Object driver=field.get(owner);
        if(driver==null)throw new IllegalStateException("original_driver_null");
        Method read=driver.getClass().getDeclaredMethod("readPageState");
        read.setAccessible(true);
        Object state=read.invoke(driver);
        if(state==null)throw new IllegalStateException("native_page_state_null");
        Class<?> cls=state.getClass();
        Object raw=cls.getMethod("getAnswer").invoke(state);
        String answer=raw instanceof String?(String)raw:"";
        return new JSONObject()
            .put("answer",answer)
            .put("generating",cls.getMethod("getGenerating").invoke(state))
            .put("generation",cls.getMethod("getGeneration").invoke(state));
    }

    /** Observe the ORIGINAL native WebDriver's cached PageState, without
     * extracting chat content. The frozen class exposes getAnswer(),
     * getGenerating(), getProviderReady(), getComposerReady() and getGeneration().
     * A working Gecko DOM is not proof this cached native state has been updated.
     */
    public static JSONObject diagnoseNativePageState() throws Exception {
        Activity owner=originalMain.get();
        if(owner==null || owner.isFinishing() || owner.isDestroyed())
            throw new IllegalStateException("original_activity_missing");
        Field field=owner.getClass().getDeclaredField("driver");
        field.setAccessible(true);
        Object driver=field.get(owner);
        if(driver==null)throw new IllegalStateException("original_driver_null");
        Method read=driver.getClass().getDeclaredMethod("readPageState");
        read.setAccessible(true);
        Object state=read.invoke(driver);
        if(state==null)throw new IllegalStateException("native_page_state_null");
        Class<?> cls=state.getClass();
        Object answer=cls.getMethod("getAnswer").invoke(state);
        Object thinking=cls.getMethod("getThinking").invoke(state);
        return new JSONObject()
            .put("nativeAnswerChars", answer instanceof String ? ((String)answer).length():-1)
            .put("nativeThinkingChars", thinking instanceof String ? ((String)thinking).length():-1)
            .put("nativeGenerating", cls.getMethod("getGenerating").invoke(state))
            .put("nativeProviderReady", cls.getMethod("getProviderReady").invoke(state))
            .put("nativeComposerReady", cls.getMethod("getComposerReady").invoke(state))
            .put("nativeGeneration", cls.getMethod("getGeneration").invoke(state));
    }

    /**
     * Observe the ACTUAL socket bound by the original MainActivity ApiServer.
     * ApiServer.isRunning and TCP reachability alone cannot establish that
     * the visible MainActivity owns the listening socket. The original
     * OverlayService contains its own independent server/driver/browser.
     *
     * Read-only: never start, stop or replace either original server.
     */
    public static JSONObject diagnoseVisibleHostSocket() {
        JSONObject result=new JSONObject();
        Activity owner=originalMain.get();
        try {
            result.put("activityAlive",owner!=null && !owner.isFinishing()
                && !owner.isDestroyed());
            result.put("configuredPort",configuredPort());
            result.put("tcpReachable",isListening());
            try {
                Class<?> serviceClass=Class.forName(
                    "com.deepseek2.app.ui.OverlayService");
                Field active=serviceClass.getDeclaredField("isRunning");
                active.setAccessible(true);
                result.put("originalOverlayServiceActive",
                    active.getBoolean(null));
            } catch(Throwable ex) {
                result.put("originalOverlayServiceActive","unknown");
            }
            if(owner==null || owner.isFinishing() || owner.isDestroyed()) {
                result.put("socketOwner","visible_host_missing"); return result;
            }
            Field serverField=owner.getClass().getDeclaredField("server");
            serverField.setAccessible(true);
            Object server=serverField.get(owner);
            if(server==null) {
                result.put("socketOwner","visible_host_server_null");return result;
            }
            result.put("runningFlag",nativeOwnerRunning());
            // Port ownership is insufficient: verify the ApiServer and the
            // displayed MainActivity reference the SAME WebDriver object.
            // This is a read-only identity check. Unknown cannot mean PASS.
            try {
                Field visibleDriverField=owner.getClass().getDeclaredField("driver");
                Field serverDriverField=server.getClass().getDeclaredField("driver");
                visibleDriverField.setAccessible(true);
                serverDriverField.setAccessible(true);
                Object visibleDriver=visibleDriverField.get(owner);
                Object serverDriver=serverDriverField.get(server);
                result.put("webDriverIdentity",
                    visibleDriver==null || serverDriver==null
                        ? "unknown_null" :
                        (visibleDriver==serverDriver ? "same" : "different"));
            } catch(Throwable reflectionFailure) {
                result.put("webDriverIdentity",
                    "unknown_"+reflectionFailure.getClass().getSimpleName());
            }
            Field socketField=server.getClass().getDeclaredField("serverSocket");
            socketField.setAccessible(true);
            Object rawSocket=socketField.get(server);
            if(!(rawSocket instanceof java.net.ServerSocket)) {
                result.put("socketOwner","visible_host_socket_null");return result;
            }
            java.net.ServerSocket socket=(java.net.ServerSocket)rawSocket;
            boolean bound=socket.isBound(), closed=socket.isClosed();
            int boundPort=bound?socket.getLocalPort():-1;
            java.net.InetAddress address=bound?socket.getInetAddress():null;
            boolean coversLoopback=address!=null
                && (address.isAnyLocalAddress() || address.isLoopbackAddress());
            String bindScope=address==null?"unbound":
                (address.isAnyLocalAddress()?"wildcard":
                (address.isLoopbackAddress()?"loopback":"other_interface"));
            result.put("socketBound",bound).put("socketClosed",closed)
                .put("actualPort",boundPort)
                .put("bindAddressScope",bindScope)
                .put("socketCoversLoopback",coversLoopback);
            result.put("visibleHostOwnsPort",bound && !closed &&
                coversLoopback && boundPort==configuredPort());
            result.put("socketOwner",bound && !closed
                ? "visible_host_has_live_socket" : "visible_host_no_live_socket");
        } catch(Throwable ex) {
            try {result.put("socketOwner","inspect_failed_"+
                ex.getClass().getSimpleName());}
            catch(org.json.JSONException ignored){}
        }
        return result;
    }

    /** Explicit, nondestructive repair of ONLY a lost MainActivity.server
     * Java reference. It NEVER binds/restarts a socket, stops an old driver,
     * or attaches a different Activity's server. Expected to fail closed
     * in the usual EADDRINUSE case, where the listener belongs elsewhere.
     */
    public static synchronized String recoverTrackedMainServerReference(){
        Activity current=originalMain.get();
        if(current==null || current.isFinishing() || current.isDestroyed())
            return "REFUSAL：当前原模型主界面不存在";
        if(current!=lastVerifiedMain.get())
            return "REFUSAL：已切换 MainActivity 实例，旧 WebDriver 不能重新挂载";
        Object old=lastVerifiedServer.get();
        if(old==null)return "REFUSAL：没有保存过该主界面的已验证 ApiServer 引用";
        if(OverlayTextClient.isSending() || OverlayUploadClient.isSending())
            return "REFUSAL：已有请求正在执行";
        try{
            Field currentField=current.getClass().getDeclaredField("server");
            Field mainDriverField=current.getClass().getDeclaredField("driver");
            Field oldDriverField=old.getClass().getDeclaredField("driver");
            Field oldSocketField=old.getClass().getDeclaredField("serverSocket");
            currentField.setAccessible(true);
            mainDriverField.setAccessible(true);
            oldDriverField.setAccessible(true);
            oldSocketField.setAccessible(true);
            if(currentField.get(current)!=null)
                return "REFUSAL：当前主界面已另有 ApiServer，禁止覆盖";
            Object currentDriver=mainDriverField.get(current);
            Object oldDriver=oldDriverField.get(old);
            if(currentDriver==null || currentDriver!=oldDriver)
                return "REFUSAL：原 WebDriver 对象不再与当前主界面一致";
            Object sock=oldSocketField.get(old);
            if(!(sock instanceof java.net.ServerSocket))
                return "REFUSAL：旧 ApiServer 未持有 ServerSocket";
            java.net.ServerSocket socket=(java.net.ServerSocket)sock;
            if(socket.isClosed() || !socket.isBound() ||
                socket.getLocalPort()!=configuredPort())
                return "REFUSAL：旧 ApiServer 的端口或存活状态不符合";
            java.net.InetAddress addr=socket.getInetAddress();
            if(addr==null || !(addr.isAnyLocalAddress() ||
                addr.isLoopbackAddress()))
                return "REFUSAL：旧服务不覆盖本地回环接口";
            Object running=old.getClass().getMethod("isRunning").invoke(old);
            if(!Boolean.TRUE.equals(running))
                return "REFUSAL：旧 ApiServer 报告已停止";
            // Cross-check immediately before restoring the field: this
            // is a reference reattachment, NOT another socket binding.
            if(!listeningAt(configuredPort()))
                return "REFUSAL：原端口不再接受连接";
            currentField.set(current,old);
            JSONObject observed=diagnoseVisibleHostSocket();
            if("main_socket_and_driver_match".equals(
                observedRouteState(observed))){
                lastOwnerTransition="same_main_live_server_reference_restored";
                return "PASS：仅恢复同一主界面的已验证原 ApiServer 引用；"+
                    "没有创建新监听或关闭网页";
            }
            if(currentField.get(current)==old)currentField.set(current,null);
            return "REFUSAL：写回后归属验证未通过；已撤销引用恢复";
        }catch(Throwable ex){
            return "REFUSAL：原服务引用恢复失败："+ex.getClass().getSimpleName();
        }
    }

    /** Route evidence is intentionally ternary: unknown is NOT verified.
     * This is a local diagnostic, not proof of which browser generated a reply.
     */
    public static String observedRouteState(JSONObject diagnosis) {
        if(!diagnosis.optBoolean("tcpReachable",false))
            return "upstream_unreachable";
        if(!diagnosis.optBoolean("activityAlive",false))
            return "unknown_no_original_main";
        if(diagnosis.optBoolean("visibleHostOwnsPort",false)) {
            if("different".equals(diagnosis.optString("webDriverIdentity","")))
                return "main_socket_driver_mismatch";
            if("same".equals(diagnosis.optString("webDriverIdentity","")))
                return "main_socket_and_driver_match";
            return "main_socket_only_driver_unknown";
        }
        if(diagnosis.optString("socketOwner","").startsWith("inspect_failed_"))
            return "unknown_reflection_failed";
        return "port_not_owned_by_main";
    }

    /** Read-only observation. Running service does NOT by itself prove
     * socket ownership, so it is a candidate only, not a verified owner.
     */
    public static boolean originalOverlayActive(){
        try {
            Class<?> cls=Class.forName("com.deepseek2.app.ui.OverlayService");
            Field field=cls.getDeclaredField("isRunning");
            field.setAccessible(true);
            return field.getBoolean(null);
        }catch(Throwable ignored){return false;}
    }

    private static boolean listeningAt(int port) {
        try(Socket s=new Socket()){
            s.connect(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"),port),500);
            return true;
        }catch(Exception ignored){return false;}
    }

    /** Explicit owner handoff only. Original overlay's RETURN action calls
     * stopRuntime() (closes overlay driver) and then returns to MainActivity.
     * Cannot prove overlay generation state from this layer; UI MUST obtain
     * informed user confirmation that overlay generation is fully idle.
     * Never silently stop a conversation, kill a process or seize a socket.
     */
    public static synchronized String requestMainOwnerHandoff(Activity caller) {
        if(handoffPending.get())return handoffState;
        if(!originalOverlayActive()){
            handoffState="拒绝接管：未观察到原版悬浮服务运行；不能假设 1601 的监听者";
            return handoffState;
        }
        if(OverlayTextClient.isSending() || OverlayUploadClient.isSending()){
            handoffState="拒绝接管：OmniGate 仍有请求进行中";
            return handoffState;
        }
        JSONObject currentOwner=diagnoseVisibleHostSocket();
        if(nativeOwnerRunning()){
            if(FALLBACK_PORT==configuredPort() &&
                "main_socket_and_driver_match".equals(
                    observedRouteState(currentOwner))){
                handoffState="无需接管：主界面已经持有 1601 且 WebDriver 匹配";
            }else{
                handoffState="拒绝接管：当前主界面 API 仍运行；"+
                    "不自动关闭可能正在接收的请求";
            }
            return handoffState;
        }
        // The overlay may own port 1601 OR the previous migrated 1602.
        // Do not assume a missing 1601 listener means it is safe to stop
        // the overlay's private runtime. User consent is still required.
        if(originalHostAlive()){
            try {
                JSONObject current=inspectNativeAnswerForUserRequest();
                JSONObject page=diagnoseCurrentPage();
                if(current.optBoolean("generating",true) ||
                    page.optBoolean("generationRaw",false) ||
                    page.optBoolean("autoRolloverActive",false)){
                    handoffState="拒绝接管：主网页仍可能正在生成";
                    return handoffState;
                }
            }catch(Throwable e){
                handoffState="拒绝接管：主界面状态未能核验("+
                    e.getClass().getSimpleName()+")";
                return handoffState;
            }
        }
        handoffOldPort=configuredPort();
        handoffAttempts=0;
        handoffState="已收到用户确认；请求原版悬浮服务返回主界面。"+
            "原悬浮 WebDriver 会被原版 stopRuntime 关闭";
        handoffPending.set(true);
        // An already-resumed MainActivity might receive onNewIntent rather
        // than another onResume. Schedule the same bounded completion on its
        // UI thread as well; duplicate calls only inspect and short-circuit
        // once handoffPending is cleared.
        Activity live=originalMain.get();
        if(live!=null && !live.isDestroyed() && !live.isFinishing()){
            live.getWindow().getDecorView().postDelayed(
                ()->completeMainOwnerHandoff(live),650);
        }
        try {
            Intent intent=new Intent();
            intent.setClassName(caller.getPackageName(),
                "com.deepseek2.app.ui.OverlayService");
            intent.setAction(ORIGINAL_RETURN_FROM_OVERLAY);
            caller.startService(intent);
            return handoffState;
        }catch(Throwable failed){
            handoffPending.set(false);
            handoffState="原版悬浮窗返回失败："+
                failed.getClass().getSimpleName()+"；未改端口";
            return handoffState;
        }
    }

    /** Only after the original OverlayService has finished stopRuntime().
     * The original MainActivity may already start its own 1602 server during
     * onCreate. A user-consented handoff can rebind this otherwise-idle API
     * to 1601 using the SAME MainActivity WebDriver. Fail closed if the
     * original overlay did not close, if 1601 remains bound, or if the
     * current model is generating.
     */
    private static void completeMainOwnerHandoff(Activity owner) {
        if(!handoffPending.get() || owner==null || owner.isFinishing() ||
            owner.isDestroyed())return;
        JSONObject mainAlready=diagnoseVisibleHostSocket();
        if(!originalOverlayActive() && configuredPort()==FALLBACK_PORT &&
            nativeOwnerRunning() &&
            "main_socket_and_driver_match".equals(
                observedRouteState(mainAlready))){
            handoffPending.set(false);
            handoffState="PASS：主界面已正常持有 1601，无须再次启动";
            return;
        }
        if(originalOverlayActive() || listeningAt(FALLBACK_PORT)){
            if(++handoffAttempts>=18){
                handoffPending.set(false);
                handoffState="接管未完成：原悬浮服务或 1601 仍被占用。"+
                    "未杀进程，也未修改原版端口";
                return;
            }
            owner.getWindow().getDecorView().postDelayed(
                ()->completeMainOwnerHandoff(owner),450);
            return;
        }
        if(OverlayTextClient.isSending() || OverlayUploadClient.isSending()){
            handoffPending.set(false);
            handoffState="接管停止：新的 OmniGate 请求开始运行";
            return;
        }
        JSONObject ownerRoute=diagnoseVisibleHostSocket();
        String currentRoute=observedRouteState(ownerRoute);
        if(handoffOldPort>0 && handoffOldPort!=FALLBACK_PORT &&
            listeningAt(handoffOldPort) &&
            !"main_socket_and_driver_match".equals(currentRoute)){
            handoffPending.set(false);
            handoffState="接管停止：旧端口 "+handoffOldPort+
                " 仍被非当前主界面持有，保留原服务，不继续改端口";
            return;
        }
        if(nativeOwnerRunning() &&
            !"main_socket_and_driver_match".equals(currentRoute)){
            handoffPending.set(false);
            handoffState="接管停止：现有 MainActivity API 运行但 WebDriver 归属不匹配";
            return;
        }
        if(!handoffChecking.compareAndSet(false,true))return;
        handoffState="正在非主线程核验模型网页是否空闲；没有停止服务";
        new Thread(()->{
            String issue=null;
            try {
                JSONObject current=inspectNativeAnswerForUserRequest();
                JSONObject page=diagnoseCurrentPage();
                if(!page.has("generationRaw")){
                    issue="未获得网页生成状态；拒绝未知状态接管";
                }else if(current.optBoolean("generating",true) ||
                    page.optBoolean("generationRaw",true) ||
                    page.optBoolean("autoRolloverActive",false)){
                    issue="网页正在生成或仍处于生成租约中";
                }
            }catch(Throwable err){
                issue="网页空闲诊断失败："+err.getClass().getSimpleName();
            }
            final String failure=issue;
            owner.runOnUiThread(()->{
                handoffChecking.set(false);
                if(!handoffPending.get() || owner.isFinishing() || owner.isDestroyed())
                    return;
                if(failure!=null){
                    handoffPending.set(false);
                    handoffState="接管停止："+failure+"；原端口与网页保持不变";
                    return;
                }
                finishMainOwnerHandoff(owner);
            });
        },"omnigate-native-handoff-idle-check").start();
    }

    /** All Service/port facts are refreshed AFTER the async WebDriver check.
     * This method is UI-thread only because original startServer touches
     * MainActivity state and view lifecycle.
     */
    private static void finishMainOwnerHandoff(Activity owner) {
        if(!handoffPending.get() || owner==null || owner.isFinishing() ||
            owner.isDestroyed())return;
        if(originalOverlayActive() || listeningAt(FALLBACK_PORT)){
            handoffPending.set(false);
            handoffState="接管停止：异步核验期间旧悬浮服务或 1601 再次被占用";
            return;
        }
        if(OverlayTextClient.isSending() || OverlayUploadClient.isSending()){
            handoffPending.set(false);
            handoffState="接管停止：异步核验期间出现新请求";
            return;
        }
        JSONObject checked=diagnoseVisibleHostSocket();
        String route=observedRouteState(checked);
        if(handoffOldPort!=FALLBACK_PORT && handoffOldPort>0 &&
            listeningAt(handoffOldPort) &&
            !"main_socket_and_driver_match".equals(route)){
            handoffPending.set(false);
            handoffState="接管停止：旧端口 "+handoffOldPort+" 归属未知";
            return;
        }
        if(nativeOwnerRunning() &&
            !"main_socket_and_driver_match".equals(route)){
            handoffPending.set(false);
            handoffState="接管停止：主界面服务器与 WebDriver 不匹配";
            return;
        }
        final int previous=configuredPort();
        try {
            Object p=preferences();
            p.getClass().getMethod("setPort",Context.class,int.class)
                .invoke(p,GatewayApp.applicationContext(),FALLBACK_PORT);
            if(configuredPort()!=FALLBACK_PORT){
                handoffPending.set(false);
                handoffState="接管失败：1601 写入回读不一致";
                return;
            }
            if(!alignOriginalInstancePort(owner)){
                handoffPending.set(false);
                handoffState="接管失败："+lastStartStatus;
                return;
            }
            handoffPending.set(false);
            // The original MainActivity.startServer() first calls stopServer()
            // and then creates ApiServer with the existing driver. This is a
            // consented rebind, NOT a seamless migration of in-flight calls.
            if(nativeOwnerRunning()){
                Method start=owner.getClass().getDeclaredMethod("startServer");
                start.setAccessible(true);
                start.invoke(owner);
            }else{
                startNativeOnResumedOwner(owner);
            }
            handoffState="原版正在由当前主界面接管 1601；"+
                "还需核对当前 /health 中的 WebDriver 归属";
            owner.getWindow().getDecorView().postDelayed(()->{
                JSONObject verified=diagnoseVisibleHostSocket();
                if("main_socket_and_driver_match".equals(
                    observedRouteState(verified)) &&
                    configuredPort()==FALLBACK_PORT &&
                    nativeOwnerRunning()){
                    handoffState="PASS：MainActivity 和 WebDriver 已共同持有 1601";
                }else{
                    handoffState="PARTIAL：已尝试恢复 1601，但归属尚未通过；"+
                        observedRouteState(verified)+
                        "；原端口="+previous+
                        "。禁止绕过 409";
                }
            },900);
        }catch(Throwable e){
            handoffPending.set(false);
            handoffState="接管失败："+e.getClass().getSimpleName()+
                "；原端口="+previous+
                "。可能需要手动恢复，不会自动重试";
        }
    }

    /** Original frozen MainActivity.server is the actual ownership evidence. */
    public static boolean nativeOwnerRunning() {
        Activity owner=originalMain.get();
        if(owner==null || owner.isFinishing() || owner.isDestroyed()) return false;
        try {
            Field field=owner.getClass().getDeclaredField("server");
            field.setAccessible(true);
            Object server=field.get(owner);
            if(server==null) return false;
            Object running=server.getClass().getMethod("isRunning").invoke(server);
            return Boolean.TRUE.equals(running);
        } catch(Throwable ex) { return false; }
    }

    public static String nativeLifecycleState() {
        Activity owner=originalMain.get();
        if(owner==null) return "原核心 MainActivity 未建立";
        if(owner.isFinishing() || owner.isDestroyed()) return "原核心 MainActivity 已关闭";
        return "原核心 MainActivity 存活；server.isRunning=" + nativeOwnerRunning()
            + "；Prefs.port=" + configuredPort();
    }

    /** The original MainActivity keeps its own PRIVATE int port initialized onCreate.
     * Updating Prefs alone is not enough on an already-open model page.
     */
    private static boolean alignOriginalInstancePort(Activity owner) {
        try {
            Field port=owner.getClass().getDeclaredField("port");
            port.setAccessible(true);
            int actual=port.getInt(owner);
            int requested=configuredPort();
            if(requested==SidecarProxyServer.LISTEN_PORT) {
                lastStartStatus="禁止把原生 API 启动到代理自己的 8787 端口";
                return false;
            }
            if(actual!=requested) port.setInt(owner, requested);
            if(port.getInt(owner)!=requested) throw new IllegalStateException("port readback");
            return true;
        } catch(Throwable ex) {
            lastStartStatus="原版页面端口同步失败：" + ex.getClass().getSimpleName();
            return false;
        }
    }

    private static void startNativeOnResumedOwner(Activity owner) {
        if(owner.isDestroyed() || owner.isFinishing()) {
            lastStartStatus="原版页面已关闭；未重复启动";
            return;
        }
        // Never rewrite an already-running original Activity's port field:
        // the live ApiServer and its WebDriver may be processing a reply.
        JSONObject observed=diagnoseVisibleHostSocket();
        if(nativeOwnerRunning()) {
            String route=observedRouteState(observed);
            lastStartStatus="MainActivity 原 API 已运行；路由="+route
                +"；为保护可能正在生成的回复，没有重启 WebDriver 或 API";
            return;
        }
        if(observed.optBoolean("tcpReachable",false)) {
            lastStartStatus="端口 "+configuredPort()
                +" 已由其他或未知实例监听（"+observedRouteState(observed)
                +"）；为避免双 WebDriver，未调用 startServer";
            return;
        }
        if(!alignOriginalInstancePort(owner)) return;
        try {
            Method start=owner.getClass().getDeclaredMethod("startServer");
            start.setAccessible(true);
            start.invoke(owner);
            lastStartStatus="已按原版 startServer() 启动；即将核对服务归属和端口";
        } catch(java.lang.reflect.InvocationTargetException ex) {
            Throwable cause=ex.getTargetException();
            lastStartStatus="原版 startServer 抛出："+
                (cause==null?"InvocationTargetException":cause.getClass().getSimpleName());
        } catch(Throwable ex) {
            lastStartStatus="原版 startServer 无法调用："+ex.getClass().getSimpleName();
        }
        owner.getWindow().getDecorView().postDelayed(()->{
            if(owner.isDestroyed() || owner.isFinishing()) return;
            boolean running=nativeOwnerRunning();
            boolean tcp=isListening();
            JSONObject observedAfter=diagnoseVisibleHostSocket();
            lastStartStatus="原版 API："+(running?"server.isRunning=true":"server 未运行")
                +"；端口 "+configuredPort()+"："+(tcp?"TCP 可连接":"TCP 不可连接")
                +"；路由="+observedRouteState(observedAfter)
                +"。未完成网页回复端到端验收。";
            GatewayApp.setLocalApiStatus(lastStartStatus);
        },700);
    }

    public static boolean isPortConflicted() {
        // The 8787 listener is owned by OmniGate proxy, never native ApiServer.
        return configuredPort() == SidecarProxyServer.LISTEN_PORT;
    }

    public static boolean repairNativePortOnExplicitClick() {
        if (!isPortConflicted()) return true;
        Context ctx = GatewayApp.applicationContext();
        if (ctx == null) {
            lastStartStatus = "缺少应用上下文，不能修复原生端口";
            return false;
        }
        try {
            Object p = preferences();
            p.getClass().getMethod("setPort", Context.class, int.class)
                .invoke(p, ctx, FALLBACK_PORT);
            if (configuredPort() != FALLBACK_PORT) {
                lastStartStatus = "写入原生端口后读回不一致；停止启动";
                return false;
            }
            lastStartStatus = "已由用户确认将原核心端口 8787 改为 1601";
            return true;
        } catch (Throwable ex) {
            lastStartStatus = "原核心端口修复失败：" +
                ex.getClass().getSimpleName() + "；请在原设置手动改为 1601";
            return false;
        }
    }

    /** User-approved non-destructive escape hatch when another unowned
     * listener occupies the original native port. Changes ONLY original
     * Prefs.port and does not stop anyone else's server or WebDriver.
     *
     * This method can inspect the native cached page and must be called from
     * a worker thread. The caller explicitly asks permission BEFORE calling.
     * The UI must call requestExplicitNativeStart on the main thread only
     * when the returned string begins with ALTERNATE_PORT_PREPARED.
     */
    public static synchronized String prepareAlternatePortOnExplicitConsent() {
        if(!originalHostAlive()) {
            lastStartStatus="备用端口拒绝：原版 MainActivity 不存在；请先打开原版网页";
            return lastStartStatus;
        }
        if(nativeOwnerRunning()) {
            lastStartStatus="备用端口拒绝：当前 MainActivity 的原 API 已运行";
            return lastStartStatus;
        }
        if(OverlayTextClient.isSending() || OverlayUploadClient.isSending()) {
            lastStartStatus="备用端口拒绝：当前仍有请求进行中";
            return lastStartStatus;
        }
        if(!isListening()) {
            lastStartStatus="备用端口无需切换：当前原生端口未被监听，应先正常恢复";
            return lastStartStatus;
        }
        JSONObject owner=diagnoseVisibleHostSocket();
        if(owner.optBoolean("visibleHostOwnsPort",false)) {
            lastStartStatus="备用端口拒绝：当前可见 MainActivity 已持有原端口";
            return lastStartStatus;
        }
        try {
            // Never move a live user conversation while reply generation
            // might still be in progress; unknown state fails closed.
            JSONObject nativeAnswer=inspectNativeAnswerForUserRequest();
            JSONObject page=diagnoseCurrentPage();
            if(nativeAnswer.optBoolean("generating",true)
                || (page.has("generationRaw")
                    && page.optBoolean("generationRaw",true))
                || page.optBoolean("autoRolloverActive",false)) {
                lastStartStatus="备用端口拒绝：原网页可能仍在生成回答";
                return lastStartStatus;
            }
        } catch(Throwable inspectError) {
            lastStartStatus="备用端口拒绝：无法确认原网页处于空闲状态（"
                +inspectError.getClass().getSimpleName()+"）";
            return lastStartStatus;
        }
        Context ctx=GatewayApp.applicationContext();
        if(ctx==null) {
            lastStartStatus="备用端口拒绝：缺少原配置上下文";
            return lastStartStatus;
        }
        final int oldPort=configuredPort();
        int freePort=-1;
        for(int port=1602;port<=1610;port++) {
            if(port==oldPort || port==SidecarProxyServer.LISTEN_PORT)continue;
            try(java.net.ServerSocket probe=new java.net.ServerSocket()) {
                probe.setReuseAddress(false);
                probe.bind(new InetSocketAddress(
                    InetAddress.getByName("127.0.0.1"),port));
                freePort=port;
                break;
            } catch(java.io.IOException occupied) {
                // Do not stop/kill the listener.
            }
        }
        if(freePort<0) {
            lastStartStatus="备用端口失败：1602–1610 均不可安全绑定";
            return lastStartStatus;
        }
        try {
            Object p=preferences();
            p.getClass().getMethod("setPort",Context.class,int.class)
                .invoke(p,ctx,freePort);
            if(configuredPort()!=freePort) {
                // Do not start either port if Prefs failed readback.
                lastStartStatus="备用端口失败：原版端口配置回读不一致；未启动";
                return lastStartStatus;
            }
            lastStartStatus="ALTERNATE_PORT_PREPARED：已由用户确认从 "
                +oldPort+" 切换至 "+freePort
                +"；原占用者未停止。下一步恢复当前 MainActivity API";
            return lastStartStatus;
        } catch(Throwable configError) {
            lastStartStatus="备用端口写入失败："
                +configError.getClass().getSimpleName()+"；未启动";
            return lastStartStatus;
        }
    }

    public static boolean isListening() {
        if (isPortConflicted()) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), configuredPort()), 550);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String lastStartStatus() {
        return lastStartStatus;
    }

    public static String requestExplicitNativeStart(Activity settings) {
        // The original Activity owns ApiServer. NEVER create a second MainActivity
        // when the previous host still exists: its onDestroy closes the server.
        if (!repairNativePortOnExplicitClick()) return lastStartStatus;
        if (originalHostAlive() && nativeOwnerRunning()) {
            JSONObject observed=diagnoseVisibleHostSocket();
            lastStartStatus="现有 MainActivity API 已运行；路由="
                +observedRouteState(observed)+"；禁止覆盖运行中的会话";
            return lastStartStatus;
        }
        if (isListening()) {
            lastStartStatus="原生端口已经有监听者；归属尚未核对，拒绝新建第二个浏览器/API";
            return lastStartStatus;
        }
        Activity live=originalMain.get();
        if(settings == live &&
            "com.deepseek2.app.ui.MainActivity".equals(settings.getClass().getName())) {
            lastStartStatus="正在现有模型页启动原版 API（不创建或关闭页面）";
            settings.getWindow().getDecorView().post(
                () -> startNativeOnResumedOwner(settings));
            return lastStartStatus;
        }
        pendingNativeStart.set(true);
        try {
            Intent request = new Intent();
            request.setClassName(settings.getPackageName(),
                "com.deepseek2.app.ui.MainActivity");
            request.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            request.putExtra(REQUEST_START,true);
            lastStartStatus="正在恢复已有模型页并核验原版服务；若不存在才创建";
            settings.startActivity(request);
        } catch(Throwable e) {
            pendingNativeStart.set(false);
            lastStartStatus="恢复原模型页失败："+e.getClass().getSimpleName();
        }
        return lastStartStatus;
    }

    /** MainActivity already calls startServer() from onCreate; do NOT restart it. */
    public static void onOriginalMainResumed(Activity activity) {
        if(originalMain.get()!=activity){
            mainInstanceEpoch++;
            lastLifecycleEvent="main_resumed_new_instance_"+
                System.identityHashCode(activity);
        }else{
            lastLifecycleEvent="main_resumed_existing_instance_"+
                System.identityHashCode(activity);
        }
        originalMain = new WeakReference<>(activity);
        if(handoffPending.get())activity.getWindow().getDecorView()
            .postDelayed(()->completeMainOwnerHandoff(activity),450);
        Intent intent = activity.getIntent();
        if (intent == null) return;
        if (intent.getBooleanExtra(REQUEST_NATIVE_OVERLAY, false)) {
            intent.removeExtra(REQUEST_NATIVE_OVERLAY);
            activity.getWindow().getDecorView().post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                try {
                    Method original = activity.getClass().getDeclaredMethod("requestOverlay");
                    original.setAccessible(true);
                    original.invoke(activity);
                    lastStartStatus = "已调用原版 requestOverlay，若需系统悬浮窗权限请在手机确认";
                } catch (Throwable ex) {
                    lastStartStatus = "原版悬浮窗无法开启：" + ex.getClass().getSimpleName();
                }
            });
        }
        boolean requested=pendingNativeStart.getAndSet(false)
            || intent.getBooleanExtra(REQUEST_START,false);
        intent.removeExtra(REQUEST_START);
        if(!requested)return;
        activity.getWindow().getDecorView().postDelayed(()->{
            if(activity.isDestroyed() || activity.isFinishing())return;
            startNativeOnResumedOwner(activity);
        },350);
    }
}
