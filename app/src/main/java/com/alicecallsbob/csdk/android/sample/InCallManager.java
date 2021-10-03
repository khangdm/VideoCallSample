package com.alicecallsbob.csdk.android.sample;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

import com.alicecallsbob.fcsdk.android.phone.Call;
import com.alicecallsbob.fcsdk.android.phone.CallListener;
import com.alicecallsbob.fcsdk.android.phone.Phone;
import com.alicecallsbob.fcsdk.android.phone.PhoneVideoCaptureResolution;
import com.alicecallsbob.fcsdk.android.phone.VideoSurface;
import com.alicecallsbob.fcsdk.android.phone.VideoSurfaceListener;

/**
 * Manages calls (including call state and remote video views) for the InCallActivity.
 * 
 * @author CafeX Communications
 *
 */
public class InCallManager 
{
    /** Logging tag */
    private static final String TAG = "InCallManager";
    
    /** Current call index state key */
    private static final String SAVED_STATE_KEY_CALL_INDEX = "_call_index";
    /** Call hold status state key */
    private static final String SAVED_STATE_KEY_HOLD = "_hold_state";
    /** Call quality state key */
    private static final String SAVED_STATE_KEY_CALL_QUALITY = "_call_quality";
    
    /** The list of active calls */
    private List<? extends Call> activeCalls;
    
    /** Remote video views for calls */
    private List<VideoSurface> videoViews = new ArrayList<VideoSurface>();
    
    /** Call hold status */
    private List<Boolean> callLocallyHeldState = new ArrayList<Boolean>();
    
    /** Call quality */
    private List<Integer> callQuality = new ArrayList<Integer>();
    
    /** Current selected call index */
    private int selectedCallIndex = 0;
    
    /** The InCallActivity */
    private InCallActivity inCallActivity;        
    
    /**
     * Indicates if video surface has been assigned to current call. Indexed by selectedCallIndex/Video surfaceIndex.
     * <p>     	 
     * When this sample app is first started and the first outbound call is made, there is a race between the
     * onSurfaceRenderingStarted callback - calls assignVideoSurfaceToCall() - for adding the VideoSurface to the call, 
     * and the establishment of the call itself done by the InCallActivity CreateOutgoingCallTask async task.
     * <p>
     * If we do have an active call and the video surface has been added, we set a flag for the 
     * InCallActivity CreateOutgoingCallTask async task to know it does not need to assign the video surface.  
     */
    private List<Boolean> callVideoSurfaceAssignmentStatuses = new ArrayList<Boolean>();

	/**
     * Constructor.
     * 
     * @param inCallActivity The InCallActivity
     * @param state Saved state
     */
    public InCallManager(InCallActivity inCallActivity, Bundle state)
    {
        if (state != null) 
        {
            Log.v(TAG, "Restoring data from previous state");
            selectedCallIndex = state
                    .getInt(SAVED_STATE_KEY_CALL_INDEX);
        }
        
        this.inCallActivity = inCallActivity;
    }
    
    /**
     * Restores previously saved state from bundle.
     * 
     * @param state The saved state
     */
    @SuppressWarnings("unchecked")
    public void restoreState(Bundle state)
    {
        callLocallyHeldState = (List<Boolean>) state.getSerializable(SAVED_STATE_KEY_HOLD);
        callQuality = (List<Integer>) state.getSerializable(SAVED_STATE_KEY_CALL_QUALITY);
    }
    
    /**
     * Save state to bundle.
     * 
     * @param bundle The bundle
     */
    public void saveState(Bundle bundle)
    {
        bundle.putInt(SAVED_STATE_KEY_CALL_INDEX, selectedCallIndex);
        bundle.putSerializable(SAVED_STATE_KEY_HOLD, (Serializable) callLocallyHeldState);
        bundle.putSerializable(SAVED_STATE_KEY_CALL_QUALITY, (Serializable) callQuality);
    }
    
