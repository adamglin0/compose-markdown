@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

kotlin {
    explicitApi()
    abiValidation()

    // The parser has no Java 21 dependencies; keep it consumable from Java 17.
    jvmToolchain(17)

    android {
        namespace = "com.adamglin.compose.markdown.core"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    jvm()

    iosArm64()
    iosSimulatorArm64()

    js {
        // Browser tests run in Karma; karma.config.d/mocha-timeout.js raises mocha's per-test timeout.
        browser()
    }

    wasmJs {
        // Browser tests run in Karma; karma.config.d/mocha-timeout.js raises mocha's per-test timeout.
        browser()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
