plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.crickscorer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crickscorer.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "2.0"
    }
}


kotlinOptions {
    jvmTarget = "17"
}
