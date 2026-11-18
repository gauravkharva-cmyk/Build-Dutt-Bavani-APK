package com.gaurav.oshoworld;

import org.json.JSONObject;
import java.util.Locale;

final class Track {
    final String url, title, series, language;
    Track(String u, String t, String s, String l) {
        url = u; title = t; series = s; language = l;
    }
    JSONObject json() {
        JSONObject x = new JSONObject();
        try { x.put("url",url); x.put("title",title); x.put("series",series); x.put("language",language); } catch(Exception ignored) {}
        return x;
    }
    static Track read(JSONObject j) {
        return new Track(j.optString("url"),j.optString("title"),j.optString("series"),j.optString("language","Other"));
    }
    static String safe(String raw) {
        String v = raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+","_").replaceAll("\\s+"," ").trim();
        if(v.length() > 84) v = v.substring(0,84).trim();
        return v.isEmpty() ? "Unsorted" : v;
    }
    static String clean(String x) {
        try { x = java.net.URLDecoder.decode(x.replace("+","%2B"),"UTF-8"); } catch(Exception ignored) {}
        return x.replace('_',' ').replaceAll("\\s+"," ").replaceAll("(?i)\\.mp3$","").trim();
    }
    static String language(String full) {
        String s = full.toLowerCase(Locale.ROOT);
        if (s.contains("hindi") || s.contains("\\u0939\\u093f\\u0902\\u0926\\u0940") || s.contains("hindiaudio")) return "Hindi";
        if (s.contains("english") || s.contains("englishaudio")) return "English";
        return "Other";
    }
}
