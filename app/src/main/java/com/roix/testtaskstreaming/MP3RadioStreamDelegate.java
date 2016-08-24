package com.roix.testtaskstreaming;

/**
 * Created by u5 on 8/24/16.
 */
public interface MP3RadioStreamDelegate {

    public void onRadioPlayerPlaybackStarted(MP3RadioStreamPlayer player);
    public void onRadioPlayerStopped(MP3RadioStreamPlayer player);
    public void onRadioPlayerError(MP3RadioStreamPlayer player);
    public void onRadioPlayerBuffering(MP3RadioStreamPlayer player);
}
