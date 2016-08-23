package com.roix.testtaskstreaming;

import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;

/**
 * Created by u5 on 8/23/16.
 */
public class StreamingMediaPlayer {
    long mediaLengthInKb;
    long mediaLengthInSeconds;
    File downloadingMediaFile;
    long totalKbRead;
    long INTIAL_KB_BUFFER;
    MediaPlayer mediaPlayer;
    Handler handle;
    File bufferedFile;
    String cachedir;
    public StreamingMediaPlayer(String cachedir){
        this.cachedir=cachedir;
        handle=new Handler();
    }

    public void startStreaming(final String mediaUrl, long mediaLengthInKb, long mediaLengthInSeconds) throws IOException {

        this.mediaLengthInKb = mediaLengthInKb;
        this.mediaLengthInSeconds = mediaLengthInSeconds;

        Runnable r = new Runnable() {

            public void run() {

                try {

                    downloadAudioIncrement(mediaUrl);

                } catch (IOException e) {

                    Log.e(getClass().getName(),"Initialization error for fileUrl=" + mediaUrl);
                    return;

                }

            }

        };
        new Thread(r).start();

    }

    public void downloadAudioIncrement(String mediaUrl) throws IOException {

// First establish connection to the media provider
        Log.i(getClass().getName(), "downloadAudioIncrement:" + mediaUrl);

        URLConnection cn = new URL(mediaUrl).openConnection();
        cn.connect();
        InputStream stream = cn.getInputStream();
        if (stream == null) {

            Log.e(getClass().getName(), "Unable to create InputStream for mediaUrl:" + mediaUrl);

        }
        Log.i(getClass().getName(), "InputStream:" + mediaUrl);

// Create the temporary file for buffering data into
        downloadingMediaFile = File.createTempFile("downloadingMedia", ".dat");
        FileOutputStream out = new FileOutputStream(downloadingMediaFile);
        Log.i(getClass().getName(), "FileOutputStream:" );

// Start reading data from the URL stream
        byte buf[] = new byte[16384];
        int totalBytesRead = 0, incrementalBytesRead = 0;
        do {

            int numread = stream.read(buf);
            Log.i(getClass().getName(), "while:" + numread);

            if (numread <= 0) {

// Nothing left to read so quit
                break;

            } else {

                out.write(buf, 0, numread);
                totalBytesRead += numread;
                incrementalBytesRead += numread;
                totalKbRead = totalBytesRead/1000;

// Test whether we need to transfer buffered data to the MediaPlayer
                testMediaBuffer();

            }

        } while (true);

// Lastly transfer fully loaded audio to the MediaPlayer and close the InputStream
        stream.close();

    }

    private void testMediaBuffer() {

// We’ll place our following code into a Runnable so the Handler can call it for running
// on the main UI thread
        Runnable updater = new Runnable() {

            public void run() {

                if (mediaPlayer == null) {

// The MediaPlayer has not yet been created so see if we have
// the minimum buffered data yet.
// For our purposes, we take the minimum buffered requirement to be:
// INTIAL_KB_BUFFER = 96*10/8;//assume 96kbps*10secs/8bits per byte
                    if ( totalKbRead >= INTIAL_KB_BUFFER) {

                        try {

// We have enough buffered content so start the MediaPlayer
                            startMediaPlayer(bufferedFile);

                        } catch (Exception e) {

                            Log.e(getClass().getName(), "Error copying buffered conent.", e);

                        }

                    }

                } else if ( mediaPlayer.getDuration() -mediaPlayer.getCurrentPosition() <= 1000 ){

// The MediaPlayer has been started and has reached the end of its buffered
// content. We test for < 1second of data (i.e. 1000ms) because the media
// player will often stop when there are still a few milliseconds of data left to play
                    transferBufferToMediaPlayer();

                }

            }

        };
        handle.post(updater);

    }
    private void startMediaPlayer(File bufferedFile) throws IOException {

        try {

            bufferedFile = File.createTempFile("playingMedia", ".dat");
            copy(downloadingMediaFile, bufferedFile);} catch (IOException e) {

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(bufferedFile.getAbsolutePath());
            mediaPlayer.prepare();

            Log.e(getClass().getName(), "Error initializing the MediaPlaer.", e);
            return;

        }

    }
    public void copy(File src, File dst) throws IOException {
        FileInputStream inStream = new FileInputStream(src);
        FileOutputStream outStream = new FileOutputStream(dst);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();
    }

    private void transferBufferToMediaPlayer() {

        try {

// Determine if we need to restart the player after transferring data (e.g. perhaps the user
// pressed pause) & also store the current audio position so we can reset it later.
            boolean wasPlaying = mediaPlayer.isPlaying();
            int curPosition = mediaPlayer.getCurrentPosition();
            mediaPlayer.pause();

// Copy the current buffer file as we can’t download content into the same file that
// the MediaPlayer is reading from.
            File bufferedFile = File.createTempFile("playingMedia", ".dat");
            copy(downloadingMediaFile, bufferedFile);

// Create a new MediaPlayer. We’ve tried reusing them but that seems to result in
// more system crashes than simply creating new ones.
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(bufferedFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.seekTo(curPosition);

// Restart if at end of prior beuffered content or mediaPlayer was previously playing.
// NOTE: We test for < 1second of data because the media player can stop when there is still
// a few milliseconds of data left to play
            boolean atEndOfFile = mediaPlayer.getDuration() - mediaPlayer.getCurrentPosition() <= 1000;
            if (wasPlaying || atEndOfFile){

                mediaPlayer.start();

            }

        }catch (Exception e) {

            Log.e(getClass().getName(), "Error updating to newly loaded content.", e);

        }

    }



}
