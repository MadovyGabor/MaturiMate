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
* `--kind=full` (the default) also writes **one pack per subject** into `packs/<subject_id>/` (the same two files, one subject each, the catalogue tables `task_format` and `tag` repeated in every pack). `--kind=starter` writes only the first published lesson of each subject (down the first published topic of every level) with the topics above it: no tasks, no practice units. The manifest carries `kind` (`FULL` or `STARTER`) and `subject_ids`; an old manifest without them reads as `FULL`.

## Bundle into the app

```bash
./gradlew :tools:content-pipeline:bundleContent -PcontentVersion=2026102801 -PcontentDir=content
```

`-PcontentKind=full|starter` (default `full`: development builds keep every lesson; CI and the release build use `starter`). Exports into `shared/src/commonMain/composeResources/files/` (gitignored). The app installs this file on first start, and on later starts only when its `content_version` is higher than the installed one. A build without bundled content runs, but has no lessons.

## Import (server copy)

Start the local database (`docker compose -f dbServer/docker-compose.yml up -d`, settings in `dbServer/.env`; see [dbServer/README.md](../../dbServer/README.md)), then:

```bash
./gradlew :tools:content-pipeline:run --args="import content --dry-run"
```

`--dry-run` only validates. Without it, the content is written in one transaction.

## Publish (the packs the app downloads)

```bash
./gradlew :tools:content-pipeline:run --args="publish content --version=2026120101"
```

Runs the full export, uploads every pack to the private R2 bucket at `packs/<schema>/<subject>/<version>/content.db` and writes the `content_release` rows (the older release of the same subject and schema stops being current). It refuses to run before `import` wrote the same files to the server. Needs `DATABASE_URL`, `R2_ACCOUNT_ID`, `R2_CONTENT_ACCESS_KEY_ID` and `R2_CONTENT_SECRET_ACCESS_KEY` (environment or `dbServer/.env`). Details: [docs/runbooks/cloudflare.md](../../docs/runbooks/cloudflare.md).

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
        10-summary.md               SUMMARY unit (a lesson): front matter + pages and boxes
        20-practice.json            PRACTICE unit (a test) and its task list
```

A complete example is the test fixture `src/test/resources/content-valid/`.

* All ids (`subject_id`, `topic_id`, `learning_unit_id`, `task_id`, …) are written in the files and never change, because the app's user database refers to them.
* The exam subjects have fixed ids and codes, because the app's onboarding offers them before any content is installed (`CatalogSubject`): 1 `HU_MJL` (Hungarian, Hungarian schools), 2 `SK_SJL` (Slovak, Slovak schools), 3 `ANJ_B1` (English B1), 4 `MAT` (Mathematics), 5 `HU_SJSL` (Slovak language and Slovak literature, Hungarian schools), 6 `ANJ_B2` (English B2). One subject is one exam. A subject's folder uses these.
* In a gap task (`GAP_CHOICE`, `GAP_FILL`) the `*italic*` and `**bold**` markers must stay inside one piece of text between the gaps; they cannot span a gap.
* The options of a task keep the order of the file, and the app shows them in it. In a `TRUE_FALSE` task the first option is the true one ("igaz") and the second the false one ("hamis"): the app puts a check on the first and an X on the second.
* In `tasks.json`, options are referenced by their label (`"key": [{"option": "B"}]`); matching keys also name the prompt label. Ids of options, accepted answers and attachments are derived as `task_id * 100 + position`, so a task has at most 99 of each.
* Every task and unit has `source_type` (`OWN`, `TEACHER`, `AI_REVIEWED`, `PUBLIC_DOMAIN`). `AI_REVIEWED` needs `reviewed_at`; `TEACHER` and `PUBLIC_DOMAIN` need `source_note`. Provenance is checked, not imported.
* Texts use only `**bold**`, `*italic*` and `[[n]]` gap markers. Only the text of a lesson (pages and boxes) may also have lists, see below; a list in a task is an error.
* Files named `*_private.json` are never read.

## Lesson files (pages and boxes)

A lesson (`SUMMARY` unit) is one Markdown file: front matter, then the text. The format uses well-known syntax, so any Markdown editor shows it sensibly.

```markdown
---
learning_unit_id: 910101
title: Toldi: az indulás
estimated_minutes: 4
sort_order: 10
is_published: true
source_type: OWN
---
## Ki az a Toldi Miklós?

