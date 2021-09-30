package com.alicecallsbob.csdk.android.sample;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

public final class User
{
	private static final String TAG = "User";

	/** Key used to save/restore the user name when the Activity is recreated. */
	private static final String SAVED_STATE_KEY_NAME = "_user_name";
	/** Key used to save/restore the user's password when the Activity is recreated. */
	private static final String SAVED_STATE_KEY_PASSWORD = "_user_password";
	/** */
	private static final String SAVED_STATE_KEY_ADDRESS = "_user_address";

	private static User INSTANCE = null;

	/** The name the user used to login. */
	private final String mName;
	private final String mPassword;
	/** The sip address (phone number). */
	private String mAddress;

	/**
	 * Private constructor to prevent external creation.
	 *
	 * @param username
	 * @param password
	 */
	private User(final String username, final String password)
	{
		Log.d(TAG, "Constructor");
		mName = username;
		mPassword = password;
	}

	/**
	 *
	 * @param username
	 * @param password
	 * @return
	 */
	public static User create(final String username, final String password)
	{
		if ((INSTANCE == null)
	        || !INSTANCE.getName().equals(username)
	        || !INSTANCE.getPassword().equals(password))
		{
			INSTANCE = new User(username, password);
		}

		return INSTANCE;
	}

	/**
	 *
	 */
	public static void destroy()
	{
		INSTANCE = null;
	}

	/**
	 *
	 * @return
	 */
	public static User getInstance()
	{
		return INSTANCE;
	}

	public String getName()
	{
		return mName;
	}

	/**
	 *
	 * @return
	 */
	public String getPassword()
	{
		return mPassword;
	}

	public String getAddress()
	{
		return mAddress;
	}

	/**
	 *
	 * @param address
	 */
	public void setAddress(final String address)
	{
		mAddress = address;
	}

	/**
	 * @param context A Context we can use to get the String resources.
	 * @return A formatted String containing the user name and number/address.
	 */
	public String getNameAndNumberTitle(final Context context)
	{
		String name = getName();
		if (TextUtils.isEmpty(name))
		{
			name = context.getString(R.string.unknownName);
		}
		String phoneNumber = getAddress();
		if (TextUtils.isEmpty(phoneNumber))
		{
			phoneNumber = context.getString(R.string.unknownAddress);
		}

		return context.getString(R.string.userTitle, name, phoneNumber);
	}

	/**
	 * Save the state of the user when the current Activity is destroyed before being re-created,
	 * e.g. if the device is rotated.
	 *
	 * @param savedStateData
	 */
	public void saveState(final Bundle savedStateData)
	{
		Log.v(TAG, "save state");
		savedStateData.putString(SAVED_STATE_KEY_NAME, mName);
		savedStateData.putString(SAVED_STATE_KEY_PASSWORD, mPassword);
		savedStateData.putString(SAVED_STATE_KEY_ADDRESS, mAddress);
	}

	/**
	 * Restore the state of the user after the current Activity has been re-created.
	 *
	 * @param savedStateData
	 */
	public static User restoreState(final Bundle savedStateData)
	{
		Log.v(TAG, "restore state");
		User user = create(savedStateData.getString(SAVED_STATE_KEY_NAME),
						   savedStateData.getString(SAVED_STATE_KEY_PASSWORD));
		user.setAddress(savedStateData.getString(SAVED_STATE_KEY_ADDRESS));
		return user;
	}
}
