// The Android app: MainActivity, the Application class, the manifest, launcher icons and the splash theme.
// Everything else comes from :shared.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "com.madovygabor.maturimate"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.madovygabor.maturimate"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            // R8 removes the code and resources the app does not use (most of the Material icons)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // signed with the debug key until the beta has its own key, so a release build can be
            // installed and tested on a phone (./gradlew :androidApp:installRelease)
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    // AppCompatActivity: the per-app language works on every Android version through it
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.datetime)

    //Koin Android
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    //Android Splash Screen API
    implementation(libs.core.splashscreen)

    debugImplementation(libs.compose.ui.tooling)
}

// The CI test fixture (content version 2000010101, see .github/workflows/ci.yml) must never ship as the app's starter:
// it clashes with the real content. A release build fails when the bundled content is the fixture, a debug build warns.
val fixtureContentVersion = "2000010101"
val repoRoot = rootProject.projectDir
val bundledContentManifests = fileTree(rootProject.file("shared/src/commonMain/composeResources/files")) {
    include("content-manifest.json", "packs/*/content-manifest.json")
}

fun registerFixtureCheck(taskName: String, fails: Boolean): TaskProvider<Task> {
    // locals only: the action must not hold the build script (configuration cache)
    val version = fixtureContentVersion
    val manifests = bundledContentManifests
    val root = repoRoot
    val pattern = Regex(""""content_version"\s*:\s*$version(?!\d)""")
    return tasks.register(taskName) {
        group = "verification"
        description = "Checks that the bundled content is not the CI test fixture (version $version)."
        inputs.files(manifests)
        doLast {
            val found = manifests.files.filter { pattern.containsMatchIn(it.readText()) }
            if (found.isNotEmpty()) {
                val message = "The bundled content is the CI test fixture (content_version $version) in " +
                    found.joinToString { it.relativeTo(root).path } +
                    ". Bundle the real content first: ./gradlew :tools:content-pipeline:bundleContent -PcontentVersion=<yyyyMMddNN> -PcontentKind=starter"
                if (fails) throw GradleException(message) else logger.warn("WARNING: $message")
            }
        }
    }
}

val verifyReleaseContent = registerFixtureCheck("verifyReleaseContent", fails = true)
val warnTestContent = registerFixtureCheck("warnTestContent", fails = false)
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease", "installRelease") }
    .configureEach { dependsOn(verifyReleaseContent) }
tasks.matching { it.name in setOf("assembleDebug", "installDebug") }.configureEach { dependsOn(warnTestContent) }
