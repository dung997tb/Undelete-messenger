import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Ecosystem signing: ONE keystore signs Core and every satellite app.
// Passwords come from env vars, falling back to the pass file on this build machine.
val passFile = File(System.getProperty("user.home"), "keystores/notify-ecosystem.pass")
val ecosystemPass: String = System.getenv("ECOSYSTEM_STORE_PASS")
    ?: if (passFile.exists()) passFile.readText().trim() else ""

android {
    namespace = "com.dung.notifycore"
    compileSdk = 35

    signingConfigs {
        create("ecosystem") {
            storeFile = File(System.getProperty("user.home"), "keystores/notify-ecosystem.jks")
            storePassword = ecosystemPass
            keyAlias = "notify-ecosystem"
            keyPassword = ecosystemPass
        }
    }

    defaultConfig {
        applicationId = "com.dung.notifycore"
        minSdk = 29
        targetSdk = 35
        versionCode = 15
        versionName = "1.3.6"
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("ecosystem") }
        release {
            signingConfig = signingConfigs.getByName("ecosystem")
            isMinifyEnabled = false
        }
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
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite:2.4.0")
}
