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
        versionCode = 14
        versionName = "2.5"
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

    lint {
        // "ForegroundServicePermission" complains "Android 14+ requires Foreground Service
        // types declaration and justification." The type IS declared
        // (foregroundServiceType="dataSync" on UploadService); the "justification" part is
        // a Play Console policy answer the lint can't see, so it always warns. The use
        // case is documented in the AndroidManifest.xml comment block above the
        // foreground-service permissions — that's the text we'll paste into Play Console.
        disable += "ForegroundServicePermission"

        // "OldTargetApi" treats every installed SDK as "the latest", including previews.
        // We intentionally stay on the latest *stable* targetSdk (36 / Android 16) and
        // will bump it deliberately when a new stable ships and we've smoke-tested it —
        // not because Android Studio's SDK manager downloaded a beta.
        disable += "OldTargetApi"
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
