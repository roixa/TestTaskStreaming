package com.roix.testtaskstreaming;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.FrameworkSampleSource;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer.smoothstreaming.DefaultSmoothStreamingTrackSelector;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingChunkSource;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifestParser;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.upstream.cache.Cache;
import com.google.android.exoplayer.upstream.cache.CacheDataSource;
import com.google.android.exoplayer.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer.upstream.cache.SimpleCache;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.PlayerControl;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;


public class MainActivity extends AppCompatActivity implements StreamCallback,View.OnClickListener{

    private ProgressBar progressBar;
    private ServiceConnection sConn;
    private Intent serviceIntent;
    private StreamService streamService;
    private String url1="http://192.168.1.59:8080/aaaa.mp3";
    private String url2="http://online.radiorecord.ru:8101/rr_128";
    private String url3="http://pub5.radiotunes.com:80/radiotunes_hit70s";


    @Override
    protected void onCreate(Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(this);

        progressBar=(ProgressBar)findViewById(R.id.progressBar);
        progressBar.setMax(15000);
        progressBar.setProgress(0);
        initService();

    }


    @Override
    protected void onStart() {
        super.onStart();
        bindService(serviceIntent, sConn, 0);
        startService(serviceIntent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(sConn);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop player if still playing
    }

    private void initService(){
        final StreamCallback streamCallback=this;
        serviceIntent = new Intent(this, StreamService.class);
        sConn = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder binder) {
                Log.i("play task","onServiceConnected");
                streamService = ((StreamService.MyBinder) binder).getService();
                streamService.initService(url2);
                streamService.setCallback(streamCallback);
            }
            public void onServiceDisconnected(ComponentName name) {

            }
        };
    }


    /**
     * Handle on Play button click
     * @param view View instance
     */
    public void onPlayButtonClick(View view){
        sendBroadcast(new Intent(StreamService.ACTION_PLAY));
    }

    public void onPauseButtonClick(View view){
        sendBroadcast(new Intent(StreamService.ACTION_PAUSE));


    }

    /**
     * Handle on Stop button click
     * @param view View instance
     */
    public void onStopButtonClick(View view){
        sendBroadcast(new Intent(StreamService.ACTION_STOP));
    }

    public void onMuteButtonClick(View view){
        sendBroadcast(new Intent(StreamService.ACTION_MUTE));
    }

    @Override
    public void onClick(View view) {

    }

    @Override
    public void onLoad(long timeMSEC) {
        progressBar.setProgress((int)timeMSEC);
    }

    @Override
    public void onError(String err) {

    }

    @Override
    public void onCompleteStream(String msg) {

    }


}
