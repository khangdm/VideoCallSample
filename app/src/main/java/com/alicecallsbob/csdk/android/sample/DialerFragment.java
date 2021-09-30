package com.alicecallsbob.csdk.android.sample;

import java.util.List;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.alicecallsbob.fcsdk.android.phone.Call;
import com.alicecallsbob.fcsdk.android.phone.Phone;

/**
 *
 */
public class DialerFragment extends Fragment implements com.alicecallsbob.csdk.android.sample.Main.ActiveCallsBarListener
{
	/** Identifier String for LogCat output. */
	protected static final String TAG = "DialerFragment";

	/** Maximum number of digits that fit inside preview TextView. */
	protected static final int MAX_TEL_NO_DIGITS = 30;

	/** Key used to save the 'telephone number' when the Fragment is destroyed/restored. */
	private static final String SAVE_STATE_KEY_TEL_NUM = "_tel_num";
	
	/** Fixed strings we'll need to enter a SIP address */
	private static final CharSequence[] BUTTON_1_TEXT= {"sip:","@","."};
	/** Fixed characters for a sip call */
	private static final CharSequence[] BUTTON_2_LETTERS = {"a","b","c"};
	/** Fixed characters for a sip call */
	private static final CharSequence[] BUTTON_3_LETTERS = {"d","e","f"};
	/** Fixed characters for a sip call */
	private static final CharSequence[] BUTTON_4_LETTERS = {"g","h","i"};
	/** Fixed characters for a sip call */
	private static final CharSequence[] BUTTON_5_LETTERS = {"j","k","l"};
	/** Fixed characters for a sip call */
	private static final CharSequence[] BUTTON_6_LETTERS = {"m","n","o"};
	/** Fixed characters for a sip call */
	private static final CharSequence[] BUTTON_7_LETTERS = {"p","q","r","s"};
	/** Fixed characters for a sip call */
	private static final CharSequence[] BUTTON_8_LETTERS = {"t","u","v"};
	/** Fixed characters for a sip call */
	private static final CharSequence[] BUTTON_9_LETTERS = {"w","x","y","z"};
	
	private final View.OnClickListener mDialpadButtonClick = new View.OnClickListener()
	{
		@Override
		public void onClick(final View v)
		{
			if (mTelNumberView.length() < MAX_TEL_NO_DIGITS)
			{
				switch (v.getId())
				{
				case R.id.button_1:
					mTelNumberView.append("1");
					break;

				case R.id.button_2:
					mTelNumberView.append("2");
					break;

				case R.id.button_3:
					mTelNumberView.append("3");
					break;

				case R.id.button_4:
					mTelNumberView.append("4");
					break;

				case R.id.button_5:
					mTelNumberView.append("5");
					break;

				case R.id.button_6:
					mTelNumberView.append("6");
					break;

				case R.id.button_7:
					mTelNumberView.append("7");
					break;

				case R.id.button_8:
					mTelNumberView.append("8");
					break;

				case R.id.button_9:
					mTelNumberView.append("9");
					break;

				case R.id.button_0:
					mTelNumberView.append("0");
					break;

				case R.id.button_star:
					mTelNumberView.append("*");
					break;

				case R.id.button_hash:
					mTelNumberView.append("#");
					break;

				default:
					break;
				}
			}
		}
	};

	/**
	 * Click listener for the 'Make call' button.
	 */
	private final View.OnClickListener mOnMakeCallClick = new OnClickListener()
	{
		/**
		 * We store the last number dialled, so that the user can press the call button and get
		 * this number back immediately.
		 */
		private String mLastDialled = null;

		public void onClick(final View view)
		{
			String numberToCall = mTelNumberView.getText().toString();

			if (!TextUtils.isEmpty(numberToCall))
			{
				Log.d(TAG, "make call clicked: " + numberToCall);
				mLastDialled = numberToCall; // store the number for recall
				mCallInitiated = ((Main)getActivity()).makeCall(numberToCall);
				mTelNumberView.setText("");
			}
			else if (mLastDialled != null)
			{
				// Put the last dialled number in the preview box
				mTelNumberView.setText(mLastDialled);
			}
		}
	};

	/**
	 * Click and long-click listeners for the Delete button.
	 */
	private final View.OnClickListener mOnDeleteClick = new OnClickListener()
	{
		public void onClick(final View view)
		{
			final String szNumber = mTelNumberView.getText().toString();
			if ((szNumber != null) && (szNumber.length() > 0))
			{
				mTelNumberView.setText(szNumber.substring(0, szNumber.length() - 1));
			}
		}
	};
	private final View.OnLongClickListener mOnLongDeleteClick = new OnLongClickListener()
	{
		@Override
		public boolean onLongClick(final View v)
		{
			// Delete all the digits
			mTelNumberView.setText("");
			return true;
		}
	};

