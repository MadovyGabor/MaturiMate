package main

import (
	"fmt"
	"strings"
)

// targetMix is the recommended mix of the formats in a topic (content guide, "Tasks and tests").
var targetMix = []struct {
	Format string
	Target int // percent
}{
	{"MC_SINGLE", 30}, {"TRUE_FALSE", 15}, {"MATCHING", 12}, {"ORDERING", 8},
	{"GAP_CHOICE", 10}, {"GAP_FILL", 15}, {"SHORT_ANSWER", 10},
}

// A format is "off" when it is further than this many percentage points from the target.
const mixTolerance = 12

// MixRow is one line of the format table.
type MixRow struct {
	Format  string
	Count   int
	Percent int
	Target  int
	Off     bool
}

// Report is the statistics and the checks of one topic.
type Report struct {
	Active   int // active tasks
	Inactive int
	Mix      []MixRow
	Checks   []string // everything that needs a look; empty means no problem found
	Notes    []string // information only, not a problem
}

// BuildReport counts the formats and collects the things worth a second look. The Kotlin validator
// (the pipeline's export) is the real check; this is a quick view while writing.
func BuildReport(tasks []*TaskView, lessons []*Lesson, units []*UnitView, used map[int]bool) *Report {
	r := &Report{}
	counts := map[string]int{}
	var noExplanation, duplicates []string
	seen := map[string]int{}
	wrongTotal, wrongNoWhy := 0, 0

	for _, t := range tasks {
		if !t.Active {
			r.Inactive++
			continue
		}
		r.Active++
		counts[t.Format]++
		if !t.HasExplanation {
			noExplanation = append(noExplanation, fmt.Sprint(t.ID))
		}
		if first, dup := seen[t.QuestionRaw]; dup {
			duplicates = append(duplicates, fmt.Sprintf("%d = %d", t.ID, first))
		} else {
			seen[t.QuestionRaw] = t.ID
		}
		wrongTotal += t.WrongOptions
		wrongNoWhy += t.WrongWithoutWhy
	}

	for _, m := range targetMix {
		row := MixRow{Format: m.Format, Count: counts[m.Format], Target: m.Target}
		if r.Active > 0 {
			row.Percent = (row.Count*100 + r.Active/2) / r.Active
		}
		row.Off = r.Active >= 10 && abs(row.Percent-m.Target) > mixTolerance
		r.Mix = append(r.Mix, row)
		delete(counts, m.Format)
	}
	for format, n := range counts { // a format the viewer does not know
		r.Checks = append(r.Checks, fmt.Sprintf("unknown format %q in %d task(s)", format, n))
	}

	if len(noExplanation) > 0 {
		r.Checks = append(r.Checks, "no explanation: tasks "+strings.Join(noExplanation, ", "))
	}
	if wrongTotal > 0 && wrongNoWhy > 0 {
		r.Notes = append(r.Notes, fmt.Sprintf("%d of %d wrong options have no \"why\" (optional; write it where two answers are easy to mix up)", wrongNoWhy, wrongTotal))
	}
	if len(duplicates) > 0 {
		r.Checks = append(r.Checks, "the same question text twice: tasks "+strings.Join(duplicates, "; "))
	}

	long := 0
	for _, l := range lessons {
		for _, p := range l.Pages {
			if p.Words > maxPageWords {
				long++
			}
		}
		for _, p := range l.Problems {
			r.Checks = append(r.Checks, l.File+": "+p)
		}
	}
	if long > 0 {
		r.Checks = append(r.Checks, fmt.Sprintf("%d page(s) over %d words", long, maxPageWords))
	}

	for _, u := range units {
		for _, p := range u.Problems {
			r.Checks = append(r.Checks, u.File+": "+p)
		}
	}
	if len(tasks) > 0 && len(units) == 0 {
		r.Checks = append(r.Checks, "the topic has tasks but no test file")
	}
	if len(units) > 0 {
		var unused []string
		for _, t := range tasks {
			if t.Active && !used[t.ID] {
				unused = append(unused, fmt.Sprint(t.ID))
			}
		}
		if len(unused) > 0 {
			r.Checks = append(r.Checks, "in no test: tasks "+strings.Join(unused, ", "))
		}
	}
	return r
}

func abs(n int) int {
	if n < 0 {
		return -n
	}
	return n
}
