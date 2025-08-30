package com.daasuu.llmsample.data.llm.task

import android.content.Context
import android.util.Log
import com.daasuu.llmsample.data.benchmark.BenchmarkMode
import com.daasuu.llmsample.data.prompts.CommonPrompts
import com.daasuu.llmsample.data.settings.SettingsRepository
import com.daasuu.llmsample.domain.LLMRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : LLMRepository {

    private val TAG = "TaskGenAI"
    private var isInitialized = false
    private var taskModelPath: String? = null

    // MediaPipe GenAI (TextGenerator) via reflection
    private var mpTextGenerator: Any? = null

    override suspend fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "TaskRepository already initialized, skipping")
            return
        }
        
        Log.d(TAG, "Initializing TaskRepository...")
        withContext(Dispatchers.IO) {
            try {
                // 前回のリソースが残っていれば確実にクリア
                mpTextGenerator?.let {
                    Log.w(TAG, "Previous generator instance found, clearing...")
                    mpTextGenerator = null
                }
                
                val destDir = File(context.filesDir, "models/lite_rt/gemma3")
                if (!destDir.exists()) destDir.mkdirs()
                copyAssets("models/lite_rt/gemma3", destDir)
                val taskFile = destDir.listFiles()?.firstOrNull { it.extension.equals("task", true) }
                taskModelPath = taskFile?.absolutePath
                Log.d(TAG, "taskModelPath=$taskModelPath")
                
                if (taskModelPath != null) {
                    val isGpuEnabled = settingsRepository.isGpuEnabled.first()
                    
                    // エミュレータ検知
                    val isEmulator = isRunningOnEmulator()
                    if (isEmulator) {
                        Log.w(TAG, "Running on emulator, disabling GPU acceleration")
                    }
                    
                    val actualGpuEnabled = isGpuEnabled && !isEmulator
                    Log.d(TAG, "Starting MediaPipe initialization with GPU=${actualGpuEnabled} (requested: ${isGpuEnabled}, emulator: ${isEmulator})")
                    
                    // 段階的初期化: GPU有効 → GPU無効 → 初期化スキップ
                    var initSuccess = false
                    
                    if (actualGpuEnabled) {
                        try {
                            Log.d(TAG, "Attempting GPU initialization...")
                            tryInitMediaPipeGenerator(true)
                            if (mpTextGenerator != null) {
                                initSuccess = true
                                Log.d(TAG, "GPU initialization successful")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "GPU initialization failed, falling back to CPU: ${e.message}")
                            mpTextGenerator = null
                        }
                    }
                    
                    if (!initSuccess) {
                        try {
                            Log.d(TAG, "Attempting CPU initialization...")
                            tryInitMediaPipeGenerator(false)
                            if (mpTextGenerator != null) {
                                initSuccess = true
                                Log.d(TAG, "CPU initialization successful")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "CPU initialization also failed: ${e.message}")
                            mpTextGenerator = null
                        }
                    }
                    
                    isInitialized = initSuccess
                    Log.d(TAG, "TaskRepository initialization completed: ${isInitialized}")
                } else {
                    Log.w(TAG, "No .task model file found, TaskRepository not initialized")
                    isInitialized = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "TaskRepository initialization failed", e)
                mpTextGenerator = null
                isInitialized = false
            }
        }
    }

    override suspend fun release() {
        Log.d(TAG, "Releasing TaskRepository...")
        withContext(Dispatchers.IO) {
            try {
                // MediaPipe GenAI インスタンスを明示的にクリア
                mpTextGenerator?.let { generator ->
                    try {
                        // MediaPipe GenAIに終了処理があれば呼び出し
                        val closeMethod = generator.javaClass.methods.firstOrNull { 
                            it.name == "close" || it.name == "release" || it.name == "destroy"
                        }
                        closeMethod?.invoke(generator)
                        Log.d(TAG, "MediaPipe generator closed successfully")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to close MediaPipe generator: ${e.message}")
                    }
                }
                
                mpTextGenerator = null
                isInitialized = false
                Log.d(TAG, "TaskRepository released successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during TaskRepository release", e)
                // エラーがあってもisInitializedをfalseにしてリセット
                mpTextGenerator = null
                isInitialized = false
            }
        }
    }

    private fun isRunningOnEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.FINGERPRINT.startsWith("unknown") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK") ||
                android.os.Build.MANUFACTURER.contains("Genymotion") ||
                android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic") ||
                "google_sdk" == android.os.Build.PRODUCT
    }

    override suspend fun isAvailable(): Boolean = isInitialized

    override suspend fun generateChatResponse(prompt: String): Flow<String> = channelFlow {
        if (!isInitialized) initialize()
        if (!isInitialized) {
            send("Error: .task model not found"); return@channelFlow
        }

        // ベンチマークモードに応じてプロンプトを処理
        val finalPrompt = if (BenchmarkMode.isCurrentlyEnabled()) {
            CommonPrompts.buildChatPrompt(prompt)
        } else {
            prompt // 最適化モード: プロンプトをそのまま使用
        }

        val generated = withContext(Dispatchers.IO) { runMediaPipeGenerate(finalPrompt) }
        if (generated != null) {
            generated.split(" ").forEach { token ->
                send("$token ")
                delay(12)
            }
        } else {
            Log.w(TAG, "MediaPipe generate failed; falling back to mock")
            withContext(Dispatchers.IO) {
                ("[task mock] Gemma3: $finalPrompt").split(" ").forEach {
                    send(it + " ")
                    delay(20)
                }
            }
        }
    }

    override suspend fun summarizeText(text: String): Flow<String> = channelFlow {
        if (!isInitialized) initialize()
        if (!isInitialized) {
            send("Error: .task model not found"); return@channelFlow
        }

        val prompt = if (BenchmarkMode.isCurrentlyEnabled()) {
            CommonPrompts.buildSummarizationPrompt(text)
        } else {
            "要約してください: \n\n$text" // 最適化モード: MediaPipe GenAI API向けシンプルプロンプト
        }

        val full = withContext(Dispatchers.IO) { runMediaPipeGenerate(prompt) }
        send(full ?: ("- 要約(擬似): " + text.take(40) + "..."))
    }

    override suspend fun proofreadText(text: String): Flow<String> = channelFlow {
        if (!isInitialized) initialize()
        if (!isInitialized) {
            send("{}"); return@channelFlow
        }

        val prompt = if (BenchmarkMode.isCurrentlyEnabled()) {
            CommonPrompts.buildProofreadingPrompt(text)
        } else {
            // 最適化モード: MediaPipe GenAI API向け詳細JSON指示
            val instruction = """
                以下の日本語文を正しく自然な表現に校正してください。出力は校正後の文章だけを返してください。
            """.trimIndent()
            "$instruction\n\n入力: $text"
        }

        val full = withContext(Dispatchers.IO) { runMediaPipeGenerate(prompt) }
        send(full ?: "{\"corrected_text\":\"$text\",\"corrections\":[]}")
    }

    private fun copyAssets(assetPath: String, destDir: File) {
        try {
            val am = context.assets
            val entries = am.list(assetPath) ?: return
            for (name in entries) {
                val child = if (assetPath.isEmpty()) name else "$assetPath/$name"
                val children = am.list(child)
                if (children != null && children.isNotEmpty()) {
                    val dir = File(destDir, name)
                    if (!dir.exists()) dir.mkdirs()
                    copyAssets(child, dir)
                } else {
                    val out = File(destDir, name)
                    if (!out.exists()) {
                        am.open(child).use { input ->
                            out.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "copyAssets failed: ${t.message}")
        }
    }

    private fun tryInitMediaPipeGenerator(enableGpu: Boolean = false) {
        val modelPath = taskModelPath ?: return
        val assetRelative = "models/lite_rt/gemma3/" + File(modelPath).name
        
        Log.d(TAG, "tryInitMediaPipeGenerator called with enableGpu: $enableGpu")
        Log.d(TAG, "Model path: $modelPath")
        Log.d(TAG, "Asset relative path: $assetRelative")
        
        val candidates = listOf(
            Triple(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference",
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions",
                null // BaseOptionsは別途確認
            )
        )
        for ((genCls, optCls, baseCls) in candidates) {
            try {
                Log.d(TAG, "Trying $genCls with GPU=$enableGpu...")
                val genClass = Class.forName(genCls)
                Log.d(TAG, "Successfully loaded class: ${genClass.name}")
                var instance: Any? = null

                // 1) Prefer simple path: createFromFile(Context, String)
                run {
                    val byFile =
                        genClass.methods.firstOrNull { it.name == "createFromFile" && it.parameterTypes.size == 2 }
                    if (byFile != null) {
                        try {
                            instance = byFile.invoke(null, context, modelPath)
                            if (instance == null) instance =
                                byFile.invoke(null, context, assetRelative)
                        } catch (t: Throwable) {
                            Log.w(TAG, "invoke createFromFile failed: ${t.message}")
                        }
                    }
                }
                if (instance != null) {
                    mpTextGenerator = instance
                    Log.d(TAG, "Initialized via createFromFile on $genCls")
                    return
                }

                // 2) Fallback: createFromOptions(Context, Options) if BaseOptions is available
                var baseOptionsClass = if (baseCls != null) try {
                    Class.forName(baseCls)
                } catch (_: Throwable) {
                    null
                } else null

                // Try alternative BaseOptions locations if not found
                val alternativeBaseOptions = listOf(
                    "com.google.mediapipe.tasks.core.BaseOptions",
                    "com.google.mediapipe.framework.MediaPipeException.BaseOptions",
                    "com.google.mediapipe.tasks.genai.llminference.BaseOptions"
                )

                for (altBaseOptions in alternativeBaseOptions) {
                    try {
                        val altClass = Class.forName(altBaseOptions)
                        Log.d(TAG, "Found BaseOptions at: $altBaseOptions")
                        baseOptionsClass = altClass
                        break
                    } catch (e: ClassNotFoundException) {
                        Log.d(TAG, "BaseOptions not found at: $altBaseOptions")
                    }
                }

                if (baseOptionsClass == null) {
                    Log.w(
                        TAG,
                        "BaseOptions class not found in any location for $genCls; trying inner Options only"
                    )
                    Log.d(TAG, "Attempting direct LlmInferenceOptions initialization...")
                    // Try builder on LlmInference$LlmInferenceOptions directly
                    try {
                        val optionsClass = Class.forName(optCls)
                        val optBuilder = optionsClass.getMethod("builder").invoke(null)

                        // 利用可能なメソッドをログ出力
                        Log.d(
                            TAG,
                            "Available LlmInferenceOptions builder methods: ${
                                optBuilder.javaClass.methods.joinToString(", ") { "${it.name}(${it.parameterTypes.joinToString { it.simpleName }})" }
                            }"
                        )

                        // GPU関連メソッドを探す
                        val gpuMethods = optBuilder.javaClass.methods.filter { method ->
                            val name = method.name.lowercase()
                            name.contains("gpu") || name.contains("delegate") || name.contains("acceleration")
                        }
                        Log.d(
                            TAG,
                            "GPU related methods: ${gpuMethods.map { it.name }.joinToString(", ")}"
                        )

                        // setPreferredBackend メソッドを使ってGPU設定を試行（GPU設定が有効な場合のみ）
                        if (enableGpu) try {
                            val setPreferredBackendMethod =
                                optBuilder.javaClass.methods.firstOrNull {
                                    it.name == "setPreferredBackend" && it.parameterTypes.size == 1
                                }

                            if (setPreferredBackendMethod != null) {
                                Log.d(
                                    TAG,
                                    "Found setPreferredBackend method with parameter type: ${setPreferredBackendMethod.parameterTypes[0].name}"
                                )

                                // Backend enum のGPU関連値を探す
                                val backendClass = setPreferredBackendMethod.parameterTypes[0]
                                val backendConstants = backendClass.enumConstants
                                Log.d(
                                    TAG,
                                    "Available Backend values: ${
                                        backendConstants?.joinToString(", ") { it.toString() } ?: "none"
                                    }"
                                )

                                // GPU関連のBackend値を探して設定
                                val gpuBackend = backendConstants?.firstOrNull { backend ->
                                    val backendName = backend.toString().lowercase()
                                    backendName.contains("gpu") || backendName.contains("opencl") || backendName.contains(
                                        "vulkan"
                                    )
                                }

                                if (gpuBackend != null) {
                                    setPreferredBackendMethod.invoke(optBuilder, gpuBackend)
                                    Log.d(TAG, "GPU backend set successfully: $gpuBackend")
                                } else {
                                    Log.w(TAG, "No GPU backend found in available options")
                                }
                            } else {
                                Log.d(TAG, "setPreferredBackend method not found")
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to set GPU backend: ${t.message}")
                        } else {
                            Log.d(TAG, "GPU backend disabled by user setting")
                        }

                        // setModelPath or setModelAssetPath on Options builder (some builds expose this directly)
                        val setModelPath2 = optBuilder.javaClass.methods.firstOrNull {
                            it.name == "setModelPath" && it.parameterTypes.contentEquals(
                                arrayOf(
                                    String::class.java
                                )
                            )
                        }
                        val setModelAssetPath2 = optBuilder.javaClass.methods.firstOrNull {
                            it.name == "setModelAssetPath" && it.parameterTypes.contentEquals(
                                arrayOf(String::class.java)
                            )
                        }
                        setModelPath2?.invoke(optBuilder, modelPath)
                        setModelAssetPath2?.invoke(
                            optBuilder,
                            assetRelative
                        )
                        val optionsBuilt =
                            optBuilder.javaClass.getMethod("build").invoke(optBuilder)
                        val methods = genClass.methods.filter { it.name == "createFromOptions" }
                        for (m in methods) {
                            try {
                                val params = m.parameterTypes
                                instance = when (params.size) {
                                    2 -> m.invoke(null, context, optionsBuilt)
                                    3 -> m.invoke(null, context, optionsBuilt, null)
                                    else -> null
                                }
                                if (instance != null) break
                            } catch (ie: Throwable) {
                                Log.w(TAG, "invoke createFromOptions failed: ${ie.message}")
                            }
                        }
                        if (instance != null) {
                            mpTextGenerator = instance
                            Log.d(
                                TAG,
                                "Initialized with $genCls via LlmInferenceOptions (GPU backend attempted)"
                            )
                            return
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "inner Options builder path failed: ${t.message}")
                    }
                    continue
                }
                val baseBuilder = baseOptionsClass!!.getMethod("builder").invoke(null)

                // GPU delegate設定を試行（詳細ログ付き）（GPU設定が有効な場合のみ）
                if (enableGpu) try {
                    Log.d(TAG, "Attempting to set GPU delegate...")

                    // まず利用可能なメソッドを確認
                    Log.d(
                        TAG,
                        "Available BaseOptions builder methods: ${
                            baseBuilder.javaClass.methods.map { it.name }.joinToString(", ")
                        }"
                    )

                    // 複数のパターンでGPU delegate設定を試行
                    var delegateSet = false

                    // パターン1: BaseOptions.Delegate.GPU
                    try {
                        val delegateClass =
                            Class.forName("com.google.mediapipe.tasks.core.BaseOptions\$Delegate")
                        Log.d(TAG, "Found Delegate class: ${delegateClass.name}")
                        Log.d(
                            TAG,
                            "Available Delegate fields: ${
                                delegateClass.declaredFields.map { it.name }.joinToString(", ")
                            }"
                        )
                        val gpuDelegate = delegateClass.getDeclaredField("GPU").get(null)
                        val setDelegate = baseBuilder.javaClass.methods.firstOrNull {
                            it.name == "setDelegate" && it.parameterTypes.size == 1
                        }
                        if (setDelegate != null) {
                            setDelegate.invoke(baseBuilder, gpuDelegate)
                            delegateSet = true
                            Log.d(TAG, "GPU delegate set via BaseOptions.Delegate.GPU")
                        } else {
                            Log.w(TAG, "setDelegate method not found")
                        }
                    } catch (t1: Throwable) {
                        Log.w(TAG, "Pattern 1 failed: ${t1.message}")
                    }

                    // パターン2: useGpu メソッド
                    if (!delegateSet) {
                        try {
                            val useGpuMethod = baseBuilder.javaClass.methods.firstOrNull {
                                it.name == "useGpu" && it.parameterTypes.isEmpty()
                            }
                            if (useGpuMethod != null) {
                                useGpuMethod.invoke(baseBuilder)
                                delegateSet = true
                                Log.d(TAG, "GPU enabled via useGpu() method")
                            }
                        } catch (t2: Throwable) {
                            Log.w(TAG, "Pattern 2 failed: ${t2.message}")
                        }
                    }

                    // パターン3: setUseGpu メソッド
                    if (!delegateSet) {
                        try {
                            val setUseGpuMethod = baseBuilder.javaClass.methods.firstOrNull {
                                it.name == "setUseGpu" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Boolean::class.java
                            }
                            if (setUseGpuMethod != null) {
                                setUseGpuMethod.invoke(baseBuilder, true)
                                delegateSet = true
                                Log.d(TAG, "GPU enabled via setUseGpu(true) method")
                            }
                        } catch (t3: Throwable) {
                            Log.w(TAG, "Pattern 3 failed: ${t3.message}")
                        }
                    }

                    if (!delegateSet) {
                        Log.w(TAG, "All GPU delegate patterns failed, using CPU")
                    }

                } catch (t: Throwable) {
                    Log.w(TAG, "GPU delegate setup failed completely: ${t.message}")
                } else {
                    Log.d(TAG, "GPU delegate disabled by user setting")
                }

                val setModelPath = baseBuilder.javaClass.methods.firstOrNull {
                    it.name == "setModelPath" && it.parameterTypes.contentEquals(arrayOf(String::class.java))
                }
                val setModelAssetPath = baseBuilder.javaClass.methods.firstOrNull {
                    it.name == "setModelAssetPath" && it.parameterTypes.contentEquals(arrayOf(String::class.java))
                }
                setModelPath?.invoke(baseBuilder, modelPath)
                setModelAssetPath?.invoke(baseBuilder, assetRelative)
                val baseBuilt = baseBuilder.javaClass.getMethod("build").invoke(baseBuilder)

                val optionsClass = Class.forName(optCls)
                val optBuilder = optionsClass.getMethod("builder").invoke(null)
                val setBaseOptions =
                    optBuilder.javaClass.methods.firstOrNull { it.name == "setBaseOptions" && it.parameterTypes.size == 1 }
                setBaseOptions?.invoke(optBuilder, baseBuilt)
                val optionsBuilt = optBuilder.javaClass.getMethod("build").invoke(optBuilder)

                val methods = genClass.methods.filter { it.name == "createFromOptions" }
                for (m in methods) {
                    try {
                        val params = m.parameterTypes
                        instance = when (params.size) {
                            2 -> m.invoke(null, context, optionsBuilt)
                            3 -> m.invoke(null, context, optionsBuilt, null)
                            else -> null
                        }
                        if (instance != null) break
                    } catch (ie: Throwable) {
                        Log.w(TAG, "invoke createFromOptions failed: ${ie.message}")
                    }
                }
                if (instance != null) {
                    mpTextGenerator = instance
                    Log.d(
                        TAG,
                        "Initialized with $genCls via BaseOptions (GPU acceleration attempted)"
                    )
                    return
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Class not available or init failed for $genCls: ${e.message}")
            }
        }
    }

    private fun runMediaPipeGenerate(prompt: String): String? {
        val gen = mpTextGenerator ?: return null
        return try {
            Log.d(TAG, "Starting MediaPipe generation with prompt length: ${prompt.length}")
            val m =
                gen.javaClass.methods.firstOrNull { (it.name == "generate" || it.name == "generateResponse") && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java }
            
            if (m == null) {
                Log.w(TAG, "No suitable generate method found in MediaPipe generator")
                return null
            }
            
            Log.d(TAG, "Invoking MediaPipe generate method: ${m.name}")
            val result = m.invoke(gen, prompt)
            Log.d(TAG, "MediaPipe generation completed, result type: ${result?.javaClass?.simpleName}")
            
            when (result) {
                is String -> {
                    Log.d(TAG, "Direct string result, length: ${result.length}")
                    result
                }
                else -> {
                    Log.d(TAG, "Non-string result, extracting text...")
                    val getText =
                        result?.javaClass?.methods?.firstOrNull { it.name == "getText" && it.parameterTypes.isEmpty() }
                    val getOut =
                        result?.javaClass?.methods?.firstOrNull { it.name == "getOutputText" && it.parameterTypes.isEmpty() }
                    val extractedText = (getText?.invoke(result) as? String) ?: (getOut?.invoke(result) as? String)
                    Log.d(TAG, "Extracted text length: ${extractedText?.length ?: 0}")
                    extractedText
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "MediaPipe generation failed with exception: ${t.javaClass.simpleName}: ${t.message}", t)
            null
        }
    }
}


