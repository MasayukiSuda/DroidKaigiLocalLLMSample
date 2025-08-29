package com.daasuu.llmsample.data.llm.gemini

import android.content.Context
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GeminiNanoモデルの生成と管理を専門に行うクラス
 * スレッドセーフなモデル管理とキャッシング機能を提供
 */
@Singleton
class GeminiNanoModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compatibilityChecker: GeminiNanoCompatibilityChecker
) {
    @Volatile
    private var cachedModel: GenerativeModel? = null
    private val modelLock = Mutex()
    
    /**
     * モデルを取得する（必要に応じて作成）
     * スレッドセーフに実装されており、複数のコルーチンから同時に呼び出しても安全
     */
    suspend fun getOrCreateModel(): GenerativeModel? {
        modelLock.withLock {
            // キャッシュされたモデルがあれば返す
            cachedModel?.let { return it }
            
            // 互換性チェック
            val compatibility = compatibilityChecker.isDeviceSupported()
            if (compatibility !is DeviceCompatibility.Supported) {
                val message = compatibilityChecker.getCompatibilityMessage(compatibility)
                println("Gemini Nano model creation failed: $message")
                return null
            }
            
            // モデル作成
            cachedModel = try {
                println("Creating new Gemini Nano model...")
                GenerativeModel(
                    generationConfig {
                        context = this@GeminiNanoModelManager.context
                        temperature = 0.7f
                        topK = 40
                        maxOutputTokens = 1000
                    }
                ).also {
                    println("Gemini Nano model created successfully")
                }
            } catch (e: Exception) {
                println("Failed to create Gemini Nano model: ${e.message}")
                e.printStackTrace()
                null
            }
            
            return cachedModel
        }
    }
    
    /**
     * モデルをリリースする
     * メモリリークを防ぐため、アプリ終了時やプロバイダー切り替え時に呼び出す
     */
    suspend fun releaseModel() {
        modelLock.withLock {
            cachedModel?.let { model ->
                try {
                    model.close()
                    println("Gemini Nano model released")
                } catch (e: Exception) {
                    println("Error releasing Gemini Nano model: ${e.message}")
                }
            }
            cachedModel = null
        }
    }
    
    /**
     * モデルが利用可能かどうかを確認
     */
    suspend fun isModelAvailable(): Boolean {
        return getOrCreateModel() != null
    }
    
    /**
     * デバイス互換性のみをチェック（モデル作成はしない）
     * 軽量な互換性確認用
     */
    suspend fun isDeviceSupported(): Boolean {
        val compatibility = compatibilityChecker.isDeviceSupported()
        return compatibility is DeviceCompatibility.Supported
    }
}
