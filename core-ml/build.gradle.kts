plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

fun stringProp(key: String, default: String = ""): String {
    return (project.findProperty(key) as String?) ?: default
}

android {
    namespace = "com.example.newstart.core.ml"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "QWEN_MODEL_URL", "\"\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"\"")
        buildConfigField("String", "OPENROUTER_MODEL", "\"google/gemini-2.5-flash\"")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    buildTypes {
        debug {
            val debugQwenModelUrl = (project.findProperty("DEBUG_QWEN_MODEL_URL") as String?) ?: ""
            val debugOpenRouterApiKey = stringProp("DEBUG_OPENROUTER_API_KEY")
            val debugOpenRouterModel = stringProp("DEBUG_OPENROUTER_MODEL", "google/gemini-2.5-flash")
            buildConfigField("String", "QWEN_MODEL_URL", "\"$debugQwenModelUrl\"")
            buildConfigField("String", "OPENROUTER_API_KEY", "\"$debugOpenRouterApiKey\"")
            buildConfigField("String", "OPENROUTER_MODEL", "\"$debugOpenRouterModel\"")
        }

        release {
            val releaseQwenModelUrl = (project.findProperty("RELEASE_QWEN_MODEL_URL") as String?) ?: ""
            val releaseOpenRouterApiKey = stringProp("RELEASE_OPENROUTER_API_KEY")
            val releaseOpenRouterModel = stringProp("RELEASE_OPENROUTER_MODEL", "google/gemini-2.5-flash")
            buildConfigField("String", "QWEN_MODEL_URL", "\"$releaseQwenModelUrl\"")
            buildConfigField("String", "OPENROUTER_API_KEY", "\"$releaseOpenRouterApiKey\"")
            buildConfigField("String", "OPENROUTER_MODEL", "\"$releaseOpenRouterModel\"")
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
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    implementation(project(":core-common"))
    implementation(project(":core-db"))
    implementation(project(":core-model"))
    testImplementation("junit:junit:4.13.2")
}
