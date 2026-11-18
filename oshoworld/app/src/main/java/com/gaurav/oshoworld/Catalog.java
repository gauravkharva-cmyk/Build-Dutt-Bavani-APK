package com.gaurav.oshoworld;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.*;
import java.net.*;
import java.util.*;

final class Catalog {
    private static final String BASE = "https://raw.githubusercontent.com/aagrawal207/OshoDiscourses/main/OshoDiscourses/Resources/";
    // Independent public catalog project, not an official OSHO World API.
    private static final String[] SOURCES = {
        BASE + "ArchiveCatalog.json", BASE + "OshoworldCatalog.json"
    };
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
    static ArrayList<Track> refresh(Context ctx) throws Exception {
        LinkedHashMap<String,Track> map = new LinkedHashMap<>();
        Exception last = null;
        int successes = 0;
        for(int source=0;source<SOURCES.length;source++) {
            try {
                String json = http(SOURCES[source]);
                Object root = new JSONTokener(json).nextValue();
                traverse(root, "", source==0, map, 0);
                successes++;
            } catch(Exception ex) { last=ex; }
        }
        if(map.size() < 3 && successes==0)
            throw new IOException("Catalog sources unavailable: "+(last==null?"Unknown":last.getMessage()));
        // Preserve any manually imported URLs.
        for(Track t:load(ctx)) map.putIfAbsent(t.url,t);
        ArrayList<Track> tracks=new ArrayList<>(map.values());
        tracks.sort((a,b) -> {
            int c=a.language.compareTo(b.language);
            if(c!=0)return c;
            c=a.series.compareToIgnoreCase(b.series);
            if(c!=0)return c;
            return a.title.compareToIgnoreCase(b.title);
        });
        save(ctx,tracks);
        return tracks;
    }
    static void save(Context ctx, Collection<Track> items) throws IOException {
        JSONArray a=new JSONArray();
        for(Track t:items) a.put(t.json());
        File dest=new File(ctx.getFilesDir(),"catalog.json");
        File temp=new File(ctx.getFilesDir(),"catalog.json.tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){
            out.write(a.toString().getBytes("UTF-8"));
            out.getFD().sync();
        }
        if(dest.exists()&&!dest.delete()) throw new IOException("Cannot update catalog");
        if(!temp.renameTo(dest)) throw new IOException("Cannot save catalog");
    }
    static ArrayList<Track> fromText(String text) {
        LinkedHashMap<String,Track> map=new LinkedHashMap<>();
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("https?://[^\\s\"'<>]+?\\.mp3(?:\\?[^\\s\"'<>]+)?",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        while(m.find()) addUrl(m.group(), "Imported", map);
        return new ArrayList<>(map.values());
    }
    private static void traverse(Object val,String where,boolean archive,Map<String,Track> map,int depth) {
        if(depth>15||map.size()>9000)return;
        if(val instanceof JSONObject) {
            JSONObject j=(JSONObject)val;
            Iterator<String> it=j.keys();
            while(it.hasNext()) {
                String key=it.next();
                if(key.toLowerCase(Locale.ROOT).contains(".mp3")) add(key,where,archive,map);
                traverse(j.opt(key),where+"/"+key,archive,map,depth+1);
            }
        } else if(val instanceof JSONArray) {
            JSONArray a=(JSONArray)val;
            for(int i=0;i<a.length();i++) traverse(a.opt(i),where,archive,map,depth+1);
        } else if(val instanceof String) {
            String v=((String)val).trim();
            if(v.toLowerCase(Locale.ROOT).contains(".mp3")) add(v,where,archive,map);
        }
    }
    private static void add(String raw,String where,boolean archive,Map<String,Track> map) {
        int index=raw.toLowerCase(Locale.ROOT).indexOf(".mp3");
        if(index<0)return;
        String s=raw.substring(0,index+4).replace("&amp;","&").replace("\\\\/","/");
        String url;
        if(s.startsWith("https://")||s.startsWith("http://"))url=s;
        else if(s.startsWith("//"))url="https:"+s;
        else if(s.contains("wp-content/")) {
            int i=s.indexOf("wp-content/");
            url="https://oshoworld.com/"+s.substring(i);
        }
        else if(archive)url="https://archive.org/download/osho-audio-discourses-collection/"+s.replaceFirst("^/","");
        else url="https://oshoworld.com/"+s.replaceFirst("^/","");
        addUrl(url,where,map);
    }
    private static void addUrl(String url,String context,Map<String,Track> map) {
        if(!(url.startsWith("https://")||url.startsWith("http://")))return;
        try {
            java.net.URI u=new java.net.URI(url.replace(" ","%20"));
            url=u.toASCIIString();
            if(!url.toLowerCase(Locale.ROOT).contains(".mp3"))return;
            String path=u.getPath();
            String[] parts=path.split("/");
            String filename=parts.length==0?"Track.mp3":parts[parts.length-1];
            String folder=parts.length<2?"Discourses":parts[parts.length-2];
            String series=Track.clean(folder);
            if(series.equalsIgnoreCase("Hindi Audio")||series.equalsIgnoreCase("English Audio")||series.length()<3){
                String[] ctx=context.split("/");
                for(int i=ctx.length-1;i>=0;i--) {
                    if(ctx[i].length()>3 && !ctx[i].matches("[0-9]+")){series=Track.clean(ctx[i]);break;}
                }
            }
            String language=Track.language(url+" "+context);
            String title=Track.clean(filename);
            if(title.isEmpty())title="Discourse";
            map.putIfAbsent(url,new Track(url,title,Track.safe(series),language));
        } catch(Exception ignored) {}
    }
    private static String http(String url) throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(16000);c.setReadTimeout(26000);
        c.setRequestProperty("User-Agent","OSHO-World-Premium-Android/1.0");
        try {
            int status=c.getResponseCode();
            if(status!=200)throw new IOException("HTTP "+status);
            if(c.getContentLengthLong()>12*1024*1024)throw new IOException("Catalog too large");
            try(InputStream in=c.getInputStream()){return read(in);}
        } finally {c.disconnect();}
    }
    private static String read(InputStream in) throws IOException {
        try(InputStream source=in; ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;int total=0;
            while((n=source.read(b))!=-1) {
                total+=n;if(total>16*1024*1024)throw new IOException("Catalog size limit");
                out.write(b,0,n);
            }
            return out.toString("UTF-8");
        }
    }
    private static ArrayList<Track> examples() {
        LinkedHashMap<String,Track> a=new LinkedHashMap<>();
        addUrl("https://oshoworld.com/wp-content/uploads/newAudios/A_Bird_on_the_Wing_(11)/A_Bird_on_the_Wing__01.mp3","English/A Bird on the Wing",a);
        addUrl("https://oshoworld.com/wp-content/uploads/newAudios/And_the_Flowers_Showerd_(11)/And_the_Flowers_Showerd_02.mp3","English/And the Flowers Showered",a);
        return new ArrayList<>(a.values());
    }
}
