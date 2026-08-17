#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import verify_coverage as coverage


class CanonicalAndSchemaTest(unittest.TestCase):
    def test_canonical_json_uses_nfc_utf8_sorted_keys_and_hex_control_escapes(self):
        value = {"z": "line\n", "e\u0301": [True, None, 7], "a": "한글"}
        self.assertEqual(
            coverage.canonical_json_bytes(value),
            b'{"a":"\xed\x95\x9c\xea\xb8\x80","z":"line\\u000a",'
            b'"\xc3\xa9":[true,null,7]}',
        )

    def test_duplicate_json_key_is_rejected_instead_of_last_value_winning(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "duplicate.json")
            path.write_text('{"schemaVersion":1,"schemaVersion":2}')
            with self.assertRaisesRegex(coverage.CoverageError, "duplicate JSON key"):
                coverage.read_json(path)

    def test_boolean_float_string_negative_and_overflow_basis_points_are_rejected(self):
        for value in (True, 80.0, "8000", -1, 10001):
            with self.subTest(value=value):
                with self.assertRaises(coverage.CoverageError):
                    coverage.require_basis_points(value, "threshold")
        self.assertEqual(8000, coverage.require_basis_points(8000, "threshold"))

    def test_measurement_policy_forbids_floors_and_blocking_policy_requires_them(self):
        measurement_unit = fixture_unit()
        coverage.validate_unit_floor_schema(measurement_unit, "measurement", branch_total=2)
        with self.assertRaisesRegex(coverage.CoverageError, "floor keys must be absent"):
            coverage.validate_unit_floor_schema(
                {**measurement_unit, "lineFloorBasisPoints": 8000},
                "measurement",
                branch_total=2,
            )
        with self.assertRaisesRegex(coverage.CoverageError, "line floor"):
            coverage.validate_unit_floor_schema(measurement_unit, "blocking", branch_total=2)


class PackageLexerTest(unittest.TestCase):
    def test_kotlin_lexer_skips_nested_comments_strings_and_file_annotations(self):
        source = b'''\
@file:JvmName("Facade")
/* package decoy.one
   /* package decoy.two */
*/
val text = "package decoy.three"
val raw = """package decoy.four"""
package real.owner
class Subject
'''
        self.assertEqual("real.owner", coverage.parse_package_declaration(source, ".kt"))

    def test_java_lexer_skips_text_blocks_and_requires_semicolon(self):
        source = b'''\
/* package decoy.one; */
class Before { String value = """package decoy.two;"""; }
package real.owner;
'''
        self.assertEqual("real.owner", coverage.parse_package_declaration(source, ".java"))
        with self.assertRaisesRegex(coverage.CoverageError, "semicolon"):
            coverage.parse_package_declaration(b"package real.owner\nclass Subject {}", ".java")

    def test_java_unicode_escape_is_rejected_before_lexing(self):
        with self.assertRaisesRegex(coverage.CoverageError, "Unicode escape"):
            coverage.parse_package_declaration(b"package real\\u002eowner;", ".java")

    def test_missing_multiple_and_invalid_packages_fail_closed(self):
        cases = (
            b"class Subject",
            b"package one\npackage two",
            b"package one..two",
        )
        for source in cases:
            with self.subTest(source=source):
                with self.assertRaises(coverage.CoverageError):
                    coverage.parse_package_declaration(source, ".kt")


