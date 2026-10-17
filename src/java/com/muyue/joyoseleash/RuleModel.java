package com.muyue.joyoseleash;

import org.json.*;
import java.math.BigDecimal;
import java.util.*;

/** Typed editing of rule values. Unopened siblings are kept intact. */
final class RuleModel {
    static JSONObject copy(JSONObject source)throws JSONException{return new JSONObject(source.toString());}
    static Object get(Object parent,Object key)throws JSONException{
        return parent instanceof JSONObject?((JSONObject)parent).get((String)key):((JSONArray)parent).get((Integer)key);
    }
    static void put(Object parent,Object key,Object value)throws JSONException{
        if(parent instanceof JSONObject)((JSONObject)parent).put((String)key,value);else ((JSONArray)parent).put((Integer)key,value);
    }
    static void remove(Object parent,Object key){if(parent instanceof JSONObject)((JSONObject)parent).remove((String)key);else ((JSONArray)parent).remove((Integer)key);}
    static List<Object> keys(Object container){
        ArrayList<Object> result=new ArrayList<>();
        if(container instanceof JSONObject){Iterator<String> it=((JSONObject)container).keys();while(it.hasNext())result.add(it.next());result.sort((a,b)->a.toString().compareToIgnoreCase(b.toString()));}
        else for(int i=0;i<((JSONArray)container).length();i++)result.add(i);
        return result;
    }
    /** A one-key object in an array is a real named field, not an index alias. */
    static final class Field {
        final Object parent,key,value;final String name;final boolean namedItem;
        Field(Object parent,Object key,Object value,boolean namedItem){this.parent=parent;this.key=key;this.value=value;this.name=key.toString();this.namedItem=namedItem;}
    }
    static Field field(Object parent,Object key)throws JSONException{
        Object value=get(parent,key);
        if(parent instanceof JSONArray&&value instanceof JSONObject&&((JSONObject)value).length()==1){String name=((JSONObject)value).keys().next();return new Field(value,name,((JSONObject)value).get(name),true);}
        return new Field(parent,key,value,false);
    }
    static void validName(String name)throws Exception{if(name.trim().isEmpty())throw new Exception("名称不能为空或只有空白");}
    static JSONObject named(String name,Object value)throws Exception{validName(name);return new JSONObject().put(name,value);}
    static boolean rename(JSONObject parent,String key,String name)throws Exception{
        if(name.equals(key))return false;validName(name);if(parent.has(name))throw new Exception("字段已存在");Object value=parent.get(key);parent.put(name,value);parent.remove(key);return true;
    }
    static int type(Object value){return value==JSONObject.NULL?5:value instanceof JSONObject?3:value instanceof JSONArray?4:value instanceof Boolean?2:value instanceof Number?1:0;}
    static Object defaultValue(int type)throws JSONException{switch(type){case 0:return "";case 1:return 0L;case 2:return false;case 3:return new JSONObject();case 4:return new JSONArray();default:return JSONObject.NULL;}}
    static Number number(String text)throws Exception{
        BigDecimal value=new BigDecimal(text.trim());
        if(!Double.isFinite(value.doubleValue()))throw new Exception("数字超出范围");
        if(value.stripTrailingZeros().scale()<=0){try{return value.longValueExact();}catch(ArithmeticException e){throw new Exception("整数超出范围");}}
        Protocol.strictJson(value.toString()); // Refuse precision loss on save/reload.
        return value;
    }
    /** Decode only encoded JSON containers; untouched strings retain their exact bytes. */
    static final class Encoded {
        final Object value;final int depth;
        Encoded(Object value,int depth){this.value=value;this.depth=depth;}
        String encode(){String text=value.toString();for(int i=1;i<depth;i++)text=JSONObject.quote(text);return text;}
    }
    static Encoded encoded(String text){
        Object value=text;for(int depth=1;depth<=4&&value instanceof String;depth++)try{
            value=Protocol.strictJson((String)value);
            if(value instanceof JSONObject||value instanceof JSONArray)return new Encoded(value,depth);
        }catch(Exception e){return null;}return null;
    }
    static JSONArray apps(JSONObject profile,boolean create)throws JSONException{
        JSONObject params=profile.getJSONObject("params"),game=params.optJSONObject("game_booster");
        if(game==null){if(!create)return new JSONArray();game=new JSONObject();params.put("game_booster",game);}
        JSONObject config=game.optJSONObject("booster_config");
        if(config==null){if(!create)return new JSONArray();config=new JSONObject();game.put("booster_config",config);}
        JSONArray list=config.optJSONArray("ovrride_config");
        if(list==null){list=new JSONArray();if(create)config.put("ovrride_config",list);}return list;
    }
    static JSONObject global(JSONObject profile,boolean create)throws JSONException{
        JSONObject params=profile.getJSONObject("params"),game=params.optJSONObject("game_booster");
        if(game==null){if(!create)return new JSONObject();game=new JSONObject();params.put("game_booster",game);}
        JSONObject table=game.optJSONObject("dynamic_fps_global");
        if(table==null){table=new JSONObject();if(create)game.put("dynamic_fps_global",table);}return table;
    }
    static JSONObject addApp(JSONObject profile,String name,int fps)throws Exception{
        name=name.trim();if(!name.matches("[A-Za-z0-9_.#-]{1,300}"))throw new Exception("包名或分组名无效");
        if(fps<0||fps>1000)throw new Exception("帧率须为0至1000");
        JSONArray base=apps(profile,true);JSONObject config=profile.getJSONObject("params").getJSONObject("game_booster").getJSONObject("booster_config"),result=null;
        for(Object key:keys(config)){
            String k=key.toString();if(!k.equals("ovrride_config")&&!k.startsWith("ovrride_config#"))continue;
            JSONArray list=config.getJSONArray(k);JSONObject found=null;
            for(int i=0;i<list.length();i++)if(name.equals(list.getJSONObject(i).optString("game_name"))){found=list.getJSONObject(i);break;}
            if(found==null){found=new JSONObject().put("game_name",name).put("scene_ovrride",new JSONArray());list.put(found);}
            found.put("dynamic_fps","10:"+fps).put("dynamic_fps_M","10:"+fps);
            if(!found.has("scene_ovrride"))found.put("scene_ovrride",new JSONArray());
            if(k.equals("ovrride_config"))result=found;
        }
        return result;
    }
    static List<String[]> tableRows(String text)throws Exception{
        ArrayList<String[]> rows=new ArrayList<>();if(text.isEmpty())return rows;
        Protocol.validateTable(text);
        for(String entry:text.split(","))rows.add(entry.split(":"));return rows;
    }
    static String table(List<String[]> input)throws Exception{
        if(input.isEmpty())return null;
        List<String[]> rows=new ArrayList<>(input);for(String[] row:rows){Double.parseDouble(row[0].trim());Integer.parseInt(row[1].trim());}
        rows.sort(Comparator.comparingDouble(a->Double.parseDouble(a[0].trim())));
        StringBuilder result=new StringBuilder();for(String[] row:rows){if(result.length()>0)result.append(',');result.append(new BigDecimal(row[0].trim()).stripTrailingZeros().toPlainString()).append(':').append(Integer.parseInt(row[1].trim()));}
        Protocol.validateTable(result.toString());return result.toString();
    }
    static void move(JSONArray array,int from,int to)throws JSONException{
        if(to<0||to>=array.length())return;Object a=array.get(from),b=array.get(to);array.put(from,b);array.put(to,a);
    }
    static String summary(Object value){
        if(value==JSONObject.NULL)return "空值";
        if(value instanceof JSONObject)return ((JSONObject)value).length()+" 项 ›";
        if(value instanceof JSONArray)return ((JSONArray)value).length()+" 项 ›";
        if(value instanceof Boolean)return (Boolean)value?"开":"关";
        String text=String.valueOf(value).replace('\n',' ');return text.length()>64?text.substring(0,64)+"…":text;
    }
}
