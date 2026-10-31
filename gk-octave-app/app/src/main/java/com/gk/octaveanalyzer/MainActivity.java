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
 private volatile boolean guidedCapture=false;
 private int savedDuration=60;
 private int rate=48000,frames=0;
 private volatile AudioRecord audio;
 private Thread worker;
 private OctaveAnalyzer.Result last;
 private double[] sumP,leq; private double overallP=0,overallLeq=OctaveAnalyzer.FLOOR,calibration=Double.NaN;
 private double estimateOffset=Double.NaN,assumedDBA=Double.NaN;
 private int levelMode=3; // 0=raw dBFS, 1=assumed SPL, 2=physical reference, 3=relative digital dB
 private TextView status,level,units,average,details,warning,durationBadge,calibrationBadge;
 private SpectrumView chart;private LinearLayout table;
 private Button start,one,third,calButton,saveButton,estimateButton;
 private Spinner weightingPicker;
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
  if(getPreferences(MODE_PRIVATE).getBoolean("roughSet",false)){
   estimateOffset=Double.longBitsToDouble(getPreferences(MODE_PRIVATE).getLong("estimateOffset",Double.doubleToLongBits(Double.NaN)));
   assumedDBA=Double.longBitsToDouble(getPreferences(MODE_PRIVATE).getLong("assumedDBA",Double.doubleToLongBits(Double.NaN)));
  }
  levelMode=getPreferences(MODE_PRIVATE).getInt("levelMode",3);
  if(!getPreferences(MODE_PRIVATE).getBoolean("v17DisplayMigrated",false)){
   if(levelMode==0)levelMode=3;
   getPreferences(MODE_PRIVATE).edit().putInt("levelMode",levelMode)
       .putBoolean("v17DisplayMigrated",true).apply();
  }
  if(levelMode==1&&!Double.isFinite(estimateOffset))levelMode=3;
  if(levelMode==2&&!Double.isFinite(calibration))levelMode=3;
  if(levelMode<0||levelMode>3)levelMode=3;
  ui();reset();refresh();
  if(!getPreferences(MODE_PRIVATE).getBoolean("v17WelcomeSeen",false)){
   getPreferences(MODE_PRIVATE).edit().putBoolean("v17WelcomeSeen",true).apply();
   new AlertDialog.Builder(this).setTitle("Nonnegative relative sound levels")
    .setMessage("Raw microphone readings (dBFS) are normally negative. This app now opens with RELATIVE dB (0 dB at -100 dBFS). The values rise as sound gets louder, but they are NOT dB SPL. For an assumed SPL estimate, set up the quiet-room reference.")
    .setPositiveButton("Continue",null)
    .setNeutralButton("Assumed SPL setup",(dialog,which)->startGuidedEstimate()).show();
  }
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
  content.addView(durationBadge,lp(-1,-2,6));
  estimateButton=btn("SET UP ESTIMATED POSITIVE dB",Color.rgb(252,206,113));
  estimateButton.setOnClickListener(v->startGuidedEstimate());
  content.addView(estimateButton,lp(-1,px(47),12));
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
  one=btn("1/1 Octave",PANEL);one.setTextColor(WHITE);
  third=btn("1/3 Octave",GREEN);
  LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,-1,1);p1.rightMargin=px(8);
  modes.addView(one,p1);modes.addView(third,new LinearLayout.LayoutParams(0,-1,1));
  one.setOnClickListener(v->setMode(1));third.setOnClickListener(v->setMode(3));
  content.addView(txt("FREQUENCY WEIGHTING",12,GREY,true),lp(-1,-2,5));
  weightingPicker=new Spinner(this);
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
    new String[]{"Z — unweighted","A weighting","C weighting"});
  adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  weightingPicker.setAdapter(adapter);content.addView(weightingPicker,lp(-1,px(48),12));
  weightingPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
   public void onItemSelected(android.widget.AdapterView<?> parent,View v,int position,long id){
    if(weighting!=position){weighting=position;reset();refresh();}
   }
  });
  start=btn("Start measuring",GREEN);content.addView(start,lp(-1,px(51),11));
  start.setOnClickListener(v->{if(recording)stop();else begin();});
  LinearLayout actions=row();content.addView(actions,lp(-1,px(48),14));
  calButton=btn("LEVEL MODE: RELATIVE / SPL / RAW",Color.rgb(129,185,237));
  saveButton=btn("Save CSV",Color.rgb(252,206,113));
  LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1);left.rightMargin=px(8);
  actions.addView(calButton,left);actions.addView(saveButton,new LinearLayout.LayoutParams(0,-1,1));
  calButton.setOnClickListener(v->showLevelModes());saveButton.setOnClickListener(v->export());
  content.addView(txt("LIVE FREQUENCY SPECTRUM",12,GREY,true),lp(-1,-2,8));
  chart=new SpectrumView(this);
  HorizontalScrollView h=new HorizontalScrollView(this);h.setBackground(bg(PANEL));h.addView(chart);
  content.addView(h,lp(-1,px(260),12));
  content.addView(txt("BAND LEVELS (Hz / LIVE / Leq)",12,GREY,true),lp(-1,-2,7));
  table=col();table.setBackground(bg(PANEL));table.setPadding(px(12),px(10),px(12),px(10));
  content.addView(table,lp(-1,-2,12));makeTable();
  content.addView(txt("RELATIVE LEVEL is nonnegative dB above a fixed -100 dBFS reference, NOT dB SPL. It supports comparisons on the same device. ASSUMED SPL uses an unverified quiet-room reference. Only a physical acoustic reference can support indicative SPL; this app is not a certified acoustic meter.",12,GREY,false),lp(-1,-2,13));
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
  if(chart!=null)chart.show(mode==1?OctaveAnalyzer.OCT:OctaveAnalyzer.THIRD,null,null,levelMode);
 }
 private String fmt(double d){return String.format(Locale.US,"%.1f",d);}
 private double activeOffset(){return levelMode==1?estimateOffset:levelMode==2?calibration:levelMode==3?100.0:0.0;}
 private String levelUnits(){return levelMode==3?"RELATIVE dB (ref -100 dBFS; NOT SPL)":levelMode==1?"estimated dB SPL (UNVERIFIED)":levelMode==2?"dB SPL (1 kHz reference)":"dBFS (raw)";}
 private double withCal(double d){return d+activeOffset();}
 private String shown(double raw){
  double v=withCal(raw);
  return levelMode==1&&v<0.0?"below est. range":fmt(levelMode==3?Math.max(0.0,v):v);
 }
 private double[] offset(double[] a){
  if(a==null)return null;double[] r=a.clone();
  for(int i=0;i<r.length;i++)r[i]=levelMode==3?Math.max(0.0,r[i]+100.0):r[i]+activeOffset();
  return r;
 }
 private void refresh(){
  if(status==null)return;
  OctaveAnalyzer.Result r;double[] l;double sum;int count;
  synchronized(lock){r=last;l=leq==null?null:leq.clone();sum=overallLeq;count=frames;}
  status.setText(guidedCapture?"● QUIET-ROOM BASELINE (10 SECONDS)":recording?"● LIVE MEASUREMENT":(autoStopped?"● TIMED MEASUREMENT COMPLETE":"● Ready / stopped"));
  status.setTextColor(recording?GREEN:GREY);
  if(calibrationBadge!=null){
   calibrationBadge.setText(levelMode==1
     ?"ESTIMATE ONLY – assumed "+fmt(assumedDBA)+" dBA background; not calibrated"
     :levelMode==2?"PHYSICAL REFERENCE (indicative); offset "+fmt(calibration)+" dB"
     :levelMode==3?"RELATIVE DIGITAL LEVEL • 0 dB = -100 dBFS • NOT SPL"
     :"RAW dBFS – negative values are normal");
   calibrationBadge.setTextColor(levelMode==2?GREEN:Color.rgb(252,206,113));
  }
  level.setText(r==null?"--.-":shown(r.overall));
  units.setText(levelUnits()+"  •  "+new String[]{"Z","A","C"}[weighting]+" weighting");
  average.setText(r==null?"Leq: --.-":"Leq: "+shown(sum)+(levelMode==0?" dBFS":levelMode==1?" est. dB SPL":levelMode==2?" dB SPL":" relative dB"));
  long measuredSec=Math.round((double)count*OctaveAnalyzer.N/rate);
  details.setText(String.format(Locale.US,"Time %02d:%02d   •   %d Hz   •   %s",
    measuredSec/60,measuredSec%60,rate,source));
  if(durationBadge!=null)durationBadge.setText("Measurement time: "+durationLabel()
    +(recording&&durationSeconds>0?"  •  remaining "+Math.max(0,(durationSeconds*1000L-(SystemClock.elapsedRealtime()-measurementStartMs)+999)/1000)+" sec":""));
  warning.setText(r!=null&&r.clip?"⚠ Mic clipping: levels may be inaccurate":"");
  chart.show(mode==1?OctaveAnalyzer.OCT:OctaveAnalyzer.THIRD,r==null?null:offset(r.live),offset(l),levelMode);
  if(table.getChildCount()>1){
   for(int i=0;i<table.getChildCount()-1;i++){
    LinearLayout row=(LinearLayout)table.getChildAt(i+1);
    ((TextView)row.getChildAt(1)).setText(r==null?"--":shown(r.live[i]));
    ((TextView)row.getChildAt(2)).setText(l==null?"--":shown(l[i]));
   }
  }
  calButton.setEnabled(true);saveButton.setEnabled(r!=null);
  estimateButton.setText(levelMode==1?"ASSUMED SPL ON • change reference":"OPTIONAL: ESTIMATE SPL FROM QUIET ROOM");
  estimateButton.setEnabled(!recording);
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
       boolean wasGuided=guidedCapture;
       autoStopped=true;
       stop();
       if(wasGuided){
         estimateFromRoom();
       }else{
         toast("Measurement time completed. Select Save CSV to save the results.");
       }
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
  recording=false;
  if(guidedCapture){guidedCapture=false;durationSeconds=savedDuration;}
  AudioRecord m=audio;
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
  popup.getMenu().add("Display mode: relative / raw / assumed SPL").setOnMenuItemClickListener(item->{showLevelModes();return true;});
  popup.getMenu().add("Instructions: estimated dB").setOnMenuItemClickListener(item->{showEstimateInstructions();return true;});
  popup.getMenu().add("Estimate from assumed quiet room").setOnMenuItemClickListener(item->{estimateFromRoom();return true;});
  popup.getMenu().add("Physical calibration").setOnMenuItemClickListener(item->{calibrate();return true;});
  popup.getMenu().add("Clear calibration (dBFS mode)").setOnMenuItemClickListener(item->{resetCalibration();return true;});
  popup.getMenu().add("Clear current readings").setOnMenuItemClickListener(item->{
    if(recording){toast("Stop measurement before clearing readings");return true;}
    reset();refresh();return true;
  });
  popup.show();
 }

 private void startGuidedEstimate(){
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},103);
   return;
  }
  if(recording){toast("Stop the current measurement first");return;}
  new AlertDialog.Builder(this).setTitle("10-second baseline • no equipment")
   .setMessage("Place the tablet in a quiet room and do not talk or touch it during the 10-second recording. Afterward choose the background dBA you ASSUME was present (e.g. 40 dBA).\n\nAbsolute sound pressure is not known. This only produces an ESTIMATE; values can differ greatly from reality.")
   .setPositiveButton("START 10-SECOND BASELINE",(dialog,which)->{
     savedDuration=durationSeconds;
     durationSeconds=10;
     guidedCapture=true;
     weighting=1;
     weightingPicker.setSelection(1);
     begin();
     if(!recording){guidedCapture=false;durationSeconds=savedDuration;refresh();}
   }).setNegativeButton("Cancel",null).show();
 }
 private void showEstimateInstructions(){
  new AlertDialog.Builder(this).setTitle("Estimate without equipment")
    .setMessage("1. Select A weighting.\n2. Start measuring in a quiet room.\n3. Measure for at least 10–20 seconds.\n4. Stop measuring.\n5. Tap Estimate dB / Raw dBFS and select the quiet-room estimate option.\n6. Choose the ASSUMED room level.\n7. Start a new measurement.\n\nThis estimate is not a calibrated dB SPL measurement.")
    .setPositiveButton("OK",null).show();
 }
 private void showLevelModes(){
  String[] modes={"RELATIVE digital level (nonnegative, NOT SPL)",
                  "Raw dBFS (negative is normal)",
                  "Assumed SPL estimate (guided quiet room)",
                  "Reuse saved assumed SPL estimate",
                  "Physical 1 kHz calibration",
                  "Erase assumed SPL reference"};
  new AlertDialog.Builder(this).setTitle("Sound level display")
   .setMessage("The microphone alone cannot determine absolute SPL. Estimated mode uses a user-selected ASSUMED background level and is unsuitable for formal measurements.")
   .setItems(modes,(dialog,which)->{
    if(which==0||which==1){
     if(recording){toast("Stop measurement before switching display modes");return;}
     levelMode=which==0?3:0;reset();refresh();storeLevelMode();
    }else if(which==2)startGuidedEstimate();
    else if(which==3){
     if(recording){toast("Stop first");return;}
     if(!Double.isFinite(estimateOffset)){toast("No assumed SPL reference saved yet");return;}
     levelMode=1;reset();refresh();storeLevelMode();
    }else if(which==4)calibrate();
    else eraseEstimate();
   }).setNegativeButton("Close",null).show();
 }
 private void storeLevelMode(){
  getPreferences(MODE_PRIVATE).edit().putInt("levelMode",levelMode).apply();
 }
 private void eraseEstimate(){
  if(recording){toast("Stop measurement first");return;}
  estimateOffset=Double.NaN;assumedDBA=Double.NaN;
  if(levelMode==1)levelMode=3;
  getPreferences(MODE_PRIVATE).edit().remove("estimateOffset").remove("assumedDBA")
     .putBoolean("roughSet",false).putInt("levelMode",levelMode).apply();
  reset();refresh();toast("Assumed reference cleared");
 }
 private void estimateFromRoom(){
  if(recording){toast("Stop first, then open Estimate");return;}
  if(weighting!=1){
   new AlertDialog.Builder(this).setTitle("Use A weighting")
    .setMessage("Select A weighting, record a quiet room for at least 10 seconds, stop, then return to this screen. This is still only an assumed reference, not acoustic calibration.")
    .setPositiveButton("OK",null).show();
   return;
  }
  int count;double baseline;boolean clipping;
  synchronized(lock){count=frames;baseline=overallLeq;clipping=last!=null&&last.clip;}
  double sec=count*(double)OctaveAnalyzer.N/rate;
  if(sec<9.5 || !Double.isFinite(baseline)){
   new AlertDialog.Builder(this).setTitle("Record quiet-room baseline")
     .setMessage("Only "+fmt(sec)+" seconds are available. In a quiet room with no speech, record 10–20 seconds of ambient sound with A weighting selected. Stop, then tap Estimate dB / Raw dBFS again. This is not a calibrated measurement.")
     .setPositiveButton("OK",null).show();
   return;
  }
  if(clipping){toast("Audio clipped. Repeat the quiet-room recording.");return;}
  String[] choices={"Assume 30 dBA","Assume 35 dBA","Assume 40 dBA","Assume 45 dBA","Assume 50 dBA","Custom assumption..."};
  new AlertDialog.Builder(this).setTitle("Assume background sound level")
   .setMessage("Quiet-room digital Leq: "+fmt(baseline)+" dBFS over "+fmt(sec)+" s.\nChoose the A-weighted sound level you ASSUME was present. The actual value is unknown; errors can exceed 10 dB.")
   .setItems(choices,(dlg,i)->{
    if(i<5)confirmEstimate(new int[]{30,35,40,45,50}[i],baseline);
    else{
     EditText input=new EditText(this);
     input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
     input.setHint("Assumed dBA (20–80)");
     LinearLayout wrap=col();wrap.setPadding(px(20),0,px(20),0);wrap.addView(input);
     new AlertDialog.Builder(this).setTitle("Assumed room dBA")
       .setView(wrap).setPositiveButton("Continue",(dd,w)->{
         try{double level=Double.parseDouble(input.getText().toString());
         if(!Double.isFinite(level)||level<20||level>80)throw new NumberFormatException();
         confirmEstimate(level,baseline);
         }catch(Exception ex){toast("Enter assumed dBA between 20 and 80.");}
       }).setNegativeButton("Cancel",null).show();
    }
   }).setNegativeButton("Cancel",null).show();
 }
 private void confirmEstimate(double assumed,double baseline){
  double shift=assumed-baseline;
  new AlertDialog.Builder(this).setTitle("Enable rough estimate?")
   .setMessage("Assumed background: "+fmt(assumed)+" dBA\nRecorded uncalibrated level: "+fmt(baseline)+" dBFS\nOffset: "+fmt(shift)+" dB.\n\nThis offset is an ASSUMPTION, not measurement or calibration. Actual values can be substantially different. Do not use for engineering acceptance or personal safety decisions.")
   .setPositiveButton("USE ESTIMATE",(dlg,i)->{
     assumedDBA=assumed;estimateOffset=shift;levelMode=1;
     getPreferences(MODE_PRIVATE).edit().putBoolean("roughSet",true)
      .putLong("estimateOffset",Double.doubleToLongBits(estimateOffset))
      .putLong("assumedDBA",Double.doubleToLongBits(assumedDBA))
      .putInt("levelMode",1).apply();
     reset();refresh();toast("Estimate enabled. Start a new measurement.");
   }).setNegativeButton("Cancel",null).show();
 }

 private void resetCalibration(){
  calibration=Double.NaN;
  if(levelMode==2)levelMode=3;
  getPreferences(MODE_PRIVATE).edit().remove("offset").putBoolean("offsetVerified",false).putInt("levelMode",levelMode).apply();
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
       levelMode=2;
       getPreferences(MODE_PRIVATE).edit()
        .putLong("offset",Double.doubleToLongBits(calibration))
        .putBoolean("offsetVerified",true).putInt("levelMode",2).apply();
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
  s.append("Units,").append(levelUnits()).append("\n");
  if(levelMode==3)s.append("Relative reference,0 relative dB equals -100 dBFS. Values below reference shown as 0; NOT SPL.").append("\n");
  s.append("Measurement time setting,").append(durationLabel()).append("\n");
  s.append("Measured duration (seconds),").append(String.format(Locale.US,"%.2f",(double)count*OctaveAnalyzer.N/rate)).append("\n");
  s.append("Number of frames,").append(count).append("\n");
  s.append("Overall live level,").append(fmt(withCal(r.overall))).append("\n");
  s.append("Overall Leq level,").append(fmt(withCal(overall))).append("\n");
   s.append("Measurement reference,").append(levelMode==3?"Relative reference -100 dBFS (nonnegative, NOT SPL)":levelMode==1?"Assumed environment ONLY":levelMode==2?"Physical reference":"Uncalibrated raw audio").append("\n");
  if(levelMode==1){s.append("Assumed room level dBA,").append(fmt(assumedDBA)).append("\n");s.append("Unverified dB offset,").append(fmt(estimateOffset)).append("\n");}
  if(levelMode==2)s.append("Physical calibration offset dB,").append(fmt(calibration)).append("\n");
  s.append("Note,Relative readings are NOT SPL. Assumed SPL depends on assumed ambient noise and can be wrong. Not suitable for compliance or motor acceptance.").append("\n");
  s.append("Center frequency Hz,Displayed live level,Displayed Leq level,Raw live dBFS,Raw Leq dBFS\n");
  for(int i=0;i<r.live.length;i++){
   s.append(String.format(Locale.US,"%.3f,%s,%s,%.2f,%.2f\n",r.center[i],shown(r.live[i]),shown(l[i]),r.live[i],l[i]));
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
  if(req==103){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startGuidedEstimate();
   else toast("Microphone permission is required for baseline measurement");}
 }
 @Override protected void onStop(){super.onStop();if(recording)stop();}
 @Override protected void onDestroy(){stop();super.onDestroy();}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
