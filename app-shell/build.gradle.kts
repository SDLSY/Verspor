plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

import java.io.FileInputStream
import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
val debugApiBaseUrl =
    (project.findProperty("DEBUG_API_BASE_URL") as String?) ?: "https://cloud.changgengring.cyou/"
val releaseApiBaseUrl =
    (project.findProperty("RELEASE_API_BASE_URL") as String?) ?: "https://cloud.changgengring.cyou/"

fun stringProp(key: String, default: String = ""): String {
    return (project.findProperty(key) as String?)
        ?: localProperties.getProperty(key)
        ?: default
}

fun buildConfigString(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "")
        .replace("\n", "\\n")
    return "\"$escaped\""
}

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val debugOpenRouterApiKey =
    stringProp("DEBUG_OPENROUTER_API_KEY")
val releaseOpenRouterApiKey =
    stringProp("RELEASE_OPENROUTER_API_KEY")
val debugOpenRouterModel =
    stringProp("DEBUG_OPENROUTER_MODEL", "google/gemini-2.5-flash")
val releaseOpenRouterModel =
    stringProp("RELEASE_OPENROUTER_MODEL", "google/gemini-2.5-flash")

val xfyunKeys = listOf(
    "XFYUN_APP_ID",
    "XFYUN_API_KEY",
    "XFYUN_API_SECRET",
    "XFYUN_IAT_APP_ID",
    "XFYUN_IAT_API_KEY",
    "XFYUN_IAT_API_SECRET",
    "XFYUN_RTASR_APP_ID",
    "XFYUN_RTASR_API_KEY",
    "XFYUN_RAASR_APP_ID",
    "XFYUN_RAASR_API_KEY",
    "XFYUN_TTS_APP_ID",
    "XFYUN_TTS_API_KEY",
    "XFYUN_TTS_API_SECRET",
    "XFYUN_TTS_VOICE_NAME",
    "XFYUN_OCR_APP_ID",
    "XFYUN_OCR_API_KEY",
    "XFYUN_OCR_API_SECRET",
    "XFYUN_SPARK_LITE_APP_ID",
    "XFYUN_SPARK_LITE_API_KEY",
    "XFYUN_SPARK_LITE_API_SECRET",
    "XFYUN_SPARK_LITE_DOMAIN",
    "XFYUN_SPARK_X_APP_ID",
    "XFYUN_SPARK_X_API_KEY",
    "XFYUN_SPARK_X_API_SECRET",
    "XFYUN_SPARK_X_DOMAIN",
    "XFYUN_AIUI_APP_ID",
    "XFYUN_AIUI_API_KEY",
    "XFYUN_AIUI_API_SECRET",
    "XFYUN_AIUI_SCENE",
    "XFYUN_AIUI_DOCTOR_PROMPT",
    "XFYUN_VH_AVATAR_ID",
    "XFYUN_VH_VOICE_NAME"
)
val xfyunDefaults = mapOf(
    "XFYUN_TTS_VOICE_NAME" to "x4_yezi",
    "XFYUN_SPARK_LITE_DOMAIN" to "lite",
    "XFYUN_SPARK_X_DOMAIN" to "x1",
    "XFYUN_AIUI_SCENE" to "sos_app",
    "XFYUN_VH_VOICE_NAME" to "x4_yezi"
)

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
        buildConfigField("String", "OPENROUTER_API_KEY", "\"\"")
        buildConfigField("String", "OPENROUTER_MODEL", "\"google/gemini-2.5-flash\"")
        xfyunKeys.forEach { key ->
            buildConfigField("String", key, buildConfigString(stringProp(key, xfyunDefaults[key] ?: "")))
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

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
            val debugQwenModelUrl = (project.findProperty("DEBUG_QWEN_MODEL_URL") as String?) ?: ""
            buildConfigField("String", "QWEN_MODEL_URL", "\"$debugQwenModelUrl\"")
            buildConfigField("String", "OPENROUTER_API_KEY", "\"$debugOpenRouterApiKey\"")
            buildConfigField("String", "OPENROUTER_MODEL", "\"$debugOpenRouterModel\"")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            val releaseQwenModelUrl = (project.findProperty("RELEASE_QWEN_MODEL_URL") as String?) ?: ""
            buildConfigField("String", "QWEN_MODEL_URL", "\"$releaseQwenModelUrl\"")
            buildConfigField("String", "OPENROUTER_API_KEY", "\"$releaseOpenRouterApiKey\"")
            buildConfigField("String", "OPENROUTER_MODEL", "\"$releaseOpenRouterModel\"")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        disable += "NullSafeMutableLiveData"
        disable += "FrequentlyChangingValue"
        disable += "RememberInComposition"
        abortOnError = false
        checkDependencies = false
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

    sourceSets {
        getByName("main") {
            assets.setSrcDirs(
                listOf(
                    "src/main/assets",
                    "../Android_aiui_soft_6.7.0001.0007/assets"
                )
            )
            jniLibs.setSrcDirs(
                listOf(
                    "../Android_aiui_soft_6.7.0001.0007/libs"
                )
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt"
            )
        }
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

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation(libs.androidx.fragment.ktx)

    implementation(project(":core-model"))
    implementation(project(":core-common"))
    implementation(project(":core-data"))
    implementation(project(":core-db"))
    implementation(project(":core-ble"))
    implementation(project(":core-network"))
    implementation(project(":core-ml"))
    implementation(project(":feature-home"))
    implementation(project(":feature-device"))
    implementation(project(":feature-doctor"))
    implementation(project(":feature-relax"))
    implementation(project(":feature-trend"))
    implementation(project(":feature-profile"))

    implementation("no.nordicsemi.android:ble:2.7.1")
    implementation("no.nordicsemi.android:ble-ktx:2.7.1")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.room:room-runtime:2.8.0")
    implementation("androidx.room:room-ktx:2.8.0")
    kapt("androidx.room:room-compiler:2.8.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("com.airbnb.android:lottie:6.1.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    debugRuntimeOnly("com.llamatik:library:0.12.0")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("com.google.android.filament:filament-android:$filamentVersion")
    implementation("com.google.android.filament:gltfio-android:$filamentVersion")
    implementation("com.google.android.filament:filament-utils-android:$filamentVersion")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    implementation(fileTree(mapOf("dir" to "../Android_aiui_soft_6.7.0001.0007/libs", "include" to listOf("*.jar", "*.aar"))))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
