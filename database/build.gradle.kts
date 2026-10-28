// Shared database code: SQLDelight schemas of the content and user databases and the platform drivers.
// Used by the app (composeApp) and by the content exporter (tools/content-pipeline).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm()

    // Android and desktop share the java.io file code of the content installer
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.runtime)
            api(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        jvmMain.dependencies {
            api(libs.sqldelight.jvm.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.madovygabor.maturimate.database"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

sqldelight {
    databases {
        // Read-only content database, shipped as a prebuilt file
        create("ContentDatabase") {
            packageName.set("com.madovygabor.maturimate.database.content")
            srcDirs.setFrom("src/commonMain/sqldelight/content")
        }
        // User database: progress and settings of the local user
        create("UserDatabase") {
            packageName.set("com.madovygabor.maturimate.database.user")
            srcDirs.setFrom("src/commonMain/sqldelight/user")
        }
    }
}
