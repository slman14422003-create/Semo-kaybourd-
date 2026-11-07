import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.semo.keyboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.semo.keyboard"
        minSdk = 26
        targetSdk = 37
        versionCode = 76
        versionName = "3.30.2"
        ndk {
            debugSymbolLevel = "NONE"
            if (!providers.gradleProperty("includeX86").isPresent) abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    val semoKeystore = file("semo-release.p12")
    if (semoKeystore.exists()) {
        signingConfigs {
            create("semo") {
                storeFile = semoKeystore
                storePassword = System.getenv("SEMO_KS_PASS") ?: "semokeyboard"
                keyAlias = System.getenv("SEMO_KS_ALIAS") ?: "semo"
                keyPassword = System.getenv("SEMO_KS_KEY_PASS") ?: System.getenv("SEMO_KS_PASS") ?: "semokeyboard"
                storeType = "pkcs12"
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/versions/**",
                "DebugProbesKt.bin"
            )
        }
    }

    androidResources {
        localeFilters += setOf("en", "ar")
        noCompress += "onnx"
    }

    buildTypes {
        debug {
            if (semoKeystore.exists()) signingConfig = signingConfigs.getByName("semo")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (semoKeystore.exists()) signingConfigs.getByName("semo") else signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions"
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")

    testImplementation("junit:junit:4.13.2")
}
