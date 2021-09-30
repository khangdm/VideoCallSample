package com.alicecallsbob.csdk.android.sample;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.provider.Settings;

/**
 * Service that plays the default android ringtone in the background when started.
 * 
 * The ringtone stops playing when the service is destroyed.
 * 
 * @author CafeX Communications
 *
 */
public class RingtoneBackgroundService extends Service
{    
    private MediaPlayer ringtonePlayer;
    
    @Override
    public IBinder onBind(Intent intent) 
    {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        ringtonePlayer = MediaPlayer.create(getApplicationContext(), Settings.System.DEFAULT_RINGTONE_URI);

        if (!ringtonePlayer.isPlaying())
        {
            ringtonePlayer.start();
        }
        
        return Service.START_NOT_STICKY;
    }
    
    @Override
    public void onDestroy() 
    {
        super.onDestroy();

        if (ringtonePlayer.isPlaying())
        {
            ringtonePlayer.stop();
        }
    }

}
