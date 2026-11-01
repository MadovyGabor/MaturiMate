# Toolchain update: AGP 9 (Milestone 6)

Plan for [Milestone 6](ROADMAP.md#milestone-6-toolchain-update-agp-9). Status: **approved in principle on 2026-11-01; waiting for the go to start 6.2**. Versions were checked on 2026-11-01 against Maven Central, Google Maven, the Gradle release list and GitHub releases.

The goal: the build moves to Android Gradle Plugin 9 and the newest **stable** version of every tool and library, and the project gets the module layout JetBrains recommends: one `shared` module and one small entry module per app. The app looks and behaves the same; only the build changes.

Owner's choices (2026-11-01, see [DECISIONS](DECISIONS.md)):

1. `composeApp` is renamed to **`shared`**.
2. Every app gets its own module: **`androidApp`** and **`desktopApp`**.
3. Structure first, on the current AGP 8.13; versions after.
4. **Stable versions only**: no alpha, beta or release candidate, even where JetBrains pairs a stable release with a pre-release.

---

## 1. Versions

Every entry is the newest stable release on 2026-11-01.

### Build tools

| Component | Now | Target | Note |
| :--- | :--- | :--- | :--- |
| Android Gradle Plugin | 8.13.2 | **9.5.0** | Needs Gradle 9.6 or newer and JDK 17 or newer; supports up to API 37 |
| Gradle | 9.5.1 | **9.8.2** | Gradle 9.6 and newer do not run AGP 8, so it moves together with AGP |
| Kotlin | 2.4.21 | 2.4.21 | Already the newest |
| Compose Multiplatform (Gradle plugin) | 1.11.1 | **1.12.2** | Built on Jetpack Compose 1.12.1 |
| Compose Hot Reload | 1.2.0 | 1.2.0 | Already the newest |
| SQLDelight | 2.4.1 | 2.4.1 | Supports the AGP 9 DSL and built-in Kotlin since 2.3.2 |
| Foojay toolchain resolver | 1.0.0 | 1.0.0 | Already the newest |
| JDK for the build (CI) | 17 | **21** | The JDK bundled with Android Studio; Compose Hot Reload runs code built for Java 21 or older. The Android bytecode target stays Java 11 |

### Android

| Component | Now | Target | Note |
| :--- | :--- | :--- | :--- |
| compileSdk / targetSdk | 36 / 36 | **37 / 37** | Compose 1.12 needs compileSdk 37; API 37 needs AGP 9.1 or newer |
| minSdk | 26 | 26 | Unchanged (supabase-kt) |
| activity-compose | 1.13.0 | 1.13.0 | Already the newest |
| core-splashscreen | 1.2.0 | 1.2.0 | Already the newest |

### Libraries

| Component | Now | Target | Note |
| :--- | :--- | :--- | :--- |
| Navigation3 (JetBrains) | 1.1.2 | **1.2.0** | |
| Lifecycle (JetBrains, ViewModel and Navigation3 integration) | 2.10.0 | **2.11.0** | Paired with Compose 1.12 |
| Material 3 (JetBrains) | 1.9.0 (through the `compose.material3` alias) | **1.9.0**, as its own catalog entry | Newest stable. JetBrains pairs Compose 1.12.2 with `1.12.0-alpha03`; we do not use it (stable only) |
| Material icons (`material-icons-extended`) | 1.7.3 | 1.7.3 | No newer version is published; still works |
| Ktor (OkHttp engine, mock client) | 3.5.1 | **3.6.1** | |
| supabase-kt | 3.8.0 | 3.8.0 | Already the newest |
| Koin | 4.2.2 | 4.2.2 | Already the newest |
| kotlinx.coroutines / serialization / datetime | 1.11.0 / 1.12.0 / 0.8.0 | unchanged | Already the newest |
| PostgreSQL JDBC (content pipeline) | 42.7.14 | 42.7.14 | Already the newest |

Catalog entries that nothing uses are removed: `androidx-appcompat`, `androidx-espresso`, `androidx-testExt`, `androidx-core-ktx`, `kotlinx-coroutines-core`, `junit`.

### CI (GitHub Actions)

| Action | Now | Target |
| :--- | :--- | :--- |
| `actions/checkout` | v4 | **v7** |
| `actions/setup-java` | v4 | **v6** |
| `gradle/actions/setup-gradle` | v4 | **v6** |
| `mikepenz/action-junit-report` | v5 | **v6** |

---

## 2. What AGP 9 changes

AGP 9 is a larger step than a usual update. The changes that affect this project:

1. **Kotlin Multiplatform and the Android app plugin can no longer be in one module.** Today `composeApp` applies `kotlinMultiplatform` and `com.android.application` together, and `:database` applies `kotlinMultiplatform` and `com.android.library`. AGP 9 refuses both. Shared code goes into a module with the new **Android-KMP library plugin** (`com.android.kotlin.multiplatform.library`), and the Android app becomes its own small module.
2. **No top-level `android {}` block in shared modules.** The Android settings move inside `kotlin { android { … } }`: `namespace`, `compileSdk`, `minSdk`, `compilerOptions`. `compileOptions` and `kotlinOptions` are no longer needed.
3. **One variant per library.** The shared library has no build types or flavors, so `debugImplementation(...)` does not exist there. The preview tooling goes on `androidRuntimeClasspath`.
4. **Off by default in the library:** Android resources (Compose resources need `androidResources { enable = true }`, or the app crashes when it loads a string), Java sources, and Android unit and device tests. We have no Android tests (all tests are `jvmTest`), so nothing to rename.
5. **Built-in Kotlin.** AGP compiles Kotlin itself; the `org.jetbrains.kotlin.android` plugin is not allowed.
6. **Only the new DSL.** The old variant APIs (`applicationVariants`, `BaseExtension` …) are gone. We use none of them.
7. **New defaults** (each could be switched back with a Gradle property, but we keep the new ones):
   * `targetSdk` defaults to `compileSdk` when not set (we set it anyway).
   * `android.useAndroidX` and non-transitive R classes are on by default, so the two lines in `gradle.properties` can go.
   * Each module needs a unique namespace.
   * Stricter R8 keep rules, and a missing ProGuard file fails the build. Minify is off in our release build today, so this matters only when it is turned on (beta).
8. **The opt-out flags end with AGP 10**, so staying on the old plugin is not a long-term option.

---

## 3. New module layout

Before:

```
:composeApp   KMP + com.android.application   shared UI, features, the Android app and the desktop app
:database     KMP + com.android.library       SQLDelight, drivers, content installer
:tools:content-pipeline   Kotlin/JVM
```

After:

```
:shared       KMP (android + jvm) + Android-KMP library plugin + Compose
              all UI and features, the expect/actual code for Android and desktop, Compose resources
              (strings, fonts, the bundled content.db), the jvmTest tests
:androidApp   com.android.application (built-in Kotlin) + Compose
              MainActivity, MyApplication, AndroidManifest.xml, launcher icons, splash theme, app_name
:desktopApp   Kotlin/JVM + Compose + Compose Hot Reload
              main.kt, the window, desktop packaging (compose.desktop { application { } })
:database     KMP (android + jvm) + Android-KMP library plugin   unchanged content
:tools:content-pipeline   unchanged
```

One `shared` module is enough because both apps use the shared UI; JetBrains suggests splitting into `sharedLogic` and `sharedUI` only when an app has its own native UI.

What moves:

| From | To |
| :--- | :--- |
| `composeApp/` (everything else) | `shared/` (`git mv`, so the history follows) |
| `composeApp/src/androidMain/kotlin/.../MainActivity.kt` | `androidApp/src/main/kotlin/...` |
| `composeApp/src/androidMain/kotlin/.../core/di/MyApplication.kt` | `androidApp/src/main/kotlin/...` |
| `composeApp/src/androidMain/AndroidManifest.xml` | `androidApp/src/main/AndroidManifest.xml` |
| `composeApp/src/androidMain/res/` (icons, `splash.xml`, `strings.xml`, Play Store images) | `androidApp/src/main/res/` |
| `composeApp/src/jvmMain/kotlin/.../main.kt` | `desktopApp/src/main/kotlin/...` |

What stays in `shared/src/androidMain` and `shared/src/jvmMain`: the `actual` implementations (`AppModul`, `SupabaseClientFactory`, `NotificationPermission`, `AppLocale`, `androidSystemPadding`), because common code needs them on every platform.

Names and ids:

* The **application id stays `com.madovygabor.maturimate`**, with the same signing key. Then the new build installs over the old app on the phone, the user database stays, and Google sign-in keeps working (the Android OAuth client is tied to the package name and the signing certificate).
* Namespaces: `androidApp` `com.madovygabor.maturimate` (the manifest's `.MainActivity` and `.core.di.MyApplication` stay as they are), `shared` `com.madovygabor.maturimate.shared`, `database` `com.madovygabor.maturimate.database`.
* The Kotlin packages of the source files do not change, so the desktop main class stays `com.madovygabor.maturimate.MainKt`.
* Compose resources stay in the `shared` module. Their generated class moves from `maturimate.composeapp.generated.resources` to `maturimate.shared.generated.resources`, so the `import maturimate.composeapp.generated.resources.*` lines change once (270 lines in 36 files, part of the rename step).

New commands:

```bash
./gradlew :database:jvmTest :tools:content-pipeline:test :shared:jvmTest   # all tests
./gradlew :androidApp:assembleDebug                                        # Android debug APK
./gradlew :desktopApp:run                                                  # desktop app
./gradlew :desktopApp:hotRun                                               # desktop app with hot reload
./gradlew :shared:compileKotlinJvm                                         # quick compile check
```

---

## 4. What we gain

* **Builds that keep working:** AGP 10 removes the old library plugin; after this milestone nothing in the build depends on deprecated APIs or opt-out flags.
* **Compose Multiplatform 1.12** (Jetpack Compose 1.12) with Navigation3 1.2 and Lifecycle 2.11: current fixes and APIs for the lesson and task screens of Milestone 7, instead of building them on versions that need a migration later.
* **API 37** as compile and target SDK, ahead of Google Play's yearly target SDK requirement.
* **The recommended layout:** each app module holds only its entry point and platform setup; `shared` is the same for every app. A future iOS or web app (Milestone 12) becomes one more small module next to them, with no change to the others.
* **Less Android build work:** the library has one variant instead of debug and release, and Android resources are processed only where they are switched on.
* **Fewer warnings:** the deprecated `compose.*` aliases, `compose.uiTooling` and `org.jetbrains.compose.ui.tooling.preview.Preview` are replaced with catalog entries and `androidx.compose.ui.tooling.preview.Preview`.

---

## 5. Implementation steps

Each step is one commit on `chore/toolchain-agp9`, builds on its own, and is reviewed before the next one. After every step: all tests, the Android debug build and the desktop app.

### 6.2. `:database` to the Android-KMP library plugin (AGP 8.13)

The new plugin already works on AGP 8.10 and newer, so this happens before the version jump, and a problem here is clearly the plugin's.

* `libs.versions.toml`: plugin `androidMultiplatformLibrary` (`com.android.kotlin.multiplatform.library`, version `agp`); root `build.gradle.kts`: `apply false`.
* `database/build.gradle.kts`: the new plugin instead of `androidLibrary`; `androidTarget { }` and the `android { }` block become `kotlin { android { namespace, compileSdk, minSdk, compilerOptions { jvmTarget } } }`.
* The `jvmShared` source set group (`withAndroidTarget()` + `withJvm()`): check that it still matches the new Android target; if not, select the target by platform type.
* Done when: database tests pass and the Android app still opens the content and user databases.

### 6.3. Rename `composeApp` to `shared` (AGP 8.13)

Only a rename, no build logic changes, so the diff is easy to review.

* `git mv composeApp shared`; `settings.gradle.kts`.
* Compose resources imports: `…composeapp.generated.resources` → `…shared.generated.resources`.
* Every path that names `composeApp`: `bundleContent` in `tools/content-pipeline/build.gradle.kts`, `.gitignore` (the bundled content files; the old `prefs.preferences_pb` line from DataStore goes), CI, `AGENTS.md`, `README.md`, the docs, the comment in `database/build.gradle.kts`.
* Done when: tests pass, the Android and desktop apps start.

### 6.4. `:androidApp` module and `:shared` as a library (AGP 8.13)

* New `androidApp/build.gradle.kts`: `com.android.application`, `composeMultiplatform`, `composeCompiler`, and the Kotlin Android plugin (`org.jetbrains.kotlin.android`, a new catalog entry) only for as long as AGP 8 needs it (removed in 6.6). Dependencies: `projects.shared`, `activity-compose`, `core-splashscreen`, Koin Android. The `android { }` block from `composeApp` (application id, versions, SDKs, packaging, release build type).
* Move the Android files listed in section 3 with `git mv`.
* `shared/build.gradle.kts`: the Android-KMP library plugin, `kotlin { android { namespace = "com.madovygabor.maturimate.shared", compileSdk, minSdk, androidResources { enable = true } } }`; the top-level `android { }` block, `applicationId` and versions go; `debugImplementation(compose.uiTooling)` waits for 6.6.
* Android Studio run configuration: `androidApp` (local, not in the repository).
* Done when: tests pass, `:androidApp:assembleDebug` builds, and on the phone the app installs **over** the current one and keeps the profile; strings, fonts and the bundled content load; the splash screen and Google sign-in work.

### 6.5. `:desktopApp` module (AGP 8.13)

* New `desktopApp/build.gradle.kts`: `kotlinJvm`, `composeMultiplatform`, `composeCompiler`, `composeHotReload`. Dependencies: `projects.shared`, `compose.desktop.currentOs`, `kotlinx-coroutines-swing`. The `compose.desktop { application { … } }` block moves here from `shared`.
* `main.kt` moves with `git mv`; `shared` drops `compose.desktop.currentOs`, the hot reload plugin and the `compose.desktop` block.
* Done when: `:desktopApp:run` and `:desktopApp:hotRun` start the app, a change in `shared` reloads, and `jvmTest` still passes.

### 6.6. AGP 9.5, Gradle 9.8, SDK 37

* `agp = "9.5.0"`, wrapper to Gradle 9.8.2 (`./gradlew wrapper --gradle-version 9.8.2`).
* `compileSdk` and `targetSdk` 37.
* Remove the Kotlin Android plugin from `androidApp` (built-in Kotlin) and the old `androidLibrary` plugin alias.
* `gradle.properties`: remove `android.useAndroidX` and `android.nonTransitiveRClass` (now the defaults). No opt-out flags (`android.newDsl=false`, `android.builtInKotlin=false`, `android.enableLegacyVariantApi`); if a plugin needs one, we stop and discuss.
* Preview tooling back: `androidRuntimeClasspath(...)` for `ui-tooling` in `shared`.
* Check the Android 17 behaviour changes for apps that target API 37 (edge-to-edge, notifications, locale).
* Done when: all tests and both app builds pass with no AGP deprecation warnings from our build scripts, and the app works on the phone.

### 6.7. Compose Multiplatform 1.12 and libraries

* `composeMultiplatform = "1.12.2"`, `cmpNavigation3 = "1.2.0"`, `androidx-lifecycle = "2.11.0"`, `ktor = "3.6.1"`.
* The `compose.*` aliases become catalog entries: `org.jetbrains.compose.runtime:runtime`, `…foundation:foundation`, `…ui:ui`, `…components:components-resources`, `…ui:ui-tooling-preview`, `…ui:ui-tooling`, `org.jetbrains.compose.material3:material3:1.9.0`, and `material-icons-extended`. `compose.desktop.currentOs` stays.
* `org.jetbrains.compose.ui.tooling.preview.Preview` → `androidx.compose.ui.tooling.preview.Preview` in the 15 files that use it.
* Remove the unused catalog entries (section 1).
* Fix what the new versions deprecate or change in our code (Navigation3 and Lifecycle APIs in `NavigationRoot`, `MainFrameNavigation` and the ViewModels).
* Done when: all tests pass, the flaky `AppViewModelTest` is run several times; onboarding, profile and tabs checked on the phone and on desktop; hot reload still works.

### 6.8. CI and documentation

* `.github/workflows/ci.yml`: the new action versions, JDK 21, `:shared:jvmTest`, `:androidApp:assembleDebug`, `:desktopApp:jar` (instead of `:composeApp:jvmJar`), report paths under `shared/build/…`.
* `AGENTS.md` commands, `README.md` (its version table is old), `docs/ARCHITECTURE.md` (stack table, module layout, source tree), `docs/design-system/README.md` and the other docs that name `composeApp`.
* `DECISIONS.md`: what was done, and anything decided along the way.
* Final phone test with a release-like build (still without minify), then the roadmap checkboxes.

---

## 6. Risks and how we handle them

| Risk | What we do |
| :--- | :--- |
| A plugin does not work with the Android-KMP plugin or AGP 9 (SQLDelight, Compose Hot Reload, Compose resources) | The structure changes (6.2 to 6.5) happen before the version jump, so the cause of a failure is clear. SQLDelight states support since 2.3.2. If something breaks without a fix, we stop and decide together instead of adding opt-out flags. |
| Compose resources missing from the APK (strings, fonts, the bundled `content.db`) | `androidResources { enable = true }` in `shared`; checked on the phone in 6.4. |
| The update does not install over the old app, or Google sign-in breaks | Same application id and signing key; checked on the phone in 6.4. |
| Hot reload with the app in a separate Kotlin/JVM module | Documented for Kotlin/JVM projects (`hotRun`); checked in 6.5. If it does not pick up changes in `shared`, `desktopApp` becomes a KMP module with a `jvm()` target (`hotRunJvm`). |
| API 37 behaviour changes | Read the Android 17 behaviour changes in 6.6. |
| The flaky `AppViewModelTest` hides a real failure | Run it several times after 6.7; the fix itself stays in 10.0 unless the new versions make it worse. |
| Android Studio support for AGP 9.5 | Use the Android Studio version that the AGP 9.5 release notes require. |
| A newer stable version comes out during the milestone | Versions are checked once more at 6.6 and 6.7 and the newest stable one is used. |

---

## 7. Notes from the work

* **6.2:** on the Android-KMP target `withAndroidTarget()` in the hierarchy template matches nothing, so the `jvmShared` group of `:database` selects the target by its type (`KotlinMultiplatformAndroidLibraryTarget`). On AGP 8.13 the new target reports the platform type `jvm`, so a platform type check would not work.
* **6.3:** the generated resources class moved to `maturimate.shared.generated.resources`; the APK carries the resources under `assets/composeResources/maturimate.shared.generated.resources/` (strings, fonts, drawables, `content.db`).
* **6.4:** `shared` keeps Koin Android and the OkHttp engine for its Android actuals; `androidApp` gets `activity-compose`, Koin Android and AndroidX Compose, the splash screen and, for debug builds, the UI tooling. The merged manifest keeps the package name, the permissions and the application and activity classes.
* **6.5:** the desktop test classpath (`shared` `jvmTest`) gets `compose.desktop.currentOs` and `kotlinx-coroutines-swing`, which the desktop app used to bring; the app itself gets them in `desktopApp`.

---

## Sources

* [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) and [AGP release notes (9.5)](https://developer.android.com/build/releases/gradle-plugin)
* [Android-KMP library plugin](https://developer.android.com/kotlin/multiplatform/plugin)
* [Migrating a multiplatform project to AGP 9 (JetBrains)](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html) and [recommended project structure](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html)
* [Compose Multiplatform releases](https://github.com/JetBrains/compose-multiplatform/releases) and [Compose Hot Reload](https://github.com/JetBrains/compose-hot-reload)
* [SQLDelight changelog](https://github.com/sqldelight/sqldelight/blob/master/CHANGELOG.md) (2.3.2: AGP 9 DSL and built-in Kotlin)
