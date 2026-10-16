package com.muyue.joyoseleash;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Process;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

/** Root-only RPC. The original Xiaomi databases are opened read-only. */
public final class Backend {
    static final File STATE=new File(Protocol.FILES,"state.json");
    static final File ROOT=new File(Protocol.DATA);
    static int appUid;
    public static void main(String[] args){
        JSONObject response=new JSONObject();
        try {
            if(Process.myUid()!=0)throw new Exception("此操作需要Root权限");
            if(args.length==0)throw new Exception("缺少操作");
            ROOT.mkdirs();new File(ROOT,"history").mkdirs();new File(ROOT,"inbox").mkdirs();
            appUid=getUid();
            String raw=Protocol.read(System.in);
            if(args.length>1&&args[1].equals("--base64"))raw=new String(Base64.decode(raw,Base64.DEFAULT),StandardCharsets.UTF_8);
            JSONObject request=raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);
            try(RandomAccessFile f=new RandomAccessFile(new File(ROOT,"backend.lock"),"rw");FileLock lock=f.getChannel().lock()){
                JSONObject state=load();response.put("ok",true).put("data",action(args[0],request,state));
            }
        }catch(Throwable e){try{response=new JSONObject().put("ok",false).put("error",Protocol.message(e));}catch(Exception ignored){}}
        System.out.println(response.toString());
    }
    static int getUid()throws Exception{
        String text=run("/system/bin/pm","list","packages","-U",Protocol.PKG);
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("package:"+java.util.regex.Pattern.quote(Protocol.PKG)+" uid:(\\d+)").matcher(text);
        if(!m.find())throw new Exception("请先安装配套的云控拴绳APK");return Integer.parseInt(m.group(1));
    }
    static JSONObject load()throws Exception{
        if(STATE.isFile()){
            JSONObject existing=new JSONObject(Protocol.read(STATE));
            if(existing.optInt("schema",1)<2){
                JSONArray hosts=existing.getJSONArray("domains");Set<String> seen=new HashSet<>();
                for(int i=0;i<hosts.length();i++)seen.add(hosts.getString(i));
                for(String h:new String[]{"api.sec.miui.com","api.sec.intl.miui.com"})if(seen.add(h))hosts.put(h);
                existing.put("schema",2);save(existing,true);
            }
            return existing;
        }
        JSONObject state=new JSONObject().put("schema",2).put("revision",1).put("networkBlocked",true)
            .put("locked",false).put("enabled",false).put("applyNonce",0).put("fetchNonce",0).put("fetchUntil",0)
            .put("discoveryBlock",true).put("domains",new JSONArray(Arrays.asList(Protocol.HOSTS)))
            .put("ips",new JSONObject().put("resolver.msg.xiaomi.net",new JSONArray(Arrays.asList("111.13.142.153","111.202.1.252")))).put("profiles",new JSONObject());
        JSONArray entries=catalog();
        for(int i=0;i<entries.length();i++){
            JSONObject e=entries.getJSONObject(i);String key=e.getString("module");JSONObject old=state.getJSONObject("profiles").optJSONObject(key);
            if(old==null||e.getLong("version")>old.getLong("version"))state.getJSONObject("profiles").put(key,new JSONObject().put("version",e.getLong("version")).put("params",e.getJSONObject("params")));
        }
        save(state,false);return state;
    }
    static Object action(String action,JSONObject r,JSONObject s)throws Exception{
        switch(action){
        case "init":return status(s);
        case "status":return status(s);
        case "catalog":return catalog();
        case "library":{
            JSONArray result=new JSONArray(),entries=catalog();
            for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);String source=entry.getString("source");
                JSONObject info=ruleInfo(entry.getString("module"),entry).put("id",ruleId(entry)).put("source",source)
                    .put("kind",source.startsWith("服务器实时拉取")?"cloud":source.equals("ROM预置")?"rom":"system");result.put(info);
            }return result;
        }
        case "library-rule":return libraryRule(r);
        case "commit":{
            if(r.has("baseProfiles")&&!r.getJSONObject("baseProfiles").toString().equals(s.getJSONObject("profiles").toString()))throw new Exception("当前规则已变化，请刷新后重试");
            JSONObject profiles=r.getJSONObject("profiles");for(Iterator<String> it=profiles.keys();it.hasNext();){String key=it.next();Protocol.validateProfile(key,profiles.getJSONObject(key));}
            s.put("profiles",profiles).put("enabled",true).put("locked",r.optBoolean("locked",s.optBoolean("locked"))).put("applyNonce",s.optLong("applyNonce")+1);
            save(s,true);notifyChanged();return status(s);
        }
        case "replace-rule":{
            if(r.has("baseProfiles")&&!r.getJSONObject("baseProfiles").toString().equals(s.getJSONObject("profiles").toString()))throw new Exception("当前规则已变化，请刷新后重试");
            JSONObject entry=libraryRule(r);String module=entry.getString("module");JSONObject profile=new JSONObject().put("version",entry.getLong("version")).put("params",entry.getJSONObject("params"));Protocol.validateProfile(module,profile);
            s.getJSONObject("profiles").put(module,profile);s.put("enabled",true).put("applyNonce",s.optLong("applyNonce")+1);save(s,true);notifyChanged();return status(s);
        }
        case "save":{
            if(r.has("baseProfiles")&&!r.getJSONObject("baseProfiles").toString().equals(s.getJSONObject("profiles").toString()))throw new Exception("规则已被其他操作修改，请刷新后重试");
            JSONObject profiles=r.getJSONObject("profiles");for(Iterator<String> it=profiles.keys();it.hasNext();){String key=it.next();Protocol.validateProfile(key,profiles.getJSONObject(key));}
            s.put("profiles",profiles);if(r.has("locked"))s.put("locked",r.getBoolean("locked"));
            save(s,true);notifyChanged();return status(s);
        }
        case "apply":s.put("enabled",true).put("applyNonce",s.optLong("applyNonce")+1);save(s,true);notifyChanged();return status(s);
        case "lock":s.put("locked",r.getBoolean("locked"));if(s.getBoolean("locked")){s.put("enabled",true).put("applyNonce",s.optLong("applyNonce")+1);}save(s,true);notifyChanged();return status(s);
        case "disable-override":s.put("enabled",false).put("locked",false);save(s,true);notifyChanged();return status(s);
        case "network":{
            boolean blocked=r.getBoolean("blocked");
            if(r.has("domains"))s.put("domains",validateHosts(r.getJSONArray("domains")));
            s.put("networkBlocked",blocked).put("discoveryBlock",r.optBoolean("discoveryBlock",s.optBoolean("discoveryBlock",true))).put("fetchUntil",0);
            if(blocked)resolveIps(s);save(s,true);writeNetwork(s);notifyChanged();return status(s);
        }
        case "refresh-ips":resolveIps(s);save(s,false);writeNetwork(s);notifyChanged();return status(s);
        case "reconcile":{
            JSONObject a=Protocol.object(new File(Protocol.FILES,"fetch-joyose.json")),b=Protocol.object(new File(Protocol.FILES,"fetch-settings.json"));
            boolean completed=s.optLong("fetchNonce")>0&&a.optLong("nonce")==s.optLong("fetchNonce")&&b.optLong("nonce")==s.optLong("fetchNonce");
            if(s.optLong("fetchUntil")>0&&(completed||s.optLong("fetchUntil")<=System.currentTimeMillis())){s.put("fetchUntil",0);save(s,false);notifyChanged();}
            writeNetwork(s);return status(s);
        }
        case "fetch":{
            JSONObject hook=Protocol.object(new File(Protocol.FILES,"hook-joyose.json"));
            if(!hook.optBoolean("ready")||System.currentTimeMillis()-hook.optLong("time")>24*3600000L)throw new Exception("Joyose钩子未就绪；请启用LSPosed作用域并重启Joyose/手机");
            if(s.optLong("fetchUntil")>System.currentTimeMillis())throw new Exception("已有云端拉取任务进行中");
            try{run("/system/bin/content","gettype","--uri","content://com.android.settings.cloud.CloudSettings/cloud_all_data");}catch(Exception ignored){}
            // Android's cached-process firewall otherwise blocks HTMLViewer after
            // its short provider-start grace period. This allowance expires itself.
            try{run("/system/bin/cmd","deviceidle","tempwhitelist","-d","90000","com.android.htmlviewer");s.put("fetchBackgroundError","");}
            catch(Exception e){s.put("fetchBackgroundError",Protocol.message(e));}
            s.put("fetchNonce",s.optLong("fetchNonce")+1).put("fetchUntil",System.currentTimeMillis()+90000);
            new File(Protocol.FILES,"fetch-joyose.json").delete();new File(Protocol.FILES,"fetch-settings.json").delete();
            save(s,false);writeNetwork(s);notifyChanged();
            new ProcessBuilder("/system/bin/sh",Protocol.MODULE+"/lease.sh",String.valueOf(s.getLong("fetchNonce"))).redirectErrorStream(true).redirectOutput(new File(ROOT,"lease.log")).start();
            return status(s);
        }
        case "history":{
            JSONArray a=new JSONArray();File[] files=new File(ROOT,"history").listFiles();if(files!=null){Arrays.sort(files,Comparator.comparing(File::getName).reversed());for(File f:files)if(f.getName().matches("[0-9]+\\.json")){
                try{JSONObject snapshot=Protocol.object(f).getJSONObject("profiles");JSONArray modules=new JSONArray();for(Iterator<String> it=snapshot.keys();it.hasNext();){String name=it.next();modules.put(ruleInfo(name,snapshot.getJSONObject(name)));}
                    a.put(new JSONObject().put("id",f.getName().replace(".json","")).put("time",f.lastModified()).put("modules",modules));
                }catch(Exception ignored){}
            }}return a;
        }
        case "rollback":{
            String id=r.getString("id");if(!id.matches("[0-9]{1,20}"))throw new Exception("备份编号不合法");
            JSONObject old=new JSONObject(Protocol.read(new File(ROOT,"history/"+id+".json")));
            JSONObject profiles=old.getJSONObject("profiles");for(Iterator<String> it=profiles.keys();it.hasNext();){String k=it.next();Protocol.validateProfile(k,profiles.getJSONObject(k));}
            s.put("profiles",profiles).put("enabled",true).put("locked",old.optBoolean("locked")).put("applyNonce",s.optLong("applyNonce")+1);save(s,true);notifyChanged();return status(s);
        }
        case "restore-network":s.put("networkBlocked",false).put("fetchUntil",0);save(s,false);writeNetwork(s);notifyChanged();return status(s);
        default:throw new Exception("不支持的操作："+action);
        }
    }
    static JSONObject ruleInfo(String name,JSONObject rule)throws Exception{
        JSONObject result=new JSONObject().put("module",name).put("version",rule.getLong("version")).put("fingerprint",ruleId(new JSONObject().put("module",name).put("version",rule.getLong("version")).put("params",rule.getJSONObject("params"))));JSONObject game=rule.getJSONObject("params").optJSONObject("game_booster");
        JSONObject config=game==null?null:game.optJSONObject("booster_config");JSONArray apps=config==null?null:config.optJSONArray("ovrride_config");result.put("games",apps==null?0:apps.length());
        if(apps!=null)for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);if(app.optString("game_name").equals("com.kiloo.subwaysurf.gw"))result.put("subwayMGAME",app.optString("dynamic_fps_M")).put("subwayTGAME",app.optString("dynamic_fps"));}
        return result;
    }
    static String ruleId(JSONObject rule)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest((rule.getString("module")+"\n"+rule.getLong("version")+"\n"+rule.getJSONObject("params").toString()).getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
    }
    static JSONObject libraryRule(JSONObject request)throws Exception{
        String id=request.getString("id"),module=request.getString("module");
        if(request.optString("kind").equals("history")){
            if(!id.matches("[0-9]{1,20}"))throw new Exception("备份编号无效");JSONObject profile=Protocol.object(new File(ROOT,"history/"+id+".json")).getJSONObject("profiles").getJSONObject(module);
            return new JSONObject().put("module",module).put("version",profile.getLong("version")).put("params",profile.getJSONObject("params"));
        }
        if(!id.matches("[0-9a-f]{64}"))throw new Exception("规则编号无效");JSONArray entries=catalog();for(int i=0;i<entries.length();i++){JSONObject rule=entries.getJSONObject(i);if(rule.getString("module").equals(module)&&ruleId(rule).equals(id))return rule;}throw new Exception("规则已变化，请刷新规则库");
    }
    static JSONObject status(JSONObject s)throws Exception{
        JSONObject result=new JSONObject().put("state",s).put("device",android.os.Build.MODEL).put("sdk",android.os.Build.VERSION.SDK_INT)
            .put("hookJoyose",runtimeReport("joyose"))
            .put("hookSettings",runtimeReport("settings"))
            .put("fetchJoyose",Protocol.object(new File(Protocol.FILES,"fetch-joyose.json")))
            .put("fetchSettings",Protocol.object(new File(Protocol.FILES,"fetch-settings.json")))
            .put("network",Protocol.object(new File(ROOT,"network-status.json")));
        try{result.put("df",Protocol.read(new File("/data/system/mcd/df"))).put("boardTemp",Double.parseDouble(Protocol.read(new File("/sys/class/thermal/thermal_message/board_sensor_temp")).trim())/1000.0);}catch(Exception ignored){}
        return result;
    }
    static JSONObject runtimeReport(String role)throws Exception{
        JSONObject report=Protocol.object(new File(Protocol.FILES,"hook-"+role+".json"));int pid=report.optInt("pid",-1);
        if(pid>0){try{String cmd=Protocol.read(new File("/proc/"+pid+"/cmdline"));String expect=role.equals("joyose")?"com.xiaomi.joyose":"com.android.htmlviewer";if(!cmd.startsWith(expect))throw new Exception();}
            catch(Exception e){report.put("ready",false).put("error","目标进程已退出，等待钩子重新连接");}}
        return report;
    }
    static void save(JSONObject s,boolean backup)throws Exception{
        if(backup&&STATE.isFile())Protocol.atomic(new File(ROOT,"history/"+System.currentTimeMillis()+".json"),Protocol.read(STATE));
        s.put("revision",s.optLong("revision")+1);
        new File(Protocol.FILES).mkdirs();
        run("/system/bin/chown",appUid+":"+appUid,Protocol.FILES);run("/system/bin/chmod","700",Protocol.FILES);
        run("/system/bin/restorecon",Protocol.FILES);Protocol.atomic(STATE,s.toString());
        run("/system/bin/chown",appUid+":"+appUid,STATE.getPath());run("/system/bin/chmod","600",STATE.getPath());
        run("/system/bin/restorecon",STATE.getPath());
        Protocol.atomic(new File(ROOT,"state-backup.json"),s.toString());
        File[] files=new File(ROOT,"history").listFiles();if(files!=null&&files.length>30){Arrays.sort(files,Comparator.comparing(File::getName));for(int i=0;i<files.length-30;i++)files[i].delete();}
    }
    static void notifyChanged(){try{run("/system/bin/content","call","--uri","content://"+Protocol.AUTH,"--method","changed");}catch(Exception ignored){}}
    static JSONArray validateHosts(JSONArray input)throws Exception{
        if(input.length()>128)throw new Exception("地址最多128条");JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();
        for(int i=0;i<input.length();i++){String h=input.getString(i).trim().toLowerCase(Locale.ROOT);
            if(!h.matches("(?=.{1,253}$)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?"))throw new Exception("域名不合法："+h);
            if(seen.add(h))result.put(h);
        }return result;
    }
    static void resolveIps(JSONObject s)throws Exception{
        // Only remove this module's hosts mount; firewall can remain during DNS resolution.
        run("/system/bin/sh",Protocol.MODULE+"/net.sh","dns-window");
        JSONObject ips=s.optJSONObject("ips");if(ips==null)ips=new JSONObject();
        JSONArray hosts=validateHosts(s.getJSONArray("domains"));ExecutorService pool=Executors.newFixedThreadPool(4,task->{Thread t=new Thread(task,"cloud-dns");t.setDaemon(true);return t;});
        try{List<Future<?>> futures=new ArrayList<>();final JSONObject map=ips;
            for(int i=0;i<hosts.length();i++){final String h=hosts.getString(i);futures.add(pool.submit(()->{
                try{Set<String> found=new TreeSet<>();synchronized(map){JSONArray previous=map.optJSONArray(h);if(previous!=null)for(int j=0;j<previous.length();j++)found.add(previous.getString(j));}
                    for(InetAddress addr:InetAddress.getAllByName(h))if(!addr.isLoopbackAddress()&&!addr.isAnyLocalAddress())found.add(addr.getHostAddress().split("%",2)[0]);
                    synchronized(map){map.put(h,new JSONArray(found));}
                }catch(Exception ignored){}
            }));}
            long deadline=System.currentTimeMillis()+12000;for(Future<?> f:futures){long left=deadline-System.currentTimeMillis();if(left<=0)break;try{f.get(left,TimeUnit.MILLISECONDS);}catch(Exception ignored){}}
        }finally{pool.shutdownNow();s.put("ips",ips);}
    }
    static void writeNetwork(JSONObject s)throws Exception{
        File dir=new File(ROOT,"net");dir.mkdirs();Set<String> v4=new TreeSet<>(),v6=new TreeSet<>();
        JSONObject ips=s.optJSONObject("ips");JSONArray hosts=validateHosts(s.getJSONArray("domains"));StringBuilder entries=new StringBuilder();
        for(int i=0;i<hosts.length();i++){
            String host=hosts.getString(i);if(!s.optBoolean("discoveryBlock",true)&&host.equals("resolver.msg.xiaomi.net"))continue;
            entries.append("127.0.0.1 ").append(host).append('\n').append("::1 ").append(host).append('\n');
            JSONArray addrs=ips==null?null:ips.optJSONArray(host);if(addrs==null)continue;
            for(int j=0;j<addrs.length();j++){String ip=addrs.getString(j);if(ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))v4.add(ip);else if(ip.matches("[0-9a-fA-F:]+")&&ip.contains(":"))v6.add(ip);}
        }
        Protocol.atomic(new File(dir,"host-entries"),entries.toString());Protocol.atomic(new File(dir,"ips4"),String.join("\n",v4)+"\n");Protocol.atomic(new File(dir,"ips6"),String.join("\n",v6)+"\n");
        String output=run("/system/bin/sh",Protocol.MODULE+"/net.sh",Protocol.networkBlocked(s)?"block":"open");
        s.put("networkLastApply",output.trim());
    }
    static JSONArray catalog()throws Exception{
        JSONArray result=new JSONArray();
        File smart=new File("/data/user/0/com.xiaomi.joyose/databases/SmartP.db");
        if(smart.isFile())try(SQLiteDatabase db=SQLiteDatabase.openDatabase(smart.getPath(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);Cursor c=db.rawQuery("SELECT config_name,version,params FROM cloud_config WHERE enable=1",null)){
            while(c.moveToNext())addRule(result,"SmartP本地缓存",c.getString(0),c.getLong(1),c.getString(2));
        }
        File teg=new File("/data/user/0/com.xiaomi.joyose/databases/teg_config.db");
        if(teg.isFile())try(SQLiteDatabase db=SQLiteDatabase.openDatabase(teg.getPath(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);Cursor c=db.rawQuery("SELECT rule_module,rule_content FROM rules",null)){
            while(c.moveToNext())try{JSONObject o=new JSONObject(c.getString(1));addRule(result,"TEG云控缓存",c.getString(0),o.optLong("version",1),o.get("params").toString());}catch(Exception ignored){}
        }
        File cloud=new File("/data/user_de/0/com.android.htmlviewer/databases/cloud_all_data.db");
        if(cloud.isFile())try(SQLiteDatabase db=SQLiteDatabase.openDatabase(cloud.getPath(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);Cursor c=db.rawQuery("SELECT moduleName,productData FROM cloud_all_data WHERE packageName=? AND status=1",new String[]{"com.xiaomi.joyose"})){
            while(c.moveToNext())try{JSONObject o=new JSONObject(c.getString(1));addRule(result,"系统云控缓存",c.getString(0),o.optLong("version",1),o.get("params").toString());}catch(Exception ignored){}
        }
        try{
            int[] bytes={0x48,0x4b,0x49,0x47,0x49,0x4a,0x48,0x4d,0x49,0x1b,0x49,0x4e,0x49,0x1c,0x49,0x19,0x48,0x4f,0x49,0x4a,0x49,0x1a,0x48,0x4c,0x48,0x4c,0x49,0x1c,0x4d,0x1a,0x49,0x47};
            byte[] key=new byte[32];for(int i=0;i<32;i++)key[i]=(byte)(bytes[i]^0x7f);
            Cipher cipher=Cipher.getInstance("AES/ECB/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"));
            byte[] raw;try(FileInputStream in=new FileInputStream("/odm/etc/default_cloud.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);raw=out.toByteArray();}
            JSONObject rom=new JSONObject(new String(cipher.doFinal(raw),StandardCharsets.UTF_8));
            for(Iterator<String> it=rom.keys();it.hasNext();){String module=it.next();JSONObject o=rom.getJSONObject(module);addRule(result,"ROM预置",module,o.optLong("version",1),o.get("params").toString());}
        }catch(Exception ignored){}
        File[] downloads=new File(Protocol.FILES).listFiles((dir,name)->name.matches("download-(joyose|settings)-[0-9]+\\.json"));
        if(downloads!=null){Arrays.sort(downloads,Comparator.comparing(File::getName).reversed());for(int i=0;i<Math.min(downloads.length,20);i++)try{
            JSONObject batch=new JSONObject(Protocol.read(downloads[i]));JSONArray rules=batch.getJSONArray("rules");
            for(int j=0;j<rules.length();j++){JSONObject rule=rules.getJSONObject(j);addRule(result,"服务器实时拉取 #"+batch.getLong("nonce"),rule.getString("module"),rule.getLong("version"),rule.getJSONObject("params").toString());}
        }catch(Exception ignored){}}
        if(result.length()==0)throw new Exception("未读取到Joyose规则，请确认机型与文件路径");return result;
    }
    static void addRule(JSONArray a,String source,String module,long version,String raw)throws Exception{
        JSONObject p=new JSONObject(raw);a.put(new JSONObject().put("module",module).put("source",source).put("version",version).put("params",p));
    }
    static String run(String...command)throws Exception{
        java.lang.Process p=new ProcessBuilder(command).redirectErrorStream(true).start();
        ExecutorService reader=Executors.newSingleThreadExecutor();Future<String> text=reader.submit(()->Protocol.read(p.getInputStream()));
        try{if(!p.waitFor(25,TimeUnit.SECONDS)){p.destroyForcibly();throw new Exception("命令超时："+command[0]);}String out=text.get(2,TimeUnit.SECONDS);if(p.exitValue()!=0)throw new Exception(out);return out;}finally{reader.shutdownNow();}
    }
}
