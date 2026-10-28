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

tasks.test {
    systemProperty("repoRoot", rootDir.absolutePath)
}
