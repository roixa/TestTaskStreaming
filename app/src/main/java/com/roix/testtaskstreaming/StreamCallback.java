package com.roix.testtaskstreaming;

/**
 * Created by u5 on 8/21/16.
 */
public interface StreamCallback {
    void onLoad(long timeMSEC);
    void onSentEvent(StreamingMediaPlayer.Event event);
}
