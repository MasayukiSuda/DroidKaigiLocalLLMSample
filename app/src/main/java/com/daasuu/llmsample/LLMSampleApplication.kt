package com.daasuu.llmsample

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.google.android.gms.tflite.java.TfLite

@HiltAndroidApp
class LLMSampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize LiteRT via Google Play services (default options)
        try {
            TfLite.initialize(this)
        } catch (_: Throwable) {
            // ignore
        }
    }
}