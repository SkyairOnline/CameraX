package com.example.camerax

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object Permission {

    fun checkPermission(context: Context, activity: Activity, permission: String, requestCode: Int){
        if(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
        }
    }
}