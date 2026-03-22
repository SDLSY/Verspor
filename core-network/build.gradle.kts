plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

import java.io.FileInputStream
import java.util.Properties

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

if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.example.newstart.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
        xfyunKeys.forEach { key ->
            buildConfigField("String", key, buildConfigString(stringProp(key, xfyunDefaults[key] ?: "")))
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }

        release {
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
        }
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(project(":core-model"))
    testImplementation("junit:junit:4.13.2")
}
