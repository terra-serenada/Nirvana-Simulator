plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.terrachemica.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.terrachemica.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
