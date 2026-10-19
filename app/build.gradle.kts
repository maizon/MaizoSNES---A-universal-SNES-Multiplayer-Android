plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.maizon.maizosnes"
    compileSdk = 35
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.maizon.maizosnes"
        minSdk = 23
        targetSdk = 35
        versionCode = 10
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-O2")
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        // Stable signing key comes only from environment variables or CI Secrets.
        fun secret(primary: String, legacy: String): String? =
            System.getenv(primary)?.takeIf(String::isNotBlank)
                ?: System.getenv(legacy)?.takeIf(String::isNotBlank)
        val ksPath = secret("KEYSTORE_PATH", "MAIZO_KEYSTORE_PATH")
        val storePass = secret("KEYSTORE_PASSWORD", "MAIZO_STORE_PASSWORD")
        val keyAliasEnv = secret("KEY_ALIAS", "MAIZO_KEY_ALIAS")
        val keyPass = secret("KEY_PASSWORD", "MAIZO_KEY_PASSWORD")
        if (!ksPath.isNullOrBlank() && !storePass.isNullOrBlank() &&
            !keyAliasEnv.isNullOrBlank() && !keyPass.isNullOrBlank() &&
            file(ksPath).exists()) {
            create("publish") {
                storeFile = file(ksPath)
                storePassword = storePass
                keyAlias = keyAliasEnv
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            signingConfigs.findByName("publish")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // BSD-3-Clause WebRTC SDK, native Android ARMv7+ARM64 binary.
    // Packages relocated to livekit.org.webrtc to avoid symbol collisions.
    implementation("io.github.webrtc-sdk:android-prefixed-stripped:150.7871.01")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
