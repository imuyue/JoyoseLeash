package com.muyue.joyoseleash;

import android.app.Dialog;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Local native rows; a single token edit cannot rewrite unrelated rule segments. */
final class StringRuleEditor {
    static final int PAGE_SIZE=40;
    interface Save {void accept(String value)throws Exception;}
    final NativeUi ui;final Save save;final LinearLayout view;final NativeUi.Rows adapter;
    StringRules.Document document;
    int open,sectionPage,fieldPage;
    StringRuleEditor(NativeUi ui,StringRules.Document document,Save save){
        this.ui=ui;this.document=document;this.save=save;view=ui.column();adapter=ui.new Rows();
        view.addView(ui.list(adapter),new LinearLayout.LayoutParams(-1,0,1));render();
    }
    private String caption(Messages.Text text,String raw){return text==null?raw:ui.message(text);}
    void render(){
        List<NativeUi.Row> rows=new ArrayList<>();
        rows.add(new NativeUi.Row(caption(document.caption,document.format),ui.q("editor_rule_editable_fields",document.fieldCount(),document.fieldCount()),null));
        rows.add(new NativeUi.Row(ui.s("editor_rule_view_raw"),ui.q("editor_rule_characters",document.raw.length(),document.raw.length()),()->showText(ui.s("editor_rule_raw_title"),document.raw)));
        rows.add(new NativeUi.Row(ui.s("editor_rule_edit_raw"),"",this::editRaw));
        if(document.unparsed>0)rows.add(new NativeUi.Row(ui.s("editor_rule_unparsed"),ui.q("editor_rule_preserved_fragments",document.unparsed,document.unparsed),()->showUnparsed(0)));
        int count=document.sections.size();sectionPage=Math.min(sectionPage,Math.max(0,(count-1)/PAGE_SIZE));
        int first=sectionPage*PAGE_SIZE,last=Math.min(count,first+PAGE_SIZE);
        if(first>0)rows.add(new NativeUi.Row(ui.s("editor_rule_previous_sections"),ui.s("editor_page_position",first,count),()->{sectionPage--;open=-1;render();}));
        for(int i=first;i<last;i++){
            final int index=i;StringRules.Section section=document.sections.get(i);String state=ui.s(open==i?"editor_rule_collapse":"editor_rule_expand");
            rows.add(new NativeUi.Row(caption(section.caption,section.title),ui.q("editor_rule_section_items",section.fields.size(),state,section.fields.size()),()->{open=open==index?-1:index;fieldPage=0;render();}));
            if(open==i){
                int fields=section.fields.size();fieldPage=Math.min(fieldPage,Math.max(0,(fields-1)/PAGE_SIZE));int start=fieldPage*PAGE_SIZE,end=Math.min(fields,start+PAGE_SIZE);
                if(start>0)rows.add(new NativeUi.Row(ui.s("editor_rule_previous_fields"),ui.s("editor_page_position",start,fields),()->{fieldPage--;render();}));
                for(int j=start;j<end;j++){StringRules.Field field=section.fields.get(j);rows.add(new NativeUi.Row(caption(field.caption,field.label),field.value,()->edit(field)));}
                if(end<fields)rows.add(new NativeUi.Row(ui.s("editor_rule_next_fields"),ui.s("editor_page_position_next",end,fields),()->{fieldPage++;render();}));
            }
        }
        if(last<count)rows.add(new NativeUi.Row(ui.s("editor_rule_next_sections"),ui.s("editor_page_position_next",last,count),()->{sectionPage++;open=-1;render();}));
        adapter.set(rows);
    }
    void editRaw(){
        ui.edit(ui.s("editor_rule_edit_raw_title"),document.raw,android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE,true,value->{
            StringRules.Document next=StringRules.parse(document.key,value);
            // Raw editing is an escape hatch, not a promise that an unknown format is valid.
            save.accept(value);document=next==null?StringRules.raw(document.key,value):next;open=0;sectionPage=fieldPage=0;render();
        });
    }
    void showText(String title,String value){
        LinearLayout content=ui.column();ScrollView scroll=new ScrollView(ui.activity);TextView text=ui.text(value.isEmpty()?ui.s("editor_rule_empty_fragment"):value,15,NativeUi.INK);text.setTextIsSelectable(true);ui.pad(text,12);scroll.addView(text);content.addView(scroll,new LinearLayout.LayoutParams(-1,ui.dp(280)));
        Dialog dialog=ui.dialog(title,content);ui.margin(content,ui.textButton(ui.s("editor_close"),dialog::dismiss),12);dialog.show();
    }
    void showUnparsed(int page){
        LinearLayout content=ui.column();List<NativeUi.Row> rows=new ArrayList<>();int first=page*PAGE_SIZE,last=Math.min(document.fragments.size(),first+PAGE_SIZE);
        for(int i=first;i<last;i++){
            StringRules.Fragment fragment=document.fragments.get(i);String value=document.raw.substring(fragment.start,fragment.end);
            rows.add(new NativeUi.Row(ui.s("editor_rule_fragment_label",i+1,caption(fragment.caption,fragment.reason)),ui.s("editor_rule_fragment_position",fragment.start,fragment.end),()->showText(ui.s("editor_rule_preserved_position",fragment.start,fragment.end),value)));
        }
        content.addView(ui.list(rows),new LinearLayout.LayoutParams(-1,ui.dp(280)));Dialog dialog=ui.dialog(ui.s("editor_rule_fragment_title"),content);LinearLayout actions=ui.row();
        if(first>0)ui.equal(actions,ui.textButton(ui.s("editor_previous_page"),()->{dialog.dismiss();showUnparsed(page-1);}));
        ui.equal(actions,ui.textButton(ui.s("editor_close"),dialog::dismiss));
        if(last<document.fragments.size())ui.equal(actions,ui.textButton(ui.s("editor_next_page"),()->{dialog.dismiss();showUnparsed(page+1);}));
        ui.margin(content,actions,12);dialog.show();
    }
    void edit(StringRules.Field field){
        int type=field.type==StringRules.TEXT||field.type==StringRules.PACKAGE||field.type==StringRules.CLASS_NAME?android.text.InputType.TYPE_CLASS_TEXT:android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;
        ui.edit(caption(field.caption,field.label),field.value,type,false,value->{String text=document.replace(field,value);StringRules.Document next=StringRules.parse(document.key,text);if(next==null)throw ui.failure("editor_rule_invalid_format");save.accept(text);document=next;render();});
    }
}
