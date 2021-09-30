package com.alicecallsbob.csdk.android.sample;

import java.util.Arrays;
import java.util.List;

import com.alicecallsbob.fcsdk.android.phone.MediaDirection;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;
import android.widget.ListView;

public class SelectMediaDirectionActivity extends ListActivity
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);        
        final List<MediaDirection> directions = Arrays.asList(MediaDirection.values());                
        
        final MediaDirection initialSelection = 
                (MediaDirection) getIntent().getExtras().get(Main.DATA_SELECTED_MEDIA_DIRECTION);
        
        final ListAdapter adapter = new MediaDirectionArrayAdapter(
                getApplicationContext(), 
                android.R.layout.simple_list_item_checked, 
                directions, 
                initialSelection);        
        setListAdapter(adapter);
    }

    /**
     * Deals with the user's selection of a media direction. This has the effect of closing the
     * activity, returning the selected media direction as an intent extra.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        final Intent data = new Intent();
        final MediaDirection extraValue = (MediaDirection) getListView().getItemAtPosition(position);
        data.putExtra(Main.DATA_SELECTED_MEDIA_DIRECTION, extraValue);
        setResult(RESULT_OK, data);        
        finish();
    }

    private class MediaDirectionArrayAdapter extends ArrayAdapter<MediaDirection>
    {
        private MediaDirection selectedDirection;
        
        public MediaDirectionArrayAdapter(
                Context context, 
                int textViewResourceId,
                List<MediaDirection> objects, 
                MediaDirection selectedDirection) 
        {
            super(context, textViewResourceId, objects);           
            this.selectedDirection = selectedDirection;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) 
        {
            final CheckedTextView checkedTextView = (CheckedTextView) super.getView(position, convertView, parent);
            final MediaDirection item = getItem(position);
            
            if (item == selectedDirection)
            {
                checkedTextView.setChecked(true);
            }            
            return checkedTextView;           
        }
    }    

}
