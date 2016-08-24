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
    private ArrayList<byte []> resultBuffer;
    private String url;
    private int playPosition=0;

    public enum State {
        Retrieving, // retrieving music (filling buffer)
        Stopped,    // player is stopped and not prepared to play
        Playing,    // playback active
    };

    /**
     * Current player state
     */
    State mState = State.Retrieving;

    public StreamingMediaPlayer(final String url){
       this.url=url;
        resultBuffer=new ArrayList<>();
        try {
            initAndSetFormat();
        } catch (IOException e) {
            e.printStackTrace();
        }
        loadAndDecodeTask();

        //playTask();

    }

    public void initAndSetFormat() throws IOException {
        extractor = new MediaExtractor();
        try {
            extractor.setDataSource(url);
        } catch (Exception e) {
            return;
        }
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
    }

    private void loadAndDecodeTask(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                while(true ){
                    int inputBufIndex = codec.dequeueInputBuffer(20000);
                    if (inputBufIndex >= 0) {
                        ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                        int sampleSize =
                                    extractor.readSampleData(dstBuf, 0 /* offset */);
                        long presentationTimeUs = extractor.getSampleTime();
                        codec.queueInputBuffer(
                                inputBufIndex,
                                0 /* offset */,
                                sampleSize,
                                presentationTimeUs,
                                0);
                        extractor.advance();
                    }


                    int res = codec.dequeueOutputBuffer(info, 20000);

                    if (res >= 0) {
                        //Log.d(LOG_TAG, "got frame, size " + info.size + "/" + info.presentationTimeUs);

                        int outputBufIndex = res;
                        ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                        final byte[] chunk = new byte[info.size];
                        buf.get(chunk);
                        buf.clear();
                        if(chunk.length > 0) {
                            Log.i("StreamingMediaPlayer", " inputBufIndex " + inputBufIndex + " outputBufIndex " + outputBufIndex+" resultBuffer.size "+resultBuffer.size());

                            //audioTrack.write(chunk,0,chunk.length);
                            resultBuffer.add(chunk);
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
            }
        }).start();

    }

    private void playTask(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    if((audioTrack.getState()==AudioTrack.STATE_INITIALIZED)&&resultBuffer.size()>5){
                        byte[] b=resultBuffer.get(playPosition);
                        audioTrack.write(b,0,b.length);
                        playPosition++;
                    }
                }
            }
        }).start();
    }


}
