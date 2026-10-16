package com.muyue.joyoseleash;

import android.app.Application;
import android.content.*;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Runs through the existing Zygisk -> LSPosed injection chain, outside games. */
public final class HookEntry implements IXposedHookLoadPackage {
    private Context context;
    private ClassLoader loader;
    private String process;
    private boolean joyose;
    private volatile JSONObject state=new JSONObject();
    private final ThreadLocal<Boolean> localApply=ThreadLocal.withInitial(()->false);
    private final ThreadLocal<Boolean> manualFetch=ThreadLocal.withInitial(()->false);
    private final ThreadLocal<String> networkError=new ThreadLocal<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile Object parser;
    private Method parseMethod;
    private long applied=-1,fetched=-1;
    private long blockedCount;
    private String lastBlocked="",lastError="";
    private boolean adapterReady;
    private Object[] lastSystemRule;
    private final Uri stateUri=Uri.parse("content://"+Protocol.AUTH+"/state");

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp)throws Throwable{
        joyose=lp.packageName.equals("com.xiaomi.joyose");
        if(!joyose&&!lp.packageName.equals("com.android.htmlviewer"))return;
        android.util.Log.i("JoyoseLeash","load "+lp.packageName+" / "+lp.processName);
        // This ROM declares CloudSettingsProvider and its updater in :remote.
        // Select one process per role so a fetch nonce cannot be executed twice.
        if(!lp.processName.equals(joyose?lp.packageName:lp.packageName+":remote"))return;
        loader=lp.classLoader;process=lp.processName;
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                android.util.Log.i("JoyoseLeash","attach "+process);
                context=((Context)p.args[0]).getApplicationContext();if(context==null)context=(Context)p.args[0];
                try{
                    refreshState();installNetworkHooks();if(joyose)installJoyoseHooks();else adapterReady=true;
                    context.getContentResolver().registerContentObserver(stateUri,false,new ContentObserver(new Handler(Looper.getMainLooper())){
                        @Override public void onChange(boolean self){worker.execute(()->onState());}
                    });
                    worker.execute(()->onState());
                    new Handler(Looper.getMainLooper()).postDelayed(()->worker.execute(()->onState()),3000);
                    reportHook();
                }catch(Throwable e){lastError=Protocol.message(e);reportHook();}
            }
        });
    }
    private void refreshState(){
        try{state=new JSONObject(Protocol.read(context.getContentResolver().openInputStream(stateUri)));if(lastError.startsWith("状态读取失败："))lastError="";}
        catch(Throwable e){lastError="状态读取失败："+Protocol.message(e);}
    }
    private void onState(){
        refreshState();
        if(joyose&&!state.optBoolean("enabled")&&applied>=0)restoreSystemRule();
        if(joyose&&state.optBoolean("enabled")&&state.optLong("applyNonce")>0&&applied!=state.optLong("applyNonce"))apply();
        if(state.optLong("fetchUntil")>System.currentTimeMillis()&&state.optLong("fetchNonce")>0&&fetched!=state.optLong("fetchNonce"))fetch();
        reportHook();
    }
    private void installJoyoseHooks()throws Throwable{
        Class<?> c=XposedHelpers.findClass("f0.c0",loader),g=XposedHelpers.findClass("f0.g0",loader);
        parseMethod=c.getDeclaredMethod("k0",String.class,String.class,long.class,boolean.class,int.class);parseMethod.setAccessible(true);
        XposedBridge.hookAllConstructors(c,new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){parser=p.thisObject;}});
        XposedBridge.hookMethod(parseMethod,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p)throws Throwable{
                parser=p.thisObject;
                if(!Boolean.TRUE.equals(localApply.get())&&"booster_config".equals(p.args[1]))lastSystemRule=p.args.clone();
                JSONObject profile=state.optJSONObject("profiles")==null?null:state.optJSONObject("profiles").optJSONObject(String.valueOf(p.args[1]));
                if(state.optBoolean("enabled")&&state.optBoolean("locked")&&profile!=null){
                    Protocol.validateProfile(String.valueOf(p.args[1]),profile);
                    p.setObjectExtra("previousLocal",localApply.get());localApply.set(true);
                    p.args[0]=params(profile).toString();p.args[2]=profile.getLong("version");
                    Object config=XposedHelpers.callStaticMethod(g,"G2",context);
                    p.setObjectExtra("config",config);
                    // ART may inline l1() into the original parser. Update its in-memory
                    // version gate for this parse; no persistent database is touched.
                    p.setObjectExtra("previousVersion",XposedHelpers.callMethod(config,"l1"));
                    XposedHelpers.callMethod(config,"B5",-1L);
                }
            }
            @Override protected void afterHookedMethod(MethodHookParam p){
                Object prior=p.getObjectExtra("previousLocal");if(prior!=null)localApply.set((Boolean)prior);
                if(p.getObjectExtra("config")!=null&&!Boolean.TRUE.equals(p.getResult()))XposedHelpers.callMethod(p.getObjectExtra("config"),"B5",p.getObjectExtra("previousVersion"));
                if(Boolean.TRUE.equals(p.getResult())&&state.optBoolean("enabled")&&state.optBoolean("locked")){applied=state.optLong("applyNonce");lastError="";reportHook();}
                if(p.hasThrowable()){lastError=Protocol.message(p.getThrowable());reportHook();}
            }
        });
        // All registered cloud libraries also pass through this notification point.
        Class<?> manager=XposedHelpers.findClass("f.f",loader);
        XposedHelpers.findAndHookMethod(manager,"q",String.class,long.class,String.class,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p)throws Throwable{
                JSONObject rules=state.optJSONObject("profiles"),v=rules==null?null:rules.optJSONObject((String)p.args[0]);
                // booster_config is replaced at k0, preserving its original candidate
                // so disabling the override can restore the real system rule.
                if(!"booster_config".equals(p.args[0])&&state.optBoolean("enabled")&&state.optBoolean("locked")&&v!=null){p.args[1]=v.getLong("version");p.args[2]=params(v).toString();}
            }
        });
        adapterReady=true;
    }
    private JSONObject params(JSONObject profile)throws Exception{
        JSONObject obj=new JSONObject(profile.getJSONObject("params").toString());JSONObject header=obj.optJSONObject("header");
        if(header!=null)header.put("version",String.valueOf(profile.getLong("version")));return obj;
    }
    private void apply(){
        try{
            if(!adapterReady)throw new Exception("当前Joyose版本的解析入口未匹配");
            if(parser==null)parser=XposedHelpers.callStaticMethod(XposedHelpers.findClass("f0.c0",loader),"P",context);
            JSONObject rules=state.getJSONObject("profiles"),p=rules.optJSONObject("booster_config");
            if(p==null)throw new Exception("缺少booster_config");Protocol.validateProfile("booster_config",p);
            Object result=invokeLocal(new Object[]{params(p).toString(),"booster_config",p.getLong("version"),false,-1});
            if(!Boolean.TRUE.equals(result))throw new Exception("Joyose返回规则加载失败；保留备份以便回滚");
            applied=state.getLong("applyNonce");lastError="";
            Object manager=XposedHelpers.callStaticMethod(XposedHelpers.findClass("f.f",loader),"k",context);
            for(Iterator<String> it=rules.keys();it.hasNext();){String key=it.next();if(!key.equals("booster_config")){JSONObject other=rules.getJSONObject(key);XposedHelpers.callMethod(manager,"q",key,other.getLong("version"),params(other).toString());}}
        }catch(Throwable e){lastError=Protocol.message(e);}reportHook();
    }
    private void restoreSystemRule(){
        try{
            if(lastSystemRule==null||parser==null)throw new Exception("尚未取得系统候选规则，请重启Joyose恢复系统配置");
            Object result=invokeLocal(lastSystemRule.clone());
            if(!Boolean.TRUE.equals(result))throw new Exception("系统候选规则恢复失败，请重启Joyose");applied=-1;lastError="";
        }catch(Throwable e){lastError=Protocol.message(e);}
    }
    private Object invokeLocal(Object[] arguments)throws Throwable{
        Object config=XposedHelpers.callStaticMethod(XposedHelpers.findClass("f0.g0",loader),"G2",context);
        Object previous=XposedHelpers.callMethod(config,"l1");Object result=null;
        localApply.set(true);XposedHelpers.callMethod(config,"B5",-1L);
        try{result=XposedBridge.invokeOriginalMethod(parseMethod,parser,arguments);return result;}
        finally{if(!Boolean.TRUE.equals(result))XposedHelpers.callMethod(config,"B5",previous);localApply.set(false);}
    }
    private void fetch(){
        long nonce=state.optLong("fetchNonce");fetched=nonce;JSONObject result=new JSONObject();
        manualFetch.set(true);
        try{
            networkError.remove();
            // A previous blocked attempt may have cached the hosts loopback result.
            try{XposedHelpers.callStaticMethod(InetAddress.class,"clearDnsCache");}catch(Throwable ignored){}
            result.put("nonce",nonce).put("kind","fetch").put("process",process).put("started",System.currentTimeMillis());
            JSONArray downloaded=new JSONArray();
            if(joyose){
                Class<?> client=XposedHelpers.findClass("com.xiaomi.teg.config.f",loader);
                String url=(String)XposedHelpers.callStaticMethod(client,"b");
                Object parameters=XposedHelpers.callStaticMethod(client,"a",0L);
                String raw=(String)XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.xiaomi.teg.config.c.c",loader),"b",url,parameters);
                if(raw==null)throw new IOException("TEG服务器未返回响应");
                JSONObject response=new JSONObject(raw);if(response.optInt("code")!=200)throw new IOException("TEG服务器返回code="+response.opt("code"));
                JSONObject data=response.getJSONObject("data");JSONArray rules=data.optJSONArray("rules");
                if(rules!=null)for(int i=0;i<rules.length();i++){JSONObject rule=rules.getJSONObject(i);if(rule.optInt("status")==1&&rule.has("content"))addDownloaded(downloaded,rule.optString("moduleKey"),new JSONObject(rule.getString("content")));}
                result.put("success",true).put("count",downloaded.length()).put("serverVersion",data.optLong("maxVersion"))
                    .put("message","TEG服务器已响应，取得"+downloaded.length()+"份规则，原数据库未改写");
            }else{
                settingsRequestOk=false;
                Class<?> conn=XposedHelpers.findClass("com.android.settings.cloud.network.Connection",loader),util=XposedHelpers.findClass("com.android.settings.cloud.CloudNetworkUtils",loader);
                String url=(String)XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.android.settings.cloud.Constants",loader),"CLOUD_APP_CONTROL_ALL_URL");
                result.put("endpoint",url);
                Object request=XposedHelpers.newInstance(conn,url);XposedHelpers.callMethod(request,"setUseGet",false);
                Object base=XposedHelpers.callStaticMethod(util,"getBaseParameterForConn",request,context);
                Map<String,String> parameters=(Map<String,String>)XposedHelpers.callStaticMethod(util,"getRequestParams",context);
                parameters.put("dataVersion","0");XposedHelpers.callStaticMethod(util,"addRequestParameter",base,parameters);
                Object status=XposedHelpers.callMethod(request,"requestJSON");if(!String.valueOf(status).equals("OK"))throw new IOException("系统云控网络结果："+status+(networkError.get()==null?"":" / "+networkError.get()));
                JSONObject response=(JSONObject)XposedHelpers.callMethod(request,"getResponse");
                if(response==null||response.has("code"))throw new IOException("系统云控错误："+(response==null?"无响应":response.opt("code")));
                JSONArray rules=response.getJSONArray("data");
                for(int i=0;i<rules.length();i++){JSONObject rule=rules.getJSONObject(i);if(rule.optString("packageName").equals("com.xiaomi.joyose")&&rule.optInt("status")==1)addDownloaded(downloaded,rule.getString("moduleName"),new JSONObject(rule.getString("productData")));}
                result.put("success",true).put("count",downloaded.length()).put("serverVersion",response.optLong("maxVersion"))
                    .put("message","系统云控服务器已响应，取得"+downloaded.length()+"份Joyose规则，原数据库未改写");
            }
            JSONObject file=new JSONObject().put("nonce",nonce).put("time",System.currentTimeMillis()).put("rules",downloaded);
            byte[] bytes=file.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>Protocol.MAX_BYTES)throw new IOException("下载规则超过4 MiB");
            try(OutputStream out=context.getContentResolver().openOutputStream(Uri.parse("content://"+Protocol.AUTH+"/stage/"+(joyose?"joyose":"settings")+"/"+nonce),"w")){out.write(bytes);}
        }catch(Throwable e){try{result.put("success",false).put("message",Protocol.message(e));}catch(Exception ignored){}}
        finally{manualFetch.set(false);networkError.remove();}
        sendReport(result);
    }
    private void addDownloaded(JSONArray output,String name,JSONObject raw)throws Exception{
        Object value=raw.get("params");JSONObject params=value instanceof JSONObject?(JSONObject)value:new JSONObject(String.valueOf(value));
        output.put(new JSONObject().put("module",name).put("version",raw.getLong("version")).put("params",params));
    }
    private volatile boolean settingsRequestOk;
    private void installNetworkHooks(){
        XC_MethodHook requestGuard=new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p)throws Throwable{
                if(!shouldBlock())return;
                for(int i=0;i<p.args.length;i++)if(p.args[i] instanceof String&&((String)p.args[i]).matches("(?i)https?://.*")){
                    String original=(String)p.args[i];p.args[i]=loopback(original);blockedCount++;lastBlocked=new URL(original).getHost();break;
                }
            }
        };
        try{
            if(joyose){
                // The private common HTTP entry handles retrieval AND upload/discovery URLs.
                Class<?> http=XposedHelpers.findClass("com.xiaomi.teg.config.c.c",loader);
                XposedHelpers.findAndHookMethod(http,"a",String.class,String.class,Map.class,boolean.class,requestGuard);
                // Redirect cloud attachment downloads before handing them to DownloadManager.
                XposedBridge.hookAllMethods(XposedHelpers.findClass("f.g",loader),"a",requestGuard);
            }else{
                XposedHelpers.findAndHookMethod("com.android.settings.cloud.util.Utils",loader,"log",String.class,new XC_MethodHook(){
                    @Override protected void beforeHookedMethod(MethodHookParam p){String message=(String)p.args[0];if(Boolean.TRUE.equals(manualFetch.get())&&message!=null&&message.contains("Exception"))networkError.set(message);}
                });
                Class<?> conn=XposedHelpers.findClass("com.android.settings.cloud.network.Connection",loader);
                XposedBridge.hookAllConstructors(conn,requestGuard);
                XposedHelpers.findAndHookMethod(conn,"innerRequest",String.class,String.class,boolean.class,boolean.class,OutputStream.class,requestGuard);
                XposedHelpers.findAndHookMethod(conn,"requestJSON",new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){if(state.optLong("fetchUntil")>System.currentTimeMillis()&&String.valueOf(p.getResult()).equals("OK"))settingsRequestOk=true;}});
                XposedBridge.hookAllMethods(XposedHelpers.findClass("com.android.settings.cloud.released.DownloadUtil",loader),"accessInternetByPost",requestGuard);
            }
        }catch(Throwable e){lastError="云控专用网络入口："+Protocol.message(e);}
        try{
            XposedHelpers.findAndHookMethod("com.xiaomi.push.ch",loader,"a",ArrayList.class,String.class,String.class,boolean.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){if(shouldBlock()&&state.optBoolean("discoveryBlock",true)){blockedCount++;lastBlocked="resolver.msg.xiaomi.net / GSLB";p.setThrowable(new IOException("JoyoseLeash: address discovery redirected to loopback policy"));}}
            });
        }catch(Throwable e){if(!joyose)lastError="地址发现入口："+Protocol.message(e);}
        // Domain interception covers cached endpoint builders and address-discovery requests.
        XposedHelpers.findAndHookMethod(URL.class,"openConnection",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p)throws Throwable{
                URL url=(URL)p.thisObject;if(shouldBlock()&&blockedHost(url.getHost())){
                    blockedCount++;lastBlocked=url.getHost();
                    p.setResult(new URL(loopback(url.toString())).openConnection());
                }
            }
        });
        XposedHelpers.findAndHookMethod(InetAddress.class,"getAllByName",String.class,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p)throws Throwable{
                String host=(String)p.args[0];InetAddress[] manual=manualAddresses(host);
                if(manual!=null)p.setResult(manual);
                else if(shouldBlock()&&blockedHost(host))p.setResult(new InetAddress[]{InetAddress.getByAddress(new byte[]{127,0,0,1}),InetAddress.getByAddress(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1})});
            }
        });
        XposedHelpers.findAndHookMethod(InetAddress.class,"getByName",String.class,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p)throws Throwable{String host=(String)p.args[0];InetAddress[] manual=manualAddresses(host);if(manual!=null)p.setResult(manual[0]);else if(shouldBlock()&&blockedHost(host))p.setResult(InetAddress.getByAddress(new byte[]{127,0,0,1}));}
        });
    }
    private InetAddress[] manualAddresses(String host)throws Exception{
        if(host==null||!Boolean.TRUE.equals(manualFetch.get())||state.optLong("fetchUntil")<=System.currentTimeMillis())return null;
        JSONObject map=state.optJSONObject("ips");JSONArray ips=map==null?null:map.optJSONArray(host.toLowerCase(Locale.ROOT));
        if(ips==null||ips.length()==0)return null;ArrayList<InetAddress> result=new ArrayList<>();
        for(int i=0;i<ips.length();i++){String ip=ips.getString(i);
            if(!ip.matches("[0-9a-fA-F:.]+"))continue;
            InetAddress address=InetAddress.getByName(ip);
            if(!address.isLoopbackAddress()&&!address.isAnyLocalAddress())result.add(InetAddress.getByAddress(host,address.getAddress()));
        }
        return result.isEmpty()?null:result.toArray(new InetAddress[0]);
    }
    private boolean shouldBlock(){return state.optBoolean("networkBlocked",true)&&!(Boolean.TRUE.equals(manualFetch.get())&&state.optLong("fetchUntil")>System.currentTimeMillis());}
    private boolean blockedHost(String host){
        if(host==null)return false;host=host.toLowerCase(Locale.ROOT);JSONArray names=state.optJSONArray("domains");
        if(names!=null)for(int i=0;i<names.length();i++){
            String n=names.optString(i);if(n.equals("resolver.msg.xiaomi.net")&&!state.optBoolean("discoveryBlock",true))continue;
            if(host.equals(n)||host.endsWith("."+n))return true;
        }return false;
    }
    private String loopback(String value)throws Exception{URL u=new URL(value);return new URL(u.getProtocol(),"127.0.0.1",u.getPort(),u.getFile()).toString();}
    private void reportHook(){
        try{JSONObject r=new JSONObject().put("kind","hook").put("process",process).put("ready",adapterReady).put("pid",android.os.Process.myPid())
            .put("appliedNonce",applied).put("blockedRequests",blockedCount).put("lastBlockedHost",lastBlocked).put("error",lastError)
            .put("engine","Zygisk Next / LSPosed").put("adapter",joyose?"Joyose-2.5.21":"HTMLViewer-remote").put("build","0.3.0");
            if(joyose&&adapterReady)try{Object config=XposedHelpers.callStaticMethod(XposedHelpers.findClass("f0.g0",loader),"G2",context);
                r.put("activeVersion",XposedHelpers.callMethod(config,"l1"))
                 .put("subwayMGAME",String.valueOf(XposedHelpers.callMethod(config,"Q1","com.kiloo.subwaysurf.gw")))
                 .put("subwayTGAME",String.valueOf(XposedHelpers.callMethod(config,"K1","com.kiloo.subwaysurf.gw")));
            }catch(Throwable ignored){}
            sendReport(r);}catch(Exception ignored){}
    }
    private void sendReport(JSONObject report){
        try{Bundle b=new Bundle();b.putString("json",report.toString());Bundle reply=context.getContentResolver().call(Uri.parse("content://"+Protocol.AUTH),"report",null,b);
            if(reply!=null&&reply.containsKey("error"))android.util.Log.e("JoyoseLeash",reply.getString("error"));
        }catch(Throwable e){android.util.Log.e("JoyoseLeash",Protocol.message(e));XposedBridge.log("JoyoseLeash: "+Protocol.message(e));}
    }
}
