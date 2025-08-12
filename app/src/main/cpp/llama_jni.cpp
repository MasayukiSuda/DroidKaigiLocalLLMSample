#include <jni.h>
#include <string>
#include <android/log.h>
#include <memory>
#include <vector>
#include <thread>
#include <chrono>
#include <mutex>
#include <cstring>
#include <sstream>
#include <limits>

#define LOG_TAG "LlamaCppJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward declarations for llama.cpp (if available)
#ifdef LLAMA_CPP_AVAILABLE
#include "llama.h"

struct llama_context_wrapper {
    llama_model* model;
    llama_context* ctx;
    llama_sampler* sampler;
    std::vector<llama_token> tokens;
    int32_t n_ctx;
    int32_t n_batch;
    std::mutex generation_mutex;
    bool is_generating;
    
    llama_context_wrapper() : model(nullptr), ctx(nullptr), sampler(nullptr), n_ctx(2048), n_batch(32), is_generating(false) {}
    
    ~llama_context_wrapper() {
        if (sampler) {
            llama_sampler_free(sampler);
        }
        if (ctx) {
            llama_free(ctx);
        }
        if (model) {
            llama_model_free(model);
        }
    }
};
#else
// Mock structure when llama.cpp is not available
struct llama_context_wrapper {
    void* model;
    void* ctx;
    std::vector<int> tokens;
    int32_t n_ctx;
    int32_t n_batch;
    std::mutex generation_mutex;
    bool is_generating;
    
    llama_context_wrapper() : model(nullptr), ctx(nullptr), n_ctx(2048), n_batch(32), is_generating(false) {}
};
#endif

