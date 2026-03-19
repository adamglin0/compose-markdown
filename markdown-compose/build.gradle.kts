@file:OptIn(ExperimentalWasmDsl::class)

import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)

    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "dev.markstream.compose"
        compileSdk = 36
        minSdk = 23
    }

    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    js(compiler = IR) {
        browser()
    }

    wasmJs {
        browser()
    }

    sourceSets {
        val iosMain = create("iosMain")
        val webMain = create("webMain")
        val highlightedMain = create("highlightedMain")

        iosMain.dependsOn(commonMain.get())
        webMain.dependsOn(commonMain.get())
        highlightedMain.dependsOn(commonMain.get())

        jvmMain.configure {
            dependsOn(highlightedMain)
        }
        iosX64Main.configure {
            dependsOn(highlightedMain)
            dependsOn(iosMain)
        }
        iosArm64Main.configure {
            dependsOn(highlightedMain)
            dependsOn(iosMain)
        }
        iosSimulatorArm64Main.configure {
            dependsOn(highlightedMain)
            dependsOn(iosMain)
        }
        jsMain.configure {
            dependsOn(highlightedMain)
            dependsOn(webMain)
        }
        wasmJsMain.configure {
            dependsOn(highlightedMain)
            dependsOn(webMain)
        }

        commonMain.dependencies {
            api(project(":markdown-core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        highlightedMain.dependencies {
            implementation(libs.highlights)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
