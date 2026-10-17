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
    static boolean hasSection(StringRules.Document doc,String title){for(StringRules.Section s:doc.sections)if(s.title.equals(title))return true;return false;}
    static boolean hasFragment(StringRules.Document doc,String value){for(StringRules.Fragment f:doc.fragments)if(doc.raw.substring(f.start,f.end).equals(value))return true;return false;}
    static void spans(StringRules.Document doc){
        check(doc.unparsed==doc.fragments.size(),"Diagnostic count is inconsistent");
        for(StringRules.Fragment f:doc.fragments){check(f.start>=0&&f.end>=f.start&&f.end<=doc.raw.length(),"Invalid diagnostic span");check(!f.reason.isEmpty(),"Diagnostic reason missing");}
        int previous=-1;
        for(StringRules.Section s:doc.sections){check(!s.fields.isEmpty(),"Empty editable section leaked");for(StringRules.Field f:s.fields){check(f.start>=previous&&f.end>=f.start&&f.end<=doc.raw.length(),"Overlapping or invalid field spans");check(doc.raw.substring(f.start,f.end).equals(f.value),"Field value detached from source");previous=f.end;}}
    }
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
        StringRules.Document plainScale=parse("support_scale_app_list","example.visual.game");
        check(plainScale.format.equals("应用列表项")&&plainScale.fieldCount()==1,"Plain package guessed as a numeric scale");
        // One oversized header or one bad row must not hide independent valid PID groups.
        String malformedPid="1001#10:0 0 0 0 0;90#201:0 90 60 1 2,44 : 45 90 60 1 2,46:47 1001 60 1 2,48:49 90 60 1 2;opaque😺;120#10:0 0 0 0 0";
        unchanged("PID_M",malformedPid);StringRules.Document resilientPid=parse("PID_M",malformedPid);spans(resilientPid);
        check(resilientPid.unparsed==4,"Malformed PID diagnostic count changed");
        check(!hasSection(resilientPid,"1001 FPS 策略"),"Out-of-range PID header exposed");
        check(!hasSection(resilientPid,"90 FPS · 温度档 1")&&!hasSection(resilientPid,"90 FPS · 温度档 3"),"Partially validated PID row leaked fields");
        StringRules.Field safePid=find(resilientPid,"90 FPS · 温度档 2","Kp");
        check(resilientPid.replace(safePid,"1.25").equals(malformedPid.replace("44 : 45 90 60 1 2","44 : 45 90 60 1.25 2")),"PID sibling edit rewrote malformed/Unicode fragments");
        check(hasFragment(resilientPid,"1001#10:0 0 0 0 0")&&hasFragment(resilientPid,"opaque😺"),"PID unknown source spans lost");
        String duplicatedPid="90#10:0 0 0 0 0;090#11:0 0 0 0 0";
        unchanged("PID_T",duplicatedPid);StringRules.Document duplicate=parse("PID_T",duplicatedPid);spans(duplicate);
        check(duplicate.unparsed==1,"Existing PID duplicate lacks warning");
        rejects(duplicate,find(duplicate,"90 FPS 策略","策略帧率"),"0090");

        // MIGT entries have independent boundaries; malformed tokens do not shift later labels.
        String brokenMigt="bad application;0:384000 1:-1 opaque 6:1017600 7:2147483648;bad;120:12 90:-1 60:18;2;10;1;0:0 bad 6:0;opaque tail;;";
        unchanged("migt",brokenMigt);StringRules.Document resilientMigt=parse("migt",brokenMigt);spans(resilientMigt);
        check(!hasSection(resilientMigt,"目标游戏")&&!hasSection(resilientMigt,"参数 3"),"Invalid MIGT section leaked fields");
        check(find(resilientMigt,"CPU频率（kHz）","CPU 6").value.equals("1017600"),"Valid MIGT CPU sibling hidden");
        check(resilientMigt.replace(find(resilientMigt,"帧耗时（ms）","60 FPS"),"16.5").equals(brokenMigt.replace("60:18","60:16.5")),"MIGT edit damaged invalid neighbors");
        check(hasFragment(resilientMigt,"1:-1")&&hasFragment(resilientMigt,"7:2147483648"),"Invalid MIGT token diagnostics missing");
        String spacedMigt=" example.game ; 0:300000\t8:1800000 9:1800000 ;30;144:7;2;10;1";
        unchanged("migt",spacedMigt);spans(parse("migt",spacedMigt));

        // Replacement itself must enforce ordering, not rely on a later editor reparse.
        StringRules.Document ordered=parse("dynamic_fps","10:0,38:90,42.8:60");
        StringRules.Field middle=find(ordered,"温度档 2","温度 ≥ ℃");
        rejects(ordered,middle,"10");rejects(ordered,middle,"9");rejects(ordered,middle,"42.800");rejects(ordered,middle,"43");
        check(ordered.replace(middle,"40.25").equals("10:0,40.25:90,42.8:60"),"Ordered threshold edit failed");
        String partialTable="10:0,20:bad,30:90,unknown,,40:60,";
        unchanged("dynamic_fps",partialTable);StringRules.Document partial=parse("dynamic_fps",partialTable);spans(partial);
        check(partial.unparsed==4&&!hasSection(partial,"温度档 2"),"Malformed temperature row leaked a partial editor");
        rejects(partial,find(partial,"温度档 3","温度 ≥ ℃"),"19");
        check(partial.replace(find(partial,"温度档 3","帧率"),"120").equals(partialTable.replace("30:90","30:120")),"Partial table edit damaged unknown rows");
        check(StringRules.parse("dynamic_fps","10:0,20:bad,19:90")==null,"Unsorted valid threshold hidden by invalid FPS");
        unchanged("dynamic_fps","10:0,10.000000000000001:90,10.000000000000002:60");
        unchanged("dynamic_fps_multiWin","10:0,43:90");unchanged("dynamic_fps_multiWin_M","10:0,43:90");

        // Commands retain empty/invalid parameters, but never accept delimiter/control injection.
        String brokenCommands="glk#WA##1;BI#bad\nvalue#0;perflock#42C08000_0#0;opaque😺;";
        unchanged("cmd",brokenCommands);StringRules.Document resilientCommands=parse("cmd",brokenCommands);spans(resilientCommands);
        StringRules.Field retained=find(resilientCommands,"2 · BI","参数 2");
        check(resilientCommands.replace(retained,"1").equals(brokenCommands.replace("bad\nvalue#0","bad\nvalue#1")),"Command sibling edit rewrote unknown parameter");
        StringRules.Field textField=find(resilientCommands,"3 · perflock","参数 1");
        for(String invalid:Arrays.asList("injected#field","injected;command","line\nvalue","\rvalue","value\t","value"+(char)0,"value"+(char)127))rejects(resilientCommands,textField,invalid);
        String badHeader="bad\ncommand#0;BI#0";StringRules.Document headerDoc=parse("cmd",badHeader);spans(headerDoc);
        check(headerDoc.sections.size()==1&&hasFragment(headerDoc,"bad\ncommand#0"),"Invalid command header exposed editable parameters");

        // Observed list entries and actual force-scale DPI maps are explicitly allowlisted.
        for(String key:Arrays.asList("game_list","package_list","support_dynamic_refresh_rate_games","support_motor_app","support_foreground_app","support_scale_app_list","background_freeze_whitelist","repeat_start_scene_pkg")){
            unchanged(key,"  com.miHoYo.Yuanshen  ");StringRules.Document packages=parse(key,"group#1");spans(packages);
            rejects(packages,find(packages,"应用 / 分组","应用 / 分组"),"other:90");
            check(StringRules.parse(key,"example.game;other.game")==null,"Guessed separator for package list item");
        }
        unchanged("force_scale_app_list","example.game:0.75");
        String forceScale=" com.tencent.nrc : 600#1.45;720#1.25;bad;800#oops : com.epicgames.ue4.GameActivity;example.Game$Inner :opaque:extra";
        check(StringRules.parse("force_scale_app_list",forceScale)==null,"Guessed extra force-scale columns");
        forceScale=" com.tencent.nrc : 600#1.45;720#1.25;bad;800#oops : com.epicgames.ue4.GameActivity;example.Game$Inner :opaque";
        unchanged("force_scale_app_list",forceScale);StringRules.Document scales=parse("force_scale_app_list",forceScale);spans(scales);
        check(scales.unparsed==3&&!hasSection(scales,"DPI 缩放档 4"),"Force-scale malformed row was partially exposed");
        StringRules.Field dpi=find(scales,"DPI 缩放档 1","DPI");rejects(scales,dpi,"720");rejects(scales,dpi,"600.5");
        StringRules.Field ratio=find(scales,"DPI 缩放档 1","缩放比例");rejects(scales,ratio,"-1");rejects(scales,ratio,"1#0");
        check(scales.replace(ratio,"1.35").equals(forceScale.replace("600#1.45","600#1.35")),"Force-scale edit rewrote activity or unknown columns");
        rejects(scales,find(scales,"Activity 类名","Activity 2"),"example.Other;Injected");
        StringRules.Document onlyRaw=StringRules.raw("cmd","future\nformat#;😺");
        check(onlyRaw.fieldCount()==0&&onlyRaw.raw.equals("future\nformat#;😺")&&onlyRaw.unparsed==1,"Raw fallback lost unsupported content");spans(onlyRaw);
        rejects(p,find(parse("PID_T",pid),"90 FPS 策略","策略帧率"),"60");
        check(StringRules.parse(null,"10:0")==null&&StringRules.parse("dynamic_fps",null)==null,"Null parse input not handled");
        check(StringRules.parse("cmd",String.join("",Collections.nCopies(262145,"x")))==null,"Oversized string parsed visually");
        System.out.println("StringRules checks passed: "+checks);
    }
}
