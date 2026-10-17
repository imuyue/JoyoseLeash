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
        if(!module.matches("[A-Za-z0-9_.-]{1,80}"))throw Messages.failure("diag_rule_library_name_invalid","规则库名称不合法");
        Object version=profile.get("version");
        if(!(version instanceof Number)||((Number)version).doubleValue()!=((Number)version).longValue()
            ||profile.getLong("version")<1||profile.getLong("version")>2147483647L)throw Messages.failure("diag_profile_version_invalid","版本必须为1至2147483647的整数");
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
                    if(name.isEmpty()||!names.add(name))throw Messages.failure("diag_rule_name_duplicate_or_empty","应用/分组规则名称重复或为空："+name,name);
                    for(String key:new String[]{"dynamic_fps","dynamic_fps_M"})if(item.has(key))validateTable(item.getString(key));
                    // Joyose uses getJSONArray rather than optJSONArray for every override.
                    // Complete minimal rules created by earlier UI builds before parsing.
                    if(!item.has("scene_ovrride"))item.put("scene_ovrride",new JSONArray());
                    item.getJSONArray("scene_ovrride");
                }
            }
        }
        if(params.toString().getBytes(StandardCharsets.UTF_8).length>MAX_BYTES-65536)throw Messages.failure("diag_rule_content_too_large","规则内容过大");
    }
    public static void validateTable(String text)throws Exception{
        double last=-Double.MAX_VALUE;
        for(String item:text.split(",",-1)){
            String[] pair=item.split(":",-1);if(pair.length!=2)throw Messages.failure("diag_temperature_table_format","温度表格式为10:0,38:90,42.8:60");
            double temp=Double.parseDouble(pair[0]);int fps=Integer.parseInt(pair[1]);
            if(!Double.isFinite(temp)||temp< -100||temp>200||temp<=last||fps<0||fps>1000)throw Messages.failure("diag_temperature_table_range","温度须递增，帧率须为0至1000");last=temp;
        }
    }
    public static boolean networkBlocked(JSONObject state){
        return state.optBoolean("networkBlocked",true)&&state.optLong("fetchUntil",0)<=System.currentTimeMillis();
    }
    public static String message(Throwable t){while(t.getCause()!=null)t=t.getCause();return (t instanceof Messages.Failure?((Messages.Failure)t).legacyType:t.getClass().getSimpleName())+": "+String.valueOf(t.getMessage());}
    /** Display metadata only. Raw values and wire keys are never translated. */
    public static JSONObject caption(Messages.Text text){
        if(text==null)return null;
        try{
            JSONArray args=new JSONArray();for(Object argument:text.args)args.put(captionArgument(argument));
            return new JSONObject().put("key",text.key).put("fallback",text.fallback).put("args",args);
        }catch(JSONException e){throw new IllegalArgumentException("Invalid message metadata",e);}
    }
    private static Object captionArgument(Object value){
        if(value==null)return JSONObject.NULL;
        if(value instanceof Messages.Text)return caption((Messages.Text)value);
        if(value instanceof String||value instanceof Number||value instanceof Boolean)return value;
        return String.valueOf(value);
    }
    public static JSONObject messageCaption(Throwable error){return caption(Messages.reason(error));}
    /** Strict, bounded and numerically lossless JSON for visual drafts. Android's
     * JSONTokener alone accepts comments, duplicate keys and lossy numbers. */
    static Object strictJson(String text)throws Exception{
        if(text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw Messages.failureAs("IllegalArgumentException","diag_json_too_large","JSON过大");
        new JsonSyntax(text).check();return new JSONTokener(text).nextValue();
    }
    static final class JsonSyntax{
        final String text;int pos,nodes;
        JsonSyntax(String text){this.text=text;}
        void check(){space();value(0);space();if(pos!=text.length())bad();}
        void bad(){throw Messages.failureAs("IllegalArgumentException","diag_json_not_strict_or_too_complex","非严格JSON或结构超过读取上限");}
        void space(){while(pos<text.length()&&" \t\r\n".indexOf(text.charAt(pos))>=0)pos++;}
        boolean take(char c){if(pos<text.length()&&text.charAt(pos)==c){pos++;return true;}return false;}
        void value(int depth){
            if(depth>48||++nodes>20000||pos>=text.length())bad();char c=text.charAt(pos);
            if(c=='"'){string();return;}
            if(c=='{'||c=='['){
                pos++;boolean object=c=='{';Set<String> keys=object?new HashSet<>():null;char close=object?'}':']';space();if(take(close))return;
                do{
                    space();if(object){
                        if(pos>=text.length()||text.charAt(pos)!='"')bad();int keyStart=pos;string();
                        try{if(!keys.add((String)new JSONTokener(text.substring(keyStart,pos)).nextValue()))bad();}catch(Exception e){bad();}
                        space();if(!take(':'))bad();space();
                    }
                    value(depth+1);space();if(take(close))return;if(!take(','))bad();
                }while(true);
            }
            for(String literal:new String[]{"true","false","null"})if(text.startsWith(literal,pos)){pos+=literal.length();return;}
            int start=pos;take('-');if(take('0')){}else{if(pos>=text.length()||text.charAt(pos)<'1'||text.charAt(pos)>'9')bad();digits();}
            if(take('.')){int fraction=pos;digits();if(pos==fraction)bad();}
            if(take('e')||take('E')){if(!take('+'))take('-');int exponent=pos;digits();if(pos==exponent)bad();}
            if(pos==start)bad();
            try{
                String literal=text.substring(start,pos);if(literal.length()>128)bad();
                Object number=new JSONTokener(literal).nextValue();if(!(number instanceof Number))bad();
                java.math.BigDecimal exact=new java.math.BigDecimal(literal),encoded=new java.math.BigDecimal(JSONObject.numberToString((Number)number));
                if(exact.compareTo(encoded)!=0)bad();
            }catch(Exception e){bad();}
        }
        void digits(){while(pos<text.length()&&text.charAt(pos)>='0'&&text.charAt(pos)<='9')pos++;}
        void string(){
            if(!take('"'))bad();while(pos<text.length()){
                char c=text.charAt(pos++);if(c=='"')return;if(c<0x20)bad();
                if(c=='\\'){if(pos>=text.length())bad();char escape=text.charAt(pos++);
                    if(escape=='u'){for(int i=0;i<4;i++){if(pos>=text.length()||"0123456789abcdefABCDEF".indexOf(text.charAt(pos++))<0)bad();}}
                    else if("\"\\/bfnrt".indexOf(escape)<0)bad();
                }
            }bad();
        }
    }
    private Protocol(){}
}
