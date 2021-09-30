package com.alicecallsbob.csdk.android.sample;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.alicecallsbob.fcsdk.android.aed.AED;
import com.alicecallsbob.fcsdk.android.aed.Topic;
import com.alicecallsbob.fcsdk.android.aed.TopicListener;

import android.util.Log;

/**
 * Manages all the {@link Topic}s that the client connects to and interacts
 * with. It does not obey the same life cycle as the Fragment and therefore it
 * can stay up-to-date at all times and does not have to be destroyed and
 * re-created if the device is rotated.
 */
public class AEDTopicManager implements TopicListener
{
	/**
	 * Object that we use to hold the {@link Topic} data key-value pairs with
	 * the {@link Topic} name for lookup.
	 */
	private class TopicData
	{
		/** The name of the {@link Topic} that this data belongs to. */
		private final String mTopicName;

		/**
		 * A table of key-value pairs that represents the data for this
		 * {@link Topic}.
		 */
		private final Hashtable<String, Object> mDataPairs;

		/**
		 * Constructor.
		 *
		 * @param topicName The name of the {@link Topic} that this data will be
		 *            associated with
		 * @param data The {@link Map} of key-value pairs for this {@link Topic}
		 */
		public TopicData(final String topicName, final Map<String, Object> data)
		{
			mTopicName = topicName;
			mDataPairs = new Hashtable<String, Object>(data);
		}

		/**
		 * Get the name of the {@link Topic} that this object belongs to.
		 *
		 * @return The name of the {@link Topic}
		 */
		public String getTopicName()
		{
			return mTopicName;
		}

		/**
		 * Get the key-value pairs.
		 *
		 * @return A {@link Hashtable} of key-value pairs
		 */
		public Hashtable<String, Object> getDataPairs()
		{
			return mDataPairs;
		}

		/**
		 * Get the index of the pair with the given key.
		 *
		 * @param key The key for the pair that we're looking for
		 * @return The index in the list of key-value pairs, or -1 if the key
		 *         wasn't found
		 */
		public int getPairIndex(final String key)
		{
			int index = 0;
			final Enumeration<String> keys = mDataPairs.keys();
			while (keys.hasMoreElements())
			{
				final String nextKey = keys.nextElement();
				if (nextKey.equals(key))
				{
					return index;
				}

				index++;
			}
			return -1;
		}
	}

	/** Label for log output. */
	private static final String TAG = "AEDTopicManager";

	/** A local list of {@link Topic}s that we are connected to. */
	private final ArrayList<Topic> mTopics;

	/**
	 * A local list of {@link TopicData} objects for every {@link Topic} that
	 * we're connected to.
	 */
	private final ArrayList<TopicData> mTopicData;

	/**
	 * A {@link TopicListener} that we keep informed of changes to the Topics
	 * that we're connected to.
	 */
	private TopicListener mListener;

	/**
	 * Constructor.
	 */
	public AEDTopicManager()
	{
		mTopics = new ArrayList<Topic>();
		mTopicData = new ArrayList<TopicData>();
	}

	/**
	 * Set the {@link TopicListener} that this manager needs to keep informed of
	 * changes.
	 *
	 * @param listener The {@link TopicListener} to keep informed.
	 */
	public final void setListener(final TopicListener listener)
	{
		mListener = listener;
	}

	/**
	 * Remove the given {@link TopicListener} so that this manager doesn't keep
	 * it informed of future changes.
	 *
	 * @param listener The {@link TopicListener} that wishes to be removed.
	 */
	public final void removeListener(final TopicListener listener)
	{
		if (mListener == listener)
		{
			mListener = null;
		}
	}

	/**
	 * Get the list of all the connected {@link Topic}s.
	 *
	 * @return An {@link ArrayList} of {@link Topic}s that we're connected to.
	 */
	public final ArrayList<Topic> getTopics()
	{
		return mTopics;
	}

	/**
	 * Get the count of how many {@link Topic}s are in our connected list.
	 *
	 * @return The number of {@link Topic}s that we're connected to.
	 */
	public final int getNumberOfTopics()
	{
		return (mTopics != null) ? mTopics.size() : 0;
	}

	/**
	 * Get the {@link Topic} in our local list with the given index.
	 *
	 * @param index The position of the {@link Topic} within the list.
	 * @return The {@link Topic} that appears in the list at the given position,
	 *         or null if not found.
	 */
	public final Topic getTopic(final int index)
	{
		return (mTopics != null) ? mTopics.get(index) : null;
	}

	/**
	 * Connect to (and create if necessary) the {@link Topic} with the given
	 * name and set the expiry time.
	 *
	 * @param name The name of the {@link Topic} that we wish to connect
	 *            to/create.
	 * @param expiry The duration, in minutes, that we wish the {@link Topic} to
	 *            stay alive.
	 */
	public final void createTopic(final String name, final int expiry)
	{
		final AED aedManager = Main.getAEDManager();
		if (aedManager != null)
		{
			aedManager.createTopic(name, expiry, this);
		}
	}

