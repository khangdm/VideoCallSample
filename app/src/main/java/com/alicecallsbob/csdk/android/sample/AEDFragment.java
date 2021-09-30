package com.alicecallsbob.csdk.android.sample;

import java.util.List;
import java.util.Map;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.alicecallsbob.csdk.android.sample.Main.ActiveCallsBarListener;
import com.alicecallsbob.fcsdk.android.aed.Topic;
import com.alicecallsbob.fcsdk.android.aed.TopicListener;
import com.alicecallsbob.fcsdk.android.phone.Call;
import com.alicecallsbob.fcsdk.android.phone.Phone;

/**
 * FIXME: synchronise messages being posted to the console, when 2 happen at same time, all
 * previous messages are currently deleted.
 */
public class AEDFragment extends Fragment implements TopicListener, ActiveCallsBarListener
{
	/** Identifier String for LogCat output. */
	private static final String TAG = "AEDFragment";

	private static final String SAVE_STATE_KEY_CONSOLE = "_console_text";
	private static final String SAVE_STATE_KEY_SELECTED = "_selected_index";

	/**
	 * A custom {@link ArrayAdapter} that we use to display the list of {@link Topic}s.
	 */
	private class TopicArrayAdapter extends ArrayAdapter<Topic>
	{
		private final int mSelectedItemBGColour;

		public TopicArrayAdapter()
		{
			super(getActivity(), R.layout.aed_topic_list_item);
			mSelectedItemBGColour = getResources().getColor(R.color.tp_mid_blue);
		}

		@Override
		public int getCount()
		{
			return mTopicManager.getNumberOfTopics();
		}

		@Override
		public View getView(final int position, final View convertView, final ViewGroup parent)
		{
			Log.v(TAG, "TopicArrayAdapter#getView, position " + position);
			final View view = mLayoutInflater
								.inflate(R.layout.aed_topic_list_item, null);

			// Set the Topic name label
			((TextView)view.findViewById(android.R.id.text1))
				.setText(mTopicManager.getTopic(position).getName());

			// If this is the selected Topic, highlight this list item
			if (position == mSelectedTopicIndex)
			{
				view.setBackgroundColor(mSelectedItemBGColour);
			}

			return view;
		}
	}

	/**
	 * A custom {@link ArrayAdapter} that we use to display the list of {@link Topic} data
	 * key-value pairs.
	 */
	private class TopicDataAdapter extends ArrayAdapter<Map.Entry<String, Object>>
	{
		public TopicDataAdapter()
		{
			super(getActivity(), R.layout.aed_data_list_item);
		}

		@Override
		public int getCount()
		{
			if (connectedToATopic())
			{
				return mTopicManager.getTopicDataCount(mSelectedTopicIndex);
			}

			return 0;
		}

		@Override
		public View getView(final int position, final View convertView, final ViewGroup parent)
		{
			Log.v(TAG, "TopicDataAdapter#getView, position " + position);
			final View view = (convertView != null) ? convertView
								: mLayoutInflater
									.inflate(R.layout.aed_data_list_item, null);

			final Map.Entry<String, Object> pair =
					mTopicManager.getTopicDataPair(mSelectedTopicIndex, position);
			assertNotNull(pair);

			// Set the Key TextView string
			((TextView)view.findViewById(android.R.id.text1)).setText(pair.getKey());

			// Set the Value TextView string
			((TextView)view.findViewById(android.R.id.text2))
				.setText(String.valueOf(pair.getValue()));

			return view;
		}

		private void assertNotNull(Object object)
		{
			if (object == null)
			{
				throw new AssertionError();
			}
		}
	}

	/** */
	private final OnItemClickListener mOnTopicClicked = new OnItemClickListener()
	{
		public void onItemClick(final AdapterView<?> parent, final View v, final int position,
				final long id)
		{
			Log.v(TAG, "Topic list item clicked, position(" + position + ") id(" + id + ")");
			mSelectedTopicIndex = (int)id; // id is zero-based, the header is -1
			mTopicsAdapter.notifyDataSetChanged();
			mTopicDataAdapter.notifyDataSetChanged();
		}
	};

