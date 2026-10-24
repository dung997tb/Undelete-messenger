plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dung.rogueclient"
    compileSdk = 35

    signingConfigs {
        create("rogue") {
            storeFile = file("$rootDir/rogue-test.jks")
            storePassword = "roguepass"
            keyAlias = "rogue"
            keyPassword = "roguepass"
        }
    }

    defaultConfig {
        applicationId = "com.dung.rogueclient"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("rogue") }
        release { signingConfig = signingConfigs.getByName("rogue") }
    }

    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":contract"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
