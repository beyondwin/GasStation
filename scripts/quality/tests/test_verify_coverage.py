#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import copy
import json
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import verify_coverage as coverage


class CanonicalAndSchemaTest(unittest.TestCase):
    def test_failed_capture_does_not_replace_existing_baseline_output(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory, "baseline.json")
            output.write_bytes(b"trusted-baseline\n")
            arguments = [
                "verify_coverage.py", "capture",
                "--manifest", "missing-manifest.json",
                "--policy", "missing-policy.json",
                "--source-commit", "1" * 40,
                "--output", str(output),
            ]
            with mock.patch.object(sys, "argv", arguments), mock.patch.object(
                coverage,
                "_measure",
                side_effect=coverage.CoverageError("synthetic capture failure"),
            ):
                self.assertEqual(1, coverage._main())

            self.assertEqual(b"trusted-baseline\n", output.read_bytes())

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
    def test_semantic_identity_covers_report_package_source_class_method_line_and_counter_structure(self):
        xml = b'''\
<report name="sample"><counter type="LINE" missed="1" covered="2"/>
<package name="owner"><counter type="BRANCH" missed="3" covered="4"/>
<class name="owner/Subject" sourcefilename="Subject.kt">
<method name="value" desc="()I" line="7"><counter type="LINE" missed="0" covered="1"/></method>
<counter type="LINE" missed="0" covered="1"/></class>
<sourcefile name="Subject.kt"><line nr="7" mi="0" ci="1" mb="1" cb="1"/>
<counter type="LINE" missed="0" covered="1"/></sourcefile></package></report>'''
        parsed = coverage.parse_jacoco_xml(xml, ":sample|main")
        kinds = {record["kind"] for record in parsed.semantic_records}
        self.assertEqual(
            {
                "report-identity", "report-counter", "package-identity", "package-counter",
                "source-identity", "source-counter", "source-line", "class-identity",
                "class-counter", "method-identity", "method-counter",
            },
            kinds,
        )
        self.assertEqual({("owner", "Subject.kt"): {"owner/Subject"}}, parsed.source_classes)

    def test_manifest_evidence_rejects_post_manifest_class_exec_and_xml_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            class_file = root / "prepared/owner/Subject.class"
            exec_file = root / "test.exec"
            xml_file = root / "report.xml"
            class_file.parent.mkdir(parents=True)
            class_file.write_bytes(b"class-v1")
            exec_file.write_bytes(b"exec-v1")
            xml_file.write_bytes(jacoco_xml([("owner", "Subject.kt", 1, 0, 1, 0, 0)]))
            parsed = coverage.parse_jacoco_xml(xml_file.read_bytes(), ":sample|main")
            execution_records = [{"classId": "0000000000000001", "name": "owner/Subject", "probes": "01"}]
            entry = {
                "reportId": ":sample|main",
                "testSources": [],
                "testInputIdentitySha256": hashlib.sha256(coverage.canonical_json_bytes([])).hexdigest(),
                "preparedClassDirectory": "prepared",
                "classFileCount": 1,
                "classes": [{
                    "path": "owner/Subject.class",
                    "sha256": hashlib.sha256(class_file.read_bytes()).hexdigest(),
                    "jacocoClassId": "0000000000000001",
                }],
                "executionData": ["test.exec"],
                "executionFileSha256": hashlib.sha256(exec_file.read_bytes()).hexdigest(),
                "executionRecords": execution_records,
                "ignoredNonProjectExecutionRecordCount": 0,
                "executionSemanticSha256": hashlib.sha256(
                    coverage.canonical_json_bytes(execution_records),
                ).hexdigest(),
                "xmlReport": "report.xml",
                "xmlFileSha256": hashlib.sha256(xml_file.read_bytes()).hexdigest(),
                "reportSemanticSha256": parsed.semantic_sha256,
            }
            coverage.validate_entry_evidence(root, entry)
            for path, replacement, expected in (
                (class_file, b"class-v2", "class hash mismatch"),
                (exec_file, b"exec-v2", "execution data hash mismatch"),
                (xml_file, b'<report name="changed"/>', "XML hash mismatch"),
            ):
                original = path.read_bytes()
                path.write_bytes(replacement)
                with self.subTest(path=path.name), self.assertRaisesRegex(coverage.CoverageError, expected):
                    coverage.validate_entry_evidence(root, entry)
                path.write_bytes(original)

    def test_manifest_evidence_requires_exactly_one_exec_and_class_inventory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            entry = {
                "reportId": ":sample|main",
                "testSources": [],
                "testInputIdentitySha256": hashlib.sha256(coverage.canonical_json_bytes([])).hexdigest(),
                "executionData": [],
                "classes": [],
                "classFileCount": 0,
            }
            with self.assertRaisesRegex(coverage.CoverageError, "exactly one execution data"):
                coverage.validate_entry_evidence(root, entry)
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

    def test_modified_rename_without_hunk_and_status_header_mismatch_fail_closed(self):
        status = b"R090\0old.kt\0new.kt\0"
        patch = b"diff --git a/old.kt b/new.kt\n--- a/old.kt\n+++ b/new.kt\n"
        with self.assertRaisesRegex(coverage.CoverageError, "no hunk"):
            coverage.parse_zero_context_diff(status, patch, changed_blob_paths={"new.kt"})
        mismatched = b"diff --git a/other.kt b/new.kt\n--- a/other.kt\n+++ b/new.kt\n@@ -1 +1 @@\n-old\n+new\n"
        with self.assertRaisesRegex(coverage.CoverageError, "patch header"):
            coverage.parse_zero_context_diff(status, mismatched, changed_blob_paths={"new.kt"})

    def test_hardened_diff_uses_raw_blob_bytes_for_modified_rename(self):
        base = self.commit
        run_git(
            self.root,
            "mv",
            "module/src/main/kotlin/owner/Subject.kt",
            "module/src/main/kotlin/owner/Renamed.kt",
        )
        self.write("module/src/main/kotlin/owner/Renamed.kt", "package owner\nclass Renamed\n")
        run_git(self.root, "add", ".")
        run_git(self.root, "commit", "-qm", "modified rename")
        changes = coverage._hardened_diff(self.root, base)
        renamed = changes["module/src/main/kotlin/owner/Renamed.kt"]
        self.assertEqual("R", renamed.status)
        self.assertGreater(renamed.hunk_count, 0)

    def test_hardened_diff_overrides_hostile_repository_configuration(self):
        base = self.commit
        self.write("module/src/main/kotlin/owner/Subject.kt", "package owner\nclass Subject { val value = 2 }\n")
        run_git(self.root, "add", ".")
        run_git(self.root, "commit", "-qm", "change")
        for key, value in (
            ("diff.noPrefix", "true"),
            ("diff.mnemonicPrefix", "true"),
            ("diff.srcPrefix", "hostile-old/"),
            ("diff.dstPrefix", "hostile-new/"),
            ("diff.indentHeuristic", "true"),
            ("diff.relative", "true"),
            ("diff.renameLimit", "1"),
            ("diff.interHunkContext", "99"),
        ):
            run_git(self.root, "config", key, value)
        changes = coverage._hardened_diff(self.root, base)
        self.assertEqual({2}, changes["module/src/main/kotlin/owner/Subject.kt"].new_lines)


class BaselineLineageTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        run_git(self.root, "init", "-q")
        run_git(self.root, "config", "user.name", "Coverage Test")
        run_git(self.root, "config", "user.email", "coverage@example.invalid")
        (self.root / "config/quality").mkdir(parents=True)
        self.policy_path = self.root / "config/quality/coverage-policy.json"
        self.baseline_path = self.root / "config/quality/coverage-baseline.json"
        self.policy_path.write_bytes(coverage.canonical_json_bytes({
            "schemaVersion": 1,
            "enforcementMode": "blocking",
            "activeModules": [":benchmark"],
            "excludedModules": [{
                "module": ":benchmark",
                "reason": "connected macrobenchmark and device performance evidence owns this module",
            }],
            "reports": [],
            "units": [],
            "changedThresholds": {"lineBasisPoints": 8000, "branchBasisPoints": 7000},
            "maximumBaselineDropBasisPoints": 50,
            "maximumFloorRaiseBasisPoints": 200,
            "nonExecutableExceptions": [],
            "unclassifiedAuthoredSource": "fail",
        }) + b"\n")
        run_git(self.root, "add", ".")
        run_git(self.root, "commit", "-qm", "first architecture")
        self.first_source = run_git(self.root, "rev-parse", "HEAD").strip()

    def tearDown(self):
        self.temporary.cleanup()

    def baseline(self, source_commit: str, predecessor=None):
        return {
            "schemaVersion": 1,
            "sourceCommit": source_commit,
            "policySha256": hashlib.sha256(self.policy_path.read_bytes()).hexdigest(),
            "manifestSchemaVersion": 1,
            "predecessor": predecessor,
            "reports": [],
            "units": [],
        }

    def test_first_baseline_requires_no_baseline_blob_at_exact_source_and_valid_ancestry(self):
        candidate = self.baseline(self.first_source)
        coverage.validate_baseline_lineage(self.root, candidate, self.first_source)
        self.baseline_path.write_text(json.dumps(candidate))
        run_git(self.root, "add", ".")
        run_git(self.root, "commit", "-qm", "baseline")
        later = run_git(self.root, "rev-parse", "HEAD").strip()
        forged = self.baseline(later)
        with self.assertRaisesRegex(coverage.CoverageError, "first baseline source already contains"):
            coverage.validate_baseline_lineage(self.root, forged, later)
        with self.assertRaisesRegex(coverage.CoverageError, "ancestor"):
            coverage.validate_baseline_lineage(self.root, candidate, "0" * 39 + "1")

    def test_replacement_requires_exact_predecessor_blobs_and_non_decreasing_bounded_floors(self):
        old = self.baseline(self.first_source)
        old["units"] = [{
            "id": ":sample|module",
            "line": {"covered": 8, "missed": 2, "total": 10},
            "branch": {"covered": 0, "missed": 0, "total": 0},
            "authoredSourceCount": 1, "executableLineCount": 10, "branchCount": 0, "classCount": 1,
            "lineFloorBasisPointsAtCapture": 7000,
        }]
        self.baseline_path.write_bytes(coverage.canonical_json_bytes(old) + b"\n")
        run_git(self.root, "add", ".")
        run_git(self.root, "commit", "-qm", "old baseline")
        source = run_git(self.root, "rev-parse", "HEAD").strip()
        predecessor = {
            "commit": source,
            "baselineBlobSha256": hashlib.sha256(self.baseline_path.read_bytes()).hexdigest(),
            "policyBlobSha256": hashlib.sha256(self.policy_path.read_bytes()).hexdigest(),
        }
        replacement = self.baseline(source, predecessor)
        replacement["units"] = [{**old["units"][0], "lineFloorBasisPointsAtCapture": 7200}]
        historical = coverage.validate_baseline_lineage(self.root, replacement, source)
        coverage.validate_predecessor_floor_transitions(historical, replacement, 200)
        replacement["units"][0]["lineFloorBasisPointsAtCapture"] = 6999
        with self.assertRaisesRegex(coverage.CoverageError, "decrease"):
            coverage.validate_predecessor_floor_transitions(historical, replacement, 200)

    def test_replacement_preserves_intentionally_unfloored_measured_unit(self):
        unit = {
            "id": ":sample|assembly",
            "line": {"covered": 8, "missed": 2, "total": 10},
            "branch": {"covered": 1, "missed": 1, "total": 2},
            "authoredSourceCount": 1,
            "executableLineCount": 10,
            "branchCount": 2,
            "classCount": 1,
        }
        historical = {"units": [unit]}
        replacement = {"units": [{**unit, "line": {"covered": 9, "missed": 1, "total": 10}}]}

        coverage.validate_predecessor_floor_transitions(historical, replacement, 200)

