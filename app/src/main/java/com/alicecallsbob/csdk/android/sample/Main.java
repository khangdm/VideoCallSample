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

	/** Message handler message type for incoming calls. */
	protected static final int INCOMING = 0;
	/** Message handler message type for when the user has logged out. */
	protected static final int LOGGED_OUT = 1;
	/** Request code for resolution selected result */
	protected static final int RESOLUTION_REQUEST_CODE = 10;
	/** Request code for camera selected result */
	protected static final int CAMERA_REQUEST_CODE = 11;
    /** Request code for selected audio media direction */
    protected static final int AUDIO_DIRECTION_REQUEST_CODE = 12;
    /** Request code for selected VIDEO media direction */
    protected static final int VIDEO_DIRECTION_REQUEST_CODE = 13;

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
				setTitle("mUser.getNameAndNumberTitle(Main.this)");
			}
		}
	}

//	private static final class MsgHandler extends Handler
//	{
//		/**
//		 * Weak reference to the Main class, used to aid garbage collection.
//		 * See {@link http://stackoverflow.com/questions/11407943/this-handler-class-should-be-static-or-leaks-might-occur-incominghandler}
//		 * for more information about this.
//		 */
//		private final WeakReference<Main> wrParent;
//
//		/**
//		 * Constructor.
//		 *
//		 * @param activity The Main Activity instance.
//		 */
//		public MsgHandler(final Main activity)
//		{
//			wrParent = new WeakReference<Main>(activity);
//		}
//
//		@Override
//		public void handleMessage(final Message msg)
//		{
//			final Main activity = wrParent.get();
//
//			switch (msg.what)
//			{
//			case INCOMING:
//				handleIncomingCall(activity);
//				break;
//
//			case LOGGED_OUT:
//				if (!LoginActivity.isAlive())
//				{
//					activity.startActivity(new Intent(activity.getApplicationContext(),
//													  LoginActivity.class));
//				}
//				activity.finish();
//				break;
//			}
//		}
//
//		/**
//		 * Show a dialog offering the user the chance to answer/reject the incoming call.
//		 *
//		 * @param activity The Main Activity instance
//		 */
//		private void handleIncomingCall(final Main activity)
//		{
//			// An incoming call should ring on the speakerphone.
//			AudioManager am = (AudioManager) activity.getSystemService(AUDIO_SERVICE);
//			am.setSpeakerphoneOn(true);
//
//			// Get the call
//			final List<? extends Call> allCalls = mCallManager.getCurrentCalls();
//			final Call incomingCall = allCalls.get(allCalls.size() - 1);
//			final String incomingCallId  = incomingCall.getCallId();
//
//			// Create a dialog to notify the user of a new call
//			final DialogFragment incomingCallFragment = new IncomingCallFragment(
//			        wrParent.get().getPreferredAudioDirection(),
//			        wrParent.get().getPreferredVideoDirection());
//
//			// Pass the Id of the call to the fragment so that it can retrieve the call in order to cancel/accept
//			final Bundle args = new Bundle();
//			args.putString(CALL_ID_ARG, incomingCallId);
//			incomingCallFragment.setArguments(args);
//
//			// Show the dialog fragment. By using a fragment we don't need to deal with the dialog being
//			// dismissed and re-shown when the device orientation changes.
//			incomingCallFragment.show(activity.getFragmentManager(), incomingCallId);
//		}
//	};
//
	private class ConnectionStateMonitor extends BroadcastReceiver
	{
	    @Override
	    public void onReceive(Context context, Intent intent)
	    {
	        final ConnectivityManager connectivityManager =
	                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

	        if (connectivityManager == null)
	        {
	            Log.w(TAG, "Connectivity manager unavailable");
	            if (mUC != null)
	            {
	                mUC.setNetworkReachable(false);
	            }
	        }

	        final NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	        if (activeNetworkInfo != null)
	        {
	            final boolean reachable = activeNetworkInfo.isConnected();
	            final String networkType = activeNetworkInfo.getTypeName();

	            if (reachable)
	            {
	                Utils.logAndToast(Main.this, TAG, Log.INFO, "Network reachable: " + networkType + " " + activeNetworkInfo);
	            }
	            else
	            {
	            		Utils.logAndToast(Main.this, TAG, Log.INFO, "Network not reachable: " + networkType  + " " + activeNetworkInfo);
	            }

	            if (mUC != null)
                {
                    mUC.setNetworkReachable(reachable);
                }
	        }
	        else
	        {
	            Utils.logAndToast(Main.this, TAG, Log.INFO, "Network not reachable: null");
	            if (mUC != null)
                {
                    mUC.setNetworkReachable(false);
                }
	        }
	    }
	}

	/** The static UC instance that we use throughout the application. */
	private static UC mUC;
	/** Boolean flag we use to keep track of whether the UC session is initialised. */
	private static boolean mUCInitialized;
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

	/** The user's details. */
	private User mUser;

	/** Message handler used to pass messages from background threads to the UI thread. */
