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
            if(Process.myUid()!=0)throw Messages.failure("diag_root_required","此操作需要Root权限");
            if(args.length==0)throw Messages.failure("diag_operation_required","缺少操作");
            String raw=Protocol.read(System.in);
            if(args.length>1&&args[1].equals("--base64"))raw=new String(Base64.decode(raw,Base64.DEFAULT),StandardCharsets.UTF_8);
            JSONObject request=raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);
            // Inventory must work even before Joyose has any profiles. Do not initialize
            // state, acquire its writable lock, fetch remotely, or notify any hook here.
            if(args[0].equals("system-sources")||args[0].equals("system-library")||args[0].equals("system-rule")){
                response.put("ok",true).put("data",args[0].equals("system-sources")?systemSources():systemCloud(request,args[0].equals("system-rule")));
            }else{
                ROOT.mkdirs();new File(ROOT,"history").mkdirs();new File(ROOT,"inbox").mkdirs();
                appUid=getUid();
                try(RandomAccessFile f=new RandomAccessFile(new File(ROOT,"backend.lock"),"rw");FileLock lock=f.getChannel().lock()){
                    JSONObject state=load();response.put("ok",true).put("data",action(args[0],request,state));
                }
            }
        }catch(Throwable e){try{response=new JSONObject().put("ok",false).put("error",Protocol.message(e)).put("errorCaption",Protocol.messageCaption(e));}catch(Exception ignored){}}
        System.out.println(response.toString());
    }
    static int getUid()throws Exception{
        String text=run("/system/bin/pm","list","packages","-U",Protocol.PKG);
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("package:"+java.util.regex.Pattern.quote(Protocol.PKG)+" uid:(\\d+)").matcher(text);
        if(!m.find())throw Messages.failure("diag_companion_app_required","请先安装配套的云控拴绳APK");return Integer.parseInt(m.group(1));
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
        case "system-sources":return systemSources();
        case "system-library":return systemCloud(r,false);
        case "system-rule":return systemCloud(r,true);
        case "library":{
            JSONArray result=new JSONArray(),entries=catalog();
            for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);String source=entry.getString("source");
                JSONObject info=ruleInfo(entry.getString("module"),entry).put("id",ruleId(entry)).put("source",source).put("sourceCaption",entry.opt("sourceCaption"))
                    .put("kind",source.startsWith("服务器实时拉取")?"cloud":source.equals("ROM预置")?"rom":"system");result.put(info);
            }return result;
        }
        case "library-rule":return libraryRule(r);
        case "commit":{
            if(r.has("baseProfiles")&&!r.getJSONObject("baseProfiles").toString().equals(s.getJSONObject("profiles").toString()))throw Messages.failure("diag_profiles_changed","当前规则已变化，请刷新后重试");
            JSONObject profiles=r.getJSONObject("profiles");for(Iterator<String> it=profiles.keys();it.hasNext();){String key=it.next();Protocol.validateProfile(key,profiles.getJSONObject(key));}
            s.put("profiles",profiles).put("enabled",true).put("locked",r.optBoolean("locked",s.optBoolean("locked"))).put("applyNonce",s.optLong("applyNonce")+1);
            save(s,true);notifyChanged();return status(s);
        }
        case "replace-rule":{
            if(r.has("baseProfiles")&&!r.getJSONObject("baseProfiles").toString().equals(s.getJSONObject("profiles").toString()))throw Messages.failure("diag_profiles_changed","当前规则已变化，请刷新后重试");
            JSONObject entry=libraryRule(r);String module=entry.getString("module");JSONObject profile=new JSONObject().put("version",entry.getLong("version")).put("params",entry.getJSONObject("params"));Protocol.validateProfile(module,profile);
            s.getJSONObject("profiles").put(module,profile);s.put("enabled",true).put("applyNonce",s.optLong("applyNonce")+1);save(s,true);notifyChanged();return status(s);
        }
        case "save":{
            if(r.has("baseProfiles")&&!r.getJSONObject("baseProfiles").toString().equals(s.getJSONObject("profiles").toString()))throw Messages.failure("diag_profiles_modified_elsewhere","规则已被其他操作修改，请刷新后重试");
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
            if(!hook.optBoolean("ready")||System.currentTimeMillis()-hook.optLong("time")>24*3600000L)throw Messages.failure("diag_joyose_hook_not_ready","Joyose钩子未就绪；请启用LSPosed作用域并重启Joyose/手机");
            if(s.optLong("fetchUntil")>System.currentTimeMillis())throw Messages.failure("diag_fetch_already_running","已有云端拉取任务进行中");
            try{run("/system/bin/content","gettype","--uri","content://com.android.settings.cloud.CloudSettings/cloud_all_data");}catch(Exception ignored){}
            // Android's cached-process firewall otherwise blocks HTMLViewer after
            // its short provider-start grace period. This allowance expires itself.
            try{run("/system/bin/cmd","deviceidle","tempwhitelist","-d","90000","com.android.htmlviewer");s.put("fetchBackgroundError","").remove("fetchBackgroundErrorCaption");}
            catch(Exception e){s.put("fetchBackgroundError",Protocol.message(e)).put("fetchBackgroundErrorCaption",Protocol.messageCaption(e));}
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
            String id=r.getString("id");if(!id.matches("[0-9]{1,20}"))throw Messages.failure("diag_backup_id_invalid","备份编号不合法");
            JSONObject old=new JSONObject(Protocol.read(new File(ROOT,"history/"+id+".json")));
            JSONObject profiles=old.getJSONObject("profiles");for(Iterator<String> it=profiles.keys();it.hasNext();){String k=it.next();Protocol.validateProfile(k,profiles.getJSONObject(k));}
            s.put("profiles",profiles).put("enabled",true).put("locked",old.optBoolean("locked")).put("applyNonce",s.optLong("applyNonce")+1);save(s,true);notifyChanged();return status(s);
        }
        case "restore-network":s.put("networkBlocked",false).put("fetchUntil",0);save(s,false);writeNetwork(s);notifyChanged();return status(s);
        default:throw Messages.failure("diag_operation_unsupported","不支持的操作："+action,action);
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
            if(!id.matches("[0-9]{1,20}"))throw Messages.failure("diag_backup_id_invalid","备份编号无效");JSONObject profile=Protocol.object(new File(ROOT,"history/"+id+".json")).getJSONObject("profiles").getJSONObject(module);
            return new JSONObject().put("module",module).put("version",profile.getLong("version")).put("params",profile.getJSONObject("params"));
        }
        if(!id.matches("[0-9a-f]{64}"))throw Messages.failure("diag_rule_id_invalid","规则编号无效");JSONArray entries=catalog();for(int i=0;i<entries.length();i++){JSONObject rule=entries.getJSONObject(i);if(rule.getString("module").equals(module)&&ruleId(rule).equals(id))return rule;}throw Messages.failure("diag_rule_changed","规则已变化，请刷新规则库");
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
            catch(Exception e){report.put("ready",false).put("error","目标进程已退出，等待钩子重新连接").put("errorCaption",Protocol.caption(Messages.text("diag_hook_process_exited","目标进程已退出，等待钩子重新连接")));}}
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
        if(input.length()>128)throw Messages.failure("diag_host_count_limit","地址最多128条");JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();
        for(int i=0;i<input.length();i++){String h=input.getString(i).trim().toLowerCase(Locale.ROOT);
            if(!h.matches("(?=.{1,253}$)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?"))throw Messages.failure("diag_host_invalid","域名不合法："+h,h);
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
        if(result.length()==0)throw Messages.failure("diag_no_joyose_rules","未读取到Joyose规则，请确认机型与文件路径");return result;
    }
    static void addRule(JSONArray a,String source,String module,long version,String raw)throws Exception{
        JSONObject p=new JSONObject(raw);a.put(new JSONObject().put("module",module).put("source",source).put("sourceCaption",Protocol.caption(sourceCaption(source))).put("version",version).put("params",p));
    }
    /** Generated source labels stay canonical for source-kind logic above. */
    static Messages.Text sourceCaption(String source){
        switch(source){
        case "SmartP本地缓存":return Messages.text("diag_source_smartp_cache",source);
        case "TEG云控缓存":return Messages.text("diag_source_teg_cache",source);
        case "系统云控缓存":return Messages.text("diag_source_system_cache",source);
        case "ROM预置":return Messages.text("diag_source_rom",source);
        default:
            String prefix="服务器实时拉取 #";
            return source.startsWith(prefix)?Messages.text("diag_source_server_fetch",source,source.substring(prefix.length())):null;
        }
    }
    static final String SYSTEM_CLOUD_PATH="/data/user_de/0/com.android.htmlviewer/databases/cloud_all_data.db";
    static final String SYSTEM_CLOUD_TABLE="cloud_all_data";
    static final String COMMON_CLOUD_PATH="/data/system/common_cloud_backup.xml";
    static final String WM_CLOUD_PATH="/data/system/cloud_control/cloud_control_cache.json";
    static final String[] SYSTEM_SOURCE_IDS={"settings-db","common-backup","wm-cache"};
    static final int CLOUD_RAW_BYTES=128*1024,CLOUD_ID_BYTES=1024,CLOUD_META_BYTES=256;
    static final String[] CLOUD_META={"status","version","dataVersion","createTime","updateTime","lastUpdateTime"};

    static JSONObject cloudSource(String id)throws Exception{
        JSONObject source=new JSONObject().put("id",id).put("sourceId",id).put("readOnly",true).put("available",false)
            .put("overrideSupported",false).put("columns",new JSONArray()).put("maxRawBytes",CLOUD_RAW_BYTES).put("maxLimit",100);
        switch(id){
        case "settings-db":return source.put("label","系统设置云控数据库").put("labelCaption",Protocol.caption(Messages.text("diag_source_settings_database","系统设置云控数据库"))).put("kind","sqlite").put("path",SYSTEM_CLOUD_PATH).put("table",SYSTEM_CLOUD_TABLE);
        case "common-backup":return source.put("label","系统公共云控备份").put("labelCaption",Protocol.caption(Messages.text("diag_source_common_backup","系统公共云控备份"))).put("kind","xml").put("path",COMMON_CLOUD_PATH).put("maxSourceBytes",Protocol.MAX_BYTES);
        case "wm-cache":return source.put("label","窗口管理云控缓存").put("labelCaption",Protocol.caption(Messages.text("diag_source_window_manager_cache","窗口管理云控缓存"))).put("kind","json").put("path",WM_CLOUD_PATH).put("maxSourceBytes",Protocol.MAX_BYTES);
        default:return source.put("label","未知来源").put("labelCaption",Protocol.caption(Messages.text("diag_source_unknown","未知来源"))).put("kind","unknown").put("path",JSONObject.NULL);
        }
    }
    /** Small descriptors/counts only: this RPC never returns raw source payloads. */
    static JSONObject systemSources()throws Exception{
        JSONArray sources=new JSONArray();
        for(String id:SYSTEM_SOURCE_IDS){
            JSONObject inventory=systemCloud(new JSONObject().put("sourceId",id).put("limit",1),false);
            JSONObject source=inventory.getJSONObject("source");source.put("recordCount",inventory.optLong("total",0));
            if(inventory.has("error"))source.put("error",inventory.getJSONObject("error"));sources.put(source);
        }
        return new JSONObject().put("sources",sources).put("readOnly",true).put("overrideSupported",false);
    }
    /** Separate, read-only inventory. Nothing returned here is a Joyose profile. */
    static JSONObject systemCloud(JSONObject request,boolean full)throws Exception{
        String id;
        try{id=CloudCatalog.text(request,"sourceId","settings-db",32);}
        catch(IllegalArgumentException e){return cloudError(cloudEnvelope(cloudSource("unknown"),full),"invalid_source","sourceId须为固定来源编号");}
        JSONObject source=cloudSource(id),result=cloudEnvelope(source,full);
        if(id.equals("common-backup")||id.equals("wm-cache"))return systemFileCloud(id,request,full,source);
        if(!id.equals("settings-db"))return cloudError(result,"invalid_source","仅支持settings-db、common-backup、wm-cache三个只读来源");
        File file=new File(SYSTEM_CLOUD_PATH);
        if(!file.isFile())return cloudError(result,"source_missing","系统云控数据库尚不存在；不会为读取而启动下载或创建数据库");
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS)){
            if(!db.isReadOnly())return cloudError(result,"source_not_readonly","未取得只读数据库连接");
            return readSystemCloud(db,request,full,source);
        }catch(Exception e){
            source.put("available",false);
            return cloudError(cloudEnvelope(source,full),"source_unavailable","系统云控数据库无法只读读取："+cloudShort(Protocol.message(e),240),Messages.text("diag_cloud_database_unavailable","系统云控数据库无法只读读取："+cloudShort(Protocol.message(e),240),cloudDiagnosticDetail(e)));
        }
    }
    static JSONObject systemFileCloud(String id,JSONObject request,boolean full,JSONObject source)throws Exception{
        JSONObject result=cloudEnvelope(source,full);File file=new File(source.getString("path"));
        if(!file.isFile())return cloudError(result,"source_missing","该系统云控来源尚不存在；不会创建、下载或改写来源");
        try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){if(out.size()+n>Protocol.MAX_BYTES)throw new CloudSourceFailure("source_too_large","来源超过4 MiB，已停止只读读取");out.write(buffer,0,n);}
            return readFileCloud(id,out.toByteArray(),request,full,source);
        }catch(CloudSourceFailure e){return cloudError(result,e.code,e.getMessage(),e.caption);}
        catch(IOException e){return cloudError(result,"source_unavailable","无法只读读取该固定系统云控文件，请检查Root权限与ROM路径");}
    }
    static final class CloudSourceFailure extends IOException{
        final String code;final Messages.Text caption;
        CloudSourceFailure(String code,String message){super(message);this.code=code;this.caption=Messages.legacy(message);}
    }
    static String cloudDigest(byte[] bytes)throws Exception{
        StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
    }
    static String cloudUtf8(byte[] bytes)throws java.nio.charset.CharacterCodingException{
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }
    /** Bounded in-memory source seam, also used by fixture tests without touching real files. */
    static JSONObject readFileCloud(String id,byte[] bytes,JSONObject request,boolean full,JSONObject source)throws Exception{
        JSONObject result=cloudEnvelope(source,full);List<CloudFileRecord> records;
        try{
            if(bytes.length>Protocol.MAX_BYTES)throw new CloudSourceFailure("source_too_large","来源超过4 MiB，已停止只读读取");
            String digest=cloudDigest(bytes);source.put("sha256",digest).put("bytes",bytes.length).put("snapshot",true);
            if(id.equals("common-backup"))records=commonCloudRecords(bytes,digest);
            else if(id.equals("wm-cache")){
                Object raw=JSONObject.NULL;String encoding="utf-8";
                if(bytes.length<=CLOUD_RAW_BYTES){try{raw=cloudUtf8(bytes);}catch(java.nio.charset.CharacterCodingException e){raw=Base64.encodeToString(bytes,Base64.NO_WRAP);encoding="base64";}}
                records=Collections.singletonList(new CloudFileRecord(id,digest,0,JSONObject.NULL,"window_manager",raw,encoding,bytes.length,new JSONObject(),true));
            }else throw new CloudSourceFailure("invalid_source","文件清单只支持common-backup和wm-cache两个固定来源");
            source.put("available",true).put("recordCount",records.size());
        }catch(CloudSourceFailure e){source.put("available",false);return cloudError(result,e.code,e.getMessage(),e.caption);}
        catch(Exception e){source.put("available",false);return cloudError(result,"invalid_source_content","系统云控文件无法安全解析；未输出其中的原始内容");}
        try{
            if(full){
                String fingerprint=CloudCatalog.text(request,"id",null,64);
                if(fingerprint==null||!fingerprint.matches("[0-9a-f]{64}"))throw Messages.failureAs("IllegalArgumentException","diag_cloud_record_id_invalid","记录id须为规则库返回的64位指纹");
                Object pkg=CloudCatalog.identity(request,"packageName"),module=CloudCatalog.identity(request,"moduleName");
                String locator=CloudCatalog.text(request,"rowLocator",null,24);
                if(locator!=null&&!locator.matches("[0-9]{1,4}"))throw Messages.failureAs("IllegalArgumentException","diag_cloud_row_locator_invalid","rowLocator无效，请刷新规则库");
                for(CloudFileRecord record:records)if(fingerprint.equals(record.id)&&CloudCatalog.same(pkg,record.pkg)&&CloudCatalog.same(module,record.module)&&(locator==null||locator.equals(String.valueOf(record.index)))){
                    JSONObject entry=record.info().put("rawData",record.raw).put("rawEncoding",record.encoding)
                        .put("productData",record.encoding.equals("base64")?CloudCatalog.raw(record.raw,"非UTF-8文本；仅保留原始Base64"):CloudCatalog.parse(record.raw));
                    return result.put("entry",entry);
                }
                return cloudError(result,"record_changed","来源或记录已变化、删除或超出读取上限；请刷新规则库重新选择");
            }
            int offset=CloudCatalog.integer(request,"offset",0,0,100000),limit=CloudCatalog.integer(request,"limit",30,1,100);
            String query=CloudCatalog.text(request,"query","",160).toLowerCase(Locale.ROOT),status=CloudCatalog.text(request,"status",null,128);
            if(status!=null)return cloudError(result,"unsupported_filter","此文件来源没有status字段；请清除此筛选条件");
            Object pkg=request.has("packageName")?CloudCatalog.identity(request,"packageName"):null;int total=0;JSONArray entries=new JSONArray();
            for(CloudFileRecord record:records){
                if(pkg!=null&&!CloudCatalog.same(pkg,record.pkg))continue;
                if(!query.isEmpty()&&!String.valueOf(record.pkg==JSONObject.NULL?"":record.pkg).toLowerCase(Locale.ROOT).contains(query)&&!String.valueOf(record.module==JSONObject.NULL?"":record.module).toLowerCase(Locale.ROOT).contains(query))continue;
                if(total>=offset&&entries.length()<limit)entries.put(record.info());total++;
            }
            return result.put("entries",entries).put("offset",offset).put("limit",limit).put("total",total).put("hasMore",offset+(long)entries.length()<total);
        }catch(IllegalArgumentException e){return cloudError(result,"invalid_request",cloudShort(e.getMessage()==null?"请求参数不合法":e.getMessage(),240),Messages.reason(e));}
    }
    static List<CloudFileRecord> commonCloudRecords(byte[] bytes,String digest)throws Exception{
        String xml;
        try{xml=cloudUtf8(bytes);}catch(java.nio.charset.CharacterCodingException e){throw new CloudSourceFailure("invalid_source_encoding","公共云控XML不是有效UTF-8；未尝试转换或改写");}
        // Reject declarations before invoking the parser: even implementations which
        // ignore an unsupported external-entity feature cannot resolve a DTD.
        if(xml.contains("<!DOCTYPE")||xml.contains("<!ENTITY"))throw new CloudSourceFailure("unsafe_xml","公共云控XML含DTD或实体声明，已拒绝读取");
        org.xmlpull.v1.XmlPullParser parser=android.util.Xml.newPullParser();
        try{
            parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_DOCDECL,false);
            if(parser.getFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_DOCDECL))throw new Exception();
            for(String feature:new String[]{"http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities"}){
                try{parser.setFeature(feature,false);}catch(org.xmlpull.v1.XmlPullParserException unsupported){/* DTDs are forbidden independently above and below. */}
            }
        }catch(Exception e){throw new CloudSourceFailure("unsafe_xml_parser","当前XML解析器无法禁用DTD处理，已拒绝读取");}
        parser.setInput(new StringReader(xml));List<CloudFileRecord> records=new ArrayList<>();boolean root=false,control=false;int tokens=0;
        try{
            for(int event=parser.getEventType();event!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT;event=parser.nextToken()){
                if(++tokens>20000||parser.getDepth()>32)throw new CloudSourceFailure("source_structure_limit","公共云控XML超过32层或20000个结构节点");
                if(event==org.xmlpull.v1.XmlPullParser.DOCDECL||event==org.xmlpull.v1.XmlPullParser.ENTITY_REF)throw new CloudSourceFailure("unsafe_xml","公共云控XML含不受支持的声明或实体引用");
                if(event==org.xmlpull.v1.XmlPullParser.START_TAG){
                    if(parser.getAttributeCount()>32)throw new CloudSourceFailure("source_structure_limit","公共云控XML单节点属性超过32项");
                    if(parser.getDepth()==1){if(root||!parser.getName().equals("common-cloud-config"))throw new CloudSourceFailure("unsupported_schema","公共云控XML根节点不是common-cloud-config");root=true;}
                    if(parser.getDepth()==2)control=parser.getName().equals("cloud-control");
                    if(parser.getDepth()==3&&control&&parser.getName().equals("cloud-data-item")){
                        if(records.size()>=4096)throw new CloudSourceFailure("source_structure_limit","公共云控XML记录超过4096条");
                        String module=parser.getAttributeValue(null,"moduleName"),pkg=parser.getAttributeValue(null,"packageName"),raw=parser.getAttributeValue(null,"moduleString");
                        JSONObject metadata=new JSONObject();cloudFileMetadata(metadata,"moduleVersion",parser.getAttributeValue(null,"moduleVersion"));cloudFileMetadata(metadata,"moduleBeanPath",parser.getAttributeValue(null,"moduleBeanPath"));
                        records.add(new CloudFileRecord("common-backup",digest,records.size(),pkg==null?JSONObject.NULL:pkg,module==null?JSONObject.NULL:module,raw==null?JSONObject.NULL:raw,"utf-8",raw==null?0:raw.getBytes(StandardCharsets.UTF_8).length,metadata,raw!=null));
                    }
                }else if(event==org.xmlpull.v1.XmlPullParser.END_TAG&&parser.getDepth()==2)control=false;
            }
        }catch(org.xmlpull.v1.XmlPullParserException e){throw new CloudSourceFailure("invalid_source_content","公共云控XML语法无效；未输出其中的原始内容");}
        if(!root)throw new CloudSourceFailure("unsupported_schema","公共云控XML没有可识别的根节点");return records;
    }
    static void cloudFileMetadata(JSONObject metadata,String key,String value)throws Exception{
        if(value==null)return;int bytes=value.getBytes(StandardCharsets.UTF_8).length;
        metadata.put(key,new JSONObject().put("type","text").put("bytes",bytes).put("value",bytes>CLOUD_META_BYTES?JSONObject.NULL:value).put("truncated",bytes>CLOUD_META_BYTES));
    }
    static final class CloudFileRecord{
        final String sourceId,id,encoding,reason;final int index;final Object pkg,module,raw;final long rawBytes;final JSONObject metadata;
        CloudFileRecord(String sourceId,String digest,int index,Object pkg,Object module,Object raw,String encoding,long rawBytes,JSONObject metadata,boolean payloadPresent)throws Exception{
            this.sourceId=sourceId;this.index=index;this.encoding=encoding;this.rawBytes=rawBytes;this.metadata=metadata;
            boolean pkgLong=pkg instanceof String&&((String)pkg).getBytes(StandardCharsets.UTF_8).length>CLOUD_ID_BYTES,moduleLong=module instanceof String&&((String)module).getBytes(StandardCharsets.UTF_8).length>CLOUD_ID_BYTES;
            this.pkg=pkgLong?JSONObject.NULL:pkg;this.module=moduleLong?JSONObject.NULL:module;this.raw=rawBytes>CLOUD_RAW_BYTES?JSONObject.NULL:raw;
            reason=rawBytes>CLOUD_RAW_BYTES?"原文超过128 KiB；未加载为可编辑记录，也未截断其原文":pkgLong||moduleLong?"包名或模块名超过1024字节":module==JSONObject.NULL||String.valueOf(module).isEmpty()?"缺少moduleName":!payloadPresent?"缺少moduleString":null;
            id=reason==null?CloudCatalog.fingerprint(this.pkg,this.module,"file:"+sourceId+":"+digest+":"+index+":"+encoding,this.raw,false,"absent",JSONObject.NULL):null;
        }
        JSONObject info()throws Exception{
            JSONObject entry=new JSONObject().put("sourceId",sourceId).put("id",id==null?JSONObject.NULL:id).put("fingerprint",id==null?JSONObject.NULL:id)
                .put("packageName",pkg).put("moduleName",module).put("rawBytes",rawBytes).put("storageType",encoding.equals("base64")?"blob":"text")
                .put("metadata",metadata).put("rowLocator",String.valueOf(index)).put("retrievable",id!=null).put("readOnly",true).put("overrideSupported",false);
            if(reason!=null)entry.put("unavailableReason",reason).put("unavailableReasonCaption",Protocol.caption(Messages.legacy(reason)));return entry;
        }
    }
    static JSONObject cloudEnvelope(JSONObject source,boolean full)throws Exception{
        if(!source.has("sourceId"))source.put("id","settings-db").put("sourceId","settings-db");
        JSONObject result=new JSONObject().put("source",source).put("sourceId",source.get("sourceId")).put("readOnly",true).put("overrideSupported",false);
        if(!full)result.put("entries",new JSONArray()).put("offset",0).put("limit",30).put("total",0).put("hasMore",false);
        return result;
    }
    static JSONObject cloudError(JSONObject result,String code,String message)throws Exception{
        return cloudError(result,code,message,Messages.legacy(message));
    }
    static JSONObject cloudError(JSONObject result,String code,String message,Messages.Text caption)throws Exception{
        return result.put("error",new JSONObject().put("code",code).put("message",message).put("caption",Protocol.caption(caption)));
    }
    static Object cloudDiagnosticDetail(Throwable error){
        String raw=cloudShort(Protocol.message(error),240);
        Messages.Text known=Messages.legacy(raw);return known==null?raw:known;
    }
    static String cloudShort(String value,int limit){return value.length()<=limit?value:value.substring(0,limit)+"…";}
    static String cloudQuote(String name){return "\""+name.replace("\"","\"\"")+"\"";}

    /** SQL identifiers originate only in the fixed table's bounded schema, never requests. */
    static final class CloudSchema{
        final Map<String,String> names=new LinkedHashMap<>();
        final List<String> primary=new ArrayList<>();
        final List<String> metadata=new ArrayList<>();
        String rowid;
        String field(String name){return cloudQuote(names.get(name.toLowerCase(Locale.ROOT)));}
        boolean has(String name){return names.containsKey(name.toLowerCase(Locale.ROOT));}
        String select(){
            String p=field("packageName"),m=field("moduleName"),d=field("productData");
            StringBuilder sql=new StringBuilder("SELECT ");
            sql.append(bounded(p,CLOUD_ID_BYTES,true)).append(",typeof(").append(p).append("),")
                .append(bounded(m,CLOUD_ID_BYTES,true)).append(",typeof(").append(m).append("),")
                .append(bounded(d,CLOUD_RAW_BYTES,false)).append(",typeof(").append(d).append("),")
                .append(bytes(d)).append(',').append(bytes(p)).append(',').append(bytes(m));
            for(String name:metadata){String col=field(name);sql.append(',').append(bounded(col,CLOUD_META_BYTES,false)).append(",typeof(").append(col).append("),").append(bytes(col));}
            sql.append(',').append(rowid==null?"NULL":cloudQuote(rowid));
            return sql.append(" FROM \"").append(SYSTEM_CLOUD_TABLE).append("\"").toString();
        }
        String order(){
            if(rowid!=null)return cloudQuote(rowid);
            List<String> order=new ArrayList<>();for(String name:primary)order.add(cloudQuote(name));
            if(order.isEmpty()){order.add(field("packageName"));order.add(field("moduleName"));order.add(field("productData"));}
            return String.join(",",order);
        }
        static String bytes(String column){return "ifnull(length(CAST("+column+" AS BLOB)),0)";}
        static String bounded(String column,int max,boolean textOnly){return "CASE WHEN "+bytes(column)+"<="+max+(textOnly?" AND typeof("+column+") IN ('text','null')":"")+" THEN "+column+" ELSE NULL END";}
    }
    static CloudSchema cloudSchema(SQLiteDatabase db,JSONObject source)throws Exception{
        CloudSchema schema=new CloudSchema();JSONArray columns=new JSONArray();TreeMap<Integer,String> primary=new TreeMap<>();
        try(Cursor c=db.rawQuery("PRAGMA table_info(\"cloud_all_data\")",null)){
            while(c.moveToNext()){
                if(columns.length()>=128)throw Messages.failureAs("IllegalArgumentException","diag_cloud_column_limit","云控数据表列数超过128，已停止读取");
                String name=c.getString(1),type=c.getString(2);int pk=c.getInt(5);
                schema.names.put(name.toLowerCase(Locale.ROOT),name);
                columns.put(new JSONObject().put("name",cloudShort(name,128)).put("type",cloudShort(type==null?"":type,80)).put("primaryKey",pk));
                if(pk>0)primary.put(pk,name);
            }
        }
        source.put("columns",columns).put("maxRawBytes",CLOUD_RAW_BYTES).put("maxLimit",100).put("snapshot",false);
        schema.primary.addAll(primary.values());for(String name:CLOUD_META)if(schema.has(name))schema.metadata.add(name);
        if(!schema.has("packageName")||!schema.has("moduleName")||!schema.has("productData"))return null;
        // A declared column may shadow SQLite's implicit rowid aliases. WITHOUT
        // ROWID tables instead have a declared primary-key ordering.
        for(String candidate:new String[]{"rowid","_rowid_","oid"})if(!schema.has(candidate)){
            // Unquoted fixed aliases avoid SQLite's quoted-string fallback when
            // a WITHOUT ROWID table does not have any implicit row identifier.
            try(Cursor probe=db.rawQuery("SELECT "+candidate+" FROM \"cloud_all_data\" LIMIT 0",null)){
                probe.getColumnCount();schema.rowid=candidate;break;
            }catch(Exception ignored){}
        }
        source.put("available",true);return schema;
    }
    static JSONObject readSystemCloud(SQLiteDatabase db,JSONObject request,boolean full,JSONObject source)throws Exception{
        JSONObject result=cloudEnvelope(source,full);CloudSchema schema=cloudSchema(db,source);
        if(schema==null)return cloudError(result,"unsupported_schema","数据表缺少packageName、moduleName或productData列；可查看source.columns确认ROM结构");
        try{
            if(full)return cloudRecord(db,schema,request,result);
            int offset=CloudCatalog.integer(request,"offset",0,0,100000),limit=CloudCatalog.integer(request,"limit",30,1,100);
            String query=CloudCatalog.text(request,"query","",160),status=CloudCatalog.text(request,"status",null,128);
            List<String> terms=new ArrayList<>(),args=new ArrayList<>();
            if(!query.isEmpty()){
                terms.add("(instr(lower(COALESCE("+schema.field("packageName")+",'')),lower(?))>0 OR instr(lower(COALESCE("+schema.field("moduleName")+",'')),lower(?))>0)");args.add(query);args.add(query);
            }
            if(request.has("packageName"))cloudIdentityTerm(terms,args,schema.field("packageName"),CloudCatalog.identity(request,"packageName"));
            if(status!=null){
                if(!schema.has("status"))return cloudError(result,"unsupported_filter","当前ROM没有status列；请清除此筛选条件");
                terms.add("CAST("+schema.field("status")+" AS TEXT)=? COLLATE BINARY");args.add(status);
            }
            String where=terms.isEmpty()?"":" WHERE "+String.join(" AND ",terms);String[] bind=args.toArray(new String[0]);long total=0;
            try(Cursor c=db.rawQuery("SELECT count(*) FROM \"cloud_all_data\""+where,bind)){if(c.moveToFirst())total=c.getLong(0);}
            JSONArray entries=new JSONArray();args.add(String.valueOf(limit));args.add(String.valueOf(offset));
            try(Cursor c=db.rawQuery(schema.select()+where+" ORDER BY "+schema.order()+" LIMIT ? OFFSET ?",args.toArray(new String[0]))){while(c.moveToNext())entries.put(new CloudRecord(c,schema).info());}
            return result.put("entries",entries).put("offset",offset).put("limit",limit).put("total",total).put("hasMore",offset+(long)entries.length()<total);
        }catch(IllegalArgumentException e){return cloudError(result,"invalid_request",cloudShort(e.getMessage()==null?"请求参数不合法":e.getMessage(),240),Messages.reason(e));}
    }
    static void cloudIdentityTerm(List<String> terms,List<String> args,String field,Object value){
        if(value==JSONObject.NULL)terms.add(field+" IS NULL");else{terms.add(field+"=? COLLATE BINARY");args.add((String)value);}
    }
    static JSONObject cloudRecord(SQLiteDatabase db,CloudSchema schema,JSONObject request,JSONObject result)throws Exception{
        String id=CloudCatalog.text(request,"id",null,64);if(id==null||!id.matches("[0-9a-f]{64}"))throw Messages.failureAs("IllegalArgumentException","diag_cloud_record_id_invalid","记录id须为规则库返回的64位指纹");
        Object pkg=CloudCatalog.identity(request,"packageName"),module=CloudCatalog.identity(request,"moduleName");
        List<String> terms=new ArrayList<>(),args=new ArrayList<>();cloudIdentityTerm(terms,args,schema.field("packageName"),pkg);cloudIdentityTerm(terms,args,schema.field("moduleName"),module);
        String locator=CloudCatalog.text(request,"rowLocator",null,24);
        if(locator!=null){if(schema.rowid==null||!locator.matches("-?[0-9]{1,19}"))throw Messages.failureAs("IllegalArgumentException","diag_cloud_row_locator_invalid","rowLocator无效，请刷新规则库");terms.add(cloudQuote(schema.rowid)+"=?");args.add(locator);}
        int inspected=0;
        try(Cursor c=db.rawQuery(schema.select()+" WHERE "+String.join(" AND ",terms)+" ORDER BY "+schema.order()+" LIMIT 1001",args.toArray(new String[0]))){
            while(c.moveToNext()){
                if(++inspected>1000)return cloudError(result,"too_many_variants","同名记录超过1000条；请携带规则库返回的rowLocator精确读取");
                CloudRecord record=new CloudRecord(c,schema);
                if(id.equals(record.id)&&CloudCatalog.same(pkg,record.pkg)&&CloudCatalog.same(module,record.module)){
                    JSONObject entry=record.info().put("rawData",record.raw).put("rawEncoding",record.encoding)
                        .put("productData",record.dataType.equals("blob")?CloudCatalog.raw(record.raw,"非文本二进制数据；仅保留原始Base64"):CloudCatalog.parse(record.raw));
                    return result.put("entry",entry);
                }
            }
        }
        return cloudError(result,"record_changed","记录已变化、删除或超出读取上限；请刷新规则库后重新选择，不会读取其他同名规则");
    }
    static final class CloudRecord{
        final Object pkg,module,raw;final String dataType,encoding,id,reason,rowLocator;final long rawBytes;
        final JSONObject metadata=new JSONObject();final Object status;final String statusType;final boolean hasStatus;
        CloudRecord(Cursor c,CloudSchema schema)throws Exception{
            pkg=c.isNull(0)?JSONObject.NULL:c.getString(0);module=c.isNull(2)?JSONObject.NULL:c.getString(2);
            String pkgType=c.getString(1),moduleType=c.getString(3);dataType=c.getString(5);rawBytes=c.getLong(6);
            encoding=dataType.equals("blob")?"base64":"utf-8";raw=c.isNull(4)?JSONObject.NULL:dataType.equals("blob")?Base64.encodeToString(c.getBlob(4),Base64.NO_WRAP):c.getString(4);
            String problem=rawBytes>CLOUD_RAW_BYTES?"productData超过128 KiB；未加载、未截断其原文":
                c.getLong(7)>CLOUD_ID_BYTES||c.getLong(8)>CLOUD_ID_BYTES?"包名或模块名超过1024字节":
                !(pkgType.equals("text")||pkgType.equals("null"))||!(moduleType.equals("text")||moduleType.equals("null"))?"包名或模块名不是文本":null;
            int index=9;Object foundStatus=JSONObject.NULL;String foundType="absent";hasStatus=schema.has("status");
            for(String name:schema.metadata){
                String type=c.getString(index+1);long bytes=c.getLong(index+2);Object value=cloudCursorValue(c,index,type);
                JSONObject item=new JSONObject().put("type",type).put("bytes",bytes).put("value",value).put("truncated",bytes>CLOUD_META_BYTES);
                metadata.put(name,item);
                if(name.equals("status")){foundStatus=value;foundType=type;if(bytes>CLOUD_META_BYTES&&problem==null)problem="status字段过长，无法验证完整记录身份";}
                index+=3;
            }
            status=foundStatus;statusType=foundType;rowLocator=c.isNull(index)?null:c.getString(index);reason=problem;
            id=problem==null?CloudCatalog.fingerprint(pkg,module,dataType,raw,hasStatus,statusType,status):null;
        }
        JSONObject info()throws Exception{
            JSONObject info=new JSONObject().put("id",id==null?JSONObject.NULL:id).put("fingerprint",id==null?JSONObject.NULL:id)
                .put("sourceId","settings-db").put("packageName",pkg).put("moduleName",module).put("rawBytes",rawBytes).put("storageType",dataType)
                .put("metadata",metadata).put("retrievable",id!=null).put("readOnly",true).put("overrideSupported",false);
            if(hasStatus)info.put("status",status);if(rowLocator!=null)info.put("rowLocator",rowLocator);if(reason!=null)info.put("unavailableReason",reason).put("unavailableReasonCaption",Protocol.caption(Messages.legacy(reason)));
            return info;
        }
    }
    static Object cloudCursorValue(Cursor c,int index,String type){
        if(c.isNull(index))return JSONObject.NULL;
        if(type.equals("integer"))return c.getLong(index);
        if(type.equals("real")){double value=c.getDouble(index);return Double.isFinite(value)?(Object)value:String.valueOf(value);}
        if(type.equals("blob"))return Base64.encodeToString(c.getBlob(index),Base64.NO_WRAP);return c.getString(index);
    }

    /** Pure JSON/identity helpers, nested so the existing backend packaging includes them. */
    static final class CloudCatalog{
        static int integer(JSONObject r,String key,int fallback,int min,int max){
            if(!r.has(key))return fallback;Object value=r.opt(key);
            if(!(value instanceof Number))throw Messages.failureAs("IllegalArgumentException","diag_request_integer_required",key+"须为整数",key);
            double number=((Number)value).doubleValue();if(!Double.isFinite(number)||number<min||number>max||number!=(int)number)throw Messages.failureAs("IllegalArgumentException","diag_request_number_range",key+"超出允许范围"+min+"至"+max,key,min,max);return (int)number;
        }
        static String text(JSONObject r,String key,String fallback,int max){
            if(!r.has(key))return fallback;Object value=r.opt(key);
            if(!(value instanceof String)||((String)value).length()>max)throw Messages.failureAs("IllegalArgumentException","diag_request_text_limit",key+"须为不超过"+max+"字符的文本",key,max);return (String)value;
        }
        static Object identity(JSONObject r,String key){
            if(!r.has(key))throw Messages.failureAs("IllegalArgumentException","diag_request_identity_missing","缺少完整身份字段："+key,key);Object value=r.opt(key);
            if(value==JSONObject.NULL)return value;
            if(!(value instanceof String)||((String)value).getBytes(StandardCharsets.UTF_8).length>CLOUD_ID_BYTES)throw Messages.failureAs("IllegalArgumentException","diag_request_identity_text_limit",key+"须为不超过1024字节的文本或null",key);return value;
        }
        static boolean same(Object a,Object b){return a==JSONObject.NULL?b==JSONObject.NULL:a.equals(b);}
        static String fingerprint(Object pkg,Object module,String storage,Object raw,boolean hasStatus,String statusType,Object status)throws Exception{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(Object part:new Object[]{"system-cloud-v1",pkg,module,storage,raw,hasStatus?"status":"no-status",statusType,status}){
                if(part==null||part==JSONObject.NULL){digest.update((byte)0);continue;}
                byte[] bytes=String.valueOf(part).getBytes(StandardCharsets.UTF_8);digest.update((byte)1);
                digest.update((bytes.length+":").getBytes(StandardCharsets.US_ASCII));digest.update(bytes);
            }
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
        }
        static String type(Object value){return value==JSONObject.NULL?"null":value instanceof JSONObject?"object":value instanceof JSONArray?"array":value instanceof Boolean?"boolean":value instanceof Number?"number":"string";}
        static JSONObject raw(Object raw,String error)throws Exception{return new JSONObject().put("type","raw").put("value",raw).put("parseable",false).put("encodingDepth",0).put("error",error).put("errorCaption",Protocol.caption(Messages.legacy(error)));}
        static JSONObject parse(Object raw)throws Exception{
            if(raw==JSONObject.NULL)return new JSONObject().put("type","null").put("value",JSONObject.NULL).put("parseable",true).put("encodingDepth",0);
            String text=(String)raw;Object value;
            try{value=json(text);}catch(Exception e){return raw(raw,"未识别为有界、严格JSON；原文已完整保留");}
            int depth=0;String warning=null;
            while(value instanceof String&&depth<4){try{Object next=json((String)value);value=next;depth++;}catch(Exception e){break;}}
            if(value instanceof String&&depth==4)warning="已达到4层字符串解码上限；更深内容仍按文本保留";
            JSONObject result=new JSONObject().put("type",type(value)).put("value",value).put("parseable",true).put("encodingDepth",depth);
            if(warning!=null)result.put("warning",warning).put("warningCaption",Protocol.caption(Messages.legacy(warning)));return result;
        }
        // Shared strict validation is identical in the backend and visual editor.
        static Object json(String text)throws Exception{return Protocol.strictJson(text);}
    }
    static String run(String...command)throws Exception{
        java.lang.Process p=new ProcessBuilder(command).redirectErrorStream(true).start();
        ExecutorService reader=Executors.newSingleThreadExecutor();Future<String> text=reader.submit(()->Protocol.read(p.getInputStream()));
        try{if(!p.waitFor(25,TimeUnit.SECONDS)){p.destroyForcibly();throw Messages.failure("diag_command_timeout","命令超时："+command[0],command[0]);}String out=text.get(2,TimeUnit.SECONDS);if(p.exitValue()!=0)throw new Exception(out);return out;}finally{reader.shutdownNow();}
    }
}
