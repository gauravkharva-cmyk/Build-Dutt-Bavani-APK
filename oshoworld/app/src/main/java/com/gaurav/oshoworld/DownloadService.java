package com.gaurav.oshoworld;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DownloadService extends Service {
    static final String START="com.gaurav.oshoworld.DOWNLOAD_START";
    static final String PAUSE="com.gaurav.oshoworld.DOWNLOAD_PAUSE";
    private static final int NOTIFICATION_ID=2101;
    private final AtomicBoolean running=new AtomicBoolean(false);
    private volatile boolean paused=false;
    private volatile HttpURLConnection active;
    private Thread worker;
    public static File queueFile(Context c){return new File(c.getFilesDir(),"queue.json");}
    public static File completeFile(Context c){return new File(c.getFilesDir(),"finished.json");}
    public static synchronized void writeQueue(Context c,Collection<Track> tasks) throws Exception {
        LinkedHashMap<String,Track> unique=new LinkedHashMap<>();
        for(Track t:readQueue(c))unique.put(t.url,t);
        for(Track t:tasks)if(!finishedUri(c,t.url).isEmpty()){}else unique.put(t.url,t);
        JSONArray a=new JSONArray();
        for(Track t:unique.values())a.put(t.json());
        writeAtomic(queueFile(c),a.toString());
    }
    static ArrayList<Track> readQueue(Context c){
        ArrayList<Track> tasks=new ArrayList<>();
        try {
            JSONArray a=new JSONArray(readFile(queueFile(c)));
            for(int i=0;i<a.length();i++) tasks.add(Track.read(a.getJSONObject(i)));
        } catch(Exception ignored){}
        return tasks;
    }
    static ArrayList<JSONObject> readFinished(Context c) {
        ArrayList<JSONObject> result=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(readFile(completeFile(c)));
            for(int i=0;i<a.length();i++)result.add(a.getJSONObject(i));
        }catch(Exception ignored){}
        return result;
    }
    static String finishedUri(Context c,String url){
        return c.getSharedPreferences("completed",MODE_PRIVATE).getString(key(url),"");
    }
    private static String key(String s){
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            byte[] d=md.digest(s.getBytes("UTF-8"));
            StringBuilder out=new StringBuilder();
            for(int i=0;i<12;i++)out.append(String.format(Locale.ROOT,"%02x",d[i]&255));
            return out.toString();
        } catch(Exception e){return Integer.toHexString(s.hashCode());}
    }
    private static void writeAtomic(File dest,String s) throws IOException{
        File tmp=new File(dest.getAbsolutePath()+".tmp");
        try(FileOutputStream f=new FileOutputStream(tmp)){
            f.write(s.getBytes("UTF-8"));f.getFD().sync();
        }
        if(dest.exists()&&!dest.delete())throw new IOException("Cannot replace "+dest);
        if(!tmp.renameTo(dest))throw new IOException("Cannot rename "+dest);
    }
    private static String readFile(File f)throws IOException {
        try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[16384];int n;
            while((n=in.read(b))>=0)out.write(b,0,n);
            return out.toString("UTF-8");
        }
    }
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("osho_downloads","OSHO audio downloads",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent in,int flags,int startId){
        if(in!=null && PAUSE.equals(in.getAction())){
            paused=true;
            if(active!=null)active.disconnect();
            getSharedPreferences("download_state",MODE_PRIVATE).edit().putString("state","Paused").apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        paused=false;
        Notification n=notification("Preparing audio downloads",0,0);
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIFICATION_ID,n);
        if(running.compareAndSet(false,true)){
            worker=new Thread(this::runQueue,"OSHO-download");
            worker.start();
        }
        return START_NOT_STICKY;
    }
    private void runQueue(){
        int completed=0,errors=0;
        ArrayList<Track> tasks=readQueue(this);
        ArrayList<Track> remaining=new ArrayList<>();
        for(int i=0;i<tasks.size();i++){
            Track t=tasks.get(i);
            if(paused){remaining.addAll(tasks.subList(i,tasks.size()));break;}
            if(!finishedUri(this,t.url).isEmpty()) {
                completed++;continue;
            }
            setStatus("Downloading: "+t.title,completed,errors);
            try {
                saveOne(t);
                completed++;
            }catch(Exception e){
                if(paused){remaining.addAll(tasks.subList(i,tasks.size()));break;}
                errors++;remaining.add(t);
                setStatus("Retry later: "+t.title,completed,errors);
            }
            try{writeAtomic(queueFile(this),asJson(remaining,tasks,i+1));}catch(Exception ignored){}
        }
        try{writeAtomic(queueFile(this),asJson(remaining,Collections.emptyList(),0));}catch(Exception ignored){}
        setStatus(paused?"Paused":(errors>0?"Finished with "+errors+" failed; tap Resume to retry":"Downloads complete"),completed,errors);
        running.set(false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    private String asJson(List<Track> failed,List<Track> future,int start){
        JSONArray a=new JSONArray();
        for(Track t:failed)a.put(t.json());
        for(int i=start;i<future.size();i++)a.put(future.get(i).json());
        return a.toString();
    }
    private void setStatus(String s,int done,int failed){
        getSharedPreferences("download_state",MODE_PRIVATE).edit()
            .putString("state",s).putInt("done",done).putInt("failed",failed).apply();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
            .notify(NOTIFICATION_ID,notification(s,done,failed));
    }
    private Notification notification(String text,int done,int failed){
        Intent activity=new Intent(this,MainActivity.class);
        PendingIntent open=PendingIntent.getActivity(this,0,activity,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent pause=new Intent(this,DownloadService.class).setAction(PAUSE);
        PendingIntent stop=PendingIntent.getService(this,1,pause,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"osho_downloads")
            .setContentTitle("OSHO World · Hindi + English")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause,"Pause",stop)
            .setSubText(done+" completed · "+failed+" failed").build();
    }
    private void saveOne(Track t)throws Exception{
        File dir=new File(getExternalFilesDir(null),"partials");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot make download directory");
        File temp=new File(dir,key(t.url)+".part");
        long have=temp.exists()?temp.length():0;
        HttpURLConnection c=(HttpURLConnection)new URL(t.url).openConnection();
        active=c;
        c.setInstanceFollowRedirects(true);c.setConnectTimeout(22000);c.setReadTimeout(45000);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Android) OSHO Downloader");
        if(have>0)c.setRequestProperty("Range","bytes="+have+"-");
        try {
            int status=c.getResponseCode();
            if(status!=200&&status!=206)throw new IOException("Server HTTP "+status);
            String type=c.getContentType();
            if(type!=null && (type.contains("text/html")||type.contains("application/json")))
                throw new IOException("Non-audio response");
            boolean append=have>0&&status==206;
            if(!append)have=0;
            try(InputStream src=c.getInputStream();
                FileOutputStream out=new FileOutputStream(temp,append)){
                byte[] buffer=new byte[32768];int n;long size=have;
                while((n=src.read(buffer))!=-1){
                    if(paused)throw new IOException("Paused");
                    out.write(buffer,0,n);size+=n;
                    if(size%(1024*1024)<32768) {
                        getSharedPreferences("download_state",MODE_PRIVATE).edit()
                            .putLong("bytes",size).putString("current",t.title).apply();
                    }
                }
                out.getFD().sync();
            }
            if(temp.length()<4096)throw new IOException("Audio file was too small");
            try(FileInputStream in=new FileInputStream(temp)){
                byte[] head=new byte[12];int n=in.read(head);
                if(n>=5 && head[0]=='<' && head[1]=='!')throw new IOException("HTML instead of MP3");
                if(n>=5 && head[0]=='<' && head[1]=='h')throw new IOException("HTML instead of MP3");
            }
            Uri saved=saveMedia(t,temp);
            getSharedPreferences("completed",MODE_PRIVATE).edit().putString(key(t.url),saved.toString()).apply();
            addFinished(t,saved);
            temp.delete();
        }finally{active=null;c.disconnect();}
    }
    private Uri saveMedia(Track t,File source)throws IOException{
        String name=Track.safe(t.title)+".mp3";
        String folder="Music/OSHO World/"+Track.safe(t.language)+"/"+Track.safe(t.series)+"/";
        if(Build.VERSION.SDK_INT>=29){
            ContentValues cv=new ContentValues();
            cv.put(MediaStore.Audio.Media.DISPLAY_NAME,name);
            cv.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mpeg");
            cv.put(MediaStore.Audio.Media.RELATIVE_PATH,folder);
            cv.put(MediaStore.Audio.Media.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cv);
            if(uri==null)throw new IOException("Cannot save to Music folder");
            try{
                try(InputStream in=new FileInputStream(source);
                    OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                    if(out==null)throw new IOException("Storage inaccessible");
                    copy(in,out);
                }
                ContentValues ready=new ContentValues();ready.put(MediaStore.Audio.Media.IS_PENDING,0);
                getContentResolver().update(uri,ready,null,null);
                return uri;
            }catch(Exception e){
                getContentResolver().delete(uri,null,null);
                throw new IOException("Could not save MP3",e);
            }
        } else {
            File base=new File(getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),Track.safe(t.language)+"/"+Track.safe(t.series));
            if(!base.exists()&&!base.mkdirs())throw new IOException("Cannot make audio folder");
            File file=new File(base,name);
            try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(file)){copy(in,out);}
            return Uri.fromFile(file);
        }
    }
    private static void copy(InputStream in,OutputStream out)throws IOException{
        byte[] buffer=new byte[65536];int n;
        while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        out.flush();
    }
    private synchronized void addFinished(Track t,Uri uri){
        try{
            JSONArray a;
            try{a=new JSONArray(readFile(completeFile(this)));}catch(Exception ignored){a=new JSONArray();}
            JSONObject j=t.json();j.put("uri",uri.toString());
            a.put(j);writeAtomic(completeFile(this),a.toString());
        }catch(Exception ignored){}
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        paused=true;
        if(active!=null)active.disconnect();
        super.onDestroy();
    }
}
