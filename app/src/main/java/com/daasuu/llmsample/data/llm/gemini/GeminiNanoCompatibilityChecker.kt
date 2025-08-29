package com.daasuu.llmsample.data.llm.gemini

import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiNanoCompatibilityChecker @Inject constructor() {

    /**
     * Check if the device supports Gemini Nano (on-device AI)
     */
    fun isDeviceSupported(): DeviceCompatibility {
        // Check Android version (Gemini Nano requires Android 14+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return DeviceCompatibility.UnsupportedAndroidVersion(Build.VERSION.SDK_INT)
        }

        // Check if device model is supported
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        if (!isSupportedDevice(deviceModel)) {
            return DeviceCompatibility.UnsupportedDevice(deviceModel)
        }

        // Basic compatibility check passed
        // Actual availability will be determined when trying to initialize GenerativeModel
        return DeviceCompatibility.Supported
    }

    /**
     * Check if the device model is known to support Gemini Nano
     */
    private fun isSupportedDevice(deviceModel: String): Boolean {
        val supportedModels = listOf(
            // Google Pixel９ devices
            "Google Pixel 9",
            "Google Pixel 9 Pro",
            "Google Pixel 9 Pro XL",
            "Google Pixel 9 Pro Fold",
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
