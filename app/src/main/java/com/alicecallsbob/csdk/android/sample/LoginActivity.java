package com.alicecallsbob.csdk.android.sample;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONObject;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity which displays a login screen to the user, offering registration as well.
 */
public class LoginActivity extends Activity {
    public static final String TAG = "LoginActivity";

    public static final String DATA_KEY_SESSION_ID = "_session_id";
    public static final String DATA_KEY_VOICE_ADDRESS = "_voice_address";
    public static final String DATA_KEY_ERROR = "_error";

    private static final String PREFERENCES_HOST_NAME = "hostname";

    private static final String PREFERENCES_ADDRESS = "address";

    private static final String PREFERENCES_SECURE = "secure";

    private static final String PREFERENCES_USE_COOKIES = "useCookies";

    private static final String PREFERENCES_USERNAME = "username";

    private static final String PREFERENCES_PASSWORD = "password";

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask;

    private User mUser;

    private static boolean mAlive = false;

    /*
     * UI components.
     */
    protected EditText mHostNameView;
    protected EditText mAddressBox;
    protected CheckBox mSecureCheckBox;
    protected CheckBox mUseCookiesCheckBox;
    protected CheckBox mSupportRenegotiationBox;
    private EditText mUsernameView;
    private EditText mPasswordView;
    private View mFormView;
    private View mStatusView;

    protected String mHost;
    protected String mAddress;
    protected boolean mUseCookies;

    protected boolean mCustomAddressEntered = false;

