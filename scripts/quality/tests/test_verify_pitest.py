from __future__ import annotations

import hashlib
import os
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from pitest_policy import (
    GitCommand,
    GitExecutor,
    MutationPolicyError,
    compare_floor,
    compare_no_coverage,
    read_strict_json,
    receipt,
    parse_pitest_xml,
    route_changed_paths,
)
from verify_pitest import changed_packages_for_module, validate_module_inventory_evolution


VALID_XML = b"""<?xml version="1.0" encoding="UTF-8"?>
<mutations partial="true">
  <mutation detected="true" status="KILLED" numberOfTestsRun="1">
    <sourceFile>Thing.kt</sourceFile>
    <mutatedClass>com.gasstation.domain.station.Thing</mutatedClass>
    <mutatedMethod>answer</mutatedMethod>
    <methodDescription>()I</methodDescription>
    <lineNumber>7</lineNumber>
    <mutator>org.pitest.mutationtest.engine.gregor.mutators.ReturnValsMutator</mutator>
    <indexes><index>3</index></indexes>
    <blocks><block>0</block></blocks>
    <killingTest>answer(com.gasstation.domain.station.ThingTest)</killingTest>
    <description>replaced int return with 0</description>
  </mutation>
  <mutation detected="false" status="SURVIVED" numberOfTestsRun="1">
    <sourceFile>Thing.kt</sourceFile>
    <mutatedClass>com.gasstation.domain.station.Thing</mutatedClass>
    <mutatedMethod>answer</mutatedMethod>
    <methodDescription>()I</methodDescription>
    <lineNumber>8</lineNumber>
    <mutator>org.pitest.mutationtest.engine.gregor.mutators.MathMutator</mutator>
    <indexes><index>4</index></indexes>
    <blocks><block>0</block></blocks>
    <killingTest></killingTest>
    <description>Replaced integer addition with subtraction</description>
  </mutation>
  <mutation detected="false" status="NO_COVERAGE" numberOfTestsRun="0">
    <sourceFile>Thing.kt</sourceFile>
    <mutatedClass>com.gasstation.domain.station.Thing</mutatedClass>
    <mutatedMethod>answer</mutatedMethod>
    <methodDescription>()I</methodDescription>
    <lineNumber>9</lineNumber>
    <mutator>org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator</mutator>
    <indexes><index>5</index></indexes>
    <blocks><block>1</block></blocks>
    <killingTest></killingTest>
    <description>changed conditional boundary</description>
  </mutation>
</mutations>
"""


