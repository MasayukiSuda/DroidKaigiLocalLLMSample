#!/bin/bash

# Setup script for llama.cpp integration
# This script downloads and sets up llama.cpp for Android JNI compilation

echo "Setting up llama.cpp for Android..."

# Navigate to the cpp directory
cd app/src/main/cpp

# Check if llama.cpp directory already exists
if [ -d "llama.cpp" ]; then
    echo "llama.cpp directory already exists. Updating..."
    cd llama.cpp
    git pull
    cd ..
else
    echo "Cloning llama.cpp repository..."
    git clone --depth 1 --branch master https://github.com/ggerganov/llama.cpp.git
fi

echo "llama.cpp setup completed!"
echo ""
echo "Note: You may need to modify CMakeLists.txt and JNI code based on the"
echo "current llama.cpp API. The provided JNI wrapper is a basic template."
echo ""
echo "To build with actual llama.cpp integration:"
echo "1. Ensure Android NDK is installed"
echo "2. Update JNI wrapper code to match llama.cpp API"
echo "3. Build the app with: './gradlew assembleDebug'"