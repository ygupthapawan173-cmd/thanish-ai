plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// API keys are entered on-device; never embed gateway, Gemini, or GitHub secrets in APKs.
val geminiApiKey = ""
val openRouterApiKey = ""

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.pandubabu.omega.v19ultra"
        minSdk = 24
        targetSdk = 36
        versionCode = 118
        versionName = "V110 ULTRA INTELLIGENCE MASTER"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterApiKey\"")
    }

    val ownerKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")?.trim().orEmpty()
    val ownerKeystorePass = System.getenv("RELEASE_KEYSTORE_PASSWORD").orEmpty()
    val ownerKeyAlias = System.getenv("RELEASE_KEY_ALIAS")?.trim().orEmpty()
    val ownerKeyPass = System.getenv("RELEASE_KEY_PASSWORD").orEmpty()
    val hasOwnerSigning = ownerKeystorePath.isNotEmpty() && file(ownerKeystorePath).isFile &&
        ownerKeystorePass.isNotEmpty() && ownerKeyAlias.isNotEmpty() && ownerKeyPass.isNotEmpty()

    signingConfigs {
        if (hasOwnerSigning) {
            create("ownerRelease") {
                storeFile = file(ownerKeystorePath)
                storePassword = ownerKeystorePass
                keyAlias = ownerKeyAlias
                keyPassword = ownerKeyPass
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasOwnerSigning) {
                signingConfig = signingConfigs.getByName("ownerRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
