package com.daasuu.llmsample.data.llm.gemini

import android.content.Context
import android.os.Build
// import com.google.mlkit.genai.GenerativeModels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiNanoCompatibilityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Check if the device supports Gemini Nano (on-device AI)
     */
    suspend fun isDeviceSupported(): DeviceCompatibility {
        // Check Android version (Gemini Nano requires Android 14+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return DeviceCompatibility.UnsupportedAndroidVersion(Build.VERSION.SDK_INT)
        }
        
        // Check if device model is supported
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        if (!isSupportedDevice(deviceModel)) {
            return DeviceCompatibility.UnsupportedDevice(deviceModel)
        }
        
        // Check for AICore availability
        return try {
            val aicoreHelper = AICoreServiceHelper(context)
            if (aicoreHelper.isAICoreAvailable() && aicoreHelper.hasGeminiNanoSupport()) {
                DeviceCompatibility.Supported
            } else {
                DeviceCompatibility.NotAvailable
            }
        } catch (e: Exception) {
            DeviceCompatibility.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Check if the device model is known to support Gemini Nano
     */
    private fun isSupportedDevice(deviceModel: String): Boolean {
        val supportedModels = listOf(
            // Google Pixel devices
            "Google Pixel 8", "Google Pixel 8 Pro", "Google Pixel 8a",
            "Google Pixel 9", "Google Pixel 9 Pro", "Google Pixel 9 Pro XL", "Google Pixel 9 Pro Fold",
            
            // Samsung Galaxy devices
            "samsung SM-S921", "samsung SM-S926", "samsung SM-S928", // Galaxy S24 series
            "samsung SM-S711", "samsung SM-S716", "samsung SM-S718", // Galaxy S24 FE
            "samsung SM-F956", "samsung SM-F741", // Galaxy Z Fold6, Z Flip6
            "SM-S921", "SM-S926", "SM-S928", // Galaxy S24 series (without samsung prefix)
            "SM-F956", "SM-F741", // Galaxy Z series (without samsung prefix)
            
            // Other supported devices
            "motorola edge 50 ultra", "motorola razr 50 ultra",
            "xiaomi 14T", "xiaomi 14T Pro", "xiaomi MIX Flip",
            "realme RMX3031" // GT 6
        )
        
        return supportedModels.any { supportedModel ->
            deviceModel.contains(supportedModel, ignoreCase = true)
        }
    }
    
    /**
     * Get user-friendly compatibility message
     */
    fun getCompatibilityMessage(compatibility: DeviceCompatibility): String {
        return when (compatibility) {
            is DeviceCompatibility.Supported -> 
                "✅ Gemini Nanoが利用可能です"
            
            is DeviceCompatibility.UnsupportedAndroidVersion -> 
                "❌ Android 14以上が必要です（現在: Android ${compatibility.currentVersion}）"
            
            is DeviceCompatibility.UnsupportedDevice -> 
                "❌ この端末（${compatibility.deviceModel}）はGemini Nanoに対応していません"
            
            is DeviceCompatibility.NotAvailable -> 
                "❌ Gemini Nanoがこの端末で利用できません。設定でオンデバイスAIを有効化してください"
            
            is DeviceCompatibility.Unknown -> 
                "⚠️ Gemini Nanoの利用可能性を確認できませんでした"
            
            is DeviceCompatibility.Error -> 
                "❌ エラーが発生しました: ${compatibility.message}"
        }
    }
}

sealed class DeviceCompatibility {
    object Supported : DeviceCompatibility()
    data class UnsupportedAndroidVersion(val currentVersion: Int) : DeviceCompatibility()
    data class UnsupportedDevice(val deviceModel: String) : DeviceCompatibility()
    object NotAvailable : DeviceCompatibility()
    object Unknown : DeviceCompatibility()
    data class Error(val message: String) : DeviceCompatibility()
}