	/**
	 * Find the {@link Topic} in our local list that has the given name.
	 *
	 * @param name The name of the {@link Topic} we wish to search for.
	 * @return The {@link Topic} with the given name, or null if not found.
	 */
	public final Topic findTopic(final String name)
	{
		Topic topic = null;

		Iterator<Topic> it = mTopics.iterator();
		while (it.hasNext())
		{
			Topic next = it.next();
			if (next.getName().equals(name))
			{
				topic = next;
				break;
			}
		}

		return topic;
	}

	/**
	 * Get the position in our local list that the given {@link Topic} occupies.
	 *
	 * @param topic The {@link Topic} that we wish to search for.
	 * @return The index of the {@link Topic}, or -1 if not found.
	 */
	public final int getIndexOfTopic(final Topic topic)
	{
		return (mTopics != null) ? mTopics.indexOf(topic) : -1;
	}

	/**
	 * Remove the given {@link Topic} from our local list, usually because we've
	 * disconnected from it.
	 *
	 * @param topic The {@link Topic} we wish to remove.
	 */
	public final void removeTopic(final Topic topic)
	{
		if (mTopics != null)
		{
			mTopics.remove(topic);
		}
		if (mTopicData != null)
		{
			removeTopicData(topic.getName());
		}
	}

	/**
	 * Get the number of key-value data pairs for the {@link Topic} with the
	 * given index.
	 *
	 * @param topicIndex The index in the list of {@link TopicData}
	 * @return The count of key-value data pairs
	 */
	public final int getTopicDataCount(final int topicIndex)
	{
		if (!mTopicData.isEmpty() && (topicIndex >= 0)
				&& (topicIndex < mTopicData.size()))
		{
			final TopicData data = mTopicData.get(topicIndex);
			if (data != null)
			{
				final Hashtable<String, Object> pairs = data.getDataPairs();
				if (!pairs.isEmpty())
				{
					return pairs.size();
				}
			}
		}

		return 0;
	}

	/**
	 * Get the key-value pair with the given index in the list of
	 * {@link TopicData} for the {@link Topic} at the given index.
	 *
	 * @param topicIndex The list index of the {@link Topic}
	 * @param pairIndex The list index of the {@link TopicData}
	 * @return A key-value {@link Entry}, or null if we couldn't find the
	 *         key-value pair
	 */
	@SuppressWarnings("unchecked")
	public final Entry<String, Object> getTopicDataPair(final int topicIndex,
			final int pairIndex)
	{
		if (!mTopicData.isEmpty() && (topicIndex >= 0)
				&& (topicIndex < mTopicData.size()))
		{
			final TopicData data = mTopicData.get(topicIndex);
			if (data != null)
			{
				final Hashtable<String, Object> pairs = data.getDataPairs();
				if (!pairs.isEmpty())
				{
					return (Entry<String, Object>)pairs.entrySet()
							.toArray()[pairIndex];
				}
			}
		}

		return null;
	}

	/**
	 * Get the {@link TopicData} object for the {@link Topic} with the given
	 * name.
	 *
	 * @param topicName The name of the {@link Topic}
	 * @return The {@link TopicData}, or null if not found
	 */
	private TopicData getTopicData(final String topicName)
	{
		TopicData topicData = null;
		Iterator<TopicData> it = mTopicData.iterator();
		while (it.hasNext())
		{
			TopicData data = it.next();
			if (data.getTopicName().equals(topicName))
			{
				topicData = data;
				break;
			}
		}

		return topicData;
	}

	/**
	 * Remove the {@link TopicData} object that matches the given {@link Topic}
	 * name.
	 *
	 * @param topicName The name of the {@link Topic}
	 * @return true if the {@link TopicData} was removed, false otherwise
	 */
	public final boolean removeTopicData(final String topicName)
	{
		TopicData dataToRemove = null;
		Iterator<TopicData> it = mTopicData.iterator();
		while (it.hasNext())
		{
			TopicData data = it.next();
			if (data.getTopicName().equals(topicName))
			{
				dataToRemove = data;
				break;
			}
		}

		if (dataToRemove != null)
		{
			mTopicData.remove(dataToRemove);
			return true;
		}

		return false;
	}

	/**
	 * Get the index (list position) of the data pair from the given
	 * {@link Topic} with the given key.
	 *
	 * @param topicName The name of the {@link Topic}
	 * @param key The key for the pair that we want the index of
	 * @return the index of the pair, or -1 if not found
	 */
	public final int getTopicDataListIndex(final String topicName,
			final String key)
	{
		int index = -1;
		Iterator<TopicData> it = mTopicData.iterator();
		while (it.hasNext())
		{
			TopicData data = it.next();
			if (data.getTopicName().equals(topicName))
			{
				index = data.getPairIndex(key);
				break;
			}
		}

		return index;
	}

