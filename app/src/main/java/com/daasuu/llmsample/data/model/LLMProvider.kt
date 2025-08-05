package com.daasuu.llmsample.data.model

enum class LLMProvider(val displayName: String) {
    GEMINI_NANO("Gemini Nano (ML Kit)"),
    LLAMA_CPP("Llama.cpp"),
    LITE_RT("LiteRT (TensorFlowLite)")
}