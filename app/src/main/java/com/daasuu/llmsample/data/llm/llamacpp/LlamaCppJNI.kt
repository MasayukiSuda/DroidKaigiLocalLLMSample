package com.daasuu.llmsample.data.llm.llamacpp

object LlamaCppJNI {
    private var isLibraryLoaded = false
    
    init {
        try {
            System.loadLibrary("llamacpp-jni")
            isLibraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            // Library not available, use mock implementation
            isLibraryLoaded = false
        }
    }

    fun loadModel(
        modelPath: String,
        contextSize: Int = 2048,
        nGpuLayers: Int = 0
    ): Long {
        return if (isLibraryLoaded) {
            loadModelNative(modelPath, contextSize, nGpuLayers)
        } else {
            // Mock implementation
            1L // Return dummy model pointer
        }
    }

    fun unloadModel(modelPtr: Long) {
        if (isLibraryLoaded) {
            unloadModelNative(modelPtr)
        }
        // Mock: do nothing
    }

    fun generate(
        modelPtr: Long,
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        callback: GenerationCallback
    ): String {
        return if (isLibraryLoaded) {
            generateNative(modelPtr, prompt, maxTokens, temperature, topP, callback)
        } else {
            // Mock implementation
            mockGenerate(prompt, callback)
        }
    }

    fun getMemoryUsage(modelPtr: Long): Long {
        return if (isLibraryLoaded) {
            getMemoryUsageNative(modelPtr)
        } else {
            256 * 1024 * 1024L // 256MB mock
        }
    }

    fun getModelSizeMB(modelPtr: Long): Float {
        return if (isLibraryLoaded) {
            getModelSizeMBNative(modelPtr)
        } else {
            1024.0f // 1GB mock
        }
    }
    
    // Mock implementation for testing
    private fun mockGenerate(prompt: String, callback: GenerationCallback): String {
        val response = "Mock response for: $prompt"
        val tokens = response.split(" ")
        
        Thread {
            try {
                for (token in tokens) {
                    callback.onToken("$token ")
                    Thread.sleep(100)
                }
                callback.onComplete()
            } catch (e: Exception) {
                callback.onError(e.message ?: "Unknown error")
            }
        }.start()
        
        return response
    }

    // Native methods (only called when library is loaded)
    private external fun loadModelNative(
        modelPath: String,
        contextSize: Int,
        nGpuLayers: Int
    ): Long

    private external fun unloadModelNative(modelPtr: Long)

    private external fun generateNative(
        modelPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        callback: GenerationCallback
    ): String

    private external fun getMemoryUsageNative(modelPtr: Long): Long
    private external fun getModelSizeMBNative(modelPtr: Long): Float

    interface GenerationCallback {
        fun onToken(token: String)
        fun onComplete()
        fun onError(error: String)
    }
}