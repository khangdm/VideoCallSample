package com.alicecallsbob.csdk.android.sample;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

/**
 *
 */
public final class LoginHandler
{
    /** Error code telling us that we were unable to make a valid connection. */
    public static final int ERROR_CONNECTION_FAILED = -1;
    /** Error code telling us that something went wrong with the login. */
    public static final int ERROR_LOGIN_FAILED = -2;

    /** Identifier String for LogCat output. */
    private static final String TAG = "LoginHandler";

    /** Content-Type header name for REST requests */
    private static final String CONTENT_TYPE_HEADER_NAME = "Content-Type";
    /** Content-Type header value for REST requests */
    private static final String CONTENT_TYPE_HEADER_VALUE = "application/json";
    /** User-Agent header name for REST requests. */
    private static final String USER_AGENT_HEADER_NAME = "User-Agent";
    /** User-Agent header value for REST requests. */
    private static final String USER_AGENT_HEADER_VALUE =
            "Android" + Build.DEVICE + "/" + Build.VERSION.CODENAME;
    /** JSON property holding the logged in user's SIP domain */
    private static final String VOICE_DOMAIN_PROPERTY = "voiceDomain";
    /** JSON property holding the logged in user's SIP username */
    private static final String VOICE_USER_PROPERTY = "voiceUser";
    /** JSON property holding the session ID */
    private static final String SESSION_ID_PROPERTY = "sessionid";

    /** size of HTTP read buffer. */
    private static final int READ_BUF_SIZE = 256;

    /** How long do we wait for the connection attempt to succeed before we flag a failure. */
    private static final int    CONNECTION_TIMEOUT    = 10000;

    /** Connection used to communicate with the server. */
    private HttpURLConnection mHttpConnection;
    /** Code received from the server in response to a given request. */
    private int mResponseCode;
    /** The body of the response from the server, could be a message or some HTML. */
    private String mResponseBody;

    /**
     * Private default constructor to prevent instantiation.
     */
    private LoginHandler()
    {
    }

    /**
     * Create an HTTP connection to the given address.
     *
     * @param address Address we want to make a connection to
     * @return A valid {@link HttpURLConnection}, or <code><b>null</b></code> if the connection
     *     failed.
     */
    private static HttpURLConnection createConnection(final String address)
    {
        return createConnection(address, true);
    }

    private static HttpURLConnection createConnection(final String address, final boolean retry)
    {
        Log.d(TAG, "createConnection");

        // attempt authentication against a network service.
        URL url = null;
        try
        {
            url = new URL(address);
        }
        catch (MalformedURLException e2)
        {
            e2.printStackTrace();
            return null;
        }

        Log.d(TAG, "URL: " + url.toExternalForm());

        HttpURLConnection conn = null;
		try 
		{
			conn = getHttpOrHttpsConnection(url);
		}
		catch (Exception e) 
		{
            Log.e(TAG, "Could not getHttpOrHttpsConnection: " + url + " - " + e.getMessage());
		}

        try
        {
            conn.setDoOutput(true);
            conn.setRequestProperty(USER_AGENT_HEADER_NAME, USER_AGENT_HEADER_VALUE);
            conn.setRequestProperty(CONTENT_TYPE_HEADER_NAME, CONTENT_TYPE_HEADER_VALUE);
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.connect();
        }
        catch (SocketTimeoutException ste)
        {
            if (retry)
            {
                return handleConnectionFailed(address, url, ste);
            }
            else
            {
                Log.w(TAG, "Connection timed out, we waited for 10 seconds. Is "
                        + "the device connected to the network correctly?");
                return null;
            }
        }
        catch (ConnectException ce)
        {
            if (retry)
            {
                return handleConnectionFailed(address, url, ce);
            }
            else
            {
                Log.w(TAG, "Server refused the connection");
                return null;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }

        return conn;
    }

    private static HttpURLConnection getHttpOrHttpsConnection(URL url) throws IOException, NoSuchAlgorithmException, KeyManagementException
    {
    	HttpURLConnection conn = null;
    	
    	if (url.getProtocol().equals("http"))
        {
        	conn = (HttpURLConnection) url.openConnection();
        }
        else
        {
            SSLContext context = SSLContext.getInstance("TLSv1.2");
			context.init(null, new TrustManager [] { new TrustAllCerts() }, null);

			HttpsURLConnection.setDefaultHostnameVerifier(new NullHostNameVerifier());
			HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());

			conn = (HttpsURLConnection) url.openConnection();
        }
    	
    	return conn;
	}

