package com.muyue.joyoseleash;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.*;

/** Native rule manager: lists, typed fields and threshold tables. */
public final class ManagerActivity extends Activity {
    private NativeUi ui;
    private RuleFields fieldNames;
    private RootClient client;
    private LinearLayout shell,nav,footer;
    private FrameLayout page;
    private TextView title,status,back,refresh;
    private final Handler main=new Handler(Looper.getMainLooper());
    private JSONObject live,profiles;
    private String baseProfiles="",module="booster_config",query="";
    private boolean dirty,busy,destroyed,foreground;
    private int tab;
    private Runnable detailBack;
    private JSONArray library=new JSONArray(),history=new JSONArray();
    private final Modern modern=new Modern();
    private final ArrayDeque<String> jobs=new ArrayDeque<>();
    private long editGeneration;
    private String notice="";
    private long noticeUntil;
    private static final int TEXT=android.text.InputType.TYPE_CLASS_TEXT;
    private static final int INTEGER=android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;
    private static final int DECIMAL=INTEGER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;
    private File draftFile(){return new File(getFilesDir(),"native-draft.json");}

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);ui=new NativeUi(this);fieldNames=new RuleFields(this);client=new RootClient(this);ui.notice=value->{notice=value;noticeUntil=System.currentTimeMillis()+4000;updateStatus();main.postDelayed(this::updateStatus,4100);};
        if(saved!=null){module=saved.getString("module","booster_config");tab=saved.getInt("tab",0);query=saved.getString("query","");}
        shell=ui.column();shell.setFocusableInTouchMode(true);
        if(Build.VERSION.SDK_INT>=30)shell.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars());Insets ime=insets.getInsets(WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));return insets;});
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout heading=ui.row();heading.setPadding(ui.dp(8),0,ui.dp(8),0);back=ui.textButton("←",()->onBackPressed());back.setTextSize(24);back.setPadding(0,0,0,0);back.setTextColor(NativeUi.INK);back.setContentDescription("返回上一页");back.setVisibility(View.GONE);heading.addView(back,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(56)));
        LinearLayout captions=ui.column();captions.setPadding(ui.dp(12),ui.dp(8),0,ui.dp(8));title=ui.text("游戏规则",22,NativeUi.INK);ui.strong(title);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);if(Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);captions.addView(title);
        status=ui.text("正在读取规则…",14,NativeUi.GRAY);status.setMaxLines(1);status.setEllipsize(TextUtils.TruncateAt.END);status.setPadding(0,ui.dp(4),0,0);captions.addView(status);heading.addView(captions,new LinearLayout.LayoutParams(0,-2,1));
        refresh=ui.textButton("⋯",()->ui.choose("更多操作",Arrays.asList("刷新状态","重新载入规则","连接状态"),choice->{if(choice==0)loadStatus(false);else if(choice==1){Runnable reload=()->{draftFile().delete();loadStatus(true);};if(dirty)ui.confirm("丢弃未应用修改并重新载入？",reload);else reload.run();}else diagnostics();}));refresh.setPadding(0,0,0,0);refresh.setTextColor(NativeUi.INK);refresh.setTextSize(26);refresh.setContentDescription("更多操作");heading.addView(refresh,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(56)));heading.setMinimumHeight(ui.dp(72));shell.addView(heading,new LinearLayout.LayoutParams(-1,-2));ui.line(shell);
        page=new FrameLayout(this);shell.addView(page,new LinearLayout.LayoutParams(-1,0,1));footer=ui.row();footer.setVisibility(View.GONE);shell.addView(footer);ui.line(shell);
        nav=ui.row();String[] names={"游戏","全局","规则库","设置"};for(int i=0;i<names.length;i++){final int index=i;ui.equal(nav,ui.tab(names[i],i==tab,()->selectTab(index)));}shell.addView(nav);setContentView(shell);shell.requestFocus();loadStatus(true);
    }
    @Override protected void onResume(){super.onResume();foreground=true;modern.startWatching();}
    @Override protected void onPause(){foreground=false;modern.stopWatching();writeDraft();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("module",module);out.putInt("tab",tab);out.putString("query",query);writeDraft();super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){destroyed=true;modern.stopWatching();modern.local.shutdownNow();main.removeCallbacksAndMessages(null);client.close();super.onDestroy();}
    @Override public void onBackPressed(){if(detailBack!=null){detailBack.run();return;}if(tab!=0){selectTab(0);return;}writeDraft();super.onBackPressed();}

    private interface Checked {void run()throws Exception;}
    private void safe(Checked action){try{action.run();}catch(Exception e){error(e);}}
    private void error(Exception e){String message=e.getMessage();if(message==null)message=Protocol.message(e);if(message.contains("error=13"))message="请在KernelSU授权Root";ui.toast(message);}
    private JSONObject obj(String key,Object value)throws JSONException{return new JSONObject().put(key,value);}
    private void call(String action,JSONObject payload,Consumer<Object> success){
        jobs.addLast(action);busy=true;updateStatus();
        client.request(action,payload,(value,error)->runOnUiThread(()->{if(destroyed)return;jobs.pollFirst();busy=!jobs.isEmpty();if(error!=null){modern.applying=false;modern.loadingLibrary=false;modern.loadingHistory=false;error(error);}else try{success.accept(value);}catch(Exception e){error(e);}updateStatus();}));
    }
    private void loadStatus(boolean initial){
        call("status",new JSONObject(),value->{try{accept((JSONObject)value,initial);if(initial){readDraft();renderHome();}}catch(Exception e){error(e);}});
    }
    private void accept(JSONObject data,boolean replace)throws Exception{
        live=data;if(modern.renderedFetch<0)modern.renderedFetch=data.getJSONObject("state").optLong("fetchNonce");JSONObject saved=data.getJSONObject("state").getJSONObject("profiles");
        if(replace||profiles==null||(!dirty&&detailBack==null)){
            boolean changed=profiles==null||!profiles.toString().equals(saved.toString());profiles=RuleModel.copy(saved);baseProfiles=saved.toString();
            if(replace){dirty=false;}if(!profiles.has(module))module=profiles.has("booster_config")?"booster_config":profiles.keys().hasNext()?profiles.keys().next():"booster_config";
            if(changed&&!replace&&detailBack==null&&tab==0)renderHome();
        }
        updateStatus();

    }
    private void updateStatus(){
        if(status==null)return;if(busy){status.setText(modern.operation(jobs.peekFirst()));return;}if(System.currentTimeMillis()<noticeUntil){status.setText(notice);return;}if(live==null){status.setText("未连接");return;}
        JSONObject s=live.optJSONObject("state"),h=live.optJSONObject("hookJoyose");if(s==null)return;
        boolean leased=s.optLong("fetchUntil")>System.currentTimeMillis();String network=leased?"临时放行":s.optBoolean("networkBlocked")?"屏蔽开启":"屏蔽关闭";
        String rule=!s.optBoolean("enabled")?"覆盖关闭":h==null||!h.optBoolean("ready")?"钩子未连接":!h.optString("error").isEmpty()?"应用失败":h.optLong("appliedNonce",-1)==s.optLong("applyNonce")?"规则已应用":"等待应用";
        JSONObject savedProfiles=s.optJSONObject("profiles");JSONObject saved=savedProfiles==null?null:savedProfiles.optJSONObject("booster_config");if(saved!=null&&h!=null&&h.has("activeVersion")&&h.optLong("activeVersion")!=saved.optLong("version"))rule="系统规则已更新";status.setText(dirty?"未应用":rule);status.setTextColor(dirty?NativeUi.ACCENT:NativeUi.GRAY);
    }
    private void changed(){dirty=true;editGeneration++;updateStatus();if(detailBack==null&&(tab==0||tab==1))modern.buttonsApply();}
    private void writeDraft(){
        if((!dirty&&!modern.netDirty)||profiles==null)return;try{Protocol.atomic(draftFile(),new JSONObject().put("profiles",profiles).put("base",baseProfiles).put("module",module).put("lockEdited",modern.lockEdited).put("locked",modern.lockDraft).put("netDirty",modern.netDirty).put("netBlocked",modern.netBlocked).put("discovery",modern.discovery).put("domains",modern.domains).toString());}catch(Exception ignored){}
    }
    private void readDraft(){
        if(!draftFile().isFile())return;try{JSONObject draft=new JSONObject(Protocol.read(draftFile()));if(!draft.optString("base").equals(baseProfiles))return;profiles=draft.getJSONObject("profiles");module=draft.optString("module",module);modern.lockEdited=draft.optBoolean("lockEdited");modern.lockDraft=draft.optBoolean("locked");modern.netDirty=draft.optBoolean("netDirty");modern.netBlocked=draft.optBoolean("netBlocked");modern.discovery=draft.optBoolean("discovery");if(draft.optJSONArray("domains")!=null)modern.domains=draft.getJSONArray("domains");dirty=true;}catch(Exception ignored){}
    }
    private void selectTab(int index){tab=index;renderHome();}
    private void content(View body){page.removeAllViews();page.addView(body,new FrameLayout.LayoutParams(-1,-1));shell.requestFocus();}
    private void actions(NativeUi.Row... buttons){footer.removeAllViews();footer.setVisibility(buttons.length==0?View.GONE:View.VISIBLE);footer.setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(12));for(int i=0;i<buttons.length;i++){NativeUi.Row item=buttons[i];TextView action=ui.button(item.title,item.value.equals("primary"),item.click);LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,-2,1);action.setMinHeight(ui.dp(52));if(i>0)layout.leftMargin=ui.dp(12);footer.addView(action,layout);}}
    private void renderHome(){
        detailBack=null;back.setVisibility(View.GONE);refresh.setVisibility(View.VISIBLE);nav.setVisibility(View.VISIBLE);title.setText(new String[]{"游戏规则","全局设置","规则库","设置"}[tab]);
        for(int i=0;i<nav.getChildCount();i++)ui.setTab(nav.getChildAt(i),i==tab);
        if(profiles==null)return;safe(()->{if(tab==0)modern.games();else if(tab==1)modern.globals();else if(tab==2)modern.libraryPage();else modern.settings();});
    }
    private void detail(String name,View body,Runnable goBack,NativeUi.Row... buttons){
        detailBack=goBack;back.setVisibility(View.VISIBLE);refresh.setVisibility(View.GONE);nav.setVisibility(View.GONE);title.setText(name);content(body);actions(buttons);
    }
    private JSONObject profile()throws JSONException{return profiles.getJSONObject(module);}
    private TextWatcher watcher(Consumer<String> changed){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int start,int before,int count){changed.accept(s.toString());}public void afterTextChanged(Editable e){}};}
    private String fps(JSONObject app,String key){String value=app.optString(key);if(value.isEmpty())return "继承";return value.matches("10:[0-9]+")?value.substring(value.indexOf(':')+1):"温控";}
    private interface SaveTables {void accept(JSONObject value)throws Exception;}
    private void copyTables(JSONObject source,JSONObject target)throws Exception{for(String key:new String[]{"dynamic_fps_M","dynamic_fps"})if(source.has(key))target.put(key,source.getString(key));else target.remove(key);}
    private String fieldName(Object key){String name=key.toString();switch(name){case "header":return "版本信息";case "game_booster":return "游戏规则";case "game_name":return "应用包名";case "version":return "版本";case "game_list":return "游戏列表";case "dynamic_fps_global":return "全局帧率";case "scene_ovrride":return "场景规则";case "booster_config":return "应用配置";case "ovrride_config":return "应用规则";case "dynamic_fps_M":return "MGAME帧率";case "dynamic_fps":return "TGAME帧率";default:return key instanceof Integer?"第 "+(((Integer)key)+1)+" 项":fieldNames.label("game_booster."+name,name);}}
    private void tree(String label,Object container,Runnable parent)throws Exception{tree(label,container,parent,"");}
    private void tree(String label,Object container,Runnable parent,String path)throws Exception{
        LinearLayout body=ui.column();NativeUi.Rows adapter=ui.new Rows();EditText search=ui.input("","搜索字段",TEXT);search.setSingleLine(true);body.addView(search);fillTree(adapter,container,label,parent,path,"");body.addView(ui.list(adapter),new LinearLayout.LayoutParams(-1,0,1));search.addTextChangedListener(watcher(filter->safe(()->fillTree(adapter,container,label,parent,path,filter))));
        detail(label,body,parent,new NativeUi.Row("添加","primary",()->addField(container,()->safe(()->tree(label,container,parent,path)))));
    }
    private void fillTree(NativeUi.Rows adapter,Object container,String label,Runnable parent,String path,String filter)throws Exception{
        ArrayList<NativeUi.Row> rows=new ArrayList<>();Runnable redraw=()->safe(()->tree(label,container,parent,path));
        String context=path.replaceAll("\\[[0-9]+\\]","");String leaf=context.substring(context.lastIndexOf('.')+1);
        for(Object key:RuleModel.keys(container)){
            Object value=RuleModel.get(container,key);String child=key instanceof Integer?path+"["+key+"]":path.isEmpty()?key.toString():path+"."+key;String fieldKey=key instanceof Integer?leaf:key.toString();
            String title=key instanceof Integer?fieldName(key):fieldNames.label(child,fieldName(key));StringRules.Document parsed=value instanceof String?StringRules.parse(fieldKey,(String)value):null;
            if(key instanceof Integer&&parsed!=null)for(StringRules.Section section:parsed.sections)for(StringRules.Field field:section.fields)if(field.type==StringRules.PACKAGE)title=modern.gameName(field.value);
            if(!title.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))&&!key.toString().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))&&!String.valueOf(value).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)))continue;
            final String itemTitle=title;String summary=parsed==null?RuleModel.summary(value):parsed.format+" ›";
            rows.add(new NativeUi.Row(title,summary,()->safe(()->{if(value instanceof JSONObject||value instanceof JSONArray)tree(itemTitle,value,redraw,child);else if(value instanceof Boolean){RuleModel.put(container,key,!((Boolean)value));changed();fillTree(adapter,container,label,parent,path,filter);}else editRuleValue(itemTitle,fieldKey,value,newValue->{RuleModel.put(container,key,newValue);changed();},redraw);}),()->fieldOptions(container,key,redraw)));
        }adapter.set(rows);
    }
    private void editRuleValue(String title,String key,Object value,SetValue save,Runnable parent){
        StringRules.Document parsed=value instanceof String?StringRules.parse(key,(String)value):null;
        if(parsed==null){editValue(title,value,result->{save.accept(result);parent.run();});return;}
        StringRuleEditor editor=new StringRuleEditor(ui,parsed,text->save.accept(text));
        detail(title,editor.view,parent,new NativeUi.Row("保存并应用","primary",modern::commit));
    }
    private interface SetValue {void accept(Object value)throws Exception;}
    private void editValue(String title,Object value,SetValue save){
        if(value==JSONObject.NULL){types(type->safe(()->save.accept(defaultValue(type))));return;}
        boolean number=value instanceof Number;ui.edit(title,String.valueOf(value),number?DECIMAL:TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE,!number,text->save.accept(number?RuleModel.number(text):text));
    }
    private void types(IntConsumer action){ui.choose("类型",Arrays.asList("文本","数字","开关","分组","列表","空值"),action);}
    private Object defaultValue(int type)throws JSONException{switch(type){case 0:return "";case 1:return 0L;case 2:return false;case 3:return new JSONObject();case 4:return new JSONArray();default:return JSONObject.NULL;}}
    private void addField(Object container,Runnable redraw){
        Consumer<String> name=key->types(type->safe(()->{Object value=defaultValue(type);SetValue save=v->{RuleModel.put(container,container instanceof JSONObject?key:((JSONArray)container).length(),v);changed();redraw.run();};if(type==0||type==1)editValue(key,value,save);else{save.accept(value);}}));
        if(container instanceof JSONObject)ui.edit("字段名称","",TEXT,false,key->{if(key.isEmpty())throw new Exception("名称不能为空");if(((JSONObject)container).has(key))throw new Exception("字段已存在");name.accept(key);});else name.accept("新项目");
    }
    private void fieldOptions(Object parent,Object key,Runnable redraw){
        ArrayList<String> choices=new ArrayList<>();if(parent instanceof JSONObject)choices.add("改名");else{choices.add("上移");choices.add("下移");}choices.add("修改类型");choices.add("删除");
        ui.choose(fieldName(key),choices,index->safe(()->{String action=choices.get(index);if(action.equals("改名"))ui.edit("字段名称",key.toString(),TEXT,false,name->{if(name.isEmpty())throw new Exception("名称不能为空");if(name.equals(key))return;if(((JSONObject)parent).has(name))throw new Exception("字段已存在");Object value=RuleModel.get(parent,key);((JSONObject)parent).put(name,value);RuleModel.remove(parent,key);changed();redraw.run();});else if(action.equals("上移")||action.equals("下移")){RuleModel.move((JSONArray)parent,(Integer)key,(Integer)key+(action.equals("上移")?-1:1));changed();redraw.run();}else if(action.equals("修改类型"))types(type->safe(()->{RuleModel.put(parent,key,defaultValue(type));changed();redraw.run();}));else ui.confirm("删除这个字段？",()->{RuleModel.remove(parent,key);changed();redraw.run();});}));
    }

    private String fetchBrief(JSONObject report,long nonce){return report==null||report.optLong("nonce")!=nonce?"等待":report.optBoolean("success")?report.optInt("count")+" 份":"失败";}
    private void diagnostics(){
        if(live==null)return;safe(()->{ArrayList<NativeUi.Row> rows=new ArrayList<>();JSONObject joy=live.optJSONObject("hookJoyose"),settings=live.optJSONObject("hookSettings"),net=live.optJSONObject("network"),s=live.getJSONObject("state");
            rows.add(new NativeUi.Row("Joyose",joy!=null&&joy.optBoolean("ready")?"已连接":"未连接",null));rows.add(new NativeUi.Row("系统云控",settings!=null&&settings.optBoolean("ready")?"已连接":"休眠",null));rows.add(new NativeUi.Row("实际规则版本",joy==null?"—":String.valueOf(joy.optLong("activeVersion")),null));
            if(joy!=null&&!joy.optString("error").isEmpty())rows.add(new NativeUi.Row(joy.optString("error"),"",null));
            rows.add(new NativeUi.Row("域名映射",net!=null&&net.optBoolean("hostsMounted")?"开":"关",null));rows.add(new NativeUi.Row("IPv4回环",net!=null&&net.optBoolean("ipv4Nat")?"开":"关",null));rows.add(new NativeUi.Row("IPv6回环",net!=null&&net.optBoolean("ipv6Nat")?"开":"不可用",null));
            for(String name:new String[]{"fetchJoyose","fetchSettings"}){JSONObject report=live.optJSONObject(name);if(report!=null&&!report.optString("message").isEmpty())rows.add(new NativeUi.Row(report.optString("message"),"",null));}
            detail("状态",ui.list(rows),this::renderHome,new NativeUi.Row(s.optBoolean("enabled")?"停用本地覆盖":"启用本地覆盖","",()->{if(s.optBoolean("enabled"))call("disable-override",new JSONObject(),v->{try{accept((JSONObject)v,false);renderHome();}catch(Exception e){error(e);}});else modern.commit();}));
        });
    }

    /** Primary workflows. Rendering and editing never invoke Root. */
    private final class Modern {
        final java.util.concurrent.ExecutorService local=java.util.concurrent.Executors.newSingleThreadExecutor();
        final Map<String,String> labels=new HashMap<>();
        final Set<String> installed=new HashSet<>();
        final List<String> launchable=new ArrayList<>();
        NativeUi.Rows gamesAdapter;
        boolean packagesLoaded,allGames,loadingLibrary,loadingHistory,libraryLoaded,historyLoaded,applying;
        boolean netDirty,netBlocked,discovery;
        JSONArray domains=new JSONArray();
        int libraryKind;
        String libraryModule="booster_config";
        FileObserver observer;
        long renderedFetch=-1;
        String operation(String action){
            if(action==null)return "正在读取…";
            switch(action){case "status":return "正在读取状态…";case "commit":return "正在应用修改…";case "replace-rule":return "正在替换现有规则…";case "library":return "正在读取规则库…";case "history":return "正在读取历史规则…";case "library-rule":return "正在读取所选规则…";case "fetch":return "正在拉取云端规则…";case "network":return "正在应用屏蔽设置…";case "refresh-ips":return "正在更新服务器IP…";default:return "正在执行操作…";}
        }
        void startWatching(){
            if(observer==null)observer=new FileObserver(Protocol.FILES,FileObserver.MOVED_TO|FileObserver.CLOSE_WRITE){@Override public void onEvent(int event,String path){
                if(path==null||!(path.equals("state.json")||path.startsWith("hook-")||path.startsWith("fetch-")))return;
                main.removeCallbacks(readLocal);main.postDelayed(readLocal,150);
            }};
            observer.startWatching();if(!packagesLoaded)loadPackages();
        }
        void stopWatching(){if(observer!=null)observer.stopWatching();main.removeCallbacks(readLocal);}
        final Runnable readLocal=()->{
            if(destroyed||!foreground)return;
            local.execute(()->{
                JSONObject state=Protocol.object(new File(Protocol.FILES,"state.json")),joy=Protocol.object(new File(Protocol.FILES,"hook-joyose.json")),settings=Protocol.object(new File(Protocol.FILES,"hook-settings.json")),a=Protocol.object(new File(Protocol.FILES,"fetch-joyose.json")),b=Protocol.object(new File(Protocol.FILES,"fetch-settings.json"));
                main.post(()->{if(destroyed||live==null)return;try{
                    JSONObject old=live.getJSONObject("state");old.put("fetchUntil",state.optLong("fetchUntil")).put("fetchNonce",state.optLong("fetchNonce")).put("applyNonce",state.optLong("applyNonce")).put("enabled",state.optBoolean("enabled")).put("locked",state.optBoolean("locked")).put("networkBlocked",state.optBoolean("networkBlocked")).put("discoveryBlock",state.optBoolean("discoveryBlock",true));
                    live.put("hookJoyose",joy).put("hookSettings",settings).put("fetchJoyose",a).put("fetchSettings",b);updateStatus();
                    long nonce=state.optLong("fetchNonce");boolean done=nonce>0&&a.optLong("nonce")==nonce&&b.optLong("nonce")==nonce&&state.optLong("fetchUntil")==0;
                    if(done&&nonce!=renderedFetch){renderedFetch=nonce;libraryLoaded=false;if(tab==2&&detailBack==null){libraryKind=3;libraryPage();}}
                }catch(Exception e){error(e);}});
            });
        };
        void loadPackages(){
            packagesLoaded=true;local.execute(()->{
                Map<String,String> names=new HashMap<>();Set<String> packages=new HashSet<>();List<String> visible=new ArrayList<>();android.content.pm.PackageManager pm=getPackageManager();
                try{for(android.content.pm.ApplicationInfo info:pm.getInstalledApplications(0)){packages.add(info.packageName);names.put(info.packageName,pm.getApplicationLabel(info).toString());if(pm.getLaunchIntentForPackage(info.packageName)!=null&&!info.packageName.equals(Protocol.PKG))visible.add(info.packageName);}}catch(Exception ignored){}
                visible.sort((a,b)->names.get(a).compareToIgnoreCase(names.get(b)));
                main.post(()->{if(destroyed)return;labels.putAll(names);installed.addAll(packages);launchable.clear();launchable.addAll(visible);if(profiles!=null&&tab==0&&detailBack==null&&gamesAdapter!=null)safe(()->fillGames(gamesAdapter,query));});
            });
        }
        String gameName(String name){
            for(String part:name.split("#"))if(labels.containsKey(part))return labels.get(part);
            if(name.contains("kiloo.subwaysurf"))return "地铁跑酷";
            if(name.contains("tmgp.sgame"))return "王者荣耀";
            if(name.contains("tmgp.pubgmhd"))return "和平精英";
            if(name.contains("miHoYo.Yuanshen")||name.contains("miHoYo.Genshin"))return "原神";
            if(name.contains("miHoYo.hkrpg"))return "崩坏：星穹铁道";
            if(name.contains("miHoYo.Nap"))return "绝区零";
            if(name.contains("tencent.lolm"))return "英雄联盟手游";
            if(name.contains("netease.dwrg"))return "第五人格";
            return name;
        }
        boolean isInstalled(String name){for(String part:name.split("#"))if(installed.contains(part))return true;return false;}
        JSONObject booster()throws Exception{return profiles.getJSONObject("booster_config");}
        String moduleName(String name){return name.equals("booster_config")?"游戏规则":name.equals("common_config")?"通用规则":name;}
        void buttonsApply(){if(dirty)actions(new NativeUi.Row("保存并应用","primary",this::commit));else actions();}
        void games()throws Exception{
            module="booster_config";LinearLayout body=ui.column();LinearLayout tools=ui.row();
            ui.equal(tools,ui.textButton(allGames?"全部游戏 ▾":"已安装游戏 ▾",()->{allGames=!allGames;safe(this::games);}));ui.equal(tools,ui.textButton("+ 添加游戏",this::addGame));body.addView(tools);
            EditText search=ui.input(query,"搜索游戏",TEXT);search.setSingleLine(true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(12));body.addView(search,p);
            NativeUi.Rows adapter=ui.new Rows();gamesAdapter=adapter;ListView list=ui.list(adapter);body.addView(list,new LinearLayout.LayoutParams(-1,0,1));fillGames(adapter,query);
            search.addTextChangedListener(watcher(text->{query=text;safe(()->fillGames(adapter,text));}));content(body);buttonsApply();
        }
        void fillGames(NativeUi.Rows adapter,String filter)throws Exception{
            JSONArray source=RuleModel.apps(booster(),false);ArrayList<JSONObject> values=new ArrayList<>();for(int i=0;i<source.length();i++)values.add(source.getJSONObject(i));
            values.sort((a,b)->{String x=a.optString("game_name"),y=b.optString("game_name");if(x.equals(y))return 0;if(x.equals("com.kiloo.subwaysurf.gw"))return -1;if(y.equals("com.kiloo.subwaysurf.gw"))return 1;return gameName(x).compareToIgnoreCase(gameName(y));});
            List<NativeUi.Row> rows=new ArrayList<>();for(JSONObject app:values){String name=app.optString("game_name"),label=gameName(name);boolean matches=label.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))||name.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));if(!matches)continue;
                if(!allGames&&filter.isEmpty()&&!isInstalled(name))continue;String a=fps(app,"dynamic_fps_M"),b=fps(app,"dynamic_fps");
                rows.add(new NativeUi.Row(label,a.equals(b)?a.equals("温控")?"温控":a.equals("继承")?"全局":a:"分别",()->safe(()->gameEditor(label,app,updated->copyTables(updated,app),ManagerActivity.this::renderHome)),()->ui.choose(label,Arrays.asList("其他规则","移除专用规则"),which->{if(which==0)safe(()->tree(label,app,ManagerActivity.this::renderHome));else ui.confirm("移除此游戏的专用规则？",()->safe(()->{String game=app.getString("game_name");JSONObject config=booster().getJSONObject("params").getJSONObject("game_booster").getJSONObject("booster_config");for(Object k:RuleModel.keys(config)){String key=k.toString();if(!key.equals("ovrride_config")&&!key.startsWith("ovrride_config#"))continue;JSONArray list=config.getJSONArray(key);for(int i=list.length()-1;i>=0;i--)if(list.getJSONObject(i).optString("game_name").equals(game))list.remove(i);}changed();games();}));})).game(a.equals(b)?a.equals("温控")?"温度分档":a.equals("继承")?"跟随全局":"固定帧率":"MGAME / TGAME"));
            }
            if(rows.isEmpty())rows.add(new NativeUi.Row(filter.isEmpty()?"尚无已安装游戏的专用规则":"没有匹配的游戏",filter.isEmpty()?"查看全部":"",filter.isEmpty()?()->{allGames=true;safe(this::games);}:null));adapter.set(rows);
        }
        void addGame(){
            LinearLayout body=ui.column();EditText search=ui.input("","搜索已安装应用",TEXT);search.setSingleLine(true);body.addView(search);NativeUi.Rows adapter=ui.new Rows();body.addView(ui.list(adapter),new LinearLayout.LayoutParams(-1,0,1));
            Consumer<String> fill=filter->{List<NativeUi.Row> rows=new ArrayList<>();for(String pkg:launchable){String label=labels.get(pkg);if(!label.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))&&!pkg.contains(filter))continue;rows.add(new NativeUi.Row(label,"添加",()->safe(()->{JSONObject app=RuleModel.addApp(booster(),pkg,144);changed();gameEditor(label,app,value->copyTables(value,app),ManagerActivity.this::renderHome); })));}adapter.set(rows);};fill.accept("");search.addTextChangedListener(watcher(fill));
            detail("添加游戏",body,ManagerActivity.this::renderHome,new NativeUi.Row("输入包名","",()->ui.edit("游戏包名","",TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,false,name->{JSONObject app=RuleModel.addApp(booster(),name,144);changed();gameEditor(gameName(name),app,value->copyTables(value,app),ManagerActivity.this::renderHome);})));
        }
        void gameEditor(String name,JSONObject target,SaveTables save,Runnable parent)throws Exception{
            FpsEditor[] editor=new FpsEditor[1];
            editor[0]=new FpsEditor(ui,target,value->{String before=target.toString();save.accept(value);copyTables(value,target);if(!before.equals(value.toString()))changed();},()->safe(()->tree(name,target,()->safe(()->gameEditor(name,target,save,parent)))));
            Runnable goBack=()->{if(editor[0].flush())parent.run();};
            detail(name,editor[0].view,goBack,new NativeUi.Row("保存并应用","primary",()->{if(editor[0].flush())commit();}));
        }
        void globals()throws Exception{
            module="booster_config";JSONObject game=booster().getJSONObject("params").getJSONObject("game_booster");List<NativeUi.Row> rows=new ArrayList<>();
            rows.add(new NativeUi.Row("规则版本",String.valueOf(booster().getLong("version")),()->editVersion("booster_config",()->safe(this::globals))));
            boolean lock=draftLocked();rows.add(new NativeUi.Row("锁定当前规则",lock?"开":"关",()->{lockDraft=!lock;lockEdited=true;changed();safe(this::globals);}));
            rows.add(new NativeUi.Row("全局帧率","设置 ›",()->safe(()->{JSONObject target=RuleModel.global(booster(),false);gameEditor("全局帧率",target,v->{copyTables(v,target);booster().getJSONObject("params").getJSONObject("game_booster").put("dynamic_fps_global",target);},ManagerActivity.this::renderHome);})));
            rows.add(new NativeUi.Row("显示与画面","›",()->group("显示与画面",game,key->key.contains("fps")||key.contains("display")||key.contains("qsync")||key.contains("frc")||key.contains("vrs")||key.contains("resolution")||key.contains("scale"))));
            rows.add(new NativeUi.Row("性能策略","›",()->group("性能策略",game,key->key.contains("performance")||key.contains("cpu")||key.contains("tuner")||key.contains("boost")||key.contains("mift")||key.contains("migt"))));
            rows.add(new NativeUi.Row("游戏识别","›",()->group("游戏识别",game,key->key.contains("identify")||key.contains("group")||key.contains("cluster")||key.contains("GameList"))));
            rows.add(new NativeUi.Row("全部全局字段","›",()->safe(()->tree("全局字段",game,ManagerActivity.this::renderHome,"game_booster"))));content(ui.list(rows));buttonsApply();
        }
        void group(String name,JSONObject data,Predicate<String> filter){safe(()->{List<NativeUi.Row> rows=new ArrayList<>();Runnable redraw=()->group(name,data,filter);for(Object k:RuleModel.keys(data)){String key=k.toString();if(!filter.test(key))continue;Object value=data.get(key);String label=globalName(key);rows.add(new NativeUi.Row(label,RuleModel.summary(value),()->safe(()->{if(value instanceof Boolean){data.put(key,!((Boolean)value));changed();redraw.run();}else if(value instanceof JSONObject||value instanceof JSONArray)tree(label,value,redraw,"game_booster."+key);else editRuleValue(label,key,value,v->{data.put(key,v);changed();},redraw);})));}detail(name,ui.list(rows),ManagerActivity.this::renderHome,new NativeUi.Row("应用修改","primary",this::commit));});}
        String globalName(String key){switch(key){case "booster_enable":return "游戏优化";case "tuner_enable":return "图形调优";case "cpuset_enable":return "CPU分组";case "qsync_enable":return "可变刷新率";case "monitor":return "性能监测";case "game_group_mapping_enable":return "游戏分组";case "support_highfps_app":return "高帧率应用";case "support_dynamic_refresh_rate_games":return "动态刷新率游戏";case "booster_config":return "应用策略";default:return fieldName(key);}}
        void editVersion(String name,Runnable redraw){safe(()->{JSONObject p=profiles.getJSONObject(name);ui.edit("规则版本",String.valueOf(p.getLong("version")),INTEGER,false,text->{long value=Long.parseLong(text.trim());if(value<1||value>2147483647L)throw new Exception("版本无效");p.put("version",value);changed();redraw.run();});});}
        boolean lockEdited,lockDraft;
        boolean draftLocked(){return lockEdited?lockDraft:live.optJSONObject("state").optBoolean("locked");}
        void commit(){
            if(applying){ui.toast("正在应用，请稍候");return;}safe(()->{
                for(Object name:RuleModel.keys(profiles))Protocol.validateProfile(name.toString(),profiles.getJSONObject(name.toString()));
                JSONObject request=new JSONObject().put("profiles",RuleModel.copy(profiles)).put("baseProfiles",new JSONObject(baseProfiles)).put("locked",draftLocked());long generation=editGeneration;applying=true;
                call("commit",request,result->{applying=false;try{JSONObject data=(JSONObject)result;if(generation==editGeneration){accept(data,true);draftFile().delete();lockEdited=false;}else{live=data;baseProfiles=data.getJSONObject("state").getJSONObject("profiles").toString();dirty=true;}historyLoaded=false;renderHome();ui.toast("已应用");}catch(Exception e){error(e);}});
            });
        }
        void libraryPage()throws Exception{
            LinearLayout body=ui.column(),tools=ui.row();tools.setPadding(ui.dp(12),ui.dp(4),ui.dp(12),0);
            TextView moduleButton=ui.textButton(moduleName(libraryModule)+" ▾",()->{
                List<String> modules=new ArrayList<>();for(Object key:RuleModel.keys(profiles))modules.add(key.toString());List<String> labels=new ArrayList<>();for(String key:modules)labels.add(moduleName(key));ui.choose("规则分类",labels,i->{libraryModule=modules.get(i);safe(this::libraryPage);});
            });tools.addView(moduleButton,new LinearLayout.LayoutParams(0,-2,1));tools.addView(ui.textButton("刷新",()->loadRules(true)));body.addView(tools);
            LinearLayout kinds=ui.row();kinds.setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(12));String[] names={"历史备份","系统缓存","ROM预置","云端下载"};for(int i=0;i<4;i++){final int index=i;ui.equal(kinds,ui.choice(names[i],libraryKind==i,()->{libraryKind=index;safe(this::libraryPage);}));}body.addView(kinds);ui.line(body);
            List<NativeUi.Row> rows=new ArrayList<>();SimpleDateFormat format=new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault());Set<String> seen=new HashSet<>();
            if(libraryKind==0){
                for(int i=0;i<history.length();i++){JSONObject snapshot=history.getJSONObject(i);JSONArray modules=snapshot.optJSONArray("modules");if(modules==null)continue;for(int j=0;j<modules.length();j++){JSONObject info=RuleModel.copy(modules.getJSONObject(j));if(!info.optString("module").equals(libraryModule))continue;String fingerprint=info.optString("fingerprint",snapshot.getString("id"));if(!seen.add(fingerprint))continue;
                    info.put("id",snapshot.getString("id")).put("kind","history").put("time",snapshot.getLong("time"));rows.add(ruleCard(info,format.format(new Date(info.getLong("time")))+"  ·  "+info.optInt("games")+" 个游戏"));}}
            }else{String kind=libraryKind==1?"system":libraryKind==2?"rom":"cloud";for(int i=library.length()-1;i>=0;i--){JSONObject info=library.getJSONObject(i);if(!info.optString("kind").equals(kind)||!info.optString("module").equals(libraryModule)||!seen.add(info.getString("id")))continue;rows.add(ruleCard(info,info.optInt("games")+" 个游戏"));}}
            if(rows.isEmpty())rows.add(new NativeUi.Row((libraryKind==0&&loadingHistory)||(libraryKind!=0&&loadingLibrary)?"正在读取规则…":"此分类暂无规则","",null));body.addView(ui.list(rows),new LinearLayout.LayoutParams(-1,0,1));
            JSONObject state=live.optJSONObject("state");if(state!=null&&state.optLong("fetchUntil")>System.currentTimeMillis()){TextView progress=ui.text("正在获取云端规则…",14,NativeUi.GRAY);ui.pad(progress,20);body.addView(progress);}
            content(body);actions(new NativeUi.Row("获取云端规则","primary",()->call("fetch",new JSONObject(),value->{try{accept((JSONObject)value,false);libraryKind=3;libraryPage();}catch(Exception e){error(e);}})));loadRules(false);
        }
        NativeUi.Row ruleCard(JSONObject info,String detail)throws Exception{
            return new NativeUi.Row(String.valueOf(info.getLong("version")),"",()->safe(()->rulePreview(info))).record(detail,()->confirmReplace(info));
        }
        void confirmReplace(JSONObject info){safe(()->ui.confirm("使用版本 "+info.getLong("version")+"？","替换并应用",()->replace(info)));}
        void loadRules(boolean force){
            if(libraryKind==0){if(loadingHistory||(!force&&historyLoaded))return;loadingHistory=true;call("history",new JSONObject(),result->{loadingHistory=false;historyLoaded=true;history=(JSONArray)result;if(tab==2&&detailBack==null)safe(this::libraryPage);});}
            else {if(loadingLibrary||(!force&&libraryLoaded))return;loadingLibrary=true;call("library",new JSONObject(),result->{loadingLibrary=false;libraryLoaded=true;library=(JSONArray)result;if(tab==2&&detailBack==null)safe(this::libraryPage);});}
        }
        JSONObject reference(JSONObject info)throws Exception{return new JSONObject().put("kind",info.getString("kind")).put("id",info.getString("id")).put("module",info.getString("module"));}
        void rulePreview(JSONObject info)throws Exception{
            String name=info.getString("module");JSONObject saved=live.getJSONObject("state").getJSONObject("profiles").optJSONObject(name);long active=saved==null?0:saved.optLong("version");JSONObject hook=live.optJSONObject("hookJoyose");if(name.equals("booster_config")&&hook!=null&&hook.has("activeVersion"))active=hook.optLong("activeVersion");
            List<NativeUi.Row> rows=new ArrayList<>();rows.add(new NativeUi.Row("当前生效",String.valueOf(active),null));rows.add(new NativeUi.Row("所选版本",String.valueOf(info.getLong("version")),null));rows.add(new NativeUi.Row("应用规则",info.optInt("games")+" 个游戏",null));
            if(info.has("subwayMGAME")){String a=tableBrief(info.optString("subwayMGAME")),b=tableBrief(info.optString("subwayTGAME"));rows.add(new NativeUi.Row("地铁跑酷",a.equals(b)?a:a+" / "+b,null));}
            rows.add(new NativeUi.Row("查看完整规则","›",()->safe(()->call("library-rule",reference(info),value->{try{JSONObject selected=(JSONObject)value;readOnlyTree(moduleName(name),selected.getJSONObject("params"),()->safe(()->rulePreview(info)));}catch(Exception e){error(e);}}))));
            detail("规则预览",ui.list(rows),ManagerActivity.this::renderHome,new NativeUi.Row("替换并应用","primary",()->confirmReplace(info)));
        }
        String tableBrief(String value){if(value.isEmpty())return "跟随全局";if(value.matches("10:[0-9]+")){String fps=value.substring(value.indexOf(':')+1);return fps.equals("0")?"不限制":fps+" FPS";}return "温度分档";}
        void readOnlyTree(String label,Object data,Runnable parent)throws Exception{
            List<NativeUi.Row> rows=new ArrayList<>();for(Object key:RuleModel.keys(data)){Object value=RuleModel.get(data,key);rows.add(new NativeUi.Row(fieldName(key),RuleModel.summary(value),value instanceof JSONObject||value instanceof JSONArray?()->safe(()->readOnlyTree(fieldName(key),value,()->safe(()->readOnlyTree(label,data,parent)))):null));}detail(label,ui.list(rows),parent,new NativeUi.Row("返回","",parent));
        }
        void replace(JSONObject info){
            if(applying)return;safe(()->{JSONObject request=reference(info).put("baseProfiles",new JSONObject(baseProfiles));applying=true;call("replace-rule",request,result->{applying=false;try{accept((JSONObject)result,true);draftFile().delete();lockEdited=false;historyLoaded=false;tab=0;query="";renderHome();ui.toast("已替换并应用");}catch(Exception e){error(e);}});});
        }
        void ensureNet()throws Exception{if(netDirty)return;JSONObject s=live.getJSONObject("state");netBlocked=s.optBoolean("networkBlocked");discovery=s.optBoolean("discoveryBlock",true);domains=new JSONArray(s.getJSONArray("domains").toString());}
        void settings()throws Exception{
            ensureNet();List<NativeUi.Row> rows=new ArrayList<>();rows.add(new NativeUi.Row("云控服务器屏蔽",netBlocked?"开":"关",()->{netBlocked=!netBlocked;netDirty=true;safe(this::settings);}));rows.add(new NativeUi.Row("地址发现屏蔽",discovery?"开":"关",()->{discovery=!discovery;netDirty=true;safe(this::settings);}));rows.add(new NativeUi.Row("服务器列表",domains.length()+" 个 ›",this::servers));rows.add(new NativeUi.Row("更新服务器IP","›",()->call("refresh-ips",new JSONObject(),value->{try{accept((JSONObject)value,false);renderHome();}catch(Exception e){error(e);}})));
            rows.add(new NativeUi.Row("其他规则库","›",this::otherModules));rows.add(new NativeUi.Row("连接状态","›",ManagerActivity.this::diagnostics));rows.add(new NativeUi.Row("刷新状态","",()->loadStatus(false)));rows.add(new NativeUi.Row("停用本地覆盖","",()->ui.confirm("停用本地规则覆盖？",()->call("disable-override",new JSONObject(),value->{try{accept((JSONObject)value,false);renderHome();}catch(Exception e){error(e);}}))));rows.add(new NativeUi.Row("关于","›",this::about));content(ui.list(rows));
            if(netDirty)actions(new NativeUi.Row("保存屏蔽设置","primary",()->safe(()->call("network",new JSONObject().put("blocked",netBlocked).put("discoveryBlock",discovery).put("domains",domains),value->{try{accept((JSONObject)value,false);netDirty=false;renderHome();ui.toast("屏蔽设置已保存");}catch(Exception e){error(e);}}))));else actions();
        }
        void about(){
            LinearLayout body=ui.column();ui.pad(body,20);
            TextView name=ui.text("云控拴绳",26,NativeUi.INK);ui.strong(name);ui.margin(body,name,12);
            ui.margin(body,ui.text("JoyoseLeash",16,NativeUi.GRAY),10);
            TextView version=ui.text("0.3.0 Beta",16,NativeUi.GRAY);ui.margin(body,version,12);ui.gap(body,24);ui.line(body);
            body.addView(ui.label("作者",ui.text("沐月、ChatGPT",16,NativeUi.INK)));ui.line(body);
            body.addView(ui.label("开源协议",ui.text("GPL-2.0-only",16,NativeUi.INK)));ui.line(body);
            ui.margin(body,ui.text("开源地址",16,NativeUi.INK),20);
            String source="https://github.com/imuyue/JoyoseLeash";
            TextView link=ui.textButton(source,()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(source)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException e){ui.toast("未找到浏览器");}});link.setTextSize(15);link.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);link.setPadding(0,ui.dp(12),0,ui.dp(12));link.setContentDescription("打开开源地址 "+source);body.addView(link,new LinearLayout.LayoutParams(-1,-2));
            ui.gap(body,20);ui.line(body);
            TextView warning=ui.text("Beta版可能存在不可预料的问题，使用时建议备份数据，遇到问题及时反馈",16,NativeUi.ERROR);warning.setLineSpacing(ui.dp(5),1);ui.margin(body,warning,24);
            ui.gap(body,24);ui.line(body);
            ui.margin(body,ui.text("本软件包含来自 JoyCtl 的代码：",16,NativeUi.INK),20);
            body.addView(sourceLink("https://github.com/hexwander/joyctl"));
            ui.margin(body,ui.text("Copyright (c) 2026 JoyCtl Contributors\nLicensed under the MIT License.",15,NativeUi.INK),8);
            ui.margin(body,ui.text("原 MIT 许可证全文如下：",16,NativeUi.INK),20);
            body.addView(sourceLink("https://github.com/hexwander/joyctl/blob/main/LICENSE"));
            TextView license=ui.textButton("查看 MIT 许可证全文",()->safe(this::joyctlLicense));license.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);license.setPadding(0,ui.dp(12),0,ui.dp(12));body.addView(license);
            ScrollView scroll=new ScrollView(ManagerActivity.this);scroll.addView(body);detail("关于",scroll,ManagerActivity.this::renderHome);
        }
        TextView sourceLink(String url){TextView link=ui.textButton(url,()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException e){ui.toast("未找到浏览器");}});link.setTextSize(15);link.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);link.setPadding(0,ui.dp(12),0,ui.dp(12));link.setContentDescription("打开链接 "+url);return link;}
        void joyctlLicense()throws Exception{TextView text=ui.text(Protocol.read(getAssets().open("licenses/JoyCtl-MIT.txt")),16,NativeUi.INK);text.setTextIsSelectable(true);text.setLineSpacing(ui.dp(4),1);ui.pad(text,20);ScrollView scroll=new ScrollView(ManagerActivity.this);scroll.addView(text);detail("JoyCtl MIT许可证",scroll,this::about);}
        void servers(){safe(()->{List<NativeUi.Row> rows=new ArrayList<>();for(int i=0;i<domains.length();i++){final int index=i;String name=domains.getString(i);rows.add(new NativeUi.Row(name,"编辑",()->ui.edit("服务器",name,TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,false,value->{domains.put(index,value.trim());netDirty=true;servers();}),()->ui.confirm("移除此服务器？",()->{domains.remove(index);netDirty=true;servers();})));}detail("服务器列表",ui.list(rows),ManagerActivity.this::renderHome,new NativeUi.Row("添加服务器","primary",()->ui.edit("服务器地址","",TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,false,value->{domains.put(value.trim());netDirty=true;servers();})));});}
        void otherModules(){safe(()->{List<NativeUi.Row> rows=new ArrayList<>();for(Object key:RuleModel.keys(profiles)){String name=key.toString();rows.add(new NativeUi.Row(moduleName(name),String.valueOf(profiles.getJSONObject(name).getLong("version")),()->safe(()->{module=name;List<NativeUi.Row> fields=Arrays.asList(new NativeUi.Row("版本",String.valueOf(profile().getLong("version")),()->editVersion(name,this::otherModules)),new NativeUi.Row("编辑字段","›",()->safe(()->tree(moduleName(name),profile().getJSONObject("params"),this::otherModules))));detail(moduleName(name),ui.list(fields),this::otherModules,new NativeUi.Row("应用修改","primary",this::commit));})));}detail("其他规则库",ui.list(rows),ManagerActivity.this::renderHome,new NativeUi.Row("新建规则库","primary",()->ui.edit("规则库名称","",TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,false,name->{if(!name.matches("[A-Za-z0-9_.-]{1,80}")||profiles.has(name))throw new Exception("名称无效或已存在");profiles.put(name,new JSONObject().put("version",1).put("params",new JSONObject()));module=name;changed();tree(name,profile().getJSONObject("params"),this::otherModules);})));});}
    }
}
