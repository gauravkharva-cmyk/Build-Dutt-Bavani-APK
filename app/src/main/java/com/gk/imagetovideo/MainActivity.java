package com.gk.imagetovideo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.ParcelFileDescriptor;
import android.util.Size;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int PICK_IMAGES = 100;
    private static final int SAVE_MOVIE = 101;
    private final ArrayList<Uri> images = new ArrayList<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private LinearLayout thumbnails, layout;
    private TextView countText, status;
    private ProgressBar progress;
    private Button createButton, cancelButton, openButton;
    private Spinner durationSpinner, aspectSpinner;
    private volatile boolean exporting = false;
    private Uri lastVideo;

    private int dp(float x) { return (int)(getResources().getDisplayMetrics().density*x+.5f); }

    private GradientDrawable rounded(int c, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(c); d.setCornerRadius(dp(radius)); return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(2),1.08f);
        if(bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(color,12));
        b.setPadding(dp(12),dp(8),dp(12),dp(8));
        return b;
    }

    private void addSpace(int n) {
        View v = new View(this);
        layout.addView(v,new LinearLayout.LayoutParams(1,dp(n)));
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(11,16,30));
        getWindow().setNavigationBarColor(Color.rgb(11,16,30));
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackgroundColor(Color.rgb(11,16,30));
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20),dp(26),dp(20),dp(28));
        sc.addView(layout);
        setContentView(sc);

        TextView heading = text("GK Image → Video",26,Color.WHITE,true);
        layout.addView(heading);
        addSpace(6);
        layout.addView(text("Create an MP4 slideshow entirely offline. No account, watermark or storage permission required.",14,0xff9DAAC4,false));
        addSpace(20);

        Button picker = button("＋  Select photos",0xff3576E8);
        layout.addView(picker,new LinearLayout.LayoutParams(-1,dp(50)));
        picker.setOnClickListener(v -> {
            if(exporting) return;
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            startActivityForResult(i,PICK_IMAGES);
        });
        addSpace(14);

        countText = text("No photos selected",16,Color.WHITE,true);
        layout.addView(countText);
        addSpace(8);
        thumbnails = new LinearLayout(this);
        thumbnails.setOrientation(LinearLayout.VERTICAL);
        layout.addView(thumbnails);
        addSpace(15);

        layout.addView(text("Time per photo",14,0xff9DAAC4,true));
        durationSpinner = spinner(new String[]{"1 second","2 seconds","3 seconds","5 seconds","10 seconds"},2);
        layout.addView(durationSpinner,new LinearLayout.LayoutParams(-1,dp(50)));
        addSpace(12);

        layout.addView(text("Video format",14,0xff9DAAC4,true));
        aspectSpinner = spinner(new String[]{
            "Landscape HD · 1280 × 720",
            "Portrait HD · 720 × 1280",
            "Square HD · 720 × 720",
            "Landscape Full HD · 1920 × 1080"
        },0);
        layout.addView(aspectSpinner,new LinearLayout.LayoutParams(-1,dp(50)));
        addSpace(20);

        createButton = button("Create MP4 · Choose save location",0xff159F8A);
        layout.addView(createButton,new LinearLayout.LayoutParams(-1,dp(54)));
        createButton.setOnClickListener(v -> {
            if(exporting || images.isEmpty()) {
                if(images.isEmpty()) Toast.makeText(this,"Select at least one photo",Toast.LENGTH_SHORT).show();
                return;
            }
            String name="GK_Video_"+new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())+".mp4";
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("video/mp4");
            i.putExtra(Intent.EXTRA_TITLE,name);
            startActivityForResult(i,SAVE_MOVIE);
        });
        addSpace(16);

        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setProgress(0);
        progress.setVisibility(View.GONE);
        layout.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
        addSpace(10);
        status = text("Choose photos, set the timing and save as MP4.",14,0xffAAB7D0,false);
        layout.addView(status);

        cancelButton = button("Cancel export",0xff8C374A);
        cancelButton.setVisibility(View.GONE);
        layout.addView(cancelButton,new LinearLayout.LayoutParams(-1,dp(48)));
        cancelButton.setOnClickListener(v -> {
            cancelled.set(true);
            status.setText("Stopping export…");
        });

        openButton = button("▶  Open saved video",0xff394C72);
        openButton.setVisibility(View.GONE);
        layout.addView(openButton,new LinearLayout.LayoutParams(-1,dp(48)));
        openButton.setOnClickListener(v -> {
            if(lastVideo==null) return;
            Intent play = new Intent(Intent.ACTION_VIEW);
            play.setDataAndType(lastVideo,"video/mp4");
            play.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivity(play); }
            catch(Exception ex) { Toast.makeText(this,"Video saved. Open it in your Files app.",Toast.LENGTH_LONG).show(); }
        });
        addSpace(18);
        TextView foot = text("Developed By Gaurav Kharva",12,0xff71809F,false);
        foot.setGravity(Gravity.RIGHT);
        layout.addView(foot);
    }

    private Spinner spinner(String[] labels, int selected) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a); s.setSelection(selected); return s;
    }

    private String filename(Uri u) {
        try(Cursor c = getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null && c.moveToFirst()) return c.getString(0);
        } catch(Exception ignored) {}
        return u.getLastPathSegment()==null?"Image":u.getLastPathSegment();
    }

    private Bitmap thumb(Uri uri) {
        try {
            if(Build.VERSION.SDK_INT >= 29) {
                return getContentResolver().loadThumbnail(uri,new Size(dp(68),dp(68)),null);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 8;
            try(InputStream in=getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in,null,options);
            }
        } catch(Exception ignored) { return null; }
    }

    private Button small(String label) {
        Button b=button(label,0xff394C72);
        b.setTextSize(13);
        b.setPadding(0,0,0,0);
        return b;
    }

    private void refreshImages() {
        countText.setText(images.size()+" photo"+(images.size()==1?"":"s")+" selected · use arrows to reorder");
        thumbnails.removeAllViews();
        for(int i=0;i<images.size();i++){
            final int pos=i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(7),dp(7),dp(7),dp(7));
            row.setBackground(rounded(0xff19233A,10));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1,dp(82));
            rp.bottomMargin=dp(6);
            thumbnails.addView(row,rp);

            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap b=thumb(images.get(i));
            if(b!=null) iv.setImageBitmap(b);
            row.addView(iv,new LinearLayout.LayoutParams(dp(64),dp(64)));

            TextView name = text((i+1)+". "+filename(images.get(i)),12,Color.WHITE,false);
            name.setMaxLines(2); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0,-2,1);
            np.leftMargin=dp(9);
            row.addView(name,np);

            Button up=small("↑");
            row.addView(up,new LinearLayout.LayoutParams(dp(34),dp(38)));
            up.setEnabled(i>0);
            up.setOnClickListener(v->{Collections.swap(images,pos,pos-1); refreshImages();});
            Button down=small("↓");
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(34),dp(38)); pp.leftMargin=dp(3);
            row.addView(down,pp);
            down.setEnabled(i<images.size()-1);
            down.setOnClickListener(v->{Collections.swap(images,pos,pos+1); refreshImages();});
            Button remove=small("×");
            LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(dp(34),dp(38));xp.leftMargin=dp(3);
            row.addView(remove,xp);
            remove.setOnClickListener(v->{images.remove(pos); refreshImages();});
        }
    }

    @Override protected void onActivityResult(int request,int code,Intent data) {
        super.onActivityResult(request,code,data);
        if(code!=RESULT_OK || data==null) return;
        if(request==PICK_IMAGES) {
            images.clear();
            if(data.getClipData()!=null) {
                for(int i=0;i<data.getClipData().getItemCount();i++){
                    Uri u=data.getClipData().getItemAt(i).getUri();
                    if(!images.contains(u)) images.add(u);
                }
            } else if(data.getData()!=null) images.add(data.getData());
            refreshImages();
            status.setText("Ready to create your video.");
            openButton.setVisibility(View.GONE);
        } else if(request==SAVE_MOVIE && data.getData()!=null) {
            export(data.getData());
        }
    }

    private int duration() {
        switch(durationSpinner.getSelectedItemPosition()){
            case 0:return 1;
            case 1:return 2;
            case 2:return 3;
            case 3:return 5;
            default:return 10;
        }
    }

    private void export(Uri dest) {
        if(exporting) return;
        exporting=true;
        cancelled.set(false);
        createButton.setEnabled(false);
        cancelButton.setVisibility(View.VISIBLE);
        openButton.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        status.setText("Preparing video encoder…");
        ArrayList<Uri> ordered = new ArrayList<>(images);
        int sec=duration();
        int which=aspectSpinner.getSelectedItemPosition();
        int w=which==1?720:which==2?720:which==3?1920:1280;
        int h=which==1?1280:which==2?720:which==3?1080:720;
        new Thread(() -> {
            boolean complete=false;
            String error=null;
            try(ParcelFileDescriptor pfd=getContentResolver().openFileDescriptor(dest,"w")) {
                if(pfd==null) throw new Exception("Could not open destination");
                MovieEncoder.write(getContentResolver(),ordered,pfd.getFileDescriptor(),w,h,sec,cancelled,(frames,total)-> {
                    int p=(int)((frames*100L)/Math.max(1,total));
                    runOnUiThread(()->{progress.setProgress(p); status.setText("Encoding video… "+p+"%");});
                });
                complete=true;
            } catch(Exception ex) {
                error=ex.getMessage()==null?ex.toString():ex.getMessage();
            }
            if(!complete) {
                try { getContentResolver().delete(dest,null,null); } catch(Exception ignored) {}
            }
            final boolean success=complete;
            final String err=error;
            runOnUiThread(()-> {
                exporting=false;
                createButton.setEnabled(true);
                cancelButton.setVisibility(View.GONE);
                if(success) {
                    progress.setProgress(100);
                    lastVideo=dest;
                    openButton.setVisibility(View.VISIBLE);
                    status.setText("✓ MP4 saved successfully in the folder you selected.");
                } else {
                    progress.setVisibility(View.GONE);
                    status.setText(cancelled.get()?"Export cancelled. Partial file removed.":"Export failed: "+err);
                }
            });
        },"GK-MP4-Encoder").start();
    }
}