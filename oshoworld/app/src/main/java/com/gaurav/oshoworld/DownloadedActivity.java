package com.gaurav.oshoworld;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;

public final class DownloadedActivity extends Activity {
    private static final int BG=0xff0B1423, PANEL=0xff19273F, GOLD=0xffE7BD72, MUTED=0xffAEBFD7, WHITE=0xffF6F7FB;
    private static final int PERMISSION_SCAN=390;
    private final ArrayList<JSONObject> all=new ArrayList<>(), shown=new ArrayList<>();
    private ListView list;
    private ArrayAdapter<JSONObject> adapter;
    private TextView count, progress;
    private EditText search;
    private String language="All";
    private boolean loading=false;
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private GradientDrawable bg(int color,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }
    private TextView text(String s,int sp,int color,boolean heavy){
        TextView t=new TextView(this);
        t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        if(heavy)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private Button button(String label,boolean primary){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);
        b.setTextColor(primary?BG:WHITE);b.setBackground(bg(primary?GOLD:PANEL,13));
        b.setMinimumHeight(0);b.setMinHeight(0);b.setPadding(dp(8),0,dp(8),0);
        return b;
    }
    private void addButton(LinearLayout parent,Button b,int height,int weight){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(weight>0?0:-2,dp(height),weight);
        p.setMargins(dp(3),0,dp(3),0);parent.addView(b,p);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(8));
        root.setBackgroundColor(BG);setContentView(root);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("‹ Back",false);
        heading.addView(back,new LinearLayout.LayoutParams(dp(88),dp(43)));
        back.setOnClickListener(v->finish());
        TextView title=text("My Downloads",25,WHITE,true);
        title.setPadding(dp(12),0,0,0);
        heading.addView(title,new LinearLayout.LayoutParams(0,dp(53),1));
        root.addView(heading);
        TextView help=text("Saved MP3s • play inside app or open in another player",12,MUTED,false);
        root.addView(help,new LinearLayout.LayoutParams(-1,dp(30)));
        LinearLayout toolbar=new LinearLayout(this);
        Button reload=button("↻ Refresh list",true),scan=button("⌕ Scan Music folder",false);
        addButton(toolbar,reload,44,1);addButton(toolbar,scan,44,1);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(46)));
        reload.setOnClickListener(v->load(false));
        scan.setOnClickListener(v->load(true));
        LinearLayout chips=new LinearLayout(this);
        for(String l:new String[]{"All","Hindi","English"}){
            Button b=button(l,l.equals(language));addButton(chips,b,38,1);
            b.setOnClickListener(v->{language=l;filter();});
        }
        LinearLayout.LayoutParams chipP=new LinearLayout.LayoutParams(-1,dp(40));
        chipP.setMargins(0,dp(10),0,dp(4));root.addView(chips,chipP);
        search=new EditText(this);search.setSingleLine(true);
        search.setHint("Search downloaded files or series");
        search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setTextSize(14);
        search.setBackground(bg(PANEL,11));search.setPadding(dp(14),0,dp(14),0);
        LinearLayout.LayoutParams searchP=new LinearLayout.LayoutParams(-1,dp(44));
        searchP.setMargins(0,dp(7),0,dp(7));
        root.addView(search,searchP);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int countChars){filter();}
            public void afterTextChanged(Editable e){}
        });
        count=text("Reading downloaded files…",14,GOLD,true);
        root.addView(count,new LinearLayout.LayoutParams(-1,dp(30)));
        progress=text("Files are stored under Music / OSHO World / Hindi or English",11,MUTED,false);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(30)));
        list=new ListView(this);list.setBackgroundColor(Color.TRANSPARENT);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0xff2C3E58));list.setDividerHeight(dp(2));
        adapter=new ArrayAdapter<JSONObject>(this,android.R.layout.simple_list_item_1,shown){
            @Override public View getView(int position,View previous,ViewGroup parent){
                LinearLayout item;
                TextView title,meta;
                Button play,open;
                if(previous instanceof LinearLayout){
                    item=(LinearLayout)previous;
                    LinearLayout info=(LinearLayout)item.getChildAt(0);
                    title=(TextView)info.getChildAt(0);
                    meta=(TextView)info.getChildAt(1);
                    LinearLayout controls=(LinearLayout)item.getChildAt(1);
                    play=(Button)controls.getChildAt(0);
                    open=(Button)controls.getChildAt(1);
                }else{
                    item=new LinearLayout(DownloadedActivity.this);item.setPadding(dp(8),dp(8),dp(8),dp(8));
                    item.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout info=new LinearLayout(DownloadedActivity.this);info.setOrientation(LinearLayout.VERTICAL);
                    title=text("",15,WHITE,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);
                    meta=text("",11,MUTED,false);meta.setMaxLines(2);meta.setEllipsize(TextUtils.TruncateAt.END);
                    info.addView(title);info.addView(meta);
                    item.addView(info,new LinearLayout.LayoutParams(-1,-2));
                    LinearLayout controls=new LinearLayout(DownloadedActivity.this);
                    play=button("▶ Play",true);open=button("↗ Open file",false);
                    addButton(controls,play,38,1);addButton(controls,open,38,1);
                    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(40));p.setMargins(0,dp(8),0,0);
                    item.addView(controls,p);
                }
                JSONObject data=getItem(position);
                title.setText(data.optString("title","Untitled discourse"));
                String info=data.optString("language","Other")+" • "+data.optString("series","Discourses");
                String folder=data.optString("folder","");
                meta.setText(info+(folder.isEmpty()?"":"\n"+folder));
                play.setOnClickListener(v->play(data));
                open.setOnClickListener(v->open(data));
                item.setBackground(bg(position%2==0?0xff111E31:0xff142137,10));
                return item;
            }
        };
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout player=new LinearLayout(this);
        Button prev=button("◀ Previous",false),toggle=button("▶ / Ⅱ",true),next=button("Next ▶",false);
        addButton(player,prev,45,1);addButton(player,toggle,45,1);addButton(player,next,45,1);
        root.addView(player,new LinearLayout.LayoutParams(-1,dp(48)));
        prev.setOnClickListener(v->command(PlayerService.PREV));
        toggle.setOnClickListener(v->command(PlayerService.TOGGLE));
        next.setOnClickListener(v->command(PlayerService.NEXT));
        TextView footer=text("Developed By : Gaurav Kharva",10,MUTED,false);
        footer.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(22)));
        load(false);
    }
    private void load(boolean scan){
        if(loading)return;
        if(scan&&!hasScanPermission()){
            if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO},PERMISSION_SCAN);
            else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},PERMISSION_SCAN);
            return;
        }
        loading=true;
        count.setText("Loading downloaded MP3s…");
        progress.setText(scan?"Scanning the device's Music/OSHO World folder…":"Reading download history…");
        new Thread(()->{
            LinkedHashMap<String,JSONObject> map=new LinkedHashMap<>();
            for(JSONObject j:DownloadService.readFinished(this)){
                String uri=j.optString("uri","");
                if(!uri.isEmpty())map.put(uri,j);
            }
            if(scan || map.isEmpty()){
                scanMediaStore(map);
            }
            ArrayList<JSONObject> items=new ArrayList<>(map.values());
            Collections.reverse(items);
            runOnUiThread(()->{
                all.clear();all.addAll(items);loading=false;
                filter();progress.setText(scan?"Music folder scan finished":"Tap Scan Music folder to find files from older installations");
            });
        },"OSHO-local-audio-list").start();
    }
    private boolean hasScanPermission(){
        if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)==PackageManager.PERMISSION_GRANTED;
        return Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==PERMISSION_SCAN){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)load(true);
            else Toast.makeText(this,"Audio permission required to scan older downloaded files",Toast.LENGTH_LONG).show();
        }
    }
    private void scanMediaStore(LinkedHashMap<String,JSONObject> map){
        if(!hasScanPermission())return;
        Uri collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] fields={
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH
        };
        if(Build.VERSION.SDK_INT<29)return;
        try(Cursor cursor=getContentResolver().query(collection,fields,
            MediaStore.Audio.Media.RELATIVE_PATH+" LIKE ?",new String[]{"%OSHO World/%"},
            MediaStore.Audio.Media.DATE_ADDED+" DESC")){
            if(cursor==null)return;
            int id=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int name=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int relative=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH);
            while(cursor.moveToNext()){
                String file=cursor.getString(name);
                if(file==null||!file.toLowerCase(Locale.ROOT).endsWith(".mp3"))continue;
                String folder=cursor.getString(relative);
                Uri uri=ContentUris.withAppendedId(collection,cursor.getLong(id));
                if(map.containsKey(uri.toString()))continue;
                JSONObject item=new JSONObject();
                item.put("uri",uri.toString());
                item.put("url","");
                item.put("title",Track.clean(file));
                item.put("language",Track.language(folder==null?"":folder));
                item.put("series",folder==null?"Discourses":seriesFromPath(folder));
                item.put("folder",folder==null?"Music/OSHO World":folder);
                map.put(uri.toString(),item);
            }
        }catch(Exception ignored){}
    }
    private String seriesFromPath(String path){
        String p=path.replaceAll("/+$","");
        int i=p.lastIndexOf('/');
        if(i>=0)return p.substring(i+1);
        return "Discourses";
    }
    private void filter(){
        if(adapter==null)return;
        shown.clear();
        String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for(JSONObject j:all){
            if(!"All".equals(language)&&!language.equals(j.optString("language")))continue;
            String info=(j.optString("title")+" "+j.optString("series")+" "+j.optString("folder")).toLowerCase(Locale.ROOT);
            if(q.isEmpty()||info.contains(q))shown.add(j);
        }
        adapter.notifyDataSetChanged();
        count.setText(shown.size()+" downloaded MP3 files"+(all.isEmpty()?" — none found yet":""));
    }
    private void play(JSONObject t){
        String uri=t.optString("uri","");
        if(uri.isEmpty()){Toast.makeText(this,"File location missing",Toast.LENGTH_LONG).show();return;}
        Intent i=new Intent(this,PlayerService.class).setAction(PlayerService.PLAY);
        i.putExtra("uri",uri);i.putExtra("title",t.optString("title","OSHO"));
        try{startForegroundService(i);Toast.makeText(this,"Playing: "+t.optString("title"),Toast.LENGTH_SHORT).show();}
        catch(Exception e){Toast.makeText(this,"Could not start audio: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void open(JSONObject t){
        try{
            Uri uri=Uri.parse(t.optString("uri",""));
            Intent i=new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri,"audio/mpeg");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i,"Open MP3 using"));
        }catch(Exception e){
            new AlertDialog.Builder(this).setTitle("Cannot open MP3")
                .setMessage("This audio file may have moved. Try Scan Music folder.\n"+e.getMessage())
                .setPositiveButton("OK",null).show();
        }
    }
    private void command(String action){
        try{startForegroundService(new Intent(this,PlayerService.class).setAction(action));}
        catch(Exception e){Toast.makeText(this,"Player unavailable",Toast.LENGTH_LONG).show();}
    }
}
