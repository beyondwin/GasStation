from __future__ import annotations

import copy
import shutil
import tempfile
import unittest
from pathlib import Path

from scripts.quality.build_inputs.test_class_decomposition import (
    DecompositionError,
    _all_test_methods,
    load_decomposition_contract,
    verify_decomposition,
    verify_decomposition_data,
)


ROOT = Path(__file__).resolve().parents[3]
CONTRACT = ROOT / "config/quality/test-class-decomposition.json"


class TestClassDecompositionTest(unittest.TestCase):
    def copied_contract_root(self, directory: str) -> Path:
        target = Path(directory)
        (target / "build-logic/convention").mkdir(parents=True)
        shutil.copy2(
            ROOT / "build-logic/convention/build.gradle.kts",
            target / "build-logic/convention/build.gradle.kts",
        )
        shutil.copytree(
            ROOT / "build-logic/convention/src",
            target / "build-logic/convention/src",
        )
        return target

    def test_reviewed_23_row_bijection_and_other_67_methods_are_exact(self) -> None:
        receipt = verify_decomposition(ROOT, CONTRACT)

        self.assertEqual(23, receipt["mappedMethodCount"])
        self.assertEqual(67, receipt["unchangedMethodCount"])
        self.assertEqual(90, receipt["totalMethodCount"])
        self.assertEqual(5, receipt["maxParallelForks"])
        self.assertEqual(15, receipt["defaultTimeoutMinutes"])
        self.assertEqual(30, receipt["sealedOuterTimeoutMinutes"])
        self.assertEqual("one-test-task-v1", receipt["testTaskTopology"])

    def test_round21_final_inventory_has_52_classes_and_90_methods(self) -> None:
        methods = _all_test_methods(ROOT)

        self.assertEqual(90, len(methods))
        self.assertEqual(52, len({method.owner for method in methods}))

    def test_round21_policy_mapping_and_selected_schedule_are_enforced(self) -> None:
        receipt = verify_decomposition(ROOT, CONTRACT)

        self.assertEqual(37, receipt["round21MovedMethodCount"])
        self.assertEqual(53, receipt["round21UnchangedMethodCount"])
        self.assertEqual(52, receipt["round21FinalClassCount"])
        self.assertEqual("B", receipt["round21SelectedOption"])
        self.assertEqual("1572.483", receipt["round21MaximumSeconds"])

    def test_duplicate_lost_mixed_or_changed_mapping_fails_closed(self) -> None:
        contract = load_decomposition_contract(CONTRACT)
        mutations = []

        duplicate = copy.deepcopy(contract)
        duplicate["mappings"][1]["newOwner"] = duplicate["mappings"][0]["newOwner"]
        duplicate["mappings"][1]["newMethod"] = duplicate["mappings"][0]["newMethod"]
        mutations.append(duplicate)

        lost = copy.deepcopy(contract)
        lost["mappings"].pop()
        mutations.append(lost)

        mixed = copy.deepcopy(contract)
        mixed["mappings"][0]["newOwner"] = (
            "com.gasstation.buildlogic.quality.coverage.MixedRootQualityCoverageTest"
        )
        mutations.append(mixed)

        changed = copy.deepcopy(contract)
        changed["mappings"][0]["methodBodySha256"] = "0" * 64
        mutations.append(changed)

        other_67 = copy.deepcopy(contract)
        other_67["unchangedMethodsSha256"] = "0" * 64
        mutations.append(other_67)

        total = copy.deepcopy(contract)
        total["expectedTotalMethods"] = 91
        mutations.append(total)

        for mutation in mutations:
            with self.subTest(mutation=mutation):
                with self.assertRaises(DecompositionError):
                    verify_decomposition_data(ROOT, mutation)

    def test_round21_mapping_duration_schedule_and_hash_mutations_fail_closed(self) -> None:
        contract = load_decomposition_contract(CONTRACT)
        mutations = []

        missing = copy.deepcopy(contract)
        missing["round21SourceClassRebalancing"].pop("selectedOption")
        mutations.append(missing)

        duplicate = copy.deepcopy(contract)
        duplicate["round21SourceClassRebalancing"]["mappings"][1]["newKey"] = (
            duplicate["round21SourceClassRebalancing"]["mappings"][0]["newKey"]
        )
        mutations.append(duplicate)

        body = copy.deepcopy(contract)
        body["round21SourceClassRebalancing"]["mappings"][0]["bodySha256"] = "0" * 64
        mutations.append(body)

        annotation = copy.deepcopy(contract)
        annotation["round21SourceClassRebalancing"]["mappings"][0]["annotation"] = "@Ignore"
        mutations.append(annotation)

        wrong_class = copy.deepcopy(contract)
        wrong_class["round21SourceClassRebalancing"]["expectedFinalClassCount"] = 51
        mutations.append(wrong_class)

        zero_duration = copy.deepcopy(contract)
        zero_duration["round21SourceClassRebalancing"]["durationLedger"][0]["durationSeconds"] = "0.000"
        mutations.append(zero_duration)

        unknown_duration = copy.deepcopy(contract)
        unknown_duration["round21SourceClassRebalancing"]["durationLedger"][0]["method"] = "unknown.Owner#method"
        mutations.append(unknown_duration)

        duration_number = copy.deepcopy(contract)
        duration_number["round21SourceClassRebalancing"]["durationLedger"][0]["durationSeconds"] = 1.0
        mutations.append(duration_number)

        schedule = copy.deepcopy(contract)
        schedule["round21SourceClassRebalancing"]["options"][1]["schedule"][0]["units"].reverse()
        mutations.append(schedule)

        bound = copy.deepcopy(contract)
        bound["round21SourceClassRebalancing"]["boundSeconds"] = "1572.482"
        mutations.append(bound)

        hash_drift = copy.deepcopy(contract)
        hash_drift["round21SourceClassRebalancing"]["durationLedgerSha256"] = "0" * 64
        mutations.append(hash_drift)

        for mutation in mutations:
            with self.subTest(mutation=mutation):
                with self.assertRaises(DecompositionError):
                    verify_decomposition_data(ROOT, mutation)

    def test_round21_access_helper_and_class_source_mutations_fail_closed(self) -> None:
        contract = load_decomposition_contract(CONTRACT)
        relative = "build-logic/convention/src/test/kotlin/AndroidLintConventionPluginTest.kt"
        mutations = [
            (
                "internal class AndroidLintPropertySelectionTest",
                "class AndroidLintPropertySelectionTest",
            ),
            (
                "protected fun newLintProject(",
                "public fun newLintProject(",
            ),
            (
                "internal class AndroidLintManagedDevicesTest",
                "internal class AndroidLintManagedDevicePolicyTest",
            ),
        ]
        for before, after in mutations:
            with self.subTest(after=after), tempfile.TemporaryDirectory() as directory:
                copied = self.copied_contract_root(directory)
                path = copied / relative
                source = path.read_text(encoding="utf-8")
                self.assertIn(before, source)
                path.write_text(source.replace(before, after, 1), encoding="utf-8")
                with self.assertRaises(DecompositionError):
                    verify_decomposition_data(copied, contract)

    def test_runtime_sharding_distribution_override_and_shared_home_are_absent(self) -> None:
        receipt = verify_decomposition(ROOT, CONTRACT)

        self.assertEqual([], receipt["prohibitedMatches"])
        self.assertEqual(23, len(set(receipt["newOwners"])))

    def test_helper_nested_dynamic_task_fork_filter_and_home_mutations_fail_closed(self) -> None:
        contract = load_decomposition_contract(CONTRACT)
        root_source = "build-logic/convention/src/test/kotlin/quality/GasStationRootQualityConventionPluginTest.kt"
        fixture = "build-logic/convention/src/test/kotlin/fixtures/GradlePluginTestProject.kt"
        build = "build-logic/convention/build.gradle.kts"
        mutations = [
            (root_source, "missing exact task surface", "changed exact task surface"),
            (root_source, "abstract class RootQualityTestSupport {", "abstract class RootQualityTestSupport {\n    @Test fun inheritedTest() = Unit"),
            (root_source, "abstract class RootQualityTestSupport {", "abstract class RootQualityTestSupport {\n    @TestFactory fun dynamicTest() = emptyList<Any>()"),
            (build, "maxParallelForks = 5", "maxParallelForks = 6"),
            (build, "tasks.withType<Test>().configureEach", "tasks.register<Test>(\"shard\")\ntasks.withType<Test>().configureEach"),
            (build, "maxParallelForks = 5", "maxParallelForks = 5\n    filter { includeTestsMatching(\"*Coverage*\") }"),
            (fixture, ".withGradleVersion(EXACT_GRADLE_VERSION)", ".withGradleInstallation(gradleHome)"),
        ]
        for relative, before, after in mutations:
            with self.subTest(relative=relative, after=after), tempfile.TemporaryDirectory() as directory:
                copied = self.copied_contract_root(directory)
                path = copied / relative
                source = path.read_text(encoding="utf-8")
                self.assertIn(before, source)
                path.write_text(source.replace(before, after, 1), encoding="utf-8")
                with self.assertRaises(DecompositionError):
                    verify_decomposition_data(copied, contract)


if __name__ == "__main__":
    unittest.main()
