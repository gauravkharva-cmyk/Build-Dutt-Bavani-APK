package com.gk.octaveanalyzer;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.media.audiofx.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Offline live spectrum and 1/1, 1/3 octave-band analyzer. */
public final class MainActivity extends Activity {
 private final Object lock=new Object();
 private static final int BG=Color.rgb(12,21,36),PANEL=Color.rgb(22,36,54);
 private static final int GREEN=Color.rgb(72,223,186),WHITE=Color.rgb(233,241,249),GREY=Color.rgb(170,188,203);
 private volatile boolean recording=false;
 private volatile int mode=3,weighting=0;
 private volatile int durationSeconds=60;
 private volatile long measurementStartMs=0;
 private volatile boolean autoStopped=false;
 private int rate=48000,frames=0;
 private volatile AudioRecord audio;
 private Thread worker;
 private OctaveAnalyzer.Result last;
 private double[] sumP,leq; private double overallP=0,overallLeq=OctaveAnalyzer.FLOOR,calibration=Double.NaN;
 private TextView status,level,units,average,details,warning,durationBadge,calibrationBadge;
 private SpectrumView chart;private LinearLayout table;
 private Button start,one,third,calButton,saveButton;
 private String pendingCsv;
 private String source="not started";

 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  // v1.2 deliberately ignores earlier unconfirmed offsets. Old values can overstate SPL.
  if(getPreferences(MODE_PRIVATE).getBoolean("offsetVerified",false)){
   long bits=getPreferences(MODE_PRIVATE).getLong("offset",Double.doubleToLongBits(Double.NaN));
   calibration=Double.longBitsToDouble(bits);
  }else{
   calibration=Double.NaN;
   getPreferences(MODE_PRIVATE).edit().remove("offset").apply();
  }
  durationSeconds=getPreferences(MODE_PRIVATE).getInt("durationSeconds",60);
  ui();reset();refresh();
 }
 private int px(float d){return (int)(d*getResources().getDisplayMetrics().density+0.5f);}
 private LinearLayout col(){LinearLayout x=new LinearLayout(this);x.setOrientation(1);return x;}
 private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(0);x.setGravity(Gravity.CENTER_VERTICAL);return x;}
 private GradientDrawable bg(int c){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(px(14));return d;}
 private TextView txt(String s,int size,int color,boolean bold){
  TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(size);
  if(bold)v.setTypeface(null,Typeface.BOLD);return v;
 }
 private Button btn(String s,int color){
  Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(BG);b.setTextSize(13);
  b.setTypeface(null,Typeface.BOLD);b.setBackground(bg(color));return b;
 }
 private LinearLayout.LayoutParams lp(int w,int h,int bottom){
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.bottomMargin=px(bottom);return p;
 }
 private void ui(){
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
  LinearLayout content=col();content.setPadding(px(16),px(16),px(16),px(24));
  scroll.addView(content);setContentView(scroll);
  LinearLayout top=row();
  TextView brand=txt("GK  /  ACOUSTICS LAB",12,GREEN,true);
  top.addView(brand,new LinearLayout.LayoutParams(0,px(46),1));
  brand.setGravity(Gravity.CENTER_VERTICAL);
  Button menuButton=btn("⋮",PANEL);
  menuButton.setTextSize(24);menuButton.setTextColor(WHITE);
  menuButton.setContentDescription("Options: measurement time, save CSV, calibration");
  top.addView(menuButton,new LinearLayout.LayoutParams(px(52),px(44)));
  menuButton.setOnClickListener(v->showOptions(menuButton));
  content.addView(top,lp(-1,px(50),3));
  content.addView(txt("Octave Sound Analyzer",26,WHITE,true),lp(-1,-2,4));
  content.addView(txt("Live mobile microphone  •  1/1 & 1/3 octave",13,GREY,false),lp(-1,-2,5));
  durationBadge=txt("",13,GREEN,true);
  content.addView(durationBadge,lp(-1,-2,14));
  LinearLayout panel=col();panel.setPadding(px(17),px(15),px(17),px(14));
  panel.setBackground(bg(PANEL));content.addView(panel,lp(-1,-2,14));
  status=txt("Ready",13,GREEN,true);panel.addView(status,lp(-1,-2,6));
  level=txt("--.-",48,WHITE,true);panel.addView(level);
  units=txt("dBFS  •  uncalibrated",13,GREY,false);panel.addView(units,lp(-1,-2,4));
  calibrationBadge=txt("",13,Color.rgb(252,206,113),true);panel.addView(calibrationBadge,lp(-1,-2,9));
  average=txt("Leq: --.-",16,GREEN,true);panel.addView(average,lp(-1,-2,8));
  details=txt("Sample rate: --",12,GREY,false);panel.addView(details,lp(-1,-2,3));
  warning=txt("",12,Color.rgb(255,134,128),true);panel.addView(warning);
  content.addView(txt("BAND WIDTH",12,GREY,true),lp(-1,-2,7));
  LinearLayout modes=row();content.addView(modes,lp(-1,px(48),14));
  one=btn("1/1 Octave",PANEL);third=btn("1/3 Octave",GREEN);
  LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,-1,1);p1.rightMargin=px(8);
  modes.addView(one,p1);modes.addView(third,new LinearLayout.LayoutParams(0,-1,1));
  one.setOnClickListener(v->setMode(1));third.setOnClickListener(v->setMode(3));
  content.addView(txt("FREQUENCY WEIGHTING",12,GREY,true),lp(-1,-2,5));
  Spinner choice=new Spinner(this);
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
    new String[]{"Z — unweighted","A weighting","C weighting"});
  adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  choice.setAdapter(adapter);content.addView(choice,lp(-1,px(48),12));
  choice.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
   public void onItemSelected(android.widget.AdapterView<?> parent,View v,int position,long id){
    if(weighting!=position){weighting=position;reset();refresh();}
   }
  });
  start=btn("Start measuring",GREEN);content.addView(start,lp(-1,px(51),11));
  start.setOnClickListener(v->{if(recording)stop();else begin();});
  LinearLayout actions=row();content.addView(actions,lp(-1,px(48),14));
  calButton=btn("Calibration / Reset",Color.rgb(129,185,237));
  saveButton=btn("Save CSV",Color.rgb(252,206,113));
  LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1);left.rightMargin=px(8);
  actions.addView(calButton,left);actions.addView(saveButton,new LinearLayout.LayoutParams(0,-1,1));
  calButton.setOnClickListener(v->calibrate());saveButton.setOnClickListener(v->export());
  content.addView(txt("LIVE FREQUENCY SPECTRUM",12,GREY,true),lp(-1,-2,8));
  chart=new SpectrumView(this);
  HorizontalScrollView h=new HorizontalScrollView(this);h.setBackground(bg(PANEL));h.addView(chart);
  content.addView(h,lp(-1,px(260),12));
  content.addView(txt("BAND LEVELS (Hz / LIVE / Leq)",12,GREY,true),lp(-1,-2,7));
  table=col();table.setBackground(bg(PANEL));table.setPadding(px(12),px(10),px(12),px(10));
  content.addView(table,lp(-1,-2,12));makeTable();
  content.addView(txt("Negative values are expected in dBFS. Calibrate with a known 1 kHz acoustic reference for approximate positive dB SPL readings. This FFT analyzer is not IEC 61260-1 Class 1/2 or IEC 61672 certified. Use certified equipment for formal testing.",12,GREY,false),lp(-1,-2,13));
  TextView credit=txt("Developed By: Gaurav Kharva",12,GREY,true);
  credit.setGravity(Gravity.END);content.addView(credit);
 }
 private TextView cell(String label,int color){
  TextView t=txt(label,13,color,false);t.setGravity(Gravity.CENTER_VERTICAL);
  t.setLayoutParams(new LinearLayout.LayoutParams(0,px(25),1));return t;
 }
 private void makeTable(){
  if(table==null)return;
  table.removeAllViews();
  String[] names=mode==1?OctaveAnalyzer.OCT:OctaveAnalyzer.THIRD;
  LinearLayout head=row();head.addView(cell("Frequency",GREY));head.addView(cell("Live",GREY));head.addView(cell("Leq",GREY));
  table.addView(head);
  for(String n:names){
   LinearLayout r=row();r.addView(cell(n+" Hz",WHITE));
   r.addView(cell("--",GREEN));r.addView(cell("--",Color.rgb(252,206,113)));table.addView(r);
  }
 }
 private void setMode(int b){
  mode=b;one.setBackground(bg(b==1?GREEN:PANEL));one.setTextColor(b==1?BG:WHITE);
  third.setBackground(bg(b==3?GREEN:PANEL));third.setTextColor(b==3?BG:WHITE);
  makeTable();reset();refresh();
 }
 private void reset(){
  autoStopped=false;
  synchronized(lock){frames=0;sumP=null;leq=null;overallP=0;overallLeq=OctaveAnalyzer.FLOOR;last=null;}
  if(chart!=null)chart.show(mode==1?OctaveAnalyzer.OCT:OctaveAnalyzer.THIRD,null,null,!Double.isNaN(calibration));
 }
 private String fmt(double d){return String.format(Locale.US,"%.1f",d);}
 private double withCal(double d){return d+(Double.isNaN(calibration)?0:calibration);}
 private double[] offset(double[] a){
  if(a==null)return null;double[] r=a.clone();
  if(!Double.isNaN(calibration))for(int i=0;i<r.length;i++)r[i]+=calibration;
  return r;
 }
 private void refresh(){
  if(status==null)return;
  OctaveAnalyzer.Result r;double[] l;double sum;int count;
  synchronized(lock){r=last;l=leq==null?null:leq.clone();sum=overallLeq;count=frames;}
  boolean c=!Double.isNaN(calibration);
  status.setText(recording?"● LIVE MEASUREMENT":(autoStopped?"● TIMED MEASUREMENT COMPLETE":"● Ready / stopped"));
  status.setTextColor(recording?GREEN:GREY);
  if(calibrationBadge!=null){
   calibrationBadge.setText(c?"CALIBRATED (indicative)   •   offset "+fmt(calibration)+" dB":
    "UNVERIFIED CALIBRATION CLEARED   •   dBFS only");
   calibrationBadge.setTextColor(c?GREEN:Color.rgb(252,206,113));
  }
  level.setText(r==null?"--.-":fmt(withCal(r.overall)));
  units.setText((c?"dB SPL (1 kHz calibrated)":"dBFS (uncalibrated)")+"  •  "+new String[]{"Z","A","C"}[weighting]+" weighting");
  average.setText(r==null?"Leq: --.-":"Leq: "+fmt(withCal(sum))+(c?" dB SPL":" dBFS"));
  long measuredSec=Math.round((double)count*OctaveAnalyzer.N/rate);
  details.setText(String.format(Locale.US,"Time %02d:%02d   •   %d Hz   •   %s",
    measuredSec/60,measuredSec%60,rate,source));
  if(durationBadge!=null)durationBadge.setText("Measurement time: "+durationLabel()
    +(recording&&durationSeconds>0?"  •  remaining "+Math.max(0,(durationSeconds*1000L-(SystemClock.elapsedRealtime()-measurementStartMs)+999)/1000)+" sec":""));
  warning.setText(r!=null&&r.clip?"⚠ Mic clipping: levels may be inaccurate":"");
  chart.show(mode==1?OctaveAnalyzer.OCT:OctaveAnalyzer.THIRD,r==null?null:offset(r.live),offset(l),c);
  if(table.getChildCount()>1){
   for(int i=0;i<table.getChildCount()-1;i++){
    LinearLayout row=(LinearLayout)table.getChildAt(i+1);
    ((TextView)row.getChildAt(1)).setText(r==null?"--":fmt(withCal(r.live[i])));
    ((TextView)row.getChildAt(2)).setText(l==null?"--":fmt(withCal(l[i])));
   }
  }
  calButton.setEnabled(true);saveButton.setEnabled(r!=null);
  start.setText(recording?"Stop measuring":"Start measuring");
  start.setBackground(bg(recording?Color.rgb(255,146,124):GREEN));
 }
 private void begin(){
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},101);return;
  }
  if(recording)return;
  AudioRecord mic=null;
  try{
   int selectedRate=0,selectedSource=0;
   for(int sr:new int[]{48000,44100}){
    for(int src:new int[]{MediaRecorder.AudioSource.UNPROCESSED,MediaRecorder.AudioSource.MIC}){
     int min=AudioRecord.getMinBufferSize(sr,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
     if(min<=0)continue;
     AudioRecord candidate=null;
     try{
      candidate=new AudioRecord(src,sr,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min*2,OctaveAnalyzer.N*2));
      if(candidate.getState()==AudioRecord.STATE_INITIALIZED){mic=candidate;selectedRate=sr;selectedSource=src;break;}
     }catch(Exception ignored){}
     if(candidate!=null)candidate.release();
    }
    if(mic!=null)break;
   }
   if(mic==null){toast("Unable to access microphone");return;}
   rate=selectedRate;source=selectedSource==MediaRecorder.AudioSource.UNPROCESSED?"Unprocessed mic":"Microphone";
   reset();disableAudioEffects(mic.getAudioSessionId());
   mic.startRecording();
   if(mic.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("Microphone did not start");
   audio=mic;autoStopped=false;measurementStartMs=SystemClock.elapsedRealtime();
    recording=true;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
   final AudioRecord use=mic;
   worker=new Thread(()->capture(use),"gk-octave-capture");worker.start();refresh();
  }catch(Exception ex){
   recording=false;if(mic!=null){try{mic.release();}catch(Exception ignored){}}
   audio=null;toast("Microphone: "+ex.getMessage());refresh();
  }
 }
 private void disableAudioEffects(int session){
  try{if(AutomaticGainControl.isAvailable()){AutomaticGainControl x=AutomaticGainControl.create(session);if(x!=null){x.setEnabled(false);x.release();}}}catch(Exception ignored){}
  try{if(NoiseSuppressor.isAvailable()){NoiseSuppressor x=NoiseSuppressor.create(session);if(x!=null){x.setEnabled(false);x.release();}}}catch(Exception ignored){}
  try{if(AcousticEchoCanceler.isAvailable()){AcousticEchoCanceler x=AcousticEchoCanceler.create(session);if(x!=null){x.setEnabled(false);x.release();}}}catch(Exception ignored){}
 }
 private void capture(AudioRecord mic){
  short[] frame=new short[OctaveAnalyzer.N];
  try{
   while(recording){
    int pos=0;
    while(recording&&pos<frame.length){
     int n=mic.read(frame,pos,frame.length-pos,AudioRecord.READ_BLOCKING);
     if(n<=0)throw new IllegalStateException("Read error "+n);pos+=n;
    }
    if(!recording)break;
    int b=mode,w=weighting;
    OctaveAnalyzer.Result r=OctaveAnalyzer.analyze(frame,rate,b,w);
    synchronized(lock){
     if(b!=mode||w!=weighting)continue;
     if(sumP==null||sumP.length!=r.live.length){sumP=new double[r.live.length];frames=0;overallP=0;}
     frames++;leq=new double[r.live.length];
     for(int i=0;i<r.live.length;i++){
      sumP[i]+=Math.pow(10,r.live[i]/10);leq[i]=OctaveAnalyzer.db(sumP[i]/frames);
     }
     overallP+=Math.pow(10,r.overall/10);overallLeq=OctaveAnalyzer.db(overallP/frames);last=r;
    }
    runOnUiThread(()->{if(recording)refresh();});
    if(durationSeconds>0 && SystemClock.elapsedRealtime()-measurementStartMs>=(long)durationSeconds*1000L){
     runOnUiThread(()->{
      if(recording){
       autoStopped=true;
       stop();
       toast("Measurement time completed. Select Save CSV to save the results.");
      }
     });
     break;
    }
   }
  }catch(Exception ex){if(recording)runOnUiThread(()->toast("Recording stopped: "+ex.getMessage()));}
  finally{
   try{mic.stop();}catch(Exception ignored){}
   try{mic.release();}catch(Exception ignored){}
   runOnUiThread(()->{if(audio==mic){recording=false;audio=null;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);refresh();}});
  }
 }
 private void stop(){
  recording=false;AudioRecord m=audio;
  if(m!=null)try{m.stop();}catch(Exception ignored){}
  getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);refresh();
 }
 private String durationLabel(){
  if(durationSeconds==0)return "Unlimited";
  if(durationSeconds%60==0)return (durationSeconds/60)+" min";
  return durationSeconds+" sec";
 }
 private void setDuration(int seconds){
  if(recording){toast("Stop measuring before changing time");return;}
  durationSeconds=seconds;
  getPreferences(MODE_PRIVATE).edit().putInt("durationSeconds",durationSeconds).apply();
  refresh();
 }
 private void chooseDuration(){
  if(recording){toast("Stop measuring to set measurement time");return;}
  String[] times={"Unlimited (manual stop)","10 seconds","30 seconds","60 seconds","2 minutes","5 minutes","10 minutes","Custom seconds…"};
  int[] seconds={0,10,30,60,120,300,600,-1};
  new AlertDialog.Builder(this).setTitle("Time for measurement")
   .setItems(times,(dialog,which)->{
     if(which==seconds.length-1){
      EditText value=new EditText(this);
      value.setInputType(InputType.TYPE_CLASS_NUMBER);
      value.setSingleLine(true);
      value.setHint("Example: 45");
      LinearLayout wrap=col();wrap.setPadding(px(22),px(8),px(22),0);wrap.addView(value);
      new AlertDialog.Builder(this).setTitle("Custom measurement duration")
        .setMessage("Enter measurement time in seconds (1–3600)")
        .setView(wrap).setPositiveButton("Set",(d,w)->{
           try{int sec=Integer.parseInt(value.getText().toString().trim());
            if(sec<1||sec>3600)throw new NumberFormatException();
            setDuration(sec);
           }catch(Exception ex){toast("Enter a duration from 1 to 3600 seconds");}
        }).setNegativeButton("Cancel",null).show();
     }else setDuration(seconds[which]);
   }).setNegativeButton("Cancel",null).show();
 }
 private void showOptions(View anchor){
  PopupMenu popup=new PopupMenu(this,anchor);
  popup.getMenu().add("Time for measurement  •  "+durationLabel()).setOnMenuItemClickListener(item->{chooseDuration();return true;});
  popup.getMenu().add("Save readings to CSV").setOnMenuItemClickListener(item->{export();return true;});
  popup.getMenu().add("Calibration / Reset").setOnMenuItemClickListener(item->{calibrate();return true;});
  popup.getMenu().add("Clear calibration (dBFS mode)").setOnMenuItemClickListener(item->{resetCalibration();return true;});
  popup.getMenu().add("Clear current readings").setOnMenuItemClickListener(item->{
    if(recording){toast("Stop measurement before clearing readings");return true;}
    reset();refresh();return true;
  });
  popup.show();
 }
 private void resetCalibration(){
  calibration=Double.NaN;
  getPreferences(MODE_PRIVATE).edit().remove("offset").putBoolean("offsetVerified",false).apply();
  reset();refresh();
  toast("Calibration cleared. Measurements now show uncalibrated dBFS.");
 }
 private void calibrate(){
  String[] choices=Double.isNaN(calibration)
    ?new String[]{"Calibrate with a real 1 kHz reference"}
    :new String[]{"Reset calibration (recommended if SPL is too high)","Recalibrate using a real 1 kHz reference"};
  new AlertDialog.Builder(this).setTitle("Acoustic calibration / reset")
   .setMessage("Positive dB SPL requires a known acoustic reference. Simply entering 94 dB without a calibrator creates incorrect, often excessively high readings.")
   .setItems(choices,(dialog,which)->{
    if(!Double.isNaN(calibration)&&which==0)resetCalibration();
    else openCalibrationReferenceDialog();
   }).setNegativeButton("Close",null).show();
 }
 private void openCalibrationReferenceDialog(){
  if(recording){
   toast("Stop measuring with the 1 kHz calibrator applied, then calibrate.");
   return;
  }
  OctaveAnalyzer.Result r;
  synchronized(lock){r=last;}
  if(r==null){toast("First measure a physically applied 1 kHz calibrator, then stop.");return;}
  // A 1 kHz acoustic calibrator should dominate the unweighted microphone spectrum.
  double dominant=r.oneK-r.overallZ;
  if(r.clip){
   new AlertDialog.Builder(this).setTitle("Invalid calibration recording")
    .setMessage("Microphone signal clipped. Reduce input gain if possible and repeat measurement with a suitable calibrator.")
    .setPositiveButton("OK",null).show();
   return;
  }
  if(dominant< -6.0){
   new AlertDialog.Builder(this).setTitle("1 kHz reference not detected")
    .setMessage("The 1 kHz band is "+fmt(-dominant)+" dB below the unweighted overall level. A stable 1 kHz reference should dominate the measured sound. Calibration was not applied. Attach an acoustic calibrator and repeat the measurement.")
    .setPositiveButton("OK",null).show();
   return;
  }
  EditText input=new EditText(this);
  input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
  input.setSingleLine(true);
  input.setText("94.0");
  CheckBox confirm=new CheckBox(this);
  confirm.setText("I used a real acoustic calibrator physically coupled to the microphone at 1 kHz.");
  confirm.setTextSize(14);
  LinearLayout wrap=col();wrap.setPadding(px(18),px(5),px(18),px(2));
  wrap.addView(txt("Reference sound pressure level (dB SPL):",14,WHITE,false));wrap.addView(input);
  wrap.addView(confirm);
  final double measuredOneK=r.oneK;
  new AlertDialog.Builder(this).setTitle("Confirm acoustic calibration")
   .setMessage("Unweighted 1 kHz measured band: "+fmt(measuredOneK)+" dBFS. "
     +"A 94 dB field is NOT implied by selecting 94 in this dialog. "
     +"Single-point calibration cannot correct microphone frequency response.")
   .setView(wrap)
   .setPositiveButton("Apply reference",(dialog,which)->{
     if(!confirm.isChecked()){
       toast("Calibration not applied: physical calibrator confirmation required.");
       return;
     }
     try{
       double reference=Double.parseDouble(input.getText().toString().trim());
       if(reference<40||reference>140||!Double.isFinite(reference))throw new NumberFormatException();
       calibration=reference-measuredOneK;
       getPreferences(MODE_PRIVATE).edit()
        .putLong("offset",Double.doubleToLongBits(calibration))
        .putBoolean("offsetVerified",true).apply();
       reset();refresh();
       toast("Reference saved. Start a new measurement.");
     }catch(Exception ex){toast("Enter a valid reference from 40 to 140 dB SPL.");}
   }).setNegativeButton("Cancel",null).show();
 }

 private void export(){
  OctaveAnalyzer.Result r;double[] l;int count;double overall;
  synchronized(lock){r=last;l=leq==null?null:leq.clone();count=frames;overall=overallLeq;}
  if(r==null){toast("Measure first");return;}
  StringBuilder s=new StringBuilder();
  s.append("GK Octave Analyzer,Indicative FFT spectrum\n");
  s.append("Timestamp,").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US).format(new Date())).append("\n");
  s.append("Band mode,").append(mode==1?"1/1 Octave":"1/3 Octave").append("\n");
  s.append("Weighting,").append(new String[]{"Z","A","C"}[weighting]).append("\n");
  s.append("Sample rate (Hz),").append(rate).append("\n");
  s.append("Units,").append(Double.isNaN(calibration)?"dBFS":"dB SPL (1kHz calibrated)").append("\n");
  s.append("Measurement time setting,").append(durationLabel()).append("\n");
  s.append("Measured duration (seconds),").append(String.format(Locale.US,"%.2f",(double)count*OctaveAnalyzer.N/rate)).append("\n");
  s.append("Number of frames,").append(count).append("\n");
  s.append("Overall live level,").append(fmt(withCal(r.overall))).append("\n");
  s.append("Overall Leq level,").append(fmt(withCal(overall))).append("\n");
  s.append("Calibration status,").append(Double.isNaN(calibration)?"Uncalibrated dBFS":"1 kHz acoustic reference (indicative dB SPL)").append("\n");
  if(!Double.isNaN(calibration))s.append("Calibration offset dB,").append(fmt(calibration)).append("\n");
  s.append("Note,This is FFT analysis and is not a certified sound-level meter.").append("\n");
  s.append("Center frequency Hz,Live level,Leq level\n");
  for(int i=0;i<r.live.length;i++){
   s.append(String.format(Locale.US,"%.3f,%.2f,%.2f\n",r.center[i],withCal(r.live[i]),withCal(l[i])));
  }
  pendingCsv=s.toString();
  Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);save.setType("text/csv");save.addCategory(Intent.CATEGORY_OPENABLE);
  save.putExtra(Intent.EXTRA_TITLE,"GK_Octave_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".csv");
  startActivityForResult(save,102);
 }
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(req==102){if(result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingCsv!=null){
   try(OutputStream stream=getContentResolver().openOutputStream(data.getData())){
    if(stream==null)throw new IllegalStateException("Cannot save");
    stream.write(pendingCsv.getBytes(StandardCharsets.UTF_8));toast("CSV exported");
   }catch(Exception ex){toast("Save failed: "+ex.getMessage());}
  }pendingCsv=null;}
 }
 @Override public void onRequestPermissionsResult(int req,String[] perms,int[] results){
  super.onRequestPermissionsResult(req,perms,results);
  if(req==101){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)begin();
   else toast("Microphone permission is required");}
 }
 @Override protected void onStop(){super.onStop();if(recording)stop();}
 @Override protected void onDestroy(){stop();super.onDestroy();}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
