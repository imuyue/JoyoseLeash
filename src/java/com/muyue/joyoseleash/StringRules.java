/*
 * SPDX-License-Identifier: MIT
 * Portions adapted from JoyCtl src/lib/comments.js (PID pattern and MIGT split logic).
 * Copyright (c) 2026 JoyCtl Contributors
 * https://github.com/hexwander/joyctl/tree/b16187b2c49bd357d9704926ef9d401ccb3ca18b
 * The original MIT notice is in licenses/JoyCtl-MIT.txt.
 * Native token editing and lossless serialization added by JoyoseLeash.
 */
package com.muyue.joyoseleash;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Only recognized spans are editable. All other characters and delimiters survive. */
final class StringRules {
    static final String NUM="[-+]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)";
    static final Pattern NUMBERS=Pattern.compile(NUM);
    static final Pattern PID_ROW=Pattern.compile("\\s*"+NUM+"\\s*:\\s*"+NUM+"(?:\\s+"+NUM+"){4,5}\\s*");
    static final Pattern MAP_TOKEN=Pattern.compile("([0-9]+):("+NUM+")");
    static final Pattern WORD=Pattern.compile("\\S+");
    static final int TEXT=0,PACKAGE=1,INTEGER=2,NUMBER=3,TEMP=4,FPS=5,NONNEGATIVE=6,CLASS_NAME=7;

