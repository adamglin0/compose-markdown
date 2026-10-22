plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The Android app of the sample: AGP 9 keeps applications out of Kotlin Multiplatform modules, so the
// shared UI lives in :sample-chat and this module only hosts it in an activity.
android {
    namespace = "com.adamglin.compose.markdown.sample.chat.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.adamglin.compose.markdown.sample.chat"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":sample-chat"))
    implementation(libs.androidx.activity.compose)
}
