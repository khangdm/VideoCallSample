package com.alicecallsbob.csdk.android.sample;

import android.content.ClipboardManager;
import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;


/**
 * Common client utilities.
 *
 * @author CafeX
 */
public final class Utils
{
	/** Identifier String for LogCat output. */
	private static final String TAG = "Utils";

	/**
	 * Private constructor because this is just a utility class.
	 */
	private Utils()
	{
	}

	/**
	 * Strip the given string phone number of separator characters, to reduce it to numbers
	 * (and possibly '+') only.
	 *
	 * @param phoneNumber
	 * @return the stripped string, or null if the argument is null
	 */
	public static String stripPhoneNumber(final String phoneNumber)
	{
		if (phoneNumber == null)
		{
			Log.e(TAG, "stripPhoneNumber was passed a null string");
			return null;
		}

		/*
		 * FIXME: this method below actually strips alphabetic characters as well as separators,
		 * but if we're trying to check if the given number is valid, this is not correct.
		 */
		return PhoneNumberUtils.stripSeparators(phoneNumber);
	}

	/**
	 * Strips the given string of separator characters, then checks if each remaining character
	 * is a dial-able character, i.e. 0-9, '#', '*' and '+'.
	 *
	 * @return
	 */
	public static boolean isValidNumberString(final String testCase)
	{
		final String stripped = stripPhoneNumber(testCase);
		final int strLen = stripped.length();
		if (strLen == 0)
		{
			return false;
		}
		int index = 0;
		char c;
		while (index < strLen)
		{
			c = stripped.charAt(index);
			if (!PhoneNumberUtils.isReallyDialable(c))
			{
				return false;
			}
			index++;
		}
		return true;
	}

	/**
	 * Specify text to be placed on system clip-board.
	 *
	 * @param text the text
	 */
	public static void setClipboardText(final Context ctx, final String text)
	{
		getClipboardManager(ctx).setText(text);
	}

	/**
	 * Get text from system clip-board.
	 *
	 * @return
	 */
	public static String getClipboardText(final Context ctx)
	{
		return getClipboardManager(ctx).getText().toString();
	}

	/**
	 * Acquire and return the system clip-board service.
	 *
	 * @return
	 */
	private static ClipboardManager getClipboardManager(final Context ctx)
	{
		Object theService = ctx.getSystemService(Context.CLIPBOARD_SERVICE);
		ClipboardManager cm = (ClipboardManager)theService;
		return cm;
	}
	
	/**
     * @param tag
     * @param context
     * @param logLevel
     * @param message
     */
    public static void logAndToast(final Context context, final String tag, final int logLevel,
            final String message)
    {
        logAndToast(context, tag, logLevel, message, Toast.LENGTH_LONG);
    }

	/**
	 * @param tag
	 * @param context
	 * @param logLevel
	 * @param message
	 */
	public static void logAndToast(final Context context, final String tag, final int logLevel,
			final String message, final int length)
	{
		switch (logLevel)
		{
		case Log.VERBOSE:
			Log.v(tag, message);
			break;

		case Log.DEBUG:
			Log.d(tag, message);
			break;

		case Log.INFO:
			Log.i(tag, message);
			break;

		case Log.WARN:
			Log.w(tag, message);
			break;

		case Log.ERROR:
			Log.e(tag, message);
			break;

		case Log.ASSERT:
			Log.wtf(tag, message);
			break;

		default: // we should never get here.
			break;
		}

		Toast.makeText(context, message, length).show();
	}

	/**
	 *
	 * @param text
	 * @return
	 */
	public static boolean stringIsNullOrEmpty(final String text)
	{
		if (TextUtils.isEmpty(text) || "null".equalsIgnoreCase(text))
		{
			return true;
		}

		return false;
	}

	/**
	 *
	 * @param address
	 * @return
	 */
	public static String getContactNameFromAddress(final String address)
	{
		final int indexOfColon = address.indexOf(':');
		final int indexOfAt = address.indexOf('@');

		String name = null;
		if (indexOfColon > -1 && indexOfAt > -1)
		{
			name = address.substring(indexOfColon + 1, indexOfAt);
		}

		return name;
	}
}
