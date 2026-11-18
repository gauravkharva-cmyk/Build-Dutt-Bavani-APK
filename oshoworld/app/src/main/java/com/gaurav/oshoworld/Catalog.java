package com.gaurav.oshoworld;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

final class Catalog {
    private static final String BASE = "https://oshoworld.com";
    private static final String SERIES_API = "/api/server/audio/search-series-home";
    private static final String EPISODE_API = "/api/server/audio/series-filter";
    private static final String SUBSERIES_API = "/api/server/audio/subseries-filter";
    interface Progress {
        void status(int tracks, int seriesFinished, int seriesTotal, String latest);
    }
    static ArrayList<Track> load(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(),"catalog.json");
            if (f.exists()) {
                JSONArray arr = new JSONArray(read(new FileInputStream(f)));
                ArrayList<Track> list = new ArrayList<>();
                for(int i=0;i<arr.length();i++) list.add(Track.read(arr.getJSONObject(i)));
                if(!list.isEmpty()) return list;
            }
        } catch(Exception ignored) {}
        return examples();
    }
    static ArrayList<Track> refresh(Context ctx,Progress progress) throws Exception {
        LinkedHashMap<String,Track> found = new LinkedHashMap<>();
        // Keep manually imported recordings and previously discovered links.
        for(Track t:load(ctx))found.put(t.url,t);
        int finished=0,all=0,success=0;
        ArrayList<JSONObject> english=null,hindi=null;
        Exception last=null;
        try{english=series("english");all+=english.size();}catch(Exception e){last=e;}
        try{hindi=series("hindi");all+=hindi.size();}catch(Exception e){last=e;}
        for(int language=0;language<2;language++) {
            String label=language==0?"English":"Hindi";
            ArrayList<JSONObject> entries=language==0?english:hindi;
            if(entries==null)continue;
            for(JSONObject group:entries){
                String title=group.optString("title","Discourses");
                try{
                    String id=group.optString("_id","");
                    if(!id.isEmpty()){
                        if(group.has("countSeries")) {
                            ArrayList<JSONObject> subs=subseries(id);
                            for(JSONObject sub:subs){
                                String subId=sub.optString("_id","");
                                String subName=sub.optString("title",title);
                                if(!subId.isEmpty())appendEpisodes(found,subId,subName,label);
                            }
                        }else{
                            appendEpisodes(found,id,title,label);
                        }
                        success++;
                    }
                }catch(Exception e){last=e;}
                finished++;
                if(progress!=null)progress.status(found.size(),finished,all,label+": "+title);
                if(finished%12==0) {
                    try{save(ctx,found.values());}catch(Exception ignored){}
                }
                // Brief courtesy delay to avoid overloading the official server.
                try{Thread.sleep(75);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
            }
        }
        if(success==0)throw new IOException("OSHO audio catalog unavailable: "+(last==null?"no results":last.getMessage()));
        ArrayList<Track> result=new ArrayList<>(found.values());
        result.sort((a,b)->{
            int c=a.language.compareTo(b.language);
            if(c!=0)return c;
            c=a.series.compareToIgnoreCase(b.series);
            if(c!=0)return c;
            return a.title.compareToIgnoreCase(b.title);
        });
        save(ctx,result);
        return result;
    }
    private static ArrayList<JSONObject> series(String language) throws Exception {
        ArrayList<JSONObject> out=new ArrayList<>();
        for(int page=1;page<=100;page++){
            JSONObject payload=new JSONObject();
            payload.put("page",page);payload.put("sortBy","name");payload.put("language",language);
            JSONObject j=post(SERIES_API,payload);
            JSONArray a=j.optJSONArray("items");
            if(a==null||a.length()==0)break;
            for(int i=0;i<a.length();i++)out.add(a.getJSONObject(i));
            int total=total(j.opt("total"));
            if(total>0 && out.size()>=total)break;
            if(page>1 && a.length()==0)break;
        }
        return out;
    }
    private static ArrayList<JSONObject> subseries(String id) throws Exception {
        ArrayList<JSONObject> out=new ArrayList<>();
        for(int page=1;page<=80;page++) {
            JSONObject req=new JSONObject();
            req.put("currentId",id);req.put("perPage",80);
            req.put("sortBy","index-dsc");req.put("page",page);
            JSONObject data=post(SUBSERIES_API,req);
            JSONArray list=data.optJSONArray("listData");
            if(list==null||list.length()==0)break;
            for(int i=0;i<list.length();i++)out.add(list.getJSONObject(i));
            int total=total(data.opt("total"));
            if(total>0 && out.size()>=total)break;
        }
        return out;
    }
    private static void appendEpisodes(Map<String,Track> result,String id,String title,String lang) throws Exception {
        int count=0;
        for(int page=1;page<=200;page++){
            JSONObject payload=new JSONObject();
            payload.put("currentId",id);payload.put("page",page);
            payload.put("perPage",100);payload.put("search","");
            JSONObject data=post(EPISODE_API,payload);
            JSONArray episodes=data.optJSONArray("listData");
            if(episodes==null||episodes.length()==0)break;
            for(int i=0;i<episodes.length();i++){
                JSONObject ep=episodes.optJSONObject(i);
                if(ep==null)continue;
                String file=ep.optString("file","");
                String uri=absoluteMp3(file);
                if(uri.isEmpty())continue;
                String discTitle=ep.optString("title","");
                if(discTitle.isEmpty())discTitle=title+" "+(count+1);
                result.putIfAbsent(uri,new Track(uri,discTitle,Track.safe(title),lang));
            }
            count+=episodes.length();
            int total=total(data.opt("total"));
            if(total>0 && count>=total)break;
        }
    }
    private static int total(Object o) {
        if(o instanceof Number)return ((Number)o).intValue();
        if(o instanceof JSONArray) {
            JSONArray a=(JSONArray)o;
            if(a.length()>0){
                JSONObject j=a.optJSONObject(0);
                if(j!=null)return j.optInt("total",0);
            }
        }
        if(o instanceof String)try{return Integer.parseInt((String)o);}catch(Exception ignored){}
        return 0;
    }
    private static String absoluteMp3(String path) {
        if(path==null||path.isEmpty())return "";
        String u=path.trim();
        if(u.startsWith("//"))u="https:"+u;
        else if(u.startsWith("/"))u=BASE+u;
        else if(!u.startsWith("http"))u=BASE+"/"+u;
        if(!u.toLowerCase(Locale.ROOT).contains(".mp3"))return "";
        try{return new URI(u.replace(" ","%20")).toASCIIString();}catch(Exception e){return "";}
    }
    private static JSONObject post(String path,JSONObject payload) throws Exception {
        Exception error=null;
        for(int attempt=0;attempt<2;attempt++){
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(BASE+path).openConnection();
                c.setRequestMethod("POST");c.setConnectTimeout(16000);c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 16)");
                c.setRequestProperty("Accept","application/json");
                c.setRequestProperty("Content-Type","application/json");
                c.setDoOutput(true);
                byte[] body=payload.toString().getBytes("UTF-8");
                try(OutputStream out=c.getOutputStream()){out.write(body);}
                int status=c.getResponseCode();
                if(status<200||status>=300)throw new IOException("OSHO API HTTP "+status);
                String s=read(c.getInputStream());
                return new JSONObject(s);
            }catch(Exception e){error=e;
                if(attempt==0)try{Thread.sleep(700);}catch(InterruptedException ignored){}
            }finally{if(c!=null)c.disconnect();}
        }
        throw new IOException("OSHO API "+path+" failed",error);
    }
    static void save(Context ctx,Collection<Track> items) throws IOException {
        JSONArray a=new JSONArray();
        for(Track t:items)a.put(t.json());
        File dest=new File(ctx.getFilesDir(),"catalog.json");
        File temp=new File(ctx.getFilesDir(),"catalog.json.tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){
            out.write(a.toString().getBytes("UTF-8"));
            out.getFD().sync();
        }
        if(dest.exists()&&!dest.delete())throw new IOException("Cannot update catalog");
        if(!temp.renameTo(dest))throw new IOException("Cannot save catalog");
    }
    static ArrayList<Track> fromText(String text) {
        LinkedHashMap<String,Track> map=new LinkedHashMap<>();
        Matcher m=Pattern.compile("https?://[^\\s\"'<>]+?\\.mp3(?:\\?[^\\s\"'<>]+)?",Pattern.CASE_INSENSITIVE).matcher(text);
        while(m.find()){
            String u=absoluteMp3(m.group());
            if(!u.isEmpty()){
                String raw=u.substring(u.lastIndexOf('/')+1);
                String dir=u.substring(0,u.lastIndexOf('/'));
                String title=Track.clean(raw);
                String series=Track.clean(dir.substring(dir.lastIndexOf('/')+1));
                map.putIfAbsent(u,new Track(u,title,Track.safe(series),Track.language(u)));
            }
        }
        return new ArrayList<>(map.values());
    }
    private static String read(InputStream in) throws IOException {
        try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[16384];int n;int size=0;
            while((n=source.read(buf))!=-1) {
                size+=n;if(size>20*1024*1024)throw new IOException("API response too large");
                out.write(buf,0,n);
            }
            return out.toString("UTF-8");
        }
    }
    private static ArrayList<Track> examples() {
        ArrayList<Track> list=new ArrayList<>();
        list.add(new Track("https://oshoworld.com/wp-content/uploads/newAudios/A_Bird_on_the_Wing_(11)/A_Bird_on_the_Wing__01.mp3",
            "A Bird on the Wing 01","A Bird on the Wing","English"));
        return list;
    }
}
