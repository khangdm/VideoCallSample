package com.alicecallsbob.csdk.android.sample;

import static com.alicecallsbob.csdk.android.sample.IncomingCallFragment.CALL_ID_ARG;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTabHost;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;

import com.alicecallsbob.fcsdk.android.aed.AED;
import com.alicecallsbob.fcsdk.android.phone.Call;
import com.alicecallsbob.fcsdk.android.phone.CallListener;
import com.alicecallsbob.fcsdk.android.phone.CallStatus;
import com.alicecallsbob.fcsdk.android.phone.CallStatusInfo;
import com.alicecallsbob.fcsdk.android.phone.MediaDirection;
import com.alicecallsbob.fcsdk.android.phone.Phone;
import com.alicecallsbob.fcsdk.android.phone.PhoneListener;
import com.alicecallsbob.fcsdk.android.phone.PhoneVideoCaptureResolution;
import com.alicecallsbob.fcsdk.android.phone.PhoneVideoCaptureSetting;
import com.alicecallsbob.fcsdk.android.uc.UC;
import com.alicecallsbob.fcsdk.android.uc.UCFactory;
import com.alicecallsbob.fcsdk.android.uc.UCListener;

@SuppressWarnings("deprecation")
public final class Main extends FragmentActivity implements PhoneListener,
	CallListener, UCListener
{
	/** Identifier String for LogCat output. */
	protected static final String TAG = "Main";

	/*
	 * Keys used to pass the data from the login activity to this activity.
	 */
	/** Key for the session key value. */
	public static final String DATA_SESSION_KEY = "_session_key";
	/** Key for the URL we use to logout. */
	public static final String DATA_LOGOUT_URL = "_logout_url";
	/** Key for the UC initialisation flag. */
    public static final String DATA_UC_INITIALIZED = "_uc_initialized";
    /** Key for the UC useCookies flag. */
    public static final String DATA_UC_USE_COOKIES = "_uc_use_cookies";
    /** Key for the UC supportsRenegotiation flag. */
	public static final String DATA_UC_SUPPORTS_RENEG = "_uc_supports_renegotiation";

    /*
     * Keys used to pass data from resolution selection activity to this activity
     */
    public static final String DATA_RECOMMENDED_SETTINGS = "_recommended_settings";
    /** Key for the selected resolution */
    public static final String DATA_SELECTED_RESOLUTION = "_selected_resolution";
    /** Key for the selected camera */
    public static final String DATA_SELECTED_CAMERA = "_selected_camera";
    /** Key for the selected media direction. */
    public static final String DATA_SELECTED_MEDIA_DIRECTION = "_selected_media_direction";

	public interface ActiveCallsBarListener
	{
		/**
		 * Called when we want to update the active calls bar, either to show
		 * it, hide it or update the number of calls displayed.  If we call
		 * this because a call has ended, we pass that Call to the listener.
		 * @param call The Call that's just ended
		 */
		public void updateActiveCallsBarVisibility(final Call call);
	}

	public static ActiveCallsBarListener mActiveCallsBarListener;

	/**
	 * An Asynchronous task object that runs in the background to create and start the UC session.
	 */
	private class UCStartTask extends AsyncTask<String, Void, Void>
	{
		@Override
		protected Void doInBackground(final String... params)
		{
			Log.v(TAG, "starting the UC");

			if (mUC != null)
			{
				/*
				 * If we get here, we've already logged in successfully, so
				 * simply set the network as reachable straight away.
				 */
				mUC.setNetworkReachable(true);
				mUC.setTrustManager(new TrustAllCerts());
				mUC.setHostnameVerifier(new NullHostNameVerifier());
				//Must be used in conjunction with setting a default CookieHandler prior to login
				mUC.setUseCookies(mUCUseCookies);
				mUC.setSupportsRenegotiation(mUCSupportsRenegotiation);
				mUC.startSession();

				mCallManager = mUC.getPhone();
				mCallManager.addListener(Main.this);
				mCallManager.addListener(mNotifyOnIncomingListener);
				
				// set preferred camera idx
				mCallManager.setCamera(preferredCameraIdx);
			}
			return null;
		}

		@Override
		protected void onPostExecute(final Void result)
		{
			if (mUC != null)
			{
				/*
				 * Set the title of the page to be the user's login name &
				 * number/address, or the application name if the user doesn't
				 * exist.
				 */
				makeCall("sip:3333@10.138.139.18");
				setTitle("mUser.getNameAndNumberTitle(Main.this)");
			}
		}
	}

	/** The static UC instance that we use throughout the application. */
	private static UC mUC;
	/** Boolean flag we use to keep track of whether the UC session is initialised. */
//	private static boolean mUCInitialized;
	/** Boolean flag we use to keep track of whether cookies should be enabled. */
	private static boolean mUCUseCookies;
	private static boolean mUCSupportsRenegotiation;

	private static Display mDisplay;
	
	/**
	 * This sample app assumes that there can be up to 2 cameras on the device (front and back).
	 * <p>
	 * If there are more cameras, you will need to change the code accordingly to select the appropriate camera
	 * for you device.
	 */
	private List<PhoneVideoCamera> mSupportedCameraSelections = 
	Arrays.asList(PhoneVideoCamera.FRONT_CAMERA, PhoneVideoCamera.BACK_CAMERA);
	
	/** The session key that's obtained during the login process. */
	private String mSessionKey;

	private String mLogoutURL;
	
	/** The camera the user wishes to use when making video calls. */
	private static int preferredCameraIdx;

    private MediaDirection preferredAudioDirection = MediaDirection.SEND_AND_RECEIVE;
    private MediaDirection preferredVideoDirection = MediaDirection.SEND_AND_RECEIVE;

	/** The manager that we use to make and receive phone calls. */
	protected static Phone mCallManager = null;
	/** A listener that generates a notification when an incoming call is received*/
	private final PhoneListener mNotifyOnIncomingListener = new PhoneListener(){
        @Override public void onCaptureSettingChange(PhoneVideoCaptureSetting setting, int camera) {}
        @Override public void onLocalMediaStream() {}

        @Override public void onIncomingCall(Call call) 
        {
            NotificationHelper.showIncomingCallNotification(getApplicationContext(), call.getRemoteAddress());
        }
	};

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Log.v(TAG, "onCreate " + savedInstanceState);

		// Check if we are re-creating a previously destroyed instance
		if (savedInstanceState != null)
		{
			Log.i(TAG, "We're re-creating a previous instance");

			mSessionKey = savedInstanceState.getString(DATA_SESSION_KEY);
			mLogoutURL = savedInstanceState.getString(DATA_LOGOUT_URL);
			mUCUseCookies  = savedInstanceState.getBoolean(DATA_UC_USE_COOKIES, false);
			mUCSupportsRenegotiation = savedInstanceState.getBoolean(DATA_UC_SUPPORTS_RENEG, true);

			if (mUC == null) 
			{
	            mUC = UCFactory.createUc(getApplicationContext(), mSessionKey, this);
			}
						
			preferredCameraIdx = savedInstanceState.getInt(DATA_SELECTED_CAMERA, getDefaultPreferredCameraIdxToUse());	
			
			if (mCallManager != null)
			{
				mCallManager.setCamera(preferredCameraIdx);
			}		
		}
		else
		{
			Bundle args = getIntent().getExtras();
			if (args != null)
			{
				mSessionKey = args.getString(DATA_SESSION_KEY);
				if (mSessionKey == null)
				{
					Log.w(TAG, "No session key passed to me, I cannot create a " +
							"session without one");
					finish();
					return;
				}

				mLogoutURL = args.getString(DATA_LOGOUT_URL);
				
				mUCUseCookies = args.getBoolean(DATA_UC_USE_COOKIES, false);
				mUCSupportsRenegotiation = args.getBoolean(DATA_UC_SUPPORTS_RENEG, true);
			}

			// Get UC from the factory
			mUC = UCFactory.createUc(getApplicationContext(), mSessionKey, this);
		}

		//Get the device Display object and what is the current rotation.
		if (mDisplay == null)
		{
			mDisplay = ((WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		}

		setContentView(R.layout.main);
		preferredCameraIdx = getDefaultPreferredCameraIdxToUse();
	}

	@Override
	protected void onStart()
	{
		super.onStart();

		if (mSessionKey != null)
		{
			new UCStartTask().execute();
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		Log.d(TAG, "onPause");

		removeUCListener(this);
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState)
	{
		super.onSaveInstanceState(outState);
	}


	/**
	 * Start the in-call Activity, passing it the phone number/address and whether we want video
	 * or not.
	 * TODO: move this into the DialerFragment.
	 *
	 * @param phoneNumber The phone number to call
	 * @return <code><b>false</b></code> if the UC is not initialised, stopping us making a
	 * 	call, or <code><b>true</b></code> if the UC is initialised and we've been able to start
	 * 	the in-call Activity.
	 */
	protected boolean makeCall(final String phoneNumber)
	{
		Intent inCall = new Intent(this, InCallActivity.class);
		Bundle data = new Bundle();
		data.putBoolean(InCallActivity.KEY_OUTGOING, true);
		data.putString(InCallActivity.KEY_ADDRESS, phoneNumber);
        data.putSerializable(InCallActivity.KEY_AUDIO_DIRECTION, preferredAudioDirection);
        data.putSerializable(InCallActivity.KEY_VIDEO_DIRECTION, preferredVideoDirection);
		inCall.putExtras(data);
		startActivity(inCall);

		return true;
	}

	public static Phone getPhoneManager()
	{
		return (mUC != null) ? mUC.getPhone() : null;
	}

	public static AED getAEDManager()
	{
		return (mUC != null) ? mUC.getAED() : null;
	}


	public static void removeUCListener(final UCListener listener)
	{
		if (mUC != null)
		{
			mUC.removeListener(listener);
		}
	}

	@Override
	public void onCallFailed(final Call call, final String message, CallStatus callStatus)
	{
		Log.w(TAG, "onCallFailed: " + message);
	}

	@Override
	public void onDialFailed(final Call call, final String message, CallStatus callStatus)
	{
		Log.w(TAG, "onDialFailed: " + message);
	}

	@Override
	public void onLocalMediaStream()
	{
		Log.v(TAG, "onLocalMediaStream");
	}

	@Override
	public void onMediaChangeRequested(final Call call, final boolean hasRemoteAudio,
			final boolean hasRemoteVideo)
	{
		Log.v(TAG, "onMediaChangeRequested: remote audio(" + hasRemoteAudio + ") remote video (" + hasRemoteVideo + ")");
	}

	@Override
	public void onRemoteDisplayNameChanged(final Call call, final String name)
	{
		Log.i(TAG, "onRemoteDisplayNameChanged: " + name);
	}

	@Override
	public void onRemoteMediaStream(final Call call)
	{
		Log.v(TAG, "onRemoteMediaStream");
	}

	@Override
	public void onStatusChanged(Call call, CallStatusInfo statusInfo) {
		
		Log.e(TAG, String.format("onStatusChanged call[%s] callStatus[%s], message[%s], reason[%s]", call, statusInfo.getCallStatus(), statusInfo.getMessage(), statusInfo.getReason()));
		
		onStatusChanged(call, statusInfo.getCallStatus());
	}
	
	@Override
	public void onStatusChanged(final Call call, final CallStatus status)
	{
	}

	@Override
	public void onIncomingCall(final Call call)
	{
		// The call will be answered in handleIncomingCall, which creates an InCallActivity.
		Log.v(TAG, "onIncomingCall");
		call.addListener(this);
	}

	@Override
	public void onConnectionLost()
	{
		Log.d(TAG, "onConnectionLost");

		this.runOnUiThread(new Runnable() {
			public void run() {
				Utils.logAndToast(Main.this, TAG, Log.WARN, "Connection to the server has been lost111");
			}
		});
		
		final List<? extends Call> calls = (mCallManager == null) ? null : mCallManager.getCurrentCalls();
		if ((calls != null) && !calls.isEmpty())
		{
			// Attempt to end the current call
			calls.get(0).end();
		}
	}

	@Override
	public void onSessionNotStarted()
	{
//		mUCInitialized = false;
		final String error = "Session initialization has failed.";
		Log.w(TAG, error);
		new AlertDialog.Builder(Main.this)
			.setTitle(android.R.string.dialog_alert_title)
			.setMessage(error)
			.setPositiveButton(android.R.string.ok, null)
			.create()
			.show();
	}

	@Override
	public void onSessionStarted()
	{
		Log.i(TAG, "Session Started");

//		mUCInitialized = true;
		if (mActiveCallsBarListener != null)
		{
			mActiveCallsBarListener.updateActiveCallsBarVisibility(null);
		}
	}

	@Override
	public void onSystemFailure()
	{
		Log.w(TAG, "onSystemFailure");
	}

	@Override
	public void onGenericError(final String error, final String reason)
	{
		final String errMsg = error + " because '" + reason + "'";
		Log.w(TAG, errMsg);
		new AlertDialog.Builder(Main.this)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(android.R.string.dialog_alert_title)
			.setMessage(errMsg)
			.setPositiveButton(android.R.string.ok, null)
			.create()
			.show();
	}

	public static Display getDisplay()
	{
		return mDisplay;
	}

    @Override
    public void onCaptureSettingChange(PhoneVideoCaptureSetting setting, int camera) 
    {
        //Do Nothing
    }

    @Override
    public void onConnectionReestablished() 
    {
        Log.d(TAG, "Connection reestablished!"); 
    }

    @Override
    public void onConnectionRetry(int attempt, long delay) 
    {
        Log.d(TAG, "Connection retry attempt " + attempt + ", in " + delay + "ms");     
    }

	@Override
	public void onInboundQualityChanged(Call call, int quality) 
	{
		Log.d("TAG", "Main onInboundQualityChanged " + quality);
	}
	
	/*
	 * Default which camera to use.
	 *  
	 * As per the android docs. if there is only 1 camera on the device, the idx is always n-1 where n is the number 
	 * of cameras.
	 * 
	 * If there is only 1 camera, the camera idx is always 0 whether it is a FRONT or BACK facing.
	 * 
	 * If there is more than 1 camera, we default to the first FRONT facing camera we can find. If we cannot find
	 * a FRONT facing camera, we default to idx 0.
	 */
	private int getDefaultPreferredCameraIdxToUse()
	{
		int preferredCameraIdx = 0;
		
		final int numberOfCameras = Camera.getNumberOfCameras();
		if (numberOfCameras == 1)
		{
			Log.d(TAG, "Only 1 camera on device, defaulting to idx 0");
		}
		else
		{
			Log.d(TAG, "More than 1 camera on device, defaulting to the first FRONT camera we can find...");

			boolean cameraFound = false;
	        for (int i = 0; i < numberOfCameras; i++)
	        {
	            final CameraInfo cameraInfo = new CameraInfo();	            
	            Camera.getCameraInfo(i, cameraInfo);
	            
	            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT)
	            {
	            	preferredCameraIdx = i;
	            	Log.d(TAG, "Using FRONT facing camera at idx: " + i);
	            	cameraFound = true;
	            	break;
	            }
	        }
	        
	        if (!cameraFound)
	        {
	        	Log.w(TAG, "Failed to find a FRONT facing camera - defaulting to idx 0");
	        }
		}
		
		return preferredCameraIdx;
	}

	@Override
    public void onRemoteHeld(Call arg0)
    {
        Log.d("TAG", "Main onRemoteHeld");
    }

    @Override
    public void onRemoteUnheld(Call arg0)
    {
        Log.d("TAG", "Main onRemoteUnheld");
    }
	
	public static void setPreferredCameraIdx(int cameraIdx) {
		preferredCameraIdx = cameraIdx; 
	}
}
