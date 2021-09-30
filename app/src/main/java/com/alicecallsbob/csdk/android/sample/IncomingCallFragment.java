package com.alicecallsbob.csdk.android.sample;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

import com.alicecallsbob.fcsdk.android.phone.Call;
import com.alicecallsbob.fcsdk.android.phone.CallListener;
import com.alicecallsbob.fcsdk.android.phone.CallStatus;
import com.alicecallsbob.fcsdk.android.phone.CallStatusInfo;
import com.alicecallsbob.fcsdk.android.phone.MediaDirection;

public class IncomingCallFragment extends DialogFragment
{
	/** The key used to pass the call ID argument to this dialog fragment. */
	public static final String CALL_ID_ARG = "callId";

	private final MediaDirection mAudioDirection;
	private final MediaDirection mVideoDirection;

	public IncomingCallFragment(MediaDirection audioDir, MediaDirection videoDir)
	{
		mAudioDirection = audioDir;
		mVideoDirection = videoDir;
	}

	private Call getCall(String callId)
	{
		for (Call call : Main.getPhoneManager().getCurrentCalls())
		{
			if (call.getCallId().equals(callId))
			{
				return call;
			}
		}

		return null;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		final Bundle args = getArguments();
		final Call incomingCall = getCall(args.getString(CALL_ID_ARG));

		if (incomingCall == null)
		{
			Log.e(Main.TAG, "Could not find call to create incoming call alert dialog.");
			return new AlertDialog.Builder(getActivity()).setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(R.string.incoming_title)
					.setMessage(R.string.incoming_error)
					.create();
		}

		// Get the name/address of the caller
		String caller = incomingCall.getRemoteDisplayName();
		if (caller == null)
		{
			caller = incomingCall.getRemoteAddress();
		}
		// Is it a video call?
		final boolean hasVideo = incomingCall.hasRemoteVideo();
		Log.i(Main.TAG, "incoming" + (hasVideo ? " VIDEO" : "") + " call from " + caller);

		// Set the dialog title
		final String title = getActivity().getString(hasVideo ? R.string.incoming_video_title : R.string.incoming_title);

		// Set the dialog message
		final String message = String.format(getActivity().getString(hasVideo ? R.string.incoming_video_message : R.string.incoming_message), caller);

		return new AlertDialog.Builder(getActivity()).setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(title)
				.setMessage(message)
				.setPositiveButton(R.string.incoming_accept, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						Log.v(Main.TAG, "User chose to answer the call");
						if (incomingCall != null)
						{
							// When we get to MEDIA_PENDING, launch the new Intent.
							// Ignore all other messages sent to this listener.
							final CountDownLatch readySignal = new CountDownLatch(1);
							incomingCall.addListener(new CallListener() {

								static private final String TAG = "CallListener";

								@Override public void onCallFailed(Call arg0, String arg1, CallStatus arg2) {}
								@Override public void onDialFailed(Call arg0, String arg1, CallStatus arg2) {}
								@Override public void onInboundQualityChanged(Call arg0, int arg1) {}
								@Override public void onMediaChangeRequested(Call arg0, boolean arg1, boolean arg2) {}
								@Override public void onRemoteDisplayNameChanged(Call arg0, String arg1) {}
								@Override public void onRemoteHeld(Call arg0) {}
								@Override public void onRemoteMediaStream(Call arg0) {}
								@Override public void onRemoteUnheld(Call arg0) {}
								@Override public void onStatusChanged(Call arg0, CallStatusInfo arg1) {}

								@Override
								public void onStatusChanged(Call call, CallStatus status)
								{
									if (status == CallStatus.MEDIA_PENDING)
									{
										Log.d(TAG, "Starting IN CALL activity after MEDIA_PENDING");
										readySignal.countDown();
										call.removeListener(this);
									}
								}
							});

							// Now the listener is in place, answer the call.
							incomingCall.answer(mAudioDirection, mVideoDirection);
							NotificationHelper.removeIncomingCallNotification(getActivity());

							try
							{
								readySignal.await();
								// We will now be at MEDIA_PENDING state and ready to launch the InCallActivity.
								final Intent intent = new Intent(getActivity().getApplicationContext(),
										InCallActivity.class);
								intent.putExtra(InCallActivity.KEY_AUDIO_DIRECTION, mAudioDirection);
								intent.putExtra(InCallActivity.KEY_VIDEO_DIRECTION, mVideoDirection);
								intent.putExtra(InCallActivity.KEY_OUTGOING, false);
								getActivity().startActivity(intent);
							}
							catch (InterruptedException e)
							{
								throw new RuntimeException(e);
							}
						}
					}
				})
				.setNegativeButton(R.string.incoming_reject, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						Log.v(Main.TAG, "User chose to reject the call");
						if (incomingCall != null)
						{
							incomingCall.end();
							NotificationHelper.removeIncomingCallNotification(getActivity());

							Log.v(Main.TAG, "User has ended the call");
						}
					}
				})
				.create();
	}
}