package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"html"
	"html/template"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
)

// The raw* types mirror tasks.json. The viewer then turns them into TaskView, which holds what is shown.

type rawOption struct {
	Label string `json:"label"`
	Text  string `json:"text"`
	Role  string `json:"role"` // ANSWER (the default) or PROMPT (MATCHING only)
	Why   string `json:"why"`
}

type rawKey struct {
	Slot   int    `json:"slot"`
	Option string `json:"option"`
	Prompt string `json:"prompt"`
}

type rawAccepted struct {
	Slot int    `json:"slot"`
	Text string `json:"text"`
}

type rawTask struct {
	ID              int           `json:"task_id"`
	Format          string        `json:"format"`
	QuestionText    string        `json:"question_text"`
	Explanation     string        `json:"explanation"`
	Options         []rawOption   `json:"options"`
	Key             []rawKey      `json:"key"`
	AcceptedAnswers []rawAccepted `json:"accepted_answers"`
	Tags            []string      `json:"tags"`
	SourceType      string        `json:"source_type"`
	IsActive        *bool         `json:"is_active"` // a pointer, so "missing" (= active) differs from false
}

// TaskView is one task, ready to show.
type TaskView struct {
	ID          int
	Format      string
	Active      bool
	Source      string
	Tags        []string
	Question    template.HTML // with the right words in the gaps
	Options     []OptionView  // MC_SINGLE, TRUE_FALSE, GAP_CHOICE word list, ORDERING items
	Pairs       []PairView    // MATCHING
	Others      []OptionView  // MATCHING: answers that belong to no prompt
	Accepted    []AcceptedView
	Explanation template.HTML

	// Facts for the report (stats.go).
	HasExplanation  bool
	QuestionRaw     string // the question text as written, to find duplicates
	WrongOptions    int    // wrong options of the choice formats where a "why" makes sense
	WrongWithoutWhy int
}

type OptionView struct {
	Label    string
	Text     template.HTML
	Correct  bool
	Position int // ORDERING: the place of the item in the right order
	Why      template.HTML
}

type PairView struct {
	Prompt template.HTML
	Answer template.HTML
}

type AcceptedView struct {
	Slot  int
	Texts []string
}

var gapPattern = regexp.MustCompile(`\[\[(\d+)\]\]`)

// LoadTasks reads tasks.json of a topic folder; a topic without the file has no tasks.
func LoadTasks(dir string) ([]*TaskView, error) {
	path := filepath.Join(dir, "tasks.json")
	data, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	data = bytes.TrimPrefix(data, []byte("\xef\xbb\xbf"))
	var raws []rawTask
	if err := json.Unmarshal(data, &raws); err != nil {
		return nil, fmt.Errorf("%s: %w", path, err)
	}
	views := make([]*TaskView, 0, len(raws))
	for _, r := range raws {
		views = append(views, buildTask(r))
	}
	return views, nil
}

func buildTask(t rawTask) *TaskView {
	v := &TaskView{
		ID: t.ID, Format: t.Format, Tags: t.Tags, Source: t.SourceType,
		Active:         t.IsActive == nil || *t.IsActive,
		Explanation:    inline(t.Explanation),
		HasExplanation: strings.TrimSpace(t.Explanation) != "",
		QuestionRaw:    strings.TrimSpace(t.QuestionText),
	}

	// A missing "slot" means slot 1 (the pipeline does the same).
	keyBySlot := map[int]rawKey{}
	correctLabels := map[string]bool{}
	for _, k := range t.Key {
		if k.Slot == 0 {
			k.Slot = 1
		}
		keyBySlot[k.Slot] = k
		correctLabels[k.Option] = true
	}
	textOf := map[string]string{} // option label -> text
	for _, o := range t.Options {
		textOf[o.Label] = o.Text
	}

	// Accepted answers per slot, in slot order.
	bySlot := map[int][]string{}
	for _, a := range t.AcceptedAnswers {
		if a.Slot == 0 {
			a.Slot = 1
		}
		bySlot[a.Slot] = append(bySlot[a.Slot], a.Text)
	}
	slots := make([]int, 0, len(bySlot))
	for s := range bySlot {
		slots = append(slots, s)
	}
	sort.Ints(slots)
	for _, s := range slots {
		v.Accepted = append(v.Accepted, AcceptedView{Slot: s, Texts: bySlot[s]})
	}

	// What goes into the gaps of the question text.
	var fill func(slot int) string
	switch t.Format {
	case "GAP_CHOICE":
		fill = func(slot int) string { return textOf[keyBySlot[slot].Option] }
	case "GAP_FILL":
		fill = func(slot int) string {
			if len(bySlot[slot]) > 0 {
				return bySlot[slot][0]
			}
			return "?"
		}
	}
	v.Question = renderQuestion(t.QuestionText, fill)

	if t.Format == "MATCHING" {
		for _, o := range t.Options {
			if o.Role != "PROMPT" && !correctLabels[o.Label] {
				v.Others = append(v.Others, OptionView{Label: o.Label, Text: inline(o.Text), Why: inline(o.Why)})
			}
		}
		promptText := map[string]string{}
		for _, o := range t.Options {
			if o.Role == "PROMPT" {
				promptText[o.Label] = o.Text
			}
		}
		keys := make([]rawKey, 0, len(keyBySlot))
		for _, k := range keyBySlot {
			keys = append(keys, k)
		}
		sort.Slice(keys, func(i, j int) bool { return keys[i].Slot < keys[j].Slot })
		for _, k := range keys {
			v.Pairs = append(v.Pairs, PairView{Prompt: inline(promptText[k.Prompt]), Answer: inline(textOf[k.Option])})
		}
		return v
	}

	if t.Format == "MC_SINGLE" || t.Format == "TRUE_FALSE" || t.Format == "GAP_CHOICE" {
		for _, o := range t.Options {
			if !correctLabels[o.Label] {
				v.WrongOptions++
				if strings.TrimSpace(o.Why) == "" {
					v.WrongWithoutWhy++
				}
			}
		}
	}

	positions := map[string]int{} // ORDERING: option label -> place in the right order
	for _, k := range keyBySlot {
		positions[k.Option] = k.Slot
	}
	for _, o := range t.Options {
		ov := OptionView{Label: o.Label, Text: inline(o.Text), Why: inline(o.Why)}
		if t.Format == "ORDERING" {
			ov.Position = positions[o.Label]
		} else {
			ov.Correct = correctLabels[o.Label]
		}
		v.Options = append(v.Options, ov)
	}
	return v
}

// renderQuestion shows the question text; every [[n]] gap becomes a highlighted gap with the right answer
// (fill is nil for formats without gaps).
func renderQuestion(text string, fill func(slot int) string) template.HTML {
	var out bytes.Buffer
	last := 0
	for _, m := range gapPattern.FindAllStringSubmatchIndex(text, -1) {
		out.WriteString(string(inline(text[last:m[0]])))
		var slot int
		fmt.Sscanf(text[m[2]:m[3]], "%d", &slot)
		answer := fmt.Sprintf("[[%d]]", slot)
		if fill != nil {
			answer = fill(slot)
		}
		out.WriteString(`<span class="gap">` + html.EscapeString(answer) + `</span>`)
		last = m[1]
	}
	out.WriteString(string(inline(text[last:])))
	return template.HTML(out.String()) // safe: every piece was escaped above
}
