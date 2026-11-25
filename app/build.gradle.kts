plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
id("com.google.gms.google-services")}

android {
    namespace = "com.crickscorer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crickscorer.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    // Firebase Bill of Materials
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))

    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth")

    // Cloud Firestore Database
    implementation("com.google.firebase:firebase-firestore")
}
