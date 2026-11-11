// content-viewer: a read-only viewer for the lessons and tasks in the content folder.
// Usage: go run . <content folder>
package main

import (
	"fmt"
	"html/template"
	"log"
	"net/http"
	"os"
	"os/exec"
	"runtime"
	"strconv"
)

// The address the viewer listens on. 127.0.0.1 means "this computer only": nobody else on the network can reach it.
const address = "127.0.0.1:8090"

// page is the HTML of the whole page. {{...}} are template commands; html/template escapes the texts for us.
// "topic" is a named piece that calls itself for the children, which draws the tree to any depth.
var page = template.Must(template.New("page").Parse(`<!doctype html>
<html lang="hu"><head><meta charset="utf-8"><title>MaturiMate content viewer</title>
<style>
  :root { color-scheme: light dark; --muted: #888; --line: #8884; --accent: #3b7ddd; --warn: #c33; }
  body { font-family: system-ui, sans-serif; margin: 0; line-height: 1.5; display: grid; grid-template-columns: 22rem 1fr; min-height: 100vh; }
  nav { padding: 1rem 1rem 3rem; border-right: 1px solid var(--line); overflow-y: auto; max-height: 100vh; position: sticky; top: 0; }
  main { padding: 1.5rem 2.5rem 4rem; max-width: 48rem; }
  h1 { font-size: 1.1rem; margin-top: 0; }
  ul { list-style: none; padding-left: 1.1rem; margin: .1rem 0; }
  nav a { color: inherit; text-decoration: none; }
  nav a:hover { color: var(--accent); }
  .subject { font-weight: 600; margin-top: 1rem; }
  .off { color: var(--muted); text-decoration: line-through; }
  .muted { color: var(--muted); font-weight: normal; font-size: .85rem; }
  .error { color: var(--warn); }
  .lesson { border-top: 1px solid var(--line); margin-top: 2rem; padding-top: 1rem; }
  .lesson h2 { margin: 0 0 .2rem; font-size: 1.25rem; }
  .page { border: 1px solid var(--line); border-radius: 12px; padding: .8rem 1.1rem; margin: 1rem 0; }
  .page h3 { margin: 0 0 .4rem; font-size: 1.05rem; }
  .words { float: right; }
  .long { color: var(--warn); font-weight: 600; }
  .box { border-left: 4px solid var(--accent); background: #3b7ddd14; padding: .4rem .8rem; margin: .6rem 0; border-radius: 0 8px 8px 0; }
  .box.tip { border-color: #2e9e5b; background: #2e9e5b14; }
  .box.warning { border-color: var(--warn); background: #cc333314; }
  .box.note { border-color: var(--muted); background: #8881; }
  .box b.label { display: block; font-size: .75rem; text-transform: uppercase; letter-spacing: .05em; color: var(--muted); }
  p { margin: .5rem 0; }
  .tasks h2 { margin-top: 2.5rem; border-top: 1px solid var(--line); padding-top: 1rem; }
  .task { border: 1px solid var(--line); border-radius: 12px; padding: .7rem 1.1rem; margin: .8rem 0; }
  .task.off { opacity: .55; }
  .fmt { font-size: .72rem; font-weight: 700; background: var(--accent); color: #fff; border-radius: 6px; padding: .1rem .45rem; }
  .q { font-weight: 600; margin: .5rem 0; }
  .gap { background: #2e9e5b30; border-bottom: 2px solid #2e9e5b; padding: 0 .3rem; border-radius: 4px; }
  .opts { padding-left: 0; }
  .opts li { padding: .15rem .5rem; border-radius: 6px; }
  .opts li.ok { background: #2e9e5b22; font-weight: 600; }
  .pos { font-weight: 700; color: var(--accent); margin-right: .3rem; }
  .why { font-size: .85rem; color: var(--muted); font-weight: normal; margin-left: 1.2rem; }
  .expl { margin-top: .5rem; padding-top: .4rem; border-top: 1px dashed var(--line); font-size: .92rem; }
  table.pairs td { padding: .1rem .8rem .1rem 0; }
  .acc { font-size: .9rem; }
  table.grid { border-collapse: collapse; width: 100%; margin: .5rem 0 1rem; font-size: .92rem; }
  table.grid th, table.grid td { text-align: left; padding: .25rem .6rem; border-bottom: 1px solid var(--line); }
  table.grid td.n, table.grid th.n { text-align: right; }
  table.grid tr.total td { font-weight: 700; }
  .bar { display: inline-block; height: .6rem; background: var(--accent); border-radius: 3px; vertical-align: middle; }
  .off-mix { color: var(--warn); font-weight: 600; }
  .report { border: 1px solid var(--line); border-radius: 12px; padding: .6rem 1.1rem; margin: 1rem 0; }
  .report h2 { font-size: 1rem; margin: .2rem 0; }
  .report ul { list-style: disc; padding-left: 1.4rem; }
  .page ul { list-style: disc; padding-left: 1.4rem; }
  .page ol { padding-left: 1.4rem; margin: .1rem 0; }
  .ok-text { color: #2e9e5b; font-weight: 600; }
  a.plain { color: inherit; }
</style></head><body>
<nav>
  <h1>MaturiMate content viewer</h1>
  <div class="muted">{{.Dir}} (F5 = read the files again)</div>
  <div><a href="/">▦ Overview of all topics</a></div>
  {{if .Error}}<p class="error">{{.Error}}</p>{{end}}
  {{range .Subjects}}
    <div class="subject">{{.NameHU}} <span class="muted">{{.Code}}</span></div>
    <ul>{{range .Topics}}{{template "topic" .}}{{end}}</ul>
  {{end}}
</nav>
<main>
{{if .Selected}}
  <h1>{{.Selected.Title}}</h1>
  {{if .Selected.Description}}<p class="muted">{{.Selected.Description}}</p>{{end}}
  {{with .Report}}
  <section class="report">
    <h2>Report</h2>
    <div class="muted">{{.Active}} active tasks{{if .Inactive}}, {{.Inactive}} inactive{{end}}</div>
    {{if .Active}}
    <table class="grid">
      <tr><th>Format</th><th class="n">Tasks</th><th class="n">Share</th><th class="n">Target</th><th></th></tr>
      {{range .Mix}}<tr {{if .Off}}class="off-mix"{{end}}><td>{{.Format}}</td><td class="n">{{.Count}}</td><td class="n">{{.Percent}}%</td><td class="n">{{.Target}}%</td>
        <td><span class="bar" style="width:{{.Percent}}px"></span></td></tr>{{end}}
    </table>
    {{end}}
    {{if .Checks}}<ul>{{range .Checks}}<li class="error">{{.}}</li>{{end}}</ul>
    {{else}}<div class="ok-text">✓ nothing to look at</div>{{end}}
    {{range .Notes}}<div class="muted">ℹ {{.}}</div>{{end}}
  </section>
  {{end}}
  {{if .Units}}
  <section class="report">
    <h2>Tests</h2>
    <table class="grid">
      <tr><th>File</th><th>Kind</th><th>Title</th><th class="n">Tasks</th><th class="n">Required</th><th class="n">Round</th></tr>
      {{range .Units}}<tr><td>{{.File}}</td><td>{{.Kind}}</td><td>{{.Title}}</td><td class="n">{{.Tasks}}</td><td class="n">{{.Core}}</td><td class="n">{{.RoundSize}}</td></tr>{{end}}
    </table>
  </section>
  {{end}}
  {{if not .Lessons}}<p class="muted">This topic has no lesson file (*.md).</p>{{end}}
  {{range .Lessons}}
    <section class="lesson">
      <h2>{{.Title}}</h2>
      <div class="muted">{{.File}} · {{.Minutes}} min · {{.Source}} · {{len .Pages}} pages</div>
      {{range .Problems}}<p class="error">⚠ {{.}}</p>{{end}}
      {{range .Pages}}
        <article class="page">
          <span class="muted words {{if gt .Words 90}}long{{end}}">{{.Words}} words</span>
          {{if .Title}}<h3>{{.Title}}</h3>{{end}}
          {{range .Blocks}}
            {{if .Kind}}<div class="box {{.Kind}}"><b class="label">{{.Label}}</b>{{.Text}}</div>
            {{else if .List}}{{.Text}}
            {{else}}<p>{{.Text}}</p>{{end}}
          {{end}}
        </article>
      {{end}}
    </section>
  {{end}}
  {{if .Tasks}}
    <section class="tasks">
      <h2>Tasks ({{len .Tasks}})</h2>
      {{range .Tasks}}
        <article class="task {{if not .Active}}off{{end}}">
          <span class="fmt">{{.Format}}</span>
          <span class="muted">#{{.ID}} · {{.Source}}{{if not .Active}} · INACTIVE{{end}}{{range .Tags}} · #{{.}}{{end}}</span>
          <p class="q">{{.Question}}</p>
          {{if .Options}}<ul class="opts">{{range .Options}}
            <li {{if .Correct}}class="ok"{{end}}>{{if .Position}}<span class="pos">{{.Position}}.</span>{{else}}{{.Label}}) {{end}}{{.Text}}{{if .Correct}} ✓{{end}}
              {{if .Why}}<div class="why">✗ {{.Why}}</div>{{end}}</li>
          {{end}}</ul>{{end}}
          {{if .Pairs}}<table class="pairs">{{range .Pairs}}<tr><td>{{.Prompt}}</td><td>→</td><td><b>{{.Answer}}</b></td></tr>{{end}}</table>{{end}}
          {{if .Others}}<div class="muted">Extra answers: {{range .Others}}{{.Text}}; {{end}}</div>{{end}}
          {{if .Accepted}}<div class="acc">Accepted: {{range .Accepted}}<b>{{.Slot}}</b> = {{range $i, $t := .Texts}}{{if $i}} / {{end}}{{$t}}{{end}}; {{end}}</div>{{end}}
          <div class="expl">💡 {{.Explanation}}</div>
        </article>
      {{end}}
    </section>
  {{end}}
{{else}}
  <h1>Overview</h1>
  <p class="muted">What each topic holds (its own files). "Check" counts the things its report flags. Click a topic to open it.</p>
  {{range .Overview}}
    <h2>{{.Name}} <span class="muted">{{.Code}}</span></h2>
    <table class="grid">
      <tr><th>Topic</th><th class="n">Lessons</th><th class="n">Pages</th><th class="n">Tasks</th><th class="n">Tests</th><th class="n">Check</th></tr>
      {{range .Rows}}<tr><td style="padding-left:{{.Indent}}px"><a class="plain {{if not .Published}}off{{end}}" href="/?id={{.ID}}">{{.Title}}</a></td>
        <td class="n">{{.Lessons}}</td><td class="n">{{.Pages}}</td><td class="n">{{.Tasks}}</td><td class="n">{{.Tests}}</td>
        <td class="n {{if .Checks}}error{{end}}">{{if .Checks}}{{.Checks}}{{end}}</td></tr>{{end}}
      <tr class="total"><td>Total</td><td class="n">{{.Totals.Lessons}}</td><td class="n">{{.Totals.Pages}}</td><td class="n">{{.Totals.Tasks}}</td><td class="n">{{.Totals.Tests}}</td>
        <td class="n {{if .Totals.Checks}}error{{end}}">{{if .Totals.Checks}}{{.Totals.Checks}}{{end}}</td></tr>
    </table>
  {{end}}
{{end}}
</main>
</body></html>
{{define "topic"}}
<li><a href="/?id={{.ID}}"><span {{if not .IsPublished}}class="off"{{end}}>{{.Title}}</span></a>
  {{if .Children}}<ul>{{range .Children}}{{template "topic" .}}{{end}}</ul>{{end}}
</li>
{{end}}`))

