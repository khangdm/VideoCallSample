package com.alicecallsbob.csdk.android.sample;

import android.hardware.Camera.CameraInfo;

/**
 * Video capture cameras supported: Front and Back.
 * 
 * @author CafeX Communications
 *
 */
public enum PhoneVideoCamera 
{
	/** Select to use {@link CameraInfo#CAMERA_FACING_FRONT} */
    FRONT_CAMERA(CameraInfo.CAMERA_FACING_FRONT, "Front Camera - faces same direction as screen."),
    /** Select to use {@link CameraInfo#CAMERA_FACING_BACK} */
    BACK_CAMERA(CameraInfo.CAMERA_FACING_BACK, "Back Camera - faces opposite direction to screen.");
       
    
    /** Camera facing direction */
    private int cameraFacingDirection;
    
    /** Camera name/description */
    private String cameraLabel;
    
    
    PhoneVideoCamera(int cameraFacingDiretion, String cameraLabel)
    {
        this.cameraFacingDirection = cameraFacingDiretion;
        this.cameraLabel = cameraLabel;
    }
        
    /**
     * @return the camera facing direction to use.
     * {@link CameraInfo#CAMERA_FACING_BACK} or {@link CameraInfo#CAMERA_FACING_FRONT}.
     */
    public Integer getCameraFacingDirection()
    {
        return cameraFacingDirection;
    }
    
    /**
     * 
     * @return the selected camera text name/description for use in UI labels. 
     */
    public String getCameraLabel()
    {
        return cameraLabel;
    }    
    
    public String toString()
    {
        return cameraLabel;
    }
}