//	private MsgHandler mMsgHandler;

	/** Device connection state monitor; will notify UC when network connectivity lost */
	private final ConnectionStateMonitor connectionStateMonitor = new ConnectionStateMonitor();
	
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
        registerReceiver(connectionStateMonitor, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

		// Check if we are re-creating a previously destroyed instance
		if (savedInstanceState != null)
		{
			Log.i(TAG, "We're re-creating a previous instance");

			mSessionKey = savedInstanceState.getString(DATA_SESSION_KEY);
			mLogoutURL = savedInstanceState.getString(DATA_LOGOUT_URL);
			mUCInitialized = savedInstanceState.getBoolean(DATA_UC_INITIALIZED, false);
			mUCUseCookies  = savedInstanceState.getBoolean(DATA_UC_USE_COOKIES, false);
			mUCSupportsRenegotiation = savedInstanceState.getBoolean(DATA_UC_SUPPORTS_RENEG, true);
			mUser = User.restoreState(savedInstanceState);

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
			mUCInitialized = false; // reset before initial initialisation

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

		FragmentTabHost tabHost = (FragmentTabHost)findViewById(android.R.id.tabhost);
		tabHost.setup(this, getSupportFragmentManager(), R.id.realtabcontent);

		// Dialer/Calls tab
		final String dialerTitle = getString(R.string.dialer_title);
		tabHost.addTab(tabHost.newTabSpec(dialerTitle).setIndicator(dialerTitle),
					   DialerFragment.class, null);

		// AED tab
		final String aedTitle = getString(R.string.aed_title);
		tabHost.addTab(tabHost.newTabSpec(aedTitle).setIndicator(aedTitle),
					   AEDFragment.class, null);

//		mMsgHandler = new MsgHandler(this);

		mUser = User.getInstance();

		// Default which camera to use.
		preferredCameraIdx = getDefaultPreferredCameraIdxToUse();
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		Log.v(TAG, "onStart " + mSessionKey + " - " + mUCInitialized);

		if (mSessionKey != null)
		{
			if (!mUCInitialized)
			{
				new UCStartTask().execute();
			}
			else
			{
				addUCListener(this);

				if (mCallManager != null)
				{
					mCallManager.addListener(this);
				}
			}
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		if (mUC != null)
		{
			setTitle("mUser.getNameAndNumberTitle(Main.this)");
		}
		
		// If we've got active calls, set this as their listeners
		if (mCallManager != null)
		{
			List<? extends Call> calls = mCallManager.getCurrentCalls();
			for (Iterator<? extends Call> callIt = calls.iterator(); callIt.hasNext(); )
			{
				Call call = callIt.next();
				call.addListener(this);
			}
		}
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
		Log.d(TAG, "Save Instance State");

		outState.putString(DATA_SESSION_KEY, mSessionKey);
		outState.putString(DATA_LOGOUT_URL, mLogoutURL);
		outState.putBoolean(DATA_UC_INITIALIZED, isUCInitialized());
		outState.putBoolean(DATA_UC_USE_COOKIES, mUCUseCookies);
		if (mUser != null)
		{
			mUser.saveState(outState);
		}
		
		outState.putInt(DATA_SELECTED_CAMERA, preferredCameraIdx);
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		Log.d(TAG, "onStop");

		if (mCallManager != null)
		{
			mCallManager.removeListener(this);
		}
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		final boolean changingConfig = isChangingConfigurations();
		Log.v(TAG, "onDestroy " + changingConfig);
		unregisterReceiver(connectionStateMonitor);

		if (!changingConfig)
		{
			User.destroy();

			if (mUC != null)
			{
				mUC.stopSession();
			}

			mUC = null;
			mDisplay = null;
		}
	}

	@Override
	public void onBackPressed()
	{
		/*
		 * The user should not be pressing Back to exit this screen, they should press Home or
		 * the Logout menu option. So in here, we show them a warning dialog and ask if they
		 * want to logout and go Back or stay here in the dialer.
		 */
		new AlertDialog.Builder(this)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(android.R.string.dialog_alert_title)
			.setMessage(R.string.logout_dialog_message)
			.setPositiveButton(R.string.menu_logout, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						logout();
					}
				})
			.setNegativeButton(android.R.string.cancel, null)
			.create()
			.show();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		
		final int numberOfCameras = Camera.getNumberOfCameras();
		if (numberOfCameras > 1)
		{	    			
			Log.d(TAG, "More than 1 camera on device - displaying camera selection menu");
		}
		else
		{
			Log.d(TAG, "Only 1 camera on device - NOT displaying camera selection menu");
			menu.removeItem(R.id.menu_camera);
		}
		
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
	    switch (item.getItemId())
	    {
	        case R.id.menu_logout:
	            logout();
	            return true;
	        case R.id.menu_about:
	            about();
	            return true;
	        case R.id.menu_resolution:
	            selectResolution();
	            return true;
	        case R.id.menu_camera:	        	
	            selectCamera();
	    			return true;
            case R.id.menu_audio_direction:
                selectAudioDirection();
                return true;
            case R.id.menu_video_direction:
                selectVideoDirection();
                return true;
	        default:
	            return super.onOptionsItemSelected(item);
	        
	    }
	}

	public static boolean isUCInitialized()
	{
		return mUCInitialized;
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

	public static boolean isLargeScreenDevice(Context context) 
	{
	    int screenLayout = context.getResources().getConfiguration().screenLayout;
	    screenLayout &= Configuration.SCREENLAYOUT_SIZE_MASK;

	    switch (screenLayout) 
	    {
		    case Configuration.SCREENLAYOUT_SIZE_LARGE:
		    case Configuration.SCREENLAYOUT_SIZE_XLARGE:
		        return true;
	    }
	    
	    return false;
    }
	
	protected void logout()
	{
		for (Call c : mUC.getPhone().getCurrentCalls()) 
		{
			if (c.getCallStatus() == CallStatus.ALERTING) 
			{
				Log.w(TAG, "Automatically removing notification for call with " + c.getRemoteDisplayName() + " before logging out.");
				NotificationHelper.removeIncomingCallNotification(getApplicationContext());
			}

			Log.w(TAG, "Automatically ending call with " + c.getRemoteDisplayName() + " before logging out.");
			c.end();			
		}
		
		LoginHandler.logout(mLogoutURL);
//		mMsgHandler.sendEmptyMessage(LOGGED_OUT);
	}
	
    /**
     * Start the about activity
     */
    protected void about()
    {
        final Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }
    
    /**
     * Start the resolution selection activity
     */
    protected void selectResolution()
    {
        final PhoneVideoCaptureResolution currentResolution = 
                mCallManager.getPreferredCaptureResolution();
        final Intent intent = new Intent(this, ResolutionActivity.class);
        intent.putExtra(DATA_RECOMMENDED_SETTINGS, 
                (Serializable)mCallManager.getRecommendedCaptureSettings());
        intent.putExtra(DATA_SELECTED_RESOLUTION, currentResolution);
        startActivityForResult(intent, RESOLUTION_REQUEST_CODE);
    }
    
    protected void selectCamera()
    {            	
        final int currentCameraIdx = preferredCameraIdx;
        final Intent intent = new Intent(this, CameraSelectionActivity.class);        
        intent.putExtra(DATA_RECOMMENDED_SETTINGS, (Serializable) mSupportedCameraSelections);        
        intent.putExtra(DATA_SELECTED_CAMERA, currentCameraIdx);        
        startActivityForResult(intent, CAMERA_REQUEST_CODE);
    }

    
    protected void selectAudioDirection()
    {
        final Intent intent = new Intent(this, SelectMediaDirectionActivity.class);  
        intent.putExtra(DATA_SELECTED_MEDIA_DIRECTION, preferredAudioDirection);
        startActivityForResult(intent, AUDIO_DIRECTION_REQUEST_CODE);
    }

    protected void selectVideoDirection()
    {
        final Intent intent = new Intent(this, SelectMediaDirectionActivity.class);  
        intent.putExtra(DATA_SELECTED_MEDIA_DIRECTION, preferredVideoDirection);
        startActivityForResult(intent, VIDEO_DIRECTION_REQUEST_CODE);
    }

	public static Phone getPhoneManager()
	{
		return (mUC != null) ? mUC.getPhone() : null;
	}

	public static AED getAEDManager()
	{
		return (mUC != null) ? mUC.getAED() : null;
	}

	public static void addUCListener(final UCListener listener)
	{
		if (mUC != null)
		{
			mUC.addListener(listener);
		}
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
		Log.v(TAG, "onStatusChanged - new status: " + status.name());

		if (status == CallStatus.ENDED)
		{
			Log.v(TAG, "call ended");

			final Fragment incomingCallFragment = getFragmentManager().findFragmentByTag(call.getCallId());

			if (incomingCallFragment != null)
			{
				// An incoming call has ended before we've answered it.
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						((DialogFragment)incomingCallFragment).dismissAllowingStateLoss();
						NotificationHelper.removeIncomingCallNotification(getApplicationContext());
					}
				});
			}

			call.removeListener(this);

			if (mActiveCallsBarListener != null)
			{
				mActiveCallsBarListener.updateActiveCallsBarVisibility(call);
			}
		}
	}

	@Override
	public void onIncomingCall(final Call call)
	{
		// The call will be answered in handleIncomingCall, which creates an InCallActivity.
		Log.v(TAG, "onIncomingCall");
		call.addListener(this);
//		mMsgHandler.sendEmptyMessage(INCOMING);
	}

	@Override
	public void onConnectionLost()
	{
		Log.d(TAG, "onConnectionLost");

		this.runOnUiThread(new Runnable() {
			public void run() {
				Utils.logAndToast(Main.this, TAG, Log.WARN, "Connection to the server has been lost");
			}
		});
		
		final List<? extends Call> calls = (mCallManager == null) ? null : mCallManager.getCurrentCalls();
		if ((calls != null) && !calls.isEmpty())
		{
			// Attempt to end the current call
			calls.get(0).end();
		}
        logout();
	}

	@Override
	public void onSessionNotStarted()
	{
		mUCInitialized = false;
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
		
		mUCInitialized = true;
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
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
	    if (resultCode == RESULT_OK)
	    {
	    	    switch(requestCode)
	    	    {
	    	        case RESOLUTION_REQUEST_CODE:
	    	            setResolutionFromResult(data);
	    	            break;
	    	            
	    	        case CAMERA_REQUEST_CODE:
	    	        		setCameraFromResult(data);
	    	            break;
	    	            
	            case AUDIO_DIRECTION_REQUEST_CODE:
	                setPreferredAudioDirectionFromResult(data);
	                break;
	                
	            case VIDEO_DIRECTION_REQUEST_CODE:
	                setPreferredVideoDirectionFromResult(data);
	                break;
	                    
	    	        default:
	    	        		Log.w(TAG, "Unrecognised requestCode receieved in onActivityResult callback. RequestCode: "
	    	                + requestCode);    	        	
	    	    }
	    }
	}
	
    private void setPreferredAudioDirectionFromResult(Intent data) 
    {
        final MediaDirection dir = (MediaDirection) data.getExtras().get(DATA_SELECTED_MEDIA_DIRECTION);
        preferredAudioDirection = dir;
        logAndToastMediaDirectionChange("audio", dir);
    }
    
    private void setPreferredVideoDirectionFromResult(Intent data) 
    {
        final MediaDirection dir = (MediaDirection) data.getExtras().get(DATA_SELECTED_MEDIA_DIRECTION);
        preferredVideoDirection = dir;
        logAndToastMediaDirectionChange("video", dir);
    }
    
    private void logAndToastMediaDirectionChange(String audioOrVideo, MediaDirection dir)
    {
        switch (dir)
        {
            case SEND_AND_RECEIVE:  Utils.logAndToast(Main.this, TAG, Log.INFO, "Send and receive " + audioOrVideo); break;
            case SEND_ONLY:         Utils.logAndToast(Main.this, TAG, Log.INFO, "Only send " + audioOrVideo); break;
            case RECEIVE_ONLY:      Utils.logAndToast(Main.this, TAG, Log.INFO, "Only receive " + audioOrVideo); break;
            case NONE:              Utils.logAndToast(Main.this, TAG, Log.INFO, "No " + audioOrVideo); break;
            default:                Utils.logAndToast(Main.this, TAG, Log.WARN, "Unrecognised selection"); break;
        }
    }    

	private void setResolutionFromResult(Intent data)
	{
	    final PhoneVideoCaptureResolution resolution = 
	            (PhoneVideoCaptureResolution)data.getExtras().get(DATA_SELECTED_RESOLUTION);
	    
	    mCallManager.setPreferredCaptureResolution(resolution);
	    
	    Utils.logAndToast(Main.this, TAG, Log.INFO, "Resolution set to: " + resolution);
	}
	
	private void setCameraFromResult(Intent data)
	{
	    final PhoneVideoCamera preferredCamera = (PhoneVideoCamera) data.getExtras().get(DATA_SELECTED_CAMERA);
	    	    	    
	    Log.d(TAG, "User selected preferred camera: " + preferredCamera);	    
	    
	    // We look for the first camera idx with preferred camera direction...
        for (int i = 0; i < Camera.getNumberOfCameras(); i++)
        {
            CameraInfo cameraInfo = new CameraInfo();            
            Camera.getCameraInfo(i, cameraInfo);
            
            if (cameraInfo.facing == preferredCamera.getCameraFacingDirection())
            {
	            	// store state for the app
	            	preferredCameraIdx = i;
	            	Log.d(TAG, "Found first camera facing direction user selected at idx: " + i);
	            	break;
            }
        }
               
        // inform FCSDK of camera selection for next call.
	    mCallManager.setCamera(preferredCameraIdx);
	 	    
        // Confirm to user what's been selected
        if (preferredCamera.getCameraFacingDirection() == CameraInfo.CAMERA_FACING_FRONT)
        {
            Utils.logAndToast(Main.this, TAG, Log.INFO, "Selected Front Camera - faces same direction as screen.");
        }
        else
        {
        		Utils.logAndToast(Main.this, TAG, Log.INFO, "Selected Back Camera - faces opposite direction to screen.");
        }
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

    private MediaDirection getPreferredAudioDirection() 
    {
        return preferredAudioDirection;
    }

    private MediaDirection getPreferredVideoDirection() 
    {
        return preferredVideoDirection;
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
