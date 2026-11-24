#!/usr/bin/env python3
"""Markdown tables from JUnit XML for the GitHub run summary (Python 3 standard library only).

  junit_summary.py group <title> <xml_dir>          one group: counts, failed tests first
  junit_summary.py total <results_root> [--whole <whole_dir>]
                                                    all groups in one table; exit 1 when a needed job
                                                    (JSON of the `needs` context in $NEEDS_JSON) did not
                                                    succeed, a test failed, or the groups do not add up
                                                    to the whole suite

<results_root> holds one folder per downloaded artifact: junit-<group>/*.xml.
The table goes to $GITHUB_STEP_SUMMARY (stdout when it is not set).
"""
from collections import Counter
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET


def read(xml_dir):
    """Counts and the failed test names of every *.xml file in a folder."""
    r = {"tests": 0, "failed": 0, "skipped": 0, "time": 0.0, "failures": [], "files": 0, "bad": [], "names": []}
    for path in sorted(glob.glob(os.path.join(xml_dir, "*.xml"))):
        r["files"] += 1
        try:
            cases = list(ET.parse(path).getroot().iter("testcase"))
        except (ET.ParseError, OSError):
            r["bad"].append(os.path.basename(path))
            continue
        for case in cases:
            r["tests"] += 1
            r["time"] += float(case.get("time") or 0)
            name = f'{case.get("classname")}.{case.get("name")}'
            r["names"].append(name)
            if case.find("failure") is not None or case.find("error") is not None:
                r["failed"] += 1
                r["failures"].append(name)
            elif case.find("skipped") is not None:
                r["skipped"] += 1
    return r


def emit(text):
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a", encoding="utf-8") as out:
            out.write(text + "\n")
    else:
        print(text)


def md(text):
    """A test name safe inside a Markdown table cell or code span."""
    return text.replace("|", "\\|").replace("`", "'").replace("\n", " ")


def seconds(value):
    return f"{value:.0f} s"


def group(title, xml_dir):
    r = read(xml_dir)
    passed = r["tests"] - r["failed"] - r["skipped"]
    lines = [f"### App: {title}", ""]
    if r["files"] == 0:
        lines.append(":red_circle: No test results found.")
    else:
        if r["failures"]:
            lines += ["**Failed tests:**", ""] + [f"- `{md(n)}`" for n in r["failures"]] + [""]
        if r["bad"]:
            lines += [":red_circle: Unreadable result file(s): " + ", ".join(f"`{md(b)}`" for b in r["bad"]), ""]
        lines += [
            "| Tests | Passed | Failed | Skipped | Time (sum of tests) |",
            "| ---: | ---: | ---: | ---: | ---: |",
            f'| {r["tests"]} | {passed} | {r["failed"]} | {r["skipped"]} | {seconds(r["time"])} |',
        ]
    emit("\n".join(lines) + "\n")


def total(root, whole_dir):
    problems = []
    needs = json.loads(os.environ.get("NEEDS_JSON") or "{}")
    for job, info in needs.items():
        if info.get("result") != "success":
            problems.append(f"Job `{job}` finished as {info.get('result')} (must be success).")

    groups = {}
    for folder in sorted(glob.glob(os.path.join(root, "junit-*"))):
        name = os.path.basename(folder)[len("junit-"):]
        if name != "whole":
            groups[name] = read(folder)

    lines = ["## CI result", "", "| Group | Tests | Failed | Skipped | Time (sum of tests) |", "| :--- | ---: | ---: | ---: | ---: |"]
    for name, r in groups.items():
        mark = ":red_circle: " if r["failed"] or r["files"] == 0 else ""
        lines.append(f'| {mark}{name.capitalize()} | {r["tests"]} | {r["failed"]} | {r["skipped"]} | {seconds(r["time"])} |')
        if r["failures"]:
            lines.append("| &nbsp;&nbsp;failed: " + "<br>".join(f"`{md(n)}`" for n in r["failures"]) + " | | | | |")
            problems.append(f"Group {name}: {r['failed']} failed test(s).")
        if r["files"] == 0:
            problems.append(f"Group {name}: no test results.")
        for b in r["bad"]:
            lines.append(f"| &nbsp;&nbsp;:red_circle: unreadable file: `{md(b)}` | | | | |")
            problems.append(f"Group {name}: result file `{md(b)}` is unreadable.")
    summed = sum(r["tests"] for r in groups.values())
    lines.append(f"| **Sum of groups** | **{summed}** | | | |")

    if whole_dir:
        whole = read(whole_dir)
        lines.append(f'| **Whole suite (Coverage job)** | **{whole["tests"]}** | | | |')
        if whole["files"] == 0:
            problems.append("No results from the Coverage job, so the group total cannot be checked.")
        for b in whole["bad"]:
            problems.append(f"Whole suite: result file `{md(b)}` is unreadable.")
        grouped = Counter(n for r in groups.values() for n in r["names"])
        full = Counter(whole["names"])
        if whole["files"] and (whole["tests"] != summed or grouped != full):
            missing = sorted((full - grouped).elements())
            extra = sorted((grouped - full).elements())
            diff = [f"in no group: `{md(n)}`" for n in missing] + [f"only in groups or twice: `{md(n)}`" for n in extra]
            more = f" (and {len(diff) - 10} more)" if len(diff) > 10 else ""
            problems.append(
                f"The groups ran {summed} tests but the whole suite has {whole['tests']}: a test is in no group "
                "or in two (check the table testGroups in shared/build.gradle.kts and the matrix in ci.yml). "
                + "Differences: " + "; ".join(diff[:10]) + more
            )

    lines.append("")
    if problems:
        lines += [":red_circle: **CI failed**", ""] + [f"- {p}" for p in problems]
    else:
        lines.append(":white_check_mark: All jobs passed.")
    emit("\n".join(lines) + "\n")
    return 1 if problems else 0


def main(argv):
    if len(argv) >= 4 and argv[1] == "group":
        group(argv[2], argv[3])
        return 0
    if len(argv) >= 3 and argv[1] == "total":
        whole = None
        if "--whole" in argv:
            i = argv.index("--whole")
            if i + 1 >= len(argv):
                print("error: --whole needs a folder, for example --whole results/junit-whole", file=sys.stderr)
                return 2
            whole = argv[i + 1]
        return total(argv[2], whole)
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
