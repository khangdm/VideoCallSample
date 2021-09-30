package com.alicecallsbob.csdk.android.sample;

import java.io.Serializable;
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


/**
 * For users to select cameras for video calls.
 * 
 * @author CafeX Communications
 *
 */
public class CameraSelectionActivity extends ListActivity
{   
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);        
        final List<PhoneVideoCamera> supportedCameras = getSupportedCameras();                
        
        final ListAdapter adapter = new CameraArrayAdapter(getApplicationContext(), 
                android.R.layout.simple_list_item_checked, 
                supportedCameras, getCurrentSelectedCamera());        
        setListAdapter(adapter);
    }
    
    private List<PhoneVideoCamera> getSupportedCameras()
    {
        @SuppressWarnings("unchecked")
        final List<PhoneVideoCamera> supportedCameras = (List<PhoneVideoCamera>)
                getIntent().getExtras().get(Main.DATA_RECOMMENDED_SETTINGS);        
        return supportedCameras;        
    }
    
    /**
     * @return the current selected camera
     */
    private Integer getCurrentSelectedCamera()
    {
        return (Integer) getIntent().getExtras().get(Main.DATA_SELECTED_CAMERA);
    }
    
    /**
     * Return the selected resolution back to the calling activity,
     * via an intent extra.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        final Intent data = new Intent();
        data.putExtra(Main.DATA_SELECTED_CAMERA,
                (Serializable) getListView().getItemAtPosition(position));
        setResult(RESULT_OK, data);        
        finish();
    }
    
    /**
     * Custom array adapter that checks the currently selected camera.
     * 
     * @author CafeX Communications
     *
     */
    private class CameraArrayAdapter extends ArrayAdapter<PhoneVideoCamera>
    {
        private int selectedCamera;
        
        public CameraArrayAdapter(Context context, int textViewResourceId,
                List<PhoneVideoCamera> objects, Integer selectedCamera) 
        {
            super(context, textViewResourceId, objects);           
            this.selectedCamera = selectedCamera;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) 
        {
            final CheckedTextView checkedTextView = (CheckedTextView) super.getView(position, convertView, parent);
            final PhoneVideoCamera item = getItem(position);
            
            if (item.getCameraFacingDirection() == selectedCamera)
            {
                checkedTextView.setChecked(true);
            }            
            return checkedTextView;           
        }
    }    
}
