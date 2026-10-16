package com.muyue.joyoseleash;

import android.view.*;
import android.widget.*;
import java.util.*;

/** Local native rows; a single token edit cannot rewrite unrelated rule segments. */
final class StringRuleEditor {
    interface Save {void accept(String value)throws Exception;}
    final NativeUi ui;final Save save;final LinearLayout view;final NativeUi.Rows adapter;
    StringRules.Document document;
    int open;
    StringRuleEditor(NativeUi ui,StringRules.Document document,Save save){
        this.ui=ui;this.document=document;this.save=save;view=ui.column();adapter=ui.new Rows();
        view.addView(ui.list(adapter),new LinearLayout.LayoutParams(-1,0,1));render();
    }
    void render(){
        List<NativeUi.Row> rows=new ArrayList<>();
        for(int i=0;i<document.sections.size();i++){
            final int index=i;StringRules.Section section=document.sections.get(i);
            rows.add(new NativeUi.Row(section.title,(open==i?"收起":"展开")+" ›",()->{open=open==index?-1:index;render();}));
            if(open==i)for(StringRules.Field field:section.fields)rows.add(new NativeUi.Row(field.label,field.value,()->edit(field)));
        }
        if(document.unparsed>0)rows.add(new NativeUi.Row("保留原样",document.unparsed+" 个片段",null));
        rows.add(new NativeUi.Row("原始字符串","›",()->ui.edit("原始字符串",document.raw,android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE,true,value->{
            StringRules.Document next=StringRules.parse(document.key,value);if(next==null)throw new Exception("此格式无法分项编辑");save.accept(value);document=next;render();
        })));adapter.set(rows);
    }
    void edit(StringRules.Field field){
        int type=field.type==StringRules.TEXT||field.type==StringRules.PACKAGE?android.text.InputType.TYPE_CLASS_TEXT:android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;
        ui.edit(field.label,field.value,type,false,value->{String text=document.replace(field,value);StringRules.Document next=StringRules.parse(document.key,text);if(next==null)throw new Exception("规则格式无效");save.accept(text);document=next;render();});
    }
}
