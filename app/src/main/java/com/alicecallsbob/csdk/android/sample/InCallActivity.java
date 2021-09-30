package com.alicecallsbob.csdk.android.sample;

import java.lang.ref.WeakReference;
import java.util.Set;

import com.alicecallsbob.fcsdk.android.phone.AudioDeviceManager;
import com.alicecallsbob.fcsdk.android.phone.AudioDeviceManager.AudioDevice;
import com.alicecallsbob.fcsdk.android.phone.AudioDeviceManagerListener;
import com.alicecallsbob.fcsdk.android.phone.Call;
import com.alicecallsbob.fcsdk.android.phone.CallCreationWithErrorException;
import com.alicecallsbob.fcsdk.android.phone.CallListener;
import com.alicecallsbob.fcsdk.android.phone.CallStatus;
import com.alicecallsbob.fcsdk.android.phone.CallStatusInfo;
import com.alicecallsbob.fcsdk.android.phone.MediaDirection;
import com.alicecallsbob.fcsdk.android.phone.Phone;
import com.alicecallsbob.fcsdk.android.phone.PhoneListener;
import com.alicecallsbob.fcsdk.android.phone.PhoneVideoCaptureSetting;
import com.alicecallsbob.fcsdk.android.phone.VideoSurface;
import com.alicecallsbob.fcsdk.android.phone.VideoSurface.Endpoint;
import com.alicecallsbob.fcsdk.android.phone.VideoSurfaceListener;
import com.alicecallsbob.fcsdk.android.uc.UCListener;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import android.util.Rational;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity that is used to display and handle one or more active calls,
 * possibly including video.
 */
