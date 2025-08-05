# DroidKaigi Local LLM Sample - Project Overview

## Purpose
This is an Android application created for DroidKaigi conference that demonstrates and benchmarks three different on-device Large Language Model (LLM) implementations:

1. **ML Kit GenAI API** with Gemini Nano (Android 16+)
2. **llama.cpp** via JNI for quantized Llama 3 models
3. **LiteRT** (formerly TensorFlowLite) with NNAPI/GPU inference

The app showcases:
- Offline AI chat functionality
- Real-time text summarization
- Real-time text proofreading

## Benchmarking Criteria
The project compares these implementations across:
1. Implementation effort and build process
2. Model size and RAM usage
3. Inference latency
4. Battery consumption
5. Licensing and operational considerations

## Target Platform
- Android SDK 36 (compile/target)
- Minimum SDK 24
- Optimized for Android 16+ to utilize AICore features