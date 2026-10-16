package com.muyue.joyoseleash;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;

/** Read-only configuration IPC; only root can request state-change notifications. */
public final class StateProvider extends ContentProvider {
    private int settingsUid=-1;
    @Override public boolean onCreate(){
        try{settingsUid=getContext().getPackageManager().getApplicationInfo("com.android.htmlviewer",0).uid;}catch(Exception ignored){}
        return true;
    }
    private void check(){
        int uid=Binder.getCallingUid();if(uid!=0&&uid!=1000&&uid!=settingsUid&&uid!=android.os.Process.myUid())throw new SecurityException("Not an authorized cloud process");
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        check();
        if(mode.equals("r")&&uri.getPath().equals("/state"))return ParcelFileDescriptor.open(new File(getContext().getFilesDir(),"state.json"),ParcelFileDescriptor.MODE_READ_ONLY);
        java.util.List<String> parts=uri.getPathSegments();
        if(mode.equals("w")&&parts.size()==3&&parts.get(0).equals("stage")){
            String role=parts.get(1),nonce=parts.get(2);int uid=Binder.getCallingUid();
            if(!nonce.matches("[0-9]{1,18}")||!(role.equals("joyose")&&uid==1000||role.equals("settings")&&uid==settingsUid))throw new SecurityException("Invalid download writer");
            JSONObject state=Protocol.object(new File(getContext().getFilesDir(),"state.json"));
            if(!nonce.equals(String.valueOf(state.optLong("fetchNonce")))||state.optLong("fetchUntil")<=System.currentTimeMillis())throw new SecurityException("Fetch lease expired");
            return ParcelFileDescriptor.open(new File(getContext().getFilesDir(),"stage-"+role+"-"+nonce+".tmp"),ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE);
        }
        throw new FileNotFoundException("Unsupported endpoint");
    }
    @Override public synchronized Bundle call(String method,String arg,Bundle extras){
        check();Bundle out=new Bundle();
        try {
            if(method.equals("changed")){
                if(Binder.getCallingUid()!=0)throw new SecurityException("Root required");
                getContext().getContentResolver().notifyChange(Uri.parse("content://"+Protocol.AUTH+"/state"),null);out.putBoolean("ok",true);return out;
            }
            if(!method.equals("report"))throw new IllegalArgumentException("Unknown method");
            JSONObject report=new JSONObject(extras.getString("json","{}"));String process=report.getString("process");
            String role=process.equals("com.xiaomi.joyose")?"joyose":process.startsWith("com.android.htmlviewer")?"settings":null;
            int uid=Binder.getCallingUid();if(role==null||(role.equals("joyose")&&uid!=1000)||(role.equals("settings")&&uid!=settingsUid))throw new SecurityException("Role mismatch");
            report.put("time",System.currentTimeMillis());String kind=report.optString("kind","hook");
            if(!kind.equals("fetch")&&!kind.equals("hook"))throw new IllegalArgumentException("Invalid report");
            if(kind.equals("fetch")){
                File stateFile=new File(getContext().getFilesDir(),"state.json");JSONObject state=Protocol.object(stateFile);
                long nonce=state.optLong("fetchNonce");
                if(report.optLong("nonce")!=nonce)throw new IllegalArgumentException("Stale fetch result");
                if(report.optBoolean("success")){
                    File temp=new File(getContext().getFilesDir(),"stage-"+role+"-"+nonce+".tmp");
                    try{
                        if(!temp.isFile()||temp.length()>Protocol.MAX_BYTES)throw new IOException("Downloaded rules missing or oversized");
                        JSONObject downloaded=new JSONObject(Protocol.read(temp));
                        if(downloaded.optLong("nonce")!=nonce||!temp.renameTo(new File(getContext().getFilesDir(),"download-"+role+"-"+nonce+".json")))throw new IOException("Unable to publish downloaded rules");
                    }catch(Exception e){report.put("success",false).put("message",Protocol.message(e));}
                }
                // The root watcher closes the lease under Backend's file lock.
                // The provider must never replace state.json concurrently with edits.
            }
            Protocol.atomic(new File(getContext().getFilesDir(),kind+"-"+role+".json"),report.toString());
            out.putBoolean("ok",true);
        }catch(Exception e){out.putString("error",Protocol.message(e));}
        return out;
    }
    @Override public String getType(Uri u){return "application/json";}
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
