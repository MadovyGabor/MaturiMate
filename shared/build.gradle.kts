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
            // the download of the content packs (the engine is the one of the Supabase client)
            implementation(libs.ktor.client.core)

            // Material icons
            implementation(libs.compose.material.icons.extended)

        }

        androidMain.dependencies {
            //Koin Android
            implementation(libs.koin.android)

            // HTTP engine of the Supabase client
            implementation(libs.ktor.client.okhttp)

        }

        jvmMain.dependencies {
            // HTTP engine of the Supabase client
            implementation(libs.ktor.client.okhttp)
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
