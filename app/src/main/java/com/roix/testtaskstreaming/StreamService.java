package com.roix.testtaskstreaming;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.rtp.AudioStream;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.TimeUtils;
import android.widget.RemoteViews;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.TimeRange;
import com.google.android.exoplayer.dash.DashChunkSource;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.PlayerControl;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;
import java.net.InetAddress;

public class StreamService extends Service {
    public static final String ACTION_PLAY="com.roix.testtaskstream.play";
    public static final String ACTION_PAUSE="com.roix.testtaskstream.pause";
    public static final String ACTION_STOP="com.roix.testtaskstream.stop";
    public static final String ACTION_MUTE="com.roix.testtaskstream.mute";
    public enum State{
        STATE1,
        STATE2
    }


    private MediaPlayer mediaPlayer;

    private ExoPlayer exoPlayer;
    private PlayerControl playerControl;
    private ExtractorSampleSource extractorSampleSource;
    MediaCodecAudioTrackRenderer audioTrackRenderer;
    private boolean isBound=false;
    private boolean cachingStarted=false;




    private final long maxCacheDurationMSEK=15000;
    private int RENDERER_COUNT = 1; //since you want to render simple audio
    private int minBufferMs = 1000;
    private int minRebufferMs = 5000;
    private int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private int BUFFER_SEGMENT_COUNT = 256;

    private long startingCachingTime=0;
    private long latency=0;
    private String lastUrl;
    private StreamCallback callback;
    MyBinder binder;

    public StreamService() {
        binder = new MyBinder();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    if(callback==null) continue;

                    callback.onLoad(latency);
                    try {
                        Thread.sleep(300);
                        if(exoPlayer!=null&&!exoPlayer.getPlayWhenReady()&&cachingStarted) latency+=300;
                        long estimatedBufferDuration = ExoPlayer.UNKNOWN_TIME;
                        long estimatedBufferPosition = exoPlayer.getBufferedPosition();
                        if (estimatedBufferPosition != ExoPlayer.UNKNOWN_TIME) {
                            estimatedBufferDuration = estimatedBufferPosition - exoPlayer.getCurrentPosition();
                        }

                        if(extractorSampleSource!=null&&audioTrackRenderer!=null) {
                            Log.i("play task", estimatedBufferDuration+" estimatedBufferDuration"

                            );

                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();

    }

    public void initService(final StreamCallback callback){
        this.callback=callback;
        setNotificationBar();
        initBroadcastReciever();


    }



    public void initPlayer(String url){
        lastUrl=url;
        exoPlayer=ExoPlayer.Factory.newInstance(RENDERER_COUNT,minBufferMs,minRebufferMs);

        DefaultUriDataSource dataSource = new DefaultUriDataSource(getApplicationContext(),
                Util.getUserAgent(getApplicationContext(), StreamService.class.getSimpleName()));
        Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);

        //ExtractorSampleSource – For formats such as MP3, M4A, MP4, WebM, MPEG-TS and AAC.
        extractorSampleSource = new ExtractorSampleSource(Uri.parse(url),
                dataSource, allocator, BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE);
        audioTrackRenderer=new MediaCodecAudioTrackRenderer(extractorSampleSource, MediaCodecSelector.DEFAULT);
        exoPlayer.prepare(audioTrackRenderer);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.seekTo(exoPlayer.getCurrentPosition());
        playerControl=new PlayerControl(exoPlayer);
    }


    @Override
    public IBinder onBind(Intent intent) {
        isBound=true;

        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        isBound=false;
        callback=null;
        return super.onUnbind(intent);
    }



    private void initBroadcastReciever(){
        StreamBroadcastReceiver broadcastReceiver = new StreamBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        intentFilter.addAction(ACTION_PLAY);
        intentFilter.addAction(ACTION_PAUSE);
        intentFilter.addAction(ACTION_STOP);
        intentFilter.addAction(ACTION_MUTE);
        registerReceiver(broadcastReceiver, intentFilter);

    }

    private void setNotificationBar(){
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager notificationManager = (NotificationManager) getSystemService(ns);


        @SuppressWarnings("deprecation")
        Notification notification = new Notification(R.drawable.notification_template_icon_bg, null, System.currentTimeMillis());

        RemoteViews notificationView = new RemoteViews(getPackageName(), R.layout.notification_layout);

        //the intent that is started when the notification is clicked (works)
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingNotificationIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.contentView = notificationView;
        notification.contentIntent = pendingNotificationIntent;
        notification.flags |= Notification.FLAG_NO_CLEAR;

        //this is the intent that is supposed to be called when the button is clicked
        PendingIntent pendingSwitchIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PLAY), 0);
        notificationView.setOnClickPendingIntent(R.id.notifPlayButton, pendingSwitchIntent);

        pendingSwitchIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PAUSE), 0);
        notificationView.setOnClickPendingIntent(R.id.notifPauseButton, pendingSwitchIntent);

        pendingSwitchIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_STOP), 0);
        notificationView.setOnClickPendingIntent(R.id.notifStopButton, pendingSwitchIntent);

        pendingSwitchIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_MUTE), 0);
        notificationView.setOnClickPendingIntent(R.id.notifMuteButton, pendingSwitchIntent);

        notificationManager.notify(1, notification);

    }

    class StreamBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(ACTION_PLAY)){
                if(exoPlayer==null) initPlayer(lastUrl);
                else {
                    long startsTime=System.currentTimeMillis()-startingCachingTime;
                    startingCachingTime=0;
                    extractorSampleSource.prepare(audioTrackRenderer.getPositionUs());

                    //extractorSampleSource.seekToUs(audioTrackRenderer.getPositionUs());
                    long estimatedBufferDuration = ExoPlayer.UNKNOWN_TIME;
                    long estimatedBufferPosition = exoPlayer.getBufferedPosition();
                    if (estimatedBufferPosition != ExoPlayer.UNKNOWN_TIME) {
                        estimatedBufferDuration = estimatedBufferPosition - exoPlayer.getCurrentPosition();
                    }
                    exoPlayer.seekTo(exoPlayer.getCurrentPosition()-estimatedBufferDuration);
                    if(startsTime>maxCacheDurationMSEK){
                        exoPlayer.seekTo(maxCacheDurationMSEK);
                    }
                    if(cachingStarted&&exoPlayer.getPlayWhenReady()){
                        exoPlayer.seekTo(0);
                        latency=0;
                        cachingStarted=false;
                        playerControl.start();
                    }
                    exoPlayer.setPlayWhenReady(true);
                }
            }
            else if(action.equals(ACTION_PAUSE)){
                if(exoPlayer==null)return;
                cachingStarted=true;
                exoPlayer.setPlayWhenReady(false);
                startingCachingTime = System.currentTimeMillis();
            }
            else if(action.equals(ACTION_STOP)){
                if(exoPlayer==null)return;
                exoPlayer.release();
                exoPlayer=null;
                latency=0;
                cachingStarted=false;

            }
            else  if(action.equals(ACTION_MUTE)){
                if(exoPlayer==null)return;

            }


        }
    }

    class MyBinder extends Binder {
        StreamService getService() {
            return StreamService.this;
        }
    }


}
