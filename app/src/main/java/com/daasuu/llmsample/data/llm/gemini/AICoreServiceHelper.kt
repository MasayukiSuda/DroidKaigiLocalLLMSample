package com.daasuu.llmsample.data.llm.gemini

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AICoreServiceHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AICoreServiceHelper"
        private const val AICORE_PACKAGE = "com.google.android.aicore"
        private const val AICORE_SERVICE = "com.google.android.aicore.AICoreLLMService"
        private const val AS_PACKAGE = "com.google.android.as"
    }
    
    private var serviceBinder: IBinder? = null
    private var isConnected = false
    
    /**
     * Check if AICore service is available on the device
     */
    fun isAICoreAvailable(): Boolean {
        return try {
            // Check if AICore package is installed
            context.packageManager.getPackageInfo(AICORE_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                // Fallback: Check Android System Intelligence package
                context.packageManager.getPackageInfo(AS_PACKAGE, 0)
                true
            } catch (e2: PackageManager.NameNotFoundException) {
                Log.d(TAG, "AICore service not found on device")
                false
            }
        }
    }
    
    /**
     * Attempt to connect to AICore service
     */
    suspend fun connectToAICore(): Boolean = suspendCancellableCoroutine { continuation ->
        if (isConnected) {
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }
        
        if (!isAICoreAvailable()) {
            Log.d(TAG, "AICore not available on device")
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }
        
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                Log.d(TAG, "AICore service connected")
                serviceBinder = binder
                isConnected = true
                continuation.resume(true)
            }
            
            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "AICore service disconnected")
                serviceBinder = null
                isConnected = false
            }
        }
        
        try {
            val intent = Intent().apply {
                component = ComponentName(AICORE_PACKAGE, AICORE_SERVICE)
            }
            
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            
            if (!bound) {
                Log.d(TAG, "Failed to bind to AICore service")
                continuation.resume(false)
            }
        } catch (e: SecurityException) {
            Log.d(TAG, "Security exception binding to AICore: ${e.message}")
            continuation.resume(false)
        } catch (e: Exception) {
            Log.d(TAG, "Exception binding to AICore: ${e.message}")
            continuation.resume(false)
        }
        
        continuation.invokeOnCancellation {
            if (isConnected) {
                try {
                    context.unbindService(serviceConnection)
                } catch (e: Exception) {
                    Log.d(TAG, "Error unbinding service: ${e.message}")
                }
                isConnected = false
                serviceBinder = null
            }
        }
    }
    
    /**
     * Generate text using AICore service (experimental implementation)
     */
    suspend fun generateText(prompt: String): String? {
        if (!isConnected || serviceBinder == null) {
            Log.d(TAG, "AICore service not connected")
            return null
        }
        
        return try {
            // This is a placeholder for actual AICore API calls
            // The real implementation would depend on the AICore AIDL interface
            // which is not publicly documented
            
            Log.d(TAG, "Attempting to generate text with AICore (experimental)")
            
            // For now, return a simulated response indicating the service was contacted
            "Gemini Nano response (experimental): Processed '$prompt' via AICore service."
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating text with AICore: ${e.message}")
            null
        }
    }
    
    /**
     * Disconnect from AICore service
     */
    fun disconnect() {
        if (isConnected) {
            try {
                // Context unbinding would be handled by the ServiceConnection
                isConnected = false
                serviceBinder = null
                Log.d(TAG, "Disconnected from AICore service")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from AICore: ${e.message}")
            }
        }
    }
    
    /**
     * Check if device supports Gemini Nano features
     */
    fun hasGeminiNanoSupport(): Boolean {
        // Check for supported device models and Android versions
        val supportedModels = listOf(
            "Pixel 8", "Pixel 8 Pro", "Pixel 8a",
            "Pixel 9", "Pixel 9 Pro", "Pixel 9 Pro XL", "Pixel 9 Pro Fold",
            "SM-S921", "SM-S926", "SM-S928", // Galaxy S24 series
            "SM-F956", "SM-F741", // Galaxy Z series
        )
        
        val deviceModel = android.os.Build.MODEL
        val manufacturer = android.os.Build.MANUFACTURER
        
        return supportedModels.any { 
            deviceModel.contains(it, ignoreCase = true) || 
            "${manufacturer} ${deviceModel}".contains(it, ignoreCase = true)
        } && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
}
