package com.muyue.joyoseleash;

import android.content.Context;
import android.content.res.Resources;
import org.json.*;
import java.util.concurrent.ConcurrentHashMap;

/** APK-only accessor for standard Android string/plural resources. Never rewrites rule data. */
final class UiText {
    private final Context context;
    private final ConcurrentHashMap<String,Integer> strings=new ConcurrentHashMap<>(),plurals=new ConcurrentHashMap<>();
    UiText(Context context){this.context=context;}
    private int id(String key,String type,ConcurrentHashMap<String,Integer> cache){
        Integer known=cache.get(key);if(known!=null)return known;
        int value=context.getResources().getIdentifier(key,type,Protocol.PKG);cache.put(key,value);return value;
    }
    String s(String key,Object... args){
        int id=id(key,"string",strings);if(id==0)throw new Resources.NotFoundException("Unknown string resource: "+key);
        return args.length==0?context.getString(id):context.getString(id,args);
    }
    String q(String key,int quantity,Object... args){
        int id=id(key,"plurals",plurals);if(id==0)throw new Resources.NotFoundException("Unknown plural resource: "+key);
        return context.getResources().getQuantityString(id,quantity,args);
    }
    Messages.Failure failure(String key,Object... args){return Messages.failure(key,s(key,args),args);}
    String message(Messages.Text text){return text==null?"":message(text,0);}
    private String message(Messages.Text text,int depth){
        if(depth>12)return text.fallback;
        Object[] args=new Object[text.args.length];for(int i=0;i<args.length;i++)args[i]=text.args[i] instanceof Messages.Text?message((Messages.Text)text.args[i],depth+1):text.args[i];
        return render(text.key,text.fallback,args);
    }
    String message(JSONObject caption){return caption==null?"":message(caption,0);}
    private String message(JSONObject caption,int depth){
        String fallback=caption.optString("fallback","");if(depth>12)return fallback;
        JSONArray values=caption.optJSONArray("args");Object[] args=new Object[values==null?0:values.length()];
        for(int i=0;i<args.length;i++){Object value=values.opt(i);args[i]=value instanceof JSONObject&&((JSONObject)value).has("key")?message((JSONObject)value,depth+1):value==JSONObject.NULL?null:value;}
        return render(caption.optString("key"),fallback,args);
    }
    private String render(String key,String fallback,Object[] args){
        if(!key.matches("[a-z][a-z0-9_]*"))return fallback;
        try{if(id(key,"string",strings)!=0)return s(key,args);if(id(key,"plurals",plurals)!=0&&args.length>0&&args[0] instanceof Number)return q(key,((Number)args[0]).intValue(),args);return fallback;}catch(java.util.IllegalFormatException e){return fallback;}
    }
    String error(Throwable error){
        if(error instanceof RootClient.RequestFailure&&((RootClient.RequestFailure)error).caption!=null)return message(((RootClient.RequestFailure)error).caption);
        JSONObject caption=Protocol.messageCaption(error);if(caption!=null)return message(caption);
        String text=error.getMessage();return text==null?Protocol.message(error):text;
    }
    String summary(Object value){
        if(value==JSONObject.NULL)return s("value_null");
        if(value instanceof JSONObject){int count=((JSONObject)value).length();return q("value_items",count,count);}
        if(value instanceof JSONArray){int count=((JSONArray)value).length();return q("value_items",count,count);}
        if(value instanceof Boolean)return s((Boolean)value?"value_on":"value_off");
        return RuleModel.summary(value);
    }
}
