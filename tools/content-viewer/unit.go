package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

// rawUnit mirrors a test file (a practice unit) such as 20-practice.json.
type rawUnit struct {
	ID        int    `json:"learning_unit_id"`
	Title     string `json:"title"`
	Kind      string `json:"practice_kind"`
	RoundSize int    `json:"round_size"`
	Tasks     []struct {
		TaskID int   `json:"task_id"`
		IsCore *bool `json:"is_core"` // missing means required (true)
	} `json:"tasks"`
}

// UnitView is one test, ready to show.
type UnitView struct {
	File      string
	Title     string
	Kind      string
	RoundSize int
	Tasks     int
	Core      int
	Problems  []string
}

// LoadUnits reads the test files of a topic folder: every .json file except topic.json and tasks.json
// (files ending in _private.json are never read, like in the pipeline). It also returns the ids of all
// tasks the tests use, so the report can find tasks that are in no test.
func LoadUnits(dir string, tasks []*TaskView) ([]*UnitView, map[int]bool, error) {
	files, err := filepath.Glob(filepath.Join(dir, "*.json"))
	if err != nil {
		return nil, nil, err
	}
	sort.Strings(files)

	own := map[int]bool{} // the tasks of this folder
	for _, t := range tasks {
		own[t.ID] = true
	}
	used := map[int]bool{}
	var units []*UnitView

	for _, f := range files {
		name := filepath.Base(f)
		if name == "topic.json" || name == "tasks.json" || strings.HasSuffix(name, "_private.json") {
			continue
		}
		data, err := os.ReadFile(f)
		if err != nil {
			return nil, nil, err
		}
		var r rawUnit
		if err := json.Unmarshal(bytes.TrimPrefix(data, []byte("\xef\xbb\xbf")), &r); err != nil {
			return nil, nil, fmt.Errorf("%s: %w", f, err)
		}
		u := &UnitView{File: name, Title: r.Title, Kind: r.Kind, RoundSize: r.RoundSize, Tasks: len(r.Tasks)}
		for _, t := range r.Tasks {
			used[t.TaskID] = true
			if t.IsCore == nil || *t.IsCore {
				u.Core++
			}
			// The tasks of a FINAL test lie in the topics below, so only the other kinds are checked here.
			if r.Kind != "FINAL" && !own[t.TaskID] {
				u.Problems = append(u.Problems, fmt.Sprintf("task %d is not in this topic's tasks.json", t.TaskID))
			}
		}
		switch {
		case u.Tasks == 0:
			u.Problems = append(u.Problems, "the test has no tasks")
		case u.RoundSize > u.Tasks:
			u.Problems = append(u.Problems, fmt.Sprintf("round_size %d is more than the %d tasks", u.RoundSize, u.Tasks))
		case u.RoundSize < u.Core:
			u.Problems = append(u.Problems, fmt.Sprintf("round_size %d is less than the %d required tasks", u.RoundSize, u.Core))
		}
		units = append(units, u)
	}
	return units, used, nil
}