    static final class Field {
        final String label,value;final int start,end,type;final Messages.Text caption;
        Field(String label,String value,int start,int end,int type){this(label,value,start,end,type,null);}
        Field(String label,String value,int start,int end,int type,Messages.Text caption){this.label=label;this.value=value;this.start=start;this.end=end;this.type=type;this.caption=caption;}
        String checked(String input){
            if(input==null)throw new Messages.Failure(Messages.text("diag_content_required","内容不能为空"));
            // Check before trim: control characters must not disappear silently on save.
            for(int i=0;i<input.length();i++)if(Character.isISOControl(input.charAt(i)))throw new Messages.Failure(Messages.text("diag_control_characters_forbidden","参数不能包含控制字符或换行"));
            String text=input.trim();
            if(text.isEmpty())throw new Messages.Failure(Messages.text("diag_content_required","内容不能为空"));
            if(type==TEXT){if(text.indexOf(';')>=0||text.indexOf('#')>=0)throw new Messages.Failure(Messages.text("diag_parameter_delimiters_forbidden","参数不能包含分号或井号"));return text;}
            if(type==PACKAGE){if(!text.matches("[A-Za-z0-9_.#-]{1,300}"))throw new Messages.Failure(Messages.text("diag_package_or_group_invalid","应用包名或分组名无效"));return text;}
            if(type==CLASS_NAME){if(!text.matches("[A-Za-z_$][A-Za-z0-9_.$]{0,299}"))throw new Messages.Failure(Messages.text("diag_activity_class_invalid","Activity 类名无效"));return text;}
            if(text.length()>40||!text.matches(NUM))throw new Messages.Failure(Messages.text("diag_number_invalid","请输入有效数字"));
            BigDecimal number=new BigDecimal(text);
            if(!Double.isFinite(number.doubleValue()))throw new Messages.Failure(Messages.text("diag_number_out_of_range","数字超出范围"));
            if(type==INTEGER||type==FPS){
                if(!text.matches("[0-9]+"))throw new Messages.Failure(Messages.text("diag_nonnegative_integer_required","请输入非负整数"));
                if(number.compareTo(BigDecimal.valueOf(type==FPS?1000:Integer.MAX_VALUE))>0)throw new Messages.Failure(type==FPS?Messages.text("diag_fps_out_of_range","帧率须为0至1000"):Messages.text("diag_integer_out_of_range","整数超出范围"));
            }
            if(type==TEMP&&(number.compareTo(BigDecimal.valueOf(-100))<0||number.compareTo(BigDecimal.valueOf(200))>0))throw new Messages.Failure(Messages.text("diag_temperature_out_of_range","温度须为-100至200℃"));
            if(type==NONNEGATIVE&&number.signum()<0)throw new Messages.Failure(Messages.text("diag_nonnegative_number_required","数值不能为负数"));
            return text;
        }
    }
    static final class Section {
        final String title;final Messages.Text caption;final List<Field> fields=new ArrayList<>();
        Section(String title,Messages.Text caption){this.title=title;this.caption=caption;}
    }
    static final class Fragment {
        final int start,end;final String reason;final Messages.Text caption;
        Fragment(int start,int end,String reason,Messages.Text caption){this.start=start;this.end=end;this.reason=reason;this.caption=caption;}
    }
    static final class Document {
        final String key,raw,format;final Messages.Text caption;
        final List<Section> sections=new ArrayList<>();
        final List<Field> groupRates=new ArrayList<>(),temperatures=new ArrayList<>(),scaleDpis=new ArrayList<>();
        final List<Fragment> fragments=new ArrayList<>();
        int unparsed;
        Document(String key,String raw,String format,Messages.Text caption){this.key=key;this.raw=raw;this.format=format;this.caption=caption;}
        void unknown(Part part,String reason,Messages.Text caption){unknown(part.start,part.start+part.text.length(),reason,caption);}
        void unknown(int start,int end,String reason,Messages.Text caption){fragments.add(new Fragment(start,end,reason,caption));unparsed=fragments.size();}
        String replace(Field field,String input){
            boolean belongs=false;for(Section section:sections)if(section.fields.contains(field))belongs=true;
            if(!belongs)throw new Messages.Failure(Messages.text("diag_field_not_in_rule","字段不属于当前规则"));
            String replacement=field.checked(input);
            if(replacement.equals(field.value))return raw;
            List<Field> unique=groupRates.contains(field)?groupRates:scaleDpis.contains(field)?scaleDpis:null;
            if(unique!=null)for(Field other:unique)if(other!=field&&new BigDecimal(other.value).compareTo(new BigDecimal(replacement))==0)throw new Messages.Failure(unique==groupRates?Messages.text("diag_strategy_fps_duplicate","策略帧率重复"):Messages.text("diag_dpi_duplicate","DPI 档位重复"));
            int index=temperatures.indexOf(field);
            if(index>=0){
                BigDecimal value=new BigDecimal(replacement);
                if((index>0&&value.compareTo(new BigDecimal(temperatures.get(index-1).value))<=0)||(index+1<temperatures.size()&&value.compareTo(new BigDecimal(temperatures.get(index+1).value))>=0))throw new Messages.Failure(Messages.text("diag_temperature_neighbors_invalid","温度档须严格递增，不能重复或跨越相邻档位"));
            }
            return raw.substring(0,field.start)+replacement+raw.substring(field.end);
        }
        int fieldCount(){int count=0;for(Section s:sections)count+=s.fields.size();return count;}
    }
    static final class Part {
        final String text;final int start;
        Part(String text,int start){this.text=text;this.start=start;}
    }
    static List<Part> split(String text,char separator,int offset){
        List<Part> result=new ArrayList<>();int from=0;
        for(int i=0;i<=text.length();i++)if(i==text.length()||text.charAt(i)==separator){result.add(new Part(text.substring(from,i),offset+from));from=i+1;}
        return result;
    }
    static Field field(Section section,String label,String raw,int start,int end,int type,Messages.Text caption){
        while(start<end&&Character.isWhitespace(raw.charAt(start)))start++;
        while(end>start&&Character.isWhitespace(raw.charAt(end-1)))end--;
        Field field=new Field(label,raw.substring(start,end),start,end,type,caption);field.checked(field.value);section.fields.add(field);return field;
    }
    static Field field(Section section,String label,String raw,Part part,int type,Messages.Text caption){return field(section,label,raw,part.start,part.start+part.text.length(),type,caption);}
    static void add(Document doc,Section section){if(!section.fields.isEmpty())doc.sections.add(section);}
    static Document raw(String key,String text){Document doc=new Document(key,text,"原始字符串",Messages.text("parser_raw_string","原始字符串"));doc.unknown(0,text.length(),"没有可安全分项编辑的字段，请使用原始字符串编辑",Messages.text("parser_raw_edit_required","没有可安全分项编辑的字段，请使用原始字符串编辑"));return doc;}

