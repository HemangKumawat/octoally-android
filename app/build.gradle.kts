plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.octoally.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.octoally.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 40
        versionName = "2.0.0-rc21"
    }

    buildFeatures {
        compose = true
    }

    kotlin {
        jvmToolchain(17)
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:projects"))
    implementation(project(":feature:session"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:git"))
    implementation(project(":feature:files"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    kapt(libs.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)
}
