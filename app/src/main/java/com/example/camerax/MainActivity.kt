package com.example.camerax

import android.Manifest
import android.os.Bundle
import androidx.core.content.ContentProviderCompat.requireContext
import com.example.camerax.AppKeys.CAMERA_PERMISSION
import com.example.camerax.AppKeys.READ_STORAGE_PERMISSION
import com.example.camerax.AppKeys.WRITE_STORAGE_PERMISSION
import com.example.camerax.databinding.ActivityMainBinding

class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Permission.checkPermission(
            applicationContext,
            this,
            Manifest.permission.CAMERA,
            CAMERA_PERMISSION
        )
        Permission.checkPermission(
            applicationContext,
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            READ_STORAGE_PERMISSION
        )
        Permission.checkPermission(
            applicationContext,
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            WRITE_STORAGE_PERMISSION
        )
    }
}