    /**
     * Listener called when the user presses the 'done' button on the soft
     * keyboard.
     */
    private final EditText.OnEditorActionListener mOnHostNameDoneListener =
            new EditText.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    boolean handled = false;
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        handled = true;
                        updateFullAddress();
                        hideSoftKeyboard();
                    }

                    return handled;
                }
            };

    /**
     * Listener called when the host name edit box gains or loses focus.
     */
    private final View.OnFocusChangeListener mOnHostNameFocusChanged =
            new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    // If this edit box has lost focus, update the full address text
                    if (!hasFocus) {
                        updateFullAddress();
                    }
                }
            };

    /**
     * Listener called when the user presses the 'done' button on the soft
     * keyboard.
     */
    private final EditText.OnEditorActionListener mOnAddressBoxDoneListener =
            new EditText.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    boolean handled = false;
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        handled = true;
                        updateHostFromAddress();
                        hideSoftKeyboard();
                    }

                    return handled;
                }
            };

    /**
     * Listener called when the address edit box gains or loses focus.
     */
    private final View.OnFocusChangeListener mOnAddressBoxFocusChanged =
            new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean hasFocus) {
                    // If this edit box has lost focus, update the host name text box
                    if (!hasFocus) {
                        updateHostFromAddress();
                    }
                }
            };

    /**
     * Listener for the secure CheckBox, when the checked state changes.
     */
    private final CompoundButton.OnCheckedChangeListener
            mOnSecureCheckedChanged = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView,
                                     boolean isChecked) {
            if (!mCustomAddressEntered) {
                updateFullAddress();
            }
            mCustomAddressEntered = false;
        }
    };

    /**
     * Listener for the Use Cookies CheckBox, when the checked state changes.
     */
    private final CompoundButton.OnCheckedChangeListener
            mOnUseCookiesCheckedChanged = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView,
                                     boolean isChecked) {
            mUseCookies = isChecked;
            //Set a default cookie manager so any returned cookies from login
            //can be resent to the server on subsequent connections.
            CookieHandler cookieHandler = CookieHandler.getDefault();
            if (isChecked) {
                if (cookieHandler == null) {
                    cookieHandler = new CookieManager();
                    CookieHandler.setDefault(cookieHandler);
                }
            } else {
                if (cookieHandler != null) {
                    CookieHandler.setDefault(null);
                }
            }
        }
    };

    /**
     * Button click listener for the login button.
     */
    private final View.OnClickListener mOnLoginButtonClick =
            new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    // Update the host name and address before we continue
                    mHost = mHostNameView.getText().toString().trim();
                    mAddress = mAddressBox.getText().toString().trim();

                    goLogin();
                }
            };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        mAlive = true;

        setContentView(R.layout.activity_login);

        mHost = getString(R.string.host_name_hint);
        mAddress = getString(R.string.host_address_insecure, mHost);

        /*
         * Set up the login form.
         */
        // Host name
        mHostNameView = (EditText) findViewById(R.id.loginHostNameBox);
        mHostNameView.setOnEditorActionListener(mOnHostNameDoneListener);
        mHostNameView.setOnFocusChangeListener(mOnHostNameFocusChanged);
        mHostNameView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHostNameView.setError(null);
            }
        });
        // Host address
        mAddressBox = (EditText) findViewById(R.id.loginAddressBox);
        mAddressBox.setText(mAddress);
        mAddressBox.setOnEditorActionListener(mOnAddressBoxDoneListener);
        mAddressBox.setOnFocusChangeListener(mOnAddressBoxFocusChanged);
        mAddressBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAddressBox.setError(null);
            }
        });
        // Secure checkbox
        mSecureCheckBox = (CheckBox) findViewById(R.id.loginSecureBox);
        mSecureCheckBox.setChecked(false);
        mSecureCheckBox.setOnCheckedChangeListener(mOnSecureCheckedChanged);

        // Cookies check box
        mUseCookiesCheckBox = (CheckBox) findViewById(R.id.loginCookiesBox);
        mUseCookiesCheckBox.setChecked(false);
        mUseCookiesCheckBox.setOnCheckedChangeListener(mOnUseCookiesCheckedChanged);

        mSupportRenegotiationBox = (CheckBox) findViewById(R.id.loginRenegotiationBox);
        mSupportRenegotiationBox.setChecked(true);

        // Username
        mUsernameView = (EditText) findViewById(R.id.username);
        // Password
        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView textView, final int id,
                                          final KeyEvent keyEvent) {
                if ((id == R.id.login) || (id == EditorInfo.IME_NULL)) {
                    attemptLogin();
                    return true;
                }

                return false;
            }
        });
        // Login button
        findViewById(R.id.loginBtn).setOnClickListener(mOnLoginButtonClick);

        mFormView = findViewById(R.id.login_form);
        mStatusView = findViewById(R.id.login_status);

        // Save parameters
        SharedPreferences settings = getPreferences(0);
        String hostName = settings.getString(PREFERENCES_HOST_NAME, "");
        mHostNameView.setText(hostName);
        String address = settings.getString(PREFERENCES_ADDRESS, mAddress);
        mAddressBox.setText(address);
        boolean secure = settings.getBoolean(PREFERENCES_SECURE, false);
        mSecureCheckBox.setChecked(secure);
        boolean useCookies = settings.getBoolean(PREFERENCES_USE_COOKIES, false);
        mUseCookiesCheckBox.setChecked(useCookies);
        String username = settings.getString(PREFERENCES_USERNAME, "");
        mUsernameView.setText(username);
        String password = settings.getString(PREFERENCES_PASSWORD, "");
        mPasswordView.setText(password);
    }

    @Override
    protected void onStop() {
        super.onStop();

        SharedPreferences settings = getPreferences(0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PREFERENCES_HOST_NAME, mHostNameView.getText().toString());
        editor.putString(PREFERENCES_ADDRESS, mAddressBox.getText().toString());
        editor.putBoolean(PREFERENCES_SECURE, mSecureCheckBox.isChecked());
        editor.putBoolean(PREFERENCES_USE_COOKIES, mUseCookiesCheckBox.isChecked());
        editor.putString(PREFERENCES_USERNAME, mUsernameView.getText().toString());
        editor.putString(PREFERENCES_PASSWORD, mPasswordView.getText().toString());

        editor.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensure that the login details view is visible and not the progress spinner
        showProgress(false);
        mHostNameView.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mAlive = false;
    }

    /**
     * @return true if this Activity is alive (i.e. has been created, with onCreate(), and
     * hasn't been destroyed, with onDestroy()) and false if not.
     */
    public static boolean isAlive() {
        return mAlive;
    }

    /**
     * Attempts to sign in or register the account specified by the login form. If there are
     * form errors (invalid email, missing fields, etc.), the errors are presented and no actual
     * login attempt is made.
     */
    protected final void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mAddressBox.setError(null);
        mUsernameView.setError(null);
        mPasswordView.setError(null);

        boolean cancel = false;
        View focusView = null;

        // Has the user edited the host name with a valid host
        if (TextUtils.isEmpty(mHost) || mHost.equals(getString(R.string.host_name_hint))) {
            mHostNameView.setError(getString(R.string.error_valid_host_required));
            focusView = mHostNameView;
            cancel = true;
        } else {
            // Does the host name match the address portion?
            final String host = getHostNameFromAddress(mAddress);
            if (!host.equals(mHost)) {
                mHostNameView.setError(getString(R.string.error_host_and_address_not_matched));
                focusView = mHostNameView;
                cancel = true;
            }
        }

        // Store values at the time of the login attempt.
        final String username = mUsernameView.getText().toString();
        final String password = mPasswordView.getText().toString();

        // Check for an empty user name
        if (!cancel && TextUtils.isEmpty(username)) {
            mUsernameView.setError(getString(R.string.error_field_required));
            focusView = mUsernameView;
            cancel = true;
        }

        // Check for an empty password.
        if (!cancel && TextUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_field_required));
            focusView = mPasswordView;
            cancel = true;
        }

        if (cancel) {
            // There was an error, don't attempt login and focus the first form field with an error.
            focusView.requestFocus();
        } else {
            mUser = User.create(username, password);
            /**
             * Request the appropriate permissions for camera and microphone access.
             */
            ArrayList<String> requiredPermissions = new ArrayList<String>();

            if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.CAMERA);
            }
            if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (requiredPermissions.isEmpty()) {
                /**
                 * Show a progress spinner, and kick off a background task to perform the user login
                 * attempt.
                 */
                showProgress(true);
                mAuthTask = new UserLoginTask();
                mAuthTask.execute();
            } else {
                /**
                 * Request the missing permissions. The attempt to login will be continued by the
                 * onRequestPermissionsResult callback if all permissions are granted.
                 */
                ActivityCompat.requestPermissions(this, requiredPermissions.toArray(new String[0]), 0);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        /**
         * We should have had permissions Manifest.permission.CAMERA and
         * Manifest.permission.RECORD_AUDIO granted. Make sure neither
         * of these have been denied. Note that there could be situations
         * where only one permission was requested (because the other
         * permission was already in place) which need to be handled.
         */
        boolean ok = true;
        for (int index = 0; index < permissions.length; index++) {
            if (permissions[index].equals(Manifest.permission.CAMERA) &&
                    grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                ok = false;
            }
            if (permissions[index].equals(Manifest.permission.RECORD_AUDIO) &&
                    grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                ok = false;
            }
        }
        if (ok) {
            /*
             * Show a progress spinner, and kick off a background task to perform the user login
             * attempt.
             */
            showProgress(true);
            mAuthTask = new UserLoginTask();
            mAuthTask.execute();
        } else {
            Toast.makeText(this.getApplicationContext(),
                    "Access to camera and microphone are required to login. Please try again and grant the permissions.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Shows the progress UI and hides the login form.
     *
     * @param show Pass in true to show the 'in-progress' view, false to hide it.
     */
    private void showProgress(final boolean show) {
        /*
         * The ViewPropertyAnimator APIs are not available, so simply show and
         * hide the relevant UI components.
         */
        mStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
        mFormView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate the user.
     */
    private class UserLoginTask extends AsyncTask<Void, Void, Bundle> {
        /**
         * SessionId provided by the gateway
         */
        private String key = null;

        @Override
        protected Bundle doInBackground(final Void... params) {
            Log.v(TAG, "doInBackground");

            removeExistingCookies();

            final Bundle data = LoginHandler.login(mAddress, getLoginFormData());
            final String sessionID;
            if (data.containsKey(DATA_KEY_SESSION_ID)) {
                sessionID = data.getString(DATA_KEY_SESSION_ID);
            } else {
                return data;
            }

            if (sessionID != null) {
                Log.v(TAG, "login key acquired successfully");
                this.key = sessionID;

                if (data.containsKey(DATA_KEY_VOICE_ADDRESS)) {
                    mUser.setAddress(data.getString(DATA_KEY_VOICE_ADDRESS));
                }
            }

            return data;
        }

        @Override
        protected void onPostExecute(final Bundle loginData) {
            Log.v(TAG, "onPostExecute");

            mAuthTask = null;
            final Integer loginResponse = loginData.getInt(DATA_KEY_ERROR);

            if (loginResponse == null || loginResponse == 0 || 1 == 1) {
                Log.v(TAG, "Login success");
                boolean supportsRenegotiation = mSupportRenegotiationBox.isChecked();
                Bundle data = new Bundle();

                startActivity(new Intent(getApplicationContext(), Main.class)
                        .putExtra(Main.DATA_SESSION_KEY, "key")
                        .putExtra(Main.DATA_UC_USE_COOKIES, "mUseCookies")
                        .putExtra(Main.DATA_LOGOUT_URL,  "mAddress + getString(R.string.logout, key)")
                        .putExtra(Main.DATA_UC_SUPPORTS_RENEG, "supportsRenegotiation"));
            } else {
                switch (loginResponse) {
                    case LoginHandler.ERROR_CONNECTION_FAILED:
                        showProgress(false);
                        Utils.logAndToast(LoginActivity.this, TAG, Log.ERROR,
                                getString(R.string.login_failed_to_connect));
                        break;

                    case LoginHandler.ERROR_LOGIN_FAILED:
                        showProgress(false);
                        Utils.logAndToast(LoginActivity.this, TAG, Log.ERROR,
                                getString(R.string.login_failed_to_login));
                        break;

                    default:
                        break;
                }
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }

    /**
     * @return
     */
    protected final String getLoginFormData() {
        final HashMap<String, String> formData = new HashMap<String, String>();
        formData.put("username", mUser.getName());
        formData.put("password", mUser.getPassword());
        return new JSONObject(formData).toString();
    }

    /**
     *
     */
    protected final void goLogin() {
        hideSoftKeyboard();
        attemptLogin();
    }

    /**
     * Hide the soft input window from the context of the window that is currently accepting input.
     */
    protected void hideSoftKeyboard() {
        final InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mFormView.getWindowToken(), 0);
    }

    /**
     * Pick out the host name from the address.
     *
     * @param address
     * @return Host name portion of the address, or null
     */
    protected String getHostNameFromAddress(final String address) {
        String host = null;

        try {
            host = new URI(address).getHost();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        if (TextUtils.isEmpty(host)) {
            Utils.logAndToast(this, TAG, Log.WARN, "Invalid host name");
        }

        return host;
    }

    /**
     * Update the text in the full address edit box to use the host name from
     * the host name edit box.
     */
    protected void updateFullAddress() {
        mHost = mHostNameView.getText().toString().trim();

        if (mSecureCheckBox.isChecked()) {
            mAddress = getString(R.string.host_address_secure, mHost);
        } else {
            mAddress = getString(R.string.host_address_insecure, mHost);
        }

        mAddressBox.setText(mAddress);
    }

    /**
     * The user has edited the address box text, so update the host name edit
     * box text and the secure check box.
     */
    protected void updateHostFromAddress() {
        mAddress = mAddressBox.getText().toString().trim();
        mCustomAddressEntered = true;

        // Pick out the host name from the address
        mHost = getHostNameFromAddress(mAddress);
        mHostNameView.setText(mHost);

        // Is is secure?
        mSecureCheckBox.setChecked(mAddress.startsWith("https"));
        // Should we be ensuring that the port is 8443 and not 8080?
    }

    public static void removeExistingCookies() {
        CookieManager cookieManager = (CookieManager) CookieHandler.getDefault();
        if (cookieManager != null) {
            CookieStore store = cookieManager.getCookieStore();
            store.removeAll();
        }

    }
}