class PitestParserTest(unittest.TestCase):
    def test_valid_report_has_exact_counters_and_semantic_identity(self) -> None:
        report = parse_pitest_xml(
            VALID_XML,
            module="station",
            package_root="com.gasstation.domain.station",
            source_lookup=lambda class_name, source_file: f"domain/station/src/main/kotlin/{source_file}",
            class_lookup=lambda class_name: ("Thing.class", source_file_bytes("Thing.kt", class_name)),
        )

        self.assertEqual({"KILLED": 1, "SURVIVED": 1, "NO_COVERAGE": 1, "total": 3}, report.counters)
        self.assertEqual((1, 3), report.mutation_score)
        self.assertEqual((1, 2), report.test_strength)
        self.assertEqual(hashlib.sha256(VALID_XML).hexdigest(), report.raw_sha256)
        self.assertEqual(64, len(report.semantic_sha256))

    def test_all_no_coverage_has_explicit_not_applicable_strength(self) -> None:
        xml = VALID_XML.replace(b'status="KILLED" numberOfTestsRun="1"', b'status="NO_COVERAGE" numberOfTestsRun="0"', 1)
        xml = xml.replace(b'detected="true"', b'detected="false"', 1)
        xml = xml.replace(b'<killingTest>answer(com.gasstation.domain.station.ThingTest)</killingTest>', b'<killingTest></killingTest>', 1)
        xml = xml.replace(b'status="SURVIVED" numberOfTestsRun="1"', b'status="NO_COVERAGE" numberOfTestsRun="0"', 1)
        report = parse_pitest_xml(
            xml,
            module="station",
            package_root="com.gasstation.domain.station",
            source_lookup=lambda class_name, source_file: source_file,
            class_lookup=lambda class_name: ("Thing.class", source_file_bytes("Thing.kt", class_name)),
        )

        self.assertIsNone(report.test_strength)
        self.assertEqual(
            {"state": "not-applicable", "numerator": 0, "denominator": 0, "value": None},
            report.rational_summary()["testStrength"],
        )

    def test_rejects_unsupported_status_and_status_contradictions(self) -> None:
        for replacement in (
            b"TIMED_OUT", b"NON_VIABLE", b"MEMORY_ERROR", b"RUN_ERROR",
            b"NOT_STARTED", b"STARTED", b"EQUIVALENT", b"UNKNOWN",
        ):
            with self.subTest(status=replacement):
                with self.assertRaisesRegex(MutationPolicyError, "unsupported PIT status"):
                    parse_pitest_xml(
                        VALID_XML.replace(b"KILLED", replacement, 1),
                        module="station",
                        package_root="com.gasstation.domain.station",
                        source_lookup=lambda class_name, source_file: source_file,
                        class_lookup=lambda class_name: ("Thing.class", source_file_bytes("Thing.kt", class_name)),
                    )
        with self.assertRaisesRegex(MutationPolicyError, "KILLED mutation must be detected"):
            parse_pitest_xml(
                VALID_XML.replace(b'detected="true"', b'detected="false"', 1),
                module="station",
                package_root="com.gasstation.domain.station",
                source_lookup=lambda class_name, source_file: source_file,
                class_lookup=lambda class_name: ("Thing.class", source_file_bytes("Thing.kt", class_name)),
            )

    def test_rejects_dtd_oversize_invalid_utf8_unknown_attributes_and_empty_indexes(self) -> None:
        cases = {
            "DTD": b'<!DOCTYPE mutations [<!ENTITY x "x">]>' + VALID_XML,
            "invalid UTF-8": VALID_XML.replace(b"Thing.kt", b"Thing\xff.kt", 1),
            "unknown attribute": VALID_XML.replace(b' detected="true"', b' extra="x" detected="true"', 1),
            "empty indexes": VALID_XML.replace(b"<indexes><index>3</index></indexes>", b"<indexes></indexes>", 1),
            "duplicate index": VALID_XML.replace(b"<index>3</index>", b"<index>3</index><index>3</index>", 1),
            "empty blocks": VALID_XML.replace(b"<blocks><block>0</block></blocks>", b"<blocks></blocks>", 1),
        }
        for label, xml in cases.items():
            with self.subTest(label=label), self.assertRaises(MutationPolicyError):
                parse_pitest_xml(
                    xml,
                    module="station",
                    package_root="com.gasstation.domain.station",
                    source_lookup=lambda class_name, source_file: source_file,
                    class_lookup=lambda class_name: ("Thing.class", source_file_bytes("Thing.kt", class_name)),
                )
        with self.assertRaisesRegex(MutationPolicyError, "input-size ceiling"):
            parse_pitest_xml(
                VALID_XML,
                module="station",
                package_root="com.gasstation.domain.station",
                source_lookup=lambda class_name, source_file: source_file,
                class_lookup=lambda class_name: ("Thing.class", source_file_bytes("Thing.kt", class_name)),
                maximum_bytes=len(VALID_XML) - 1,
            )

    def test_semantic_digest_ignores_row_order_and_killing_test_choice_but_raw_hash_does_not(self) -> None:
        root = ET.fromstring(VALID_XML)
        root[:] = list(reversed(list(root)))
        reordered = ET.tostring(root, encoding="utf-8", xml_declaration=True)
        changed_killer = VALID_XML.replace(
            b"answer(com.gasstation.domain.station.ThingTest)",
            b"other(com.gasstation.domain.station.ThingTest)",
        )

        def parse(data: bytes):
            return parse_pitest_xml(
                data,
                module="station",
                package_root="com.gasstation.domain.station",
                source_lookup=lambda class_name, source_file: source_file,
                class_lookup=lambda class_name: ("Thing.class", source_file_bytes("Thing.kt", class_name)),
            )

        original = parse(VALID_XML)
        for changed in (parse(reordered), parse(changed_killer)):
            self.assertEqual(original.semantic_sha256, changed.semantic_sha256)
            self.assertNotEqual(original.raw_sha256, changed.raw_sha256)

    def test_rejects_schema_descriptor_duplicate_identity_and_sourcefile_mismatch(self) -> None:
        mutations = {
            "root": VALID_XML.replace(b' partial="true"', b""),
            "descriptor": VALID_XML.replace(b"()I", b"bad", 1),
            "duplicate": VALID_XML.replace(b"</mutations>", VALID_XML.split(b"<mutation", 1)[1].split(b"</mutation>", 1)[0].join((b"<mutation", b"</mutation>\n</mutations>"))),
        }
        for label, xml in mutations.items():
            with self.subTest(label=label):
                with self.assertRaises(MutationPolicyError):
                    parse_pitest_xml(
                        xml,
                        module="station",
                        package_root="com.gasstation.domain.station",
                        source_lookup=lambda class_name, source_file: source_file,
                        class_lookup=lambda class_name: ("Thing.class", source_file_bytes("Thing.kt", class_name)),
                    )
        with self.assertRaisesRegex(MutationPolicyError, "SourceFile"):
            parse_pitest_xml(
                VALID_XML,
                module="station",
                package_root="com.gasstation.domain.station",
                source_lookup=lambda class_name, source_file: source_file,
                class_lookup=lambda class_name: ("Thing.class", source_file_bytes("Other.kt", class_name)),
            )

    def test_rejects_attributes_on_every_ordinary_child_and_nested_container(self) -> None:
        for tag in (
            "sourceFile", "mutatedClass", "mutatedMethod", "methodDescription", "lineNumber",
            "mutator", "indexes", "blocks", "killingTest", "description",
        ):
            with self.subTest(tag=tag), self.assertRaisesRegex(MutationPolicyError, "attributes"):
                parse_pitest_xml(
                    VALID_XML.replace(f"<{tag}>".encode(), f'<{tag} unexpected="x">'.encode(), 1),
                    module="station",
                    package_root="com.gasstation.domain.station",
                    source_lookup=lambda class_name, source_file: source_file,
                    class_lookup=lambda class_name: ("Thing.class", source_file_bytes("Thing.kt", class_name)),
                )

    def test_jvm_descriptor_grammar_rejects_array_void_and_accepts_complete_valid_shapes(self) -> None:
        valid = ("()V", "(I)I", "([I[[Ljava/lang/String;)Ljava/util/List;", "(J[D)[[B")
        invalid = ("()[V", "([V)V", "(V)V", "()L;", "()Ljava.lang.String;", "()[", "(I)", "I)V")
        for descriptor in valid:
            with self.subTest(valid=descriptor):
                parse_pitest_xml(
                    VALID_XML.replace(b"()I", descriptor.encode(), 1),
                    module="station",
                    package_root="com.gasstation.domain.station",
                    source_lookup=lambda class_name, source_file: source_file,
                    class_lookup=lambda class_name: ("Thing.class", source_file_bytes("Thing.kt", class_name)),
                )
        for descriptor in invalid:
            with self.subTest(invalid=descriptor), self.assertRaisesRegex(MutationPolicyError, "descriptor"):
                parse_pitest_xml(
                    VALID_XML.replace(b"()I", descriptor.encode(), 1),
                    module="station",
                    package_root="com.gasstation.domain.station",
                    source_lookup=lambda class_name, source_file: source_file,
                    class_lookup=lambda class_name: ("Thing.class", source_file_bytes("Thing.kt", class_name)),
                )

    def test_class_parser_rejects_out_of_range_and_wrong_kind_constant_pool_references(self) -> None:
        good = source_file_bytes("Thing.kt", "com.gasstation.domain.station.Thing")
        mutations = {
            "this class out of range": good[:10 + 3 + len(b"com/gasstation/domain/station/Thing") + 3 + 3 + len(b"java/lang/Object") + 3 + 3 + len(b"SourceFile") + 3 + len(b"Thing.kt")] + good[0:0],
            "class name wrong kind": good.replace(b"\x07\x00\x01", b"\x07\x00\x02", 1),
            "source attribute wrong kind": good[:-2] + b"\x00\x02",
        }
        # Truncation and both reference-kind mutations must be rejected by the bounded parser.
        for label, class_bytes in mutations.items():
            with self.subTest(label=label), self.assertRaises(MutationPolicyError):
                parse_pitest_xml(
                    VALID_XML,
                    module="station",
                    package_root="com.gasstation.domain.station",
                    source_lookup=lambda class_name, source_file: source_file,
                    class_lookup=lambda class_name: ("Thing.class", class_bytes),
                )

    def test_java17_method_handle_reference_kind_matrix_is_exact(self) -> None:
        valid = {
            1: {9}, 2: {9}, 3: {9}, 4: {9},
            5: {10}, 6: {10, 11}, 7: {10, 11}, 8: {10}, 9: {11},
        }
        for reference_kind, allowed_tags in valid.items():
            for reference_tag in (9, 10, 11):
                class_bytes = source_file_bytes_with_method_handle(reference_kind, reference_tag)
                if reference_tag in allowed_tags:
                    parse_pitest_xml(
                        VALID_XML,
                        module="station",
                        package_root="com.gasstation.domain.station",
                        source_lookup=lambda class_name, source_file: source_file,
                        class_lookup=lambda class_name, data=class_bytes: ("Thing.class", data),
                    )
                else:
                    with self.subTest(reference_kind=reference_kind, reference_tag=reference_tag):
                        with self.assertRaisesRegex(MutationPolicyError, "method-handle reference"):
                            parse_pitest_xml(
                                VALID_XML,
                                module="station",
                                package_root="com.gasstation.domain.station",
                                source_lookup=lambda class_name, source_file: source_file,
                                class_lookup=lambda class_name, data=class_bytes: ("Thing.class", data),
                            )

    def test_class_parser_accepts_strict_modified_utf8_and_rejects_malformed_forms(self) -> None:
        modified_utf8 = b"prefix\xc0\x80\xed\xa0\xbd\xed\xb8\x80suffix"
        parse_pitest_xml(
            VALID_XML,
            module="station",
            package_root="com.gasstation.domain.station",
            source_lookup=lambda class_name, source_file: source_file,
            class_lookup=lambda class_name: (
                "Thing.class",
                source_file_bytes("Thing.kt", class_name, extra_utf8=modified_utf8),
            ),
        )

        for malformed in (b"literal\x00nul", b"\xc1\x81", b"\xe0\x80\x80", b"\xed\xa0"):
            with self.subTest(malformed=malformed):
                with self.assertRaisesRegex(MutationPolicyError, "UTF-8"):
                    parse_pitest_xml(
                        VALID_XML,
                        module="station",
                        package_root="com.gasstation.domain.station",
                        source_lookup=lambda class_name, source_file: source_file,
                        class_lookup=lambda class_name: (
                            "Thing.class",
                            source_file_bytes("Thing.kt", class_name, extra_utf8=malformed),
                        ),
                    )