extern "C" {

// Model management
JNIEXPORT jlong JNICALL
Java_com_daasuu_llmsample_data_llm_llamacpp_LlamaCppJNI_loadModelNative(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath,
        jint contextSize,
        jint nGpuLayers) {
    
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);
    
    try {
        auto wrapper = std::make_unique<llama_context_wrapper>();
        wrapper->n_ctx = contextSize;
        
#ifdef LLAMA_CPP_AVAILABLE
        // Initialize llama backend
        llama_backend_init();
        
        // Load model
        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = nGpuLayers;
        model_params.use_mmap = true;
        model_params.use_mlock = false;
        
        wrapper->model = llama_model_load_from_file(path, model_params);
        if (!wrapper->model) {
            LOGE("Failed to load model from %s", path);
            env->ReleaseStringUTFChars(modelPath, path);
            return 0;
        }
        
        // Verify model properties
        LOGI("Model loaded successfully");
        
        // Test vocabulary access immediately after loading
        const struct llama_vocab* test_vocab = llama_model_get_vocab(wrapper->model);
        if (!test_vocab) {
            LOGE("WARNING: Cannot get vocabulary from loaded model");
        } else {
            LOGI("Vocabulary accessible from model");
            
            // Test simple tokenization right after model load
            const char* test_str = "test";
            int test_tokens = llama_tokenize(test_vocab, test_str, strlen(test_str), nullptr, 0, true, false);
            LOGI("Initial tokenization test result: %d tokens for 'test'", test_tokens);
        }
        
        // Create context
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = contextSize;
        ctx_params.n_batch = wrapper->n_batch;
        ctx_params.n_threads = std::min(4, (int)std::thread::hardware_concurrency());
        // ctx_params.seed = -1; // Random seed (removed in newer versions)
        
        wrapper->ctx = llama_init_from_model(wrapper->model, ctx_params);
        if (!wrapper->ctx) {
            LOGE("Failed to create context");
            env->ReleaseStringUTFChars(modelPath, path);
            return 0;
        }
        
        // Initialize sampler chain
        llama_sampler_chain_params chain_params = llama_sampler_chain_default_params();
        wrapper->sampler = llama_sampler_chain_init(chain_params);
        llama_sampler_chain_add(wrapper->sampler, llama_sampler_init_top_p(0.9, 1));
        llama_sampler_chain_add(wrapper->sampler, llama_sampler_init_temp(0.8));
        llama_sampler_chain_add(wrapper->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        
        LOGI("Model loaded successfully. Context size: %d", contextSize);
#else
        // Mock implementation
        wrapper->model = (void*)0x1;
        wrapper->ctx = (void*)0x1;
        LOGI("Mock model loaded: %s", path);
#endif
        
        env->ReleaseStringUTFChars(modelPath, path);
        return reinterpret_cast<jlong>(wrapper.release());
        
    } catch (const std::exception& e) {
        LOGE("Exception loading model: %s", e.what());
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_daasuu_llmsample_data_llm_llamacpp_LlamaCppJNI_unloadModelNative(
        JNIEnv *env,
        jobject /* this */,
        jlong modelPtr) {
    
    if (modelPtr == 0) return;
    
    auto wrapper = reinterpret_cast<llama_context_wrapper*>(modelPtr);
    
    // Wait for any ongoing generation to complete
    {
        std::lock_guard<std::mutex> lock(wrapper->generation_mutex);
        wrapper->is_generating = false;
    }
    
    delete wrapper;
    
#ifdef LLAMA_CPP_AVAILABLE
    llama_backend_free();
#endif
    
    LOGI("Model unloaded: %ld", modelPtr);
}

// Text generation
JNIEXPORT jstring JNICALL
Java_com_daasuu_llmsample_data_llm_llamacpp_LlamaCppJNI_generateNative(
        JNIEnv *env,
        jobject /* this */,
        jlong modelPtr,
        jstring prompt,
        jint maxTokens,
        jfloat temperature,
        jfloat topP,
        jobject callback) {
    
    if (modelPtr == 0) {
        LOGE("Invalid model pointer");
        return env->NewStringUTF("Error: Invalid model");
    }
    
    auto wrapper = reinterpret_cast<llama_context_wrapper*>(modelPtr);
    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    
    // Get callback methods
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "()V");
    jmethodID onErrorMethod = env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V");
    
    std::string response;
    
    {
        std::lock_guard<std::mutex> lock(wrapper->generation_mutex);
        if (wrapper->is_generating) {
            LOGE("Generation already in progress");
            env->ReleaseStringUTFChars(prompt, promptStr);
            return env->NewStringUTF("Error: Generation in progress");
        }
        wrapper->is_generating = true;
    }
    
    try {
#ifdef LLAMA_CPP_AVAILABLE
        LOGI("Starting generation with prompt: %s", promptStr);
        
        // Tokenize prompt
        const struct llama_model* model = llama_get_model(wrapper->ctx);
        if (!model) {
            LOGE("Model is null - failed to get model from context");
            jstring errorStr = env->NewStringUTF("Model not loaded properly");
            env->CallVoidMethod(callback, onErrorMethod, errorStr);
            env->DeleteLocalRef(errorStr);
            wrapper->is_generating = false;
            env->ReleaseStringUTFChars(prompt, promptStr);
            return env->NewStringUTF("Error: Model not loaded");
        }
        
        // Check if prompt is valid
        if (!promptStr || strlen(promptStr) == 0) {
            LOGE("Empty or null prompt provided");
            jstring errorStr = env->NewStringUTF("Empty prompt");
            env->CallVoidMethod(callback, onErrorMethod, errorStr);
            env->DeleteLocalRef(errorStr);
            wrapper->is_generating = false;
            env->ReleaseStringUTFChars(prompt, promptStr);
            return env->NewStringUTF("Error: Empty prompt");
        }
        
        // Check for valid UTF-8 encoding and reasonable length
        size_t prompt_len = strlen(promptStr);
        if (prompt_len > 8192) { // Reasonable limit for prompt length
            LOGE("Prompt too long: %zu characters", prompt_len);
            jstring errorStr = env->NewStringUTF("Prompt too long");
            env->CallVoidMethod(callback, onErrorMethod, errorStr);
            env->DeleteLocalRef(errorStr);
            wrapper->is_generating = false;
            env->ReleaseStringUTFChars(prompt, promptStr);
            return env->NewStringUTF("Error: Prompt too long");
        }
        
        LOGI("Processing prompt: length=%zu, content='%.100s%s'", 
             prompt_len, promptStr, prompt_len > 100 ? "..." : "");
        
        // Get vocabulary from model first
        const struct llama_vocab* vocab = llama_model_get_vocab(model);
        if (!vocab) {
            LOGE("Failed to get vocabulary from model");
            jstring errorStr = env->NewStringUTF("Vocabulary not available");
            env->CallVoidMethod(callback, onErrorMethod, errorStr);
            env->DeleteLocalRef(errorStr);
            wrapper->is_generating = false;
            env->ReleaseStringUTFChars(prompt, promptStr);
            return env->NewStringUTF("Error: Vocabulary not available");
        }
        
        LOGI("Attempting tokenization with vocabulary");

        // Phase 1: query required token buffer size
        int32_t n_tokens_res = llama_tokenize(
            vocab,
            promptStr,
            static_cast<int32_t>(strlen(promptStr)),
            nullptr,
            0,
            /*add_special=*/true,
            /*parse_special=*/false);

        if (n_tokens_res == std::numeric_limits<int32_t>::min()) {
            LOGE("Tokenization failed: input too large (int32 overflow)");
            jstring errorStr = env->NewStringUTF("Prompt too large to tokenize");
            env->CallVoidMethod(callback, onErrorMethod, errorStr);
            env->DeleteLocalRef(errorStr);
            wrapper->is_generating = false;
            env->ReleaseStringUTFChars(prompt, promptStr);
            return env->NewStringUTF("Error: Prompt too large");
        }

        int32_t n_tokens_needed = n_tokens_res < 0 ? -n_tokens_res : n_tokens_res;
        LOGI("Tokenization buffer required: %d tokens", n_tokens_needed);

        // Phase 2: allocate and tokenize into buffer
        wrapper->tokens.resize(n_tokens_needed);
        int32_t n_tokens = llama_tokenize(
            vocab,
            promptStr,
            static_cast<int32_t>(strlen(promptStr)),
            wrapper->tokens.data(),
            static_cast<int32_t>(wrapper->tokens.size()),
            /*add_special=*/true,
            /*parse_special=*/false);

        if (n_tokens < 0) {
            // This should not happen after allocating the required size, but handle defensively
            LOGE("Tokenization failed unexpectedly after allocation");
            jstring errorStr = env->NewStringUTF("Tokenization failed");
            env->CallVoidMethod(callback, onErrorMethod, errorStr);
            env->DeleteLocalRef(errorStr);
            wrapper->is_generating = false;
            env->ReleaseStringUTFChars(prompt, promptStr);
            return env->NewStringUTF("Error: Tokenization failed");
        }
        LOGI("Tokenization successful: %d tokens", n_tokens);
        // shrink to actual token count to avoid reading uninitialized values
        wrapper->tokens.resize(n_tokens);

        // Evaluate prompt
        int n_eval = 0;
        for (size_t i = 0; i < wrapper->tokens.size(); i += wrapper->n_batch) {
            int n_tokens_batch = std::min(wrapper->n_batch, (int)(wrapper->tokens.size() - i));
            struct llama_batch batch = llama_batch_get_one(&wrapper->tokens[i], n_tokens_batch);

            // When pos == nullptr, llama_decode will track positions automatically
            if (llama_decode(wrapper->ctx, batch)) {
                LOGE("Failed to decode prompt");
                jstring errorStr = env->NewStringUTF("Prompt evaluation failed");
                env->CallVoidMethod(callback, onErrorMethod, errorStr);
                env->DeleteLocalRef(errorStr);
                wrapper->is_generating = false;
                env->ReleaseStringUTFChars(prompt, promptStr);
                return env->NewStringUTF("Error: Prompt evaluation failed");
            }
            n_eval += n_tokens_batch;
        }
        
        // Generate tokens
        for (int i = 0; i < maxTokens && wrapper->is_generating; ++i) {
            // Sample next token
            llama_token new_token_id = llama_sampler_sample(wrapper->sampler, wrapper->ctx, -1);
            
            if (llama_vocab_is_eog(vocab, new_token_id)) {
                break;
            }
            
            // Convert token to text
            char token_str[256];
            int n_chars = llama_token_to_piece(vocab, new_token_id, token_str, sizeof(token_str), 0, false);
            if (n_chars < 0) {
                LOGE("Failed to convert token to string");
                break;
            }
            token_str[n_chars] = '\0';
            
            response += token_str;
            
            // Send token to callback
            jstring jToken = env->NewStringUTF(token_str);
            env->CallVoidMethod(callback, onTokenMethod, jToken);
            env->DeleteLocalRef(jToken);
            
            // Decode the new token
            wrapper->tokens.push_back(new_token_id);
            struct llama_batch batch_single = llama_batch_get_one(&new_token_id, 1);
            if (llama_decode(wrapper->ctx, batch_single)) {
                LOGE("Failed to decode new token");
                break;
            }
            n_eval++;
        }
        
#else
        // Mock generation
        LOGI("Mock generation for prompt: %s", promptStr);
        std::string mockResponse = "Mock response from llama.cpp for: ";
        mockResponse += promptStr;
        
        // Split into tokens and simulate streaming
        std::vector<std::string> tokens;
        std::istringstream iss(mockResponse);
        std::string token;
        while (iss >> token) {
            tokens.push_back(token + " ");
        }
        
        for (const auto& tokenStr : tokens) {
            if (!wrapper->is_generating) break;
            
            response += tokenStr;
            jstring jToken = env->NewStringUTF(tokenStr.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jToken);
            env->DeleteLocalRef(jToken);
            
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
#endif
        
        env->CallVoidMethod(callback, onCompleteMethod);
        
    } catch (const std::exception& e) {
        LOGE("Exception during generation: %s", e.what());
        jstring errorStr = env->NewStringUTF(e.what());
        env->CallVoidMethod(callback, onErrorMethod, errorStr);
        env->DeleteLocalRef(errorStr);
        response = "Error: " + std::string(e.what());
    }
    
    wrapper->is_generating = false;
    env->ReleaseStringUTFChars(prompt, promptStr);
    return env->NewStringUTF(response.c_str());
}

// Performance metrics
JNIEXPORT jlong JNICALL
Java_com_daasuu_llmsample_data_llm_llamacpp_LlamaCppJNI_getMemoryUsageNative(
        JNIEnv *env,
        jobject /* this */,
        jlong modelPtr) {
    
    if (modelPtr == 0) return 0;
    
    auto wrapper = reinterpret_cast<llama_context_wrapper*>(modelPtr);
    
#ifdef LLAMA_CPP_AVAILABLE
    if (wrapper->ctx) {
        // Get actual memory usage from llama.cpp
        return llama_state_get_size(wrapper->ctx);
    }
#endif
    
    // Mock memory usage
    return 256 * 1024 * 1024; // 256MB
}

JNIEXPORT jfloat JNICALL
Java_com_daasuu_llmsample_data_llm_llamacpp_LlamaCppJNI_getModelSizeMBNative(
        JNIEnv *env,
        jobject /* this */,
        jlong modelPtr) {
    
    if (modelPtr == 0) return 0.0f;
    
    auto wrapper = reinterpret_cast<llama_context_wrapper*>(modelPtr);
    
#ifdef LLAMA_CPP_AVAILABLE
    if (wrapper->model) {
        // Get actual model size from llama.cpp
        return llama_model_size(wrapper->model) / (1024.0f * 1024.0f);
    }
#endif
    
    // Mock model size
    return 1024.0f; // 1GB
}

// Utility functions
JNIEXPORT jint JNICALL
Java_com_daasuu_llmsample_data_llm_llamacpp_LlamaCppJNI_getContextSize(
        JNIEnv *env,
        jobject /* this */,
        jlong modelPtr) {
    
    if (modelPtr == 0) return 0;
    
    auto wrapper = reinterpret_cast<llama_context_wrapper*>(modelPtr);
    return wrapper->n_ctx;
}

JNIEXPORT void JNICALL
Java_com_daasuu_llmsample_data_llm_llamacpp_LlamaCppJNI_stopGeneration(
        JNIEnv *env,
        jobject /* this */,
        jlong modelPtr) {
    
    if (modelPtr == 0) return;
    
    auto wrapper = reinterpret_cast<llama_context_wrapper*>(modelPtr);
    std::lock_guard<std::mutex> lock(wrapper->generation_mutex);
    wrapper->is_generating = false;
    
    LOGI("Generation stopped for model: %ld", modelPtr);
}

} // extern "C"