    /**
     * 
     * @return the currently selected call
     */
    public Call getCurrentCall()
    {
        if (activeCalls != null 
                && !activeCalls.isEmpty()
                && activeCalls.size() > selectedCallIndex
                && selectedCallIndex >= 0)
        {
            return activeCalls.get(selectedCallIndex);
        }
        
        return null;
    }
    
    /**
     * Gets the call with the specified call id.
     * 
     * @param callId The call id.
     * @return The call with the specified id
     */
    public Call getCall(String callId)
    {
        if (activeCalls != null)
        {
            for (Call call : activeCalls)
            {
                if (callId.equals(call.getCallId()))
                {
                    return call;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 
     * @return all active calls
     */
    public List<? extends Call> getActiveCalls()
    {
        return activeCalls;
    }
    
    /**
     * 
     * @param call
     * @return
     */
    public boolean contains(Call call)
    {
        if (activeCalls != null)
        {
            return activeCalls.contains(call);
        }
        
        return false;
    }
    
    /**
     * Selects a call
     * 
     * @param call the call to select
     */
    public void selectCall(Call call)
    {
        if (call != null)
        {
            selectedCallIndex = activeCalls.indexOf(call);
        }
        else
        {
            selectedCallIndex = -1;
        }
    }
    
    /**
     * Update the active calls.
     * 
     * @param currentCalls The current active calls
     */
    public void updateActiveCalls(List<? extends Call> currentCalls)
    {
        activeCalls = currentCalls;
        
        // If the selected call index is now out of bounds,
        // set to zero
        if (activeCalls != null
                && selectedCallIndex >= activeCalls.size())
        {
            selectedCallIndex = 0;
        }
        
        // Add any additional hold states (if new calls have been created since
        // this method was last called).
        for (int i = callLocallyHeldState.size(); i < activeCalls.size(); i++)
        {
            callLocallyHeldState.add(false);
        }
        
        // Add any additional quality states (if new calls have been created since
        // this method was last called).
        for (int i = callQuality.size(); i < activeCalls.size(); i++)
        {
            callQuality.add(100);
        }
    }
    
    /**
     * Sets the call listener to all active calls.
     * 
     * @param listener The call listener
     */
    public void setCallListener(CallListener listener)
    {
        // If we've got active calls, set this as their listeners
        if ((activeCalls != null) && !activeCalls.isEmpty()) 
        {
            for (final Iterator<? extends Call> callIt = activeCalls.iterator(); callIt.hasNext();) 
            {
                final Call call = callIt.next();
                call.addListener(listener);
            }
        }
    }
    
    /**
     * 
     * @return the number of active calls
     */
    public int getNumberOfActiveCalls()
    {
        return activeCalls.size();
    }
    
    /**
     * 
     * @return the number of remote video surfaces
     */
    public int getNumberOfVideoSurfaces()
    {
        return videoViews.size();
    }
    
    /**
     * Set the remote video surface for the correct call.
     * 
     * The setVideoView method of a call should not be invoked until the video surface has started rendering.
     * (Called from onSurfaceRenderingStarted in this implementation).
     * 
     * @param surface The video surface
     */
    void assignVideoSurfaceToCall(VideoSurface surface)
    {    	
	    	// Set this video surface on the call at the same index
	    	final int surfaceIndex = videoViews.indexOf(surface);
	    	Log.d(TAG, "assignVideoSurfaceToCall() - activeCalls.size=" + activeCalls.size() + " surface index=" + surfaceIndex);
	
	    	if (surfaceIndex != -1 && activeCalls.size() > surfaceIndex)
	    	{        
	    		/*      	 
	    		 * When this sample app is first started and the first outbound call is made, there is a race between the
	    		 * onSurfaceRenderingStarted callback (calls here) for adding the VideoSurface to the call, and the 
	    		 * establishment of the call itself done by the InCallActivity CreateOutgoingCallTask async task.
	    		 * 
	    		 * If we do have an active call and the video surface has been added, we set a flag for the 
	    		 * InCallActivity CreateOutgoingCallTask async task to know it does not need to assign the video surface.
	    		 */ 
	    		final Call call = activeCalls.get(surfaceIndex);               
	    		if (call != null)
	    		{
	    			Log.d(TAG, "assignVideoSurfaceToCall() - setting video surface on CSDK Call object");            	            	            	
	    			call.setVideoView(surface);                
	    			setVideoBeenAssignedToCurrentCall(surfaceIndex);
	    		}
	    		else
	    		{
	    			Log.w(TAG, "assignVideoSurfaceToCall() - Call is null - cannot set video view yet");   
	    		}
	    	}
	    	else
	    	{
	    		Log.d(TAG, "assignVideoSurfaceToCall() - Cannot set video view as no active calls established yet");              
	    	}
    }
    
    /**
     * 
     * @return true if the currently selected call is held locally, false otherwise.
     */
    public boolean isSelectedCallLocallyHeld()
    {
        if (getCurrentCall() != null)
        {
            if (selectedCallIndex < callLocallyHeldState.size())
            {
                final Boolean isCallLocallyHeld = callLocallyHeldState.get(selectedCallIndex);
                
                if (isCallLocallyHeld != null)
                {
                    return isCallLocallyHeld;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Clean up the in-call manager data.
     */
    public void destroy()
    {
	    	if (activeCalls != null)
	    	{
	    		for (Call call : activeCalls)
	    		{
	    			if (call != null)
	    			{
	    				call.setVideoView(null);
	    				call.removeListener(inCallActivity);
	    			}
	    		}
	    	}
	
	    	if (videoViews != null)
	    	{
	    		videoViews.clear();
	    	}        
	
	    	synchronized (callVideoSurfaceAssignmentStatuses)
	    	{
	    		callVideoSurfaceAssignmentStatuses.clear();
	    	}
	
	    	inCallActivity = null;
    }
    
    /**
     * Gets the video surface for a call.
     * 
     * @param call The call
     * @return The video surface for the call
     */
    public VideoSurface getVideoSurfaceForCall(Call call)
    {
        if (videoViews != null)
        {
            final int callIndex = activeCalls.indexOf(call);
            
            return getVideoSurfaceForCall(callIndex);
        }
        
        return null;
    }
    
    /**
     * Gets the video surface for a call index.
     * 
     * @param callIndex The call index
     * @return The video surface for the call at the specified index
     */
    private VideoSurface getVideoSurfaceForCall(int callIndex)
    {
        if (callIndex >= 0)
        {
            if (videoViews.size() > callIndex)
            {
                return videoViews.get(callIndex);
            }
        }
        
        return null;
    }
    
    /**
     * 
     * @return
     */
    public VideoSurface getVideoSurfaceForSelectedCall()
    {
        return getVideoSurfaceForCall(selectedCallIndex);
    }
    
    /**
     * Removes the video surface associated with the specified call.
     * 
     * @param call  The call 
     * @return      True if a video surface was removed.  False if there is no video surface
     *              associated with the call.
     */
    public boolean removeVideoSurfaceForCall(Call call)
    {
        final VideoSurface videoSurface = getVideoSurfaceForCall(call);
        
        if (videoSurface != null)
        {
            call.setVideoView(null);
            
            videoViews.remove(videoSurface);
            return true;
        }
        
        return false;
    }
    
    /**
     * Removes the call (also removes the video surface associated with the call)
     * 
     * @param call the call to remove
     */
    public void removeCall(Call call)
    {
        removeVideoSurfaceForCall(call);
        
        if (activeCalls != null
                && activeCalls.contains(call))
        {
            activeCalls.remove(call);
            selectedCallIndex = 0;
        }
    }
    
    /**
     * Get the call quality of a call.
     * 
     * @param call the call
     * @return the call quality of the call (between 0-100) or -1 if unavailable
     */
    public int getCallQuality(Call call)
    {
        final int callIndex = activeCalls.indexOf(call);
        
        if (callIndex >= 0 && callIndex < callQuality.size())
        {
            return callQuality.get(callIndex);
        }
        
        return -1;
    }
    
    /**
     * Sets the call quality of a call (or does nothing if callQuality.size()
     * is too small to contain the value).
     * 
     * @param call the call
     * @param quality the call quality of the call
     */
    public void setCallQuality(Call call, int quality)
    {
        final int callIndex = activeCalls.indexOf(call);
        
        if (callIndex >= 0 && callIndex < callQuality.size())
        {
            callQuality.set(callIndex, quality);
        }
    }
    
    /**
     * Hold or resume the selected call
     * 
     * @param putOnHold true if the call should be held, false if the call should be resumed
     */
    public void holdSelectedCall(boolean putOnHold)
    {
        if (putOnHold != isSelectedCallLocallyHeld()) 
        {
            callLocallyHeldState.set(selectedCallIndex, putOnHold);

            final Call currentCall = getCurrentCall();
            if (currentCall != null) 
            {
                if (putOnHold) 
                {
                    currentCall.hold();
                } 
                else 
                {
                    currentCall.resume();
                }
            }
        }
    }
    
    /**
     * Generates the video surfaces for remote video.
     * 
     * NOTE: This method only currently supports upto 2 concurrent calls.
     * 
     * @param numberOfCalls The number of active calls
     * @return Root view which contains the remote video surfaces
     */
    public View generateRemoteVideoViews(Phone phone, View videoContainer, 
            VideoSurfaceListener videoSurfaceListener, int numberOfCalls)
    {        
        /**
         * If there is only 1 call, then create a video surface and return that directly.
         */
        if (numberOfCalls == 1)
        {
            final Point screenSize = new Point();
            final Display display = Main.getDisplay();
            screenSize.x = display.getWidth();
            screenSize.y = display.getHeight();
            final PhoneVideoCaptureResolution preferredResolution = phone
                    .getPreferredCaptureResolution();
            final Point remoteSize = new Point(getRemoteVideoViewSize(videoContainer,
                    preferredResolution.getWidth(),
                    preferredResolution.getHeight(), screenSize, numberOfCalls));
            remoteSize.x *= 0.9f;
            remoteSize.y = (int) (remoteSize.x * 0.7f);
            Log.d(TAG, "remoteSize=" + remoteSize);
            VideoSurface videoView;
            if (videoViews.size() > 0 && videoViews.get(0) != null)
            {
                videoView = videoViews.get(0);
                videoView.setDimensions(remoteSize);
                removeViewFromParent(videoView);
            }
            else
            {
                videoView = phone.createVideoSurface(inCallActivity, remoteSize, new VideoSurfaceListenerWrapper(videoSurfaceListener));
                videoView.setAlpha(0);
                
                if (videoViews.size() != 0)
                {
                    videoViews.set(0, videoView);
                }
                else
                {
                    videoViews.add(videoView);
                }
            }
            // Centre the remote video view in the container
            final RelativeLayout.LayoutParams remoteLp = new RelativeLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            remoteLp.addRule(RelativeLayout.CENTER_IN_PARENT);
            videoView.setLayoutParams(remoteLp);

            return videoView;
        }
        // Creates a layout with 2 video surfaces side by side
        else
        {
            final Point screenSize = new Point();
            final Display display = Main.getDisplay();
            screenSize.x = display.getWidth();
            screenSize.y = display.getHeight();
            final PhoneVideoCaptureResolution preferredResolution = phone
                    .getPreferredCaptureResolution();
            final Point remoteSize = new Point(getRemoteVideoViewSize(videoContainer, 
                    preferredResolution.getWidth(),
                    preferredResolution.getHeight(), screenSize, numberOfCalls));
            remoteSize.x *= 0.9f / 2;
            remoteSize.y = (int) (remoteSize.x * 0.7f);
            
            final LinearLayout.LayoutParams linearLayoutLp = new LinearLayout.LayoutParams(
                    LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
            linearLayoutLp.gravity = Gravity.CENTER;
            final LinearLayout linearLayout = new LinearLayout(inCallActivity.getApplicationContext());
            linearLayout.setLayoutParams(linearLayoutLp);
            
            final LinearLayout.LayoutParams panelLayoutLp = new LinearLayout.LayoutParams(
                    LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 0.5f);
            panelLayoutLp.gravity = Gravity.CENTER;
            panelLayoutLp.leftMargin = 5;
            panelLayoutLp.rightMargin = 5;
            
            final LinearLayout leftPanelLayout = new LinearLayout(inCallActivity.getApplicationContext());
            leftPanelLayout.setLayoutParams(panelLayoutLp);
            final LinearLayout rightPanelLayout = new LinearLayout(inCallActivity.getApplicationContext());
            rightPanelLayout.setLayoutParams(panelLayoutLp);
                       
            final LinearLayout.LayoutParams videoLayoutParams = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            videoLayoutParams.gravity = Gravity.CENTER;

            VideoSurface videoViewLeft;
            VideoSurface videoViewRight;
            
            if (videoViews.size() == 2)
            {
                videoViewLeft = videoViews.get(0);
                removeViewFromParent(videoViewLeft);
                videoViewRight = videoViews.get(1);
                removeViewFromParent(videoViewRight);
            }
            else if (videoViews.size() == 1)
            {
                videoViewLeft = videoViews.get(0);
                removeViewFromParent(videoViewLeft);
                videoViewLeft.setDimensions(remoteSize);
                videoViewRight = phone.createVideoSurface(inCallActivity, remoteSize, videoSurfaceListener); 
                videoViews.add(videoViewRight);
            }
            else
            {
                videoViewLeft = phone.createVideoSurface(inCallActivity, remoteSize, videoSurfaceListener);
                videoViewLeft.setAlpha(0);
                videoViews.add(videoViewLeft);
                videoViewRight = phone.createVideoSurface(inCallActivity, remoteSize, videoSurfaceListener); 
                videoViewRight.setAlpha(0);
                videoViews.add(videoViewRight);
            }
                       
            videoViewLeft.setLayoutParams(videoLayoutParams);
            videoViewRight.setLayoutParams(videoLayoutParams);
            
            leftPanelLayout.addView(videoViewLeft);
            rightPanelLayout.addView(videoViewRight);
            
            // Highlight the currently selected call
            if (selectedCallIndex == 0)
            {
                leftPanelLayout.setBackgroundResource(R.color.video_highlight);
            }
            else
            {
                rightPanelLayout.setBackgroundResource(R.color.video_highlight);
            }
            
            linearLayout.addView(leftPanelLayout);
            linearLayout.addView(rightPanelLayout);

            return linearLayout;
        }         
    }
    
    /**
     * @param imageWidth
     *            Width of the incoming video
     * @param imageHeight
     *            Height of the incoming video
     * @param screenSize
     *            Dimensions of the device's screen
     * @return Dimensions of the {@link View} to display the remote video
     */
    public Point getRemoteVideoViewSize(final View videoContainer, final int imageWidth,
            final int imageHeight, final Point screenSize, int numberOfRemoteVideoViews) 
    {

        final boolean landscapeVideo = imageWidth > imageHeight;
        final boolean landscapeOrientation = inCallActivity.getResources().getConfiguration().
                orientation == Configuration.ORIENTATION_LANDSCAPE;
        float videoRatio = 0.0f;       
        
        if (landscapeVideo) 
        {
            videoRatio = (float) imageHeight / (float) imageWidth;
        } 
        else 
        {
            videoRatio = (float) imageWidth / (float) imageHeight;
        }

        Point referenceDimensions = null;
        if (screenSize != null) 
        {
            referenceDimensions = new Point(screenSize);
        } 
        else 
        {
            referenceDimensions = new Point(videoContainer.getWidth() / numberOfRemoteVideoViews,
                    videoContainer.getHeight());
            
            if (!landscapeOrientation)
            {
                // The video container width and height don't seem to reflect the
                // portrait orientation in some instances so flip
                if (videoContainer.getWidth() > videoContainer.getHeight())
                {
                    referenceDimensions = new Point(videoContainer.getHeight() / numberOfRemoteVideoViews,
                            videoContainer.getWidth());
                }
            }
        }
        
        float referenceRatio = 0.0f;
        if (landscapeOrientation) 
        {
            referenceRatio = (float) referenceDimensions.y / (float) referenceDimensions.x;
        } 
        else 
        {
            referenceRatio = (float) referenceDimensions.x / (float) referenceDimensions.y;
        }

        final Point remoteSize = new Point();        
        
        if (landscapeVideo)
        {
            if (referenceRatio >= videoRatio || !landscapeOrientation)
            {
                remoteSize.x = (int) referenceDimensions.x;
                remoteSize.y = (int) (((float) remoteSize.x / imageWidth) * imageHeight);
            }
            else
            {
                remoteSize.y = (int) referenceDimensions.y;
                remoteSize.x = (int) (((float) remoteSize.y / imageHeight) * imageWidth);
            }
        }
        else
        {
            if (referenceRatio >= videoRatio || landscapeOrientation)
            {
                remoteSize.y = (int) referenceDimensions.y;
                remoteSize.x = (int) (((float) remoteSize.y / imageHeight) * imageWidth);
                
            }
            else
            {
                remoteSize.x = (int) referenceDimensions.x;
                remoteSize.y = (int) (((float) remoteSize.x / imageWidth) * imageHeight);
            }
        }

        Log.i(TAG, "in getRemoteVideoViewSize: remote video view dimensions are " + remoteSize.x + "x"
                + remoteSize.y);

        return remoteSize;
    }
    
    /**
     * Remove the specified view from its parent.
     * 
     * @param view the view
     */
    private void removeViewFromParent(View view)
    {
        final ViewParent parent = view.getParent();
        
        if (parent != null)
        {
            ((ViewGroup) parent).removeView(view);
        }
    }
    
    /**
     * Wraps a VideoSurfaceListener in order to call assignVideoSurfaceToCall
     * when a video surface starts rendering.
     * 
     * @author CafeX Communications
     *
     */
    private class VideoSurfaceListenerWrapper implements VideoSurfaceListener
    {
        /** The listener to wrap */
        private final VideoSurfaceListener listener;
        
        public VideoSurfaceListenerWrapper(VideoSurfaceListener listener)
        {
            this.listener = listener;
        }
        
        @Override
        public void onFrameSizeChanged(int width, int height,
                VideoSurface.Endpoint endpoint, VideoSurface videoView)
        {
            if (listener != null)
            {
                listener.onFrameSizeChanged(width, height, endpoint, videoView);  
            }
        }

        @Override
        public void onSurfaceRenderingStarted(VideoSurface videoSurface) 
        {
            if (videoViews.contains(videoSurface))
            {
                assignVideoSurfaceToCall(videoSurface);
            }
            if (listener != null)
            {
                listener.onSurfaceRenderingStarted(videoSurface);
            }
        }


        
    }
    
    boolean hasVideoBeenAssignedToCurrentCall() {
    	
    	synchronized (callVideoSurfaceAssignmentStatuses)
    	{
	    	if (callVideoSurfaceAssignmentStatuses.size() <= selectedCallIndex)
	    	{
	    		// nothing in here yet, so video cannot have been assigned already
	    		return false;
	    	}
			return callVideoSurfaceAssignmentStatuses.get(selectedCallIndex);
    	}
	}

    void setVideoBeenAssignedToCurrentCall(int callIndex) {
		
    	synchronized (callVideoSurfaceAssignmentStatuses)
    	{
		    callVideoSurfaceAssignmentStatuses.add(callIndex, true);
    	}
	}
}
