plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "com.onvexa.recorder.core.audio"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    api(project(":core:database"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
}
