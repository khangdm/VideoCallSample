package com.alicecallsbob.csdk.android.sample;

import android.app.Application;

/**
 *
 * @author aburns
 *
 */
public class CSDKSample extends Application
{
	@Override
	public void onCreate()
	{
		super.onCreate();

	    // Since the error-handling of this demo consists of throwing
	    // RuntimeExceptions and we assume that'll terminate the app, we install
	    // this default handler so it's applied to background threads as well.
	    Thread.setDefaultUncaughtExceptionHandler(
	        new Thread.UncaughtExceptionHandler() {
	          public void uncaughtException(final Thread t, final Throwable e) {
	            e.printStackTrace();
	            System.exit(-1);
	          }
	        });
	}
}
