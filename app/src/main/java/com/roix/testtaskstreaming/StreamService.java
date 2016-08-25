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
import android.media.MediaCodec;
import android.media.MediaExtractor;
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


    private boolean isBound=false;
    StreamingMediaPlayer streamingMediaPlayer;


    private String lastUrl;
    private MyBinder binder;

    public StreamService() {
        binder = new MyBinder();

    }

    public void initService(String url){
        setNotificationBar();
        initBroadcastReciever();
        if(streamingMediaPlayer!=null&&streamingMediaPlayer.getState()!= StreamingMediaPlayer.State.Stopped)return;
        lastUrl=url;
        streamingMediaPlayer=new StreamingMediaPlayer();
        streamingMediaPlayer.startStreaming(url);

    }

    public void playWithThisUrl(String url){
        lastUrl=url;
        streamingMediaPlayer.restartStreaming(url);
    }

    public void setCallback(StreamCallback callback){
        if(streamingMediaPlayer!=null)streamingMediaPlayer.setCallback(callback);
    }


    @Override
    public IBinder onBind(Intent intent) {
        isBound=true;
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        isBound=false;
        streamingMediaPlayer.setCallback(null);

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
                if(streamingMediaPlayer.getState()==StreamingMediaPlayer.State.Playing) streamingMediaPlayer.moveToLiveEdge();
                streamingMediaPlayer.start();
            }
            else if(action.equals(ACTION_PAUSE)){
                streamingMediaPlayer.pause();
            }
            else if(action.equals(ACTION_STOP)){
                streamingMediaPlayer.stop();

            }
            else  if(action.equals(ACTION_MUTE)){
                streamingMediaPlayer.mute();

            }

        }
    }

    class MyBinder extends Binder {
        StreamService getService() {
            return StreamService.this;
        }
    }


}
