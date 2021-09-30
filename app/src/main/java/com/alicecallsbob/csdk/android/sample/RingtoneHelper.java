package com.alicecallsbob.csdk.android.sample;

import android.content.Context;
import android.content.Intent;

/**
 * Helper class for playing a background ringtone.
 * 
 * @author CafeX Communications
 *
 */
public class RingtoneHelper 
{
    /**
     * Plays the ringtone
     * 
     * @param context
     */
    public static void playRingtone(Context context)
    {
       context.startService(getServiceIntent(context));
    }
    
    /**
     * Stops playing the ringtone
     * 
     * @param context
     */
    public static void stopRingtone(Context context)
    {
        context.stopService(getServiceIntent(context));
    }
    
    private static Intent getServiceIntent(Context context)
    {
        return new Intent(context, RingtoneBackgroundService.class);
    }
    
}
