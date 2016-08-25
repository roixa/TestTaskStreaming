package com.roix.testtaskstreaming;

import android.annotation.TargetApi;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCrypto;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

/**
 * Created by u5 on 8/23/16.
 */
public class StreamingMediaPlayer {

    private MediaExtractor extractor;
    private MediaCodec codec;
    private AudioTrack audioTrack;
    private ByteBuffer[] codecInputBuffers;
    private ByteBuffer[] codecOutputBuffers;
    private ArrayList<Pair<byte[],Integer>> resultBuffer;
    private String url;
    private int maxBufferDurationMSEC=15000;
    private StreamCallback callback;
    private volatile boolean commandMoveToLiveEdge=false;
    private volatile boolean commandStreamAfterRelease=false;


    private final Object lock=new Object();
    private long lastTimePosition;
    public enum Event{
        StartBuffering,
        Playing,
        IncorrectUrl,
        EndOfStream
    }

    public enum State {
        Stopped, // player is stopped and not prepared to play
        Playing,    // playback active
        Paused //
    };

    /**
     * Current player state
     */
    private volatile State state;
    private boolean isMuted=false;

    public StreamingMediaPlayer(){
        state=State.Stopped;
        lastTimePosition=System.currentTimeMillis();

    }

    public void startStreaming(String url){
        this.url=url;
        initAndSetFormat();

        //loadAndDecodeTask();
        //playTask();
    }

    public void restartStreaming(String url){
        this.url=url;
        if(state==State.Stopped){
            startStreaming(url);
        }
        else {
            state=State.Stopped;
            commandStreamAfterRelease=true;

        }
    }

    public void moveToLiveEdge(){
        if(state==State.Stopped)startStreaming(url);
        commandMoveToLiveEdge=true;
    }

    public void start(){
        if(state==State.Stopped)startStreaming(url);
        state=State.Playing;

    }

    public void pause(){
        if(state==State.Stopped) return;
        state=State.Paused;
    }

    public void stop(){
        state=State.Stopped;
        //releaseStreamingResources();

    }

    public void mute(){
        float gain=isMuted?1:0;
        audioTrack.setStereoVolume(gain,gain);
        isMuted=!isMuted;
    }

    public State getState(){return state;}

    public void setCallback(StreamCallback callback){
        this.callback=callback;
    }



    private void initAndSetFormat()  {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sendEvent(Event.StartBuffering);
                    resultBuffer=new ArrayList<>();
                    extractor = new MediaExtractor();

                    extractor.setDataSource(url);
                    MediaFormat format = extractor.getTrackFormat(0);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    // the actual decoder
                    codec = MediaCodec.createDecoderByType(mime);
                    codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
                    codec.start();
                    codecInputBuffers = codec.getInputBuffers();
                    codecOutputBuffers = codec.getOutputBuffers();
                    // get the sample rate to configure AudioTrack
                    int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    // create our AudioTrack instance
                    audioTrack = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_STEREO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            AudioTrack.getMinBufferSize (
                                    sampleRate,
                                    AudioFormat.CHANNEL_OUT_STEREO,
                                    AudioFormat.ENCODING_PCM_16BIT
                            ),
                            AudioTrack.MODE_STREAM
                    );
                    // start playing, we will feed you later
                    audioTrack.play();
                    extractor.selectTrack(0);
                    state=State.Playing;
                    sendEvent(Event.Playing);

