package com.muyue.joyoseleash;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.*;

/** Transport shared by native screens. Requests never become shell arguments. */
final class RootClient implements AutoCloseable {
    interface Result {void complete(Object value,Exception error);}
    private final Context context;
    private final UiText strings;
    static final class RequestFailure extends IOException {
        final JSONObject caption;
        RequestFailure(String message,JSONObject caption){super(message);this.caption=caption;}
    }
    volatile long requests;
    private final ExecutorService queue=Executors.newSingleThreadExecutor();
    RootClient(Context context){this.context=context.getApplicationContext();strings=new UiText(this.context);}
    private RequestFailure localFailure(String key){String message=strings.s(key);return new RequestFailure(message,Protocol.caption(Messages.text(key,message)));}
    void request(String action,JSONObject payload,Result callback){
        final String input=payload.toString();
        queue.execute(()->{
            Object result=null;Exception error=null;File request=null;
            try{
                if(!action.matches("[a-z-]{1,30}"))throw localFailure("error_invalid_action");
                if(input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>Protocol.MAX_BYTES)throw localFailure("error_rules_too_large");
                request=File.createTempFile("native-request-",".json",context.getCacheDir());Protocol.atomic(request,input);
                String command="if [ ! -r "+Protocol.MODULE+"/backend.jar ]; then echo '{\"ok\":false,\"error\":\"module_missing\",\"errorCaption\":{\"key\":\"error_install_module\",\"fallback\":\"Install the companion module first\",\"args\":[]}}'; exit 1; fi; CLASSPATH="+Protocol.MODULE+"/backend.jar /system/bin/app_process /system/bin com.muyue.joyoseleash.Backend "+action+" < '"+request.getPath()+"'";
                requests++;
                java.lang.Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
                ExecutorService reader=Executors.newSingleThreadExecutor();
                try{
                    Future<String> output=reader.submit(()->Protocol.read(process.getInputStream()));
                    if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();throw localFailure("error_root_timeout");}
                    String text=output.get(2,TimeUnit.SECONDS);JSONObject response=null;
                    String[] lines=text.trim().split("\n");
                    for(int i=lines.length-1;i>=0;i--)try{JSONObject candidate=new JSONObject(lines[i]);if(candidate.has("ok")){response=candidate;break;}}catch(Exception ignored){}
                    if(response==null){if(text.isEmpty())throw localFailure("error_backend_no_response");throw new IOException(text.substring(0,Math.min(300,text.length())));}
                    if(!response.optBoolean("ok"))throw new RequestFailure(response.optString("error",strings.s("error_operation_failed")),response.optJSONObject("errorCaption"));
                    result=response.get("data");
                }finally{reader.shutdownNow();}
            }catch(Exception e){error=e;}
            finally{if(request!=null)request.delete();}
            callback.complete(result,error);
        });
    }
    @Override public void close(){queue.shutdownNow();}
}