// pageData is what the template shows.
type pageData struct {
	Dir      string
	Subjects []*Subject
	Selected *Topic
	Lessons  []*Lesson
	Tasks    []*TaskView
	Units    []*UnitView
	Report   *Report
	Overview []*SubjectOverview
	Error    string
}

func main() {
	// os.Args holds the command line: os.Args[0] is the program, os.Args[1] the first argument.
	if len(os.Args) < 2 {
		fmt.Println("usage: go run . <content folder>")
		os.Exit(1)
	}
	contentDir := os.Args[1]

	// Check that the folder exists. In Go a function that can fail returns an error as its last value.
	if info, err := os.Stat(contentDir); err != nil || !info.IsDir() {
		log.Fatalf("not a folder: %s", contentDir)
	}

	// A handler is a function that answers one web request. "/" matches every address.
	// The files are read again on every request, so the page always shows what is in the folder now.
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		data := pageData{Dir: contentDir}
		subjects, err := LoadSubjects(contentDir)
		if err != nil {
			data.Error = err.Error()
		}
		data.Subjects = subjects

		// "?id=123" in the address chooses a topic.
		if id, err := strconv.Atoi(r.URL.Query().Get("id")); err == nil {
			data.Selected = findTopic(subjects, id)
			if data.Selected != nil {
				if data.Lessons, err = LoadLessons(data.Selected.Dir); err != nil {
					data.Error = err.Error()
				}
				if data.Tasks, err = LoadTasks(data.Selected.Dir); err != nil {
					data.Error = err.Error()
				}
				units, used, err := LoadUnits(data.Selected.Dir, data.Tasks)
				if err != nil {
					data.Error = err.Error()
				}
				data.Units = units
				data.Report = BuildReport(data.Tasks, data.Lessons, units, used)
			}
		}
		if data.Selected == nil {
			data.Overview = BuildOverview(subjects)
		}

		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		if err := page.Execute(w, data); err != nil {
			log.Println("template error:", err)
		}
	})

	url := "http://" + address
	fmt.Println("viewer running at", url, "(Ctrl+C to stop)")
	openBrowser(url)

	// ListenAndServe blocks (waits for requests) until the program is stopped.
	log.Fatal(http.ListenAndServe(address, nil))
}

// findTopic looks a topic up by its id in all subjects; nil when there is none.
func findTopic(subjects []*Subject, id int) *Topic {
	for _, s := range subjects {
		if t := findIn(s.Topics, id); t != nil {
			return t
		}
	}
	return nil
}

func findIn(topics []*Topic, id int) *Topic {
	for _, t := range topics {
		if t.ID == id {
			return t
		}
		if found := findIn(t.Children, id); found != nil {
			return found
		}
	}
	return nil
}

// openBrowser opens the address in the default browser of the computer.
func openBrowser(url string) {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	case "darwin":
		cmd = exec.Command("open", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	_ = cmd.Start()
}
