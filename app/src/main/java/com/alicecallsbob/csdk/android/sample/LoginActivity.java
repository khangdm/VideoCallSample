package com.alicecallsbob.csdk.android.sample;

import java.util.HashMap;
import org.json.JSONObject;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class LoginActivity extends Activity {
    public static final String TAG = "LoginActivity";

    public static final String ADDRESS = "https://113.161.71.83:8443/csdk-sample/SDK/login";

    public static final String DATA_KEY_SESSION_ID = "_session_id";
    public static final String DATA_KEY_VOICE_ADDRESS = "_voice_address";
    public static final String DATA_KEY_ERROR = "_error";

    private UserLoginTask mAuthTask;

    private User mUser;

    private static boolean mAlive = false;


    protected String mAddress = "https://113.161.71.83:8443/csdk-sample/SDK/login";

    private final View.OnClickListener mOnLoginButtonClick =
            v -> goLogin();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        mAlive = true;

        setContentView(R.layout.activity_login);
        findViewById(R.id.loginBtn).setOnClickListener(mOnLoginButtonClick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mAlive = false;
    }

    public static boolean isAlive() {
        return mAlive;
    }

    protected final void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }
        mAuthTask = new UserLoginTask();
        mAuthTask.execute();
    }

    private class UserLoginTask extends AsyncTask<Void, Void, Bundle> {
        private String key = null;

        @Override
        protected Bundle doInBackground(final Void... params) {
            final Bundle data = LoginHandler.login(mAddress, getLoginFormData());
            final String sessionID;
            if (data.containsKey(DATA_KEY_SESSION_ID)) {
                sessionID = data.getString(DATA_KEY_SESSION_ID);
            } else {
                return data;
            }

            if (sessionID != null) {
                this.key = sessionID;
            }

            return data;
        }

        @Override
        protected void onPostExecute(final Bundle loginData) {
            Log.v(TAG, "Login success");
            Log.e(TAG, "onPostExecute: " + key);
            startActivity(new Intent(getApplicationContext(), Main.class)
                    .putExtra(Main.DATA_SESSION_KEY, key)
                    .putExtra(Main.DATA_UC_USE_COOKIES, true)
                    .putExtra(Main.DATA_LOGOUT_URL, ADDRESS + getString(R.string.logout, key))
                    .putExtra(Main.DATA_UC_SUPPORTS_RENEG, true));

        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
        }
    }

    protected final String getLoginFormData() {
        final HashMap<String, String> formData = new HashMap<String, String>();
        formData.put("username", "1001");
        formData.put("password", "123");
        return new JSONObject(formData).toString();
    }

    protected final void goLogin() {
        attemptLogin();
    }
}