	private static HttpURLConnection handleConnectionFailed(String address, URL url, IOException cause)
    {
        final String host = url.getHost();
        try
        {
            final InetAddress[] allAddresses = InetAddress.getAllByName(host);
            if (allAddresses.length > 1)
            {
                /* If the gateway is running in a clustered environment, and the
                node that was selected first by DNS is down, the default address
                resolution will need to be bypassed and the raw IP of each node
                tried individually. For a single-box environment, just fail
                normally. */

                HttpURLConnection connection = null;
                for (InetAddress retryAddress : allAddresses)
                {
                    connection = createConnection(address.replaceFirst(host, retryAddress.getHostAddress()), false);
                    if (connection != null)
                    {
                        return connection;
                    }
                }
            }
    
            Log.e(TAG, "Could not connect: " + cause.getMessage());
            cause.printStackTrace();
            return null;
        }
        catch (UnknownHostException e)
        {
            /* This shouldn't happen, as any UnknownHostException should have
            been thrown earlier, but log it in case it does. */
            Log.e(TAG, "Could not resolve address " + address);
            return null;
        }
    }

    /**
     * Send a POST request using the given connection and sending the given content.
     * @param content {@link String} containing the data that we want to send.
     * @return <code><b>true</b></code> if the POST succeeded, <code><b>false</b></code> otherwise.
     */
    private boolean post(final String content)
    {
        Log.d(TAG, "POST: " + content);

        try
        {
            final OutputStream out = mHttpConnection.getOutputStream();
            out.write(content.getBytes());
            out.flush();
        }
        catch (IOException e1)
        {
            e1.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Opens an input stream on the specified connection, reads all the available data, closes
     * the stream, returns the data.
     * @param in The {@link InputStream} to read from
     * @return The data read from the stream, as a {@link String}
     * @throws IOException
     */
    private static String readAllFromConnection(final InputStream in) throws IOException
    {
        try
        {
            final byte[] data = new byte[READ_BUF_SIZE];
            int len = 0;
            final StringBuffer raw = new StringBuffer();

            while (-1 != (len = in.read(data)))
            {
                raw.append(new String(data, 0, len));
            }

            return raw.toString();
        }
        finally
        {
            in.close();
        }
    }

    /**
     * Get the response from the given connection.
     *
     * @return <code><b>true</b></code> if we got a valid response, <code><b>false</b></code>
     *     otherwise.
     */
    private boolean getResponse()
    {
        try
        {
            mResponseCode = mHttpConnection.getResponseCode();
            Log.d(TAG, "Got response: " + mResponseCode + " " + mHttpConnection.getResponseMessage());

            final InputStream in = mHttpConnection.getInputStream();
            mResponseBody = readAllFromConnection(in);
        }
        catch (Exception ex)
        {
            final InputStream es = mHttpConnection.getErrorStream();
            if (es != null)
            {
                Log.e(TAG, "getResponse errored");
                return false;
            }

            ex.printStackTrace();

            return false;
        }
        finally
        {
            mHttpConnection.disconnect();
        }

        return true;
    }

    /**
     *
     * @param message A {@link String} containing the message we want to send.
     * @return <code><b>true</b></code> if the message was sent successfully,
     *     <code><b>false</b></code> if not.
     */
    private boolean doSendMessage(final String message)
    {
        // Send the call connect request
        if (!post(message))
        {
            Log.e(TAG, "Failed to post the message");
            mHttpConnection.disconnect();
            return false;
        }

        // What's the answer? Are we good?
        if (!getResponse())
        {
            Log.e(TAG, "Failed to get the response");
            return false;
        }

        if (mResponseCode != HttpURLConnection.HTTP_OK)
        {
            Log.w(TAG, "Unexpected response code: " + mResponseCode);
            return false;
        }

        return true;
    }

    /**
     * Create a connection to the given address and send the given message.
     *
     * @param url The url to connect to
     * @param message The message to send
     * @return 0 if the message was sent successfully, or one of our error codes.
     */
    private int sendMessage(final String url, final String message)
    {
        Log.d(TAG, "sendMessage");

        mHttpConnection = createConnection(url);
        if (mHttpConnection == null)
        {
            Log.e(TAG, "Failed to create a connection to '" + url + "'");
            return ERROR_CONNECTION_FAILED;
        }

        if (doSendMessage(message))
        {
            return 0;
        }
        else
        {
            return ERROR_LOGIN_FAILED;
        }
    }

    /**
     * Login to the web-app server passing the given data. Create a connection to the server,
     * send the POST request and read the response to get the session key.
     * @param url The url of the server we want to login to
     * @param data The data we want to login with
     * @return A {@link Bundle} that either contains an <code>int</code> error code, or a valid
     *     <code>String</code> session ID.
     */
    public static Bundle login(final String url, final String data)
    {
        Log.d(TAG, "login");

        final LoginHandler handler = new LoginHandler();

        final Bundle ret = new Bundle();

        final int loginOutcome = handler.sendMessage(url, data);
        if (loginOutcome < 0)
        {
            ret.putInt(LoginActivity.DATA_KEY_ERROR, loginOutcome);
            return ret;
        }

        if (TextUtils.isEmpty(handler.mResponseBody))
        {
            ret.putInt(LoginActivity.DATA_KEY_ERROR, ERROR_LOGIN_FAILED);
            return ret;
        }

        Log.d(TAG, "login response: " + handler.mResponseBody);

        // Parse the response body for the session key
        final String key;
        String voiceUser;
        try
        {
            final JSONObject responseData = new JSONObject(handler.mResponseBody);
            key = responseData.getString(SESSION_ID_PROPERTY);

            try
            {
                final String user = responseData.getString(VOICE_USER_PROPERTY);
                final String domain = responseData.getString(VOICE_DOMAIN_PROPERTY);

                if (user != null && domain != null
                        && !user.isEmpty() && !domain.isEmpty()
                        && !user.equals("null") && !domain.equals("null"))
                {
                    voiceUser = user + "@" + domain;
                }
                else
                {
                    voiceUser = null;
                }
            }
            catch (JSONException e)
            {
                voiceUser = null;
            }
        }
        catch (JSONException e)
        {
            Log.w(TAG, "Error parsing JSON string " + handler.mResponseBody + " - " + e.getMessage());
            ret.putInt(LoginActivity.DATA_KEY_ERROR, ERROR_LOGIN_FAILED);
            return ret;
        }

        Log.v(TAG, "Encoded session key = " + key);
        ret.putString(LoginActivity.DATA_KEY_SESSION_ID, key);

        if (voiceUser != null)
        {
            Log.v(TAG, "Encoded voice user = " + voiceUser);
            ret.putString(LoginActivity.DATA_KEY_VOICE_ADDRESS, voiceUser);
        }
        else
        {
            Log.v(TAG, "No encoded voice user");
        }

        return ret;
    }

    /**
     *
     * @param address The address that we want to send our logout message to.
     */
    public static void logout(final String address)
    {
        Log.d(TAG, "logout");

        new Thread(new Runnable()
        {
            public void run()
            {
                try
                {
                    final HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
                    connection.setRequestMethod("DELETE");
                    connection.connect();
                    connection.disconnect();
                }
                catch (IOException e)
                {
                    Log.i(TAG, "Sending logout message failed: " + e.getLocalizedMessage());
                }
            }
        }, "Logging out").start();
    }
}
