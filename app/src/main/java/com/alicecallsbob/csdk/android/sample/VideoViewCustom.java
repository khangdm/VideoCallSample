package com.alicecallsbob.csdk.android.sample;

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;

import com.alicecallsbob.fcsdk.android.phone.VideoSurface;

import org.webrtc.VideoFrame;

public class VideoViewCustom extends VideoSurface {
    protected VideoViewCustom(Context context) {
        super(context);
    }

    protected VideoViewCustom(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setEndpoint(Endpoint endpoint) {

    }

    @Override
    public void setDimensions(Point point) {

    }

    @Override
    public void destroy() {

    }

    @Override
    public void onFrame(VideoFrame frame) {
        super.onFrame(frame);
        Log.e("TAG", "onFrame: custom");
    }
}
