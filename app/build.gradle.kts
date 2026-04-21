plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.butlermanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.butlermanager"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.navigation.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Room for local storage
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ML Kit for QR code scanning
    implementation(libs.mlkit.barcode.scanning)

    // Gson for JSON parsing
    implementation(libs.gson)

    // Espressif Provisioning
    implementation(libs.esp.idf.provisioning)
    implementation(libs.eventbus)

    // MQTT
    implementation(libs.paho.mqtt)
    implementation(libs.paho.android)
    implementation(libs.localbroadcastmanager)
}

configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "androidx.camera") {
                useVersion(libs.versions.camera.get())
            }
        }
    }
}
