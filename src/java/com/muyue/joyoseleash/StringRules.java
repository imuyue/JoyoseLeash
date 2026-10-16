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

/** Only recognized spans are editable. All other bytes and delimiters survive. */
final class StringRules {
    static final String NUM="[-+]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)";
    static final Pattern NUMBERS=Pattern.compile(NUM);
    static final int TEXT=0,PACKAGE=1,INTEGER=2,NUMBER=3,TEMP=4,FPS=5,NONNEGATIVE=6;

    static final class Field {
        final String label,value;final int start,end,type;
        Field(String label,String value,int start,int end,int type){this.label=label;this.value=value;this.start=start;this.end=end;this.type=type;}
        String checked(String input){
            String text=input.trim();
            if(text.isEmpty())throw new IllegalArgumentException("内容不能为空");
            if(type==TEXT){if(text.matches("(?s).*[;#\\r\\n].*"))throw new IllegalArgumentException("参数不能包含分号、井号或换行");return text;}
            if(type==PACKAGE){if(!text.matches("[A-Za-z0-9_.#-]{1,300}"))throw new IllegalArgumentException("应用包名或分组名无效");return text;}
            if(!text.matches(NUM)||text.length()>40)throw new IllegalArgumentException("请输入有效数字");
            BigDecimal number=new BigDecimal(text);
            if(!Double.isFinite(number.doubleValue()))throw new IllegalArgumentException("数字超出范围");
            if(type==INTEGER||type==FPS){
                if(!text.matches("[0-9]+"))throw new IllegalArgumentException("请输入非负整数");
                if(number.compareTo(BigDecimal.valueOf(type==FPS?1000:Integer.MAX_VALUE))>0)throw new IllegalArgumentException(type==FPS?"帧率须为0至1000":"整数超出范围");
            }
            if(type==TEMP&&(number.compareTo(BigDecimal.valueOf(-100))<0||number.compareTo(BigDecimal.valueOf(200))>0))throw new IllegalArgumentException("温度须为-100至200℃");
            if(type==NONNEGATIVE&&number.signum()<0)throw new IllegalArgumentException("数值不能为负数");
            return text;
        }
    }
    static final class Section {
        final String title;final List<Field> fields=new ArrayList<>();
        Section(String title){this.title=title;}
    }
    static final class Document {
        final String key,raw,format;
        final List<Section> sections=new ArrayList<>();
        final List<Field> groupRates=new ArrayList<>();
        int unparsed;
        Document(String key,String raw,String format){this.key=key;this.raw=raw;this.format=format;}
        String replace(Field field,String input){
            boolean belongs=false;for(Section section:sections)if(section.fields.contains(field))belongs=true;
            if(!belongs)throw new IllegalArgumentException("字段不属于当前规则");
            String replacement=field.checked(input);
            if(groupRates.contains(field))for(Field other:groupRates)if(other!=field&&new BigDecimal(other.value).compareTo(new BigDecimal(replacement))==0)throw new IllegalArgumentException("策略帧率重复");
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
    static Field field(Section section,String label,String raw,int start,int end,int type){
        while(start<end&&Character.isWhitespace(raw.charAt(start)))start++;
        while(end>start&&Character.isWhitespace(raw.charAt(end-1)))end--;
        Field field=new Field(label,raw.substring(start,end),start,end,type);field.checked(field.value);section.fields.add(field);return field;
    }
    static Section section(Document doc,String title){Section section=new Section(title);doc.sections.add(section);return section;}

    static Document parse(String key,String text){
        if(text==null||text.length()>262144)return null;
        try{
            Document doc;
            switch(key){
                case "PID_M":case "PID_T":doc=pid(key,text);break;
                case "migt":doc=migt(key,text);break;
                case "recommend_tgame_thresh":doc=pair(key,text,'#',"TGAME推荐阈值","温度（℃）",TEMP,"帧率",FPS);break;
                case "support_highfps_app":case "novatek_gex_fps_limit":doc=pair(key,text,':',"应用帧率","应用 / 分组",PACKAGE,"帧率",FPS);break;
                case "support_scale_app_list":case "force_scale_app_list":doc=pair(key,text,':',"渲染比例","应用 / 分组",PACKAGE,"缩放比例",NONNEGATIVE);break;
                case "reset_game_min_duration":doc=pair(key,text,':',"游戏时长","应用 / 分组",PACKAGE,"时长（ms）",INTEGER);break;
                case "dynamic_fps":case "dynamic_fps_M":doc=temperature(key,text);break;
                case "cmd":doc=commands(key,text);break;
                default:return null;
            }
            return doc.fieldCount()==0?null:doc;
        }catch(IllegalArgumentException e){return null;}
    }
    static Document pair(String key,String text,char separator,String title,String a,int at,String b,int bt){
        List<Part> parts=split(text,separator,0);if(parts.size()!=2)throw new IllegalArgumentException();
        Document doc=new Document(key,text,title);Section s=section(doc,title);
        field(s,a,text,parts.get(0).start,parts.get(0).start+parts.get(0).text.length(),at);
        field(s,b,text,parts.get(1).start,parts.get(1).start+parts.get(1).text.length(),bt);return doc;
    }
    static Document temperature(String key,String text){
        Document doc=new Document(key,text,"温度分档");int index=0;double previous=-Double.MAX_VALUE;
        for(Part row:split(text,',',0)){
            List<Part> pair=split(row.text,':',row.start);if(pair.size()!=2)throw new IllegalArgumentException();
            Section s=section(doc,"温度档 "+(++index));
            Field temperature=field(s,"温度 ≥ ℃",text,pair.get(0).start,pair.get(0).start+pair.get(0).text.length(),TEMP);
            double threshold=Double.parseDouble(temperature.value);if(threshold<=previous)throw new IllegalArgumentException();previous=threshold;
            field(s,"帧率",text,pair.get(1).start,pair.get(1).start+pair.get(1).text.length(),FPS);
        }return doc;
    }
    static Document pid(String key,String text){
        Document doc=new Document(key,text,"PID温控");
        for(Part group:split(text,';',0)){
            List<Part> pair=split(group.text,'#',group.start);
            if(pair.size()!=2||!pair.get(0).text.trim().matches("[0-9]+")){doc.unparsed++;continue;}
            String rate=pair.get(0).text.trim();Section header=section(doc,rate+" FPS 策略");
            doc.groupRates.add(field(header,"策略帧率",text,pair.get(0).start,pair.get(0).start+pair.get(0).text.length(),FPS));
            int index=0;
            for(Part row:split(pair.get(1).text,',',pair.get(1).start)){
                String pattern="\\s*"+NUM+":"+NUM+"(?:\\s+"+NUM+"){4,5}\\s*";
                if(!row.text.matches(pattern)){doc.unparsed++;continue;}
                Section s=section(doc,rate+" FPS · 温度档 "+(++index));
                String[] labels={"温度 A（℃）","温度 B（℃）","目标帧率","最低帧率","Kp","Ki","Kd"};
                Matcher tokens=NUMBERS.matcher(row.text);int i=0;
                while(tokens.find()){int type=i<2?TEMP:i<4?FPS:NUMBER;field(s,labels[i++],text,row.start+tokens.start(),row.start+tokens.end(),type);}
            }
        }return doc;
    }
    static Document migt(String key,String text){
        Document doc=new Document(key,text,"MIGT策略");List<Part> parts=split(text,';',0);
        if(parts.size()<2)throw new IllegalArgumentException();
        Section app=section(doc,"目标游戏");Part name=parts.get(0);field(app,"应用 / 分组",text,name.start,name.start+name.text.length(),PACKAGE);
        for(int i=1;i<parts.size();i++){
            Part part=parts.get(i);
            if((i==1||i==3||i==7)&&part.text.matches("\\s*[0-9]+:"+NUM+"(?:\\s+[0-9]+:"+NUM+")*\\s*")){
                Section s=section(doc,i==1?"CPU频率（kHz）":i==3?"帧耗时（ms）":"CPU标记");
                Matcher tokens=Pattern.compile("([0-9]+):("+NUM+")").matcher(part.text);
                while(tokens.find())field(s,i==3?tokens.group(1)+" FPS":"CPU "+tokens.group(1),text,part.start+tokens.start(2),part.start+tokens.end(2),i==3?NONNEGATIVE:INTEGER);
            }else if(part.text.trim().matches(NUM)){
                Section s=section(doc,"参数 "+(i+1));field(s,"数值",text,part.start,part.start+part.text.length(),NUMBER);
            }else doc.unparsed++;
        }return doc;
    }
    static Document commands(String key,String text){
        Document doc=new Document(key,text,"场景命令");int index=0;
        for(Part part:split(text,';',0)){
            List<Part> parts=split(part.text,'#',part.start);
            if(parts.size()<2||parts.get(0).text.trim().isEmpty()){doc.unparsed++;continue;}
            Section s=section(doc,(++index)+" · "+parts.get(0).text.trim());
            for(int i=1;i<parts.size();i++){
                Part p=parts.get(i);if(p.text.trim().isEmpty()){doc.unparsed++;continue;}
                field(s,"参数 "+i,text,p.start,p.start+p.text.length(),TEXT);
            }
        }return doc;
    }
    private StringRules(){}
}
