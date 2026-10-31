package com.gk.octaveanalyzer;
import android.content.Context;
import android.graphics.*;
import android.view.View;
public final class SpectrumView extends View {
 private double[] bands,leq;private String[] names=OctaveAnalyzer.THIRD;private boolean calibrated=false;
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final float den;
 public SpectrumView(Context ctx){super(ctx);den=getResources().getDisplayMetrics().density;setBackgroundColor(Color.rgb(18,31,48));}
 private float dp(float x){return den*x;}
 public void show(String[] labels,double[] values,double[] avg,boolean cal){names=labels;bands=values;leq=avg;calibrated=cal;requestLayout();invalidate();}
 @Override protected void onMeasure(int w,int h){setMeasuredDimension(Math.max(MeasureSpec.getSize(w),(int)dp(55+names.length*(names.length>12?34:50))),(int)dp(260));}
 @Override protected void onDraw(Canvas c){
  float left=dp(43),top=dp(19),bottom=getHeight()-dp(45),right=getWidth()-dp(15);
  double min=calibrated?20:-100,max=calibrated?140:0;
  p.setTextSize(dp(11));p.setTypeface(Typeface.DEFAULT);
  for(int j=0;j<=6;j++){
   double val=min+(max-min)*j/6.0;
   float y=y(val,top,bottom,min,max);p.setColor(Color.rgb(50,65,83));p.setStrokeWidth(dp(1));c.drawLine(left,y,right,y,p);
   p.setTextAlign(Paint.Align.RIGHT);p.setColor(Color.LTGRAY);c.drawText(String.valueOf(Math.round(val)),left-dp(5),y+dp(3),p);
  }
  if(bands==null){p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.LTGRAY);p.setTextSize(dp(13));c.drawText("Press Start to see the spectrum",getWidth()/2f,(top+bottom)/2f,p);return;}
  float cw=(right-left)/names.length;
  for(int i=0;i<names.length;i++){
   float x=left+i*cw+dp(3),width=cw-dp(7);
   float yy=y(bands[i],top,bottom,min,max);
   if(bands[i]>min){p.setColor(Color.rgb(72,223,186));c.drawRoundRect(new RectF(x,yy,x+width,bottom),dp(2),dp(2),p);}
   if(leq!=null&&i<leq.length){p.setColor(Color.rgb(255,204,106));p.setStrokeWidth(dp(2));float ly=y(leq[i],top,bottom,min,max);c.drawLine(x,ly,x+width,ly,p);}
   p.setColor(Color.LTGRAY);p.setTextSize(dp(10));p.setTextAlign(Paint.Align.CENTER);c.drawText(names[i],x+width/2,bottom+dp(17),p);
  }
  p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(10));c.drawText("Band center Hz     Green: live   Yellow: Leq",getWidth()/2f,getHeight()-dp(8),p);
 }
 private float y(double val,float top,float bottom,double min,double max){return (float)(bottom-Math.max(0,Math.min(1,(val-min)/(max-min)))*(bottom-top));}
}
