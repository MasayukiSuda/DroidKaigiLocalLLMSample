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

# Include llama.cpp
if(EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)
    add_subdirectory(llama.cpp)
endif()

# Create our JNI wrapper library
add_library(llamacpp-jni SHARED
    llama_jni.cpp
)

# Set include directories
target_include_directories(llamacpp-jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp
)

# Link libraries
target_link_libraries(llamacpp-jni
    ${log-lib}
    ${android-lib}
    llama
)