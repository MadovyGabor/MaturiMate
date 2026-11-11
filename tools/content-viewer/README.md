# Content viewer

A small read-only tool for the author: it shows the lessons, tasks and tests of the `content/` folder in the browser, so a topic can be reviewed quickly. It writes nothing, and the students never see it.

It reads the files again on every page load, so it always shows what is in the folder now (press F5 after a change). It is **not a validator**: the real check is the pipeline's export (`tools/content-pipeline`). The viewer only gives a quick look and a few hints.

## Run

Go is needed (`winget install --id GoLang.Go`), then from this folder:

```bash
go run . ../../content
```

It opens `http://127.0.0.1:8090` (reachable from this computer only). Stop it with Ctrl+C.

## What it shows

* **Overview** (the first page): per topic the number of lessons, pages, active tasks and tests, and how many things its report flags.
* **A topic:** the report, its tests, its lessons (pages, boxes, words per page) and its tasks (all seven formats, with the right answer, the `why` of wrong options and the explanation).
* **The report** compares the mix of formats with the target mix of the content guide and lists: tasks without explanation, repeated questions, pages over 90 words, a test whose `round_size` does not fit, tasks in no test, a topic with tasks but no test. Wrong options without `why` are only a note.

## Files

| File | Job |
| :--- | :--- |
| `main.go` | the web server, the page template |
| `tree.go` | subjects and the topic tree (`subject.json`, `topic.json`) |
| `lesson.go` | lesson files (Markdown pages and boxes) |
| `task.go` | `tasks.json` and how each format is shown |
| `unit.go` | test files (`*practice*.json`, `90-final.json`, …) |
| `stats.go` | the report and the target mix |
| `overview.go` | the overview table |

Only the Go standard library is used. The file formats are described in `tools/content-pipeline/README.md`; when they change, change the viewer too.
