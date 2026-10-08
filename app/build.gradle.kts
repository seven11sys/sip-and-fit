plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.sev7n.drinkexercise"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sev7n.drinkexercise"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.6.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        getByName("debug") {
            System.getenv("SIPFIT_DEBUG_KEYSTORE")?.let { storeFile = file(it) }
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
