package com.alicecallsbob.csdk.android.sample;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import androidx.core.app.NotificationCompat;

/**
 * Utility class for creating notifications.
 * 
 * @author CafeX Communications
 *
 */
public class NotificationHelper 
{
    /** ID of incoming call notification */
    private static final int INCOMING_CALL_NOTIFICATION_ID = 11123;
    
    /**
     * Displays an incoming call notification.
     * 
     * @param context The context
     * @param callerName The incoming call caller name
     */
    public static void showIncomingCallNotification(Context context, String callerName)
    {
        final NotificationManager notificationManager = (NotificationManager) 
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        NotificationCompat.Builder notificationBuilder =
        		new NotificationCompat.Builder(context)
        		.setSmallIcon(android.R.drawable.ic_menu_call)
        		.setContentTitle("Incoming call")
        		.setContentText("From " + callerName);
        
        final Intent notificationIntent = new Intent(context, InCallActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        notificationBuilder.setContentIntent(contentIntent);
        Notification incomingNotification = notificationBuilder.build();
        
        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, incomingNotification);
        
        //Wake up screen
        final WakeLock screenOn = ((PowerManager)context.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "example");
        screenOn.acquire(5000);
        
        //Play ringtone
        RingtoneHelper.playRingtone(context);
    }
    
    /**
     * Remove the incoming call notification.
     * 
     * @param context The context
     */
    public static void removeIncomingCallNotification(Context context)
    {
        final NotificationManager notificationManager = (NotificationManager) 
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID);
        
        RingtoneHelper.stopRingtone(context);
    }
}
