import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val backendProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun backendValue(name: String): String =
    (backendProperties.getProperty(name) ?: System.getenv(name) ?: "")
        .replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.onvexa.recorder.app"
    compileSdk = 35

    defaultConfig {
        // Debe ser idéntico en :wear: la Wearable Data Layer solo enlaza apps con el mismo
        // applicationId y la misma firma.
        applicationId = "com.onvexa.recorder"
        minSdk = 29 // Android 10
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${backendValue("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${backendValue("SUPABASE_PUBLISHABLE_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false // se activa antes de distribuir
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
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:database"))
    implementation(project(":core:model"))
    implementation(project(":core:audio"))
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
