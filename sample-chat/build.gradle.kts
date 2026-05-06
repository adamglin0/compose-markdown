import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

fun kotlinStringLiteral(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '$' -> append("\\$")
            else -> append(character)
        }
    }
    append('"')
}

val sampleMarkdownExamplesDir = layout.projectDirectory.dir("markdown-examples")
val generatedSampleExamplesDir = layout.buildDirectory.dir("generated/sample-chat/examples/kotlin")

val generateBundledSampleScripts by tasks.registering {
    inputs.dir(sampleMarkdownExamplesDir)
    outputs.dir(generatedSampleExamplesDir)

    doLast {
        val outputDir = generatedSampleExamplesDir.get().asFile
        val outputFile = outputDir.resolve(
            "com/adamglin/compose/markdown/sample/chat/SampleChatBundledExamples.kt",
        )
        val exampleFiles = sampleMarkdownExamplesDir.asFile
            .listFiles { file -> file.isFile && file.extension == "md" }
            ?.sortedBy { file -> file.name }
            ?: emptyList()

        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            buildString {
                appendLine("package com.adamglin.compose.markdown.sample.chat")
                appendLine()
                appendLine("internal object SampleChatBundledExamples {")
                appendLine("    private val messagesByPath: Map<String, String> = mapOf(")
                exampleFiles.forEachIndexed { index, file ->
                    append("        ")
                    append(kotlinStringLiteral("markdown-examples/${file.name}"))
                    append(" to ")
                    append(kotlinStringLiteral(file.readText()))
                    if (index != exampleFiles.lastIndex) {
                        append(',')
                    }
                    appendLine()
                }
                appendLine("    )")
                appendLine()
                appendLine("    fun readExample(path: String): String = messagesByPath[path]")
                appendLine("        ?: error(\"unexpected path: ${'$'}path\")")
                appendLine("}")
            },
        )
    }
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    androidTarget()
    jvm()
    val iosX64 = iosX64()
    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()
    js(IR) {
        browser()
        binaries.executable()
    }
    wasmJs {
        browser()
        binaries.executable()
    }
    jvmToolchain(17)

    listOf(iosX64, iosArm64, iosSimulatorArm64).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SampleChat"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generatedSampleExamplesDir)

            dependencies {
                implementation(project(":markdown-compose"))
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

tasks.configureEach {
    if (name.startsWith("compile") && name.contains("Kotlin")) {
        dependsOn(generateBundledSampleScripts)
    }
}

android {
    namespace = "com.adamglin.compose.markdown.sample.chat"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.adamglin.compose.markdown.sample.chat.MainKt"
    }
}
