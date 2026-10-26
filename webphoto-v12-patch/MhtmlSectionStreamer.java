package com.gauravkharva.webphotoppt;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/** Processes an MHT one MIME section at a time instead of loading the whole archive. */
public final class MhtmlSectionStreamer {
    private static final int MAX_PART = 40 * 1024 * 1024;
    private static final Pattern BOUNDARY = Pattern.compile("(?i)(?:^|;)\\s*boundary\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^;\\s]+))");
    private MhtmlSectionStreamer() {}
    public static MhtmlArchiveReader.Report parse(InputStream source, MhtmlArchiveReader.Handler handler) throws IOException {
        BufferedReader lines=new BufferedReader(new InputStreamReader(source,StandardCharsets.ISO_8859_1),65536);
        StringBuilder header=new StringBuilder();String line;
        while((line=lines.readLine())!=null){
            if(line.isEmpty())break;
            if(header.length()+line.length()>262144)throw new IOException("MHT header too large");
            header.append(line).append("\r\n");
        }
        Matcher m=BOUNDARY.matcher(header.toString().replaceAll("\\r?\\n[ \\t]+"," "));
        if(!m.find())throw new IOException("MHT is missing its archive boundary");
        String boundary=m.group(1)!=null?m.group(1):m.group(2)!=null?m.group(2):m.group(3);
        if(boundary==null||boundary.isEmpty()||boundary.length()>180)throw new IOException("Invalid MHT boundary");
        String marker="--"+boundary;
        StringBuilder part=new StringBuilder();boolean active=false,huge=false;
        MhtmlArchiveReader.Report all=new MhtmlArchiveReader.Report();all.foundBoundary=true;
        while((line=lines.readLine())!=null){
            String delimiter=line.trim();
            if(delimiter.equals(marker)||delimiter.equals(marker+"--")){
                if(active){
                    if(huge)all.rejectedParts++;
                    else if(part.length()>0){
                        String single=header.toString()+"\r\n"+marker+"\r\n"+part+marker+"--\r\n";
                        MhtmlArchiveReader.Report r=MhtmlArchiveReader.parse(single.getBytes(StandardCharsets.ISO_8859_1),handler);
                        all.parts+=r.parts;all.imageParts+=r.imageParts;all.htmlParts+=r.htmlParts;all.rejectedParts+=r.rejectedParts;
                    }
                }
                part.setLength(0);huge=false;active=!delimiter.equals(marker+"--");
                if(!active)break;
            }else if(active&&!huge){
                if((long)part.length()+line.length()+2>MAX_PART){huge=true;part.setLength(0);}
                else part.append(line).append("\r\n");
            }
        }
        return all;
    }
}
