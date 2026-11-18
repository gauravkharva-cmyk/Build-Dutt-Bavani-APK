package com.gaurav.oshoworld;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;
import java.util.ArrayList;

public final class PlayerService extends Service {
    static final String PLAY="com.gaurav.oshoworld.PLAY";
    static final String TOGGLE="com.gaurav.oshoworld.TOGGLE";
    static final String NEXT="com.gaurav.oshoworld.NEXT";
    static final String PREV="com.gaurav.oshoworld.PREV";
    private MediaPlayer mediaPlayer;
    private MediaSession session;
    private AudioManager audio;
    private String currentUri="",currentTitle="OSHO Discourse";
    private boolean prepared=false;
    private final Handler ticker=new Handler(Looper.getMainLooper());
    private AudioManager.OnAudioFocusChangeListener focus;
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager n=getSystemService(NotificationManager.class);
        n.createNotificationChannel(new NotificationChannel("osho_playback","OSHO background audio",NotificationManager.IMPORTANCE_LOW));
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        session=new MediaSession(this,"OSHO Premium Player");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){resume();}
            @Override public void onPause(){pause();}
            @Override public void onSkipToNext(){skip(1);}
            @Override public void onSkipToPrevious(){skip(-1);}
            @Override public void onSeekTo(long pos){if(prepared&&mediaPlayer!=null)mediaPlayer.seekTo((int)pos);}
            @Override public void onStop(){pause();stopSelf();}
        });
        session.setActive(true);
        focus=change -> {
            if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)pause();
            else if(change==AudioManager.AUDIOFOCUS_GAIN&&mediaPlayer!=null&&prepared)resume();
            else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK&&mediaPlayer!=null)mediaPlayer.setVolume(.25f,.25f);
        };
        currentUri=getSharedPreferences("playback",MODE_PRIVATE).getString("uri","");
        currentTitle=getSharedPreferences("playback",MODE_PRIVATE).getString("title","OSHO Discourse");
        ticker.postDelayed(new Runnable(){
            @Override public void run(){
                if(prepared&&mediaPlayer!=null) {
                    try{
                        getSharedPreferences("playback",MODE_PRIVATE).edit()
                            .putInt("position",mediaPlayer.getCurrentPosition())
                            .putBoolean("playing",mediaPlayer.isPlaying()).apply();
                    }catch(Exception ignored){}
                }
                ticker.postDelayed(this,4000);
            }
        },4000);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?TOGGLE:intent.getAction();
        if(PLAY.equals(action)){
            String uri=intent.getStringExtra("uri");
            String title=intent.getStringExtra("title");
            if(uri!=null&&!uri.isEmpty())startTrack(uri,title==null?"OSHO Discourse":title);
        }else if(NEXT.equals(action))skip(1);
        else if(PREV.equals(action))skip(-1);
        else if(TOGGLE.equals(action)){
            if(mediaPlayer!=null&&prepared&&mediaPlayer.isPlaying())pause();
            else if(mediaPlayer!=null&&prepared)resume();
            else if(!currentUri.isEmpty())startTrack(currentUri,currentTitle);
        }
        updateNotice();
        return START_STICKY;
    }
    private void startTrack(String uri,String title){
        try {
            boolean restore=uri.equals(currentUri);
            releasePlayer();
            currentUri=uri;currentTitle=title;
            int saved=restore?getSharedPreferences("playback",MODE_PRIVATE).getInt("position",0):0;
            getSharedPreferences("playback",MODE_PRIVATE).edit()
                .putString("uri",uri).putString("title",title)
                .putInt("position",saved).apply();
            mediaPlayer=new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mediaPlayer.setDataSource(this,Uri.parse(uri));
            mediaPlayer.setOnPreparedListener(p -> {
                prepared=true;
                if(saved>0)p.seekTo(saved);
                resume();
            });
            mediaPlayer.setOnCompletionListener(p -> {
                getSharedPreferences("playback",MODE_PRIVATE).edit().putInt("position",0).apply();
                skip(1);
            });
            mediaPlayer.setOnErrorListener((p,what,extra)->{
                prepared=false;updateNotice();return false;
            });
            mediaPlayer.prepareAsync();
        }catch(Exception ex){
            prepared=false;
            getSharedPreferences("playback",MODE_PRIVATE).edit().putBoolean("playing",false).apply();
        }
    }
    private void resume(){
        try{
            if(!prepared||mediaPlayer==null)return;
            audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
            mediaPlayer.setVolume(1f,1f);
            mediaPlayer.start();
            float speed=getSharedPreferences("playback",MODE_PRIVATE).getFloat("speed",1f);
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
            updateNotice();
        }catch(Exception ignored){}
    }
    private void pause(){
        if(prepared&&mediaPlayer!=null){
            try{if(mediaPlayer.isPlaying())mediaPlayer.pause();}catch(Exception ignored){}
        }
        updateNotice();
    }
    private void skip(int d){
        ArrayList<JSONObject> a=DownloadService.readFinished(this);
        if(a.isEmpty())return;
        int index=-1;
        for(int i=0;i<a.size();i++)if(currentUri.equals(a.get(i).optString("uri"))){index=i;break;}
        int newIndex=index<0?(d>0?0:a.size()-1):(index+d+a.size())%a.size();
        JSONObject item=a.get(newIndex);
        startTrack(item.optString("uri"),item.optString("title","OSHO Discourse"));
    }
    private PendingIntent command(String action,int requestCode){
        Intent i=new Intent(this,PlayerService.class).setAction(action);
        return PendingIntent.getService(this,requestCode,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private void updateNotice(){
        if(currentUri.isEmpty())return;
        boolean playing=false;
        try{playing=prepared&&mediaPlayer!=null&&mediaPlayer.isPlaying();}catch(Exception ignored){}
        Intent i=new Intent(this,MainActivity.class);
        PendingIntent open=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=new Notification.Builder(this,"osho_playback")
            .setContentTitle(currentTitle).setContentText("OSHO World · background audio")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(open)
            .setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous,"Previous",command(PREV,1))
            .addAction(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,
                playing?"Pause":"Play",command(TOGGLE,2))
            .addAction(android.R.drawable.ic_media_next,"Next",command(NEXT,3))
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2));
        if(Build.VERSION.SDK_INT>=29)startForeground(2202,b.build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(2202,b.build());
        long position=0;
        if(prepared&&mediaPlayer!=null)try{position=mediaPlayer.getCurrentPosition();}catch(Exception ignored){}
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|
                        PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO)
            .setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,position,playing?1f:0f)
            .build());
        session.setMetadata(new android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE,currentTitle)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST,"OSHO")
            .build());
    }
    private void releasePlayer(){
        prepared=false;
        if(mediaPlayer!=null)try{mediaPlayer.reset();mediaPlayer.release();}catch(Exception ignored){}
        mediaPlayer=null;
    }
    @Override public void onDestroy(){
        ticker.removeCallbacksAndMessages(null);
        releasePlayer();
        if(audio!=null&&focus!=null)audio.abandonAudioFocus(focus);
        if(session!=null){session.setActive(false);session.release();}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
