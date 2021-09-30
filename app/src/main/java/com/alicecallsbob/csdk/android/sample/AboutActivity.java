package com.alicecallsbob.csdk.android.sample;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.alicecallsbob.fcsdk.android.SdkVersion;

/**
 * 
 */
public class AboutActivity extends Activity
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        
        final TextView sdkVersion = (TextView) findViewById(R.id.sdk_version);
        sdkVersion.setText(SdkVersion.SDK_VERSION_NUMBER);
    }
}