class ExactFloorAndRoutingTest(unittest.TestCase):
    def test_exact_floor_boundaries_do_not_round(self) -> None:
        self.assertTrue(compare_floor(45, 100, 45))
        self.assertFalse(compare_floor(44, 100, 45))
        self.assertTrue(compare_floor(75, 100, 75))
        self.assertFalse(compare_floor(74, 100, 75))

    def test_route_module_shared_wrapper_and_rename_paths(self) -> None:
        self.assertEqual(
            ["station"],
            route_changed_paths([("M", "domain/station/src/main/kotlin/A.kt", "domain/station/src/main/kotlin/A.kt")]),
        )
        self.assertEqual(
            ["location", "settings", "station"],
            route_changed_paths([("M", "gradlew", "gradlew")]),
        )
        self.assertEqual(
            ["location", "settings", "station"],
            route_changed_paths([("M", "build-logic/convention/src/main/kotlin/GasStationJvmLibraryConventionPlugin.kt", "build-logic/convention/src/main/kotlin/GasStationJvmLibraryConventionPlugin.kt")]),
        )
        self.assertEqual(
            ["location", "station"],
            route_changed_paths([("R", "domain/station/src/main/kotlin/A.kt", "domain/location/src/main/kotlin/A.kt")]),
        )
        self.assertEqual([], route_changed_paths([("M", "docs/architecture.md", "docs/architecture.md")]))

        for path in (
            "core/model/src/main/kotlin/Shared.kt",
            "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.properties",
            "scripts/quality/pitest_policy/contracts.py",
            "scripts/agent/verify.sh",
            ".github/workflows/mutation-schedule.yml",
        ):
            with self.subTest(path=path):
                self.assertEqual(
                    ["location", "settings", "station"],
                    route_changed_paths([("M", path, path)]),
                )

    def test_unknown_mutation_related_path_fails_closed(self) -> None:
        with self.assertRaisesRegex(MutationPolicyError, "unclassified mutation-related path"):
            route_changed_paths([("M", "config/quality/mutation-unknown.json", "config/quality/mutation-unknown.json")])

    def test_settings_and_new_packages_remain_no_coverage_blocking(self) -> None:
        baseline = {"com.gasstation.domain.settings": 0, "com.gasstation.domain.settings.old": 2}
        self.assertEqual([], compare_no_coverage(baseline, {"com.gasstation.domain.settings": 0}, {"com.gasstation.domain.settings"}))
        self.assertEqual(
            ["com.gasstation.domain.settings NO_COVERAGE increased: baseline=0 current=1"],
            compare_no_coverage(baseline, {"com.gasstation.domain.settings": 1}, {"com.gasstation.domain.settings"}),
        )
        self.assertEqual(
            ["com.gasstation.domain.settings.new NO_COVERAGE increased: baseline=0 current=1"],
            compare_no_coverage(baseline, {"com.gasstation.domain.settings.new": 1}, {"com.gasstation.domain.settings.new"}),
        )

    def test_changed_package_selection_reads_both_sides_of_renames_and_falls_back_for_tests(self) -> None:
        route = {
            "event": "pull-request",
            "mergeBase": "a" * 40,
            "sourceCommit": "b" * 40,
            "changes": [{
                "status": "R",
                "oldPath": "domain/station/src/main/kotlin/old/Thing.kt",
                "newPath": "domain/station/src/main/kotlin/new/Thing.kt",
            }],
        }
        blobs = {
            ("a" * 40, "domain/station/src/main/kotlin/old/Thing.kt"): b"package com.gasstation.domain.station.old\n",
            ("b" * 40, "domain/station/src/main/kotlin/new/Thing.kt"): b"package com.gasstation.domain.station.new\n",
        }
        self.assertEqual(
            {"com.gasstation.domain.station.old", "com.gasstation.domain.station.new"},
            changed_packages_for_module(
                route,
                "station",
                {"com.gasstation.domain.station", "com.gasstation.domain.station.old", "com.gasstation.domain.station.new"},
                lambda commit, path: blobs[(commit, path)],
            ),
        )
        route["changes"] = [{
            "status": "M",
            "oldPath": "domain/station/src/test/kotlin/ThingTest.kt",
            "newPath": "domain/station/src/test/kotlin/ThingTest.kt",
        }]
        self.assertEqual(
            {"com.gasstation.domain.station", "com.gasstation.domain.station.old"},
            changed_packages_for_module(
                route,
                "station",
                {"com.gasstation.domain.station", "com.gasstation.domain.station.old"},
                lambda _commit, _path: b"",
            ),
        )

    def test_unchanged_authored_source_cannot_silently_lose_a_compiled_class(self) -> None:
        shared = {
            "authoredMain": {"count": 1, "records": [{"path": "Thing.kt"}], "sha256": "a"},
            "authoredTest": {"count": 0, "records": [], "sha256": "b"},
            "compiledTest": {"count": 0, "records": [], "sha256": "c"},
            "effectiveSurface": {"fields": {}, "sha256": "d"},
            "sourceDirs": "source", "mutableCodePaths": "mutable",
            "additionalClasspath": "additional", "launchClasspath": "launch",
        }
        old = {
            **shared,
            "compiledMain": {"count": 1, "records": [{"path": "Thing.class", "sha256": "1" * 64}], "sha256": "e"},
        }
        new = {
            **shared,
            "compiledMain": {"count": 0, "records": [], "sha256": "f"},
        }
        with self.assertRaisesRegex(MutationPolicyError, "unchanged authored source lost compiled classes"):
            validate_module_inventory_evolution(
                "station", old, new, global_inputs_unchanged=False, toolchain_unchanged=True,
            )

    def test_receipt_binds_exact_predecessor_bytes(self) -> None:
        first = b'{"schemaVersion":1}\n'
        second = b'{"sourceCommit":"' + b"a" * 40 + b'"}\n'
        value = receipt("attempt-v1", {"route": first, "tasks": second})
        self.assertEqual(hashlib.sha256(first).hexdigest(), value["predecessors"]["route"])
        self.assertEqual(hashlib.sha256(second).hexdigest(), value["predecessors"]["tasks"])
        self.assertNotEqual(
            value,
            receipt("attempt-v1", {"route": first + b" ", "tasks": second}),
        )

    def test_strict_json_rejects_duplicate_keys_and_booleans_as_integers(self) -> None:
        with self.assertRaisesRegex(MutationPolicyError, "duplicate JSON key"):
            read_strict_json(b'{"schemaVersion":1,"schemaVersion":2}\n')
        self.assertIs(True, read_strict_json(b'{"enabled":true}\n')["enabled"])


