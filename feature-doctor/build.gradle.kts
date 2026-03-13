plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.newstart.feature.doctor"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation(project(":core-model"))
    implementation(project(":core-common"))
    implementation(project(":core-data"))
    implementation(project(":core-db"))
    implementation(project(":core-network"))
    implementation(project(":core-ml"))
    implementation(fileTree(mapOf("dir" to "../Android_aiui_soft_6.7.0001.0007/libs", "include" to listOf("*.jar"))))
    compileOnly(files("libs/xrtcsdk-compile-only.jar"))
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(libs.junit)
}
