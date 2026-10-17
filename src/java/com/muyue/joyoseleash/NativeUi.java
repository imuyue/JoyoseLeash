package com.muyue.joyoseleash;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * Native surface primitives. Screens are built from plain Android widgets with platform
 * typography and metrics; no explanatory captions are rendered here or by callers.
 */
final class NativeUi {
    static final int INK=0xff1a1d21,GRAY=0xff6b7280,LINE=0xffe4e6ea,WHITE=Color.WHITE;
    static final int PALE=0xfff2f3f5,ACCENT=0xff2e55d9,TINT=0xffeef2ff,ERROR=0xffb3261e,CONTROL=0xff8d939c;
    final Activity activity;
    final UiText strings;
    java.util.function.Consumer<String> notice;
    NativeUi(Activity activity){this.activity=activity;strings=new UiText(activity);}
    String s(String key,Object... args){return strings.s(key,args);}
    String q(String key,int quantity,Object... args){return strings.q(key,quantity,args);}
    String summary(Object value){return strings.summary(value);}
    String error(Throwable error){return strings.error(error);}
    Messages.Failure failure(String key,Object... args){return strings.failure(key,args);}
    String message(Messages.Text caption){return strings.message(caption);}
    String message(org.json.JSONObject caption){return strings.message(caption);}
    int dp(float value){return (int)(value*activity.getResources().getDisplayMetrics().density+.5f);}

    LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);v.setBackgroundColor(WHITE);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(activity);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    TextView text(String value,int sp,int color){TextView v=new TextView(activity);v.setText(value);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);return v;}
    void strong(TextView text){text.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}
    GradientDrawable square(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(0);if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    StateListDrawable press(int normal,int pressed){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_pressed},square(pressed,0));d.addState(new int[]{},square(normal,0));return d;}

    TextView button(String label,boolean primary,Runnable action){
        TextView b=text(label,16,primary?WHITE:INK);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(48));b.setMinimumWidth(dp(48));b.setPadding(dp(16),dp(12),dp(16),dp(12));
        if(primary)b.setBackground(press(ACCENT,0xff2444b3));else b.setBackground(square(WHITE,LINE));
        b.setOnClickListener(v->action.run());b.setFocusable(true);return b;
    }
    TextView textButton(String label,Runnable action){TextView b=new TextView(activity);b.setText(label);b.setTextSize(15);b.setTextColor(ACCENT);b.setGravity(Gravity.CENTER_VERTICAL);b.setMinHeight(dp(48));b.setPadding(dp(8),dp(12),dp(8),dp(12));b.setBackground(press(WHITE,TINT));b.setOnClickListener(v->action.run());b.setFocusable(true);return b;}
    TextView choice(String label,boolean selected,Runnable action){TextView b=button(label,false,action);b.setTextSize(14);b.setPadding(dp(8),dp(10),dp(8),dp(10));selectChoice(b,selected);return b;}
    void selectChoice(TextView b,boolean selected){b.setSelected(selected);b.setTextColor(selected?ACCENT:GRAY);b.setBackground(square(selected?TINT:WHITE,selected?ACCENT:LINE));if(Build.VERSION.SDK_INT>=30)b.setStateDescription(s(selected?"a11y_selected":"a11y_not_selected"));}

    /** Single top app bar: navigation affordance, one title, optional actions. */
    LinearLayout appBar(){
        LinearLayout bar=row();bar.setMinimumHeight(dp(56));bar.setPadding(dp(4),0,dp(4),0);bar.setBackgroundColor(WHITE);return bar;
    }
    TextView navButton(String glyph,Runnable action,int size){
        TextView b=text(glyph,size,INK);b.setGravity(Gravity.CENTER);b.setMinWidth(dp(48));b.setMinHeight(dp(48));b.setPadding(0,0,0,0);
        b.setBackground(press(WHITE,PALE));b.setOnClickListener(v->action.run());b.setFocusable(true);return b;
    }
    /** Bottom bar tabs: label only, selected state carried by colour and an underline. */
    View tab(String label,boolean selected,Runnable action){
        LinearLayout box=column();box.setGravity(Gravity.CENTER);
        TextView name=text(label,14,GRAY);name.setGravity(Gravity.CENTER);box.addView(name,new LinearLayout.LayoutParams(-1,dp(54)));
        View mark=new View(activity);mark.setBackgroundColor(WHITE);box.addView(mark,new LinearLayout.LayoutParams(-1,dp(2)));
        box.setTag(name);box.setMinimumHeight(dp(56));box.setFocusable(true);box.setOnClickListener(v->action.run());box.setContentDescription(label);
        setTab(box,selected);return box;
    }
    void setTab(View view,boolean selected){LinearLayout box=(LinearLayout)view;box.setSelected(selected);TextView label=(TextView)box.getTag();
        label.setTextColor(selected?ACCENT:GRAY);if(selected)strong(label);else label.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        box.getChildAt(1).setBackgroundColor(selected?ACCENT:WHITE);
        if(Build.VERSION.SDK_INT>=30)box.setStateDescription(s(selected?"a11y_selected":"a11y_not_selected"));}

    EditText input(String value,String hint,int type){
        EditText edit=new EditText(activity);edit.setTextSize(16);edit.setTextColor(INK);edit.setHintTextColor(GRAY);edit.setText(value);edit.setHint(hint);edit.setInputType(type);edit.setPadding(dp(4),dp(10),dp(4),dp(10));edit.setMinHeight(dp(44));
        StateListDrawable frame=new StateListDrawable();frame.addState(new int[]{android.R.attr.state_focused},square(WHITE,ACCENT));frame.addState(new int[]{},square(WHITE,CONTROL));
        edit.setBackground(frame);edit.setSelectAllOnFocus(true);return edit;
    }
    void line(LinearLayout parent){View v=new View(activity);v.setBackgroundColor(LINE);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
    void pad(View v,int n){v.setPadding(dp(n),dp(n),dp(n),dp(n));}
    void equal(LinearLayout row,View child){row.addView(child,new LinearLayout.LayoutParams(0,-2,1));}
    void margin(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);}
    void gap(LinearLayout parent,int height){parent.addView(new View(activity),new LinearLayout.LayoutParams(-1,dp(height)));}
    TextView section(String title){TextView t=text(title,13,GRAY);t.setPadding(dp(4),dp(18),dp(4),dp(8));if(Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(true);return t;}
    LinearLayout label(String title,View control){LinearLayout r=row();TextView t=text(title,16,INK);r.addView(t,new LinearLayout.LayoutParams(0,dp(52),1));r.addView(control);return r;}
    void toast(String message){if(notice!=null)notice.accept(message);}

    static final class Row {
        final String title,value;final Runnable click,hold;
        int style;String detail="",actionLabel="",actionKey,fieldKey;Runnable action;Boolean checked;
        Row field(String key){fieldKey=key;return this;}
        Row typed(Object value){checked=value instanceof Boolean?(Boolean)value:null;return this;}
        Row(String title,String value,Runnable click){this(title,value,click,null);}
        Row(String title,String value,Runnable click,Runnable hold){this.title=title;this.value=value;this.click=click;this.hold=hold;}
        Row game(String description){detail=description;return this;}
        Row record(String description,Runnable replace){style=2;detail=description;actionKey="action_replace";action=replace;return this;}
    }
    final class Holder {
        LinearLayout root;TextView title,detail,value,action;Switch toggle;
    }
    final class Rows extends BaseAdapter {
        List<Row> data=new ArrayList<>();
        void set(List<Row> items){data=items;notifyDataSetChanged();}
        @Override public int getCount(){return data.size();}
        @Override public Row getItem(int p){return data.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public int getViewTypeCount(){return 1;}
        @Override public int getItemViewType(int p){return 0;}
        Holder create(){
            Holder h=new Holder();h.root=row();h.root.setPadding(dp(16),dp(12),dp(8),dp(12));h.root.setMinimumHeight(dp(60));h.root.setBackground(press(WHITE,PALE));
            LinearLayout left=column();left.setBackgroundColor(Color.TRANSPARENT);
            h.title=text("",16,INK);h.title.setMaxLines(1);h.title.setEllipsize(TextUtils.TruncateAt.END);left.addView(h.title);
            h.detail=text("",13,GRAY);h.detail.setMaxLines(1);h.detail.setEllipsize(TextUtils.TruncateAt.END);margin(left,h.detail,3);h.root.addView(left,new LinearLayout.LayoutParams(0,-2,1));
            h.value=text("",15,GRAY);h.value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);h.value.setMaxLines(1);h.value.setMaxWidth(dp(150));h.value.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams valueParams=new LinearLayout.LayoutParams(-2,-2);valueParams.leftMargin=dp(12);h.root.addView(h.value,valueParams);
            h.action=textButton("",()->{});h.action.setTextSize(15);LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(-2,-2);actionParams.leftMargin=dp(4);h.root.addView(h.action,actionParams);
            h.toggle=new Switch(activity);h.toggle.setBackgroundColor(Color.TRANSPARENT);h.toggle.setMinHeight(dp(48));h.toggle.setMinWidth(dp(48));h.toggle.setGravity(Gravity.CENTER);h.root.addView(h.toggle,new LinearLayout.LayoutParams(-2,dp(48)));
            h.root.setTag(h);return h;
        }
        @Override public View getView(int p,View convert,android.view.ViewGroup group){
            Row item=getItem(p);Holder h=convert==null?create():(Holder)convert.getTag();boolean menu=item.style!=2;
            h.title.setText(item.title);h.detail.setText(item.detail);h.detail.setVisibility(item.detail.isEmpty()?View.GONE:View.VISIBLE);
            boolean bool=item.style!=2&&item.checked!=null&&item.click!=null;h.value.setText(item.value);h.value.setVisibility(bool||item.value.isEmpty()?View.GONE:View.VISIBLE);
            h.toggle.setVisibility(bool?View.VISIBLE:View.GONE);h.toggle.setChecked(Boolean.TRUE.equals(item.checked));h.toggle.setContentDescription(bool?item.title:null);
            h.toggle.setOnClickListener(!bool?null:v->item.click.run());h.toggle.setEnabled(bool);h.toggle.setClickable(bool);h.toggle.setFocusable(bool);
            h.toggle.setOnLongClickListener(!bool||item.hold==null?null:v->{item.hold.run();return true;});h.toggle.setLongClickable(bool&&item.hold!=null);
            Runnable action=item.action!=null?item.action:item.hold;String label=item.action!=null?(item.actionKey==null?item.actionLabel:s(item.actionKey)):"⋮";
            h.action.setVisibility(action==null?View.GONE:View.VISIBLE);h.action.setText(action==null?"":label);
            h.action.setContentDescription(action==null?null:item.action!=null?label+" "+item.title:s(item.fieldKey==null?"a11y_more_operations":"a11y_field_operations",item.fieldKey==null?item.title:item.fieldKey));
            h.action.setOnClickListener(action==null?null:v->action.run());h.action.setEnabled(action!=null);h.action.setClickable(action!=null);h.action.setFocusable(action!=null);
            h.root.setOnClickListener(item.click==null?null:v->item.click.run());h.root.setClickable(item.click!=null);
            h.root.setOnLongClickListener(item.hold==null?null:v->{item.hold.run();return true;});h.root.setLongClickable(item.hold!=null);return h.root;
        }
    }
    ListView list(Rows adapter){ListView list=new ListView(activity);list.setDivider(new ColorDrawable(LINE));list.setDividerHeight(dp(1));list.setCacheColorHint(WHITE);list.setAdapter(adapter);list.setClipToPadding(false);return list;}
    ListView list(List<Row> rows){Rows adapter=new Rows();adapter.set(rows);return list(adapter);}

    Dialog dialog(String title,View content){
        Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout box=column();pad(box,20);
        TextView name=text(title,19,INK);strong(name);name.setMinHeight(dp(32));if(Build.VERSION.SDK_INT>=28)name.setAccessibilityHeading(true);box.addView(name,new LinearLayout.LayoutParams(-1,-2));
        margin(box,content,12);
        dialog.setContentView(box);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(WHITE));window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        dialog.setOnShowListener(d->{Window w=dialog.getWindow();if(w!=null)w.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(40),dp(520)),-2);});return dialog;
    }
    void choose(String title,List<String> choices,java.util.function.IntConsumer selected){
        LinearLayout content=column();Dialog dialog=dialog(title,content);ScrollView scroll=new ScrollView(activity);LinearLayout options=column();scroll.addView(options);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(360),dp(48*choices.size()))));
        for(int i=0;i<choices.size();i++){final int n=i;TextView option=text(choices.get(i),16,INK);option.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);option.setMinHeight(dp(48));option.setBackground(press(WHITE,PALE));option.setOnClickListener(v->{dialog.dismiss();selected.accept(n);});options.addView(option,new LinearLayout.LayoutParams(-1,-2));if(i+1<choices.size())line(options);}
        dialog.show();
    }
    interface Submit {void accept(String value)throws Exception;}
    void edit(String title,String value,int type,boolean multiline,Submit save){
        LinearLayout content=column();EditText edit=input(value,"",type);edit.setSingleLine(!multiline);if(multiline){edit.setMinLines(3);edit.setMaxLines(7);edit.setGravity(Gravity.TOP);}content.addView(edit,new LinearLayout.LayoutParams(-1,-2));
        TextView error=text("",13,ERROR);error.setVisibility(View.GONE);margin(content,error,6);Dialog dialog=dialog(title,content);LinearLayout actions=row();equal(actions,textButton(s("action_cancel"),dialog::dismiss));equal(actions,button(s("action_save"),true,()->{try{save.accept(edit.getText().toString());dialog.dismiss();}catch(Exception e){error.setText(error(e));error.setVisibility(View.VISIBLE);}}));margin(content,actions,16);dialog.show();edit.requestFocus();
    }
    void confirm(String title,Runnable yes){confirm(title,s("action_confirm"),yes);}
    void confirm(String title,String positive,Runnable yes){LinearLayout content=column();Dialog dialog=dialog(title,content);LinearLayout actions=row();equal(actions,textButton(s("action_cancel"),dialog::dismiss));equal(actions,button(positive,true,()->{dialog.dismiss();yes.run();}));content.addView(actions);dialog.show();}
}
