plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.daasuu.llmsample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daasuu.llmsample"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
    testOptions {
        managedDevices {
            allDevices {
                create("pixel7Api34", com.android.build.api.dsl.ManagedVirtualDevice::class) {
                    device = "Pixel 7"
                    apiLevel = 34
                    systemImageSource = "google"
                    // optional:
                    // abi = "arm64-v8a"
                }
            }
        }
    }
}

dependencies {

    implementation(files("libs/llama-release.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt for DI
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.foundation.layout)
    ksp(libs.hilt.compiler)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Material Icons Extended
    implementation(libs.androidx.material.icons.extended)

    // HTTP Client for model downloads
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // DataStore (Preferences)
    implementation(libs.androidx.datastore.preferences)

    // MediaPipe Tasks Inference API
    implementation(libs.tasks.genai)

    // Google AI Edge SDK for Gemini Nano experimental access
    implementation(libs.aicore)

    // Timber for logging
    implementation(libs.timber)


    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine) // Flow testing
    testImplementation(libs.androidx.core.testing) // LiveData testing
    testImplementation(libs.kotlin.test) // Kotlin test assertions

    // Android Instrumentation Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debug tools
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}