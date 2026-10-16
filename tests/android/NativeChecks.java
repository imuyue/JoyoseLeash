package com.muyue.joyoseleash.qa;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Exercises real native controls without depending on another app being idle. */
public final class NativeChecks extends Instrumentation {
    Activity activity;
    JSONObject report=new JSONObject();
    List<Double> clickTimes=new ArrayList<>();
    Object originalProfiles;
    boolean originalDirty;
    String originalBase;
    long originalGeneration;
    Object modern;
    boolean originalLockEdited,originalLockDraft;
    File output;
    boolean previewOnly;
    @Override public void onCreate(Bundle args){super.onCreate(args);previewOnly=args!=null&&"true".equalsIgnoreCase(String.valueOf(args.get("previewOnly")));start();}
    @Override public void onStart(){
        int result=Activity.RESULT_CANCELED;
        try{
            output=new File(getTargetContext().getFilesDir(),"native-qa");output.mkdirs();
            ActivityMonitor monitor=addMonitor("com.muyue.joyoseleash.ManagerActivity",null,false);
            try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand("am start -n com.muyue.joyoseleash/.ManagerActivity"))){byte[] buffer=new byte[512];while(in.read(buffer)!=-1){}}
            activity=monitor.waitForActivityWithTimeout(12000);removeMonitor(monitor);check(activity!=null,"Native activity did not open");
            waitReady();modern=get(activity,"modern");
            originalProfiles=new JSONObject(((JSONObject)get(activity,"profiles")).toString());originalDirty=(Boolean)get(activity,"dirty");originalBase=(String)get(activity,"baseProfiles");originalGeneration=(Long)get(activity,"editGeneration");
            originalLockEdited=(Boolean)get(modern,"lockEdited");originalLockDraft=(Boolean)get(modern,"lockDraft");
            if(previewOnly){preview();report.put("previewOnly",true).put("passed",true);result=Activity.RESULT_OK;return;}
            report.put("restoredDraftVersion",((JSONObject)originalProfiles).getJSONObject("booster_config").getLong("version"));
            select(0);onMain(()->{set(activity,"query","");invoke(activity,"renderHome");return null;});sleep(200);
            assertNative(activity.getWindow().getDecorView());shot("games");
            long before=requests();select(1);check(has("锁定当前规则"),"Global categories missing");shot("globals");
            click("锁定当前规则");check(requests()==before,"Local toggle started Root");click("锁定当前规则");
            select(2);waitReady();click("历史备份");waitReady();check(hasRecord(),"No historical rules to replace");shot("history");clickRecord();check(has("替换并应用"),"Replace action missing");shot("replace-preview");back();
            click("系统缓存");waitReady();shot("system-rules");click("ROM预置");waitReady();click("云端下载");waitReady();
            select(3);check(has("服务器列表"),"Network category missing");shot("settings");checkAbout();
            long warm=requests();for(int n=0;n<3;n++){select(0);select(1);select(2);select(3);}waitReady();check(requests()==warm,"Cached navigation started Root");report.put("cachedNavigationRootCalls",requests()-warm);
            select(0);onMain(()->{set(activity,"query","");invoke(activity,"renderHome");return null;});sleep(150);
            if(has("地铁跑酷"))click("地铁跑酷");else{click("+ 添加游戏");click("地铁跑酷");}
            click("固定帧率");click("120");check(onMain(()->findClick(activity.getWindow().getDecorView(),"120").isSelected()),"Selected preset missing");
            onMain(()->{EditText edit=findEditor(activity.getWindow().getDecorView());check(edit!=null,"FPS input missing");edit.setText("1001");return null;});click("保存并应用");check(requests()==warm,"Invalid FPS reached backend");check(has("帧率须为0至1000"),"Invalid FPS feedback missing");click("144");shot("game-fps");check(has("保存并应用"),"Direct apply missing");check(requests()==warm,"Frame preset started Root");report.put("gameFpsControls",true).put("invalidFpsBlocked",true).put("selectedPresetFeedback",true);back();
            select(1);click("全局帧率");click("温度分档");check(has("+ 添加温度档"),"Inline threshold rows missing");assertNative(activity.getWindow().getDecorView());click("+ 添加温度档");click("×");check(requests()==warm,"Inline threshold editing started Root");report.put("inlineThresholdEditing",true);shot("thresholds");
            // No model mutations are committed to the phone by this UI run.
            onMain(()->{set(activity,"profiles",new JSONObject(originalProfiles.toString()));set(activity,"dirty",originalDirty);set(activity,"baseProfiles",originalBase);set(activity,"editGeneration",originalGeneration);set(modern,"lockEdited",originalLockEdited);set(modern,"lockDraft",originalLockDraft);set(activity,"tab",0);set(activity,"query","");invoke(activity,"renderHome");return null;});
            long idle=requests();sleep(12000);check(requests()==idle,"Idle screen polled Root");report.put("idleSeconds",12).put("idleRootCalls",requests()-idle);
            modelChecks();structuredChecks();select(0);shot("final-home");
            double max=0;for(double v:clickTimes)max=Math.max(max,v);report.put("nativeClickCount",clickTimes.size()).put("maxClickHandlerMs",max).put("passed",true);
            result=Activity.RESULT_OK;
        }catch(Throwable e){try{report.put("passed",false).put("error",e.toString());}catch(Exception ignored){}e.printStackTrace();}
        finally{
            try{if(activity!=null&&originalProfiles!=null)onMain(()->{set(activity,"profiles",new JSONObject(originalProfiles.toString()));set(activity,"dirty",originalDirty);set(activity,"baseProfiles",originalBase);set(activity,"editGeneration",originalGeneration);set(modern,"lockEdited",originalLockEdited);set(modern,"lockDraft",originalLockDraft);set(activity,"tab",0);set(activity,"query","");invoke(activity,"renderHome");invoke(activity,"writeDraft");return null;});}catch(Throwable ignored){}
            try{if(output!=null)try(FileOutputStream out=new FileOutputStream(new File(output,previewOnly?"preview-report.json":"report.json"))){out.write(report.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}
            Bundle out=new Bundle();out.putString("report",report.toString());finish(result,out);
        }
    }
    TextView record(View view){if(view instanceof TextView&&!(view instanceof EditText)&&((TextView)view).getText().toString().matches("[0-9]{8,}"))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView v=record(((ViewGroup)view).getChildAt(i));if(v!=null)return v;}return null;}
    boolean hasRecord()throws Exception{return onMain(()->record(activity.getWindow().getDecorView())!=null);}
    void clickRecord()throws Exception{onMain(()->{View v=record(activity.getWindow().getDecorView());while(v!=null&&!v.isClickable()&&v.getParent() instanceof View)v=(View)v.getParent();check(v!=null&&v.performClick(),"Record preview not clickable");return null;});sleep(120);}
    EditText findEditor(View view){if(view instanceof EditText&&"目标帧率".contentEquals(view.getContentDescription()))return (EditText)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText v=findEditor(((ViewGroup)view).getChildAt(i));if(v!=null)return v;}return null;}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    void preview()throws Exception{
        select(0);onMain(()->{set(activity,"query","");invoke(activity,"renderHome");return null;});sleep(150);appShot("games");
        if(has("地铁跑酷"))click("地铁跑酷");else{click("+ 添加游戏");click("地铁跑酷");}click("固定帧率");click("144");appShot("game-fps");back();
        select(2);waitReady();click("历史备份");waitReady();appShot("history");clickRecord();appShot("replace-preview");back();
        select(1);click("全局帧率");click("温度分档");appShot("thresholds");
        select(3);checkAbout();
    }
    void checkAbout()throws Exception{
        long before=requests();click("关于");assertNative(activity.getWindow().getDecorView());
        check(has("云控拴绳")&&has("JoyoseLeash")&&has("0.3.0 Beta")&&has("沐月、ChatGPT")&&has("GPL-2.0-only")&&has("https://github.com/imuyue/JoyoseLeash"),"About metadata mismatch");
        check(has("本软件包含来自 JoyCtl 的代码：")&&has("https://github.com/hexwander/joyctl")&&has("https://github.com/hexwander/joyctl/blob/main/LICENSE"),"JoyCtl attribution missing");
        check(onMain(()->{TextView warning=find(activity.getWindow().getDecorView(),"Beta版可能存在不可预料的问题，使用时建议备份数据，遇到问题及时反馈");return warning!=null&&warning.getCurrentTextColor()==0xffa52020;}),"Beta warning missing or not red");
        appShot("about");click("查看 MIT 许可证全文");check(has("JoyCtl MIT许可证"),"Offline MIT license missing");appShot("joyctl-license");back();back();check(has("服务器列表"),"About back navigation failed");check(requests()==before,"About started Root");report.put("aboutMetadataAndRedWarning",true).put("joyctlAttributionAndOfflineLicense",true).put("aboutRootCalls",requests()-before);
    }
    void structuredChecks()throws Exception{
        long before=requests();
        JSONArray fixture=new JSONArray().put("example.visual.game;0:384000 6:1017600 7:1017600;30;120:12 60:18;2;10;1;0:0 6:0;opaque");
        onMain(()->{Method tree=activity.getClass().getDeclaredMethod("tree",String.class,Object.class,Runnable.class,String.class);tree.setAccessible(true);tree.invoke(activity,"MIGT检查",fixture,(Runnable)()->{},"game_booster.migt");return null;});sleep(100);
        click("example.visual.game");check(has("CPU频率（kHz）"),"String array lost field context");click("CPU频率（kHz）");check(has("CPU 6")&&has("1017600"),"MIGT native fields missing");appShot("migt-editor");
        JSONObject pid=new JSONObject().put("PID_T","90#10:0 0 0 0 0 0,44.5:45 90 60 15 1.5 3;future");
        onMain(()->{Method tree=activity.getClass().getDeclaredMethod("tree",String.class,Object.class,Runnable.class,String.class);tree.setAccessible(true);tree.invoke(activity,"PID检查",pid,(Runnable)()->{},"game_booster");return null;});sleep(100);
        click("PID_T");click("90 FPS · 温度档 2");check(has("Kp")&&has("Ki")&&has("Kd"),"PID native fields missing");appShot("pid-editor");
        check(requests()==before,"Structured editing started Root");report.put("structuredMigtAndPidControls",true).put("structuredEditorRootCalls",requests()-before);
    }
    void appShot(String name)throws Exception{
        sleep(120);Bitmap bitmap=onMain(()->{View root=activity.getWindow().getDecorView();Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(b));return b;});
        try(FileOutputStream out=new FileOutputStream(new File(output,name+"-app.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    static Object get(Object object,String field)throws Exception{Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(object);}
    static void set(Object object,String field,Object value)throws Exception{Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);f.set(object,value);}
    static Object invoke(Object object,String method)throws Exception{Method m=object.getClass().getDeclaredMethod(method);m.setAccessible(true);return m.invoke(object);}
    <T>T onMain(Callable<T> action)throws Exception{AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{result.set(action.call());}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new Exception(error.get());return result.get();}
    void sleep(long milliseconds){SystemClock.sleep(milliseconds);}
    long requests()throws Exception{return (Long)get(get(activity,"client"),"requests");}
    void waitReady()throws Exception{for(int i=0;i<200;i++){if(get(activity,"profiles")!=null&&!(Boolean)get(activity,"busy")){sleep(100);return;}sleep(100);}throw new AssertionError("Backend did not finish");}
    void select(int index)throws Exception{onMain(()->{Method m=activity.getClass().getDeclaredMethod("selectTab",int.class);m.setAccessible(true);long start=SystemClock.elapsedRealtimeNanos();m.invoke(activity,index);clickTimes.add((SystemClock.elapsedRealtimeNanos()-start)/1e6);return null;});sleep(80);}
    TextView find(View root,String text){if(root instanceof TextView&&((TextView)root).getText().toString().equals(text)&&root.getVisibility()==View.VISIBLE)return (TextView)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView value=find(group.getChildAt(i),text);if(value!=null)return value;}}return null;}
    boolean has(String text)throws Exception{return onMain(()->find(activity.getWindow().getDecorView(),text)!=null);}
    TextView findClick(View root,String text){if(root instanceof TextView&&!(root instanceof EditText)&&((TextView)root).getText().toString().equals(text)&&root.getVisibility()==View.VISIBLE)return (TextView)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView value=findClick(group.getChildAt(i),text);if(value!=null)return value;}}return null;}
    void click(String text)throws Exception{onMain(()->{View v=findClick(activity.getWindow().getDecorView(),text);if(v==null)throw new AssertionError("Control missing: "+text);while(!v.isClickable()&&v.getParent() instanceof View)v=(View)v.getParent();long start=SystemClock.elapsedRealtimeNanos();check(v.performClick(),"Control not clickable: "+text);clickTimes.add((SystemClock.elapsedRealtimeNanos()-start)/1e6);return null;});sleep(120);}
    void back()throws Exception{onMain(()->{activity.onBackPressed();return null;});sleep(80);}
    void assertNative(View view){check(!view.getClass().getName().startsWith("android.webkit."),"Web control present");Drawable d=view.getBackground();if(d instanceof StateListDrawable)d=((StateListDrawable)d).getCurrent();if(d instanceof GradientDrawable)check(((GradientDrawable)d).getCornerRadius()==0,"Rounded app control present");if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)assertNative(group.getChildAt(i));}}
    void shot(String name)throws Exception{sleep(150);Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap!=null)try(FileOutputStream stream=new FileOutputStream(new File(output,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}if(bitmap!=null)bitmap.recycle();}
    void modelChecks()throws Exception{
        Class<?> model=Class.forName("com.muyue.joyoseleash.RuleModel",true,activity.getClassLoader());
        Method table=model.getDeclaredMethod("table",List.class);table.setAccessible(true);List<String[]> rows=new ArrayList<>();rows.add(new String[]{"42.8","60"});rows.add(new String[]{"10","144"});check(table.invoke(null,rows).equals("10:144,42.8:60"),"Temperature rows not sorted");rows.add(new String[]{"10","90"});boolean rejected=false;try{table.invoke(null,rows);}catch(InvocationTargetException e){rejected=true;}check(rejected,"Duplicate temperature accepted");
        Method add=model.getDeclaredMethod("addApp",JSONObject.class,String.class,int.class);add.setAccessible(true);JSONObject p=new JSONObject("{\"version\":1,\"params\":{\"header\":{},\"unknown\":{\"keep\":[true,null,\"001\"]},\"game_booster\":{\"booster_config\":{\"ovrride_config\":[]}}}}");String unknown=p.getJSONObject("params").getJSONObject("unknown").toString();add.invoke(null,p,"example.visual.game",144);check(p.getJSONObject("params").getJSONObject("unknown").toString().equals(unknown),"Unrelated fields changed");JSONObject app=p.getJSONObject("params").getJSONObject("game_booster").getJSONObject("booster_config").getJSONArray("ovrride_config").getJSONObject(0);check(app.getJSONArray("scene_ovrride").length()==0,"New app missing parser field");report.put("typedModelPreservesUnknownFields",true).put("thresholdValidation",true);
    }
}
