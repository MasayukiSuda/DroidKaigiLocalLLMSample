cmake_minimum_required(VERSION 3.18.1)
project("llmsample")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find required libraries
find_library(log-lib log)
find_library(android-lib android)

# Add llama.cpp as a subdirectory (we'll download it later)
set(LLAMA_BUILD_SHARED OFF)
set(LLAMA_BUILD_STATIC ON)
set(BUILD_SHARED_LIBS OFF)

# Check if llama.cpp exists and configure accordingly
set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)
set(LLAMA_CPP_AVAILABLE OFF)

if(EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    set(LLAMA_CPP_AVAILABLE ON)
    message(STATUS "llama.cpp found, building with real implementation")
    
    # Configure llama.cpp build options for mobile
    set(LLAMA_BUILD_SHARED OFF CACHE BOOL "")
    set(LLAMA_BUILD_STATIC ON CACHE BOOL "")
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "")
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "")
    set(LLAMA_NATIVE OFF CACHE BOOL "")
    set(LLAMA_AVX OFF CACHE BOOL "")
    set(LLAMA_AVX2 OFF CACHE BOOL "")
    set(LLAMA_FMA OFF CACHE BOOL "")
    set(GGML_USE_LLAMAFILE OFF CACHE BOOL "")
    
    add_subdirectory(llama.cpp)
else()
    message(STATUS "llama.cpp not found, building with mock implementation")
endif()

# Create our JNI wrapper library
add_library(llamacpp-jni SHARED
    llama_jni.cpp
)

# Set include directories
target_include_directories(llamacpp-jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(LLAMA_CPP_AVAILABLE)
    target_include_directories(llamacpp-jni PRIVATE
        ${LLAMA_CPP_DIR}/include
        ${LLAMA_CPP_DIR}/ggml/include
    )
    target_compile_definitions(llamacpp-jni PRIVATE LLAMA_CPP_AVAILABLE)
endif()

# Link libraries
target_link_libraries(llamacpp-jni
    ${log-lib}
    ${android-lib}
)

if(LLAMA_CPP_AVAILABLE)
    target_link_libraries(llamacpp-jni
        llama
    )
endif()