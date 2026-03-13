plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

import java.util.Properties
import java.io.FileInputStream

// ========== Read keystore config ==========
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
val debugApiBaseUrl = (project.findProperty("DEBUG_API_BASE_URL") as String?) ?: "https://cloud.changgengring.cyou/"
val releaseApiBaseUrl =
    (project.findProperty("RELEASE_API_BASE_URL") as String?) ?: "https://cloud.changgengring.cyou/"

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val debugGeminiApiKey =
    (project.findProperty("DEBUG_GEMINI_API_KEY") as String?)
        ?: localProperties.getProperty("DEBUG_GEMINI_API_KEY")
        ?: ""
val releaseGeminiApiKey =
    (project.findProperty("RELEASE_GEMINI_API_KEY") as String?)
        ?: localProperties.getProperty("RELEASE_GEMINI_API_KEY")
        ?: ""
val debugGeminiModel =
    (project.findProperty("DEBUG_GEMINI_MODEL") as String?)
        ?: localProperties.getProperty("DEBUG_GEMINI_MODEL")
        ?: "gemini-2.5-flash"
val releaseGeminiModel =
    (project.findProperty("RELEASE_GEMINI_MODEL") as String?)
        ?: localProperties.getProperty("RELEASE_GEMINI_MODEL")
        ?: "gemini-2.5-flash"
val debugOpenRouterApiKey =
    (project.findProperty("DEBUG_OPENROUTER_API_KEY") as String?)
        ?: localProperties.getProperty("DEBUG_OPENROUTER_API_KEY")
        ?: ""
val releaseOpenRouterApiKey =
    (project.findProperty("RELEASE_OPENROUTER_API_KEY") as String?)
        ?: localProperties.getProperty("RELEASE_OPENROUTER_API_KEY")
        ?: ""
val debugOpenRouterModel =
    (project.findProperty("DEBUG_OPENROUTER_MODEL") as String?)
        ?: localProperties.getProperty("DEBUG_OPENROUTER_MODEL")
        ?: "google/gemini-2.5-flash"
val releaseOpenRouterModel =
    (project.findProperty("RELEASE_OPENROUTER_MODEL") as String?)
        ?: localProperties.getProperty("RELEASE_OPENROUTER_MODEL")
        ?: "google/gemini-2.5-flash"

android {
    namespace = "com.example.newstart"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.newstart"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
        buildConfigField("String", "QWEN_MODEL_URL", "\"\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"\"")
        buildConfigField("String", "GEMINI_MODEL", "\"gemini-2.5-flash\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"\"")
        buildConfigField("String", "OPENROUTER_MODEL", "\"google/gemini-2.5-flash\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ========== Signing config ==========
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file("../${keystoreProperties["storeFile"]}")
                storePassword = keystoreProperties["storePassword"].toString()
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
            val debugQwenModelUrl =
                (project.findProperty("DEBUG_QWEN_MODEL_URL") as String?) ?: ""
            buildConfigField("String", "QWEN_MODEL_URL", "\"$debugQwenModelUrl\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"$debugGeminiApiKey\"")
            buildConfigField("String", "GEMINI_MODEL", "\"$debugGeminiModel\"")
            buildConfigField("String", "OPENROUTER_API_KEY", "\"$debugOpenRouterApiKey\"")
            buildConfigField("String", "OPENROUTER_MODEL", "\"$debugOpenRouterModel\"")
        }

        release {
            isMinifyEnabled = true
            // Enable code shrinking and resource shrinking for release.
            isShrinkResources = true
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            val releaseQwenModelUrl =
                (project.findProperty("RELEASE_QWEN_MODEL_URL") as String?) ?: ""
            buildConfigField("String", "QWEN_MODEL_URL", "\"$releaseQwenModelUrl\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"$releaseGeminiApiKey\"")
            buildConfigField("String", "GEMINI_MODEL", "\"$releaseGeminiModel\"")
            buildConfigField("String", "OPENROUTER_API_KEY", "\"$releaseOpenRouterApiKey\"")
            buildConfigField("String", "OPENROUTER_MODEL", "\"$releaseOpenRouterModel\"")
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Apply signing config when available
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    
    // ========== Lint config ==========
    lint {
        // Disable selected checks to avoid blocking release builds
        disable += "NullSafeMutableLiveData"
        disable += "FrequentlyChangingValue"
        disable += "RememberInComposition"
        
        // Keep lint non-blocking for now
        abortOnError = false
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    val filamentVersion = "1.54.5"

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    
    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // 蓝牙通信
    implementation("no.nordicsemi.android:ble:2.7.1")
    implementation("no.nordicsemi.android:ble-ktx:2.7.1")
    
    // Chart library - MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Networking - Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Database - Room
    implementation("androidx.room:room-runtime:2.8.0")
    implementation("androidx.room:room-ktx:2.8.0")
    kapt("androidx.room:room-compiler:2.8.0")
    
    // CardView & RecyclerView
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    
    // Lottie animation
    implementation("com.airbnb.android:lottie:6.1.0")

    // Shimmer 骨架屏
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    
    // DataStore (replacing SharedPreferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("com.llamatik:library:0.12.0")

    // On-device AI runtime (P1)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")

    // 3D rendering (Filament)
    implementation("com.google.android.filament:filament-android:$filamentVersion")
    implementation("com.google.android.filament:gltfio-android:$filamentVersion")
    implementation("com.google.android.filament:filament-utils-android:$filamentVersion")

    // OCR (medical report V1)
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
