"""Capture one source-commit quality baseline from JaCoCo and PIT XML reports."""

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ElementTree


REQUIRED_MUTATION_STATUSES = ("KILLED", "NO_COVERAGE", "SURVIVED")


def counter(root, counter_type):
    matches = [
        element
        for element in root.findall("counter")
        if element.attrib.get("type") == counter_type
    ]
    if len(matches) != 1:
        raise ValueError(f"expected exactly one {counter_type} counter")

    covered = int(matches[0].attrib["covered"])
    missed = int(matches[0].attrib["missed"])
    total = covered + missed
    if total == 0:
        raise ValueError(f"{counter_type} counter total must be non-zero")
    return {"covered": covered, "missed": missed, "total": total}


def parse_coverage(path):
    root = ElementTree.parse(path).getroot()
    if root.tag != "report":
        raise ValueError(f"coverage report is not a JaCoCo report: {path}")
    sessions = [
        {
            "dump": session.attrib.get("dump", ""),
            "id": session.attrib.get("id", ""),
            "start": session.attrib.get("start", ""),
        }
        for session in root.findall("sessioninfo")
    ]
    return {
        "branch": counter(root, "BRANCH"),
        "line": counter(root, "LINE"),
    }, sessions


def parse_mutations(path):
    root = ElementTree.parse(path).getroot()
    if root.tag != "mutations":
        raise ValueError(f"PIT report is not a mutations report: {path}")

    statuses = {status: 0 for status in REQUIRED_MUTATION_STATUSES}
    mutations = root.findall("mutation")
    if not mutations:
        raise ValueError(f"PIT report has no mutations: {path}")

    for mutation in mutations:
        status = mutation.attrib.get("status")
        if not status:
            raise ValueError(f"PIT mutation has no status: {path}")
        statuses[status] = statuses.get(status, 0) + 1

    statuses["total"] = len(mutations)
    return statuses


def parse_input_commits(values, expected_paths, source_commit):
    if not values:
        return

    commits_by_path = {}
    for value in values:
        if "=" not in value:
            raise ValueError("input commit must use PATH=COMMIT")
        path_text, commit = value.split("=", 1)
        path = str(Path(path_text))
        if not commit:
            raise ValueError("input commit may not be empty")
        if path in commits_by_path:
            raise ValueError(f"input commit repeated for {path}")
        commits_by_path[path] = commit

    expected = {str(path) for path in expected_paths}
    if set(commits_by_path) != expected:
        raise ValueError("input commits must identify every report exactly once")
    if any(commit != source_commit for commit in commits_by_path.values()):
        raise ValueError("reports were captured from mixed source commits")


def report_module(path):
    parts = Path(path).parts
    for index, part in enumerate(parts[:-1]):
        if part == "domain" and index + 1 < len(parts):
            return f":domain:{parts[index + 1]}"
    return Path(path).stem


def capture(args):
    coverage_path = Path(args.coverage)
    pitest_paths = [Path(path) for path in args.pitest]
    input_paths = [coverage_path, *pitest_paths]
    for path in input_paths:
        if not path.is_file():
            raise ValueError(f"report does not exist: {path}")

    if len(pitest_paths) != 3:
        raise ValueError("exactly three PIT reports are required")
    parse_input_commits(args.input_commit, input_paths, args.commit)

    coverage, sessions = parse_coverage(coverage_path)
    mutation_reports = []
    mutation_statuses = {status: 0 for status in REQUIRED_MUTATION_STATUSES}
    for path in pitest_paths:
        statuses = parse_mutations(path)
        mutation_reports.append(
            {
                "module": report_module(path),
                "path": str(path),
                "status": statuses,
            }
        )
        for status, count in statuses.items():
            if status != "total":
                mutation_statuses[status] = mutation_statuses.get(status, 0) + count
    mutation_statuses["total"] = sum(status for name, status in mutation_statuses.items() if name != "total")

    return {
        "coverage": coverage,
        "environment": {
            "jacocoSessions": sessions,
        },
        "inputs": {
            "coverage": str(coverage_path),
            "pitest": [str(path) for path in pitest_paths],
        },
        "mutation": {
            "byReport": mutation_reports,
            "status": mutation_statuses,
        },
        "sourceCommit": args.commit,
    }


def parser():
    argument_parser = argparse.ArgumentParser()
    argument_parser.add_argument("--commit", required=True)
    argument_parser.add_argument("--coverage", required=True)
    argument_parser.add_argument("--pitest", action="append", required=True)
    argument_parser.add_argument("--output", required=True)
    argument_parser.add_argument(
        "--input-commit",
        action="append",
        default=[],
        help="optional PATH=COMMIT report provenance assertions",
    )
    return argument_parser


def main(argv=None):
    argument_parser = parser()
    args = argument_parser.parse_args(argv)
    try:
        baseline = capture(args)
    except (ElementTree.ParseError, OSError, ValueError) as error:
        argument_parser.error(str(error))

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(baseline, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    main()