	@Override
	public void onDataDeleted(final Topic topic, final String key,
			final int version)
	{
		// Find the pair with the given key in the TopicData for the given topic
		// and remove it.
		final TopicData topicData = getTopicData(topic.getName());
		if (topicData != null)
		{
			final Hashtable<String, Object> data = topicData.getDataPairs();
			if (data != null)
			{
				data.remove(key);
			}
		}

		if (mListener != null)
		{
			mListener.onDataDeleted(topic, key, version);
		}
	}

	@Override
	public void onDataNotDeleted(final Topic topic, final String key,
			final String message)
	{
		if (mListener != null)
		{
			mListener.onDataNotDeleted(topic, key, message);
		}
	}

	@Override
	public void onMessageReceived(final Topic topic, final String message)
	{
		if (mListener != null)
		{
			mListener.onMessageReceived(topic, message);
		}
	}

	@Override
	public void onTopicConnected(final Topic topic,
			final Map<String, Object> data)
	{
		mTopics.add(topic);

		/*
		 * The data object we've been given is the overall data for the given
		 * topic, which includes the topic name, expiry and type. We don't want
		 * to display that to the user so we pick put the actual data from the
		 * given data and create a new Map with it and store that one for
		 * display instead.
		 */
		Map<String, Object> dataMap = new HashMap<String, Object>();
		final ArrayList<LinkedHashMap<String, Object>> actualDataList =
				(ArrayList<LinkedHashMap<String, Object>>)data.get("data");
		if (actualDataList != null)
		{
			Log.v(TAG, "actual data for this topic is: " + actualDataList);

			Iterator<LinkedHashMap<String, Object>> dataListIt =
					actualDataList.iterator();
			while (dataListIt.hasNext())
			{
				final LinkedHashMap<String, Object> pair = dataListIt.next();
				Log.d(TAG, "data mapping: " + pair);
				
				// Only add non deleted data to the dataMap that the UI displays
				final Boolean deletedValue = (Boolean) pair.get("deleted");
				if (deletedValue.booleanValue())
				{
					Log.d(TAG, "Data mapping has been deleted, not adding this item to the dataMap used by the UI to " +
							"display data items. Data mapping: " + pair);
				}
				else
				{				
				    dataMap.put((String)pair.get("key"), (String)pair.get("value"));
				}
			}
		}

		mTopicData.add(new TopicData(topic.getName(), dataMap));

		if (mListener != null)
		{
			mListener.onTopicConnected(topic, data);
		}
	}

	@Override
	public void onTopicDeleted(final Topic topic, final String message)
	{
		if (mTopics.contains(topic))
		{
			mTopics.remove(topic);
			removeTopicData(topic.getName());

			if (mListener != null)
			{
				mListener.onTopicDeleted(topic, message);
			}
		}
	}

	@Override
	public void onTopicDeletedRemotely(final Topic topic)
	{
		if (mTopics.contains(topic))
		{
			mTopics.remove(topic);
			removeTopicData(topic.getName());

			if (mListener != null)
			{
				mListener.onTopicDeletedRemotely(topic);
			}
		}
	}

	@Override
	public void onTopicNotConnected(final Topic topic, final String message)
	{
		if (mListener != null)
		{
			mListener.onTopicNotConnected(topic, message);
		}
	}

	@Override
	public void onTopicNotDeleted(final Topic topic, final String message)
	{
		if (mListener != null)
		{
			mListener.onTopicNotDeleted(topic, message);
		}
	}

	@Override
	public void onTopicNotSent(final Topic topic, final String message,
			final String error)
	{
		if (mListener != null)
		{
			mListener.onTopicNotSent(topic, message, error);
		}
	}

	@Override
	public void onTopicNotSubmitted(final Topic topic, final String key,
			final String value, final String message)
	{
		if (mListener != null)
		{
			mListener.onTopicNotSubmitted(topic, key, value, message);
		}
	}

	@Override
	public void onTopicSent(final Topic topic, final String message)
	{
		if (mListener != null)
		{
			mListener.onTopicSent(topic, message);
		}
	}

	@Override
	public void onTopicSubmitted(final Topic topic, final String key,
			final String value, final int version)
	{
		final int topicIndex = mTopics.indexOf(topic);
		final TopicData data = mTopicData.get(topicIndex);
		final Hashtable<String, Object> pairs = data.getDataPairs();
		pairs.put(key, value);

		if (mListener != null)
		{
			mListener.onTopicSubmitted(topic, key, value, version);
		}
	}

	@Override
    public void onTopicUpdated(final Topic topic, final String key, final String value, final int version,
            final boolean deleted)
	{
		final int topicIndex = mTopics.indexOf(topic);
		final TopicData data = mTopicData.get(topicIndex);
		final Hashtable<String, Object> pairs = data.getDataPairs();
		if (deleted)
		{
			pairs.remove(key);
		}
		else
		{
			pairs.put(key, value);
		}

		if (mListener != null)
		{
			mListener.onTopicUpdated(topic, key, value, version, deleted);
		}
	}
}
