package com.soundly.sdk.v0.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Utility class for handling audio recording permissions.
 */
object PermissionUtils {
    
    /**
     * Required permissions for audio recording.
     */
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    
    /**
     * Checks if all required permissions are granted.
     * @param context The application context
     * @return true if all permissions are granted, false otherwise
     */
    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Gets the list of missing permissions.
     * @param context The application context
     * @return List of missing permission strings
     */
    fun getMissingPermissions(context: Context): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Checks if the RECORD_AUDIO permission is granted.
     * @param context The application context
     * @return true if permission is granted, false otherwise
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Checks if storage permissions are granted.
     * @param context The application context
     * @return true if storage permissions are granted, false otherwise
     */
    fun hasStoragePermissions(context: Context): Boolean {
        val writePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        
        val readPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        
        return writePermission && readPermission
    }
}
