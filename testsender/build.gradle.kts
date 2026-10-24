import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val passFile = File(System.getProperty("user.home"), "keystores/notify-ecosystem.pass")
val ecosystemPass: String = System.getenv("ECOSYSTEM_STORE_PASS")
    ?: if (passFile.exists()) passFile.readText().trim() else ""

android {
    namespace = "com.dung.testsender"
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
        applicationId = "com.dung.testsender"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("ecosystem") }
        release { signingConfig = signingConfigs.getByName("ecosystem") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
