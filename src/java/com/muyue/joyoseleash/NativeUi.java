package com.muyue.joyoseleash;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Shared native design primitives. Geometry, focus and state are explicit. */
final class NativeUi {
    static final int INK=0xff191d26,GRAY=0xff626b78,LINE=0xffdfe3e9,WHITE=Color.WHITE;
    static final int PALE=0xfff4f6f9,ACCENT=0xff2e55d9,TINT=0xffeef2ff,ERROR=0xffa52020,CONTROL=0xff87919f;
    final Activity activity;
    java.util.function.Consumer<String> notice;
    NativeUi(Activity activity){this.activity=activity;}
    int dp(float value){return (int)(value*activity.getResources().getDisplayMetrics().density+.5f);}
    LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);v.setBackgroundColor(WHITE);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(activity);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    TextView text(String value,int sp,int color){TextView v=new TextView(activity);v.setText(value);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);return v;}
    void strong(TextView text){text.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}
    GradientDrawable square(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(0);if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    StateListDrawable press(int normal,int pressed){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_pressed},square(pressed,0));d.addState(new int[]{},square(normal,0));return d;}
    TextView button(String label,boolean primary,Runnable action){
        TextView b=text(label,16,primary?WHITE:INK);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(48));b.setMinimumWidth(dp(48));b.setPadding(dp(14),dp(12),dp(14),dp(12));
        if(primary)b.setBackground(press(ACCENT,0xff2444b3));else b.setBackground(square(WHITE,LINE));
        b.setOnClickListener(v->action.run());b.setFocusable(true);return b;
    }
    TextView textButton(String label,Runnable action){TextView b=button(label,false,action);b.setTextColor(ACCENT);b.setBackground(press(WHITE,TINT));return b;}
    TextView choice(String label,boolean selected,Runnable action){TextView b=button(label,false,action);b.setTextSize(14);b.setPadding(dp(6),dp(12),dp(6),dp(12));selectChoice(b,selected);return b;}
    void selectChoice(TextView b,boolean selected){b.setSelected(selected);b.setTextColor(selected?ACCENT:GRAY);b.setBackground(square(selected?TINT:WHITE,selected?ACCENT:LINE));if(Build.VERSION.SDK_INT>=30)b.setStateDescription(selected?"已选中":"未选中");}
    View tab(String label,boolean selected,Runnable action){
        LinearLayout box=column();View mark=new View(activity);mark.setBackgroundColor(selected?ACCENT:WHITE);box.addView(mark,new LinearLayout.LayoutParams(-1,dp(3)));
        TextView name=text(label,15,selected?ACCENT:GRAY);name.setGravity(Gravity.CENTER);box.addView(name,new LinearLayout.LayoutParams(-1,dp(53)));box.setMinimumWidth(dp(48));box.setMinimumHeight(dp(56));box.setFocusable(true);box.setOnClickListener(v->action.run());box.setContentDescription(label);box.setTag(name);setTab(box,selected);return box;
    }
    void setTab(View view,boolean selected){LinearLayout box=(LinearLayout)view;box.setSelected(selected);box.getChildAt(0).setBackgroundColor(selected?ACCENT:WHITE);TextView label=(TextView)box.getTag();label.setTextColor(selected?ACCENT:GRAY);if(selected)strong(label);else label.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));if(Build.VERSION.SDK_INT>=30)box.setStateDescription(selected?"已选中":"未选中");}
    EditText input(String value,String hint,int type){
        EditText edit=new EditText(activity);edit.setTextSize(16);edit.setTextColor(INK);edit.setHintTextColor(GRAY);edit.setText(value);edit.setHint(hint);edit.setInputType(type);edit.setPadding(dp(12),dp(12),dp(12),dp(12));edit.setMinHeight(dp(48));StateListDrawable frame=new StateListDrawable();frame.addState(new int[]{android.R.attr.state_focused},square(WHITE,ACCENT));frame.addState(new int[]{},square(WHITE,CONTROL));edit.setBackground(frame);edit.setSelectAllOnFocus(true);return edit;
    }
    void line(LinearLayout parent){View v=new View(activity);v.setBackgroundColor(LINE);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
    void pad(View v,int n){v.setPadding(dp(n),dp(n),dp(n),dp(n));}
    void equal(LinearLayout row,View child){row.addView(child,new LinearLayout.LayoutParams(0,-2,1));}
    void margin(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);}
    void gap(LinearLayout parent,int height){parent.addView(new View(activity),new LinearLayout.LayoutParams(-1,dp(height)));}
    TextView section(String title){TextView t=text(title,14,GRAY);t.setPadding(dp(20),dp(20),dp(20),dp(10));if(Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(true);return t;}
    LinearLayout label(String title,View control){LinearLayout r=row();TextView t=text(title,16,INK);r.addView(t,new LinearLayout.LayoutParams(0,dp(56),1));r.addView(control);return r;}
    void toast(String message){if(notice!=null)notice.accept(message);}

    static final class Row {
        final String title,value;final Runnable click,hold;
        int style;String detail="",actionLabel="";Runnable action;
        Row(String title,String value,Runnable click){this(title,value,click,null);}
        Row(String title,String value,Runnable click,Runnable hold){this.title=title;this.value=value;this.click=click;this.hold=hold;}
        Row game(String description){style=1;detail=description;return this;}
        Row record(String description,Runnable replace){style=2;detail=description;actionLabel="替换";action=replace;return this;}
    }
    final class Holder {
        LinearLayout root;TextView title,detail,value,action;CheckBox toggle;
    }
    final class Rows extends BaseAdapter {
        List<Row> data=new ArrayList<>();
        void set(List<Row> items){data=items;notifyDataSetChanged();}
        @Override public int getCount(){return data.size();}
        @Override public Row getItem(int p){return data.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public int getViewTypeCount(){return 3;}
        @Override public int getItemViewType(int p){return data.get(p).style;}
        Holder create(int style){
            Holder h=new Holder();h.root=row();h.root.setPadding(dp(20),dp(14),dp(20),dp(14));h.root.setMinimumHeight(dp(style==0?64:92));h.root.setBackground(press(WHITE,PALE));
            LinearLayout left=column();left.setBackgroundColor(Color.TRANSPARENT);h.title=text("",style==2?20:17,INK);h.title.setMaxLines(2);h.title.setEllipsize(TextUtils.TruncateAt.END);if(style>0)strong(h.title);left.addView(h.title);
            h.detail=text("",14,GRAY);h.detail.setMaxLines(1);h.detail.setEllipsize(TextUtils.TruncateAt.END);margin(left,h.detail,7);h.root.addView(left,new LinearLayout.LayoutParams(0,-2,1));
            h.value=text("",style==1?26:16,style==1?INK:GRAY);h.value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);h.value.setMaxLines(1);h.value.setMaxWidth(dp(146));h.value.setEllipsize(TextUtils.TruncateAt.END);if(style==1)strong(h.value);LinearLayout.LayoutParams valueParams=new LinearLayout.LayoutParams(-2,-2);valueParams.leftMargin=dp(14);h.root.addView(h.value,valueParams);
            h.action=textButton("",()->{});h.action.setTextSize(15);LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(-2,-2);actionParams.leftMargin=dp(12);h.root.addView(h.action,actionParams);
            h.toggle=new CheckBox(activity);h.toggle.setBackgroundColor(Color.TRANSPARENT);h.toggle.setButtonDrawable(new CheckMark());h.toggle.setMinimumHeight(dp(48));h.toggle.setMinimumWidth(dp(48));h.toggle.setGravity(Gravity.CENTER);h.root.addView(h.toggle,new LinearLayout.LayoutParams(dp(48),dp(48)));
            h.root.setTag(h);return h;
        }
        @Override public View getView(int p,View convert,android.view.ViewGroup group){
            Row item=getItem(p);Holder h=convert==null?create(item.style):(Holder)convert.getTag();h.title.setText(item.title);h.detail.setText(item.detail);h.detail.setVisibility(item.detail.isEmpty()?View.GONE:View.VISIBLE);
            boolean bool=item.style==0&&(item.value.equals("开")||item.value.equals("关"));h.value.setText(item.value);h.value.setVisibility(bool||item.value.isEmpty()?View.GONE:View.VISIBLE);
            h.toggle.setVisibility(bool?View.VISIBLE:View.GONE);h.toggle.setChecked(item.value.equals("开"));h.toggle.setContentDescription(item.title);h.toggle.setOnClickListener(v->{if(item.click!=null)item.click.run();});
            h.action.setVisibility(item.action==null?View.GONE:View.VISIBLE);h.action.setText(item.actionLabel);h.action.setContentDescription(item.actionLabel+" "+item.title);h.action.setOnClickListener(v->{if(item.action!=null)item.action.run();});
            h.root.setOnClickListener(item.click==null?null:v->item.click.run());h.root.setOnLongClickListener(item.hold==null?null:v->{item.hold.run();return true;});return h.root;
        }
    }
    final class CheckMark extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public boolean isStateful(){return true;}
        @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
        @Override public int getIntrinsicWidth(){return dp(24);}
        @Override public int getIntrinsicHeight(){return dp(24);}
        @Override public void draw(Canvas canvas){boolean checked=false;for(int state:getState())if(state==android.R.attr.state_checked)checked=true;Rect b=getBounds();float inset=dp(2);RectF rect=new RectF(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset);p.setStyle(Paint.Style.FILL);p.setColor(checked?ACCENT:WHITE);canvas.drawRect(rect,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(checked?ACCENT:GRAY);canvas.drawRect(rect,p);if(checked){p.setColor(WHITE);p.setStrokeWidth(dp(2));Path line=new Path();line.moveTo(rect.left+dp(4),rect.centerY());line.lineTo(rect.left+dp(8),rect.bottom-dp(5));line.lineTo(rect.right-dp(3),rect.top+dp(5));canvas.drawPath(line,p);}}
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    ListView list(Rows adapter){ListView list=new ListView(activity);list.setDivider(new ColorDrawable(LINE));list.setDividerHeight(dp(1));list.setCacheColorHint(WHITE);list.setAdapter(adapter);list.setClipToPadding(false);return list;}
    ListView list(List<Row> rows){Rows adapter=new Rows();adapter.set(rows);return list(adapter);}

    Dialog dialog(String title,View content){
        Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout box=column();pad(box,20);TextView name=text(title,21,INK);strong(name);name.setMinHeight(dp(36));if(Build.VERSION.SDK_INT>=28)name.setAccessibilityHeading(true);box.addView(name,new LinearLayout.LayoutParams(-1,-2));margin(box,content,16);
        dialog.setContentView(box);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(WHITE));window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        dialog.setOnShowListener(d->{Window w=dialog.getWindow();if(w!=null)w.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(40),dp(520)),-2);});return dialog;
    }
    void choose(String title,List<String> choices,java.util.function.IntConsumer selected){
        LinearLayout content=column();Dialog dialog=dialog(title,content);ScrollView scroll=new ScrollView(activity);LinearLayout options=column();scroll.addView(options);content.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(384),dp(48*choices.size()))));
        for(int i=0;i<choices.size();i++){final int n=i;TextView option=textButton(choices.get(i),()->{dialog.dismiss();selected.accept(n);});option.setTextColor(INK);option.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);options.addView(option,new LinearLayout.LayoutParams(-1,-2));if(i+1<choices.size())line(options);}dialog.show();
    }
    interface Submit {void accept(String value)throws Exception;}
    void edit(String title,String value,int type,boolean multiline,Submit save){
        LinearLayout content=column();EditText edit=input(value,"",type);edit.setSingleLine(!multiline);if(multiline){edit.setMinLines(3);edit.setMaxLines(7);edit.setGravity(Gravity.TOP);}content.addView(edit,new LinearLayout.LayoutParams(-1,-2));
        TextView error=text("",14,ERROR);error.setVisibility(View.GONE);margin(content,error,8);Dialog dialog=dialog(title,content);LinearLayout actions=row();equal(actions,textButton("取消",dialog::dismiss));equal(actions,button("保存",true,()->{try{save.accept(edit.getText().toString());dialog.dismiss();}catch(Exception e){error.setText(e.getMessage());error.setVisibility(View.VISIBLE);}}));margin(content,actions,20);dialog.show();edit.requestFocus();
    }
    void confirm(String title,Runnable yes){confirm(title,"确定",yes);}
    void confirm(String title,String positive,Runnable yes){LinearLayout content=column();Dialog dialog=dialog(title,content);LinearLayout actions=row();equal(actions,textButton("取消",dialog::dismiss));equal(actions,button(positive,true,()->{dialog.dismiss();yes.run();}));content.addView(actions);dialog.show();}
}