class ClassificationAndSummaryTest(unittest.TestCase):
    def test_historical_test_inventory_may_differ_when_report_topology_identity_is_stable(self):
        current_unit = {
            "id": ":sample|rendering",
            "line": {"covered": 0, "missed": 1, "total": 1},
            "branch": {"covered": 0, "missed": 0, "total": 0},
            "authoredSourceCount": 1,
            "executableLineCount": 1,
            "branchCount": 0,
            "classCount": 1,
        }
        measurement = {
            "schemaVersion": 1,
            "policySha256": "1" * 64,
            "reports": [{
                "reportId": ":sample|main",
                "inputIdentitySha256": "2" * 64,
                "measuredTestInputIdentitySha256": "new",
                "measuredTestSources": [{"path": "NewTest.kt", "sha256": "3" * 64}],
            }],
            "units": [copy.deepcopy(current_unit)],
        }
        baseline = {
            "schemaVersion": 1,
            "sourceCommit": "1" * 40,
            "policySha256": "1" * 64,
            "manifestSchemaVersion": 1,
            "predecessor": None,
            "reports": [{
                "reportId": ":sample|main",
                "inputIdentitySha256": "2" * 64,
                "measuredTestInputIdentitySha256": "4" * 64,
                "measuredTestSources": [{"path": "OldTest.kt", "filename": "OldTest.kt", "sha256": "4" * 64}],
            }],
            "units": [current_unit],
        }
        policy = {
            "enforcementMode": "blocking",
            "maximumBaselineDropBasisPoints": 50,
            "units": [{"id": ":sample|rendering", "family": "rendering"}],
        }
        self.assertEqual([], coverage._verify_current(measurement, policy, baseline))
        measurement["units"][0]["classCount"] = 0
        self.assertEqual(
            [":sample|rendering denominator decreased: classCount 1 -> 0"],
            coverage._verify_current(measurement, policy, baseline),
        )
        measurement["units"][0]["classCount"] = 1
        measurement["reports"][0]["inputIdentitySha256"] = "9" * 64
        with self.assertRaisesRegex(coverage.CoverageError, "topology identity"):
            coverage._verify_current(measurement, policy, baseline)

    def test_app_shared_changed_lines_are_checked_independently_in_demo_and_prod(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared = "app/src/main/kotlin/owner/Shared.kt"
            entries = {}
            for variant, covered in (("demoDebug", 1), ("prodDebug", 0)):
                report_id = f":app|{variant}"
                xml = root / f"{variant}.xml"
                xml.write_bytes(jacoco_xml([("owner", "Shared.kt", 1, 1 - covered, covered, 0, 0)]))
                entries[report_id] = {
                    "xmlReport": xml.name,
                    "sources": [{"path": shared, "package": "owner", "filename": "Shared.kt"}],
                }
            changed = {
                shared: coverage.ChangedFile("M", shared, shared, {1}, hunk_count=1),
            }
            policy = {"changedThresholds": {"lineBasisPoints": 8000, "branchBasisPoints": 7000}}
            with mock.patch.object(coverage, "_git", side_effect=[b"", b"1" * 40 + b"\n"]), \
                    mock.patch.object(coverage, "_hardened_diff", return_value=changed):
                violations = coverage._changed_violations(
                    Path("manifest.json"), policy, entries, root, "local", "1" * 40,
                )
            self.assertEqual([":app|prodDebug changed line coverage is below 8000bp"], violations)

    def test_changed_coverage_details_record_exact_counters_and_source_lines(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = "module/src/main/kotlin/owner/Subject.kt"
            xml = root / "report.xml"
            xml.write_bytes(
                b'<report name="sample"><package name="owner"><sourcefile name="Subject.kt">'
                b'<line nr="3" mi="0" ci="1" mb="1" cb="1"/>'
                b'<line nr="4" mi="1" ci="0" mb="0" cb="0"/>'
                b'</sourcefile></package></report>',
            )
            entries = {
                ":sample|main": {
                    "xmlReport": xml.name,
                    "sources": [{"path": source, "package": "owner", "filename": "Subject.kt"}],
                },
            }
            changes = {source: coverage.ChangedFile("M", source, source, {2, 3, 4}, hunk_count=1)}
            policy = {"changedThresholds": {"lineBasisPoints": 8000, "branchBasisPoints": 7000}}
            with mock.patch.object(coverage, "_git", side_effect=[b"", b"1" * 40 + b"\n"]), \
                    mock.patch.object(coverage, "_hardened_diff", return_value=changes):
                violations, details, merge_base = coverage._changed_coverage(
                    Path("manifest.json"), policy, entries, root, "local", "1" * 40,
                )

            self.assertEqual("1" * 40, merge_base)
            self.assertEqual(
                [
                    ":sample|main changed line coverage is below 8000bp",
                    ":sample|main changed branch coverage is below 7000bp",
                ],
                violations,
            )
            self.assertEqual(
                [{
                    "reportId": ":sample|main",
                    "line": {"covered": 1, "missed": 1, "total": 2},
                    "branch": {"covered": 1, "missed": 1, "total": 2},
                    "sourceLines": [f"{source}:3", f"{source}:4"],
                }],
                details,
            )

    def test_event_base_semantics_fail_closed_for_pull_request_and_skip_tag(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(coverage.CoverageError, "pull-request"):
                coverage._changed_violations(
                    Path("manifest.json"), {"changedThresholds": {}}, {}, root, "pull-request", None,
                )
            self.assertEqual(
                [],
                coverage._changed_violations(
                    Path("manifest.json"), {"changedThresholds": {}}, {}, root, "tag", None,
                ),
            )

    def test_capture_keeps_rendering_units_report_only_without_inventing_floors(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run_git(root, "init", "-q")
            run_git(root, "config", "user.name", "Coverage Test")
            run_git(root, "config", "user.email", "coverage@example.invalid")
            (root / "marker").write_text("fixture")
            run_git(root, "add", "marker")
            run_git(root, "commit", "-qm", "fixture")
            commit = run_git(root, "rev-parse", "HEAD").strip()
            measurement = {
                "schemaVersion": 1,
                "sourceCommit": commit,
                "policySha256": "1" * 64,
                "manifestSchemaVersion": 1,
                "reports": [],
                "units": [
                    {
                        "id": f":sample|{family}",
                        "line": {"covered": 0, "missed": 1, "total": 1},
                        "branch": {"covered": 0, "missed": 0, "total": 0},
                        "authoredSourceCount": 1,
                        "executableLineCount": 1,
                        "branchCount": 0,
                        "classCount": 1,
                    }
                    for family in ("assembly", "rendering", "tool", "state")
                ],
            }
            policy = {
                "enforcementMode": "blocking",
                "units": [
                    {"id": f":sample|{family}", "family": family}
                    for family in ("assembly", "rendering", "tool", "state")
                ],
            }
            with self.assertRaisesRegex(coverage.CoverageError, "line floor"):
                coverage._capture(measurement, policy, root)
            policy["units"][-1]["lineFloorBasisPoints"] = 0
            captured = coverage._capture(measurement, policy, root)
            for unit in captured["units"]:
                if unit["id"] != ":sample|state":
                    self.assertNotIn("lineFloorBasisPointsAtCapture", unit)

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
