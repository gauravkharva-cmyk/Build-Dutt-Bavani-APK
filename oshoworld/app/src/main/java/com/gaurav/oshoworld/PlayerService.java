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

/**
 * Independent foreground media service. Activity lifecycle changes must not stop audio.
 * Positions are stored per URI so a recording resumes after pause, app switches, or process recreation.
 */
public final class PlayerService extends Service {
    static final String PLAY="com.gaurav.oshoworld.PLAY";
    static final String RESUME="com.gaurav.oshoworld.RESUME";
    static final String TOGGLE="com.gaurav.oshoworld.TOGGLE";
    static final String NEXT="com.gaurav.oshoworld.NEXT";
    static final String PREV="com.gaurav.oshoworld.PREV";
    static final String SEEK="com.gaurav.oshoworld.SEEK";
    static final String STOP="com.gaurav.oshoworld.STOP";
    private static final int PLAYER_NOTIFICATION=2202;
    private static final String CHANNEL="osho_playback";

    private final Handler handler=new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private MediaSession mediaSession;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private AudioManager.OnAudioFocusChangeListener focusListener;
    private String currentUri="",currentTitle="OSHO Discourse";
    private boolean prepared=false, preparing=false, playWhenPrepared=false, resumeAfterFocus=false;
    private int generation=0;
    private final Runnable saveProgress=new Runnable(){
        @Override public void run(){
            try {
                if(prepared && player!=null)persistPosition(player.isPlaying());
            } catch(Exception ignored){}
            handler.postDelayed(this,2000);
        }
    };

