package com.muyue.joyoseleash;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Protocol {
    public static final String PKG="com.muyue.joyoseleash", AUTH=PKG+".state";
    public static final String MODULE="/data/adb/modules/cloudruleguard";
    public static final String DATA="/data/adb/cloudruleguard";
    public static final String FILES="/data/user/0/"+PKG+"/files";
    public static final int MAX_BYTES=4*1024*1024;
    public static final String[] HOSTS={
        "mcc.inf.miui.com","mcc.intl.inf.miui.com","mcc.india.inf.miui.com",
        "mcc.russia.inf.miui.com","staging.mcc.inf.miui.com",
        "jupiter.sys.miui.com","jupiter.intl.sys.miui.com",
        "jupiter.rus.sys.miui.com","preview.jupiter.sys.miui.com",
        "api.sec.miui.com","api.sec.intl.miui.com",
        "resolver.msg.xiaomi.net"
    };
    public static String read(InputStream in) throws IOException {
        try(InputStream s=in; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192]; int n;
            while((n=s.read(b))!=-1){ if(out.size()+n>MAX_BYTES)throw new IOException("内容超过4 MiB");out.write(b,0,n); }
            return out.toString("UTF-8");
        }
    }
    public static String read(File f) throws IOException {return read(new FileInputStream(f));}
    public static JSONObject object(File f){try{return new JSONObject(read(f));}catch(Exception e){return new JSONObject();}}
    public static void atomic(File f,String value) throws IOException {
        File parent=f.getParentFile(); if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法创建目录");
        File temp=new File(parent,f.getName()+".new");
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(value.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        if(!temp.renameTo(f)){temp.delete();throw new IOException("无法原子保存 "+f.getName());}
    }
    public static void validateProfile(String module,JSONObject profile)throws Exception{
        if(!module.matches("[A-Za-z0-9_.-]{1,80}"))throw new Exception("规则库名称不合法");
        Object version=profile.get("version");
        if(!(version instanceof Number)||((Number)version).doubleValue()!=((Number)version).longValue()
            ||profile.getLong("version")<1||profile.getLong("version")>2147483647L)throw new Exception("版本必须为1至2147483647的整数");
        JSONObject params=profile.getJSONObject("params");
        if(module.equals("booster_config")) {
            params.getJSONObject("header");
            JSONObject game=params.getJSONObject("game_booster");
            JSONObject global=game.optJSONObject("dynamic_fps_global");
            if(global!=null)for(String key:new String[]{"dynamic_fps","dynamic_fps_M"})if(global.has(key))validateTable(global.getString(key));
            JSONObject config=game.optJSONObject("booster_config");
            if(config!=null)for(Iterator<String> keys=config.keys();keys.hasNext();){String listKey=keys.next();
                if(!listKey.equals("ovrride_config")&&!listKey.startsWith("ovrride_config#"))continue;
                JSONArray list=config.getJSONArray(listKey);Set<String> names=new HashSet<>();for(int i=0;i<list.length();i++){
                    JSONObject item=list.getJSONObject(i);String name=item.getString("game_name");
                    if(name.isEmpty()||!names.add(name))throw new Exception("应用/分组规则名称重复或为空："+name);
                    for(String key:new String[]{"dynamic_fps","dynamic_fps_M"})if(item.has(key))validateTable(item.getString(key));
                    // Joyose uses getJSONArray rather than optJSONArray for every override.
                    // Complete minimal rules created by earlier UI builds before parsing.
                    if(!item.has("scene_ovrride"))item.put("scene_ovrride",new JSONArray());
                    item.getJSONArray("scene_ovrride");
                }
            }
        }
        if(params.toString().getBytes(StandardCharsets.UTF_8).length>MAX_BYTES-65536)throw new Exception("规则内容过大");
    }
    public static void validateTable(String text)throws Exception{
        double last=-Double.MAX_VALUE;
        for(String item:text.split(",",-1)){
            String[] pair=item.split(":",-1);if(pair.length!=2)throw new Exception("温度表格式为10:0,38:90,42.8:60");
            double temp=Double.parseDouble(pair[0]);int fps=Integer.parseInt(pair[1]);
            if(!Double.isFinite(temp)||temp< -100||temp>200||temp<=last||fps<0||fps>1000)throw new Exception("温度须递增，帧率须为0至1000");last=temp;
        }
    }
    public static boolean networkBlocked(JSONObject state){
        return state.optBoolean("networkBlocked",true)&&state.optLong("fetchUntil",0)<=System.currentTimeMillis();
    }
    public static String message(Throwable t){while(t.getCause()!=null)t=t.getCause();return t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage());}
    private Protocol(){}
}