Text of the first page, with **bold** and *italic*.

:::example
A box with an example.
:::

## A testvérek

Text of the second page.

:::tip
A box with the thing to remember.
:::
```

* **`## Title` starts a page.** The title appears in the table of contents of the lesson. Text before the first `##` is a page without a title; a file without any `##` is one page. Only level 2 headings are allowed.
* **Boxes** are written `:::type` … `:::`, alone on their lines. Types: `example`, `tip`, `note`, `warning`, `quote`. The keyword is the same in every language; the label ("Példa", "Príklad") is an app string in both languages. An unknown type, a box that is not closed, or a box inside a box is an error.
* **Inside a page and a box** `**bold**`, `*italic*`, paragraphs and lists are allowed (no links, code or HTML).
* **Lists** are written like in Markdown, one item per line, with a blank line before and after the list:

  ```
  Három ismert program:

  - **Microsoft Word**: a legelterjedtebb.
  - **Google Docs**: böngészőben fut.
  - **LibreOffice Writer**: ingyenes.

  A lépések sorrendje fontos:

  1. Megírod a szöveget.
  2. Formázod.
  3. Átnézed a helyesírást.
  ```

  * `- ` (dash and a space) starts a bullet, `1. ` (number, dot, space) a numbered item. The app shows the bullets as "•" and numbers the items by position (1., 2., 3.), whatever number is written.
  * A list is not mixed: one list is all bullets or all numbered. A blank line ends it; a line without a marker right after an item continues that item.
  * A list item right after an ordinary line, without a blank line, starts a new block anyway; still leave the blank line for readability.
  * Items are short (one or two lines) and carry no nested lists. `+` and `*` markers are an error (`*` is italic).
  * Take care that a normal paragraph does not start with a number and a dot ("5. nap volt…") or "- " by accident: it would become a list.
  * Lists count in the 60 to 90 words of a page.
  * Not allowed in tasks (question, options, explanation): a list there is a validation error.
* **A short page** fits one phone screen without scrolling (about 60 to 90 words); a longer text, such as the plot of a work, is split into several pages.
* **Several parts of one topic** are several files in its folder; the number in the file name and the `sort_order` give the order, with tests between them. For example `10-intro.md`, `20-mini.json`, `30-part2.md`, `40-mini.json`, `50-test.json`.

## Tests (practice units)

A practice unit (`*.json`) has a `practice_kind`:

| Kind | Where | Meaning |
| :--- | :--- | :--- |
| `MINI` | leaf topic | a short test after one part of a lesson |
| `TOPIC` | leaf topic | the test of the whole topic (for example all of one work) |
| `FINAL` | parent topic | a test of everything below it (for example all of one author); its tasks come from the topics below |

A test round shows `round_size` tasks: every **required** task (`"is_core": true`, the default) and random ones of the other tasks (`"is_core": false`) to fill the round, in random order. `round_size` is at least the number of required tasks and at most the number of all tasks. For example 20 tasks with `round_size` 10 and no required task: every round shows a random half.

Only `FINAL` units may sit on a parent topic (a `FINAL` test on a leaf topic is an error); every other unit sits on a leaf topic. The tasks of a unit are listed by id; the tasks of a `FINAL` test must all lie in topics below its own. The exporter writes the pages into the `unit_page` and `page_block` tables, the kind into `learning_unit.practice_kind`.

## Explanations

* `explanation` (task, required): why the correct answer is correct. It works for every format.
* `why` (option, optional): why this option is wrong. The app shows it only to a student who picked that option. Write it where two answers are easy to mix up.

## What an import does (PostgreSQL)

* Upserts rows by id. A second run with the same files changes nothing.
* Replaces the rows owned by a task (options, keys, accepted answers, attachments, tags) and the task list of each practice unit.
* Deactivates tasks (`is_active`), learning units and topics (`is_published`) that are no longer in the files. Nothing is deleted.
* Moves the identity sequences past the highest imported id.
