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
    private boolean dirty,busy,destroyed,foreground,localOnlyDetail;
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
        LinearLayout heading=ui.appBar();back=ui.navButton("←",()->onBackPressed(),24);back.setContentDescription(ui.s("a11y_back"));back.setVisibility(View.GONE);heading.addView(back,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(56)));
        LinearLayout captions=ui.column();captions.setPadding(ui.dp(4),ui.dp(8),0,ui.dp(8));title=ui.text(ui.s("screen_games"),20,NativeUi.INK);ui.strong(title);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);if(Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);captions.addView(title);
        status=ui.text(ui.s("status_reading_rules"),13,NativeUi.GRAY);status.setMaxLines(1);status.setEllipsize(TextUtils.TruncateAt.END);status.setPadding(0,ui.dp(3),0,0);captions.addView(status);heading.addView(captions,new LinearLayout.LayoutParams(0,-2,1));
        refresh=ui.navButton("⋮",this::moreActions,22);refresh.setContentDescription(ui.s("action_more"));heading.addView(refresh,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(56)));heading.setMinimumHeight(ui.dp(64));shell.addView(heading,new LinearLayout.LayoutParams(-1,-2));ui.line(shell);
        page=new FrameLayout(this);shell.addView(page,new LinearLayout.LayoutParams(-1,0,1));footer=ui.row();footer.setVisibility(View.GONE);shell.addView(footer);ui.line(shell);
        nav=ui.row();String[] names={ui.s("nav_games"),ui.s("mode_global"),ui.s("nav_library"),ui.s("nav_settings")};for(int i=0;i<names.length;i++){final int index=i;ui.equal(nav,ui.tab(names[i],i==tab,()->selectTab(index)));}nav.setBackgroundColor(NativeUi.WHITE);shell.addView(nav);ui.line(shell);setContentView(shell);shell.requestFocus();loadStatus(true);
    }
    @Override protected void onResume(){super.onResume();foreground=true;modern.startWatching();}
    @Override protected void onPause(){foreground=false;modern.stopWatching();writeDraft();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("module",module);out.putInt("tab",tab);out.putString("query",query);writeDraft();super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){destroyed=true;modern.stopWatching();modern.local.shutdownNow();main.removeCallbacksAndMessages(null);client.close();super.onDestroy();}
    @Override public void onBackPressed(){if(detailBack!=null){detailBack.run();return;}if(tab!=0){selectTab(0);return;}writeDraft();super.onBackPressed();}

    private interface Checked {void run()throws Exception;}
    private void safe(Checked action){try{action.run();}catch(Exception e){error(e);}}
    private void error(Exception e){String message=ui.error(e);if(message.contains("error=13"))message=ui.s("error_root_permission");ui.toast(message);}
    private String diagnostic(JSONObject data,String field){JSONObject caption=data.optJSONObject(field+"Caption");if(caption==null&&field.equals("message"))caption=data.optJSONObject("caption");return caption==null?ui.error(new Exception(data.optString(field))):ui.message(caption);}
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
        if(status==null)return;if(localOnlyDetail){status.setText(ui.s("status_local_only"));status.setTextColor(NativeUi.GRAY);return;}if(busy){status.setText(modern.operation(jobs.peekFirst()));return;}if(System.currentTimeMillis()<noticeUntil){status.setText(notice);return;}if(live==null){status.setText(ui.s("status_disconnected"));return;}
        JSONObject s=live.optJSONObject("state"),h=live.optJSONObject("hookJoyose");if(s==null)return;
        boolean leased=s.optLong("fetchUntil")>System.currentTimeMillis();String network=leased?ui.s("status_temporary_access"):s.optBoolean("networkBlocked")?ui.s("status_blocking_on"):ui.s("status_blocking_off");
        String rule=!s.optBoolean("enabled")?ui.s("status_override_off"):h==null||!h.optBoolean("ready")?ui.s("status_hook_disconnected"):!h.optString("error").isEmpty()?ui.s("status_apply_failed"):h.optLong("appliedNonce",-1)==s.optLong("applyNonce")?ui.s("status_applied"):ui.s("status_waiting_apply");
        JSONObject savedProfiles=s.optJSONObject("profiles");JSONObject saved=savedProfiles==null?null:savedProfiles.optJSONObject("booster_config");if(saved!=null&&h!=null&&h.has("activeVersion")&&h.optLong("activeVersion")!=saved.optLong("version"))rule=ui.s("status_system_updated");status.setText(dirty?ui.s("status_unapplied"):rule);status.setTextColor(dirty?NativeUi.ACCENT:NativeUi.GRAY);
    }
    private void moreActions(){
        JSONObject state=live==null?null:live.optJSONObject("state");boolean enabled=state!=null&&state.optBoolean("enabled");
        List<String> items=Arrays.asList(ui.s("action_refresh_status"),ui.s("action_reload"),ui.s(enabled?"override_disable":"override_enable"),ui.s("action_connections"));
        ui.choose(ui.s("action_more"),items,choice->{
            if(choice==0)loadStatus(false);
            else if(choice==1){Runnable reload=()->{draftFile().delete();loadStatus(true);};if(dirty)ui.confirm(ui.s("confirm_discard_reload"),reload);else reload.run();}
            else if(choice==2){if(enabled)ui.confirm(ui.s("confirm_disable_override"),this::disableOverride);else modern.commit();}
            else diagnostics();
        });
    }
    private void disableOverride(){call("disable-override",new JSONObject(),value->{try{accept((JSONObject)value,false);renderHome();}catch(Exception e){error(e);}});}
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
        localOnlyDetail=false;updateStatus();detailBack=null;back.setVisibility(View.GONE);refresh.setVisibility(View.VISIBLE);nav.setVisibility(View.VISIBLE);title.setText(new String[]{ui.s("screen_games"),ui.s("screen_global"),ui.s("nav_library"),ui.s("nav_settings")}[tab]);
        for(int i=0;i<nav.getChildCount();i++)ui.setTab(nav.getChildAt(i),i==tab);
        if(profiles==null){content(ui.list(Arrays.asList(new NativeUi.Row(ui.s("error_games_not_ready"),ui.s("action_refresh_status"),()->loadStatus(true)),new NativeUi.Row(ui.s("system_cloud"),ui.s("system_independent_readonly"),modern::systemSources))));actions();return;}safe(()->{if(tab==0)modern.games();else if(tab==1)modern.globals();else if(tab==2)modern.libraryPage();else modern.settings();});
    }
    private void detail(String name,View body,Runnable goBack,NativeUi.Row... buttons){
        localOnlyDetail=false;detailBack=goBack;back.setVisibility(View.VISIBLE);refresh.setVisibility(View.GONE);nav.setVisibility(View.GONE);title.setText(name);content(body);actions(buttons);updateStatus();
    }
    private void cloudDetail(String name,View body,Runnable goBack,NativeUi.Row... buttons){detail(name,body,goBack,buttons);localOnlyDetail=true;updateStatus();}
    private JSONObject profile()throws JSONException{return profiles.getJSONObject(module);}
    private TextWatcher watcher(Consumer<String> changed){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int start,int before,int count){changed.accept(s.toString());}public void afterTextChanged(Editable e){}};}
    private String fps(JSONObject app,String key){String value=app.optString(key);if(value.isEmpty())return "inherit";return value.matches("10:[0-9]+")?value.substring(value.indexOf(':')+1):"thermal";}
    private interface SaveTables {void accept(JSONObject value)throws Exception;}
    private void copyTables(JSONObject source,JSONObject target)throws Exception{for(String key:new String[]{"dynamic_fps_M","dynamic_fps"})if(source.has(key))target.put(key,source.getString(key));else target.remove(key);}
    private String fieldName(Object key){String name=key.toString();switch(name){case "header":return ui.s("manager_field_header");case "game_booster":return ui.s("screen_games");case "game_name":return ui.s("field_package");case "version":return ui.s("field_version");case "game_list":return ui.s("field_game_list");case "dynamic_fps_global":return ui.s("field_global_fps");case "scene_ovrride":return ui.s("field_scenes");case "booster_config":return ui.s("field_app_config");case "ovrride_config":return ui.s("library_rules_for_apps");case "dynamic_fps_M":return ui.s("field_mgame_fps");case "dynamic_fps":return ui.s("field_tgame_fps");default:return key instanceof Integer?ui.s("field_item_index",((Integer)key)+1):fieldNames.label("game_booster."+name,name);}}
    private void tree(String label,Object container,Runnable parent)throws Exception{tree(label,container,parent,"");}
    private void tree(String label,Object container,Runnable parent,String path)throws Exception{
        LinearLayout body=ui.column();NativeUi.Rows adapter=ui.new Rows();EditText search=ui.input("",ui.s("field_search"),TEXT);search.setSingleLine(true);body.addView(search);fillTree(adapter,container,label,parent,path,"");body.addView(ui.list(adapter),new LinearLayout.LayoutParams(-1,0,1));search.addTextChangedListener(watcher(filter->safe(()->fillTree(adapter,container,label,parent,path,filter))));
        detail(label,body,parent,new NativeUi.Row(ui.s("field_add"),"primary",()->addField(container,()->safe(()->tree(label,container,parent,path)))));
    }
    private void fillTree(NativeUi.Rows adapter,Object container,String label,Runnable parent,String path,String filter)throws Exception{
        ArrayList<NativeUi.Row> rows=new ArrayList<>();Runnable redraw=()->safe(()->tree(label,container,parent,path));
        String context=path.replaceAll("\\[[0-9]+\\]","");String leaf=context.substring(context.lastIndexOf('.')+1);
        for(Object key:RuleModel.keys(container)){
            RuleModel.Field field=RuleModel.field(container,key);Object value=field.value;String itemPath=key instanceof Integer?path+"["+key+"]":path.isEmpty()?key.toString():path+"."+key;String child=field.namedItem?itemPath+"."+field.name:itemPath;String fieldKey=field.namedItem?field.name:key instanceof Integer?leaf:key.toString();
            String title=key instanceof Integer&&!field.namedItem?fieldName(key):fieldNames.label(child,fieldName(field.name));StringRules.Document parsed=value instanceof String?StringRules.parse(fieldKey,(String)value):null;
            if(key instanceof Integer&&!field.namedItem&&parsed!=null)for(StringRules.Section section:parsed.sections)for(StringRules.Field parsedField:section.fields)if(parsedField.type==StringRules.PACKAGE)title=modern.gameName(parsedField.value);
            if(!title.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))&&!key.toString().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))&&!field.name.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))&&!String.valueOf(value).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)))continue;
            final String itemTitle=title;String summary=parsed==null?ui.summary(value):(parsed.caption==null?parsed.format:ui.message(parsed.caption))+" ›";
            rows.add(new NativeUi.Row(title,summary,()->safe(()->{if(value instanceof JSONObject||value instanceof JSONArray)tree(itemTitle,value,redraw,child);else if(value instanceof Boolean){RuleModel.put(field.parent,field.key,!((Boolean)value));changed();fillTree(adapter,container,label,parent,path,filter);}else editRuleValue(itemTitle,fieldKey,value,newValue->{RuleModel.put(field.parent,field.key,newValue);changed();},redraw);}),()->fieldOptions(container,key,redraw)).typed(value).field(field.name));
        }adapter.set(rows);
    }
    private void editRuleValue(String title,String key,Object value,SetValue save,Runnable parent){
        StringRules.Document parsed=value instanceof String?StringRules.parse(key,(String)value):null;
        RuleModel.Encoded encoded=value instanceof String?RuleModel.encoded((String)value):null;
        if(parsed==null&&encoded!=null){ui.choose(title,Arrays.asList(ui.s("field_structured_json"),ui.s("field_raw_string")),which->{if(which==0)new JsonValueEditor(ui,ManagerActivity.this::detail,()->save.accept(encoded.encode()),new NativeUi.Row(ui.s("field_save_apply"),"primary",modern::commit)).open(title,encoded.value,parent);else editValue(title,value,result->{save.accept(result);parent.run();});});return;}
        if(parsed==null){editValue(title,value,result->{save.accept(result);parent.run();});return;}
        StringRuleEditor editor=new StringRuleEditor(ui,parsed,text->save.accept(text));
        detail(title,editor.view,parent,new NativeUi.Row(ui.s("field_save_apply"),"primary",modern::commit));
    }
    private interface SetValue {void accept(Object value)throws Exception;}
    private void editValue(String title,Object value,SetValue save){
        if(value==JSONObject.NULL){types(type->{if(type!=5)safe(()->save.accept(defaultValue(type)));});return;}
        boolean number=value instanceof Number;ui.edit(title,String.valueOf(value),number?DECIMAL:TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE,!number,text->save.accept(number?RuleModel.number(text):text));
    }
    private void types(IntConsumer action){ui.choose(ui.s("field_types"),Arrays.asList(ui.s("field_type_string"),ui.s("field_type_number"),ui.s("field_type_boolean"),ui.s("field_type_object"),ui.s("field_type_array"),ui.s("value_null")),action);}
    private Object defaultValue(int type)throws JSONException{return RuleModel.defaultValue(type);}
    private void addField(Object container,Runnable redraw){
        if(container instanceof JSONArray){ui.choose(ui.s("field_add_array_item"),Arrays.asList(ui.s("field_named_item"),ui.s("field_plain_item")),which->{if(which==0)ui.edit(ui.s("field_original_name"),"",TEXT,false,name->{RuleModel.validName(name);addFieldValue(container,name,true,redraw);});else addFieldValue(container,ui.s("field_new_item"),false,redraw);});return;}
        ui.edit(ui.s("field_original_name"),"",TEXT,false,name->{RuleModel.validName(name);if(((JSONObject)container).has(name))throw ui.failure("field_exists");addFieldValue(container,name,false,redraw);});
    }
    private void addFieldValue(Object container,String name,boolean named,Runnable redraw){
        types(type->safe(()->{Object value=defaultValue(type);SetValue save=v->{RuleModel.put(container,container instanceof JSONObject?name:((JSONArray)container).length(),named?RuleModel.named(name,v):v);changed();redraw.run();};if(type==0||type==1)editValue(name,value,save);else save.accept(value);}));
    }
    private void fieldOptions(Object parent,Object key,Runnable redraw){safe(()->{
        RuleModel.Field field=RuleModel.field(parent,key);ArrayList<String> choices=new ArrayList<>();if(parent instanceof JSONObject||field.namedItem)choices.add("rename");else choices.add("make_named");if(field.namedItem)choices.add("expand");if(parent instanceof JSONArray){choices.add("up");choices.add("down");}choices.add("type");choices.add("delete");
        List<String> labels=new ArrayList<>();for(String action:choices)labels.add(ui.s("manager_field_action_"+action));ui.choose(field.namedItem?field.name:fieldName(key),labels,index->safe(()->{String action=choices.get(index);
            if(action.equals("rename"))ui.edit(ui.s("field_original_name"),field.name,TEXT,false,name->{if(RuleModel.rename((JSONObject)field.parent,field.name,name)){changed();redraw.run();}});
            else if(action.equals("make_named"))ui.edit(ui.s("field_original_name"),"",TEXT,false,name->{RuleModel.validName(name);ui.confirm(ui.s("field_confirm_named"),ui.s("field_convert"),()->safe(()->{RuleModel.put(parent,key,RuleModel.named(name,RuleModel.get(parent,key)));changed();redraw.run();}));});
            else if(action.equals("expand"))tree(field.name,RuleModel.get(parent,key),redraw);
            else if(action.equals("up")||action.equals("down")){int from=(Integer)key,to=from+(action.equals("up")?-1:1);if(to>=0&&to<((JSONArray)parent).length()){RuleModel.move((JSONArray)parent,from,to);changed();redraw.run();}}
            else if(action.equals("type"))types(type->safe(()->{if(type==RuleModel.type(RuleModel.get(field.parent,field.key)))return;ui.confirm(ui.s("field_confirm_type"),ui.s("field_replace_type"),()->safe(()->{RuleModel.put(field.parent,field.key,defaultValue(type));changed();redraw.run();}));}));
            else ui.confirm(ui.s("field_confirm_delete"),()->{RuleModel.remove(parent,key);changed();redraw.run();});
        }));
    });}

    private String fetchBrief(JSONObject report,long nonce){return report==null||report.optLong("nonce")!=nonce?ui.s("status_waiting"):report.optBoolean("success")?ui.q("count_documents",report.optInt("count"),report.optInt("count")):ui.s("status_failed");}
    private void diagnostics(){
        if(live==null)return;safe(()->{ArrayList<NativeUi.Row> rows=new ArrayList<>();JSONObject joy=live.optJSONObject("hookJoyose"),settings=live.optJSONObject("hookSettings"),net=live.optJSONObject("network"),s=live.getJSONObject("state");
            rows.add(new NativeUi.Row("Joyose",joy!=null&&joy.optBoolean("ready")?ui.s("status_connected"):ui.s("status_disconnected"),null));rows.add(new NativeUi.Row(ui.s("system_cloud_short"),settings!=null&&settings.optBoolean("ready")?ui.s("status_connected"):ui.s("status_sleeping"),null));rows.add(new NativeUi.Row(ui.s("diagnostics_active_version"),joy==null?"—":String.valueOf(joy.optLong("activeVersion")),null));
            if(joy!=null&&!joy.optString("error").isEmpty())rows.add(new NativeUi.Row(diagnostic(joy,"error"),"",null));
            rows.add(new NativeUi.Row(ui.s("diagnostics_hosts"),net!=null&&net.optBoolean("hostsMounted")?ui.s("value_on"):ui.s("value_off"),null));rows.add(new NativeUi.Row(ui.s("diagnostics_ipv4"),net!=null&&net.optBoolean("ipv4Nat")?ui.s("value_on"):ui.s("value_off"),null));rows.add(new NativeUi.Row(ui.s("diagnostics_ipv6"),net!=null&&net.optBoolean("ipv6Nat")?ui.s("value_on"):ui.s("status_unavailable"),null));
            for(String name:new String[]{"fetchJoyose","fetchSettings"}){JSONObject report=live.optJSONObject(name);if(report!=null&&!report.optString("message").isEmpty())rows.add(new NativeUi.Row(diagnostic(report,"message"),"",null));}
            detail(ui.s("diagnostics_title"),ui.list(rows),this::renderHome,new NativeUi.Row(s.optBoolean("enabled")?ui.s("override_disable"):ui.s("override_enable"),"",()->{if(s.optBoolean("enabled"))call("disable-override",new JSONObject(),v->{try{accept((JSONObject)v,false);renderHome();}catch(Exception e){error(e);}});else modern.commit();}));
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
            if(action==null)return ui.s("status_reading");
            switch(action){case "status":return ui.s("status_reading_state");case "commit":return ui.s("status_applying");case "replace-rule":return ui.s("status_replacing");case "library":return ui.s("status_loading_library");case "history":return ui.s("status_loading_history");case "library-rule":return ui.s("status_loading_selected");case "fetch":return ui.s("status_fetching");case "network":return ui.s("status_applying_blocking");case "refresh-ips":return ui.s("status_refreshing_ips");default:return ui.s("status_working");}
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
            if(name.contains("kiloo.subwaysurf"))return ui.s("game_subway");
            if(name.contains("tmgp.sgame"))return ui.s("game_honor");
            if(name.contains("tmgp.pubgmhd"))return ui.s("game_peace");
            if(name.contains("miHoYo.Yuanshen")||name.contains("miHoYo.Genshin"))return ui.s("game_genshin");
            if(name.contains("miHoYo.hkrpg"))return ui.s("game_starrail");
            if(name.contains("miHoYo.Nap"))return ui.s("game_zenless");
            if(name.contains("tencent.lolm"))return ui.s("game_wildrift");
            if(name.contains("netease.dwrg"))return ui.s("game_identity");
            return name;
        }
        boolean isInstalled(String name){for(String part:name.split("#"))if(installed.contains(part))return true;return false;}
        JSONObject booster()throws Exception{return profiles.getJSONObject("booster_config");}
        String moduleName(String name){return name.equals("booster_config")?ui.s("screen_games"):name.equals("common_config")?ui.s("module_common"):name;}
        void buttonsApply(){if(dirty)actions(new NativeUi.Row(ui.s("field_save_apply"),"primary",this::commit));else actions();}
        void games()throws Exception{
            module="booster_config";LinearLayout body=ui.column();LinearLayout tools=ui.row();
            ui.equal(tools,ui.textButton(allGames?ui.s("games_all"):ui.s("games_installed"),()->{allGames=!allGames;safe(this::games);}));ui.equal(tools,ui.textButton(ui.s("games_add"),this::addGame));body.addView(tools);
            EditText search=ui.input(query,ui.s("games_search"),TEXT);search.setSingleLine(true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(12));body.addView(search,p);
            NativeUi.Rows adapter=ui.new Rows();gamesAdapter=adapter;ListView list=ui.list(adapter);body.addView(list,new LinearLayout.LayoutParams(-1,0,1));fillGames(adapter,query);
            search.addTextChangedListener(watcher(text->{query=text;safe(()->fillGames(adapter,text));}));content(body);buttonsApply();
        }
        void fillGames(NativeUi.Rows adapter,String filter)throws Exception{
            JSONArray source=RuleModel.apps(booster(),false);ArrayList<JSONObject> values=new ArrayList<>();for(int i=0;i<source.length();i++)values.add(source.getJSONObject(i));
            values.sort((a,b)->{String x=a.optString("game_name"),y=b.optString("game_name");if(x.equals(y))return 0;if(x.equals("com.kiloo.subwaysurf.gw"))return -1;if(y.equals("com.kiloo.subwaysurf.gw"))return 1;return gameName(x).compareToIgnoreCase(gameName(y));});
            List<NativeUi.Row> rows=new ArrayList<>();for(JSONObject app:values){String name=app.optString("game_name"),label=gameName(name);boolean matches=label.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))||name.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));if(!matches)continue;
                if(!allGames&&filter.isEmpty()&&!isInstalled(name))continue;String a=fps(app,"dynamic_fps_M"),b=fps(app,"dynamic_fps");
                rows.add(new NativeUi.Row(label,a.equals(b)?a.equals("thermal")?ui.s("mode_thermal"):a.equals("inherit")?ui.s("mode_global"):a:ui.s("mode_separate"),()->safe(()->gameEditor(label,app,updated->copyTables(updated,app),ManagerActivity.this::renderHome)),()->ui.choose(label,Arrays.asList(ui.s("games_other_rules"),ui.s("games_remove_rules")),which->{if(which==0)safe(()->tree(label,app,ManagerActivity.this::renderHome));else ui.confirm(ui.s("games_confirm_remove"),()->safe(()->{String game=app.getString("game_name");JSONObject config=booster().getJSONObject("params").getJSONObject("game_booster").getJSONObject("booster_config");for(Object k:RuleModel.keys(config)){String key=k.toString();if(!key.equals("ovrride_config")&&!key.startsWith("ovrride_config#"))continue;JSONArray list=config.getJSONArray(key);for(int i=list.length()-1;i>=0;i--)if(list.getJSONObject(i).optString("game_name").equals(game))list.remove(i);}changed();games();}));})).game(a.equals(b)?a.equals("thermal")?ui.s("mode_thresholds"):a.equals("inherit")?ui.s("mode_follow_global"):ui.s("mode_fixed"):"MGAME / TGAME"));
            }
            if(rows.isEmpty())rows.add(new NativeUi.Row(filter.isEmpty()?ui.s("games_no_installed"):ui.s("games_no_matches"),filter.isEmpty()?ui.s("games_view_all"):"",filter.isEmpty()?()->{allGames=true;safe(this::games);}:null));adapter.set(rows);
        }
        void addGame(){
            LinearLayout body=ui.column();EditText search=ui.input("",ui.s("games_search_installed"),TEXT);search.setSingleLine(true);body.addView(search);NativeUi.Rows adapter=ui.new Rows();body.addView(ui.list(adapter),new LinearLayout.LayoutParams(-1,0,1));
            Consumer<String> fill=filter->{List<NativeUi.Row> rows=new ArrayList<>();for(String pkg:launchable){String label=labels.get(pkg);if(!label.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))&&!pkg.contains(filter))continue;rows.add(new NativeUi.Row(label,ui.s("field_add"),()->safe(()->{JSONObject app=RuleModel.addApp(booster(),pkg,144);changed();gameEditor(label,app,value->copyTables(value,app),ManagerActivity.this::renderHome); })));}adapter.set(rows);};fill.accept("");search.addTextChangedListener(watcher(fill));
            detail(ui.s("games_add_title"),body,ManagerActivity.this::renderHome,new NativeUi.Row(ui.s("games_enter_package"),"",()->ui.edit(ui.s("games_package_name"),"",TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,false,name->{JSONObject app=RuleModel.addApp(booster(),name,144);changed();gameEditor(gameName(name),app,value->copyTables(value,app),ManagerActivity.this::renderHome);})));
        }
        void gameEditor(String name,JSONObject target,SaveTables save,Runnable parent)throws Exception{
            FpsEditor[] editor=new FpsEditor[1];
            editor[0]=new FpsEditor(ui,target,value->{String before=target.toString();save.accept(value);copyTables(value,target);if(!before.equals(value.toString()))changed();},()->safe(()->tree(name,target,()->safe(()->gameEditor(name,target,save,parent)))));
            Runnable goBack=()->{if(editor[0].flush())parent.run();};
            detail(name,editor[0].view,goBack,new NativeUi.Row(ui.s("field_save_apply"),"primary",()->{if(editor[0].flush())commit();}));
        }
        void globals()throws Exception{
            module="booster_config";JSONObject game=booster().getJSONObject("params").getJSONObject("game_booster");List<NativeUi.Row> rows=new ArrayList<>();
            rows.add(new NativeUi.Row(ui.s("global_rule_version"),String.valueOf(booster().getLong("version")),()->editVersion("booster_config",()->safe(this::globals))));
            boolean lock=draftLocked();rows.add(new NativeUi.Row(ui.s("global_lock"),lock?ui.s("value_on"):ui.s("value_off"),()->{lockDraft=!lock;lockEdited=true;changed();safe(this::globals);}).typed(lock));
            rows.add(new NativeUi.Row(ui.s("field_global_fps"),ui.s("action_settings_arrow"),()->safe(()->{JSONObject target=RuleModel.global(booster(),false);gameEditor(ui.s("field_global_fps"),target,v->{copyTables(v,target);booster().getJSONObject("params").getJSONObject("game_booster").put("dynamic_fps_global",target);},ManagerActivity.this::renderHome);})));
            rows.add(new NativeUi.Row(ui.s("global_display"),"›",()->group(ui.s("global_display"),game,key->key.contains("fps")||key.contains("display")||key.contains("qsync")||key.contains("frc")||key.contains("vrs")||key.contains("resolution")||key.contains("scale"))));
            rows.add(new NativeUi.Row(ui.s("global_performance"),"›",()->group(ui.s("global_performance"),game,key->key.contains("performance")||key.contains("cpu")||key.contains("tuner")||key.contains("boost")||key.contains("mift")||key.contains("migt"))));
            rows.add(new NativeUi.Row(ui.s("global_identification"),"›",()->group(ui.s("global_identification"),game,key->key.contains("identify")||key.contains("group")||key.contains("cluster")||key.contains("GameList"))));
            rows.add(new NativeUi.Row(ui.s("global_all_fields"),"›",()->safe(()->tree(ui.s("global_fields"),game,ManagerActivity.this::renderHome,"game_booster"))));content(ui.list(rows));buttonsApply();
        }
        void group(String name,JSONObject data,Predicate<String> filter){safe(()->{List<NativeUi.Row> rows=new ArrayList<>();Runnable redraw=()->group(name,data,filter);for(Object k:RuleModel.keys(data)){String key=k.toString();if(!filter.test(key))continue;Object value=data.get(key);String label=globalName(key);rows.add(new NativeUi.Row(label,ui.summary(value),()->safe(()->{if(value instanceof Boolean){data.put(key,!((Boolean)value));changed();redraw.run();}else if(value instanceof JSONObject||value instanceof JSONArray)tree(label,value,redraw,"game_booster."+key);else editRuleValue(label,key,value,v->{data.put(key,v);changed();},redraw);}),()->fieldOptions(data,key,redraw)).typed(value).field(key));}detail(name,ui.list(rows),ManagerActivity.this::renderHome,new NativeUi.Row(ui.s("action_apply_changes"),"primary",this::commit));});}
        String globalName(String key){switch(key){case "booster_enable":return ui.s("global_game_optimization");case "tuner_enable":return ui.s("global_graphics_tuning");case "cpuset_enable":return ui.s("global_cpu_groups");case "qsync_enable":return ui.s("global_variable_refresh");case "monitor":return ui.s("global_monitor");case "game_group_mapping_enable":return ui.s("global_game_groups");case "support_highfps_app":return ui.s("global_high_fps_apps");case "support_dynamic_refresh_rate_games":return ui.s("global_dynamic_refresh_games");case "booster_config":return ui.s("global_app_policy");default:return fieldName(key);}}
        void editVersion(String name,Runnable redraw){safe(()->{JSONObject p=profiles.getJSONObject(name);ui.edit(ui.s("global_rule_version"),String.valueOf(p.getLong("version")),INTEGER,false,text->{long value=Long.parseLong(text.trim());if(value<1||value>2147483647L)throw ui.failure("error_invalid_version");p.put("version",value);changed();redraw.run();});});}
        boolean lockEdited,lockDraft;
        boolean draftLocked(){return lockEdited?lockDraft:live.optJSONObject("state").optBoolean("locked");}
        void commit(){
            if(applying){ui.toast(ui.s("status_applying_wait"));return;}safe(()->{
                for(Object name:RuleModel.keys(profiles))Protocol.validateProfile(name.toString(),profiles.getJSONObject(name.toString()));
                JSONObject request=new JSONObject().put("profiles",RuleModel.copy(profiles)).put("baseProfiles",new JSONObject(baseProfiles)).put("locked",draftLocked());long generation=editGeneration;applying=true;
                call("commit",request,result->{applying=false;try{JSONObject data=(JSONObject)result;if(generation==editGeneration){accept(data,true);draftFile().delete();lockEdited=false;}else{live=data;baseProfiles=data.getJSONObject("state").getJSONObject("profiles").toString();dirty=true;}historyLoaded=false;renderHome();ui.toast(ui.s("status_applied_notice"));}catch(Exception e){error(e);}});
            });
        }
        void libraryPage()throws Exception{
            LinearLayout body=ui.column(),tools=ui.row();tools.setPadding(ui.dp(12),ui.dp(4),ui.dp(12),0);
            TextView moduleButton=ui.textButton(moduleName(libraryModule)+" ▾",()->{
                List<String> modules=new ArrayList<>();for(Object key:RuleModel.keys(profiles))modules.add(key.toString());List<String> labels=new ArrayList<>();for(String key:modules)labels.add(moduleName(key));ui.choose(ui.s("library_classification"),labels,i->{libraryModule=modules.get(i);safe(this::libraryPage);});
            });tools.addView(moduleButton,new LinearLayout.LayoutParams(0,-2,1));tools.addView(ui.textButton(ui.s("action_refresh"),()->loadRules(true)));body.addView(tools);
            LinearLayout kinds=ui.row();kinds.setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(12));String[] names={ui.s("library_history"),ui.s("library_system"),ui.s("library_rom"),ui.s("library_cloud")};for(int i=0;i<4;i++){final int index=i;ui.equal(kinds,ui.choice(names[i],libraryKind==i,()->{libraryKind=index;safe(this::libraryPage);}));}body.addView(kinds);ui.line(body);
            List<NativeUi.Row> rows=new ArrayList<>();SimpleDateFormat format=new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault());Set<String> seen=new HashSet<>();
            if(libraryKind==0){
                for(int i=0;i<history.length();i++){JSONObject snapshot=history.getJSONObject(i);JSONArray modules=snapshot.optJSONArray("modules");if(modules==null)continue;for(int j=0;j<modules.length();j++){JSONObject info=RuleModel.copy(modules.getJSONObject(j));if(!info.optString("module").equals(libraryModule))continue;String fingerprint=info.optString("fingerprint",snapshot.getString("id"));if(!seen.add(fingerprint))continue;
                    info.put("id",snapshot.getString("id")).put("kind","history").put("time",snapshot.getLong("time"));rows.add(ruleCard(info,format.format(new Date(info.getLong("time"))),ui.s("field_version")+" "+info.getLong("version")+"  ·  "+ui.q("count_games",info.optInt("games"),info.optInt("games"))));}}
            }else{String kind=libraryKind==1?"system":libraryKind==2?"rom":"cloud";for(int i=library.length()-1;i>=0;i--){JSONObject info=library.getJSONObject(i);if(!info.optString("kind").equals(kind)||!info.optString("module").equals(libraryModule)||!seen.add(info.getString("id")))continue;rows.add(ruleCard(info,ui.q("count_games",info.optInt("games"),info.optInt("games"))));}}
            if(rows.isEmpty())rows.add(new NativeUi.Row((libraryKind==0&&loadingHistory)||(libraryKind!=0&&loadingLibrary)?ui.s("status_reading_rules"):ui.s("library_empty"),"",null));body.addView(ui.list(rows),new LinearLayout.LayoutParams(-1,0,1));
            JSONObject state=live.optJSONObject("state");if(state!=null&&state.optLong("fetchUntil")>System.currentTimeMillis()){TextView progress=ui.text(ui.s("status_reading_cloud"),14,NativeUi.GRAY);ui.pad(progress,20);body.addView(progress);}
            content(body);actions(new NativeUi.Row(ui.s("library_fetch"),"primary",()->call("fetch",new JSONObject(),value->{try{accept((JSONObject)value,false);libraryKind=3;libraryPage();}catch(Exception e){error(e);}})));loadRules(false);
        }
        NativeUi.Row ruleCard(JSONObject info,String detail)throws Exception{
            return ruleCard(info,ui.s("field_version")+" "+info.getLong("version"),detail);
        }
        NativeUi.Row ruleCard(JSONObject info,String title,String detail)throws Exception{
            return new NativeUi.Row(title,"",()->safe(()->rulePreview(info))).record(detail,()->confirmReplace(info));
        }
        void confirmReplace(JSONObject info){safe(()->ui.confirm(ui.s("library_use_version",String.valueOf(info.getLong("version"))),ui.s("library_replace_apply"),()->replace(info)));}
        void loadRules(boolean force){
            if(libraryKind==0){if(loadingHistory||(!force&&historyLoaded))return;loadingHistory=true;call("history",new JSONObject(),result->{loadingHistory=false;historyLoaded=true;history=(JSONArray)result;if(tab==2&&detailBack==null)safe(this::libraryPage);});}
            else {if(loadingLibrary||(!force&&libraryLoaded))return;loadingLibrary=true;call("library",new JSONObject(),result->{loadingLibrary=false;libraryLoaded=true;library=(JSONArray)result;if(tab==2&&detailBack==null)safe(this::libraryPage);});}
        }
        JSONObject reference(JSONObject info)throws Exception{return new JSONObject().put("kind",info.getString("kind")).put("id",info.getString("id")).put("module",info.getString("module"));}
        void rulePreview(JSONObject info)throws Exception{
            String name=info.getString("module");JSONObject saved=live.getJSONObject("state").getJSONObject("profiles").optJSONObject(name);long active=saved==null?0:saved.optLong("version");JSONObject hook=live.optJSONObject("hookJoyose");if(name.equals("booster_config")&&hook!=null&&hook.has("activeVersion"))active=hook.optLong("activeVersion");
            List<NativeUi.Row> rows=new ArrayList<>();rows.add(new NativeUi.Row(ui.s("library_active"),String.valueOf(active),null));rows.add(new NativeUi.Row(ui.s("library_selected"),String.valueOf(info.getLong("version")),null));rows.add(new NativeUi.Row(ui.s("library_rules_for_apps"),ui.q("count_games",info.optInt("games"),info.optInt("games")),null));
            if(info.has("subwayMGAME")){String a=tableBrief(info.optString("subwayMGAME")),b=tableBrief(info.optString("subwayTGAME"));rows.add(new NativeUi.Row(ui.s("game_subway"),a.equals(b)?a:a+" / "+b,null));}
            rows.add(new NativeUi.Row(ui.s("library_view_complete"),"›",()->safe(()->call("library-rule",reference(info),value->{try{JSONObject selected=(JSONObject)value;readOnlyTree(moduleName(name),selected.getJSONObject("params"),()->safe(()->rulePreview(info)));}catch(Exception e){error(e);}}))));
            detail(ui.s("library_preview"),ui.list(rows),ManagerActivity.this::renderHome,new NativeUi.Row(ui.s("library_replace_apply"),"primary",()->confirmReplace(info)));
        }
        String tableBrief(String value){if(value.isEmpty())return ui.s("mode_follow_global");if(value.matches("10:[0-9]+")){String fps=value.substring(value.indexOf(':')+1);return fps.equals("0")?ui.s("mode_unlimited"):fps+" FPS";}return ui.s("mode_thresholds");}
        void readOnlyTree(String label,Object data,Runnable parent)throws Exception{readOnlyTree(label,data,parent,false);}
        void readOnlyTree(String label,Object data,Runnable parent,boolean localOnly)throws Exception{
            List<NativeUi.Row> rows=new ArrayList<>();for(Object key:RuleModel.keys(data)){Object value=RuleModel.get(data,key);rows.add(new NativeUi.Row(fieldName(key),ui.summary(value),value instanceof JSONObject||value instanceof JSONArray?()->safe(()->readOnlyTree(fieldName(key),value,()->safe(()->readOnlyTree(label,data,parent,localOnly)),localOnly)):null));}
            if(localOnly)cloudDetail(label,ui.list(rows),parent,new NativeUi.Row(ui.s("action_back"),"",parent));else detail(label,ui.list(rows),parent,new NativeUi.Row(ui.s("action_back"),"",parent));
        }
        void replace(JSONObject info){
            if(applying)return;safe(()->{JSONObject request=reference(info).put("baseProfiles",new JSONObject(baseProfiles));applying=true;call("replace-rule",request,result->{applying=false;try{accept((JSONObject)result,true);draftFile().delete();lockEdited=false;historyLoaded=false;tab=0;query="";renderHome();ui.toast(ui.s("status_replaced_notice"));}catch(Exception e){error(e);}});});
        }
        void ensureNet()throws Exception{if(netDirty)return;JSONObject s=live.getJSONObject("state");netBlocked=s.optBoolean("networkBlocked");discovery=s.optBoolean("discoveryBlock",true);domains=new JSONArray(s.getJSONArray("domains").toString());}
        void settings()throws Exception{
            ensureNet();List<NativeUi.Row> rows=new ArrayList<>();rows.add(new NativeUi.Row(ui.s("settings_block_cloud"),netBlocked?ui.s("value_on"):ui.s("value_off"),()->{netBlocked=!netBlocked;netDirty=true;safe(this::settings);}).typed(netBlocked));rows.add(new NativeUi.Row(ui.s("settings_block_discovery"),discovery?ui.s("value_on"):ui.s("value_off"),()->{discovery=!discovery;netDirty=true;safe(this::settings);}).typed(discovery));rows.add(new NativeUi.Row(ui.s("settings_servers"),ui.q("count_servers",domains.length(),domains.length())+" ›",this::servers));rows.add(new NativeUi.Row(ui.s("settings_refresh_ips"),"›",()->call("refresh-ips",new JSONObject(),value->{try{accept((JSONObject)value,false);renderHome();}catch(Exception e){error(e);}})));
            rows.add(new NativeUi.Row(ui.s("system_cloud"),"›",this::systemSources));rows.add(new NativeUi.Row(ui.s("settings_other_libraries"),"›",this::otherModules));rows.add(new NativeUi.Row(ui.s("action_connections"),"›",ManagerActivity.this::diagnostics));rows.add(new NativeUi.Row(ui.s("settings_about"),"›",this::about));content(ui.list(rows));
            if(netDirty)actions(new NativeUi.Row(ui.s("settings_save_blocking"),"primary",()->safe(()->call("network",new JSONObject().put("blocked",netBlocked).put("discoveryBlock",discovery).put("domains",domains),value->{try{accept((JSONObject)value,false);netDirty=false;renderHome();ui.toast(ui.s("settings_blocking_saved"));}catch(Exception e){error(e);}}))));else actions();
        }
        long systemScreen;
        int systemOffset;String systemQuery="",systemSource="settings-db";
        JSONArray systemSourceDescriptors=new JSONArray();
        void systemHome(){systemScreen++;renderHome();}
        void systemSources(){
            long screen=++systemScreen;NativeUi.Rows adapter=ui.new Rows();adapter.set(Arrays.asList(new NativeUi.Row(ui.s("status_sources_checking"),"",null)));
            cloudDetail(ui.s("system_cloud"),ui.list(adapter),this::systemHome);
            call("system-sources",new JSONObject(),result->{if(screen!=systemScreen||detailBack==null)return;safe(()->{
                JSONArray sources=((JSONObject)result).getJSONArray("sources");systemSourceDescriptors=sources;List<NativeUi.Row> rows=new ArrayList<>();
                for(int i=0;i<sources.length();i++){JSONObject source=sources.getJSONObject(i);String id=source.getString("id"),name=id.equals("settings-db")?ui.s("system_source_database"):id.equals("common-backup")?ui.s("system_source_common"):ui.s("system_source_window");
                    String info=source.optString("path")+(source.has("recordCount")?" · "+ui.q("count_records",source.optInt("recordCount"),source.optInt("recordCount")):"");
                    rows.add(new NativeUi.Row(name,source.optBoolean("available")?ui.q("count_records",source.optInt("recordCount"),source.optInt("recordCount"))+" ›":ui.s("status_unavailable_arrow"),()->{systemSource=id;systemLibrary(0,"");},()->safe(()->showText(ui.s("system_source_details"),source.toString(2),this::systemSources))).game(info));
                }adapter.set(rows);
            });});
        }
        void systemLibrary(int offset,String filter){
            long screen=++systemScreen;systemOffset=offset;systemQuery=filter;
            LinearLayout body=ui.column();
            EditText search=ui.input(filter,ui.s("system_search_hint"),TEXT);search.setSingleLine(true);search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);search.setOnEditorActionListener((v,actionId,event)->{systemLibrary(0,v.getText().toString());return true;});body.addView(search);
            LinearLayout tools=ui.row();ui.equal(tools,ui.textButton(ui.s("action_search"),()->systemLibrary(0,search.getText().toString())));ui.equal(tools,ui.textButton(ui.s("action_refresh"),()->systemLibrary(offset,search.getText().toString())));body.addView(tools);
            NativeUi.Rows adapter=ui.new Rows();adapter.set(Arrays.asList(new NativeUi.Row(ui.s("status_system_reading"),"",null)));body.addView(ui.list(adapter),new LinearLayout.LayoutParams(-1,0,1));LinearLayout pages=ui.row();body.addView(pages);
            cloudDetail(ui.s("system_cloud"),body,this::systemSources);
            safe(()->call("system-library",new JSONObject().put("sourceId",systemSource).put("offset",offset).put("limit",30).put("query",filter),result->{if(screen!=systemScreen||detailBack==null)return;safe(()->{
                JSONObject data=(JSONObject)result;ArrayList<NativeUi.Row> rows=new ArrayList<>();JSONObject failure=data.optJSONObject("error");
                if(failure!=null)rows.add(new NativeUi.Row(diagnostic(failure,"message"),ui.s("system_read_failed"),null));
                JSONArray entries=data.optJSONArray("entries");if(entries!=null)for(int i=0;i<entries.length();i++){
                    JSONObject entry=entries.getJSONObject(i);String name=entry.optString("moduleName","null"),pkg=entry.isNull("packageName")?ui.s("system_package_unspecified"):entry.optString("packageName");String info=pkg+(entry.has("status")?ui.s("system_record_status",String.valueOf(entry.get("status"))):"")+" · "+entry.optLong("rawBytes")+" B";
                    rows.add(new NativeUi.Row(name,entry.optBoolean("retrievable")?ui.s("system_view"):ui.s("system_unsupported_record"),()->{if(!entry.optBoolean("retrievable")){ui.toast(entry.has("unavailableReason")?diagnostic(entry,"unavailableReason"):ui.s("system_unsafe_record"));return;}systemRule(entry,offset,filter);}).game(info));
                }
                if(rows.isEmpty())rows.add(new NativeUi.Row(ui.s("system_no_records"),"",null));adapter.set(rows);int count=entries==null?0:entries.length();long total=data.optLong("total");
                if(offset>0)ui.equal(pages,ui.textButton(ui.s("action_previous_page"),()->systemLibrary(Math.max(0,offset-30),filter)));
                ui.equal(pages,ui.textButton((count==0?0:offset+1)+"–"+(offset+count)+" / "+total,()->{}));if(data.optBoolean("hasMore"))ui.equal(pages,ui.textButton(ui.s("action_next_page"),()->systemLibrary(offset+count,filter)));
            });}));
        }
        void systemRule(JSONObject info,int offset,String filter){safe(()->{
            long screen=++systemScreen;JSONObject request=new JSONObject().put("sourceId",info.optString("sourceId",systemSource)).put("id",info.get("id")).put("packageName",info.get("packageName")).put("moduleName",info.get("moduleName"));if(info.has("rowLocator"))request.put("rowLocator",info.get("rowLocator"));
            call("system-rule",request,result->{if(screen!=systemScreen||detailBack==null)return;safe(()->{JSONObject data=(JSONObject)result;if(data.has("error")){ui.toast(diagnostic(data.getJSONObject("error"),"message"));return;}systemPreview(data.getJSONObject("entry"),offset,filter);});});
        });}
        void systemPreview(JSONObject entry,int offset,String filter)throws Exception{
            List<NativeUi.Row> rows=new ArrayList<>();JSONObject parsed=entry.getJSONObject("productData");
            rows.add(new NativeUi.Row(ui.s("system_package"),entry.isNull("packageName")?ui.s("system_package_unspecified"):entry.optString("packageName"),null));rows.add(new NativeUi.Row(ui.s("system_module"),entry.optString("moduleName","null"),null));rows.add(new NativeUi.Row(ui.s("system_data_type"),ui.s("system_data_type_depth",parsed.optString("type"),parsed.optInt("encodingDepth")),null));
            rows.add(new NativeUi.Row(ui.s("system_fingerprint"),entry.getString("id").substring(0,16)+"…",()->showText(ui.s("system_fingerprint"),entry.optString("id"),()->safe(()->systemPreview(entry,offset,filter)))));
            if(parsed.has("error"))rows.add(new NativeUi.Row(diagnostic(parsed,"error"),ui.s("system_preserve_raw"),null));if(parsed.has("warning"))rows.add(new NativeUi.Row(diagnostic(parsed,"warning"),"",null));
            rows.add(new NativeUi.Row(ui.s("system_view_source"),entry.optLong("rawBytes")+" B ›",()->showText(ui.s("system_raw_encoding",entry.optString("rawEncoding")),entry.optString("rawData","null"),()->safe(()->systemPreview(entry,offset,filter)))));
            rows.add(new NativeUi.Row(ui.s("system_view_metadata"),ui.s("system_readonly_arrow"),()->safe(()->readOnlyTree(ui.s("system_metadata"),entry.getJSONObject("metadata"),()->safe(()->systemPreview(entry,offset,filter)),true))));
            cloudDetail(entry.optString("moduleName",ui.s("system_cloud_short")),ui.list(rows),()->systemLibrary(offset,filter),new NativeUi.Row(ui.s("system_edit_draft"),"primary",()->safe(()->systemDraft(entry,offset,filter))));
        }
        void showText(String label,String value,Runnable parent){TextView text=ui.text(value,14,NativeUi.INK);text.setTextIsSelectable(true);ui.pad(text,16);ScrollView scroll=new ScrollView(ManagerActivity.this);scroll.addView(text);cloudDetail(label,scroll,parent);}
        File systemDraftFile(){return new File(getFilesDir(),"system-cloud-draft.json");}
        void systemDraft(JSONObject entry,int offset,String filter)throws Exception{
            JSONObject parsed=entry.getJSONObject("productData");Object sourceValue=parsed.get("value");if(sourceValue instanceof JSONObject||sourceValue instanceof JSONArray)sourceValue=Protocol.strictJson(sourceValue.toString());JSONObject wrapper=new JSONObject().put("value",sourceValue);boolean restored=false;
            if(systemDraftFile().isFile())try{JSONObject previous=new JSONObject(Protocol.read(systemDraftFile()));if(entry.getString("id").equals(previous.optString("id"))){wrapper=previous.getJSONObject("draft");restored=true;}}catch(Exception ignored){}
            final JSONObject draft=wrapper;final boolean[] localDirty={false};Runnable parent=()->{Runnable leave=()->safe(()->systemPreview(entry,offset,filter));if(localDirty[0])ui.confirm(ui.s("system_confirm_discard"),leave);else leave.run();};
            NativeUi.Row save=new NativeUi.Row(ui.s("system_save_draft"),"primary",()->safe(()->{String text=new JSONObject().put("id",entry.getString("id")).put("packageName",entry.get("packageName")).put("moduleName",entry.get("moduleName")).put("sourceEncodingDepth",parsed.optInt("encodingDepth")).put("sourceType",parsed.optString("type")).put("draft",draft).toString();Protocol.strictJson(text);Protocol.atomic(systemDraftFile(),text);localDirty[0]=false;ui.toast(ui.s("system_draft_saved"));}));
            new JsonValueEditor(ui,ManagerActivity.this::cloudDetail,()->localDirty[0]=true,save).open(ui.s("system_draft_title",entry.optString("moduleName")),draft,parent);if(restored)ui.toast(ui.s("system_draft_restored"));
        }
        void about(){
            LinearLayout body=ui.column();ui.pad(body,20);
            TextView name=ui.text(ui.s("app_name"),26,NativeUi.INK);ui.strong(name);ui.margin(body,name,12);
            ui.margin(body,ui.text("JoyoseLeash",16,NativeUi.GRAY),10);
            TextView version=ui.text("0.3.3 Beta",16,NativeUi.GRAY);ui.margin(body,version,12);ui.gap(body,24);ui.line(body);
            body.addView(ui.label(ui.s("about_author"),ui.text("沐月、ChatGPT",16,NativeUi.INK)));ui.line(body);
            body.addView(ui.label(ui.s("about_license"),ui.text("GPL-2.0-only",16,NativeUi.INK)));ui.line(body);
            ui.margin(body,ui.text(ui.s("about_source"),16,NativeUi.INK),20);
            String source="https://github.com/imuyue/JoyoseLeash";
            TextView link=ui.textButton(source,()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(source)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException e){ui.toast(ui.s("about_no_browser"));}});link.setTextSize(15);link.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);link.setPadding(0,ui.dp(12),0,ui.dp(12));link.setContentDescription(ui.s("a11y_open_source",source));body.addView(link,new LinearLayout.LayoutParams(-1,-2));
            ui.gap(body,20);ui.line(body);
            TextView warning=ui.text(ui.s("about_beta_warning"),16,NativeUi.ERROR);warning.setLineSpacing(ui.dp(5),1);ui.margin(body,warning,24);
            ui.gap(body,24);ui.line(body);
            ui.margin(body,ui.text(ui.s("about_joyctl_notice"),16,NativeUi.INK),20);
            body.addView(sourceLink("https://github.com/hexwander/joyctl"));
            ui.margin(body,ui.text("Copyright (c) 2026 JoyCtl Contributors\nLicensed under the MIT License.",15,NativeUi.INK),8);
            ui.margin(body,ui.text(ui.s("about_mit_full"),16,NativeUi.INK),20);
            body.addView(sourceLink("https://github.com/hexwander/joyctl/blob/main/LICENSE"));
            TextView license=ui.textButton(ui.s("about_view_mit"),()->safe(this::joyctlLicense));license.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);license.setPadding(0,ui.dp(12),0,ui.dp(12));body.addView(license);
            ScrollView scroll=new ScrollView(ManagerActivity.this);scroll.addView(body);detail(ui.s("settings_about"),scroll,ManagerActivity.this::renderHome);
        }
        TextView sourceLink(String url){TextView link=ui.textButton(url,()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException e){ui.toast(ui.s("about_no_browser"));}});link.setTextSize(15);link.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);link.setPadding(0,ui.dp(12),0,ui.dp(12));link.setContentDescription(ui.s("a11y_open_link",url));return link;}
        void joyctlLicense()throws Exception{TextView text=ui.text(Protocol.read(getAssets().open("licenses/JoyCtl-MIT.txt")),16,NativeUi.INK);text.setTextIsSelectable(true);text.setLineSpacing(ui.dp(4),1);ui.pad(text,20);ScrollView scroll=new ScrollView(ManagerActivity.this);scroll.addView(text);detail(ui.s("about_mit_title"),scroll,this::about);}
        void servers(){safe(()->{List<NativeUi.Row> rows=new ArrayList<>();for(int i=0;i<domains.length();i++){final int index=i;String name=domains.getString(i);rows.add(new NativeUi.Row(name,ui.s("server_edit"),()->ui.edit(ui.s("server_title"),name,TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,false,value->{domains.put(index,value.trim());netDirty=true;servers();}),()->ui.confirm(ui.s("server_confirm_remove"),()->{domains.remove(index);netDirty=true;servers();})));}detail(ui.s("settings_servers"),ui.list(rows),ManagerActivity.this::renderHome,new NativeUi.Row(ui.s("server_add"),"primary",()->ui.edit(ui.s("server_address"),"",TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,false,value->{domains.put(value.trim());netDirty=true;servers();})));});}
        void otherModules(){safe(()->{List<NativeUi.Row> rows=new ArrayList<>();for(Object key:RuleModel.keys(profiles)){String name=key.toString();rows.add(new NativeUi.Row(moduleName(name),String.valueOf(profiles.getJSONObject(name).getLong("version")),()->safe(()->{module=name;List<NativeUi.Row> fields=Arrays.asList(new NativeUi.Row(ui.s("field_version"),String.valueOf(profile().getLong("version")),()->editVersion(name,this::otherModules)),new NativeUi.Row(ui.s("library_edit_fields"),"›",()->safe(()->tree(moduleName(name),profile().getJSONObject("params"),this::otherModules))));detail(moduleName(name),ui.list(fields),this::otherModules,new NativeUi.Row(ui.s("action_apply_changes"),"primary",this::commit));})));}detail(ui.s("settings_other_libraries"),ui.list(rows),ManagerActivity.this::renderHome,new NativeUi.Row(ui.s("library_new"),"primary",()->ui.edit(ui.s("library_name"),"",TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,false,name->{if(!name.matches("[A-Za-z0-9_.-]{1,80}")||profiles.has(name))throw ui.failure("error_invalid_library_name");profiles.put(name,new JSONObject().put("version",1).put("params",new JSONObject()));module=name;changed();tree(name,profile().getJSONObject("params"),this::otherModules);})));});}
    }
}