	/** */
	private final OnItemClickListener mOnTopicDataClicked = new OnItemClickListener()
	{
		public void onItemClick(final AdapterView<?> parent, final View v, final int position,
				final long id)
		{
			Log.v(TAG, "Data list item clicked, position(" + position + ") id(" + id + ")");

			final String key = ((TextView)v.findViewById(android.R.id.text1)).getText().toString();
			final String value = ((TextView)v.findViewById(android.R.id.text2))
									.getText().toString();

			showEditDataDialog(key, value);
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

	protected ListView mTopicsList;
	protected TopicArrayAdapter mTopicsAdapter;
	protected int mSelectedTopicIndex;

	protected ListView mDataList;
	protected TopicDataAdapter mTopicDataAdapter;

	private ScrollView mConsoleScrollView;
	private TextView mMessagesAndNotifications;

	protected LayoutInflater mLayoutInflater;

	protected static AEDTopicManager mTopicManager;

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);

		if (mTopicManager == null)
		{
			mTopicManager = new AEDTopicManager();
		}
		mTopicManager.setListener(this);

		mTopicsAdapter = new TopicArrayAdapter();

		mTopicDataAdapter = new TopicDataAdapter();

		mLayoutInflater = getActivity().getLayoutInflater();
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
			final Bundle savedInstanceState)
	{
		Log.d(TAG, "onCreateView");

		View view = inflater.inflate(R.layout.fragment_aed, container, false);

		// Topics list, i.e. the Topics that the user is connected/subscribed to
		mTopicsList = (ListView)view.findViewById(R.id.topicsList);
		mTopicsList.setAdapter(mTopicsAdapter);
		mTopicsList.setOnItemClickListener(mOnTopicClicked);

		// Data list
		mDataList = (ListView)view.findViewById(R.id.dataList);
		mDataList.setAdapter(mTopicDataAdapter);
		mDataList.setOnItemClickListener(mOnTopicDataClicked);

		// Connect button
		view.findViewById(R.id.aedConnectButton).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				showConnectTopicDialog();
			}
		});

		// Disconnect button
		view.findViewById(R.id.aedDisconnectButton).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				if (connectedToATopic())
				{
					showDisconnectDialog();
				}
				else
				{
					final Context ctx = getActivity();
					Toast.makeText(ctx, ctx.getText(R.string.aed_disconnect_button_not_connected),
								   Toast.LENGTH_SHORT)
					   .show();
				}
			}
		});

		// Data button
		view.findViewById(R.id.aedDataButton).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				if (connectedToATopic())
				{
					showAddDataDialog();
				}
				else
				{
					final Context ctx = getActivity();
					Toast.makeText(ctx, ctx.getText(R.string.aed_data_button_not_connected),
								   Toast.LENGTH_SHORT)
					   .show();
				}
			}
		});

		// Message button
		view.findViewById(R.id.aedMessageButton).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				if (connectedToATopic())
				{
					showCreateMessageDialog();
				}
				else
				{
					final Context ctx = getActivity();
					Toast.makeText(ctx, ctx.getText(R.string.aed_message_button_not_connected),
								   Toast.LENGTH_SHORT)
					   .show();
				}
			}
		});

		// Messages and notifications view
		mConsoleScrollView = (ScrollView)view.findViewById(R.id.consoleScrollView);
		mMessagesAndNotifications = (TextView)view.findViewById(R.id.msgNoteView);

		// Have we restored a destroyed instance?
		if (savedInstanceState != null)
		{
			// Restore the console text
			final String consoleText = savedInstanceState.getString(SAVE_STATE_KEY_CONSOLE);
			if (!TextUtils.isEmpty(consoleText))
			{
				addConsoleText(consoleText);
			}

			// Select the same Topic.
			mSelectedTopicIndex = savedInstanceState.getInt(SAVE_STATE_KEY_SELECTED);
		}

		// Active calls bar
		mShowActiveCallsBar = (TextView)view.findViewById(R.id.activeCallsBar);
		mShowActiveCallsBar.setOnClickListener(mOnShowActiveClicked);

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		Main.mActiveCallsBarListener = this;

		updateActiveCallsBarVisibility(null);
	}

	@Override
	public void onSaveInstanceState(final Bundle outState)
	{
		super.onSaveInstanceState(outState);
		if (isVisible())
		{
			Log.d(TAG, "onSaveInstanceState");

			// Save the full contents of the console
			outState.putString(SAVE_STATE_KEY_CONSOLE,
							   mMessagesAndNotifications.getText().toString());

			// Save the selected Topic index
			outState.putInt(SAVE_STATE_KEY_SELECTED, mSelectedTopicIndex);
		}
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		mTopicManager.removeListener(this);
	}

	/**
	 * Display a dialog that allows the user to connect to (and possibly create)
	 * a {@link Topic}.
	 */
	protected final void showConnectTopicDialog()
	{
		final View contentView = mLayoutInflater
									.inflate(R.layout.aed_connect_topic_dialog,
											 null);

		createDialogBuilder(R.string.aed_connect_topic_dialog_title, contentView)
			.setPositiveButton(R.string.aed_connect_button_label,
				new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						// Get the name of the topic
						EditText editBox =
								(EditText)contentView.findViewById(R.id.aedConnectDialogName);
						final String topicName = editBox.getText().toString();
						if (topicName.contains(" "))
						{
							Utils.logAndToast(getActivity(), TAG, Log.WARN,
											  "Topic names cannot contain spaces");
						}
						else
						{
							// Get the (optional) expiry time
							int expiryTime = 0;
							editBox =
								(EditText)contentView.findViewById(R.id.aedConnectDialogExpiry);
							final String value = editBox.getText().toString();
							if (!TextUtils.isEmpty(value))
							{
								expiryTime = Integer.valueOf(value);
							}

							// Now ask to connect to the topic, creating it if need be
							if (mTopicManager != null)
							{
								mTopicManager.createTopic(topicName, expiryTime);
							}
						}
					}
				})
			.create()
			.show();
	}

	/**
	 * Display a dialog that allows the user to disconnect (and delete) the
	 * selected {@link Topic}.
	 */
	protected final void showDisconnectDialog()
	{
		final View contentView =
				mLayoutInflater.inflate(R.layout.aed_disconnect_topic_dialog,
										null);

		// Put the name of the selected Topic in the name view by default
		final String topicName = mTopicManager.getTopic(mSelectedTopicIndex).getName();
		((TextView)contentView.findViewById(R.id.aedDisconnectDialogName)).setText(topicName);

		createDialogBuilder(R.string.aed_disconnect, contentView)
			.setPositiveButton(R.string.aed_disconnect, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						// Find the Topic with the given name
						final Topic topic = mTopicManager.findTopic(topicName);
						if (topic != null)
						{
							/*
							 * Ask to disconnect from the given topic (and delete it if the check
							 * box is checked)
							 */
							if (((CheckBox)contentView
									.findViewById(R.id.aedDisconnectDialogDelete)).isChecked())
							{
								topic.disconnect(true);
							}
							else
							{
								topic.disconnect();

								/*
								 * We don't get a callback when we disconnect without deleting,
								 * so we have to update the topics, data and views now and also
								 * send a message to the console.
								 */
								mTopicManager.removeTopic(topic);
								updateSelectedIndex();
								mTopicsAdapter.notifyDataSetChanged();
								mTopicDataAdapter.notifyDataSetChanged();
								addConsoleText("(" + topicName + ") disconnected");
							}
						}
					}
				})
			.create()
			.show();
	}

	/**
	 * Display a dialog that allows the user to add a key-value data pair to the selected
	 * {@link Topic}.
	 */
	protected final void showAddDataDialog()
	{
		final View content = mLayoutInflater
								.inflate(R.layout.aed_new_data_dialog, null);

		createDialogBuilder(R.string.aed_new_data_dialog_title, content)
			.setPositiveButton(R.string.submit, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						// Get the topic
						final Topic topic = mTopicManager.getTopic(mSelectedTopicIndex);
						if (topic != null)
						{
							// Get the new Key name
							final String keyName =
									((EditText)content.findViewById(R.id.aedEditDataKey))
										.getText().toString();

							// Get the new value
							final String editedValue =
									((EditText)content.findViewById(R.id.aedEditDataValue))
										.getText().toString();

							// Submit the data
							topic.submitData(keyName, editedValue);
						}
					}
				})
			.create()
			.show();
	}

	/**
	 * Display a dialog that allows the user to edit/delete the selected
	 * key-value data pair.
	 * @param key The key {@link String} for the selected key-value pair to edit
	 * @param value The value of the selected key-value pair, as a {@link String}
	 */
	protected final void showEditDataDialog(final String key, final String value)
	{
		final View content = mLayoutInflater
								.inflate(R.layout.aed_edit_data_dialog, null);

		// Set the text for the key
		((EditText)content.findViewById(R.id.aedEditDataKey)).setText(key);

		// Set the current text for the value
		final EditText valueTextBox = (EditText)content.findViewById(R.id.aedEditDataValue);
		valueTextBox.setText(value);

		// Create and show the dialog
		createDialogBuilder(R.string.aed_edit_data_dialog_title, content)
			.setPositiveButton(R.string.submit, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						// Get the topic
						final Topic topic = mTopicManager.getTopic(mSelectedTopicIndex);
						if (topic != null)
						{
							// Get the edited value
							final String editedValue = valueTextBox.getText().toString();

							// Submit the data
							topic.submitData(key, editedValue);
						}
					}
				})
			.setNeutralButton(R.string.aed_edit_data_delete_button_label,
				new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						// What's the topic?
						final Topic topic = mTopicManager.getTopic(mSelectedTopicIndex);
						if (topic != null)
						{
							topic.deleteData(key);
						}
					}
				})
			.create()
			.show();
	}

	/**
	 * Display a dialog that allows the user to compose and send a new message
	 * for the selected {@link Topic}.
	 */
	protected final void showCreateMessageDialog()
	{
		final View content = mLayoutInflater
								.inflate(R.layout.aed_create_message_dialog,
										 null);
		final EditText textView = (EditText)content.findViewById(android.R.id.text1);
		textView.setOnEditorActionListener(new TextView.OnEditorActionListener()
		{
			@Override
			public boolean onEditorAction(final TextView v, final int actionId,
					final KeyEvent event)
			{
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_SEND)
				{
					handled = true;
					sendMessage(textView.getText().toString());
				}
				return handled;
			}
		});

		createDialogBuilder(R.string.aed_create_message_dialog_title, content)
			.setPositiveButton(R.string.send_button_label,
				new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						sendMessage(textView.getText().toString());
					}
				})
			.create()
			.show();
	}

	/**
	 * Create a base {@link AlertDialog#Builder} object with the given title and content view and
	 * a 'Cancel' button.
	 * @param titleId The resource id of the title {@link String} for the dialog
	 * @param content The content layout for the dialog
	 * @return The {@link AlertDialog#Builder} object that we can build a dialog upon
	 */
	private Builder createDialogBuilder(final int titleId, final View content)
	{
		return new AlertDialog.Builder(getActivity())
				.setTitle(titleId)
				.setView(content)
				.setNegativeButton(android.R.string.cancel, null);
	}

	/**
	 * Append the given text to the console view on a new line.
	 * @param text The text to append.
	 */
	private void addConsoleText(final String text)
	{
		Log.v(TAG, "addConsoleText");

		// Append the new message to the console text
		if (TextUtils.isEmpty(mMessagesAndNotifications.getText()))
		{
			mMessagesAndNotifications.setText(text);
		}
		else
		{
			mMessagesAndNotifications.append("\r\n" + text);
		}

		// Force the view to scroll down so we can see the recent message
		mConsoleScrollView.post(new Runnable()
		{
			@Override
			public void run()
			{
				mConsoleScrollView.fullScroll(View.FOCUS_DOWN);
			}
		});
	}

	/**
	 * Utility method that tells us if we are currently connected to at least 1 {@link Topic}.
	 * @return true if we are currently connected, false otherwise.
	 */
	protected final boolean connectedToATopic()
	{
		return (mTopicManager != null) ? (mTopicManager.getNumberOfTopics() > 0) : false;
	}

	@Override
	public final void onDataDeleted(final Topic topic, final String key, final int version)
	{
		final String topicName = topic.getName();
		Log.v(TAG, "onDataDeleted (" + topicName + ", key:" + key + ")");

		/*
		 * Need to do this as websocket inbound transport layer is running on a separate thread to main UI thread.
		 * If we don't do this, we'll get a: 
		 * Android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
		 */
		getActivity().runOnUiThread( new Runnable()
		{
			@Override
			public void run()
			{					
		        mTopicDataAdapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	public final void onDataNotDeleted(final Topic topic, final String key, final String message)
	{
		final String topicName = topic.getName();
		Log.v(TAG, "onDataNotDeleted (" + topicName + ", key:" + key + "): " + message);

		addConsoleText("(" + topicName + ") data (key:" + key + ") not deleted. " + message);
	}

	@Override
	public final void onMessageReceived(final Topic topic, final String message)
	{
		final String topicName = topic.getName();
		Log.v(TAG, "onMessageReceived: topic('" + topicName + "'), message('" + message + "')");

		/*
		 * Need to do this as websocket inbound transport layer is running on a separate thread to main UI thread.
		 * If we don't do this, we'll get a: 
		 * Android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
		 */
		getActivity().runOnUiThread( new Runnable()
		{
			@Override
			public void run()
			{		
	     	    addConsoleText("(" + topic.getName() + ") new message: " + message);
			}
		});
	}

	@Override
	public final void onTopicConnected(final Topic topic, final Map<String, Object> data)
	{	
		/*
		 * Need to do this as websocket inbound transport layer is running on a separate thread to main UI thread.
		 * If we don't do this, we'll get a: 
		 * Android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
		 */
		getActivity().runOnUiThread( new Runnable()
		{
			@Override
			public void run()
			{					
				final String topicName = topic.getName();
				Log.v(TAG, "onTopicConnected: " + topicName + ", selected index=" + mSelectedTopicIndex);

				mSelectedTopicIndex = mTopicManager.getIndexOfTopic(topic);					

				// notify the topics list adapter that the contents have changed
				mTopicsAdapter.notifyDataSetChanged();

				mTopicsList.setSelection(mSelectedTopicIndex);

				// make sure that the new, selected topic is visible
				mTopicsList.post(new Runnable()
				{
					@Override
					public void run()
					{
						mTopicsList.setSelection(mSelectedTopicIndex);
					}
				});

				// notify the topics data list adapter that the contents have changed
				mTopicDataAdapter.notifyDataSetChanged();

				addConsoleText("(" + topicName + ") connected");				
			}
		});
	}

	@Override
	public final void onTopicDeleted(final Topic topic, final String message)
	{
		/*
		 * Need to do this as websocket inbound transport layer is running on a separate thread to main UI thread.
		 * If we don't do this, we'll get a: 
		 * Android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
		 */
		getActivity().runOnUiThread( new Runnable()
		{
			@Override
			public void run()
			{	
				final String topicName = topic.getName();
				Log.v(TAG, "onTopicDeleted: " + topicName);
		
				Utils.logAndToast(getActivity(), TAG, Log.VERBOSE, message);
				updateSelectedIndex();
				mTopicsAdapter.notifyDataSetChanged();
				mTopicDataAdapter.notifyDataSetChanged();
				addConsoleText("(" + topicName + ") deleted. " + message);
			}
		});			
	}

	@Override
	public final void onTopicDeletedRemotely(final Topic topic)
	{
		final String topicName = topic.getName();
		Log.v(TAG, "onTopicDeletedRemotely: " + topicName);

		/*
		 * If the deleted topic is the selected topic, or above it in the list, update the
		 * selected topic index.
		 */
		final int topicIndex = mTopicManager.getIndexOfTopic(topic);
		if (topicIndex <= mSelectedTopicIndex)
		{
			updateSelectedIndex();
		}
		
		/*
		 * Need to do this as websocket inbound transport layer is running on a separate thread to main UI thread.
		 * If we don't do this, we'll get a: 
		 * Android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
		 */
		getActivity().runOnUiThread( new Runnable()
		{
			@Override
			public void run()
			{
				mTopicsAdapter.notifyDataSetChanged();
				mTopicDataAdapter.notifyDataSetChanged();
				addConsoleText("(" + topicName + ") deleted remotely");
			}			
	    });
	}

	@Override
	public final void onTopicNotConnected(final Topic topic, final String message)
	{
		Utils.logAndToast(getActivity(), TAG, Log.WARN, message);
	}

	@Override
	public final void onTopicNotDeleted(final Topic topic, final String message)
	{
		Utils.logAndToast(getActivity(), TAG, Log.WARN, message);
	}

	@Override
	public final void onTopicNotSent(final Topic topic, final String message, final String error)
	{
		Utils.logAndToast(getActivity(), TAG, Log.WARN, message);
	}

	@Override
	public final void onTopicNotSubmitted(final Topic topic, final String key, final String value,
			final String message)
	{
		Utils.logAndToast(getActivity(), TAG, Log.WARN, message);
	}

	@Override
	public final void onTopicSent(final Topic topic, final String message)
	{
		final String topicName = topic.getName();
		Log.i(TAG, "onTopicSent: topic('" + topicName + "'), message('" + message + "')");
	}

	@Override
	public final void onTopicSubmitted(final Topic topic, final String key, final String value,
			final int version)
	{
		/*
		 * Need to do this as websocket inbound transport layer is running on a separate thread to main UI thread.
		 * If we don't do this, we'll get a: 
		 * Android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
		 */
		getActivity().runOnUiThread( new Runnable()
		{
			@Override
			public void run()
			{	
				final String topicName = topic.getName();
				String change = null;
				if (version == 0)
				{
					change = "added";
				}
				else
				{
					change = "updated";
				}
				Log.i(TAG, "onTopicSubmitted (topic:" + topicName + ", key:" + key + ", value:" + value
						+ ", version:" + version + ") data " + change);
		
				// notify the topics data list adapter that the contents have changed
				mTopicDataAdapter.notifyDataSetChanged();
				// make sure that the new, selected topic is visible
				mDataList.post(new Runnable()
				{
					@Override
					public void run()
					{
						mDataList.setSelection(mTopicManager.getTopicDataListIndex(topicName, key));
					}
				});		
			}
		});
	}

	@Override
    public final void onTopicUpdated(final Topic topic, final String key, final String value, final int version,
            final boolean deleted)
	{
		/*
		 * Need to do this as websocket inbound transport layer is running on a separate thread to main UI thread.
		 * If we don't do this, we'll get a: 
		 * Android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
		 */
		getActivity().runOnUiThread( new Runnable()
		{
			@Override
			public void run()
			{
				final String topicName = topic.getName();
				String change = null;
				if (deleted)
				{
					change = "deleted";
				}
				else if (version == 0)
				{
					change = "added";
				}
				else
				{
					change = "updated";
				}
				Log.i(TAG, "onTopicUpdatedRemotely (topic:" + topicName + ", key:" + key + ", value:"
						+ value + ", version:" + version + ") data " + change);
				
				addConsoleText("(" + topicName + ") data (with key:" + key + ") " + change);		
				
				// Update the views to show the updated data
				mTopicDataAdapter.notifyDataSetChanged();	
			}
		});			
	}

	/**
	 * Send a text message to the current {@link Topic}.
	 * @param message The text message to send.
	 */
	final protected void sendMessage(final String message)
	{
		final Topic topic = mTopicManager.getTopic(mSelectedTopicIndex);
		if (topic != null)
		{
			topic.sendAedMessage(message);
		}
	}

	/**
	 * Update the index of the selected {@link Topic}, usually after we've connected to a new
	 * {@link Topic}, or we've disconnected from an existing {@link Topic}.
	 */
	final protected void updateSelectedIndex()
	{
		if (mSelectedTopicIndex > 0)
		{
			mSelectedTopicIndex--;
		}
		else
		{
			mSelectedTopicIndex = 0;
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
