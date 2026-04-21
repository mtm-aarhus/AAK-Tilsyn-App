plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // Requires app/google-services.json — download from Firebase Console.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.aak.tilsynsapp"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.aak.tilsynsapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "2.2"
        buildConfigField("String", "API_URL", "\"https://pyorchestrator.aarhuskommune.dk/api/\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true      // Dette aktiverer R8
            isShrinkResources = true    // Fjerner ubrugte billeder/ressourcer
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // Core & Kotlin
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui)
    implementation(libs.osmdroid)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.text)
    implementation(libs.androidx.ui.tooling.preview)

    // Networking & JSON
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Room (Database)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Material Design
    implementation(libs.material)

    // Google Play In-App Updates
    implementation(libs.app.update)

    // Firebase Cloud Messaging (push notifications)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
}
