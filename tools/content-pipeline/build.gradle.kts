// Content pipeline: imports lesson and task files into PostgreSQL.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.postgresql)
    implementation(projects.database)

    testImplementation(libs.kotlin.testJunit)
}

application {
    mainClass.set("com.madovygabor.maturimate.pipeline.MainKt")
}

// Paths given on the command line are relative to the repository root
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

// Exports the content files into the app's resources, so the app ships them as its bundled content:
// ./gradlew :tools:content-pipeline:bundleContent -PcontentVersion=2026102801 [-PcontentDir=content]
tasks.register<JavaExec>("bundleContent") {
    group = "content"
    description = "Exports the content files into composeApp/src/commonMain/composeResources/files."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = rootDir
    val contentDir = providers.gradleProperty("contentDir").orElse("content")
    val contentVersion = providers.gradleProperty("contentVersion")
    argumentProviders += CommandLineArgumentProvider {
        listOf(
            "export",
            contentDir.get(),
            "composeApp/src/commonMain/composeResources/files",
            "--version=${contentVersion.orNull ?: error("-PcontentVersion=yyyyMMddNN is required")}",
        )
    }
}

tasks.test {
    systemProperty("repoRoot", rootDir.absolutePath)
}
