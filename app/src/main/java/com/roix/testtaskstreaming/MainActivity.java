package com.roix.testtaskstreaming;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
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
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
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
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity implements StreamCallback,View.OnClickListener,AdapterView.OnItemClickListener{

    private ProgressBar progressBar;
    private ServiceConnection sConn;
    private Intent serviceIntent;
    private StreamService streamService;
    private TextView urlTextView;
    private ListView listView;
    private String url1="http://192.168.1.59:8080/aaaa.mp3";
    private String url2="http://online.radiorecord.ru:8101/rr_128";

    private String currUrl=url2;
    private ArrayList<String> urls;
    ArrayAdapter<String> adapter;


    @Override
    protected void onCreate(Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        urlTextView=(TextView)findViewById(R.id.textView);
        listView=(ListView)findViewById(R.id.listView);
        urlTextView.setText(currUrl);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(this);

        progressBar=(ProgressBar)findViewById(R.id.progressBar);
        progressBar.setMax(15000);
        progressBar.setProgress(0);

        urls=new ArrayList<>();
        urls.add(url2);
        urls.add(url1);
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, urls);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);


        initService();
        startService(serviceIntent);

    }


    @Override
    protected void onStart() {
        super.onStart();
        bindService(serviceIntent, sConn, 0);
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
                streamService.initService(currUrl);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add stream address");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD );
        builder.setView(input);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newUrl = input.getText().toString();
                urls.add(newUrl);
                adapter.notifyDataSetChanged();


            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();

    }

    @Override
    public void onLoad(long timeMSEC) {
        progressBar.setProgress((int)timeMSEC);
    }

    @Override
    public void onSentEvent(final StreamingMediaPlayer.Event event) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(event== StreamingMediaPlayer.Event.StartBuffering) urlTextView.setText("please wait");
                else if(event== StreamingMediaPlayer.Event.Playing)urlTextView.setText(currUrl);
                else if(event== StreamingMediaPlayer.Event.EndOfStream) urlTextView.setText("end of stream");
                else if(event== StreamingMediaPlayer.Event.IncorrectUrl) urlTextView.setText("dont read url");


            }
        });
    }




    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        String url=urls.get(i);
        currUrl=url;
        streamService.playWithThisUrl(url);
    }
}
