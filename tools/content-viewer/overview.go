package main

// OverviewRow is one topic in the overview table (its own files only, not the topics below it).
type OverviewRow struct {
	ID        int
	Title     string
	Indent    int // pixels, from the depth of the topic
	Published bool
	Lessons   int
	Pages     int
	Tasks     int // active tasks
	Tests     int
	Checks    int // how many things in the topic's report need a look
}

// SubjectOverview is the table of one subject with its totals.
type SubjectOverview struct {
	Name   string
	Code   string
	Rows   []OverviewRow
	Totals OverviewRow
}

// BuildOverview reads every topic once and counts what it holds. It is the "where are we" page.
func BuildOverview(subjects []*Subject) []*SubjectOverview {
	var result []*SubjectOverview
	for _, s := range subjects {
		o := &SubjectOverview{Name: s.NameHU, Code: s.Code}
		o.addTopics(s.Topics, 0)
		result = append(result, o)
	}
	return result
}

func (o *SubjectOverview) addTopics(topics []*Topic, depth int) {
	for _, t := range topics {
		row := OverviewRow{ID: t.ID, Title: t.Title, Indent: depth * 18, Published: t.IsPublished}

		lessons, err1 := LoadLessons(t.Dir)
		tasks, err2 := LoadTasks(t.Dir)
		units, used, err3 := LoadUnits(t.Dir, tasks)
		for _, err := range []error{err1, err2, err3} {
			if err != nil {
				row.Checks++ // a file that cannot be read counts as one thing to look at
			}
		}
		for _, l := range lessons {
			row.Pages += len(l.Pages)
		}
		row.Lessons = len(lessons)
		row.Tests = len(units)
		report := BuildReport(tasks, lessons, units, used)
		row.Tasks = report.Active
		row.Checks += len(report.Checks)

		o.Rows = append(o.Rows, row)
		o.Totals.Lessons += row.Lessons
		o.Totals.Pages += row.Pages
		o.Totals.Tasks += row.Tasks
		o.Totals.Tests += row.Tests
		o.Totals.Checks += row.Checks

		o.addTopics(t.Children, depth+1)
	}
}
