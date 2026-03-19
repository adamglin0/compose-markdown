plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
}

allprojects {
    group = "dev.markstream"
    version = "0.1.0-SNAPSHOT"
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
