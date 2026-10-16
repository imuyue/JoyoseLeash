package com.muyue.joyoseleash;

import java.util.*;

/** Host-side format regressions. No phone, Android runtime, or system rules needed. */
public final class StringRulesChecks {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static StringRules.Document parse(String key,String value){StringRules.Document doc=StringRules.parse(key,value);check(doc!=null,"Unable to parse "+key);return doc;}
    static StringRules.Field find(StringRules.Document doc,String section,String label){for(StringRules.Section s:doc.sections)if(s.title.equals(section))for(StringRules.Field f:s.fields)if(f.label.equals(label))return f;throw new AssertionError(section+" / "+label);}
    static void unchanged(String key,String value){StringRules.Document doc=parse(key,value);for(StringRules.Section s:doc.sections)for(StringRules.Field f:s.fields)check(doc.replace(f,f.value).equals(value),"Roundtrip rewrote other spans: "+key+" / "+f.label);}
    static void rejects(StringRules.Document doc,StringRules.Field field,String value){boolean rejected=false;try{doc.replace(field,value);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Invalid edit accepted: "+value);}
    public static void main(String[] args){
        String pid="90#10:0 0 0 0 0 0,44.5:45 90 60 15 1.5 3;120#10:0 0 0 0 0,43:44 120 90 20 2;future-format";
        unchanged("PID_T",pid);StringRules.Document p=parse("PID_T",pid);
        check(p.unparsed==1,"Unknown PID group lost");
        StringRules.Field temp=find(p,"90 FPS · 温度档 2","温度 A（℃）");
        String updated=p.replace(temp,"46.5");check(updated.equals(pid.replace("44.5:45","46.5:45")),"PID edit touched another group");
        rejects(p,temp,"NaN");rejects(p,temp,"201");rejects(p,temp,"46;bad");
        rejects(p,find(p,"90 FPS 策略","策略帧率"),"120");
        String migt="example.visual.game;0:384000 1:384000 6:1017600 7:1017600;30;120:12 90:15 60:18;2;10;1;0:0 6:0;opaque tail;;";
        unchanged("migt",migt);StringRules.Document m=parse("migt",migt);
        StringRules.Field core=find(m,"CPU频率（kHz）","CPU 6");
        check(m.replace(core,"1400000").equals(migt.replace("6:1017600","6:1400000")),"MIGT edit damaged suffix");
        rejects(m,core,"-1");rejects(m,core,"2147483648");
        unchanged("migt","example.ten.core;0:300000 8:1800000 9:1800000;30;144:7;2;10;1");
        unchanged("support_highfps_app","  example.visual.game:144  ");
        StringRules.Document fps=parse("novatek_gex_fps_limit","example.visual.game:90");
        rejects(fps,find(fps,"应用帧率","帧率"),"1001");rejects(fps,find(fps,"应用帧率","帧率"),"60.0");
        unchanged("recommend_tgame_thresh","44#90");unchanged("support_scale_app_list","example.visual.game:0.75");
        unchanged("reset_game_min_duration","example.visual.game:5000");
        unchanged("dynamic_fps","10:0,38:90,42.8:60");
        check(StringRules.parse("dynamic_fps","10:0,10:90")==null,"Duplicate temperature accepted");
        check(StringRules.parse("dynamic_fps","42:60,10:90")==null,"Unsorted temperature accepted");
        String command="glk#WA#1;BI#0;/sys/devices/example#0 1152000:-10;perflock#42C08000_0#0;unknown;";
        unchanged("cmd",command);StringRules.Document c=parse("cmd",command);
        StringRules.Field parameter=find(c,"4 · perflock","参数 1");rejects(c,parameter,"42;injected");
        check(c.replace(parameter,"42C08000_1").equals(command.replace("42C08000_0","42C08000_1")),"Command edit changed surrounding data");
        check(StringRules.parse("unrecognized","example.visual.game:90")==null,"Guessed unknown field semantics");
        check(StringRules.parse("support_highfps_app","example.visual.game:90:extra")==null,"Dropped unknown pair suffix");
        check(StringRules.parse("PID_M","not-a-PID-table")==null,"Malformed PID recognized");
        check(StringRules.parse("support_scale_app_list","example.visual.game")==null,"Plain package converted to scale");
        System.out.println("StringRules checks passed: "+checks);
    }
}
