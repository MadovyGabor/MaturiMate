// Shared code of the apps: UI, features, Koin wiring and the platform (expect/actual) code for Android and desktop.
// The apps themselves are thin modules: androidApp and desktopApp.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.madovygabor.maturimate.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        // Compose resources (strings, fonts, the bundled content) are packaged as Android resources
        androidResources {
            enable = true
        }
    }

    jvm()

    sourceSets {


        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.datetime)

            // Navigation
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.lifecycle.viewmodel.nav3)
            implementation(libs.kotlinx.serialization.json)

            // DI
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // JSON & Database
            implementation(libs.kotlinx.serialization.json)
            implementation(projects.database)

            // Supabase: sign-up and sign-in
            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
            implementation(libs.supabase.compose.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.multiplatform.settings.no.arg)
            // the download of the content packs (the engine is the one of the Supabase client)
            implementation(libs.ktor.client.core)

            // Material icons
            implementation(libs.compose.material.icons.extended)

        }

        androidMain.dependencies {
            //Koin Android
            implementation(libs.koin.android)

            // the file picker of the data export
            implementation(libs.androidx.activity.compose)

            // HTTP engine of the Supabase client
            implementation(libs.ktor.client.okhttp)

        }

        jvmMain.dependencies {
            // HTTP engine of the Supabase client
            implementation(libs.ktor.client.okhttp)
            // the desktop Google sign-in's own loopback receiver (GoogleSignIn.jvm.kt); used directly, so declared here
            implementation(libs.ktor.server.cio)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.mock)
            implementation(libs.compose.ui.test)
        }

    }
}

dependencies {
    // Compose previews in Android Studio; the Android-KMP library has no debug variant
    "androidRuntimeClasspath"(libs.compose.ui.tooling)
}

// Test classes run in several JVMs at once; each JVM still runs its classes one after another
tasks.withType<Test>().configureEach {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

// Test groups for CI: ./gradlew :shared:jvmTest -PtestGroup=<name> runs one group; no property runs everything.
// THE table (group -> test class patterns). The group "other" is not listed: it is every test that is in no
// listed group, so a new test can never fall out of all groups. Keep the names in step with .github/workflows/ci.yml.
val testGroups: Map<String, List<String>> = mapOf(
    "frame" to listOf("com.madovygabor.maturimate.core.*"),
    "onboarding" to listOf("com.madovygabor.maturimate.features.appOnboarding.*"),
    "auth" to listOf("com.madovygabor.maturimate.features.auth.*"),
    "settings" to listOf(
        "com.madovygabor.maturimate.features.profileDetails.presentation.Settings*",
        "com.madovygabor.maturimate.features.profileDetails.presentation.ProfileSignInNavigationTest",
    ),
    "reminder" to listOf("com.madovygabor.maturimate.features.reminder.*"),
    "profile" to listOf("com.madovygabor.maturimate.features.userProfile.*"),
)
val otherGroup = "other"

providers.gradleProperty("testGroup").orNull?.let { group ->
    if (group != otherGroup && group !in testGroups) {
        throw GradleException(
            "Unknown -PtestGroup='$group'. Known groups: ${(testGroups.keys + otherGroup).joinToString()}"
        )
    }
    tasks.withType<Test>().configureEach {
        filter {
            if (group == otherGroup) {
                testGroups.values.flatten().forEach { excludeTestsMatching(it) }
            } else {
                testGroups.getValue(group).forEach { includeTestsMatching(it) }
            }
        }
        // A group that selects no test fails the build, so a wrong pattern is never a silent green
        var executed = 0L
        afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ descriptor, result ->
            if (descriptor.parent == null) executed = result.testCount
        }))
        doLast {
            if (executed == 0L) {
                throw GradleException("Test group '$group' selected no tests: check the table testGroups in shared/build.gradle.kts")
            }
        }
    }
}

// Test coverage (./gradlew :shared:koverLog, :shared:koverHtmlReport). Generated code, Composable functions and previews is left out.
kover {
    reports {
        // ./gradlew :shared:koverVerify fails when the line coverage drops under this (CI runs it)
        verify {
            rule { minBound(80) }
        }
        filters {
            excludes {
                packages("maturimate.shared.generated.resources")
                annotatedBy("androidx.compose.runtime.Composable", "androidx.compose.ui.tooling.preview.Preview")
                classes("*ComposableSingletons*")
            }
        }
    }
}