                    loadAndDecodeTask();
                    playTask();
                } catch (IOException e) {
                    releaseStreamingResources();
                    sendEvent(Event.IncorrectUrl);
                    e.printStackTrace();
                }

            }
        }).start();

    }

    private void loadAndDecodeTask(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                while(state!=State.Stopped ){
                    int inputBufIndex = codec.dequeueInputBuffer(10000);
                    if (inputBufIndex >= 0) {
                        ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                        int sampleSize =
                                    extractor.readSampleData(dstBuf, 0 /* offset */);
                        if(sampleSize<0){
                            sendEvent(Event.EndOfStream);
                            state=State.Stopped;
                            break;
                        }
                        long presentationTimeUs = extractor.getSampleTime();

                        codec.queueInputBuffer(
                                inputBufIndex,
                                0 /* offset */,
                                sampleSize,
                                presentationTimeUs,
                                0);
                        extractor.advance();
                    }


                    int res = codec.dequeueOutputBuffer(info, 10000);

                    if (res >= 0) {
                        //Log.d(LOG_TAG, "got frame, size " + info.size + "/" + info.presentationTimeUs);

                        int outputBufIndex = res;
                        ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                        final byte[] chunk = new byte[info.size];
                        buf.get(chunk);
                        buf.clear();
                        if(chunk.length > 0) {
                            Log.i("StreamingMediaPlayer", " inputBufIndex " + inputBufIndex +
                                    " outputBufIndex " + outputBufIndex+
                                    " resultBuffer.size "+resultBuffer.size());

                            //audioTrack.write(chunk,0,chunk.length);
                            synchronized (lock) {
                                long chunkDuration=System.currentTimeMillis()-lastTimePosition;
                                lastTimePosition=System.currentTimeMillis();
                                resultBuffer.add(new Pair<>(chunk,new Integer((int)chunkDuration)));
                                int buffDuration=bufferDuration();
                                if(buffDuration>maxBufferDurationMSEC) resultBuffer.remove(0);
                                if(callback!=null)callback.onLoad(buffDuration);
                            }
                            /*
                            if(playPosition<resultBuffer.size()&&resultBuffer.size()>10){
                                byte[] b=resultBuffer.get(playPosition);
                                audioTrack.write(b,0,b.length);
                                playPosition++;
                            }
                            */
                        }
                        codec.releaseOutputBuffer(outputBufIndex, false /* render */);
                    } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        codecOutputBuffers = codec.getOutputBuffers();


                    } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                    } else {
                    }

                }
                releaseStreamingResources();
            }
        }).start();

    }

    private void playTask(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (state!=State.Stopped){
                    synchronized (lock) {

                        if (state==State.Playing&&(audioTrack.getState() == AudioTrack.STATE_INITIALIZED) &&resultBuffer.size()>0) {

                            byte[] b = resultBuffer.get(0).first;
                            audioTrack.write(b, 0, b.length);
                            if(commandMoveToLiveEdge){
                                Pair<byte[],Integer> pair=resultBuffer.get(resultBuffer.size()-1);
                                resultBuffer.clear();
                                resultBuffer.add(pair);
                                commandMoveToLiveEdge=false;
                            }else resultBuffer.remove(0);

                            lastTimePosition=System.currentTimeMillis();
                            if(callback!=null)callback.onLoad(bufferDuration());
                        }
                    }
                }
                //releaseStreamingResources();
            }
        }).start();
    }

    private int bufferDuration(){
        int ret=0;
        for(Pair<byte[],Integer> pair:resultBuffer){
            ret+=pair.second;
        }
        return ret;
    }

    private synchronized void sendEvent(Event event){
        if(callback!=null)callback.onSentEvent(event);
    }

    private void releaseStreamingResources(){
        if(callback!=null)callback.onLoad(0);

            if(codec != null)
        {
            codec.stop();
            codec.release();
            codec = null;
        }
        if(audioTrack != null)
        {
            audioTrack.flush();
            audioTrack.release();
            audioTrack = null;
        }
        if(extractor!=null) {
            extractor.release();
            extractor=null;
        }
        codecInputBuffers=null;
        codecOutputBuffers=null;
        synchronized (lock){
            resultBuffer.clear();
            resultBuffer=null;
        }
        if(commandStreamAfterRelease){
            commandStreamAfterRelease=false;
            startStreaming(url);
        }


    }


}
