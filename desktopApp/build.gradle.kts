// The desktop app: main(), the window and the desktop packaging. Everything else comes from :shared.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.reload.gradle.ComposeHotRun

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
}

compose.desktop {
    application {
        mainClass = "com.madovygabor.maturimate.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.madovygabor.maturimate"
            packageVersion = "1.0.0"
        }
    }
}

// ./gradlew :desktopApp:hotRun starts the app with Compose Hot Reload
tasks.withType<ComposeHotRun>().configureEach {
    mainClass.set("com.madovygabor.maturimate.MainKt")
}
