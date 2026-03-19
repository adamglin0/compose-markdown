plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":markdown-core"))
}

application {
    mainClass = "com.adamglin.compose.markdown.benchmarks.MarkdownBenchmarkRunnerKt"
}

tasks.register<JavaExec>("benchmarkSmoke") {
    group = "verification"
    description = "Runs a lightweight benchmark smoke suite."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args("--smoke")
}