@SuppressWarnings("deprecation")
public final class InCallActivity extends Activity implements PhoneListener,
        CallListener, UCListener, VideoSurfaceListener, AudioDeviceManagerListener {
    /** Identifier String for LogCat output. */
    private static final String TAG = "InCallActivity";

    protected static final int MSG_ERROR = -1;
    protected static final int MSG_INCOMING = 0;

    public static final String KEY_ADDRESS = "_address";
    public static final String KEY_OUTGOING = "_outgoing_call";
    public static final String KEY_AUDIO_DIRECTION = "_audio_direction";
    public static final String KEY_VIDEO_DIRECTION = "_video_direction";

    private static final String SAVED_STATE_KEY_AUDIO_MUTED = "_audio_muted";
    private static final String SAVED_STATE_KEY_VIDEO_MUTED = "_video_muted";
    private static final String SAVED_STATE_KEY_FRONT_CAMERA_ACTIVE = "_front_camera_active";
    private static final String SAVED_STATE_KEY_AUDIO_DEVICE = "_audio_device";
    private static final String SAVED_STATE_KEY_KEYPAD_VISIBLE = "_keypad_visible";
    private static final String SAVED_STATE_KEY_AUDIO_DIRECTION = "_state_audio_direction";
    private static final String SAVED_STATE_KEY_VIDEO_DIRECTION = "_state_video_direction";
    private static final String SAVED_STATE_KEY_REMOTE_VIDEO = "_remote_video";

    private static final int MAXIMUM_CALLS = 2;
    
    /** 
        How many times smaller the preview window should be, compared to the 
        video container size. 
    */
    private static final int PREVIEW_WINDOW_SIZE_RATIO = 4;

    /**
     *
     */
    private static final class MsgHandler extends Handler {
        private final WeakReference<InCallActivity> wrParent;

        public MsgHandler(final InCallActivity parent) {
            wrParent = new WeakReference<InCallActivity>(parent);
        }

        @Override
        public void handleMessage(final Message msg) {
            Log.d(TAG, "handleMessage: " + msg);
            InCallActivity parent = wrParent.get();

            switch (msg.what) {
            case MSG_ERROR:
                Utils.logAndToast(parent, TAG, Log.ERROR, (String) msg.obj);
                break;

            case MSG_INCOMING:
                Toast.makeText(parent, R.string.incoming_title,
                        Toast.LENGTH_SHORT).show();
                break;
            }
        }
    }

    /**
     * Asynchronous task that creates a new outgoing call on a background thread
     * and updates the UI components on the UI thread when it's done.
     */
    private class CreateOutgoingCallTask extends AsyncTask<Void, Void, Call> {

        @Override
        protected Call doInBackground(Void... params) {
            // The preview and view VideoSurfaces should have been created by
            // the time the call
            // is actually created.           

            Log.d("CreateOutgoingCallTask",
                    "Setting preview and video view for call " + mPreviewView);

            Log.v(TAG, "Initiate an outgoing call");

            // Note that this call is being created on a worker thread and not the UI thread. Care must be taken
            // in other parts of the sample when accessing the mCurrentCall field as it may have not been
            // initialised e.g. if you call muteVideo immediately you initiate a call mCurrentCall may still be
            // null and that mute operation will not be actioned. Application developers should therefore
            // actively consider when to present/enable UI elements that act upon a Call object. One strategy
            // would be to enable in-call options such as mute/hold etc. after the call has been established
            // i.e. onStatusChanged called with Callstatus.IN_CALL.

            Call createdCall = null;
            try
            {
                createdCall = mCallManager.createCall(mCalleeAddress, mAudioDirection, mVideoDirection, InCallActivity.this);
            }
            catch (CallCreationWithErrorException e)
            {
                Log.e(TAG, "Unable to create call", e);
                mMsgHandler.obtainMessage(MSG_ERROR,
                        "Unable to create new call - " + e.getCallCreationError().name())
                        .sendToTarget();
                finish();
            }
            updateCallsList();
            return createdCall;
        }

        @Override
        protected void onPostExecute(Call createdCall) {
            Log.d(TAG, "onPostExecute " + createdCall);
            if (createdCall == null) {
                // outgoing call failed
                return;
            }

            // Update the selected index to point at the new call
            selectCall(createdCall);

            /*
             * When this sample app is first started and the first outbound call is made, there is a race between the
             * setting of the active call (done by this task) and the InCallManager::VideoSurfaceListenerWrapper::onSurfaceRenderingStarted
             * callback for adding the VideoSurface.
             *
             * If we get the video surface rendered callback happens first, we assign the video surface here.
             */
            if (!mInCallManager.hasVideoBeenAssignedToCurrentCall())
            {
                Log.d(TAG, "CreateOutgoingCallTaskVideo::onPostExecute() - Video Surface has not been assigned to call yet - doing it now.");

                final VideoSurface selectedCallVideoSurface = mInCallManager.getVideoSurfaceForSelectedCall();
                mInCallManager.assignVideoSurfaceToCall(selectedCallVideoSurface);
            }
            else
            {
                Log.d(TAG, "CreateOutgoingCallTaskVideo::onPostExecute() - Video Surface has already been assigned to call.");
            }

            populateViews();
        }
    }

    /**
     * Picture in Picture button listener.
     */
    private final View.OnClickListener mPipButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                pipToggle(toggleButton(v), true);
            }
            else
            {
                Utils.logAndToast(getApplicationContext(), TAG, Log.INFO,
                        "Picture in Picture unavailable due to low resources or Android version.");
            }
        }
    };

    /**
     * Mute audio button listener.
     */
    private final View.OnClickListener mMuteButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            muteAudio(toggleButton(v), true);
        }
    };
    /**
     * Mute video button listener.
     */
    private final View.OnClickListener mMuteVideoButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            muteVideo(toggleButton(v), true);
        }
    };

    private final View.OnClickListener mHoldButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            putOnHold(toggleButton(v), true);

        }
    };

	/**
	 * Camera button listener.
	 */
	private final View.OnClickListener mCameraButtonClickListener = new View.OnClickListener() {
		@Override
		public void onClick(final View v) {

			Log.d(TAG, "Camera toggle button clicked");
			toggleCamera(toggleButton(v), true);
		}
	};

    /**
     * Torch button listener.
     */
    private final View.OnClickListener mTorchButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {

            Log.d(TAG, "Torch toggle button clicked");
            toggleTorch(toggleButton(v), true);
        }
    };

    /**
     * DTMF keypad button listener.
     */
    private final View.OnClickListener mKeypadButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            showDTMFKeypad(toggleButton(v));
        }
    };

    /**
     * Click listener for the 'Add call' thumbnail.
     */
    private final View.OnClickListener mOnAddCallClicked = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            if (mInCallManager.getNumberOfActiveCalls() < MAXIMUM_CALLS)
            {
                mAddCallClicked = true;
                finish();
            }
            else
            {
                Utils.logAndToast(getApplicationContext(), TAG, Log.INFO,
                        "A maximum of 2 concurrent calls is supported in this sample application");
            }
        }
    };

    /**
     * click listener for the 'end call' button.
     */
    private final View.OnClickListener mOnEndCallClicked = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            Log.d(TAG, "End call button clicked");
            endCall();
        }
    };

    /**
     * click listener for the 'answer call' button.
     */
    private final View.OnClickListener mOnAnswerCallClicked = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            answerCall();
        }
    };

    private MsgHandler mMsgHandler;

    private Phone mCallManager;
    private InCallManager mInCallManager;

    private LinearLayout mDetailsContainer;
    private TextView mStatusView;
    protected TextView mRemoteNameView;
    private RelativeLayout mVideoContainer;
    private VideoSurface mPreviewView;
    private int mDisplayRotation;
    private TableLayout mDTMFKeypad;
    private TextView mDTMFDigitsView;
    private View mPipButton;
    private View mAudioMuteButton;
    private View mVideoMuteButton;
    private View mHoldButton;
    private View mCameraButton;
    private View mTorchButton;
    private View mSpeakerButton;
    private View mBluetoothButton;
    private View mDTMFKeypadButton;
    private View mAnswerCallButton;
    private View mRemoteHeldIcon;
    /** Horizontal list containing items for all the active calls. */
    private LinearLayout mCallThumbnails;

    private boolean mCallOutgoing;
    private String mCalleeAddress;
    private MediaDirection mAudioDirection;
    private MediaDirection mVideoDirection;
    private boolean mRemoteVideo;
    private boolean mIsPictureInPicture;
    private boolean mIsAudioMuted;
    private boolean mIsVideoMuted;
    private boolean mIsSpeakerActive;
    private boolean mIsBluetoothActive;
    private boolean mIsFrontCameraActive;
    private boolean mIsKeypadVisible;

    /** Is this the initial creation of this activity? */
    private boolean mInitial = false;
    protected boolean mAddCallClicked;

    private static InCallActivity INSTANCE = null;

	private boolean mNeedToSetupVideoWhenCallEstablished;

	private BluetoothAdapter mBluetoothAdapter;

	private final int MENU_EARPIECE = Menu.FIRST;
	private final int MENU_SPEAKER = Menu.FIRST + 1;
	private final int MENU_WIRED = Menu.FIRST + 2;
	private final int MENU_BLUETOOTH = Menu.FIRST + 3;

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu)
    {
        Log.d(TAG, "onPrepareOptionsMenu");
        menu.clear();
        final Set<AudioDevice> devices = mCallManager.getAudioDeviceManager().getAudioDevices();
        final AudioDevice selectedDevice = mCallManager.getAudioDeviceManager().getSelectedAudioDevice();
        if (devices.contains(AudioDevice.EARPIECE))
        {
            selectMenuItem(menu.add(0, MENU_EARPIECE, Menu.NONE, R.string.earpiece), AudioDevice.EARPIECE, selectedDevice);
        }
        if (devices.contains(AudioDevice.SPEAKER_PHONE))
        {
            selectMenuItem(menu.add(0, MENU_SPEAKER, Menu.NONE, R.string.speaker), AudioDevice.SPEAKER_PHONE, selectedDevice);
        }
        if (devices.contains(AudioDevice.BLUETOOTH))
        {
            selectMenuItem(menu.add(0, MENU_BLUETOOTH, Menu.NONE, R.string.bluetooth), AudioDevice.BLUETOOTH, selectedDevice);
        }
        if (devices.contains(AudioDevice.WIRED_HEADSET))
        {
            selectMenuItem(menu.add(0, MENU_WIRED, Menu.NONE, R.string.wired), AudioDevice.WIRED_HEADSET, selectedDevice);
        }
        return super.onPrepareOptionsMenu(menu);
    }

	private void selectMenuItem(MenuItem item, AudioDevice device, AudioDevice selectedDevice)
	{
	    item.setCheckable(true);
	    if (device == selectedDevice)
	    {
            item.setChecked(true);
        }
	}

	@Override
    public boolean onOptionsItemSelected(final MenuItem item)
    {
        Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId())
        {
            case MENU_EARPIECE:
                mCallManager.getAudioDeviceManager().setAudioDevice(AudioDevice.EARPIECE);
                return true;
            case MENU_SPEAKER:
                mCallManager.getAudioDeviceManager().setAudioDevice(AudioDevice.SPEAKER_PHONE);
                return true;
            case MENU_WIRED:
                mCallManager.getAudioDeviceManager().setAudioDevice(AudioDevice.WIRED_HEADSET);
                return true;
            case MENU_BLUETOOTH:
                mCallManager.getAudioDeviceManager().setAudioDevice(AudioDevice.BLUETOOTH);
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        Log.d(TAG, "onNewIntent() " + intent);
        super.onNewIntent(intent);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.d(TAG, "onCreate() " + INSTANCE);
        super.onCreate(savedInstanceState);

        if (INSTANCE != null) {
            Log.e(TAG, "InCallActivity already exists, we shouldn't be "
                    + "creating it again.");
            finish();
            return;
        }

        INSTANCE = this;

        mInCallManager = new InCallManager(this, savedInstanceState);

        mInitial = true;

        mIsAudioMuted = false;
        mIsVideoMuted = false;
        mIsSpeakerActive = false;
        mIsBluetoothActive = false;
        mIsKeypadVisible = false;
        mCallOutgoing = false;

//        Main.addUCListener(this);

        mCallManager = Main.getPhoneManager();
        mCallManager.addListener(this);

        mCallManager.getAudioDeviceManager().addListener(this);

        //Set the fallback device if wired headset unplugged or bluetooth disconnects.
        mCallManager.getAudioDeviceManager().setDefaultAudioDevice(AudioDevice.SPEAKER_PHONE);

        startLocalCamera();

        updateCallsList();

        final Bundle data = getIntent().getExtras();
        if ((data != null) && (savedInstanceState == null)) {
        	// Then a new call is starting.
            mCallOutgoing = data.getBoolean(KEY_OUTGOING);
            mCalleeAddress = data.getString(KEY_ADDRESS);
            mAudioDirection = (MediaDirection) data.getSerializable(KEY_AUDIO_DIRECTION);
            mVideoDirection = (MediaDirection) data.getSerializable(KEY_VIDEO_DIRECTION);
            mInitial = true;
            /*
             * We assume that remote video will only be present if we are sending it.
             * The assumption is disregarded if necessary when the call state becomes IN_CALL.
             */
            mRemoteVideo = mVideoDirection.isSending();

            if (mCallOutgoing) {
                mInCallManager.selectCall(null);
            }

            Log.d(TAG, "outgoing:" + mCallOutgoing + ", address:" + mCalleeAddress);
        } else if (mInCallManager.getCurrentCall() != null) {
        	// Then a call is already in existence (but we may not be IN_CALL yet).
            if (savedInstanceState != null)
            {
                mRemoteVideo = savedInstanceState.getBoolean(SAVED_STATE_KEY_REMOTE_VIDEO);
                mAudioDirection = (MediaDirection) savedInstanceState.getSerializable(SAVED_STATE_KEY_AUDIO_DIRECTION);
                mVideoDirection = (MediaDirection) savedInstanceState.getSerializable(SAVED_STATE_KEY_VIDEO_DIRECTION);
            }
            else
            {
                mRemoteVideo = mInCallManager.getCurrentCall().hasRemoteVideo();
            }
            mCalleeAddress = mInCallManager.getCurrentCall().getRemoteAddress();
        } else {
            /*
             * ERROR. When we start this Activity, we should either have an
             * existing Call, or we should have some Intent data telling us to
             * create a new outgoing call. If we have neither, we shouldn't be
             * in this Activity, so we can finish it now.
             */
            Log.w(TAG,
                    "We have neither an existing Call, or some Intent data for"
                            + " a new Call, so we shouldn't be in here and will leave right"
                            + " now");
            finish();
        }

        if (isInPictureInPictureMode())
        {
            setContentView(R.layout.activity_incall_pip);
        }
        else {
            setContentView(R.layout.activity_incall);
        }

        // This call originated remotely and has not been answered yet
        if (mInCallManager.getCurrentCall() != null
                && mInCallManager.getCurrentCall().getCallStatus() == CallStatus.ALERTING
                && mCallOutgoing == false) {
        	Log.d(TAG, "Incoming call at ALERTING state");
            toggleViewForIncomingUnansweredCall(true);
        }

        /*
         * Tell the system that the hardware volume keys should control the call
         * stream volume
         */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        mMsgHandler = new MsgHandler(this);

        /*
         * Set up the UI components...
         */

        // Call details view
        mDetailsContainer = (LinearLayout) findViewById(R.id.callDetailsLayout);

        // Call status
        mStatusView = ((TextView) findViewById(R.id.labelStatus));

        // Remote name
        mRemoteNameView = ((TextView) findViewById(R.id.labelRemoteName));

        // Picture in Picture button
        mPipButton = findViewById(R.id.switchPiP);
        mPipButton.setOnClickListener(mPipButtonClickListener);

        // Mute audio button
        mAudioMuteButton = findViewById(R.id.switchMute);
        mAudioMuteButton.setOnClickListener(mMuteButtonClickListener);

        // Mute video button
        mVideoMuteButton = findViewById(R.id.switchMuteVideo);
        mVideoMuteButton.setOnClickListener(mMuteVideoButtonClickListener);

        mHoldButton = findViewById(R.id.switchHold);
        mHoldButton.setOnClickListener(mHoldButtonClickListener);

        mCameraButton = findViewById(R.id.switchCamera);
        mCameraButton.setOnClickListener(mCameraButtonClickListener);
        if (isFacingBack(mCallManager.getCamera())) {
            toggleButton(mCameraButton);
            mIsFrontCameraActive = false;
        }

        mTorchButton = findViewById(R.id.switchTorch);
        mTorchButton.setOnClickListener(mTorchButtonClickListener);

        // Only display the toggle camera button if there is more than 1 camera - this sample app assumes
        // there is a front and back facing camera if there is.
		final int numberOfCameras = Camera.getNumberOfCameras();
		if (numberOfCameras <= 1)
		{
			Log.d(TAG, "There is not > 1 camera on device - disabling camera toggle button");
			mCameraButton.setEnabled(false);
			mCameraButton.setVisibility(View.INVISIBLE);
		}

        // Speaker button
        // An application should ideally detect the speaker/earpiece capabilities of the device and use
        // that to determine whether to provide a speakerphone button. Unfortunately Android does not
        // support this. The following article explains the problem and suggests some workarounds:
        // http://stackoverflow.com/questions/6943068/detect-lack-of-earpiece-speakerphone-only-on-an-android-device.
        mSpeakerButton = findViewById(R.id.switchSpeaker);

        mBluetoothButton = findViewById(R.id.switchBluetooth);

        // DTMF keypad button
        mDTMFKeypadButton = findViewById(R.id.switchDTMF);
        mDTMFKeypadButton.setOnClickListener(mKeypadButtonClickListener);

        // Answer call button
        mAnswerCallButton = findViewById(R.id.btnAnswerCall);
        mAnswerCallButton.setOnClickListener(mOnAnswerCallClicked);

        // DTMF keypad
        mDTMFKeypad = (TableLayout) findViewById(R.id.dtmfKeypad);
        mDTMFDigitsView = (TextView) findViewById(R.id.dtmfDigits);

        // 'End call' button
        findViewById(R.id.btnEndCall).setOnClickListener(mOnEndCallClicked);

        // Get the object that we use for the call thumbnails
        mCallThumbnails = (LinearLayout) findViewById(R.id.callThumbnails);

        // Disable the 'Add call' button as we only support a single call ATM.
        final View addCallThumbnail = findViewById(R.id.addCallThumbnail);

        //Not all devices will be able to add multiple calls
        if (addCallThumbnail != null)
        {
            //findViewById(R.id.addCallThumbnail).setEnabled(false);
            addCallThumbnail.setOnClickListener(mOnAddCallClicked);
            mAddCallClicked = false;
        }

        // Video views
        createVideoComponents(mCallOutgoing);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled())
        {
            final Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(intent);
        }
    }

    @Override
    public void onUserLeaveHint ()
    {
        Log.d(TAG, "onUserLeaveHint");
        pipToggle(true, false);
    }

    @Override
    public void onPictureInPictureModeChanged (boolean isInPictureInPictureMode, Configuration newConfig) {
        Log.d(TAG, "onPictureInPictureModeChanged" + isInPictureInPictureMode);
	    //Show/Hide place before switching of mode
        pipToggleVisibility(!isInPictureInPictureMode);
    }

    private boolean isFacingBack(int cameraIndex) {
        final CameraInfo cameraInfo = new CameraInfo();
        Camera.getCameraInfo(cameraIndex, cameraInfo);
        return cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK;
    }

    private void startLocalCamera() {
    		// Default to what camera was used previously.
	    	final int cameraIdxBeingUsed = mCallManager.getCamera();
	    	final CameraInfo cameraInfo = new CameraInfo();
	    	Camera.getCameraInfo(cameraIdxBeingUsed, cameraInfo);
	    	if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT)
	    	{
	    		Log.d(TAG, "onCreate() - using front camera");
	    		mIsFrontCameraActive = true;
	    		setCameraToUse(CameraInfo.CAMERA_FACING_FRONT);
	    	}
	    	else
	    	{
	    		Log.d(TAG, "onCreate() - using rear camera");
	    		mIsFrontCameraActive = false;
	    		setCameraToUse(CameraInfo.CAMERA_FACING_BACK);
	    	}
    }

    /**
     * Change the activity views based on whether the call is unanswered or not
     *
     * @param isUnanswered
     *            is the call unanswered
     */
    private void toggleViewForIncomingUnansweredCall(boolean isUnanswered)
    {
        if (isUnanswered)
        {
            findViewById(R.id.btnAnswerCall).setVisibility(View.VISIBLE);
            findViewById(R.id.switchMute).setVisibility(View.GONE);
            //Visibility that should be set is different depending on orientation, to get desired layout
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            {
                findViewById(R.id.switchMuteVideo).setVisibility(View.GONE);
            }
            else
            {
                findViewById(R.id.switchMuteVideo).setVisibility(View.INVISIBLE);
            }
            findViewById(R.id.switchHold).setVisibility(View.INVISIBLE);
        }
        else
        {
            findViewById(R.id.btnAnswerCall).setVisibility(View.GONE);
            findViewById(R.id.switchMute).setVisibility(View.VISIBLE);
            findViewById(R.id.switchMuteVideo).setVisibility(View.VISIBLE);
            findViewById(R.id.switchHold).setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onRestoreInstanceState(final Bundle savedInstanceState) {
        Log.d(TAG, "onRestoreInstanceState() called");
        super.onRestoreInstanceState(savedInstanceState);

        /*
         * Restore the previous states of the toggle buttons
         */
        // Audio mute
        boolean savedState = savedInstanceState
                .getBoolean(SAVED_STATE_KEY_AUDIO_MUTED);
        if (savedState) {
            toggleButton(mAudioMuteButton);
        }
        muteAudio(savedState, false);

        // Video mute
        savedState = savedInstanceState.getBoolean(SAVED_STATE_KEY_VIDEO_MUTED);
        if (savedState) {
            toggleButton(mVideoMuteButton);
        }
        muteVideo(savedState, false);

        // Camera swap
        savedState = savedInstanceState.getBoolean(SAVED_STATE_KEY_FRONT_CAMERA_ACTIVE);
        if (savedState) {
            toggleButton(mCameraButton);
        }
        toggleCamera(savedState, false);

        final AudioDevice device = AudioDevice.valueOf(savedInstanceState.getString(SAVED_STATE_KEY_AUDIO_DEVICE));
        mCallManager.getAudioDeviceManager().setAudioDevice(device);

        // DTMF keypad
        savedState = savedInstanceState
                .getBoolean(SAVED_STATE_KEY_KEYPAD_VISIBLE);
        if (savedState) {
            toggleButton(mDTMFKeypadButton);
        }
        showDTMFKeypad(savedState);
        mInCallManager.restoreState(savedInstanceState);

        // Local video
        mAudioDirection = (MediaDirection) savedInstanceState.getSerializable(SAVED_STATE_KEY_AUDIO_DIRECTION);
        mVideoDirection = (MediaDirection) savedInstanceState.getSerializable(SAVED_STATE_KEY_VIDEO_DIRECTION);

        // Remote video
        mRemoteVideo = savedInstanceState.getBoolean(SAVED_STATE_KEY_REMOTE_VIDEO);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        if (isInPictureInPictureMode())
        {
            pipToggleVisibility(false);
        }

        mInCallManager.setCallListener(this);

        //Only create an outgoing call if this is the initial creation of this
        //activity.  (ie. NOT when a user returns to app after placing in background.)
        setupCallAndVideoViews(mInitial && mCallOutgoing);
        mInitial = false;

        populateViews();
    }

    /**
     * Setup the views used to display video streams, along with the capture
     * camera.
     */
    private void setupCallAndVideoViews(boolean shouldCreateOutgoingCall) {
        if (mVideoDirection.isSending()) {
            Log.d(TAG, "Call has video, setup the video views and camera");
        }

        if (shouldCreateOutgoingCall) {
            new CreateOutgoingCallTask().execute();
        }
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        Log.d(TAG, "Save Instance State");
        super.onSaveInstanceState(outState);

        final int displayRotation = Main.getDisplay().getRotation();
        if (displayRotation != mDisplayRotation) {
            Log.d(TAG, "Device is rotating/rotated. current/new display "
                    + "orientation (" + displayRotation + ") doesn't match the"
                    + " previous/old rotation (" + mDisplayRotation + ")");
        }
        AudioDevice mAudioDevice = AudioDevice.NONE;
        if (mCallManager.getAudioDeviceManager() != null)
        {
            mAudioDevice = mCallManager.getAudioDeviceManager().getSelectedAudioDevice();
        }
        outState.putBoolean(SAVED_STATE_KEY_AUDIO_MUTED, mIsAudioMuted);
        outState.putBoolean(SAVED_STATE_KEY_VIDEO_MUTED, mIsVideoMuted);
        outState.putBoolean(SAVED_STATE_KEY_FRONT_CAMERA_ACTIVE, mIsFrontCameraActive);
        outState.putString(SAVED_STATE_KEY_AUDIO_DEVICE, mAudioDevice.name());
        outState.putBoolean(SAVED_STATE_KEY_KEYPAD_VISIBLE, mIsKeypadVisible);
        outState.putSerializable(SAVED_STATE_KEY_AUDIO_DIRECTION, mAudioDirection);
        outState.putSerializable(SAVED_STATE_KEY_VIDEO_DIRECTION, mVideoDirection);
        outState.putBoolean(SAVED_STATE_KEY_REMOTE_VIDEO, mRemoteVideo);

        mInCallManager.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

        if (!mAddCallClicked) {
            // Just in case the speaker phone feature is enabled, turn it off
            // now.
            setSpeakerphoneOn(false);
        }

        Main.removeUCListener(this);

        if (mCallManager != null)
        {
            mCallManager.removeListener(this);
            mCallManager.setPreviewView(null);

            if (mCallManager.getAudioDeviceManager() !=null)
            {
                mCallManager.getAudioDeviceManager().removeListener(this);
            }
        }

        mInCallManager.destroy();

        INSTANCE = null;
    }

    /**
     * Set up the appearance of the views; colours, text and thumbnails.
     */
    private void populateViews() {
        Log.d(TAG, "populateViews");

        final Call currentCall = mInCallManager.getCurrentCall();
        // Remote name/address/number
        if (currentCall != null) {
            final String name = currentCall.getRemoteDisplayName();
            if (TextUtils.isEmpty(name)) {
                mRemoteNameView.setText(currentCall.getRemoteAddress());
            } else {
                mRemoteNameView.setText(name);
            }
        } else {
            mRemoteNameView.setText(mCalleeAddress);
        }

        // Call status
        if (currentCall != null) {
            setCallStatusDisplayText(currentCall.getCallStatus());
        } else {
            setCallStatusDisplayText(null);
        }

        // Call thumbnails
        setupCallThumbnails();
        setButtonStatus(mHoldButton, mInCallManager.isSelectedCallLocallyHeld());

        updateStatusAndButtons();
    }

    /**
     * Create the layout view components that relate to the video feature.
     */
    private void createVideoComponents(boolean outgoingCallPending) {
        Log.d(TAG, "createVideoComponents(" + outgoingCallPending + ")");
        mVideoContainer = (RelativeLayout) findViewById(R.id.videoFrame);
        mRemoteHeldIcon = (ImageView) findViewById(R.id.remoteCallHeld);
        mDisplayRotation = Main.getDisplay().getRotation();

        //clear all view
        for (int i = 0; i < mVideoContainer.getChildCount(); i++)
        {
            mVideoContainer.removeView(mVideoContainer.getChildAt(i));
        }

        //Number of current calls
        int numberOfCurrentCalls = mInCallManager.getNumberOfActiveCalls();

        if (outgoingCallPending)
        {
            numberOfCurrentCalls++;
        }

        Log.v(TAG, "showing remote video component");

        showVideoContainer(
        		((mCallOutgoing) && mVideoDirection.isSending()) ||
        		((!mCallOutgoing) && (mVideoDirection != MediaDirection.NONE))
        		);

        /*
         * Add the remote video view to the container first, so it's behind
         * the preview
         */
        mVideoContainer.addView(mInCallManager.generateRemoteVideoViews(mCallManager,
                mVideoContainer, this, numberOfCurrentCalls));

        Log.v(TAG, "Creating local video component");
        //Set the preview view to a pixel.  It will get resized when there is an image to render,
        //in onFrameSizeChanged.
        final Point previewSize = new Point(1, 1);

        // This creates a view for local video (i.e. that captured by the local camera).
        if (mPreviewView == null)
        {
            mPreviewView = mCallManager.createVideoSurface(this, previewSize, this);
            //The preview view should be on top
            mPreviewView.setZOrderOnTop(true);
            // Add the preview view to the container, with a top and left margin
            RelativeLayout.LayoutParams localLp = new RelativeLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            localLp.setMargins(10, 10, 0, 0);
            mPreviewView.setLayoutParams(localLp);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                mPreviewView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {
                        float positionX = (int) motionEvent.getX();
                        float positionY = (int) motionEvent.getY();

                        Log.d(TAG, "onTouch x=" + positionX + ",y=" + positionY);

                        //Build the Rectangle, where the focus should be applied.
                        final MeteringRectangle[] meterRecArray;
                        meterRecArray = calculateFocusRect(motionEvent.getX(), motionEvent.getY());

                        Log.d(TAG, "setFocusArea " + meterRecArray[0].toString());

                        mCallManager.setFocusArea(meterRecArray);

                        return false;
                    }
                });
            }
        }
        else
        {
            ((ViewGroup)mPreviewView.getParent()).removeView(mPreviewView);
        }
        mVideoContainer.addView(mPreviewView);

        if (mRemoteHeldIcon.getParent() != null)
        {
            ((RelativeLayout)mRemoteHeldIcon.getParent()).removeView(mRemoteHeldIcon);
        }

        mVideoContainer.addView(mRemoteHeldIcon);
        mRemoteHeldIcon.setVisibility(View.INVISIBLE);

        /*
         * We need to keep the screen on to prevent it sleeping during video
         * calls
         */
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    //Calculate a Rectangle, where the user touched the screen.
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private MeteringRectangle[] calculateFocusRect(float x, float y) {
        //Size of the Rectangle.
        int areaSize = 200;

        int left = clamp((int) x - areaSize / 2, 0, mPreviewView.getWidth() - areaSize);
        int top = clamp((int) y - areaSize / 2, 0, mPreviewView.getHeight() - areaSize);

        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
        Rect focusRect = new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
        MeteringRectangle meteringRectangle = new MeteringRectangle(focusRect, MeteringRectangle.METERING_WEIGHT_MAX);

        return new MeteringRectangle[] {meteringRectangle};
    }

    //Clamp the inputs.
    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    /**
     * End the current call and exit this Activity.
     */
    protected void endCall() {
        NotificationHelper
                .removeIncomingCallNotification(getApplicationContext());
        final Call currentCall = mInCallManager.getCurrentCall();
        if (currentCall == null) {
            Log.w(TAG, "Trying to end a call that no longer exists, something "
                    + "has gone amiss.");
            finish();
        } else {
            boolean doesEndedCallHaveAView = mInCallManager.removeVideoSurfaceForCall(currentCall);

            currentCall.end();
            currentCall.removeListener(this);
            updateCallsList();

            // If this was the only call, leave this Activity
            if (mInCallManager.getNumberOfActiveCalls() == 0) {
                finish();
            } else {
                //Only need to refresh video views if the ended call had a remote video view
                if (doesEndedCallHaveAView)
                {
                    refreshVideoViews();
                }
                else
                {
                 // This call originated remotely and has not been answered yet
                    if (currentCall != null
                            && currentCall.getCallStatus() == CallStatus.ALERTING) {
                        toggleViewForIncomingUnansweredCall(true);
                    }
                    else
                    {
                        toggleViewForIncomingUnansweredCall(false);
                    }
                }
                populateViews();
            }
        }
    }

    private void refreshVideoViews()
    {
        createVideoComponents(false);
    }

    protected void answerCall() {
        NotificationHelper
                .removeIncomingCallNotification(getApplicationContext());
        final Call currentCall = mInCallManager.getCurrentCall();
        if (currentCall == null) {
            Log.w(TAG, "Trying to answer a call that no longer exists, something "
                    + "has gone amiss.");
            finish();
        } else {
            mRemoteVideo = currentCall.hasRemoteVideo();
            currentCall.answer(mAudioDirection, mVideoDirection);

            Log.d(TAG, "answerCall(): currentCall.hasRemoteVideo()="+currentCall.hasRemoteVideo());

            toggleViewForIncomingUnansweredCall(false);

            //This is the only call
            if (mInCallManager.getNumberOfActiveCalls() == 1)
            {
                createVideoComponents(false);
                setupCallAndVideoViews(false);
                mNeedToSetupVideoWhenCallEstablished = true;
            }
            else
            {
                refreshVideoViews();
            }
        }
    }

    /**
     *
     * @param view
     *            The UI button ({@link View}) that was pressed by the user.
     */
    public void onDtmfKeyClick(final View view) {
        String digit = (String) view.getTag();
        Log.i(TAG, "DTMF digit entered: " + digit);
        final String digits = mDTMFDigitsView.getText().toString();
        mDTMFDigitsView.setText(digits + digit);

        // Only play the tone if we are NOT muted
        if (mIsAudioMuted) {
            Log.w(TAG, "Mute is enabled, so we don't play the DTMF tone");
        } else if (mInCallManager.getCurrentCall() != null) {
            mInCallManager.getCurrentCall().playDTMFCode(digit, true);
        }
    }

    /**
     * Show/hide the layout containing the video views and hide/show the call
     * details layout.
     */
    protected void showVideoContainer(final boolean showVideo) {
	    	mVideoContainer.setVisibility(showVideo ? View.VISIBLE : View.GONE);
	    	mDetailsContainer.setVisibility(showVideo ? View.GONE : View.VISIBLE);
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.phone.CallListener#
     *      onCallFailed(com.alicecallsbob.fcsdk.android.phone.Call,
     *      java.lang.String, com.alicecallsbob.fcsdk.android.phone.CallStatus)
     */
    @Override
    public void onCallFailed(final Call call, final String description, final CallStatus callStatus) {
        Log.d(TAG, "onCallFailed:" + description);
        if (mInCallManager.contains(call)) {
            terminate(call, description);
        }
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.phone.CallListener#
     *      onDialFailed(com.alicecallsbob.fcsdk.android.phone.Call,
     *      java.lang.String, com.alicecallsbob.fcsdk.android.phone.CallStatus)
     */
    @Override
    public void onDialFailed(final Call call, final String description, CallStatus callStatus) {
        Log.d(TAG, "onDialFailed:" + description);
        if (mInCallManager.contains(call)) {
            terminate(call, description);
        }
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.phone.PhoneListener#
     *      onLocalMediaStream()
     */
    @Override
    public void onLocalMediaStream() {
        Log.d(TAG, "onLocalMediaStream");
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.phone.CallListener#
     *      onMediaChangeRequested(com.alicecallsbob.fcsdk.android.phone.Call,
     *      boolean, boolean)
     */
    @Override
    public void onMediaChangeRequested(final Call call,
            final boolean hasRemoteAudio, final boolean hasRemoteVideo) {
        Log.d(TAG, "onMediaChangeRequested:audio="+hasRemoteAudio+",video="+hasRemoteVideo);
        if (hasRemoteVideo != mRemoteVideo) {
            Log.i(TAG, "The video stream has been added/removed/refused");
            mRemoteVideo = hasRemoteVideo;
            /*
             * Show or hide the video views as required.
             */
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showVideoContainer(hasRemoteVideo);
                }
            });
        }
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.phone.CallListener#
     *      onRemoteDisplayNameChanged(com.alicecallsbob.fcsdk.android.phone.Call,
     *      java.lang.String)
     */
    @Override
    public void onRemoteDisplayNameChanged(final Call call, final String name) {
        Log.i(TAG, "Remote party name has changed");
        if (!TextUtils.isEmpty(name)) {
            mRemoteNameView.post(new Runnable() {
                @Override
                public void run() {
                    mRemoteNameView.setText(name);
                }
            });
        }
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.phone.CallListener#
     *      onRemoteMediaStream(com.alicecallsbob.fcsdk.android.phone.Call)
     */
    @Override
    public void onRemoteMediaStream(final Call call) {
        Log.d(TAG, "onRemoteMediaStream");
    }

	@Override
	public void onStatusChanged(Call call, CallStatusInfo statusInfo) {

		Log.e(TAG, String.format("onStatusChanged call[%s] callStatus[%s], message[%s], reason[%s]", call, statusInfo.getCallStatus(), statusInfo.getMessage(), statusInfo.getReason()));

		onStatusChanged(call, statusInfo.getCallStatus());
	}

    /**
     * @see com.alicecallsbob.fcsdk.android.phone.CallListener#
     *      onStatusChanged(com.alicecallsbob.fcsdk.android.phone.Call,
     *      com.alicecallsbob.fcsdk.android.phone.CallStatus)
     */
    @Override
    public void onStatusChanged(final Call call, final CallStatus status) {
        Log.i(TAG, "call status changed: " + status.name());

        switch (status) {
        case RINGING:
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    populateViews();
                }
            });
            break;

        case IN_CALL:
            final InCallActivity activity = this;
            mRemoteVideo = call.hasRemoteVideo();
            Log.d(TAG, "Remote video: " + mRemoteVideo);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                		showVideoContainer(mRemoteVideo || mVideoDirection.isSending());
                		if (call == mInCallManager.getCurrentCall())
                    {
                        toggleViewForIncomingUnansweredCall(false);
                    }

                    activity.setSpeakerphoneOn(false);

                    populateViews();

                    if (mNeedToSetupVideoWhenCallEstablished) {
                    	Log.d(TAG, "IN_CALL: Setting up video");
                    	startLocalCamera();
                    	mNeedToSetupVideoWhenCallEstablished = false;
                    }
                }
            });
            break;

        case ENDED:
            terminate(call, "Call ended");
            break;

        case ERROR:
            mMsgHandler.obtainMessage(MSG_ERROR,
                    "An error has occured, so the" + " call has ended")
                    .sendToTarget();
            terminate(call, "Call errored");
            break;

        case ALERTING:
            break;

        case BUSY:
            terminate(call, "Callee is busy");
            break;

        case MEDIA_PENDING:
        		break;

        case NOT_FOUND:
            terminate(call, "User not found");
            break;

        case SETUP:
        		break;

        case TIMED_OUT:
            terminate(call, "Call timed out");
            break;

        case UNINITIALIZED:
        default:
            break;
        }
    }

    /**
     * The given {@link Call} has ended, so put out the given message and update
     * the UI, leaving this Activity if needs be.
     *
     * @param call
     *            The {@link Call} that has ended
     * @param message
     *            A descriptive message saying why the {@link Call} has ended
     */
    private void terminate(final Call call, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (message != null) {
                    Log.i(TAG, message);
                    Toast.makeText(InCallActivity.this, message,
                            Toast.LENGTH_SHORT).show();
                }

                call.removeListener(InCallActivity.this);

                mInCallManager.removeCall(call);


                // If this was the only call, leave this Activity
                if (mInCallManager.getNumberOfActiveCalls() == 0) {
                    finish();
                } else {
                    // Set the current call to the first in the list, why not
                    populateViews();
                    refreshVideoViews();
                }
            }
        });
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.phone.PhoneListener#
     *      onIncomingCall(com.alicecallsbob.fcsdk.android.phone.Call)
     */
    @Override
    public void onIncomingCall(final Call call) {
        Log.i(TAG, "Incoming call");
        call.addListener(this);

        runOnUiThread(new Runnable(){

            @Override
            public void run() {
                updateCallsList();
                populateViews();
            }

        });

        mMsgHandler.sendEmptyMessage(MSG_INCOMING);
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.uc.UCListener#onConnectionLost()
     */
    @Override
    public void onConnectionLost() {
        Log.w(TAG, "onConnectionLost");
        mMsgHandler.obtainMessage(MSG_ERROR, "Connection lost").sendToTarget();
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.uc.UCListener#onSessionNotStarted()
     */
    @Override
    public void onSessionNotStarted() {
        Log.w(TAG, "onSessionNotStarted");
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.uc.UCListener#onSessionStarted()
     */
    @Override
    public void onSessionStarted() {
        Log.d(TAG, "onSessionStarted");
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.uc.UCListener#onSystemFailure()
     */
    @Override
    public void onSystemFailure() {
        Log.w(TAG, "onSystemFailure");
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.uc.UCListener#
     *      onGenericError(java.lang.String, java.lang.String)
     */
    @Override
    public void onGenericError(final String error, final String reason) {
        Log.w(TAG, "onGenericError");
    }

	/**
	 * Selects the correct camera index to use for the chosen camera facing direction and informs the SDK.
	 *
	 * @param cameraDirectionSelected the camera direction selected. Can be CameraInfo.CAMERA_FACING_FRONT or
	 *        CameraInfo.CAMERA_FACING_BACK;
	 */
    protected void setCameraToUse(final int cameraDirectionSelected)
    {
	    	Log.d(TAG, "setCameraToUse() - cameraDirectionSelected: " + cameraDirectionSelected);

	    	/*
	    	 * As per the android docs. if there is only 1 camera on the device, the idx is always n-1 where n is the number
	    	 * of cameras. If there is only 1 camera, the camera idx is always 0 whether it is a FRONT or BACK facing.
	    	 *
	    	 * If there is more than 1 camera, we default to the first selected facing camera we can find.
	    	 * If we cannot find a selected facing camera, we default to idx 0.
	    	 */
	    	int cameraIdxToUse = 0; // default to 0
	    	final int numberOfCameras = Camera.getNumberOfCameras();
	    	if (numberOfCameras == 1)
	    	{
	    		// be safe
	    		Log.d(TAG, "setCameraToUse() - Only 1 camera on device, defaulting to idx 0");
	    	}
	    	else
	    	{
	    		Log.d(TAG, "setCameraToUse() - More than 1 camera on device, using the first camera facing the selected direction...");

	    		boolean cameraFound = false;
	    		for (int i = 0; i < numberOfCameras; i++)
	    		{
	    			final CameraInfo cameraInfo = new CameraInfo();
	    			Camera.getCameraInfo(i, cameraInfo);

	    			if (cameraInfo.facing == cameraDirectionSelected)
	    			{
	    				cameraIdxToUse = i;
	    				Log.d(TAG, "Using camera at idx: " + i);
	    				cameraFound = true;
	    				break;
	    			}
	    		}

	    		if (!cameraFound)
	    		{
	    			Log.w(TAG, "setCameraToUse() - Failed to find camera facing the direction selected - defaulting to idx 0");
	    		}
	    	}

	    	mCallManager.setCamera(cameraIdxToUse);
    }

    /**
     *
     * @param on
     *            Should we turn the speaker phone on, or not.
     */
    protected void setSpeakerphoneOn(final boolean on)
    {

        final AudioDeviceManager audioManager = mCallManager.getAudioDeviceManager();
        if (audioManager != null && audioManager.getAudioDevices().contains(AudioDevice.SPEAKER_PHONE))
        {
            Log.v(TAG, "Set speaker phone " + (on ? "ON" : "OFF"));
            if (on)
            {
                if (audioManager.getSelectedAudioDevice() != AudioDevice.SPEAKER_PHONE)
                {
                    audioManager.setAudioDevice(AudioDevice.SPEAKER_PHONE);
                }
            }
            else
            {
                if (audioManager.getSelectedAudioDevice() == AudioDevice.SPEAKER_PHONE)
                {
                    audioManager.setAudioDevice(AudioDevice.NONE);
                }
            }

        }
        updateStatusAndButtons();
    }

    private void updateStatusAndButtons()
    {
        final AudioDevice selected = mCallManager.getAudioDeviceManager().getSelectedAudioDevice();
        switch (selected)
        {
            case BLUETOOTH:
                mIsSpeakerActive = false;
                setButtonStatus(mSpeakerButton, mIsSpeakerActive);
                mIsBluetoothActive = true;
                setButtonStatus(mBluetoothButton, mIsBluetoothActive);
                break;
            case SPEAKER_PHONE:
                mIsSpeakerActive = true;
                setButtonStatus(mSpeakerButton, mIsSpeakerActive);
                mIsBluetoothActive = false;
                setButtonStatus(mBluetoothButton, mIsBluetoothActive);
                break;
            default:
                mIsSpeakerActive = false;
                setButtonStatus(mSpeakerButton, mIsSpeakerActive);
                mIsBluetoothActive = false;
                setButtonStatus(mBluetoothButton, mIsBluetoothActive);
                break;
        }
    }

    /**
     * Toggle the given button (actually a {@link LinearLayout} that's accessed
     * as a {@link View}), set the Tag on the {@link View} and change the image
     * for the toggle icon.
     *
     * @param view
     *            The button ({@link View}) we have to toggle
     * @return true if we just toggled the button on, false otherwise.
     */
    protected boolean toggleButton(final View view) {
        ImageView toggleImage = null;
        switch (view.getId()) {
        case R.id.switchPiP:
            toggleImage = (ImageView) view.findViewById(R.id.pipToggleImage);
            break;

        case R.id.switchMute:
            toggleImage = (ImageView) view.findViewById(R.id.muteToggleImage);
            break;

        case R.id.switchMuteVideo:
            toggleImage = (ImageView) view.findViewById(R.id.videoToggleImage);
            break;

        case R.id.switchHold:
            toggleImage = (ImageView) view.findViewById(R.id.holdToggleImage);
            break;

        case R.id.switchSpeaker:
            toggleImage = (ImageView) view
                    .findViewById(R.id.speakerToggleImage);
            break;

        case R.id.switchBluetooth:
            toggleImage = (ImageView) view
                    .findViewById(R.id.btToggleImage);
            break;
        case R.id.switchCamera:
            toggleImage = (ImageView) view
                    .findViewById(R.id.cameraToggleImage);
            break;

        case R.id.switchTorch:
            toggleImage = (ImageView) view
                    .findViewById(R.id.torchToggleImage);
            break;

        case R.id.switchDTMF:
            toggleImage = (ImageView) view.findViewById(R.id.keypadToggleImage);
            break;
        }

        final boolean isChecked = "ON".equals(toggleImage.getTag());
        final boolean check = !isChecked;
        if (check) {
            toggleImage.setTag("ON");
            toggleImage
                    .setImageResource(android.R.drawable.button_onoff_indicator_on);
        } else {
            toggleImage.setTag("OFF");
            toggleImage
                    .setImageResource(android.R.drawable.button_onoff_indicator_off);
        }

        return check;
    }

    private void setButtonStatus(View view, boolean isOn)
    {
        ImageView toggleImage = null;
        switch (view.getId()) {
        case R.id.switchMute:
            toggleImage = (ImageView) view.findViewById(R.id.muteToggleImage);
            break;

        case R.id.switchMuteVideo:
            toggleImage = (ImageView) view.findViewById(R.id.videoToggleImage);
            break;

        case R.id.switchHold:
            toggleImage = (ImageView) view.findViewById(R.id.holdToggleImage);
            break;

        case R.id.switchSpeaker:
            toggleImage = (ImageView) view
                    .findViewById(R.id.speakerToggleImage);
            break;

        case R.id.switchBluetooth:
            toggleImage = (ImageView) view
                    .findViewById(R.id.btToggleImage);
            break;

        case R.id.switchCamera:
            toggleImage = (ImageView) view
                    .findViewById(R.id.cameraToggleImage);
            break;

        case R.id.switchDTMF:
            toggleImage = (ImageView) view.findViewById(R.id.keypadToggleImage);
            break;
        }

        if (isOn) {
            toggleImage.setTag("ON");
            toggleImage
                    .setImageResource(android.R.drawable.button_onoff_indicator_on);
        } else {
            toggleImage.setTag("OFF");
            toggleImage
                    .setImageResource(android.R.drawable.button_onoff_indicator_off);
        }
    }

    /**
     * Sets the local video preview size to be a ratio of the overall video container size.
     *
     * @param baseImageWidth
     * @param baseImageHeight
     * @return The local video size
     */
    private Point getLocalVideoViewSize(final int baseImageWidth,
            final int baseImageHeight, int numberOfRemoteVideoViews)
    {
        final boolean landscapeVideo = baseImageWidth > baseImageHeight;
        final boolean landscapeOrientation = getResources().getConfiguration().
                orientation == Configuration.ORIENTATION_LANDSCAPE;

        Point videoContainerSize = new Point(mVideoContainer.getWidth(),
                mVideoContainer.getHeight());

        if (!landscapeOrientation)
        {
            //The video container width and height don't seem to reflect the
            //portrait orientation in some instances so flip
            if (mVideoContainer.getWidth() > mVideoContainer.getHeight())
            {
                videoContainerSize = new Point(mVideoContainer.getHeight(),
                        mVideoContainer.getWidth());
            }
        }

        int height;
        int width;
        //Add 2 to the ratio for every additional active video view (over 1).
        //This is because the preview looks too big when there are multiple calls
        final int ratio = PREVIEW_WINDOW_SIZE_RATIO + ((numberOfRemoteVideoViews - 1) * 2);

        if (landscapeVideo)
        {
            width = videoContainerSize.x / ratio;
        }
        else
        {
            width = videoContainerSize.y / ratio;
        }

        if (landscapeVideo && !landscapeOrientation)
        {
            height = (int)(baseImageWidth * ((float)width / baseImageHeight));
        }
        else {
            height = (int) (baseImageHeight * ((float) width / baseImageWidth));
        }

        return new Point(width, height);
    }

    /**
     * @see com.alicecallsbob.fcsdk.android.phone.VideoSurfaceListener#
     *      onFrameSizeChanged(int, int,
     *      com.alicecallsbob.fcsdk.android.phone.VideoSurface.Endpoint)
     */
    @Override
    public void onFrameSizeChanged(final int width, final int height,
            final VideoSurface.Endpoint endpoint, final VideoSurface videoView) {
        Log.d(TAG, "onFrameSizeChanged, " + endpoint.name() + ": " + width
                + ", " + height);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: onFrameSizeChanged(Start)");
                final int numberOfVideoViews = mInCallManager.getNumberOfVideoSurfaces();
                if (endpoint == Endpoint.LOCAL) {
                    //Point newSize = getLocalVideoViewSize(480, 640, numberOfVideoViews);
                    Point newSize = getLocalVideoViewSize(width, height, numberOfVideoViews);
                    Log.v(TAG, "Setting local size to " + newSize);
                    videoView.setDimensions(newSize);
                } else if (endpoint == Endpoint.REMOTE) {
                    //When rotating quickly, there seems to sometimes be a race which can
                    //cause the video views to be empty when this method is called.
                    //Check for this scenario.
                    if (numberOfVideoViews > 0) {
                        videoView.setDimensions(mInCallManager.getRemoteVideoViewSize(mVideoContainer, width,
                                height, null, numberOfVideoViews));
                    }
                }
                videoView.setAlpha(1);
                Log.d(TAG, "run: onFrameSizeChanged(Finish)");
            }
        });
    }

    public void onSurfaceRenderingStarted(final VideoSurface surface)
    {
        Log.d(TAG, "in onSurfaceRenderingStarted surface=" + surface);
        if (surface == mPreviewView)
        {
            Log.d(TAG, "calling setPreviewView");
            mCallManager.setPreviewView(surface);
        }
    }

    /**
     * Hold or resume the call.
     *
     * @param onHold
     */
    protected void putOnHold(final boolean onHold, final boolean shouldToast) {

        mInCallManager.holdSelectedCall(onHold);

        if (shouldToast) {
            Utils.logAndToast(InCallActivity.this, TAG, Log.INFO,
                    onHold ? getString(R.string.hold_toggle_on) : getString(R.string.hold_toggle_off),
                            Toast.LENGTH_SHORT);
        }
    }

    @Override
    public boolean isInPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= 24) {
            return super.isInPictureInPictureMode();
        }

        return false;
    }

    /**
     *1001
     * @param pictureInPicture
     */
    protected boolean pipToggle(final boolean pictureInPicture, final boolean shouldToast) {
        Log.d(TAG, "pipToggle:" + pictureInPicture);
        if (pictureInPicture != mIsPictureInPicture) {
            Utils.logAndToast(InCallActivity.this, TAG, Log.INFO, "TOGGLE PIP!!");

            // Set Picture in Picture Mode
            mIsPictureInPicture = pictureInPicture;
            Log.v(TAG, "mIsPictureInPicture:" + mIsPictureInPicture);

            if (Build.VERSION.SDK_INT >= 26) {
                Rational aspectRatio = new Rational(this.getWindow().getDecorView().getWidth(),
                                this.getWindow().getDecorView().getHeight());
                final PictureInPictureParams.Builder mPictureInPictureParamsBuilder = new PictureInPictureParams.Builder();
                mPictureInPictureParamsBuilder.setAspectRatio(aspectRatio).build();

                enterPictureInPictureMode(mPictureInPictureParamsBuilder.build());
            }
            else if (Build.VERSION.SDK_INT >= 24) {
                enterPictureInPictureMode();
            }
            else
            {
                Utils.logAndToast(InCallActivity.this, TAG, Log.INFO, "Picture in Picture not support on this device/OS version.", Toast.LENGTH_SHORT);
            }

            if (mInCallManager.getCurrentCall() != null) {
                if (shouldToast) {
                    Utils.logAndToast(InCallActivity.this, TAG, Log.INFO,
                            pictureInPicture ? getString(R.string.pip_toggle_on)
                                    : getString(R.string.pip_toggle_off), Toast.LENGTH_SHORT);
                }
            }
        }

        return mIsPictureInPicture;
    }

    protected  void pipToggleVisibility(final boolean showControls) {
        // Hide the full-screen UI (controls, etc.) while in picture-in-picture mode.
        findViewById(R.id.callDetailsLayout).setVisibility((showControls) ? View.VISIBLE : View.GONE);
        findViewById(R.id.switchesBottom).setVisibility((showControls) ? View.VISIBLE : View.GONE);
        findViewById(R.id.divider).setVisibility((showControls) ? View.VISIBLE : View.GONE);
        findViewById(R.id.switchesTop).setVisibility((showControls) ? View.VISIBLE : View.GONE);
        findViewById(R.id.dtmfKeypad).setVisibility((showControls) ? View.VISIBLE : View.GONE);
        findViewById(R.id.callQualityBar).setVisibility((showControls) ? View.VISIBLE : View.GONE);
        findViewById(R.id.remoteCallHeld).setVisibility((showControls) ? View.VISIBLE : View.GONE);

        if (!showControls)
        {
            this.getActionBar().hide();
        }
        else
        {
            this.getActionBar().show();
        }
    }

    /**
     *
     * @param mute
     */
    protected void muteAudio(final boolean mute, final boolean shouldToast) {
        if (mute != mIsAudioMuted) {
            mIsAudioMuted = mute ? true : false;

            if (mInCallManager.getCurrentCall() != null) {
                if (shouldToast) {
                    Utils.logAndToast(InCallActivity.this, TAG, Log.INFO,
                            mute ? getString(R.string.mute_audio_toggle_on)
                                    : getString(R.string.mute_audio_toggle_off), Toast.LENGTH_SHORT);
                }

                // if mute is checked we disable audio and vice versa
                mCallManager.enableLocalAudio(mute ? false : true);
            }
        }
    }

    /**
     *
     * @param mute
     */
    protected void muteVideo(final boolean mute, final boolean shouldToast) {
        if (mute != mIsVideoMuted) {
            mIsVideoMuted = mute ? true : false;

            if (mInCallManager.getCurrentCall() != null) {
                if (shouldToast) {
                    Utils.logAndToast(InCallActivity.this, TAG, Log.INFO,
                            mute ? getString(R.string.mute_video_toggle_on)
                                    : getString(R.string.mute_video_toggle_off), Toast.LENGTH_SHORT);
                }

                // if mute is checked we disable video and vice versa
                mCallManager.enableLocalVideo(mute ? false : true);

                // If mute is checked, release camera
                if (mute) {
                    mCallManager.releaseCamera();
                }
            }
        }
    }

    /**
     * Toggles the camera to use. When app is first started, we default to first front camera that can be found.
     *
     * @param toggledForward has camera been toggled?
     * @param shouldToast should we toast the camera change? This should be set to false if this is called from
     *        restoreInstsanceState as we do not want to change camera given user did not initiate the toggle.
     */
	protected void toggleCamera(final boolean toggledForward, final boolean shouldToast) {

			Log.d(TAG, "toggleCamera() - toggledForward: " + toggledForward +
					" mIsFrontCameraActive: " + mIsFrontCameraActive + " shouldToast: " + shouldToast);

			if (!shouldToast)
			{
				Log.d(TAG, "toggleCamera() not been asked to toast - called from restoreInstsanceState() - nothing to do");
				return;
			}

			final int cameraSelected;
		    if (toggledForward)
		    {
		    	if (mIsFrontCameraActive)
		    	{
		    		Log.d(TAG, "Camera choice toggled forward - currently using front camera - switching to rear");
			    	cameraSelected = CameraInfo.CAMERA_FACING_BACK;
			    	mIsFrontCameraActive = false;
		    	}
		    	else
		    	{
		    		Log.d(TAG, "Camera choice toggled forward - currently using rear camera - switching to front");
			    	cameraSelected = CameraInfo.CAMERA_FACING_FRONT;
			    	mIsFrontCameraActive = true;
		    	}
		    }
		    else
		    {
		    	if (mIsFrontCameraActive)
		    	{
		    		Log.d(TAG, "Camera choice toggled backward - currently using front camera - switching to rear");
			    	cameraSelected = CameraInfo.CAMERA_FACING_BACK;
			    	mIsFrontCameraActive = false;
		    	}
		    	else
		    	{
		    		Log.d(TAG, "Camera choice toggled backward - currently using rear camera - switching to front");
			    	cameraSelected = CameraInfo.CAMERA_FACING_FRONT;
			    	mIsFrontCameraActive = true;
		    	}
		    }

		    // tell SDK which camera to use
			setCameraToUse(cameraSelected);

			if (shouldToast) {
				Utils.logAndToast(InCallActivity.this, TAG, Log.INFO,
						mIsFrontCameraActive ? getString(R.string.camera_toggle_front)
								: getString(R.string.camera_toggle_back),
						Toast.LENGTH_SHORT);
			}

			// finally update the action bar menu item selection
			Main.setPreferredCameraIdx(mCallManager.getCamera());
	}

    /**
     * Toggles the torch to use.
     *
     * @param toggledOn has torch been toggled?
     * @param shouldToast should we toast the torch change? This should be set to false if this is called from
     *        restoreInstsanceState as we do not want to change torch given user did not initiate the toggle.
     */
    protected void toggleTorch(final boolean toggledOn, final boolean shouldToast) {

        Log.d(TAG, "toggleTorch() - toggledOn: " + toggledOn +
                " mIsFrontCameraActive: " + mIsFrontCameraActive + " shouldToast: " + shouldToast);

        if (toggledOn) {
            mCallManager.setFlashlightMode(Phone.FLASH_MODE_TORCH);
        } else {
            mCallManager.setFlashlightMode(Phone.FLASH_MODE_OFF);
        }
    }

    /**
     *
     * @param show
     */
    protected void showDTMFKeypad(final boolean show) {
        if (show != mIsKeypadVisible) {
            Log.i(TAG, (show ? "Show" : "Hide") + " the DTMF keypad");

            mIsKeypadVisible = show;

            mDTMFKeypad.setVisibility(show ? View.VISIBLE : View.GONE);

            if (mVideoContainer.getVisibility() != View.GONE) {
                mVideoContainer.setVisibility(show ? View.INVISIBLE
                        : View.VISIBLE);
            } else if (mDetailsContainer.getVisibility() != View.GONE) {
                mDetailsContainer.setVisibility(show ? View.INVISIBLE
                        : View.VISIBLE);
            }

            if (show) {
                mDTMFDigitsView.setText(null);
            }
        }
    }

    /**
     * Populate the view of thumbnails for all calls.
     */
     private void setupCallThumbnails()
     {
         Log.d(TAG, "setupCallThumbnails");
         if (mInCallManager.getNumberOfActiveCalls() > 0 && mCallThumbnails != null)
         {
             final int thumbnailCount = mCallThumbnails.getChildCount();
             Log.d(TAG, "got " + thumbnailCount + " thumbnails");

             // remove all the thumbnails, except the 'Add call' view
             mCallThumbnails.removeViews(1, thumbnailCount - 1);

             // create a thumbnail view for each active call
             for (Call call : mInCallManager.getActiveCalls())
             {
                 View item = createThumbView(call);
                 // set the tag so we know which call this thumbnail belongs to
                 item.setTag(call.getCallId());
                 mCallThumbnails.addView(item);
             }
         }
     }

    /**
     * Create the thumbnail view for the call at the given position in the list.
     *
     * @param call the call
     * @return the populated View
     */
    private View createThumbView(final Call call) {
        Log.d(TAG, "createThumbView for call: " + call.getCallId());

        /*
         * Create the view, selected or unselected
         */
        final LayoutInflater inflater = getLayoutInflater();
        View view = null;
        if (call == mInCallManager.getCurrentCall()) {
            view = inflater.inflate(R.layout.call_thumbnail_selected, null);
        } else {
            view = inflater.inflate(R.layout.call_thumbnail_unselected, null);
        }

        /*
         * Name/number
         */
        String name = call.getRemoteDisplayName();
        if (TextUtils.isEmpty(name)) {
            name = call.getRemoteAddress();

            // if we still don't have a valid string to display, use "Unknown"
            if (TextUtils.isEmpty(name)) {
                name = getString(android.R.string.unknownName);
            }
        }
        ((TextView) view.findViewById(R.id.thumbnailText)).setText(name);

        /*
         * Status icon
         */
        final ImageView statusIcon = (ImageView) view
                .findViewById(R.id.thumbnailStatus);
        if (statusIcon != null) {
            CallStatus status = call.getCallStatus();
            switch (status) {
            case IN_CALL:
                statusIcon.setImageResource(R.drawable.ic_call_status_active);
                break;

            case ALERTING:
                statusIcon.setImageResource(R.drawable.ic_call_status_incoming);
                break;

            case RINGING:
                statusIcon.setImageResource(R.drawable.ic_call_status_outgoing);
                break;

            default:
                statusIcon.setVisibility(View.GONE);
            }
        }

        //Set the progress
        updateCallQuality(call, (ProgressBar) view.findViewById(R.id.callQualityBar),
                mInCallManager.getCallQuality(call));

        return view;
    }

    /**
     * A thumbnail for a call has been clicked.
     *
     * @param v
     *            - the {@link android.view.View} that was clicked.
     */
    public void onThumbnailClick(final View v) {
        Log.d(TAG, "onThumbnailClick");
        // First check if the calls are still valid
        if (mInCallManager.getNumberOfActiveCalls() > 0) {
            final String callId = (String) v.getTag();
            Log.d(TAG, "thumbnail clicked, id:" + callId);

            if (mInCallManager.getCurrentCall().getCallId() != callId) {
                Log.v(TAG, "switch the selected call");

                selectCall(mInCallManager.getCall(callId));

                // ensure that this thumbnail is fully visible
                mCallThumbnails.requestChildFocus(v,
                        v.findViewById(R.id.thumbnailImage));
            }
        }
    }
    
    private View getThumbnailForCall(Call call)
    {
        if (mCallThumbnails != null)
        {
            for (int i = 0; i < mCallThumbnails.getChildCount(); i++)
            {
                final View thumbnail = mCallThumbnails.getChildAt(i);
                
                if (call.getCallId().equals((String) thumbnail.getTag()))
                {
                    return thumbnail;
                }
            }
        }
        
        return null;
    }
        
    /**
     * Select and highlight the specified call
     * @param selectedCall The call
     */
    private void selectCall(Call selectedCall)
    {
        mInCallManager.selectCall(selectedCall);
        
        final Call currentCall = mInCallManager.getCurrentCall();
        
        //If there are multiple calls, set the selected video background colour
        if (mInCallManager.getNumberOfActiveCalls() > 1)
        {
            final VideoSurface selectedCallVideoSurface = mInCallManager.getVideoSurfaceForSelectedCall();
            
            if (selectedCallVideoSurface != null)
            {
                final View viewParent = (View)selectedCallVideoSurface.getParent();
                viewParent.setBackgroundResource(R.color.video_highlight);        
            }
            
            //Set all others to transparent
            for (Call call : mInCallManager.getActiveCalls())
            {
                if (call != currentCall)
                {
                    final VideoSurface videoSurface = mInCallManager.getVideoSurfaceForCall(call);
                    
                    if (videoSurface != null)
                    {
                        final View viewParent = (View)videoSurface.getParent();
                        viewParent.setBackgroundColor(Color.TRANSPARENT);
                    }
                }
            }
        }

        populateViews();
        
        // This call originated remotely and has not been answered yet
        if (currentCall != null
                && currentCall.getCallStatus() == CallStatus.ALERTING) {
            toggleViewForIncomingUnansweredCall(true);
        }
        else
        {
            toggleViewForIncomingUnansweredCall(false);
        }
    }

    /**
     * Set the text in the call status view to reflect the {@link CallStatus}.
     * 
     * @param status
     *            The status that we want to display
     */
    private void setCallStatusDisplayText(final CallStatus status) {
        if (status != null) {
            switch (status) {
            case RINGING:
                mStatusView.setText(R.string.call_status_ringing);
                break;

            case IN_CALL:
                mStatusView.setText(R.string.call_status_connected);
                break;

            case ALERTING:
                mStatusView.setText(R.string.call_status_incoming);
                break;

            default:
                mStatusView.setText(status.name());
                break;
            }
        }
    }

    /**
     * Get the latest list of current calls.
     */
    private void updateCallsList() 
    {
        if (mCallManager != null) 
        {
            mInCallManager.updateActiveCalls(mCallManager.getCurrentCalls());   
        }
    }
    
    /**
     * Update the call quality for a call.
     * 
     * @param call the call
     * @param quality The new call quality
     */
    private void updateCallQuality(Call call, int quality)
    {
        final View thumbnail = getThumbnailForCall(call);
        
        ProgressBar progressBar;
        
        if (thumbnail != null)
        {
            //Get thumbnail quality bar
            progressBar = (ProgressBar) thumbnail.findViewById(R.id.callQualityBar);
        }
        else
        {
            //There are no thumbnails which means that the call quality progress bar
            //must be within the content layout
            progressBar = (ProgressBar) findViewById(R.id.callQualityBar);
        }
        
        updateCallQuality(call, progressBar, quality);
    }
    
    /**
     * Update the call quality for a call (as long as the value is valid).
     * 
     * @param call the call
     * @param progressBar the call quality progress bar for the call
     * @param quality The new call quality
     */
    private void updateCallQuality(Call call, ProgressBar progressBar, int quality)
    {        
        if (progressBar != null && quality >= 0 && quality <= progressBar.getMax())
        {
            progressBar.setProgress(quality);
        }
        else
        {
            Log.e(TAG, "Cannot find call quality progress bar...something has gone wrong!");
        }
        
        //Update call quality value
        mInCallManager.setCallQuality(call, quality);
    }
    
    /**
     * Show the held icon.
     */
    private void showHeldIcon()
    {
        mRemoteHeldIcon.setVisibility(View.VISIBLE);
    }
    
    /**
     * Hide the held icon.
     */
    private void hideHeldIcon()
    {
        mRemoteHeldIcon.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onCaptureSettingChange(PhoneVideoCaptureSetting setting,
            int camera) {
        Log.d(TAG, "Capture setting set to: " + setting.toString()
                + ", for camera id: " + camera);
    }

    @Override
    public void onConnectionReestablished() 
    {
        //DO NOTHING
        Log.d(TAG, "onConnectionReestablished");
    }

    @Override
    public void onConnectionRetry(int retryAttempt, long delay) 
    {
        //DO NOTHING
        Log.d(TAG, "onConnectionRetry");
    }

    @Override
    public void onInboundQualityChanged(Call call, int quality) 
    {
        Log.d("TAG", "InCallActivity onInboundQualityChanged " + quality);
        updateCallQuality(call, quality);
    }

    @Override
    public void onRemoteHeld(Call arg0)
    {
        Log.d("TAG", "InCallActivity onRemoteHeld");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showHeldIcon();
            }
        });
    }

    @Override
    public void onRemoteUnheld(Call arg0)
    {
        Log.d("TAG", "InCallActivity onRemoteUnheld");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideHeldIcon();
            }
        });
    }
    
    @Override
    public void onDeviceListChanged(Set<AudioDevice> devices, AudioDevice selectedDevice)
    {
        Log.d(TAG,"Device list changed: " + devices + ", device selected :" +selectedDevice);
        updateStatusAndButtons();
    }
}
