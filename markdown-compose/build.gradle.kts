import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    androidTarget()
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) {
        browser()
    }
    wasmJs {
        browser()
    }
    jvmToolchain(17)

    sourceSets {
        val iosMain by creating {
            dependsOn(commonMain.get())
        }
        val webMain by creating {
            dependsOn(commonMain.get())
        }
        val highlightedMain by creating {
            dependsOn(commonMain.get())

            dependencies {
                implementation(libs.highlights)
            }
        }
        val jvmMain by getting {
            dependsOn(highlightedMain)
        }
        val iosX64Main by getting {
            dependsOn(highlightedMain)
            dependsOn(iosMain)
        }
        val iosArm64Main by getting {
            dependsOn(highlightedMain)
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(highlightedMain)
            dependsOn(iosMain)
        }
        val jsMain by getting {
            dependsOn(highlightedMain)
            dependsOn(webMain)
        }
        val wasmJsMain by getting {
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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "dev.markstream.compose"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