class ReportIdentityAndCountersTest(unittest.TestCase):
    def test_line_counter_uses_jacoco_line_semantics_not_instruction_totals(self):
        parsed = coverage.parse_jacoco_xml(
            jacoco_xml(source_rows=[("owner", "Authored.kt", 1, 5, 2, 0, 0)]),
            ":sample|main",
        )
        counters, _ = coverage.authored_counters(parsed, {("owner", "Authored.kt")})
        self.assertEqual({"covered": 1, "missed": 0, "total": 1}, counters["line"])

    def test_authored_counter_selection_excludes_coroutine_room_and_generated_noise(self):
        parsed = coverage.parse_jacoco_xml(
            jacoco_xml(
                source_rows=[
                    ("owner", "Authored.kt", 1, 0, 1, 0, 0),
                    ("kotlinx/coroutines/flow", "Emitters.kt", 1, 1, 0, 0, 0),
                    ("owner", "Database_Impl.kt", 1, 1, 0, 0, 0),
                ],
            ),
            ":sample|main",
        )
        counters, excluded = coverage.authored_counters(
            parsed,
            {("owner", "Authored.kt")},
        )
        self.assertEqual({"covered": 1, "missed": 0, "total": 1}, counters["line"])
        self.assertEqual(
            [("kotlinx/coroutines/flow", "Emitters.kt"), ("owner", "Database_Impl.kt")],
            excluded,
        )

    def test_empty_non_executable_source_remains_visible_with_zero_counters(self):
        parsed = coverage.parse_jacoco_xml(
            b'<report name="sample"><package name="owner"><sourcefile name="Marker.kt"/></package></report>',
            ":sample|main",
        )
        counters, excluded = coverage.authored_counters(parsed, {("owner", "Marker.kt")})
        self.assertEqual(0, counters["line"]["total"])
        self.assertEqual([], excluded)

    def test_new_authored_source_absent_from_xml_fails_instead_of_becoming_zero_coverage(self):
        parsed = coverage.parse_jacoco_xml(
            b'<report name="sample"><package name="owner"/></report>',
            ":sample|main",
        )
        with self.assertRaisesRegex(coverage.CoverageError, "missing authored source"):
            coverage.authored_counters(parsed, {("owner", "New.kt")})

    def test_equal_counters_swapped_between_source_identities_change_semantic_digest(self):
        first = jacoco_xml(
            source_rows=[
                ("owner", "First.kt", 1, 0, 1, 0, 0),
                ("owner", "Second.kt", 2, 1, 0, 0, 0),
            ],
        )
        second = jacoco_xml(
            source_rows=[
                ("owner", "First.kt", 2, 1, 0, 0, 0),
                ("owner", "Second.kt", 1, 0, 1, 0, 0),
            ],
        )
        self.assertNotEqual(
            coverage.parse_jacoco_xml(first, ":sample|main").semantic_sha256,
            coverage.parse_jacoco_xml(second, ":sample|main").semantic_sha256,
        )

    def test_duplicate_source_or_line_identity_is_rejected(self):
        xml = b'''\
<report name="sample"><package name="owner"><sourcefile name="Same.kt">
<line nr="1" mi="0" ci="1" mb="0" cb="0"/>
<line nr="1" mi="1" ci="0" mb="0" cb="0"/>
</sourcefile></package></report>'''
        with self.assertRaisesRegex(coverage.CoverageError, "duplicate XML identity"):
            coverage.parse_jacoco_xml(xml, ":sample|main")


class ExactArithmeticTest(unittest.TestCase):
    def test_changed_line_and_branch_threshold_boundaries_are_exact(self):
        self.assertFalse(coverage.ratio_below_basis_points(8, 10, 8000))
        self.assertTrue(coverage.ratio_below_basis_points(7999, 10000, 8000))
        self.assertFalse(coverage.ratio_below_basis_points(7, 10, 7000))

    def test_half_percentage_point_drop_boundary_is_exact(self):
        self.assertFalse(coverage.baseline_drop_exceeded(895, 1000, 900, 1000, 50))
        self.assertTrue(coverage.baseline_drop_exceeded(894, 1000, 900, 1000, 50))

    def test_no_branch_changed_code_is_na_while_partial_branches_count_probes(self):
        no_branch = coverage.changed_counters({1: (0, 1, 0, 0)}, {1})
        self.assertIsNone(no_branch["branch"])
        partial = coverage.changed_counters({1: (0, 1, 3, 7)}, {1})
        self.assertEqual({"covered": 7, "missed": 3, "total": 10}, partial["branch"])
        instructions = coverage.changed_counters({1: (5, 2, 0, 0)}, {1})
        self.assertEqual({"covered": 1, "missed": 0, "total": 1}, instructions["line"])

    def test_floor_transition_never_decreases_and_never_raises_over_200bp(self):
        coverage.validate_floor_transition(7000, 7000, 200)
        coverage.validate_floor_transition(7000, 7200, 200)
        with self.assertRaisesRegex(coverage.CoverageError, "decrease"):
            coverage.validate_floor_transition(7000, 6999, 200)
        with self.assertRaisesRegex(coverage.CoverageError, "200"):
            coverage.validate_floor_transition(7000, 7201, 200)


class GitSourceAndDiffTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        run_git(self.root, "init", "-q")
        run_git(self.root, "config", "user.name", "Coverage Test")
        run_git(self.root, "config", "user.email", "coverage@example.invalid")
        self.write("module/src/main/kotlin/owner/Subject.kt", "package owner\nclass Subject\n")
        self.write("module/src/test/kotlin/owner/SubjectTest.kt", "package owner\nclass SubjectTest\n")
        run_git(self.root, "add", ".")
        run_git(self.root, "commit", "-qm", "fixture")
        self.commit = run_git(self.root, "rev-parse", "HEAD").strip()

    def tearDown(self):
        self.temporary.cleanup()

    def write(self, path: str, value: str):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(value)

    def records(self, roots: list[str]):
        result = []
        for root in roots:
            for path in sorted((self.root / root).rglob("*")):
                if path.suffix in {".kt", ".java"}:
                    result.append(
                        {
                            "path": path.relative_to(self.root).as_posix(),
                            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                        },
                    )
        return result

    def test_git_tree_equality_accepts_exact_production_and_test_inventories(self):
        production = ["module/src/main/kotlin"]
        tests = ["module/src/test/kotlin"]
        coverage.verify_git_source_set(self.root, self.commit, production, self.records(production), "production")
        coverage.verify_git_source_set(self.root, self.commit, tests, self.records(tests), "test")

    def test_worktree_deleted_committed_production_and_test_sources_both_fail(self):
        for relative, roots, label in (
            ("module/src/main/kotlin/owner/Subject.kt", ["module/src/main/kotlin"], "production"),
            ("module/src/test/kotlin/owner/SubjectTest.kt", ["module/src/test/kotlin"], "test"),
        ):
            with self.subTest(label=label):
                path = self.root / relative
                original = path.read_bytes()
                path.unlink()
                with self.assertRaisesRegex(coverage.CoverageError, "missing"):
                    coverage.verify_git_source_set(self.root, self.commit, roots, self.records(roots), label)
                path.write_bytes(original)

    def test_dirty_and_untracked_production_or_test_sources_fail(self):
        path = self.root / "module/src/test/kotlin/owner/SubjectTest.kt"
        path.write_text("package owner\nclass Dirty\n")
        with self.assertRaisesRegex(coverage.CoverageError, "hash"):
            coverage.verify_git_source_set(
                self.root,
                self.commit,
                ["module/src/test/kotlin"],
                self.records(["module/src/test/kotlin"]),
                "test",
            )
        path.write_text("package owner\nclass SubjectTest\n")
        self.write("module/src/test/kotlin/owner/Untracked.kt", "package owner\nclass Untracked\n")
        with self.assertRaisesRegex(coverage.CoverageError, "extra"):
            coverage.verify_git_source_set(
                self.root,
                self.commit,
                ["module/src/test/kotlin"],
                self.records(["module/src/test/kotlin"]),
                "test",
            )

    def test_diff_parser_handles_omitted_counts_zero_ranges_rename_spaces_unicode_and_crlf(self):
        status = b"M\0module/src/main/kotlin/owner/Space Name.kt\0R100\0old.kt\0new\xe2\x98\x83.kt\0"
        patch = (
            b'diff --git "a/module/src/main/kotlin/owner/Space Name.kt" "b/module/src/main/kotlin/owner/Space Name.kt"\r\n'
            b'--- "a/module/src/main/kotlin/owner/Space Name.kt"\r\n'
            b'+++ "b/module/src/main/kotlin/owner/Space Name.kt"\r\n'
            b'@@ -0,0 +1 @@\r\n+package owner\r\n'
            b'diff --git "a/old.kt" "b/new\\342\\230\\203.kt"\r\n'
            b'--- "a/old.kt"\r\n+++ "b/new\\342\\230\\203.kt"\r\n'
            b'@@ -4 +4,2 @@\r\n-old\r\n+new\r\n+next\r\n'
        )
        changes = coverage.parse_zero_context_diff(status, patch)
        self.assertEqual({1}, changes["module/src/main/kotlin/owner/Space Name.kt"].new_lines)
        self.assertEqual({4, 5}, changes["new\u2603.kt"].new_lines)
        self.assertEqual("R", changes["new\u2603.kt"].status)

    def test_modified_blob_without_hunk_is_rejected_not_treated_as_empty_denominator(self):
        status = b"M\0module/src/main/kotlin/owner/Subject.kt\0"
        patch = b"diff --git a/module/src/main/kotlin/owner/Subject.kt b/module/src/main/kotlin/owner/Subject.kt\n"
        with self.assertRaisesRegex(coverage.CoverageError, "no hunk"):
            coverage.parse_zero_context_diff(status, patch, changed_blob_paths={"module/src/main/kotlin/owner/Subject.kt"})