    static Document parse(String key,String text){
        if(key==null||text==null||text.length()>262144)return null;
        try{
            Document doc;
            switch(key){
                case "PID_M":case "PID_T":doc=pid(key,text);break;
                case "migt":doc=migt(key,text);break;
                case "recommend_tgame_thresh":doc=pair(key,text,'#',"TGAME推荐阈值","温度（℃）",TEMP,"帧率",FPS,Messages.text("parser_tgame_recommended_threshold","TGAME推荐阈值"),Messages.text("parser_temperature_celsius","温度（℃）"),Messages.text("parser_fps","帧率"));break;
                case "support_highfps_app":case "novatek_gex_fps_limit":doc=pair(key,text,':',"应用帧率","应用 / 分组",PACKAGE,"帧率",FPS,Messages.text("parser_application_fps","应用帧率"),Messages.text("parser_application_or_group","应用 / 分组"),Messages.text("parser_fps","帧率"));break;
                case "support_scale_app_list":doc=text.indexOf(':')<0?packageName(key,text):pair(key,text,':',"渲染比例","应用 / 分组",PACKAGE,"缩放比例",NONNEGATIVE,Messages.text("parser_render_scale","渲染比例"),Messages.text("parser_application_or_group","应用 / 分组"),Messages.text("parser_scale_ratio","缩放比例"));break;
                case "force_scale_app_list":doc=forceScale(key,text);break;
                case "reset_game_min_duration":doc=pair(key,text,':',"游戏时长","应用 / 分组",PACKAGE,"时长（ms）",INTEGER,Messages.text("parser_game_duration","游戏时长"),Messages.text("parser_application_or_group","应用 / 分组"),Messages.text("parser_duration_ms","时长（ms）"));break;
                case "dynamic_fps":case "dynamic_fps_M":case "dynamic_fps_multiWin":case "dynamic_fps_multiWin_M":doc=temperature(key,text);break;
                case "game_list":case "package_list":case "support_dynamic_refresh_rate_games":case "support_motor_app":case "support_foreground_app":case "background_freeze_whitelist":case "repeat_start_scene_pkg":doc=packageName(key,text);break;
                case "cmd":doc=commands(key,text);break;
                default:return null;
            }
            return doc.fieldCount()==0?null:doc;
        }catch(IllegalArgumentException e){return null;}
    }
    static Document packageName(String key,String text){
        Document doc=new Document(key,text,"应用列表项",Messages.text("parser_application_list_item","应用列表项"));Section s=new Section("应用 / 分组",Messages.text("parser_application_or_group","应用 / 分组"));field(s,"应用 / 分组",text,0,text.length(),PACKAGE,Messages.text("parser_application_or_group","应用 / 分组"));add(doc,s);return doc;
    }
    static Document pair(String key,String text,char separator,String title,String a,int at,String b,int bt,Messages.Text caption,Messages.Text aCaption,Messages.Text bCaption){
        List<Part> parts=split(text,separator,0);if(parts.size()!=2)throw new IllegalArgumentException();
        Document doc=new Document(key,text,title,caption);Section s=new Section(title,caption);
        field(s,a,text,parts.get(0),at,aCaption);field(s,b,text,parts.get(1),bt,bCaption);add(doc,s);return doc;
    }
    static Document temperature(String key,String text){
        Document doc=new Document(key,text,"温度分档",Messages.text("parser_temperature_table","温度分档"));int index=0;BigDecimal previous=null;
        for(Part row:split(text,',',0)){
            ++index;List<Part> pair=split(row.text,':',row.start);
            if(pair.size()!=2){doc.unknown(row,"温度档格式未识别",Messages.text("parser_temperature_band_unrecognized","温度档格式未识别"));continue;}
            Section s=new Section("温度档 "+index,Messages.text("parser_temperature_band","温度档 "+index,index));Field threshold;
            try{threshold=field(s,"温度 ≥ ℃",text,pair.get(0),TEMP,Messages.text("parser_temperature_threshold","温度 ≥ ℃"));}catch(IllegalArgumentException e){doc.unknown(row,"温度无效："+e.getMessage(),Messages.text("parser_temperature_invalid_reason","温度无效："+e.getMessage(),Messages.reason(e)));continue;}
            BigDecimal value=new BigDecimal(threshold.value);
            // Keep rejecting ambiguous duplicate/descending tables, including across malformed FPS rows.
            if(previous!=null&&value.compareTo(previous)<=0)throw new Messages.Failure(Messages.text("diag_temperature_order_invalid","温度档须严格递增"));
            previous=value;doc.temperatures.add(threshold);
            try{field(s,"帧率",text,pair.get(1),FPS,Messages.text("parser_fps","帧率"));add(doc,s);}catch(IllegalArgumentException e){doc.unknown(row,"帧率无效，整档保留："+e.getMessage(),Messages.text("parser_fps_invalid_band_preserved","帧率无效，整档保留："+e.getMessage(),Messages.reason(e)));}
        }return doc;
    }
    static Document pid(String key,String text){
        Document doc=new Document(key,text,"PID温控",Messages.text("parser_pid_temperature_control","PID温控"));
        for(Part group:split(text,';',0)){
            List<Part> pair=split(group.text,'#',group.start);
            if(pair.size()!=2){doc.unknown(group,"PID 策略格式未识别",Messages.text("parser_pid_strategy_unrecognized","PID 策略格式未识别"));continue;}
            Section header=new Section(pair.get(0).text.trim()+" FPS 策略",Messages.text("parser_fps_strategy",pair.get(0).text.trim()+" FPS 策略",pair.get(0).text.trim()));Field rateField;
            try{rateField=field(header,"策略帧率",text,pair.get(0),FPS,Messages.text("parser_strategy_fps","策略帧率"));}catch(IllegalArgumentException e){doc.unknown(group,"策略帧率无效，整组保留："+e.getMessage(),Messages.text("parser_strategy_fps_invalid_group_preserved","策略帧率无效，整组保留："+e.getMessage(),Messages.reason(e)));continue;}
            String rate=rateField.value;
            for(Field other:doc.groupRates)if(new BigDecimal(other.value).compareTo(new BigDecimal(rate))==0){doc.unknown(pair.get(0),"原有策略帧率重复",Messages.text("parser_existing_strategy_fps_duplicate","原有策略帧率重复"));break;}
            doc.groupRates.add(rateField);add(doc,header);int index=0;
            for(Part row:split(pair.get(1).text,',',pair.get(1).start)){
                ++index;if(!PID_ROW.matcher(row.text).matches()){doc.unknown(row,"PID 温度档格式未识别",Messages.text("parser_pid_temperature_band_unrecognized","PID 温度档格式未识别"));continue;}
                Section s=new Section(rate+" FPS · 温度档 "+index,Messages.text("parser_fps_temperature_band",rate+" FPS · 温度档 "+index,rate,index));
                String[] labels={"温度 A（℃）","温度 B（℃）","目标帧率","最低帧率","Kp","Ki","Kd"};
                String[] ids={"parser_temperature_a","parser_temperature_b","parser_target_fps","parser_minimum_fps","parser_pid_kp","parser_pid_ki","parser_pid_kd"};
                Matcher tokens=NUMBERS.matcher(row.text);int i=0;
                try{
                    while(tokens.find()){int type=i<2?TEMP:i<4?FPS:NUMBER;field(s,labels[i],text,row.start+tokens.start(),row.start+tokens.end(),type,Messages.text(ids[i],labels[i]));i++;}
                    add(doc,s);
                }catch(IllegalArgumentException e){doc.unknown(row,"PID 参数无效，整档保留："+e.getMessage(),Messages.text("parser_pid_invalid_band_preserved","PID 参数无效，整档保留："+e.getMessage(),Messages.reason(e)));}
            }
        }return doc;
    }
    static Document migt(String key,String text){
        Document doc=new Document(key,text,"MIGT策略",Messages.text("parser_migt_strategy","MIGT策略"));List<Part> parts=split(text,';',0);
        if(parts.size()<2)throw new IllegalArgumentException();
        Section app=new Section("目标游戏",Messages.text("parser_target_game","目标游戏"));Part name=parts.get(0);
        try{field(app,"应用 / 分组",text,name,PACKAGE,Messages.text("parser_application_or_group","应用 / 分组"));add(doc,app);}catch(IllegalArgumentException e){doc.unknown(name,"应用 / 分组未识别",Messages.text("parser_application_or_group_unrecognized","应用 / 分组未识别"));}
        for(int i=1;i<parts.size();i++){
            Part part=parts.get(i);
            if(i==1||i==3||i==7){
                Section s=new Section(i==1?"CPU频率（kHz）":i==3?"帧耗时（ms）":"CPU标记",i==1?Messages.text("parser_cpu_frequency_khz","CPU频率（kHz）"):i==3?Messages.text("parser_frame_time_ms","帧耗时（ms）"):Messages.text("parser_cpu_flags","CPU标记"));Matcher words=WORD.matcher(part.text);boolean any=false;
                while(words.find()){
                    any=true;Part token=new Part(words.group(),part.start+words.start());Matcher pair=MAP_TOKEN.matcher(token.text);
                    if(!pair.matches()){doc.unknown(token,"MIGT 映射项未识别",Messages.text("parser_migt_mapping_unrecognized","MIGT 映射项未识别"));continue;}
                    try{field(s,i==3?pair.group(1)+" FPS":"CPU "+pair.group(1),text,token.start+pair.start(2),token.start+pair.end(2),i==3?NONNEGATIVE:INTEGER,i==3?Messages.text("parser_fps_mapping",pair.group(1)+" FPS",pair.group(1)):Messages.text("parser_cpu_mapping","CPU "+pair.group(1),pair.group(1)));}catch(IllegalArgumentException e){doc.unknown(token,"MIGT 映射值无效："+e.getMessage(),Messages.text("parser_migt_mapping_invalid_reason","MIGT 映射值无效："+e.getMessage(),Messages.reason(e)));}
                }
                if(!any)doc.unknown(part,"空的 MIGT 映射段",Messages.text("parser_migt_mapping_empty","空的 MIGT 映射段"));add(doc,s);
            }else{
                Section s=new Section("参数 "+(i+1),Messages.text("parser_parameter","参数 "+(i+1),i+1));
                try{field(s,"数值",text,part,NUMBER,Messages.text("parser_number","数值"));add(doc,s);}catch(IllegalArgumentException e){doc.unknown(part,"MIGT 参数未识别",Messages.text("parser_migt_parameter_unrecognized","MIGT 参数未识别"));}
            }
        }return doc;
    }
    static Document forceScale(String key,String text){
        List<Part> parts=split(text,':',0);
        if(parts.size()==2&&parts.get(1).text.indexOf('#')<0)return pair(key,text,':',"渲染比例","应用 / 分组",PACKAGE,"缩放比例",NONNEGATIVE,Messages.text("parser_render_scale","渲染比例"),Messages.text("parser_application_or_group","应用 / 分组"),Messages.text("parser_scale_ratio","缩放比例"));
        if(parts.size()<2||parts.size()>4)throw new IllegalArgumentException();
        Document doc=new Document(key,text,"强制缩放",Messages.text("parser_force_scale","强制缩放"));Section app=new Section("目标应用",Messages.text("parser_target_application","目标应用"));
        field(app,"应用 / 分组",text,parts.get(0),PACKAGE,Messages.text("parser_application_or_group","应用 / 分组"));add(doc,app);int index=0;
        for(Part row:split(parts.get(1).text,';',parts.get(1).start)){
            Section s=new Section("DPI 缩放档 "+(++index),Messages.text("parser_dpi_scale_band","DPI 缩放档 "+index,index));List<Part> pair=split(row.text,'#',row.start);
            if(pair.size()!=2){doc.unknown(row,"DPI#比例 格式未识别",Messages.text("parser_dpi_scale_unrecognized","DPI#比例 格式未识别"));continue;}
            try{
                Field dpi=field(s,"DPI",text,pair.get(0),INTEGER,Messages.text("parser_dpi","DPI"));field(s,"缩放比例",text,pair.get(1),NONNEGATIVE,Messages.text("parser_scale_ratio","缩放比例"));
                for(Field other:doc.scaleDpis)if(new BigDecimal(other.value).compareTo(new BigDecimal(dpi.value))==0){doc.unknown(row,"原有 DPI 档位重复",Messages.text("parser_existing_dpi_duplicate","原有 DPI 档位重复"));break;}
                doc.scaleDpis.add(dpi);add(doc,s);
            }catch(IllegalArgumentException e){doc.unknown(row,"缩放参数无效，整档保留："+e.getMessage(),Messages.text("parser_scale_invalid_band_preserved","缩放参数无效，整档保留："+e.getMessage(),Messages.reason(e)));}
        }
        if(parts.size()>=3){
            Section activities=new Section("Activity 类名",Messages.text("parser_activity_classes","Activity 类名"));int n=0;
            for(Part part:split(parts.get(2).text,';',parts.get(2).start)){
                try{field(activities,"Activity "+(++n),text,part,CLASS_NAME,Messages.text("parser_activity_index","Activity "+n,n));}catch(IllegalArgumentException e){doc.unknown(part,"Activity 类名未识别",Messages.text("parser_activity_class_unrecognized","Activity 类名未识别"));}
            }add(doc,activities);
        }
        if(parts.size()==4)doc.unknown(parts.get(3),"附加参数的语义未验证",Messages.text("parser_additional_parameters_unverified","附加参数的语义未验证"));return doc;
    }
    static Document commands(String key,String text){
        Document doc=new Document(key,text,"场景命令",Messages.text("parser_scene_commands","场景命令"));int index=0;
        for(Part part:split(text,';',0)){
            List<Part> parts=split(part.text,'#',part.start);
            if(parts.size()<2||parts.get(0).text.trim().isEmpty()){doc.unknown(part,"命令段未识别",Messages.text("parser_command_unrecognized","命令段未识别"));continue;}
            String name=parts.get(0).text.trim();
            try{new Field("",name,0,0,TEXT).checked(parts.get(0).text);}catch(IllegalArgumentException e){doc.unknown(part,"命令名称包含控制字符，整段保留",Messages.text("parser_command_name_control_characters","命令名称包含控制字符，整段保留"));continue;}
            Section s=new Section((++index)+" · "+name,Messages.text("parser_command_index",index+" · "+name,index,name));
            for(int i=1;i<parts.size();i++){
                Part p=parts.get(i);
                try{field(s,"参数 "+i,text,p,TEXT,Messages.text("parser_parameter","参数 "+i,i));}catch(IllegalArgumentException e){doc.unknown(p,"命令参数保留："+e.getMessage(),Messages.text("parser_command_argument_preserved","命令参数保留："+e.getMessage(),Messages.reason(e)));}
            }
            add(doc,s);
        }return doc;
    }
    private StringRules(){}
}
