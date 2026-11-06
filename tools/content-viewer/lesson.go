package main

import (
	"bytes"
	"fmt"
	"html"
	"html/template"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

// Lesson is one Markdown file of a topic: the front matter, then pages made of paragraphs and boxes.
type Lesson struct {
	File     string
	Title    string
	Minutes  int
	Source   string
	Pages    []*Page
	Problems []string // things the viewer noticed (an unknown box, a box not closed); the validator is the real check
}

// Page starts with a "## Title" line. The text before the first title is a page without a title.
type Page struct {
	Title  string
	Blocks []Block
	Words  int
}

// Block is a paragraph (Kind is empty) or a box (Kind is example, tip, note or warning).
type Block struct {
	Kind  string
	Label string
	Text  template.HTML // ready to show: escaped, with **bold** and *italic* turned into tags
}

// A short page fits one phone screen: about 60 to 90 words (content guide).
const maxPageWords = 90

var boxLabels = map[string]string{
	"example": "Példa",
	"tip":     "Jegyezd meg",
	"note":    "Megjegyzés",
	"warning": "Vigyázat",
}

var (
	boldPattern   = regexp.MustCompile(`\*\*(.+?)\*\*`)
	italicPattern = regexp.MustCompile(`\*(.+?)\*`)
)

// LoadLessons reads every .md file of a topic folder, in the order of the file names (10-..., 30-...).
func LoadLessons(dir string) ([]*Lesson, error) {
	files, err := filepath.Glob(filepath.Join(dir, "*.md"))
	if err != nil {
		return nil, err
	}
	sort.Strings(files)
	var lessons []*Lesson
	for _, f := range files {
		data, err := os.ReadFile(f)
		if err != nil {
			return nil, err
		}
		lessons = append(lessons, ParseLesson(filepath.Base(f), string(data)))
	}
	return lessons, nil
}

// ParseLesson reads the text of a lesson file. It never fails: problems are listed in the lesson.
func ParseLesson(file, text string) *Lesson {
	text = string(bytes.TrimPrefix([]byte(text), []byte("\xef\xbb\xbf")))
	text = strings.ReplaceAll(text, "\r\n", "\n") // Windows line ends

	l := &Lesson{File: file}
	body := text
	if strings.HasPrefix(text, "---\n") {
		if end := strings.Index(text[4:], "\n---"); end >= 0 {
			front := text[4 : 4+end]
			body = text[4+end+4:]
			for _, line := range strings.Split(front, "\n") {
				key, value, found := strings.Cut(line, ":")
				if !found {
					continue
				}
				value = strings.Trim(strings.TrimSpace(value), `"`)
				switch strings.TrimSpace(key) {
				case "title":
					l.Title = value
				case "estimated_minutes":
					l.Minutes, _ = strconv.Atoi(value)
				case "source_type":
					l.Source = value
				}
			}
		}
	}
	parseBody(l, body)
	return l
}

func parseBody(l *Lesson, body string) {
	var page *Page
	var paragraph []string
	box := "" // the kind of the box we are in, empty outside a box

	newPage := func(title string) {
		page = &Page{Title: title}
		l.Pages = append(l.Pages, page)
	}
	flush := func() {
		if len(paragraph) == 0 {
			return
		}
		if page == nil {
			newPage("")
		}
		raw := strings.Join(paragraph, " ")
		page.Words += len(strings.Fields(raw))
		b := Block{Kind: box, Text: inline(raw)}
		b.Label = boxLabels[box]
		page.Blocks = append(page.Blocks, b)
		paragraph = nil
	}

	for _, line := range strings.Split(body, "\n") {
		line = strings.TrimSpace(line)
		switch {
		case strings.HasPrefix(line, "## "):
			flush()
			if box != "" {
				l.Problems = append(l.Problems, fmt.Sprintf("a page starts inside the %q box", box))
				box = ""
			}
			newPage(strings.TrimSpace(line[3:]))
		case line == ":::":
			flush()
			if box == "" {
				l.Problems = append(l.Problems, "a closing ::: without an open box")
			}
			box = ""
		case strings.HasPrefix(line, ":::"):
			flush()
			kind := strings.TrimSpace(line[3:])
			if _, known := boxLabels[kind]; !known {
				l.Problems = append(l.Problems, fmt.Sprintf("unknown box type %q", kind))
			}
			if box != "" {
				l.Problems = append(l.Problems, "a box inside a box")
			}
			box = kind
		case line == "":
			flush()
		default:
			paragraph = append(paragraph, line)
		}
	}
	flush()
	if box != "" {
		l.Problems = append(l.Problems, fmt.Sprintf("the %q box is not closed", box))
	}
}

// inline escapes the text (so < and & cannot break the page) and then turns **bold** and *italic* into tags.
func inline(text string) template.HTML {
	text = html.EscapeString(text)
	text = boldPattern.ReplaceAllString(text, "<strong>$1</strong>")
	text = italicPattern.ReplaceAllString(text, "<em>$1</em>")
	return template.HTML(text) // safe: everything the author wrote was escaped above
}
