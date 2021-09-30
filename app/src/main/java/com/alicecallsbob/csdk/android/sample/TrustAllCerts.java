package com.alicecallsbob.csdk.android.sample;

import javax.net.ssl.X509TrustManager;

/**
 * Create a trust manager that does not validate the certificate chain.
 * 
 * This trust manager should only be used for testing.
 *  
 * A better solution would be to implement a trust manager that enhances the default Android X509 
 * TrustManager behaviour by providing alternative authentication logic when the default TrustManager 
 * fails.
 * 
 * http://docs.oracle.com/javase/1.5.0/docs/guide/security/jsse/JSSERefGuide.html#X509TrustManager
 * 
 */
public class TrustAllCerts implements X509TrustManager
{
	public java.security.cert.X509Certificate[] getAcceptedIssuers() 
	{
		return null;
    }
	
    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) 
    {
    } 
    
    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) 
    {
    } 
}