class GitObjectViewTest(unittest.TestCase):
    def test_typed_executor_disables_replacements_and_rejects_inventory(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run(["git", "init", "-q"], root)
            run(["git", "config", "user.email", "quality@example.invalid"], root)
            run(["git", "config", "user.name", "Quality Test"], root)
            (root / "value.txt").write_text("A\n", encoding="utf-8")
            run(["git", "add", "value.txt"], root)
            run(["git", "commit", "-qm", "original"], root)
            original = output(["git", "rev-parse", "HEAD"], root)
            original_tree = output(["git", "rev-parse", "HEAD^{tree}"], root)
            (root / "value.txt").write_text("B\n", encoding="utf-8")
            run(["git", "commit", "-qam", "substitute"], root)
            substitute = output(["git", "rev-parse", "HEAD"], root)
            substitute_tree = output(["git", "rev-parse", "HEAD^{tree}"], root)
            run(["git", "replace", original, substitute], root)

            self.assertEqual(substitute_tree, output(["git", "rev-parse", f"{original}^{{tree}}"], root))
            executor = GitExecutor(root, git_path=Path(output(["which", "git"], root)))
            self.assertEqual(original_tree, executor.text(GitCommand.REV_PARSE, f"{original}^{{tree}}"))
            with self.assertRaisesRegex(MutationPolicyError, "replacement refs"):
                executor.assert_original_full_history()

            for argv in executor.recorded_argv:
                self.assertEqual("--no-replace-objects", argv[1])
                self.assertEqual("-C", argv[2])
                self.assertEqual(str(root.resolve()), argv[3])
            for environment in executor.recorded_environment:
                self.assertEqual("1", environment["GIT_NO_REPLACE_OBJECTS"])

    def test_real_graft_alternate_and_shallow_metadata_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run(["git", "init", "-q"], root)
            run(["git", "config", "user.email", "quality@example.invalid"], root)
            run(["git", "config", "user.name", "Quality Test"], root)
            (root / "value.txt").write_text("A\n", encoding="utf-8")
            run(["git", "add", "value.txt"], root)
            run(["git", "commit", "-qm", "original"], root)
            head = output(["git", "rev-parse", "HEAD"], root)
            executor = GitExecutor(root, git_path=Path(output(["which", "git"], root)))
            executor.assert_original_full_history()

            grafts = root / ".git/info/grafts"
            grafts.write_text(f"{head}\n", encoding="ascii")
            with self.assertRaisesRegex(MutationPolicyError, "legacy grafts"):
                executor.assert_original_full_history()
            grafts.unlink()

            alternate_objects = root / "alternate-objects"
            alternate_objects.mkdir()
            alternates = root / ".git/objects/info/alternates"
            alternates.write_text(f"{alternate_objects}\n", encoding="utf-8")
            with self.assertRaisesRegex(MutationPolicyError, "object alternates"):
                executor.assert_original_full_history()
            alternates.unlink()

            shallow = root / ".git/shallow"
            shallow.write_text(f"{head}\n", encoding="ascii")
            with self.assertRaisesRegex(MutationPolicyError, "shallow metadata"):
                executor.assert_original_full_history()


def source_file_bytes(source_file: str, class_name: str, *, extra_utf8: bytes | None = None) -> bytes:
    """Build the smallest valid Java-17 class file with one SourceFile attribute."""
    internal = class_name.replace(".", "/").encode()
    source = source_file.encode()
    utf8 = lambda value: b"\x01" + len(value).to_bytes(2, "big") + value
    pool = [
        utf8(internal),
        b"\x07\x00\x01",
        utf8(b"java/lang/Object"),
        b"\x07\x00\x03",
        utf8(b"SourceFile"),
        utf8(source),
    ]
    if extra_utf8 is not None:
        pool.append(utf8(extra_utf8))
    return (
        b"\xca\xfe\xba\xbe"
        + (0).to_bytes(2, "big")
        + (61).to_bytes(2, "big")
        + (len(pool) + 1).to_bytes(2, "big")
        + b"".join(pool)
        + b"\x00\x21\x00\x02\x00\x04"
        + b"\x00\x00\x00\x00\x00\x00"
        + b"\x00\x01\x00\x05\x00\x00\x00\x02\x00\x06"
    )


def source_file_bytes_with_method_handle(reference_kind: int, reference_tag: int) -> bytes:
    internal = b"com/gasstation/domain/station/Thing"
    utf8 = lambda value: b"\x01" + len(value).to_bytes(2, "big") + value
    pool = [
        utf8(internal),             # 1
        b"\x07\x00\x01",          # 2 Class
        utf8(b"java/lang/Object"), # 3
        b"\x07\x00\x03",          # 4 Class
        utf8(b"SourceFile"),       # 5
        utf8(b"Thing.kt"),         # 6
        utf8(b"member"),           # 7
        utf8(b"()V"),              # 8
        b"\x0c\x00\x07\x00\x08",  # 9 NameAndType
        bytes((reference_tag,)) + b"\x00\x02\x00\x09", # 10 member reference
        b"\x0f" + bytes((reference_kind,)) + b"\x00\x0a", # 11 MethodHandle
    ]
    return (
        b"\xca\xfe\xba\xbe"
        + (0).to_bytes(2, "big")
        + (61).to_bytes(2, "big")
        + (len(pool) + 1).to_bytes(2, "big")
        + b"".join(pool)
        + b"\x00\x21\x00\x02\x00\x04"
        + b"\x00\x00\x00\x00\x00\x00"
        + b"\x00\x01\x00\x05\x00\x00\x00\x02\x00\x06"
    )


def run(command: list[str], root: Path) -> None:
    subprocess.run(command, cwd=root, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def output(command: list[str], root: Path) -> str:
    return subprocess.run(command, cwd=root, check=True, stdout=subprocess.PIPE, text=True).stdout.strip()


if __name__ == "__main__":
    unittest.main()
