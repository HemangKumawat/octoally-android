plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.octoally.feature.session"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    buildFeatures {
        compose = true
    }

    kotlin {
        jvmToolchain(17)
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    kapt(libs.hilt.compiler)

    // rc10 dual-WS: xterm.js WebView render path needs its own OkHttp socket.
    // rc18: folded into the version catalog (versions byte-identical, hygiene only).
    implementation(libs.okhttp)
    implementation(libs.androidx.webkit)
    implementation(libs.appcompat)
}
