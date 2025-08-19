package com.daasuu.llmsample.data.model

enum class LLMProvider(val displayName: String) {
    GEMINI_NANO("Gemini Nano (On-Device)"),
    LLAMA_CPP("Llama.cpp"),
    LITE_RT("Gemma3 (.task)")
}