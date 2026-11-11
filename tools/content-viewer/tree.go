package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
)

// A struct is a record with named fields. The `json:"..."` tags say which JSON key fills which field.

// Subject is the content of a subject.json file, plus the topics found under its folder.
type Subject struct {
	ID     int    `json:"subject_id"`
	Code   string `json:"code"`
	NameHU string `json:"name_hu"`
	NameSK string `json:"name_sk"`
	Topics []*Topic
}

// Topic is the content of a topic.json file. Its parent comes from the folder, so it is not in the file.
type Topic struct {
	ID          int    `json:"topic_id"`
	Title       string `json:"title"`
	Description string `json:"description"`
	OrderIndex  int    `json:"order_index"`
	IsPublished bool   `json:"is_published"`
	Dir         string // the folder of the topic, filled by the loader
	Children    []*Topic
}

// LoadSubjects reads every subject folder (one that holds a subject.json) of the content folder.
func LoadSubjects(root string) ([]*Subject, error) {
	entries, err := os.ReadDir(root)
	if err != nil {
		return nil, err
	}
	var subjects []*Subject
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		dir := filepath.Join(root, e.Name())
		path := filepath.Join(dir, "subject.json")
		if _, err := os.Stat(path); err != nil {
			continue // a folder without subject.json is not a subject (for example .git)
		}
		s := &Subject{}
		if err := readJSON(path, s); err != nil {
			return nil, err
		}
		if s.Topics, err = loadTopics(dir); err != nil {
			return nil, err
		}
		subjects = append(subjects, s)
	}
	sort.Slice(subjects, func(i, j int) bool { return subjects[i].ID < subjects[j].ID })
	return subjects, nil
}

// loadTopics reads the sub folders of dir that hold a topic.json, and their sub folders, recursively.
func loadTopics(dir string) ([]*Topic, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}
	var topics []*Topic
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		sub := filepath.Join(dir, e.Name())
		path := filepath.Join(sub, "topic.json")
		if _, err := os.Stat(path); err != nil {
			continue
		}
		t := &Topic{Dir: sub}
		if err := readJSON(path, t); err != nil {
			return nil, err
		}
		if t.Children, err = loadTopics(sub); err != nil {
			return nil, err
		}
		topics = append(topics, t)
	}
	sort.Slice(topics, func(i, j int) bool { return topics[i].OrderIndex < topics[j].OrderIndex })
	return topics, nil
}

// readJSON reads a JSON file into target (a pointer to a struct). A UTF-8 byte order mark is ignored.
func readJSON(path string, target any) error {
	data, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	data = bytes.TrimPrefix(data, []byte("\xef\xbb\xbf"))
	if err := json.Unmarshal(data, target); err != nil {
		return fmt.Errorf("%s: %w", path, err) // %w wraps the error, so the file name stays in the message
	}
	return nil
}