	/**
	 * Long-click listener for the zero button.
	 */
	private final View.OnLongClickListener mOnZeroLongClick = new OnLongClickListener()
	{
		@Override
		public boolean onLongClick(final View v)
		{
			if (mTelNumberView.length() < MAX_TEL_NO_DIGITS)
			{
				mTelNumberView.append("+");
			}

			return true;
		}
	};
	
	/**
	 * Long-click listener for the keypad buttons. 
	 */
        private final View.OnLongClickListener mDigitLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(final View v)
            {
                if (mTelNumberView.length() < MAX_TEL_NO_DIGITS)
                {
                    switch (v.getId())
                    {
                    case R.id.button_1:
                        showAlertDialog(BUTTON_1_TEXT);
                        break;
                    case R.id.button_2:
                        showAlertDialog(BUTTON_2_LETTERS);
                        break;
                    case R.id.button_3:
                        showAlertDialog(BUTTON_3_LETTERS);
                        break;
                    case R.id.button_4:
                        showAlertDialog(BUTTON_4_LETTERS);
                        break;
                    case R.id.button_5:
                        showAlertDialog(BUTTON_5_LETTERS);
                        break;
                    case R.id.button_6:
                        showAlertDialog(BUTTON_6_LETTERS);
                        break;
                    case R.id.button_7:
                        showAlertDialog(BUTTON_7_LETTERS);
                        break;
                    case R.id.button_8:
                        showAlertDialog(BUTTON_8_LETTERS);
                        break;
                    case R.id.button_9:
                        showAlertDialog(BUTTON_9_LETTERS);
                        break;
                    default:
                        break;
                    }
                }
                return true;
            }
        };

        /**
         * Opens the alert for the button options
         * @param options The list of options to display
         */
        private final void showAlertDialog(final CharSequence[] options)
        {
            new AlertDialog.Builder(getActivity())
                .setItems(options, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which)
                    {
                        mTelNumberView.append(options[which]);
                    }
                })
                .create()
                .show();
        }
        
	/**
	 * Telephone number TextView long click listener, displays a single-choice list dialog
	 * containing actions that can be performed on the telephone number text.
	 */
	private final OnLongClickListener mTelNumLongClickListener = new OnLongClickListener()
	{
		@Override
		public boolean onLongClick(final View v)
		{
			final CharSequence[] listItems =
					getResources().getStringArray(R.array.tel_num_context_menu_options);

			new AlertDialog.Builder(getActivity())
				.setItems(listItems, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(final DialogInterface dialog, final int which)
						{
							switch (which)
							{
							case 0: // clear all text
								mTelNumberView.setText("");
								break;

							case 1: // paste from the clipboard
								pasteNumber();
								break;

							case 2: // copy to the clipboard
								Utils.setClipboardText(getActivity(),
													   mTelNumberView.getText().toString());
								break;
							}
						}
					})
				.create()
				.show();

			return true; // this method consumed the long click
		}
	};

	private final OnClickListener mOnShowActiveClicked =
			new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			startActivity(new Intent(getActivity(), InCallActivity.class));
		}
	};

	private TextView mShowActiveCallsBar;

	/** Number preview TextView, contains the number that will be dialled. */
	protected TextView mTelNumberView;
	private CharSequence mTelNumText;

	/** Has a call been successfully initiated? */
	public boolean mCallInitiated;

	@Override
	public final void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Log.v(TAG, "onCreate");

		mCallInitiated = false;
	}

	@Override
	public final View onCreateView(final LayoutInflater inflater, final ViewGroup container,
			final Bundle savedInstanceState)
	{
		Log.v(TAG, "onCreateView");

		View view = inflater.inflate(R.layout.fragment_dialer, container, false);

		// Telephone number preview
		mTelNumberView = (TextView)view.findViewById(R.id.tvNumber);
		mTelNumberView.setOnLongClickListener(mTelNumLongClickListener);
		if (savedInstanceState != null)
		{
			mTelNumberView.setText(savedInstanceState.getString(SAVE_STATE_KEY_TEL_NUM));
		}

		// Append a '+' to the phone number text on a long click of this button
		View zeroBtn = view.findViewById(R.id.button_0);
		zeroBtn.setOnLongClickListener(mOnZeroLongClick);

		// 'Delete digit' button, a long click on the button deletes the whole string
		View button = view.findViewById(R.id.deleteDigit);
		button.setOnClickListener(mOnDeleteClick);
		button.setOnLongClickListener(mOnLongDeleteClick);

		// 'Make call' buttons
		view.findViewById(R.id.btnMakeCall).setOnClickListener(mOnMakeCallClick);

		// Set the button click listener for each dialpad button
		zeroBtn.setOnClickListener(mDialpadButtonClick);
		view.findViewById(R.id.button_1).setOnClickListener(mDialpadButtonClick);
		view.findViewById(R.id.button_2).setOnClickListener(mDialpadButtonClick);
		view.findViewById(R.id.button_3).setOnClickListener(mDialpadButtonClick);
		view.findViewById(R.id.button_4).setOnClickListener(mDialpadButtonClick);
		view.findViewById(R.id.button_5).setOnClickListener(mDialpadButtonClick);
		view.findViewById(R.id.button_6).setOnClickListener(mDialpadButtonClick);
		view.findViewById(R.id.button_7).setOnClickListener(mDialpadButtonClick);
		view.findViewById(R.id.button_8).setOnClickListener(mDialpadButtonClick);
		view.findViewById(R.id.button_9).setOnClickListener(mDialpadButtonClick);
		view.findViewById(R.id.button_star).setOnClickListener(mDialpadButtonClick);
		view.findViewById(R.id.button_hash).setOnClickListener(mDialpadButtonClick);

		// Bind the long click listener
		view.findViewById(R.id.button_1).setOnLongClickListener(mDigitLongClick);
		view.findViewById(R.id.button_2).setOnLongClickListener(mDigitLongClick);
		view.findViewById(R.id.button_3).setOnLongClickListener(mDigitLongClick);
		view.findViewById(R.id.button_4).setOnLongClickListener(mDigitLongClick);
		view.findViewById(R.id.button_5).setOnLongClickListener(mDigitLongClick);
		view.findViewById(R.id.button_6).setOnLongClickListener(mDigitLongClick);
		view.findViewById(R.id.button_7).setOnLongClickListener(mDigitLongClick);
		view.findViewById(R.id.button_8).setOnLongClickListener(mDigitLongClick);
		view.findViewById(R.id.button_9).setOnLongClickListener(mDigitLongClick);
		
		
		// Active calls bar
		mShowActiveCallsBar = (TextView)view.findViewById(R.id.activeCallsBar);
		mShowActiveCallsBar.setOnClickListener(mOnShowActiveClicked);

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();
		Log.v(TAG, "onResume");

		Main.mActiveCallsBarListener = this;

		// If the telephone number/address view is empty, re-use the text we saved onPause.
		if (TextUtils.isEmpty(mTelNumberView.getText()))
		{
			mTelNumberView.setText(mTelNumText);
		}

		updateActiveCallsBarVisibility(null);
	}

	@Override
	public void onPause()
	{
		super.onPause();
		Log.v(TAG, "onPause");

		// Save the telephone number/address for re-use when we come back to the dialer.
		mTelNumText = mTelNumberView.getText();
	}

	@Override
	public void onSaveInstanceState(final Bundle outState)
	{
		super.onSaveInstanceState(outState);
		if (isVisible())
		{
			Log.v(TAG, "Save Instance State");
			outState.putString(SAVE_STATE_KEY_TEL_NUM, mTelNumberView.getText().toString());
		}
	}

	/**
	 * Validate and paste the number from the clip-board to the number field, appending it to the
	 * existing content. If the clip-board content does not conform to a valid number, show a
	 * toast for user feedback.
	 */
	protected final void pasteNumber()
	{
		if (mTelNumberView.length() < MAX_TEL_NO_DIGITS)
		{
			String testNumber = Utils.getClipboardText(getActivity());
			final String existingNumber = mTelNumberView.getText().toString();
			// how many digits short of the max is the existing number?
			final int spareChars = MAX_TEL_NO_DIGITS - existingNumber.length();
			// how many digits should we actually copy?
			final int numDigitsToCopy = Math.min(spareChars, testNumber.length());
			testNumber = existingNumber.concat(testNumber.substring(0, numDigitsToCopy));
			if (Utils.isValidNumberString(testNumber))
			{
				mTelNumberView.setText(testNumber);
			}
			else
			{
				Toast.makeText(getActivity(), "Phone number is not valid", Toast.LENGTH_SHORT)
					.show();
			}
		}
		else
		{
			Toast.makeText(getActivity(),
						   "Phone number cannot be longer than " + MAX_TEL_NO_DIGITS + " digits"
							   	+ " long",
						   Toast.LENGTH_SHORT)
			   .show();
		}
	}

	@Override
	public void updateActiveCallsBarVisibility(final Call call)
	{
		if (Main.isUCInitialized() && isResumed())
		{
			final Phone callManager = Main.getPhoneManager();
			final List<? extends Call> calls = callManager.getCurrentCalls();

			// Have we got active calls?
			boolean gotCalls = (calls != null) && !calls.isEmpty();

			// If we have and we've been given a Call which has just ended...
			if (gotCalls && (call != null) && calls.contains(call))
			{
				/* ...remove the call from our local list and reassess whether
				 * we have calls */
				calls.remove(call);
				gotCalls = !calls.isEmpty();
			}

			/*
			 * If we (still) have calls, show the bar and display the correct
			 * number. If we don't have active calls, hide the bar.
			 */
			if (gotCalls)
			{
				mShowActiveCallsBar.setVisibility(View.VISIBLE);
				mShowActiveCallsBar.setText(getString(R.string.show_active_calls,
													  calls.size()));
			}
			else if ((calls == null) || calls.isEmpty())
			{
				mShowActiveCallsBar.setVisibility(View.GONE);
			}
		}
	}
}
