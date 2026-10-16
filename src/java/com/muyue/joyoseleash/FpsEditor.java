package com.muyue.joyoseleash;

import android.graphics.Typeface;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import org.json.*;
import java.util.*;

/** A single, local editor for fixed FPS and temperature thresholds. */
final class FpsEditor {
    interface Change {void accept(JSONObject value)throws Exception;}
    final NativeUi ui;
    final JSONObject original;
    final Change change;
    final LinearLayout body,fields,scopes,modes;
    final ScrollView view;
    final TextView error,scopeButton;
    final String[] tables=new String[2];
    final List<Threshold> rows=new ArrayList<>();
    final List<TextView> presets=new ArrayList<>();
    EditText number;
    boolean separate;
    int selected,kind;
    String lastSaved;
    boolean rendering;
    static final String[] KEYS={"dynamic_fps_M","dynamic_fps"};
    static final int NUM=android.text.InputType.TYPE_CLASS_NUMBER;
    static final int TEMP=NUM|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;
    static final class Threshold {
        final EditText temperature,fps;
        Threshold(EditText temperature,EditText fps){this.temperature=temperature;this.fps=fps;}
    }

    FpsEditor(NativeUi ui,JSONObject source,Change change,Runnable more)throws Exception{
        this.ui=ui;this.original=RuleModel.copy(source);this.change=change;lastSaved=original.toString();
        tables[0]=source.optString(KEYS[0]);tables[1]=source.optString(KEYS[1]);separate=!tables[0].equals(tables[1]);
        body=ui.column();ui.pad(body,20);view=new ScrollView(ui.activity);view.setFillViewport(true);view.setClipToPadding(false);view.addView(body,new ScrollView.LayoutParams(-1,-2));
        LinearLayout section=ui.row();TextView heading=ui.text("帧率方式",15,NativeUi.GRAY);section.addView(heading,new LinearLayout.LayoutParams(0,ui.dp(48),1));scopeButton=ui.textButton(separate?"统一设置":"分别设置",()->{
            if(!flush())return;separate=!separate;if(!separate)tables[1-selected]=tables[selected];selected=0;render();publish();
        });scopeButton.setTextSize(14);section.addView(scopeButton);body.addView(section);
        scopes=ui.row();body.addView(scopes);modes=ui.row();ui.margin(body,modes,8);fields=ui.column();ui.margin(body,fields,24);
        error=ui.text("",14,NativeUi.ERROR);error.setVisibility(View.GONE);ui.margin(body,error,12);ui.gap(body,20);ui.line(body);ui.margin(body,ui.textButton("更多规则  ›",()->{if(flush())more.run();}),8);render();
    }
    private TextWatcher watcher(Runnable change){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(!rendering)change.run();}public void afterTextChanged(Editable e){}};}
    private int mode(String table){return table.isEmpty()?0:table.matches("10:[0-9]+")?1:2;}
    void render(){
        rendering=true;try{
            scopes.removeAllViews();scopes.setVisibility(separate?View.VISIBLE:View.GONE);scopeButton.setText(separate?"统一设置":"分别设置");
            if(separate)for(int i=0;i<2;i++){final int index=i;ui.equal(scopes,ui.choice(i==0?"MGAME":"TGAME",selected==i,()->{if(flush()){selected=index;render();}}));}
            kind=mode(tables[selected]);renderModes();fields.removeAllViews();rows.clear();presets.clear();number=null;error.setVisibility(View.GONE);
            if(kind==0){TextView inherit=ui.text("使用全局帧率规则",18,NativeUi.INK);inherit.setMinHeight(ui.dp(84));fields.addView(inherit);}
            else if(kind==1)fixed();else thresholds();
        }catch(Exception e){showError(e);}finally{rendering=false;}
    }
    void renderModes(){modes.removeAllViews();String[] names={"跟随全局","固定帧率","温度分档"};for(int i=0;i<names.length;i++){final int chosen=i;ui.equal(modes,ui.choice(names[i],kind==i,()->{
        if(chosen==kind)return;
        tables[selected]=chosen==0?"":chosen==1?"10:144":"10:144,38:90,43:60";
        if(!separate)tables[1-selected]=tables[selected];render();publish();
    }));}}
    void fixed(){
        TextView title=ui.text("目标帧率",14,NativeUi.GRAY);fields.addView(title);ui.gap(fields,12);LinearLayout stepper=ui.row();stepper.setBackground(ui.square(NativeUi.WHITE,NativeUi.LINE));stepper.setPadding(ui.dp(1),ui.dp(1),ui.dp(1),ui.dp(1));
        TextView minus=ui.textButton("−",()->step(-1));minus.setTextSize(26);minus.setContentDescription("减少帧率");stepper.addView(minus,new LinearLayout.LayoutParams(ui.dp(56),ui.dp(96)));
        number=ui.input(tables[selected].substring(tables[selected].indexOf(':')+1),"",NUM);number.setTextSize(40);number.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));number.setGravity(Gravity.CENTER);number.setSingleLine(true);number.setBackgroundColor(NativeUi.WHITE);number.setContentDescription("目标帧率");number.setMinHeight(ui.dp(96));number.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);stepper.addView(number,new LinearLayout.LayoutParams(0,-2,1));
        TextView unit=ui.text(number.getText().toString().equals("0")?"不限帧":"FPS",14,NativeUi.GRAY);stepper.addView(unit,new LinearLayout.LayoutParams(ui.dp(50),ui.dp(48)));TextView plus=ui.textButton("+",()->step(1));plus.setTextSize(26);plus.setContentDescription("增加帧率");stepper.addView(plus,new LinearLayout.LayoutParams(ui.dp(56),ui.dp(96)));fields.addView(stepper);
        LinearLayout values=ui.row();for(int value:new int[]{60,90,120,144,165}){TextView option=ui.choice(String.valueOf(value),number.getText().toString().equals(String.valueOf(value)),()->number.setText(String.valueOf(value)));presets.add(option);ui.equal(values,option);}ui.margin(fields,values,16);
        number.addTextChangedListener(watcher(()->{unit.setText(number.getText().toString().equals("0")?"不限帧":"FPS");updatePresets();publishIfValid();}));
    }
    void updatePresets(){String value=number==null?"":number.getText().toString();for(TextView option:presets)ui.selectChoice(option,option.getText().toString().equals(value));}
    void step(int delta){try{int value=Integer.parseInt(number.getText().toString().trim());number.setText(String.valueOf(Math.max(0,Math.min(1000,value+delta))));}catch(Exception e){showError(new Exception("请输入帧率"));}}
    void thresholds()throws Exception{
        LinearLayout labels=ui.row();ui.equal(labels,ui.text("温度 ≥ ℃",14,NativeUi.GRAY));ui.equal(labels,ui.text("帧率",14,NativeUi.GRAY));labels.addView(new View(ui.activity),new LinearLayout.LayoutParams(ui.dp(48),1));fields.addView(labels);ui.gap(fields,8);
        for(String[] pair:RuleModel.tableRows(tables[selected]))addRow(pair[0],pair[1]);
        TextView add=ui.textButton("+ 添加温度档",()->{
            String temperature="10",fps="144";if(!rows.isEmpty()){Threshold last=rows.get(rows.size()-1);try{temperature=String.valueOf(Double.parseDouble(last.temperature.getText().toString())+1);fps=last.fps.getText().toString();}catch(Exception ignored){}}
            addRow(temperature,fps);publishIfValid();
        });ui.margin(fields,add,8);
    }
    void addRow(String temperature,String fps){
        LinearLayout row=ui.row();EditText t=ui.input(temperature,"温度",TEMP),f=ui.input(fps,"帧率",NUM);t.setSingleLine(true);f.setSingleLine(true);t.setContentDescription("温度阈值，摄氏度");f.setContentDescription("此温度对应帧率");ui.equal(row,t);ui.equal(row,f);
        Threshold model=new Threshold(t,f);TextView remove=ui.textButton("×",()->{rows.remove(model);fields.removeView(row);publishIfValid();});remove.setTextColor(NativeUi.GRAY);remove.setTextSize(24);remove.setContentDescription("删除温度档");row.addView(remove,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));rows.add(model);
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.bottomMargin=ui.dp(8);int index=fields.getChildCount();if(index>0&&fields.getChildAt(index-1) instanceof TextView)index--;fields.addView(row,index,layout);
        t.addTextChangedListener(watcher(this::publishIfValid));f.addTextChangedListener(watcher(this::publishIfValid));
    }
    String editedTable()throws Exception{
        if(kind==0)return "";
        if(kind==1){int fps=Integer.parseInt(number.getText().toString().trim());if(fps<0||fps>1000)throw new Exception("帧率须为0至1000");return "10:"+fps;}
        if(rows.isEmpty())throw new Exception("请添加温度档");List<String[]> values=new ArrayList<>();for(Threshold r:rows)values.add(new String[]{r.temperature.getText().toString(),r.fps.getText().toString()});return RuleModel.table(values);
    }
    void publishIfValid(){try{updateTables();if(publish())error.setVisibility(View.GONE);}catch(Exception ignored){}}
    void updateTables()throws Exception{tables[selected]=editedTable();if(!separate)tables[1-selected]=tables[selected];}
    boolean publish(){
        try{JSONObject value=RuleModel.copy(original);for(int i=0;i<2;i++)if(tables[i].isEmpty())value.remove(KEYS[i]);else value.put(KEYS[i],tables[i]);String snapshot=value.toString();if(!snapshot.equals(lastSaved)){change.accept(value);lastSaved=snapshot;}return true;}
        catch(Exception e){showError(e);return false;}
    }
    boolean flush(){try{updateTables();if(!publish())return false;error.setVisibility(View.GONE);return true;}catch(Exception e){showError(e);return false;}}
    void showError(Exception e){String text=e.getMessage();if(e instanceof NumberFormatException)text="请输入有效数字";error.setText(text==null?"请检查输入":text);error.setVisibility(View.VISIBLE);}
}
