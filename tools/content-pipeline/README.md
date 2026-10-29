# Content pipeline

Turns the lesson and task files (the source of truth, kept in Git) into:

* **`content.db` + `content-manifest.json`** for the app (`export`). No database server is needed.
* **the server's copy of the content** in PostgreSQL (`import`). Only needed once the backend exists; the user tables there reference content rows by foreign key.

Both commands use the same loader and validator. Nothing is written if any check fails; every problem is listed.

## Export (for the app)

From the repository root:

```bash
./gradlew :tools:content-pipeline:run --args="export content build/content --version=2026102801 --previous=releases/content.db"
```

* `--version` is `yyyyMMddNN` and must be higher than the previous release; the app only installs a higher version.
* `--previous` is the `content.db` of the last release. The export fails if any subject, topic, learning unit or task of it is missing from the files: switch rows off (`"is_active": false`, `"is_published": false`) instead of removing them, because users' progress refers to them.
* The schema comes from the shared `:database` module, the same code the app uses. The same files and version give a byte-identical file (same `sha256`).

## Bundle into the app

```bash
./gradlew :tools:content-pipeline:bundleContent -PcontentVersion=2026102801 -PcontentDir=content
```

Exports into `composeApp/src/commonMain/composeResources/files/` (gitignored). The app installs this file on first start, and on later starts only when its `content_version` is higher than the installed one. A build without bundled content runs, but has no lessons.

## Import (server copy)

Start the local database (`docker compose -f dbServer/docker-compose.yml up -d`, settings in `dbServer/.env`; see [dbServer/README.md](../../dbServer/README.md)), then:

```bash
./gradlew :tools:content-pipeline:run --args="import content --dry-run"
```

`--dry-run` only validates. Without it, the content is written in one transaction.

## Content folder

```
content/
  task_formats.json                 task_format rows (the 7 formats of the design)
  tags.json                         tag rows
  HU_MJL/
    subject.json                    subject row
    01-stilisztika/
      topic.json                    topic row (parent and subject come from the folders)
      01-szokepek/                  leaf topic
        topic.json
        tasks.json                  tasks of this topic
        10-summary.md               SUMMARY unit: front matter + text
        20-practice.json            PRACTICE unit and its task list
```

A complete example is the test fixture `src/test/resources/content-valid/`.

* All ids (`subject_id`, `topic_id`, `learning_unit_id`, `task_id`, …) are written in the files and never change, because the app's user database refers to them.
* In `tasks.json`, options are referenced by their label (`"key": [{"option": "B"}]`); matching keys also name the prompt label. Ids of options, accepted answers and attachments are derived as `task_id * 100 + position`, so a task has at most 99 of each.
* Every task and unit has `source_type` (`OWN`, `TEACHER`, `AI_REVIEWED`, `PUBLIC_DOMAIN`). `AI_REVIEWED` needs `reviewed_at`; `TEACHER` and `PUBLIC_DOMAIN` need `source_note`. Provenance is checked, not imported.
* Texts use only `**bold**`, `*italic*` and `[[n]]` gap markers.
* Files named `*_private.json` are never read.

## What an import does (PostgreSQL)

* Upserts rows by id. A second run with the same files changes nothing.
* Replaces the rows owned by a task (options, keys, accepted answers, attachments, tags) and the task list of each practice unit.
* Deactivates tasks (`is_active`), learning units and topics (`is_published`) that are no longer in the files. Nothing is deleted.
* Moves the identity sequences past the highest imported id.
