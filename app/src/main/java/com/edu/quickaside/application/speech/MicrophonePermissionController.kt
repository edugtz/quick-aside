package com.edu.quickaside.application.speech

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

interface MicrophonePermissionController {
    fun isGranted(): Boolean

    fun shouldShowRationale(): Boolean

    fun request(onResult: (Boolean) -> Unit)
}

class AndroidMicrophonePermissionController(
    private val context: Context,
    private val requestPermission: ((Boolean) -> Unit) -> Unit,
) : MicrophonePermissionController {
    override fun isGranted(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    override fun shouldShowRationale(): Boolean = (context as? Activity)?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(
            it,
            Manifest.permission.RECORD_AUDIO,
        )
    } ?: false

    override fun request(onResult: (Boolean) -> Unit) {
        requestPermission(onResult)
    }
}
