package com.muyue.joyoseleash;

import android.text.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Local-only, schema-independent JSON editing. No Root or cloud write capability. */
final class JsonValueEditor {
    interface Host {void show(String title,android.view.View body,Runnable back,NativeUi.Row... actions);}
    interface Changed {void accept()throws Exception;}
    private final NativeUi ui;private final Host host;private final Changed changed;private final NativeUi.Row[] actions;
    private static final int TEXT=android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
    private enum Action {
        RENAME("editor_action_rename"), NAME_ITEM("editor_action_name_item"), EXPAND("editor_action_expand"),
        MOVE_UP("editor_action_move_up"), MOVE_DOWN("editor_action_move_down"), CHANGE_TYPE("editor_action_change_type"), DELETE("editor_action_delete");
        final String resource;Action(String resource){this.resource=resource;}
    }
    JsonValueEditor(NativeUi ui,Host host,Changed changed,NativeUi.Row... actions){this.ui=ui;this.host=host;this.changed=changed;this.actions=actions;}
    void open(String label,Object container,Runnable back){page(label,container,back,"",0);}
    private void fail(Exception e){ui.toast(ui.error(e));}
    private void page(String label,Object data,Runnable back,String filter,int offset){try{
        LinearLayout body=ui.column();
        EditText search=ui.input(filter,ui.s("editor_json_search"),android.text.InputType.TYPE_CLASS_TEXT);search.setSingleLine(true);body.addView(search);
        NativeUi.Rows adapter=ui.new Rows();body.addView(ui.list(adapter),new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=ui.row();body.addView(controls);
        Runnable redraw=()->page(label,data,back,search.getText().toString(),offset);
        Runnable fill=()->{try{
            String q=search.getText().toString().toLowerCase(Locale.ROOT);List<Object> matches=new ArrayList<>();
            for(Object key:RuleModel.keys(data)){RuleModel.Field field=RuleModel.field(data,key);if((key+" "+field.name+" "+kind(field.value)+" "+kindLabel(field.value)+" "+String.valueOf(field.value)).toLowerCase(Locale.ROOT).contains(q))matches.add(key);}
            int start=q.equals(filter)?Math.min(offset,Math.max(0,((matches.size()-1)/60)*60)):0,end=Math.min(start+60,matches.size());List<NativeUi.Row> rows=new ArrayList<>();
            for(int i=start;i<end;i++){Object key=matches.get(i);RuleModel.Field field=RuleModel.field(data,key);Object value=field.value;String name=key instanceof Integer&&!field.namedItem?"["+key+"]":field.name;
                rows.add(new NativeUi.Row(name,ui.s("editor_json_value_summary",kindLabel(value),ui.summary(value)),()->edit(name,field.parent,field.key,redraw),()->options(data,key,redraw,newName->page(label,data,back,newName,0))).typed(value).field(field.name));
            }
            if(rows.isEmpty())rows.add(new NativeUi.Row(ui.s("editor_json_no_matches"),"",null));adapter.set(rows);controls.removeAllViews();
            if(start>0)ui.equal(controls,ui.textButton(ui.s("editor_previous_page"),()->page(label,data,back,q,start-60)));
            ui.equal(controls,ui.textButton(ui.s("editor_page_range",matches.isEmpty()?0:start+1,end,matches.size()),()->{}));
            if(end<matches.size())ui.equal(controls,ui.textButton(ui.s("editor_next_page"),()->page(label,data,back,q,end)));
        }catch(Exception e){fail(e);}};
        fill.run();search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){fill.run();}public void afterTextChanged(Editable e){}});
        LinearLayout tools=ui.row();ui.equal(tools,ui.textButton(ui.s("editor_json_add_field"),()->add(data,newName->page(label,data,back,newName,0))));ui.equal(tools,ui.textButton(ui.s("editor_json_view_raw"),()->raw(label,data,redraw)));body.addView(tools);
        host.show(label,body,back,actions);
    }catch(Exception e){fail(e);}}
    private static String kind(Object value){return value==JSONObject.NULL?"null":value instanceof JSONObject?"object":value instanceof JSONArray?"array":value instanceof Boolean?"boolean":value instanceof Number?"number":"string";}
    private String kindLabel(Object value){return ui.s("editor_type_"+kind(value));}
    private void edit(String label,Object parent,Object key,Runnable redraw){try{
        Object value=RuleModel.get(parent,key);
        if(value instanceof JSONObject||value instanceof JSONArray){open(label,value,redraw);return;}
        if(value instanceof Boolean){RuleModel.put(parent,key,!((Boolean)value));changed.accept();redraw.run();return;}
        if(value==JSONObject.NULL){chooseType(parent,key,redraw);return;}
        if(value instanceof String){
            RuleModel.Encoded encoded=RuleModel.encoded((String)value);
            if(encoded!=null){ui.choose(label,Arrays.asList(ui.s("editor_json_edit_embedded"),ui.s("editor_json_edit_raw_string")),which->{if(which==0){JsonValueEditor nested=new JsonValueEditor(ui,host,()->{RuleModel.put(parent,key,encoded.encode());changed.accept();},actions);nested.open(ui.s("editor_json_embedded_title",label),encoded.value,redraw);}else scalar(label,parent,key,value,redraw);});return;}
            StringRules.Document parsed=StringRules.parse(key.toString(),(String)value);
            if(parsed!=null){StringRuleEditor editor=new StringRuleEditor(ui,parsed,text->{RuleModel.put(parent,key,text);changed.accept();});host.show(label,editor.view,redraw,actions);return;}
        }
        scalar(label,parent,key,value,redraw);
    }catch(Exception e){fail(e);}}
    private void scalar(String label,Object parent,Object key,Object value,Runnable redraw){ui.edit(label,String.valueOf(value),value instanceof Number?android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL:TEXT,!(value instanceof Number),text->{RuleModel.put(parent,key,value instanceof Number?RuleModel.number(text):text);changed.accept();redraw.run();});}
    private void safe(Changed action){try{action.accept();}catch(Exception e){fail(e);}}
    private void types(java.util.function.IntConsumer selected){ui.choose(ui.s("editor_json_field_type"),Arrays.asList(ui.s("editor_type_string"),ui.s("editor_type_number"),ui.s("editor_type_boolean"),ui.s("editor_type_object"),ui.s("editor_type_array"),ui.s("editor_type_null")),selected);}
    private void chooseType(Object data,Object key,Runnable redraw){types(type->safe(()->{if(type==RuleModel.type(RuleModel.get(data,key)))return;ui.confirm(ui.s("editor_json_replace_type_warning"),ui.s("editor_json_replace_type"),()->safe(()->{RuleModel.put(data,key,RuleModel.defaultValue(type));changed.accept();redraw.run();}));}));}
    private void add(Object data,java.util.function.Consumer<String> reveal){
        if(data instanceof JSONArray){ui.choose(ui.s("editor_json_add_item"),Arrays.asList(ui.s("editor_json_named_item"),ui.s("editor_json_unnamed_item")),which->{if(which==0)ui.edit(ui.s("editor_json_original_name"),"",android.text.InputType.TYPE_CLASS_TEXT,false,name->{RuleModel.validName(name);addValue(data,name,true,reveal);});else addValue(data,"",false,reveal);});return;}
        ui.edit(ui.s("editor_json_original_name"),"",android.text.InputType.TYPE_CLASS_TEXT,false,name->{RuleModel.validName(name);if(((JSONObject)data).has(name))throw ui.failure("editor_json_field_exists");addValue(data,name,false,reveal);});
    }
    private void addValue(Object data,String name,boolean named,java.util.function.Consumer<String> reveal){types(type->safe(()->{Object key=data instanceof JSONObject?name:((JSONArray)data).length();Object value=RuleModel.defaultValue(type);RuleModel.put(data,key,named?RuleModel.named(name,value):value);changed.accept();reveal.accept(named||data instanceof JSONObject?name:key.toString());}));}
    private void options(Object data,Object key,Runnable redraw,java.util.function.Consumer<String> reveal){safe(()->{
        RuleModel.Field field=RuleModel.field(data,key);List<Action> choices=new ArrayList<>();choices.add(data instanceof JSONObject||field.namedItem?Action.RENAME:Action.NAME_ITEM);if(field.namedItem)choices.add(Action.EXPAND);if(data instanceof JSONArray){choices.add(Action.MOVE_UP);choices.add(Action.MOVE_DOWN);}choices.add(Action.CHANGE_TYPE);choices.add(Action.DELETE);
        List<String> labels=new ArrayList<>();for(Action choice:choices)labels.add(ui.s(choice.resource));
        ui.choose(field.namedItem?field.name:key.toString(),labels,index->safe(()->{Action choice=choices.get(index);
            if(choice==Action.RENAME)ui.edit(ui.s("editor_json_original_name"),field.name,android.text.InputType.TYPE_CLASS_TEXT,false,name->{if(RuleModel.rename((JSONObject)field.parent,field.name,name)){changed.accept();reveal.accept(name);}});
            else if(choice==Action.NAME_ITEM)ui.edit(ui.s("editor_json_original_name"),"",android.text.InputType.TYPE_CLASS_TEXT,false,name->{RuleModel.validName(name);ui.confirm(ui.s("editor_json_named_conversion_warning"),ui.s("editor_json_convert"),()->safe(()->{RuleModel.put(data,key,RuleModel.named(name,RuleModel.get(data,key)));changed.accept();reveal.accept(name);}));});
            else if(choice==Action.EXPAND)open(field.name,RuleModel.get(data,key),redraw);
            else if(choice==Action.CHANGE_TYPE)chooseType(field.parent,field.key,redraw);
            else if(choice==Action.DELETE)ui.confirm(ui.s("editor_json_delete_warning"),()->safe(()->{RuleModel.remove(data,key);changed.accept();redraw.run();}));
            else{int from=(Integer)key,to=from+(choice==Action.MOVE_UP?-1:1);if(to>=0&&to<((JSONArray)data).length()){RuleModel.move((JSONArray)data,from,to);changed.accept();redraw.run();}}
        }));
    });}
    private void raw(String label,Object data,Runnable back){TextView text=ui.text(data.toString(),14,NativeUi.INK);text.setTextIsSelectable(true);ui.pad(text,16);ScrollView scroll=new ScrollView(ui.activity);scroll.addView(text);host.show(ui.s("editor_json_raw_title",label),scroll,back,actions);}
}
