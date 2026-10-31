# MaturiMate: notes for AI agents

Kotlin Multiplatform app (Android and desktop JVM) with Compose Multiplatform: an offline-first micro-learning app (short lessons and practice tasks) for students preparing for the Slovak graduation exam (*maturita*), in Hungarian and Slovak. Read [README.md](README.md) for the full picture.

## Read before you work

| Before you… | Read |
| :--- | :--- |
| change anything | [docs/ROADMAP.md](docs/ROADMAP.md) (where we are, what comes next) |
| change a design or a past decision | [docs/DECISIONS.md](docs/DECISIONS.md) (what was decided and why) |
| touch modules, layers, navigation or data | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| build or change UI | [docs/design-system/README.md](docs/design-system/README.md) and [tokens.json](docs/design-system/tokens.json) |
| touch content, the validator, exporter or importer | [tools/content-pipeline/README.md](tools/content-pipeline/README.md) |
| touch Supabase settings (auth, providers, e-mail) | [docs/supabase-setup.md](docs/supabase-setup.md) |
| touch the server database | [dbServer/README.md](dbServer/README.md); the schema is in `supabase/migrations` (add a new migration, never edit an applied one); row level security is tested in `supabase/tests/rls_test.sql` |

## How we work

- **Plan first, code second.** For anything beyond a small fix, write a short plan and wait for the owner's approval before writing code.
- **Small steps.** One roadmap step per change. Update the roadmap checkbox when a step is done.
- **Record decisions.** When the owner decides something lasting, add a short entry to `docs/DECISIONS.md` (what and why). Mark replaced entries instead of deleting them.
- **Branches:** never commit to `main`. Use `feat/…`, `fix/…`, `docs/…`, `chore/…`. Commit messages follow Conventional Commits (`feat:`, `fix:`, `docs:`, `ci:`, `chore:`).
- Documentation, code and comments are in **English**. App text is Hungarian and Slovak, in `composeResources/values-hu` and `values-sk`.

## Commands

```bash
./gradlew :database:jvmTest :tools:content-pipeline:test :composeApp:jvmTest   # all tests
./gradlew :composeApp:assembleDebug                                            # Android debug APK
./gradlew :composeApp:run                                                      # desktop app
./gradlew :composeApp:compileKotlinJvm                                         # quick compile check
```

`:composeApp:jvmTest` needs bundled content; CI creates it with `./gradlew :tools:content-pipeline:bundleContent -PcontentDir=tools/content-pipeline/src/test/resources/content-valid -PcontentVersion=2000010101`.

## Rules

- **Layers:** screens talk to ViewModels, ViewModels to repository interfaces in `domain/`. Generated SQLDelight classes stay in `data/`. The UI never calls a database directly.
- **Design system:** screens use theme values only (`MaterialTheme.colorScheme`, `typography`, `shapes`, `extendedColors`, `extendedTypography` and `spacing`). No `Color(0xFF…)`, font size, corner radius or spacing value written directly in a screen. A new color or style goes into `docs/design-system/tokens.json` first.
- **Components are built when a screen needs them**, not in advance. A new reusable component goes into `core/presentation/uiComponents/` (named `App…`, built from theme values only), with a short page in `docs/design-system/components/`. Check there first and reuse what exists.
- **Content ids never change and content rows are never deleted**, only switched off.
- **No official exam content** (NIVAM papers, answer keys, audio) in this repository or in the app. V1 uses only content written for this project.
- **Lessons and tasks live in a separate private repository**, cloned into `content/` (gitignored). Tests use the fixtures under `tools/content-pipeline/src/test/resources/`.
- **Secrets** (connection strings, secret keys) stay in `.env` or `local.properties`, never in the repository. The app holds only public values (`core/config/SupabaseConfig.kt`); everything else is protected by row level security.

## Local, uncommitted folders

- `llm/`: the owner's local planning notes, design inspiration and outputs of AI sessions (`llm/outputs/`). Read it when useful, but `docs/` is the source of truth; put scratch output in `llm/outputs/`. Screenshots of other apps in it are licensed and must never be committed.
- `content/`: the private content repository (see above).
