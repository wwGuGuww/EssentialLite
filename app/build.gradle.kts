plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.uygar.essentiallite"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.uygar.essentiallite"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.core:core:1.13.1")
    // API anahtarını şifreli saklamak için.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Ekran görüntüsündeki metni cihazda okumak için (model uygulamanın içinde, ağ gerekmez).
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
