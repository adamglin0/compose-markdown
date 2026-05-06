import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

data class PublishedModule(
    val artifactId: String,
    val pomName: String,
    val pomDescription: String,
)

val publishedModules = mapOf(
    ":markdown-core" to PublishedModule(
        artifactId = "markdown-core",
        pomName = "Compose Markdown Core",
        pomDescription = "Kotlin Multiplatform Markdown parser with streaming-friendly incremental snapshots.",
    ),
    ":markdown-compose" to PublishedModule(
        artifactId = "markdown-compose",
        pomName = "Compose Markdown",
        pomDescription = "Compose Multiplatform Markdown renderer built on top of streaming-friendly incremental snapshots.",
    ),
)

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = rootProject.providers.gradleProperty("releaseVersion")
        .orElse(rootProject.providers.gradleProperty("VERSION_NAME"))
        .get()
}

subprojects {
    val publishedModule = publishedModules[path] ?: return@subprojects
    val isMavenLocalPublish = gradle.startParameter.taskNames.any { taskName ->
        "MavenLocal" in taskName
    }

    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure(MavenPublishBaseExtension::class.java) {
            configure(
                KotlinMultiplatform(
                    javadocJar = JavadocJar.Empty(),
                ),
            )
            publishToMavenCentral(automaticRelease = !version.toString().endsWith("-SNAPSHOT"))
            if (!isMavenLocalPublish) {
                signAllPublications()
            }
            pom {
                name.set(publishedModule.pomName)
                description.set(publishedModule.pomDescription)
                inceptionYear.set(rootProject.providers.gradleProperty("POM_INCEPTION_YEAR"))
                url.set(rootProject.providers.gradleProperty("POM_URL"))
                licenses {
                    license {
                        name.set(rootProject.providers.gradleProperty("POM_LICENSE_NAME"))
                        url.set(rootProject.providers.gradleProperty("POM_LICENSE_URL"))
                        distribution.set(rootProject.providers.gradleProperty("POM_LICENSE_DIST"))
                    }
                }
                developers {
                    developer {
                        id.set(rootProject.providers.gradleProperty("POM_DEVELOPER_ID"))
                        name.set(rootProject.providers.gradleProperty("POM_DEVELOPER_NAME"))
                        url.set(rootProject.providers.gradleProperty("POM_DEVELOPER_URL"))
                    }
                }
                scm {
                    url.set(rootProject.providers.gradleProperty("POM_SCM_URL"))
                    connection.set(rootProject.providers.gradleProperty("POM_SCM_CONNECTION"))
                    developerConnection.set(rootProject.providers.gradleProperty("POM_SCM_DEV_CONNECTION"))
                }
            }
        }

        extensions.configure(PublishingExtension::class.java) {
            publications.withType(MavenPublication::class.java).configureEach {
                version = project.version.toString()
            }
        }
    }
}

tasks.register("benchmarkSmoke") {
    group = "verification"
    description = "Runs the lightweight JVM benchmark smoke suite."
    dependsOn(":benchmarks:benchmarkSmoke")
}

tasks.register("test") {
    group = "verification"
    description = "Runs all configured multiplatform test tasks."
    dependsOn(
        ":markdown-core:allTests",
        ":markdown-compose:allTests",
        ":sample-chat:allTests",
    )
}

tasks.register("ciCheck") {
    group = "verification"
    description = "Runs the checks required by GitHub CI."
    dependsOn(
        ":markdown-core:build",
        ":markdown-compose:build",
        ":sample-chat:build",
        ":benchmarks:build",
        "test",
        "benchmarkSmoke",
    )
}
