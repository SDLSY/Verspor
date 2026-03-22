plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val debugApiBaseUrl =
    (project.findProperty("DEBUG_API_BASE_URL") as String?) ?: "https://cloud.changgengring.cyou/"
val releaseApiBaseUrl =
    (project.findProperty("RELEASE_API_BASE_URL") as String?) ?: "https://cloud.changgengring.cyou/"

android {
    namespace = "com.example.newstart.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
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
    implementation("androidx.room:room-runtime:2.8.0")
    implementation("androidx.room:room-ktx:2.8.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation(project(":core-model"))
    implementation(project(":core-common"))
    implementation(project(":core-db"))
    implementation(project(":core-ble"))
    implementation(project(":core-network"))
    implementation(project(":core-ml"))
    testImplementation("junit:junit:4.13.2")
}