    private android.content.SharedPreferences prefs(){
        return getSharedPreferences("playback",MODE_PRIVATE);
    }
    private String positionKey(String uri) {
        return "position_"+Integer.toHexString(uri.hashCode());
    }
    private int savedPosition(String uri){
        int fallback=uri.equals(prefs().getString("uri",""))?prefs().getInt("position",0):0;
        return Math.max(0,prefs().getInt(positionKey(uri),fallback));
    }
    private void persistPosition(boolean playing){
        if(currentUri.isEmpty())return;
        int position=savedPosition(currentUri);
        try {
            if(prepared && player!=null)position=Math.max(0,player.getCurrentPosition());
        }catch(Exception ignored){}
        prefs().edit().putString("uri",currentUri).putString("title",currentTitle)
            .putInt("position",position).putInt(positionKey(currentUri),position)
            .putBoolean("playing",playing).putBoolean("wantPlaying",playing).apply();
    }
    private void onFocusChanged(int change){
        if(change==AudioManager.AUDIOFOCUS_GAIN){
            if(player!=null)try{player.setVolume(1f,1f);}catch(Exception ignored){}
            if(resumeAfterFocus){
                resumeAfterFocus=false;
                resumePlayback();
            }
        }else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){
            boolean wasPlaying=isPlaying();
            resumeAfterFocus=wasPlaying;
            pausePlayback(true);
        }else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){
            if(player!=null)try{player.setVolume(.2f,.2f);}catch(Exception ignored){}
        }else if(change==AudioManager.AUDIOFOCUS_LOSS){
            resumeAfterFocus=false;
            pausePlayback(true);
        }
    }
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager manager=getSystemService(NotificationManager.class);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"OSHO background music",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Playback keeps running when another app is open or the screen is locked");
        manager.createNotificationChannel(channel);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        focusListener=this::onFocusChanged;
        AudioAttributes attributes=new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        if(Build.VERSION.SDK_INT>=26){
            focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener,handler)
                .setAcceptsDelayedFocusGain(false)
                .build();
        }
        currentUri=prefs().getString("uri","");
        currentTitle=prefs().getString("title","OSHO Discourse");
        mediaSession=new MediaSession(this,"OSHO World Player");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
            |MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){resumeFromLast();}
            @Override public void onPause(){pausePlayback(false);}
            @Override public void onStop(){pausePlayback(false);stopSelf();}
            @Override public void onSkipToNext(){skip(1);}
            @Override public void onSkipToPrevious(){skip(-1);}
            @Override public void onSeekTo(long position){seekTo((int)position);}
        },handler);
        mediaSession.setActive(true);
        handler.postDelayed(saveProgress,2000);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){
            // START_STICKY after the OS reclaimed this foreground service.
            if(prefs().getBoolean("wantPlaying",false))resumeFromLast();
            else updateNotification();
            return START_STICKY;
        }
        String action=intent.getAction();
        if(PLAY.equals(action)){
            String uri=intent.getStringExtra("uri");
            String title=intent.getStringExtra("title");
            if(uri!=null&&!uri.isEmpty())playTrack(uri,title==null?"OSHO Discourse":title);
        }else if(RESUME.equals(action)){
            resumeFromLast();
        }else if(TOGGLE.equals(action)){
            if(isPlaying())pausePlayback(false);
            else resumeFromLast();
        }else if(NEXT.equals(action)){skip(1);}
        else if(PREV.equals(action)){skip(-1);}
        else if(SEEK.equals(action)){seekTo(intent.getIntExtra("position",0));}
        else if(STOP.equals(action)){pausePlayback(false);stopSelf();}
        else {updateNotification();}
        return START_STICKY;
    }
    private boolean isPlaying(){
        try{return prepared&&player!=null&&player.isPlaying();}
        catch(Exception ignored){return false;}
    }
    private void resumeFromLast(){
        if(isPlaying())return;
        if(prepared && player!=null){resumePlayback();return;}
        if(preparing && player!=null){playWhenPrepared=true;return;}
        if(!currentUri.isEmpty())playTrack(currentUri,currentTitle);
    }
    private void playTrack(String uri,String title){
        if(uri.equals(currentUri) && player!=null){
            currentTitle=title;
            if(prepared){resumePlayback();return;}
            if(preparing){playWhenPrepared=true;updateNotification();return;}
        }
        if(!uri.equals(currentUri))persistPosition(isPlaying());
        releasePlayer();
        currentUri=uri;currentTitle=title;
        final int requestedPosition=savedPosition(uri);
        final int playGeneration=++generation;
        preparing=true;prepared=false;playWhenPrepared=true;
        prefs().edit().putString("uri",uri).putString("title",title)
            .putInt("position",requestedPosition)
            .putBoolean("wantPlaying",true).apply();
        try{
            MediaPlayer mp=new MediaPlayer();
            player=mp;
            mp.setWakeMode(getApplicationContext(),PowerManager.PARTIAL_WAKE_LOCK);
            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(this,Uri.parse(uri));
            mp.setOnPreparedListener(p->{
                if(playGeneration!=generation||p!=player)return;
                preparing=false;prepared=true;
                try{
                    int duration=p.getDuration();
                    int restore=requestedPosition;
                    if(duration>0 && restore>duration-2000)restore=0;
                    updateMetadata(duration);
                    if(restore>0){
                        final int offset=restore;
                        p.setOnSeekCompleteListener(ready->{
                            if(playGeneration!=generation||ready!=player)return;
                            p.setOnSeekCompleteListener(null);
                            if(playWhenPrepared)resumePlayback();
                            else updateNotification();
                        });
                        p.seekTo(offset,MediaPlayer.SEEK_CLOSEST);
                        updateNotification();
                    }else if(playWhenPrepared)resumePlayback();
                    else updateNotification();
                }catch(Exception ex){if(playWhenPrepared)resumePlayback();}
            });
            mp.setOnCompletionListener(p->{
                if(playGeneration!=generation)return;
                prefs().edit().putInt(positionKey(currentUri),0).putInt("position",0).apply();
                // Avoid saving the completed end-position when releasing the track.
                prepared=false;
                skip(1);
            });
            mp.setOnErrorListener((p,what,extra)->{
                if(playGeneration==generation){
                    persistPosition(false);
                    preparing=false;prepared=false;
                    updateNotification();
                }
                return true;
            });
            // startForeground is called before asynchronous preparation, keeping the service active
            updateNotification();
            mp.prepareAsync();
        }catch(Exception e){
            preparing=false;prepared=false;playWhenPrepared=false;
            persistPosition(false);
            updateNotification();
        }
    }
    private boolean requestFocus(){
        if(audio==null)return true;
        try{
            int result=Build.VERSION.SDK_INT>=26
                ?audio.requestAudioFocus(focusRequest)
                :audio.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
            return result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }catch(Exception ignored){return false;}
    }
    private void resumePlayback(){
        if(!prepared||player==null)return;
        if(isPlaying()){updateNotification();return;}
        playWhenPrepared=true;
        resumeAfterFocus=false;
        if(!requestFocus()){
            // Respect another active media app. Ordinary app switching does not take audio focus.
            updateNotification();
            return;
        }
        try{
            player.setVolume(1f,1f);
            player.start();
            float speed=prefs().getFloat("speed",1f);
            if(Build.VERSION.SDK_INT>=23)
                player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));
            persistPosition(true);
        }catch(Exception ignored){persistPosition(false);}
        updateNotification();
    }
    private void pausePlayback(boolean focusPause){
        if(!focusPause)resumeAfterFocus=false;
        playWhenPrepared=false;
        if(prepared&&player!=null){
            try {if(player.isPlaying())player.pause();}catch(Exception ignored){}
        }
        persistPosition(false); // Save immediately, not just on the periodic timer.
        updateNotification();
    }
    private void seekTo(int position){
        if(!prepared||player==null)return;
        try{
            int duration=player.getDuration();
            int target=Math.max(0,duration>0?Math.min(position,duration):position);
            player.seekTo(target);
            prefs().edit().putInt(positionKey(currentUri),target)
                .putInt("position",target).apply();
            updateNotification();
        }catch(Exception ignored){}
    }
    private void skip(int delta){
        final String from=currentUri;
        final int token=generation;
        new Thread(()->{
            ArrayList<JSONObject> list=DownloadService.readFinished(getApplicationContext());
            handler.post(()->{
                if(token!=generation || !from.equals(currentUri) || list.isEmpty())return;
                int index=-1;
                for(int i=0;i<list.size();i++){
                    if(from.equals(list.get(i).optString("uri"))){index=i;break;}
                }
                int next=index<0?(delta>0?0:list.size()-1)
                    :(index+delta+list.size())%list.size();
                JSONObject entry=list.get(next);
                String uri=entry.optString("uri","");
                if(!uri.isEmpty()){
                    // Preserve the old track position unless it has completed.
                    playTrack(uri,entry.optString("title","OSHO Discourse"));
                }
            });
        },"OSHO-find-next-audio").start();
    }
    private PendingIntent command(String action,int requestCode){
        Intent intent=new Intent(this,PlayerService.class).setAction(action);
        return PendingIntent.getService(this,requestCode,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private void updateMetadata(int duration){
        if(mediaSession==null)return;
        android.media.MediaMetadata.Builder metadata=new android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE,currentTitle)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST,"OSHO")
            .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM,"OSHO World");
        if(duration>0)metadata.putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,duration);
        mediaSession.setMetadata(metadata.build());
    }
    private void updateNotification(){
        if(currentUri.isEmpty()||mediaSession==null)return;
        boolean playing=isPlaying();
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent openIntent=PendingIntent.getActivity(this,0,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL)
            .setContentTitle(currentTitle)
            .setContentText(playing?"Playing in the background":preparing?"Loading recording…":"Paused · tap Play to resume")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous,"Previous",command(PREV,1))
            .addAction(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,
                playing?"Pause":"Resume",command(TOGGLE,2))
            .addAction(android.R.drawable.ic_media_next,"Next",command(NEXT,3))
            .setStyle(new Notification.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0,1,2))
            .build();
        if(Build.VERSION.SDK_INT>=29)
            startForeground(PLAYER_NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(PLAYER_NOTIFICATION,notification);
        long position=savedPosition(currentUri);
        if(prepared&&player!=null)try{position=player.getCurrentPosition();}catch(Exception ignored){}
        int state=playing?PlaybackState.STATE_PLAYING:
            preparing?PlaybackState.STATE_BUFFERING:PlaybackState.STATE_PAUSED;
        mediaSession.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE
                |PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SEEK_TO
                |PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS
                |PlaybackState.ACTION_STOP)
            .setState(state,position,playing?1f:0f).build());
        updateMetadata(prepared&&player!=null?safeDuration():0);
    }
    private int safeDuration(){
        try{return player.getDuration();}catch(Exception ignored){return 0;}
    }
    private void releasePlayer(){
        if(player!=null){
            try{player.reset();player.release();}catch(Exception ignored){}
            player=null;
        }
        prepared=false;preparing=false;playWhenPrepared=false;
    }
    @Override public void onTaskRemoved(Intent rootIntent){
        // User switching apps or clearing the UI task must not stop the foreground player.
        // Android can still reclaim the process, so progress is also periodically persisted.
    }
    @Override public void onDestroy(){
        handler.removeCallbacks(saveProgress);
        persistPosition(isPlaying());
        releasePlayer();
        if(audio!=null){
            try{
                if(Build.VERSION.SDK_INT>=26)audio.abandonAudioFocusRequest(focusRequest);
                else audio.abandonAudioFocus(focusListener);
            }catch(Exception ignored){}
        }
        if(mediaSession!=null){mediaSession.setActive(false);mediaSession.release();}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
