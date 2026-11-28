plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.friedbrain.clipvlip"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.friedbrain.clipvlip"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("VERSION_CODE").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("VERSION_NAME").orNull ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GITHUB_REPOSITORY", "\"fried-brain/ClipVlip\"")
        buildConfigField("String", "MOBILE_CONFIG_URL", "\"https://clipvlip.vercel.app/api/mobile/config\"")
    }
    signingConfigs {
        create("release") {
            val storePath = System.getenv("KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    testImplementation("junit:junit:4.13.2")
}
