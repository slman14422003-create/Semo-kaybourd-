plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.semo.keyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.semo.keyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = 49
        versionName = "3.15.1"
        ndk { debugSymbolLevel = "NONE" }
    }
    val semoKeystore = file("semo-release.p12")
    if (semoKeystore.exists()) {
        signingConfigs {
            create("semo") {
                storeFile = semoKeystore
                storePassword = "semokeyboard"
                keyAlias = "semo"
                keyPassword = "semokeyboard"
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

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions"
        )
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

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
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
}
