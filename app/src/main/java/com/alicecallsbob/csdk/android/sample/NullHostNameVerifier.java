package com.alicecallsbob.csdk.android.sample;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import android.util.Log;

public class NullHostNameVerifier implements HostnameVerifier {

    public boolean verify(String hostname, SSLSession session) {
        Log.i("NullHostNameVerifier" + this, "Approving certificate for " + hostname);
        return true;
    }
}			
