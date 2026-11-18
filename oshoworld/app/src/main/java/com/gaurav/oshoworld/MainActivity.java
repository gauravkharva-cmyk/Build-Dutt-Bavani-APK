package com.gaurav.oshoworld;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int BG=0xff0B1423, PANEL=0xff19273F, GOLD=0xffE7BD72, MUTED=0xffAEBFD7, WHITE=0xffF6F7FB;
    private LinearLayout root, content;
    private ListView listView;
    private TextView stateText, countText, nowText, catalogText;
    private EditText searchText;
    private ArrayAdapter<Track> adapter;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ArrayList<Track> tracks=new ArrayList<>(), visible=new ArrayList<>();
    private String filter="All";
    private boolean busy=false;
    private Runnable poller;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},110);
        // Never parse thousands of catalog entries on Android's main thread.
        buildUi();
        updateList();
        poller=new Runnable(){
            @Override public void run(){refreshCounters();handler.postDelayed(this,3000);}
        };
        handler.post(poller);
        new Thread(()->{
            ArrayList<Track> cached=Catalog.load(getApplicationContext());
            runOnUiThread(()->{
                tracks=cached;updateList();
                if(cached.size()<=1)
                    catalogText.setText("Tap Refresh catalog to discover Hindi + English recordings");
            });
        },"OSHO-load-local-catalog").start();
    }
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private GradientDrawable background(int color,float radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }
    private TextView text(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return t;
    }
    private Button button(String s,boolean accent){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);
        b.setTextColor(accent?BG:WHITE);
        b.setTextSize(13);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setBackground(background(accent?GOLD:PANEL,14));
        b.setPadding(dp(10),0,dp(10),0);
        b.setMinHeight(0);b.setMinimumHeight(0);
        b.setElevation(dp(2));return b;
    }
    private void rowAdd(LinearLayout row,View v,int weight,int height){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(weight>0?0:-2,dp(height),weight);
        p.setMargins(dp(3),0,dp(3),0);row.addView(v,p);
    }
    private void buildUi(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setPadding(dp(14),dp(14),dp(14),dp(8));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=text("◉",31,GOLD,true);top.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(54)));
        LinearLayout titleGroup=new LinearLayout(this);titleGroup.setOrientation(LinearLayout.VERTICAL);
        TextView kicker=text("OSHO WORLD  ·  PREMIUM",11,GOLD,true);
        TextView title=text("Audio Library",27,WHITE,true);
        titleGroup.addView(kicker);titleGroup.addView(title);
        top.addView(titleGroup,new LinearLayout.LayoutParams(-1,-2));
        root.addView(top);
        TextView subtitle=text("Hindi + English  •  download & listen offline",12,MUTED,false);
        root.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout statCard=new LinearLayout(this);statCard.setPadding(dp(16),dp(12),dp(16),dp(12));
        statCard.setOrientation(LinearLayout.VERTICAL);statCard.setBackground(background(PANEL,17));
        catalogText=text("Discovering MP3 recordings…",17,WHITE,true);
        statCard.addView(catalogText);
        stateText=text("Ready · Downloaded files save under Music/OSHO World",12,MUTED,false);
        statCard.addView(stateText);
        LinearLayout.LayoutParams statp=new LinearLayout.LayoutParams(-1,-2);
        statp.setMargins(0,dp(10),0,dp(12));root.addView(statCard,statp);
        LinearLayout actionRow=new LinearLayout(this);actionRow.setGravity(Gravity.CENTER_VERTICAL);
        Button all=button("↓  Download all",true);
        Button pause=button("Ⅱ  Pause",false);
        Button resume=button("↻  Resume",false);
        rowAdd(actionRow,all,1,48);rowAdd(actionRow,pause,1,48);rowAdd(actionRow,resume,1,48);
        root.addView(actionRow);
        all.setOnClickListener(v->confirmAll());
        pause.setOnClickListener(v->{
            Intent i=new Intent(this,DownloadService.class).setAction(DownloadService.PAUSE);startService(i);
            Toast.makeText(this,"Download pause requested",Toast.LENGTH_SHORT).show();
        });
        // The service checks the queue on its worker thread; do not parse it on UI.
        resume.setOnClickListener(v->startDownloads());
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button refresh=button("⟳  Refresh catalog",false);
        Button importUrls=button("+  Import MP3 links",false);
        rowAdd(toolbar,refresh,1,42);rowAdd(toolbar,importUrls,1,42);
        LinearLayout.LayoutParams toolp=new LinearLayout.LayoutParams(-1,dp(42));
        toolp.setMargins(0,dp(10),0,dp(8));root.addView(toolbar,toolp);
        refresh.setOnClickListener(v->fetchCatalog());
        importUrls.setOnClickListener(v->showImport());

        LinearLayout filesRow=new LinearLayout(this);
        Button downloaded=button("▶  My Downloads & Play",true);
        Button lastPlay=button("♫  Play last",false);
        rowAdd(filesRow,downloaded,2,46);rowAdd(filesRow,lastPlay,1,46);
        LinearLayout.LayoutParams filesP=new LinearLayout.LayoutParams(-1,dp(48));
        filesP.setMargins(0,dp(6),0,dp(9));root.addView(filesRow,filesP);
        downloaded.setOnClickListener(v->startActivity(new Intent(this,DownloadedActivity.class)));
        lastPlay.setOnClickListener(v->{
            String uri=getSharedPreferences("playback",MODE_PRIVATE).getString("uri","");
            String lastTitle=getSharedPreferences("playback",MODE_PRIVATE).getString("title","OSHO Discourse");
            if(uri.isEmpty()){
                startActivity(new Intent(this,DownloadedActivity.class));
                return;
            }
            Intent intent=new Intent(this,PlayerService.class).setAction(PlayerService.PLAY);
            intent.putExtra("uri",uri);intent.putExtra("title",lastTitle);
            startForegroundService(intent);
        });
        LinearLayout langs=new LinearLayout(this);
        for(String l:new String[]{"All","Hindi","English","Other"}){
            Button b=button(l,l.equals(filter));b.setTextSize(12);
            rowAdd(langs,b,1,39);
            b.setOnClickListener(v->{filter=l;updateList();});
        }
        root.addView(langs,new LinearLayout.LayoutParams(-1,dp(40)));
        searchText=new EditText(this);
        searchText.setSingleLine(true);searchText.setHint("⌕  Search series or discourse");
        searchText.setTextSize(14);searchText.setHintTextColor(MUTED);searchText.setTextColor(WHITE);
        searchText.setPadding(dp(14),0,dp(14),0);searchText.setBackground(background(PANEL,13));
        LinearLayout.LayoutParams searchP=new LinearLayout.LayoutParams(-1,dp(43));
        searchP.setMargins(0,dp(9),0,dp(4));root.addView(searchText,searchP);
        searchText.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){updateList();}
            public void afterTextChanged(Editable e){}
        });
        countText=text("Audio tracks",12,MUTED,false);
        root.addView(countText,new LinearLayout.LayoutParams(-1,dp(26)));
        listView=new ListView(this);listView.setBackgroundColor(Color.TRANSPARENT);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(0xff2C3E58));
        listView.setDividerHeight(dp(1));listView.setCacheColorHint(Color.TRANSPARENT);
        adapter=new ArrayAdapter<Track>(this,android.R.layout.simple_list_item_1,visible){
            @Override public View getView(int position,View convert,android.view.ViewGroup parent){
                LinearLayout layout;
                TextView name,meta,badge;
                if(convert instanceof LinearLayout){
                    layout=(LinearLayout)convert;
                    LinearLayout group=(LinearLayout)layout.getChildAt(0);
                    name=(TextView)group.getChildAt(0);meta=(TextView)group.getChildAt(1);
                    badge=(TextView)layout.getChildAt(1);
                }else{
                    layout=new LinearLayout(MainActivity.this);layout.setGravity(Gravity.CENTER_VERTICAL);
                    layout.setPadding(dp(11),dp(9),dp(11),dp(9));layout.setMinimumHeight(dp(61));
                    LinearLayout group=new LinearLayout(MainActivity.this);group.setOrientation(LinearLayout.VERTICAL);
                    name=text("",14,WHITE,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    meta=text("",11,MUTED,false);meta.setSingleLine(true);meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    group.addView(name);group.addView(meta);
                    layout.addView(group,new LinearLayout.LayoutParams(0,-2,1));
                    badge=text("",19,GOLD,true);badge.setGravity(Gravity.CENTER);
                    layout.addView(badge,new LinearLayout.LayoutParams(dp(38),dp(40)));
                }
                Track t=getItem(position);
                name.setText(t.title);
                meta.setText(t.language+"  ·  "+t.series);
                badge.setText(DownloadService.finishedUri(MainActivity.this,t.url).isEmpty()?"↓":"▶");
                layout.setBackground(background(position%2==0?0xff111E31:0xff142137,9));
                return layout;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((p,v,pos,id)->{
            Track t=visible.get(pos);
            String saved=DownloadService.finishedUri(this,t.url);
            if(!saved.isEmpty())play(t,saved);
            else new AlertDialog.Builder(this).setTitle(t.title).setMessage("Download this discourse?\n"+t.language+" · "+t.series)
                .setPositiveButton("Download",(d,w)->queueTracks(Collections.singletonList(t)))
                .setNegativeButton("Cancel",null).show();
        });
        root.addView(listView,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout player=new LinearLayout(this);player.setGravity(Gravity.CENTER_VERTICAL);
        player.setPadding(dp(8),dp(5),dp(8),dp(5));player.setBackground(background(PANEL,16));
        nowText=text("▶  Background MP3 player",12,WHITE,true);
        nowText.setSingleLine(true);nowText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        player.addView(nowText,new LinearLayout.LayoutParams(0,dp(41),1));
        Button previous=button("◀",false),toggle=button("▶/Ⅱ",true),next=button("▶▶",false),speed=button("1×",false);
        rowAdd(player,previous,0,38);rowAdd(player,toggle,0,38);rowAdd(player,next,0,38);rowAdd(player,speed,0,38);
        previous.setOnClickListener(v->playerCommand(PlayerService.PREV));
        toggle.setOnClickListener(v->playerCommand(PlayerService.TOGGLE));
        next.setOnClickListener(v->playerCommand(PlayerService.NEXT));
        speed.setOnClickListener(v->{
            float now=getSharedPreferences("playback",MODE_PRIVATE).getFloat("speed",1f);
            float newSpeed=now>=2f?1f:now>=1.5f?2f:now>=1.25f?1.5f:1.25f;
            getSharedPreferences("playback",MODE_PRIVATE).edit().putFloat("speed",newSpeed).apply();
            speed.setText(newSpeed+"×");
            Toast.makeText(this,"Playback speed for next play/resume: "+newSpeed+"×",Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams playerP=new LinearLayout.LayoutParams(-1,dp(51));
        playerP.setMargins(0,dp(4),0,dp(3));root.addView(player,playerP);
        TextView credit=text("Developed By : Gaurav Kharva",10,MUTED,false);credit.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        root.addView(credit,new LinearLayout.LayoutParams(-1,dp(22)));
    }
    private void playerCommand(String command){
        Intent i=new Intent(this,PlayerService.class).setAction(command);
        startForegroundService(i);
    }
    private void play(Track track,String uri){
        Intent i=new Intent(this,PlayerService.class).setAction(PlayerService.PLAY);
        i.putExtra("uri",uri);i.putExtra("title",track.title);
        startForegroundService(i);
    }
    private void updateList(){
        if(adapter==null)return;
        visible.clear();String q=searchText==null?"":searchText.getText().toString().toLowerCase(Locale.ROOT);
        for(Track t:tracks){
            if(!filter.equals("All")&&!t.language.equals(filter))continue;
            if(!q.isEmpty()&&!(t.title+" "+t.series+" "+t.language).toLowerCase(Locale.ROOT).contains(q))continue;
            visible.add(t);
        }
        adapter.notifyDataSetChanged();
        countText.setText(visible.size()+" recordings  ·  tap a title to download or play");
        catalogText.setText(tracks.size()+" catalog recordings · Hindi + English");
    }
    private void confirmAll(){
        if(busy){Toast.makeText(this,"Catalog is still loading. Please use Download all after the refresh finishes.",Toast.LENGTH_LONG).show();return;}
        if(tracks.size()<5){
            new AlertDialog.Builder(this).setTitle("Load the catalog first")
                .setMessage("The Hindi and English audio catalog has not loaded yet. Tap Refresh catalog and wait for the series counter to finish.")
                .setPositiveButton("Refresh", (d,w)->fetchCatalog())
                .setNegativeButton("Cancel",null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Download the entire collection?")
            .setMessage("Both Hindi and English MP3 recordings will be queued. This may require hundreds of GB of storage and many hours. Android limits how long bulk downloads can run in the background; tap Resume to continue later. Wi-Fi is recommended.")
            .setPositiveButton("Queue all",(d,w)->queueTracks(tracks))
            .setNegativeButton("Cancel",null).show();
    }
    private void queueTracks(Collection<Track> list){
        ArrayList<Track> items=new ArrayList<>(list);
        if(items.isEmpty()){Toast.makeText(this,"Nothing selected",Toast.LENGTH_SHORT).show();return;}
        stateText.setText("Preparing "+items.size()+" downloads…");
        new Thread(()->{
            try{
                DownloadService.writeQueue(getApplicationContext(),items);
                runOnUiThread(()->{
                    startDownloads();
                    Toast.makeText(this,"Queued "+items.size()+" recordings",Toast.LENGTH_LONG).show();
                });
            }catch(Exception e){
                runOnUiThread(()->Toast.makeText(this,"Could not queue downloads: "+e.getMessage(),Toast.LENGTH_LONG).show());
            }
        },"OSHO-write-download-queue").start();
    }
    private void startDownloads(){
        Intent i=new Intent(this,DownloadService.class).setAction(DownloadService.START);
        startForegroundService(i);
    }
    private void fetchCatalog(){
        if(busy)return;busy=true;
        catalogText.setText("Refreshing remote catalog…");
        new Thread(()->{
            try{
                ArrayList<Track> result=Catalog.refresh(this,(found,done,total,latest)->runOnUiThread(()->{
                    catalogText.setText("Loading official OSHO catalog: "+done+"/"+total+" series · "+found+" MP3s");
                }));
                runOnUiThread(()->{
                    tracks=result;busy=false;updateList();
                    Toast.makeText(this,"Catalog loaded: "+tracks.size()+" MP3 links",Toast.LENGTH_LONG).show();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    busy=false;updateList();
                    Toast.makeText(this,"Catalog refresh unavailable. Import links if needed.",Toast.LENGTH_LONG).show();
                });
            }
        },"Catalog-refresh").start();
    }
    private void showImport(){
        EditText e=new EditText(this);e.setMinLines(5);e.setMaxLines(10);
        e.setGravity(Gravity.TOP);e.setTextSize(13);
        e.setHint("Paste one or more direct MP3 URLs here");
        new AlertDialog.Builder(this).setTitle("Import audio links").setView(e)
            .setPositiveButton("Import",(d,w)->{
                ArrayList<Track> imported=Catalog.fromText(e.getText().toString());
                LinkedHashMap<String,Track> merged=new LinkedHashMap<>();
                for(Track t:tracks)merged.put(t.url,t);
                for(Track t:imported)merged.put(t.url,t);
                tracks=new ArrayList<>(merged.values());
                try{Catalog.save(this,tracks);}catch(Exception ignored){}
                updateList();
                Toast.makeText(this,"Imported "+imported.size()+" links",Toast.LENGTH_LONG).show();
            }).setNegativeButton("Cancel",null).show();
    }
    private void refreshCounters(){
        if(stateText==null)return;
        android.content.SharedPreferences prefs=getSharedPreferences("download_state",MODE_PRIVATE);
        String state=prefs.getString("state","Ready · files in Music/OSHO World");
        // Cached counters avoid repeatedly reading huge JSON queue files on UI.
        int done=prefs.getInt("total_done",0);
        int pending=prefs.getInt("pending_count",0);
        int failed=prefs.getInt("failed",0);
        stateText.setText(done+" downloaded · "+pending+" pending · "+failed+" failed\n"+state);
        nowText.setText("♫  "+getSharedPreferences("playback",MODE_PRIVATE).getString("title","Background MP3 player"));
    }
    @Override public void onDestroy(){
        if(poller!=null)handler.removeCallbacks(poller);
        super.onDestroy();
    }
}