class ClassificationAndSummaryTest(unittest.TestCase):
    def test_feature_classification_is_exhaustive_non_overlapping_and_has_no_rendering_floor(self):
        sources = {"feature/src/main/State.kt", "feature/src/main/Screen.kt"}
        units = [
            {"id": "feature|state", "family": "state", "sources": ["feature/src/main/State.kt"], "lineFloorBasisPoints": 8000},
            {"id": "feature|rendering", "family": "rendering", "sources": ["feature/src/main/Screen.kt"]},
        ]
        self.assertEqual(sources, set(coverage.classify_exact_sources(sources, units)))
        with self.assertRaisesRegex(coverage.CoverageError, "rendering.*floor"):
            coverage.classify_exact_sources(
                sources,
                [units[0], {**units[1], "lineFloorBasisPoints": 1}],
            )
        with self.assertRaisesRegex(coverage.CoverageError, "unclassified"):
            coverage.classify_exact_sources(sources | {"feature/src/main/New.kt"}, units)
        with self.assertRaisesRegex(coverage.CoverageError, "overlap"):
            coverage.classify_exact_sources(sources, [units[0], {**units[1], "sources": list(sources)}])

    def test_failure_summary_is_sorted_relative_deterministic_and_written_before_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "summary.json")
            coverage.write_summary(
                path,
                source_commit="1" * 40,
                event="local",
                status="fail",
                violations=["zeta", "alpha", "alpha"],
                artifacts=["build/z.json", "build/a.json"],
            )
            first = path.read_bytes()
            coverage.write_summary(
                path,
                source_commit="1" * 40,
                event="local",
                status="fail",
                violations=["alpha", "zeta"],
                artifacts=["build/a.json", "build/z.json"],
            )
            self.assertEqual(first, path.read_bytes())
            payload = json.loads(first)
            self.assertEqual(["alpha", "zeta"], payload["violations"])
            self.assertNotIn(str(Path(directory)), first.decode())


def fixture_unit():
    return {
        "id": ":domain:sample|module",
        "family": "contract",
        "selection": "all",
        "sources": [],
        "lineTargetBasisPoints": 9000,
        "branchTargetBasisPoints": 8000,
    }


def jacoco_xml(source_rows):
    grouped: dict[str, list[tuple]] = {}
    for row in source_rows:
        grouped.setdefault(row[0], []).append(row)
    packages = []
    for package, rows in grouped.items():
        sources = []
        for _, filename, line, missed, covered, missed_branches, covered_branches in rows:
            sources.append(
                f'<sourcefile name="{filename}"><line nr="{line}" mi="{missed}" ci="{covered}" '
                f'mb="{missed_branches}" cb="{covered_branches}"/>'
                f'<counter type="LINE" missed="{missed}" covered="{covered}"/>'
                f'<counter type="BRANCH" missed="{missed_branches}" covered="{covered_branches}"/>'
                '</sourcefile>',
            )
        packages.append(f'<package name="{package}">{"".join(sources)}</package>')
    return f'<report name="sample">{"".join(packages)}</report>'.encode()


def run_git(root: Path, *arguments: str) -> str:
    return subprocess.check_output(["git", *arguments], cwd=root, text=True)


if __name__ == "__main__":
    unittest.main()
