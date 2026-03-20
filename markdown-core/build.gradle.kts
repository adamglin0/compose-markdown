@file:OptIn(ExperimentalWasmDsl::class)

import com.android.build.api.dsl.androidLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

fun requiredProperty(name: String): String =
    providers.gradleProperty(name).orNull ?: error("Missing Gradle property: $name")

val pomInceptionYear = requiredProperty("POM_INCEPTION_YEAR")
val pomUrl = requiredProperty("POM_URL")
val pomScmUrl = requiredProperty("POM_SCM_URL")
val pomScmConnection = requiredProperty("POM_SCM_CONNECTION")
val pomScmDevConnection = requiredProperty("POM_SCM_DEV_CONNECTION")
val pomLicenseName = requiredProperty("POM_LICENSE_NAME")
val pomLicenseUrl = requiredProperty("POM_LICENSE_URL")
val pomLicenseDist = requiredProperty("POM_LICENSE_DIST")
val pomDeveloperId = requiredProperty("POM_DEVELOPER_ID")
val pomDeveloperName = requiredProperty("POM_DEVELOPER_NAME")
val pomDeveloperUrl = requiredProperty("POM_DEVELOPER_URL")

val hasSigningCredentials =
    providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.secretKeyRingFile").isPresent

kotlin {
    jvmToolchain(17)

    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "com.adamglin.compose.markdown.core"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
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
        commonMain.dependencies {
            implementation(libs.compose.runtime)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Empty(),
        ),
    )
    publishToMavenCentral(automaticRelease = !version.toString().endsWith("-SNAPSHOT"))
    if (hasSigningCredentials) {
        signAllPublications()
    }
    coordinates(project.group.toString(), "markdown-core", project.version.toString())
    pom {
        name.set("Compose Markdown Core")
        description.set("Kotlin Multiplatform Markdown parser with streaming-friendly incremental snapshots.")
        inceptionYear.set(pomInceptionYear)
        url.set(pomUrl)
        licenses {
            license {
                name.set(pomLicenseName)
                url.set(pomLicenseUrl)
                distribution.set(pomLicenseDist)
            }
        }
        developers {
            developer {
                id.set(pomDeveloperId)
                name.set(pomDeveloperName)
                url.set(pomDeveloperUrl)
            }
        }
        scm {
            url.set(pomScmUrl)
            connection.set(pomScmConnection)
            developerConnection.set(pomScmDevConnection)
        }
    }
}
