package com.alicecallsbob.csdk.android.sample;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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

import com.alicecallsbob.fcsdk.android.phone.PhoneVideoCaptureResolution;
import com.alicecallsbob.fcsdk.android.phone.PhoneVideoCaptureSetting;

/**
 * A List Activity, used to allow the user to select a supported resolution,
 * that should be used for video capture.
 * 
 * @author CafeX Communications
 *
 */
public class ResolutionActivity extends ListActivity
{   
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        final List<PhoneVideoCaptureResolution> supportedResolutions = getSupportedResolutions();
        
        final ListAdapter adapter = new ResolutionArrayAdapter(getApplicationContext(), 
                android.R.layout.simple_list_item_checked, 
                supportedResolutions, getCurrentSelectedResolution());
        
        setListAdapter(adapter);
    }
    
    private List<PhoneVideoCaptureResolution> getSupportedResolutions()
    {
        @SuppressWarnings("unchecked")
        final List<PhoneVideoCaptureSetting> recommendedSettings = (List<PhoneVideoCaptureSetting>)
                getIntent().getExtras().get(Main.DATA_RECOMMENDED_SETTINGS);
        
        final List<PhoneVideoCaptureResolution> supportedResolutions = new ArrayList<PhoneVideoCaptureResolution>();
        for (PhoneVideoCaptureSetting setting : recommendedSettings)
        {
        		final PhoneVideoCaptureResolution resolution = setting.getResolution();
        		//duplicates can happen because the SDK can recommend multiple settings with the same resolution, but
        		//different framerates. This sample app only allows for resolution selection.
        		if (!supportedResolutions.contains(resolution))
        		{
        			supportedResolutions.add(resolution);
        		}
        }
        
        return supportedResolutions;
    }
    
    /**
     * @return the current selected resolution
     */
    private PhoneVideoCaptureResolution getCurrentSelectedResolution()
    {
        return (PhoneVideoCaptureResolution)getIntent().getExtras().get(Main.DATA_SELECTED_RESOLUTION);
    }
    
    /**
     * Return the selected resolution back to the calling activity,
     * via an intent extra.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        final Intent data = new Intent();
        data.putExtra(Main.DATA_SELECTED_RESOLUTION, 
                (Serializable)getListView().getItemAtPosition(position));
        setResult(RESULT_OK, data);
        
        finish();
    }
    
    /**
     * Custom array adapter that checks the currently selected resolution.
     * 
     * @author CafeX Communications
     *
     */
    private class ResolutionArrayAdapter extends ArrayAdapter<PhoneVideoCaptureResolution>
    {
        private PhoneVideoCaptureResolution selectedResolution;
        
        public ResolutionArrayAdapter(Context context, int textViewResourceId,
                List<PhoneVideoCaptureResolution> objects, PhoneVideoCaptureResolution selectedResolution) 
        {
            super(context, textViewResourceId, objects);
            
            this.selectedResolution = selectedResolution;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) 
        {
            final CheckedTextView checkedTextView = (CheckedTextView) super.getView(position, convertView, parent);
            
            if (getItem(position) == selectedResolution)
            {
                checkedTextView.setChecked(true);
            }
            
            return checkedTextView;           
        }
    }
    
}
