from __future__ import annotations

import copy
import hashlib
import json
import re
import shutil
import tempfile
import unittest
from pathlib import Path

from scripts.quality.build_inputs.test_class_decomposition import (
    DecompositionError,
    _all_test_methods,
    _round21_schedule,
    load_decomposition_contract,
    round21_bridge_inventory_source,
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

    def assert_round21_source_mutation_fails(
        self,
        relative: str,
        mutate: object,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            copied = self.copied_contract_root(directory)
            path = copied / relative
            source = path.read_text(encoding="utf-8")
            changed = mutate(source) if callable(mutate) else source.replace(*mutate, 1)
            self.assertNotEqual(source, changed)
            path.write_text(changed, encoding="utf-8")
            contract = load_decomposition_contract(CONTRACT)
            source_row = next(
                row
                for row in contract["round21SourceClassRebalancing"]["sourceFiles"]
                if row["path"] == relative
            )
            source_row["finalSourceSha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
            with self.assertRaises(DecompositionError):
                verify_decomposition_data(copied, contract)

    def assert_contract_mutations_fail(
        self,
        mutations: list[tuple[str, dict[str, object]]],
        expected_count: int,
    ) -> None:
        self.assertEqual(expected_count, len(mutations))
        for label, mutation in mutations:
            with self.subTest(label=label):
                with self.assertRaises(DecompositionError):
                    verify_decomposition_data(ROOT, mutation)

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

    def test_round21_correction_envelopes_and_bridge_are_exact(self) -> None:
        receipt = verify_decomposition(ROOT, CONTRACT)

        self.assertEqual(
            "cf952140198dde7b6b4335996fffd7b08ff73ecdaa138cf00b87b29837ac2f80",
            receipt["round21BridgeSha256"],
        )
        self.assertEqual(
            "d68bfbb8238fd8cf1b6b6c3b24a8a04e1adfb08a45a4577b991da7bedbe786cc",
            receipt["round21UseSha256"],
        )
        self.assertEqual(
            [24, 15, 5, 12],
            [receipt["round21AccessCount"][path] for path in sorted(receipt["round21AccessCount"])],
        )
        self.assertEqual(
            {"0596b5ad38e0287dd72c7a92cf5debeb1a4422eefaf01537d842e82539ff5cb9"},
            set(receipt["round21AnnotationSha256"].values()),
        )
        self.assertEqual(
            {"83634e032e4a2bd7b1eea117445f36c77d14fa737a2304cc9b04589fddd37a89"},
            set(receipt["round21RuleSha256"].values()),
        )

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

        self.assertEqual(6, len(mutations))
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index):
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

        duplicate_old = copy.deepcopy(contract)
        duplicate_old["round21SourceClassRebalancing"]["mappings"][1]["oldKey"] = (
            duplicate_old["round21SourceClassRebalancing"]["mappings"][0]["oldKey"]
        )
        mutations.append(duplicate_old)

        lost = copy.deepcopy(contract)
        lost["round21SourceClassRebalancing"]["mappings"].pop()
        mutations.append(lost)

        extra = copy.deepcopy(contract)
        extra["round21SourceClassRebalancing"]["mappings"].append(
            {
                "annotation": "@Test",
                "bodySha256": "0" * 64,
                "newKey": "com.gasstation.buildlogic.ExtraTest#extra",
                "oldKey": "com.gasstation.buildlogic.ExtraOldTest#extra",
            },
        )
        mutations.append(extra)

        renamed = copy.deepcopy(contract)
        renamed["round21SourceClassRebalancing"]["mappings"][0]["newKey"] += "Renamed"
        mutations.append(renamed)

        mixed_family = copy.deepcopy(contract)
        old_key = mixed_family["round21SourceClassRebalancing"]["mappings"][0]["oldKey"]
        method_name = old_key.rsplit("#", 1)[1]
        mixed_family["round21SourceClassRebalancing"]["mappings"][0]["newKey"] = (
            f"com.gasstation.buildlogic.RoborazziPropertySelectionTest#{method_name}"
        )
        mutations.append(mixed_family)

        body = copy.deepcopy(contract)
        body["round21SourceClassRebalancing"]["mappings"][0]["bodySha256"] = "0" * 64
        mutations.append(body)

        annotation = copy.deepcopy(contract)
        annotation["round21SourceClassRebalancing"]["mappings"][0]["annotation"] = "@Ignore"
        mutations.append(annotation)

        wrong_class = copy.deepcopy(contract)
        wrong_class["round21SourceClassRebalancing"]["expectedFinalClassCount"] = 51
        mutations.append(wrong_class)

        wrong_target_class = copy.deepcopy(contract)
        mapping = wrong_target_class["round21SourceClassRebalancing"]["mappings"][0]
        method_name = mapping["newKey"].rsplit("#", 1)[1]
        mapping["newKey"] = f"com.gasstation.buildlogic.AndroidLintPropertySelectionTest#{method_name}"
        mutations.append(wrong_target_class)

        zero_duration = copy.deepcopy(contract)
        zero_duration["round21SourceClassRebalancing"]["durationLedger"][0]["durationSeconds"] = "0.000"
        mutations.append(zero_duration)

        negative_duration = copy.deepcopy(contract)
        negative_duration["round21SourceClassRebalancing"]["durationLedger"][0]["durationSeconds"] = "-0.001"
        mutations.append(negative_duration)

        unknown_duration = copy.deepcopy(contract)
        unknown_duration["round21SourceClassRebalancing"]["durationLedger"][0]["method"] = "unknown.Owner#method"
        mutations.append(unknown_duration)

        duration_number = copy.deepcopy(contract)
        duration_number["round21SourceClassRebalancing"]["durationLedger"][0]["durationSeconds"] = 1.0
        mutations.append(duration_number)

        missing_source = copy.deepcopy(contract)
        missing_source["round21SourceClassRebalancing"]["durationLedger"][0]["sourceCommit"] = ""
        mutations.append(missing_source)

        source_status = copy.deepcopy(contract)
        source_status["round21SourceClassRebalancing"]["durationLedger"][0]["sourceStatus"] = "FAILED"
        mutations.append(source_status)

        source_artifact = copy.deepcopy(contract)
        source_artifact["round21SourceClassRebalancing"]["durationLedger"][0]["sourceArtifactSha256"] = "0" * 64
        mutations.append(source_artifact)

        source_registry = copy.deepcopy(contract)
        source_registry["round21SourceClassRebalancing"]["durationSources"][0]["relativePath"] += ".drift"
        mutations.append(source_registry)

        schedule = copy.deepcopy(contract)
        schedule["round21SourceClassRebalancing"]["options"][1]["schedule"][0]["units"].reverse()
        mutations.append(schedule)

        grouped_order = copy.deepcopy(contract)
        grouped_order["round21SourceClassRebalancing"]["options"][1]["units"][0:2] = reversed(
            grouped_order["round21SourceClassRebalancing"]["options"][1]["units"][0:2],
        )
        mutations.append(grouped_order)

        one_method_tie = copy.deepcopy(contract)
        one_method_units = one_method_tie["round21SourceClassRebalancing"]["options"][2]["units"]
        tie_indexes = [
            index
            for index, row in enumerate(one_method_units)
            if row["durationSeconds"] == "0.005"
            and "ProductionDependencyPolicyTest#" in row["unitId"]
        ]
        self.assertEqual(2, len(tie_indexes))
        first_tie, second_tie = tie_indexes
        one_method_units[first_tie], one_method_units[second_tie] = (
            one_method_units[second_tie],
            one_method_units[first_tie],
        )
        mutations.append(one_method_tie)

        worker_tie = copy.deepcopy(contract)
        worker_tie["round21SourceClassRebalancing"]["options"][1]["schedule"][0]["worker"] = 2
        mutations.append(worker_tie)

        bound = copy.deepcopy(contract)
        bound["round21SourceClassRebalancing"]["boundSeconds"] = "1572.482"
        mutations.append(bound)

        hash_drift = copy.deepcopy(contract)
        hash_drift["round21SourceClassRebalancing"]["durationLedgerSha256"] = "0" * 64
        mutations.append(hash_drift)

        mapping_hash = copy.deepcopy(contract)
        mapping_hash["round21SourceClassRebalancing"]["mappingLedgerSha256"] = "0" * 64
        mutations.append(mapping_hash)

        current_inventory = copy.deepcopy(contract)
        current_inventory["round21SourceClassRebalancing"]["inventorySha256"]["currentMethods"] = "0" * 64
        mutations.append(current_inventory)

        final_inventory = copy.deepcopy(contract)
        final_inventory["round21SourceClassRebalancing"]["inventorySha256"]["finalMethods"] = "0" * 64
        mutations.append(final_inventory)

        membership_hash = copy.deepcopy(contract)
        membership_hash["round21SourceClassRebalancing"]["options"][1]["membershipLedgerSha256"] = "0" * 64
        mutations.append(membership_hash)

        unit_hash = copy.deepcopy(contract)
        unit_hash["round21SourceClassRebalancing"]["options"][1]["unitDurationLedgerSha256"] = "0" * 64
        mutations.append(unit_hash)

        schedule_hash = copy.deepcopy(contract)
        schedule_hash["round21SourceClassRebalancing"]["options"][1]["scheduleSha256"] = "0" * 64
        mutations.append(schedule_hash)

        option_description = copy.deepcopy(contract)
        option_description["round21SourceClassRebalancing"]["options"][1]["description"] += "-drift"
        mutations.append(option_description)

        nested_schema = copy.deepcopy(contract)
        nested_schema["round21SourceClassRebalancing"]["schemaVersion"] = 2
        mutations.append(nested_schema)

        source_current_hash = copy.deepcopy(contract)
        source_current_hash["round21SourceClassRebalancing"]["sourceFiles"][0]["currentSourceSha256"] = "0" * 64
        mutations.append(source_current_hash)

        source_support_hash = copy.deepcopy(contract)
        source_support_hash["round21SourceClassRebalancing"]["sourceFiles"][0]["currentSupportSha256"] = "0" * 64
        mutations.append(source_support_hash)

        source_final_hash = copy.deepcopy(contract)
        source_final_hash["round21SourceClassRebalancing"]["sourceFiles"][0]["finalSourceSha256"] = "0" * 64
        mutations.append(source_final_hash)

        self.assertEqual(36, len(mutations))
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index):
                with self.assertRaises(DecompositionError):
                    verify_decomposition_data(ROOT, mutation)

    def test_contract_serializer_rejects_every_noncanonical_byte_shape(self) -> None:
        raw = CONTRACT.read_bytes()
        parsed = json.loads(raw)
        reversed_keys = {key: parsed[key] for key in reversed(parsed)}
        noncanonical = [
            ("utf8-bom", b"\xef\xbb\xbf" + raw),
            ("crlf", raw.replace(b"\n", b"\r\n")),
            ("missing-final-lf", raw.removesuffix(b"\n")),
            ("extra-final-lf", raw + b"\n"),
            ("insignificant-space", raw.replace(b"{", b"{ ", 1)),
            ("unicode-escape", raw.replace(b'"@Test"', b'"\\u0040Test"', 1)),
            (
                "object-key-order",
                (json.dumps(reversed_keys, ensure_ascii=False, separators=(",", ":")) + "\n").encode(),
            ),
            ("invalid-utf8", raw.replace(b'"@Test"', b'"\xffTest"', 1)),
            (
                "non-finite-number",
                raw.replace(b'"durationSeconds":"0.014"', b'"durationSeconds":NaN', 1),
            ),
        ]
        self.assertEqual(9, len(noncanonical))
        for label, content in noncanonical:
            with self.subTest(label=label), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "contract.json"
                path.write_bytes(content)
                with self.assertRaises(DecompositionError):
                    load_decomposition_contract(path)

        self.assertEqual(parsed, load_decomposition_contract(CONTRACT))

    def test_round21_schema_shape_and_decimal_mutations_fail_independently(self) -> None:
        contract = load_decomposition_contract(CONTRACT)
        mutations: list[tuple[str, dict[str, object]]] = []

        def add(label: str, mutate: object) -> None:
            changed = copy.deepcopy(contract)
            mutate(changed)
            mutations.append((label, changed))

        add("outer-schema-version-value", lambda row: row.__setitem__("schemaVersion", 2))
        add("outer-schema-version-type", lambda row: row.__setitem__("schemaVersion", "1"))
        add("outer-schema-version-missing", lambda row: row.pop("schemaVersion"))
        add("outer-extra-field", lambda row: row.__setitem__("extra", None))
        add("outer-renamed-field", lambda row: row.__setitem__("expectedTotalMethodCount", row.pop("expectedTotalMethods")))

        add("round21-schema-version-value", lambda row: row["round21SourceClassRebalancing"].__setitem__("schemaVersion", 2))
        add("round21-schema-version-type", lambda row: row["round21SourceClassRebalancing"].__setitem__("schemaVersion", "1"))
        add("round21-schema-version-missing", lambda row: row["round21SourceClassRebalancing"].pop("schemaVersion"))
        add("round21-extra-field", lambda row: row["round21SourceClassRebalancing"].__setitem__("extra", None))
        add(
            "round21-renamed-field",
            lambda row: row["round21SourceClassRebalancing"].__setitem__(
                "selectedSchedule",
                row["round21SourceClassRebalancing"].pop("selectedOption"),
            ),
        )

        row_locations = [
            ("mapping", lambda row: row["round21SourceClassRebalancing"]["mappings"][0]),
            ("duration", lambda row: row["round21SourceClassRebalancing"]["durationLedger"][0]),
            ("duration-source", lambda row: row["round21SourceClassRebalancing"]["durationSources"][0]),
            ("source-file", lambda row: row["round21SourceClassRebalancing"]["sourceFiles"][0]),
            ("option", lambda row: row["round21SourceClassRebalancing"]["options"][0]),
            ("unit", lambda row: row["round21SourceClassRebalancing"]["options"][0]["units"][0]),
            ("schedule-lane", lambda row: row["round21SourceClassRebalancing"]["options"][0]["schedule"][0]),
            ("schedule-unit", lambda row: row["round21SourceClassRebalancing"]["options"][0]["schedule"][0]["units"][0]),
            ("corroboration", lambda row: row["round21SourceClassRebalancing"]["localCorroborations"][0]),
        ]
        key_by_location = {
            "mapping": "annotation",
            "duration": "sourceStatus",
            "duration-source": "commit",
            "source-file": "supportOwner",
            "option": "description",
            "unit": "members",
            "schedule-lane": "worker",
            "schedule-unit": "owner",
            "corroboration": "relativePath",
        }
        for location, select in row_locations:
            key = key_by_location[location]
            add(f"{location}-missing-field", lambda row, select=select, key=key: select(row).pop(key))
            add(f"{location}-extra-field", lambda row, select=select: select(row).__setitem__("extra", None))
            add(
                f"{location}-renamed-field",
                lambda row, select=select, key=key: select(row).__setitem__(f"{key}Renamed", select(row).pop(key)),
            )

        for container in ("inventorySha256", "lpt"):
            add(
                f"{container}-missing-field",
                lambda row, container=container: row["round21SourceClassRebalancing"][container].pop(
                    next(iter(row["round21SourceClassRebalancing"][container])),
                ),
            )
            add(
                f"{container}-extra-field",
                lambda row, container=container: row["round21SourceClassRebalancing"][container].__setitem__("extra", None),
            )
            add(
                f"{container}-renamed-field",
                lambda row, container=container: row["round21SourceClassRebalancing"][container].__setitem__(
                    "renamed",
                    row["round21SourceClassRebalancing"][container].pop(
                        next(iter(row["round21SourceClassRebalancing"][container])),
                    ),
                ),
            )

        decimal_locations = [
            ("bound", lambda row: row["round21SourceClassRebalancing"], "boundSeconds"),
            ("lower-bound", lambda row: row["round21SourceClassRebalancing"], "idealLowerBoundSeconds"),
            ("total", lambda row: row["round21SourceClassRebalancing"], "totalDurationSeconds"),
            ("duration", lambda row: row["round21SourceClassRebalancing"]["durationLedger"][0], "durationSeconds"),
            ("corroboration", lambda row: row["round21SourceClassRebalancing"]["localCorroborations"][0], "durationSeconds"),
        ]
        for option_index, option_id in enumerate(("A", "B", "C")):
            decimal_locations.extend(
                [
                    (f"option-{option_id}", lambda row, index=option_index: row["round21SourceClassRebalancing"]["options"][index], "maximumSeconds"),
                    (f"unit-{option_id}", lambda row, index=option_index: row["round21SourceClassRebalancing"]["options"][index]["units"][0], "durationSeconds"),
                    (f"schedule-lane-{option_id}", lambda row, index=option_index: row["round21SourceClassRebalancing"]["options"][index]["schedule"][0], "durationSeconds"),
                    (f"schedule-unit-{option_id}", lambda row, index=option_index: row["round21SourceClassRebalancing"]["options"][index]["schedule"][0]["units"][0], "durationSeconds"),
                ],
            )
        for location, select, key in decimal_locations:
            for shape, value in (("number", 1.0), ("two-place", "1.00"), ("four-place", "1.0000"), ("unit-suffix", "1.000s")):
                add(
                    f"{location}-{shape}",
                    lambda row, select=select, key=key, value=value: select(row).__setitem__(key, value),
                )

        self.assertEqual(111, len(mutations))
        for label, mutation in mutations:
            with self.subTest(label=label):
                if label.endswith(("-number", "-two-place", "-four-place", "-unit-suffix")):
                    with self.assertRaisesRegex(DecompositionError, "three-place seconds string"):
                        verify_decomposition_data(ROOT, mutation)
                else:
                    with self.assertRaises(DecompositionError):
                        verify_decomposition_data(ROOT, mutation)

    def test_every_normative_integer_rejects_bool_and_equal_float_types(self) -> None:
        contract = load_decomposition_contract(CONTRACT)
        locations: list[tuple[str, object, str]] = [
            ("outer-schema-version", lambda row: row, "schemaVersion"),
            ("outer-total-count", lambda row: row, "expectedTotalMethods"),
            (
                "round21-schema-version",
                lambda row: row["round21SourceClassRebalancing"],
                "schemaVersion",
            ),
            (
                "round21-final-class-count",
                lambda row: row["round21SourceClassRebalancing"],
                "expectedFinalClassCount",
            ),
            (
                "round21-moved-method-count",
                lambda row: row["round21SourceClassRebalancing"],
                "expectedMovedMethodCount",
            ),
            (
                "round21-total-method-count",
                lambda row: row["round21SourceClassRebalancing"],
                "expectedTotalMethods",
            ),
            (
                "round21-unchanged-method-count",
                lambda row: row["round21SourceClassRebalancing"],
                "expectedUnchangedMethodCount",
            ),
            (
                "round21-lpt-workers",
                lambda row: row["round21SourceClassRebalancing"]["lpt"],
                "workers",
            ),
        ]
        for option_index, option_id in enumerate(("A", "B", "C")):
            locations.append(
                (
                    f"option-{option_id}-unit-count",
                    lambda row, index=option_index: row["round21SourceClassRebalancing"]["options"][index],
                    "unitCount",
                ),
            )
            for schedule_index in range(5):
                locations.append(
                    (
                        f"option-{option_id}-schedule-worker-{schedule_index + 1}",
                        lambda row, option=option_index, schedule=schedule_index: row[
                            "round21SourceClassRebalancing"
                        ]["options"][option]["schedule"][schedule],
                        "worker",
                    ),
                )

        self.assertEqual(26, len(locations))
        mutations: list[tuple[str, dict[str, object]]] = []
        for label, select, field in locations:
            bool_mutation = copy.deepcopy(contract)
            select(bool_mutation)[field] = True
            mutations.append((f"{label}-bool", bool_mutation))

            float_mutation = copy.deepcopy(contract)
            selected = select(float_mutation)
            selected[field] = float(selected[field])
            mutations.append((f"{label}-equal-float", float_mutation))

        self.assertEqual(52, len(mutations))
        selected_b_worker_bool = next(
            mutation
            for label, mutation in mutations
            if label == "option-B-schedule-worker-1-bool"
        )
        submitted_schedule = selected_b_worker_bool["round21SourceClassRebalancing"]["options"][1]["schedule"]
        submitted_schedule_sha = hashlib.sha256(
            (
                json.dumps(
                    submitted_schedule,
                    ensure_ascii=False,
                    allow_nan=False,
                    sort_keys=True,
                    separators=(",", ":"),
                )
                + "\n"
            ).encode(),
        ).hexdigest()
        self.assertEqual(
            "09ec8afee9cbb21ebe91f96a8c757c1856de6f544cc3ad0e1f79d0c0c7cb8bb2",
            submitted_schedule_sha,
        )
        self.assertNotEqual(
            selected_b_worker_bool["round21SourceClassRebalancing"]["options"][1]["scheduleSha256"],
            submitted_schedule_sha,
        )

        for label, mutation in mutations:
            with self.subTest(label=label):
                with self.assertRaisesRegex(DecompositionError, "must be an integer"):
                    verify_decomposition_data(ROOT, mutation)

    def test_round21_option_component_order_and_lpt_mutations_fail_independently(self) -> None:
        contract = load_decomposition_contract(CONTRACT)
        mutations: list[tuple[str, dict[str, object]]] = []

        def add(label: str, mutate: object) -> None:
            changed = copy.deepcopy(contract)
            mutate(changed)
            mutations.append((label, changed))

        for option_index, option_id in enumerate(("A", "B", "C")):
            option_fields = {
                "id": f"{option_id}-drift",
                "decision": "drift",
                "description": "drift",
                "unitIdentity": "drift",
                "maximumSeconds": "9999.999",
                "unitCount": 999,
            }
            for field, value in option_fields.items():
                add(
                    f"option-{option_id}-{field}",
                    lambda row, index=option_index, field=field, value=value: row[
                        "round21SourceClassRebalancing"
                    ]["options"][index].__setitem__(field, value),
                )
            for field in (
                "membershipLedgerSha256",
                "unitDurationLedgerSha256",
                "scheduleSha256",
            ):
                add(
                    f"option-{option_id}-{field}",
                    lambda row, index=option_index, field=field: row[
                        "round21SourceClassRebalancing"
                    ]["options"][index].__setitem__(field, "0" * 64),
                )

        add("options-order", lambda row: row["round21SourceClassRebalancing"]["options"].reverse())
        add("options-missing", lambda row: row["round21SourceClassRebalancing"]["options"].pop())
        add(
            "options-extra",
            lambda row: row["round21SourceClassRebalancing"]["options"].append(
                copy.deepcopy(row["round21SourceClassRebalancing"]["options"][-1]),
            ),
        )
        for field in (
            "mappings",
            "durationLedger",
            "durationSources",
            "sourceFiles",
            "localCorroborations",
        ):
            add(
                f"canonical-list-order-{field}",
                lambda row, field=field: row["round21SourceClassRebalancing"][field].reverse(),
            )

        for option_index, option_id in enumerate(("A", "B", "C")):
            add(
                f"option-{option_id}-unit-order",
                lambda row, index=option_index: row["round21SourceClassRebalancing"]["options"][index]["units"].reverse(),
            )
            add(
                f"option-{option_id}-schedule-lane-order",
                lambda row, index=option_index: row["round21SourceClassRebalancing"]["options"][index]["schedule"].reverse(),
            )

            def reverse_busy_lane(row: dict[str, object], index: int = option_index) -> None:
                schedule = row["round21SourceClassRebalancing"]["options"][index]["schedule"]
                lane = next(candidate for candidate in schedule if len(candidate["units"]) > 1)
                lane["units"].reverse()

            add(f"option-{option_id}-schedule-unit-order", reverse_busy_lane)
            add(
                f"option-{option_id}-unit-id",
                lambda row, index=option_index: row["round21SourceClassRebalancing"]["options"][index]["units"][0].__setitem__(
                    "unitId",
                    row["round21SourceClassRebalancing"]["options"][index]["units"][0]["unitId"] + "-drift",
                ),
            )

        for option_index, option_id in enumerate(("A", "B")):
            def reverse_group_members(row: dict[str, object], index: int = option_index) -> None:
                units = row["round21SourceClassRebalancing"]["options"][index]["units"]
                unit = next(candidate for candidate in units if len(candidate["members"]) > 1)
                unit["members"].reverse()

            add(f"option-{option_id}-group-member-order", reverse_group_members)

        tie_groups = {
            "0.001": [
                "com.gasstation.buildlogic.ContractApiConventionTest#exactFiveActiveJvmContractsOwnImmutableDumpMappings",
                "com.gasstation.buildlogic.quality.KotlinAbiDumpParserTest#forbiddenFamiliesMatchCanonicalTypesNotSubstrings",
            ],
            "0.003": [
                "com.gasstation.buildlogic.AndroidLintPropertySelectionTest#fixtureMappingExcludesJvmLibraryFromAndroidLintClaims",
                "com.gasstation.buildlogic.GradlePluginHarnessFileSafetyTest#builderWritesNestedUtf8WithExactlyOneFinalNewline",
                "com.gasstation.buildlogic.quality.ProductionDependencyBoundaryTest#failingModuleGuardReusesConfigurationCacheAndReproducesPolicyEvidence",
                "com.gasstation.buildlogic.quality.ProductionDependencyPolicyTest#evidenceAggregationKeepsExactDeclarationBucketAndCompileRuntimeComponents",
            ],
            "0.004": [
                "GasStationConventionPropertiesTest#exactBooleanValuesAreAccepted",
                "com.gasstation.buildlogic.quality.GasStationJvmMutationConventionPluginTest#canonicalEffectiveSurfaceRejectsEveryChangedOrAddedMutationProducingField",
                "com.gasstation.buildlogic.quality.ProductionDependencyPolicyTest#exactProjectAllowlistKillsEveryRetiredRuleAndKeepsTheOneIntentionalException",
            ],
            "0.005": [
                "com.gasstation.buildlogic.quality.GasStationJvmMutationConventionPluginTest#blockingPhaseUsesExactNativeFloorsAndKeepsSettingsScoreReportOnly",
                "com.gasstation.buildlogic.quality.ProductionDependencyPolicyTest#activeTopologyBindsEveryScopeAndTestedTargetEndpoint",
                "com.gasstation.buildlogic.quality.ProductionDependencyPolicyTest#policyFailsClosedForCrLfWildcardsDuplicatesUnknownAndModuleDrift",
            ],
            "0.008": [
                "com.gasstation.buildlogic.GradlePluginHarnessFileSafetyTest#builderRejectsAbsoluteTraversalAndResolvedSymlinkEscapes",
                "com.gasstation.buildlogic.quality.GasStationJvmMutationConventionPluginTest#encodingSurfaceRejectsSameValueAlternateSourcesAndRequiresOneManagedArgument",
            ],
            "0.011": [
                "com.gasstation.buildlogic.quality.KotlinAbiDumpParserTest#signatureScannerFindsDirectArrayNestedGenericBoundSuspendAndFunctionPositions",
                "com.gasstation.buildlogic.quality.ProductionDependencyPolicyTest#canonicalPolicyRequiresExactModulesScopesAndSortedRecords",
            ],
            "0.014": [
                "GasStationConventionPropertiesTest#everyInvalidBooleanSpellingIsRejectedWithStableDiagnostic",
                "com.gasstation.buildlogic.quality.GasStationJvmMutationConventionPluginTest#dedicatedGradleCacheDependenciesUseLocationNeutralContentIdentity",
            ],
            "0.025": [
                "com.gasstation.buildlogic.quality.KotlinAbiDumpParserTest#realWriterGrammarSelectsClassesFieldsAndFunctions",
                "com.gasstation.buildlogic.quality.coverage.CoverageExecutionMergeTest#executionProducerMergesCompatibleBlocksByProbeOrAndRejectsIncompatibleDuplicates",
            ],
            "0.042": [
                "com.gasstation.buildlogic.KotlinCompilerRunnerPolicyTest#bothRunnerModesRejectEveryCacheAndIsolationOverride",
                "com.gasstation.buildlogic.quality.ProductionDependencyPolicyTest#directComparisonBindsDeclarationConfigurationAndComponentMembership",
            ],
        }
        option_c = contract["round21SourceClassRebalancing"]["options"][2]
        actual_ties: dict[str, list[str]] = {}
        for unit in option_c["units"]:
            actual_ties.setdefault(unit["durationSeconds"], []).append(unit["unitId"])
        actual_ties = {duration: owners for duration, owners in actual_ties.items() if len(owners) > 1}
        self.assertEqual(tie_groups, actual_ties)

        def swap_schedule_owners(row: dict[str, object], first: str, second: str) -> None:
            schedule = row["round21SourceClassRebalancing"]["options"][2]["schedule"]
            references = [unit for lane in schedule for unit in lane["units"]]
            first_ref = next(unit for unit in references if unit["owner"] == first)
            second_ref = next(unit for unit in references if unit["owner"] == second)
            self.assertEqual(first_ref["durationSeconds"], second_ref["durationSeconds"])
            first_ref["owner"], second_ref["owner"] = second_ref["owner"], first_ref["owner"]

        for duration, owners in tie_groups.items():
            add(
                f"one-method-owner-tie-{duration}",
                lambda row, first=owners[0], second=owners[1]: swap_schedule_owners(row, first, second),
            )

        option_b_schedule = contract["round21SourceClassRebalancing"]["options"][1]["schedule"]
        first_assignments = [lane["units"][0]["owner"] for lane in option_b_schedule]
        self.assertEqual(5, len(set(first_assignments)))

        def swap_first_assignments(row: dict[str, object], left: int, right: int) -> None:
            schedule = row["round21SourceClassRebalancing"]["options"][1]["schedule"]
            schedule[left]["units"][0], schedule[right]["units"][0] = (
                schedule[right]["units"][0],
                schedule[left]["units"][0],
            )

        for left in range(4):
            add(
                f"worker-zero-total-ordinal-{left + 1}-{left + 2}",
                lambda row, left=left: swap_first_assignments(row, left, left + 1),
            )

        for field in (
            "durationOrder",
            "groupedUnitTieBreak",
            "oneMethodUnitTieBreak",
            "workerTieBreak",
            "workers",
        ):
            add(
                f"lpt-{field}",
                lambda row, field=field: row["round21SourceClassRebalancing"]["lpt"].__setitem__(field, "drift"),
            )

        for field in (
            "currentClasses",
            "currentMethods",
            "finalClasses",
            "finalMethods",
            "unchangedMethods",
        ):
            add(
                f"inventory-{field}",
                lambda row, field=field: row["round21SourceClassRebalancing"]["inventorySha256"].__setitem__(field, "0" * 64),
            )

        equal_units = [
            {"durationSeconds": "1.000", "members": [f"Owner#{name}"], "unitId": f"Owner#{name}"}
            for name in reversed(("a", "b", "c", "d", "e", "f"))
        ]
        equal_schedule = _round21_schedule(equal_units)
        self.assertEqual(
            ["Owner#a", "Owner#b", "Owner#c", "Owner#d", "Owner#e"],
            [lane["units"][0]["owner"] for lane in equal_schedule],
        )
        self.assertEqual("Owner#f", equal_schedule[0]["units"][1]["owner"])

        self.assert_contract_mutations_fail(mutations, 72)

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

    def test_round21_support_annotation_and_rule_mutations_fail_independently(self) -> None:
        relative = "build-logic/convention/src/test/kotlin/AndroidLintConventionPluginTest.kt"
        mutations = [
            ("    @get:Rule\n    val temporaryFolder", "    val temporaryFolder"),
            (
                "    @get:Rule\n    val temporaryFolder",
                "    @get:Rule\n    @Suppress(\"unused\")\n    val temporaryFolder",
            ),
            (
                "    val temporaryFolder = TemporaryFolder()",
                "    var temporaryFolder = TemporaryFolder()",
            ),
            (
                "    val temporaryFolder = TemporaryFolder()",
                "    val temporaryFolders = TemporaryFolder()",
            ),
            (
                "    val temporaryFolder = TemporaryFolder()",
                "    val temporaryFolder = TemporaryFolder(File(\"drift\"))",
            ),
            (
                "    val temporaryFolder = TemporaryFolder()",
                "    val temporaryFolder: TemporaryFolder = TemporaryFolder()",
            ),
        ]
        for before, after in mutations:
            with self.subTest(after=after):
                self.assert_round21_source_mutation_fails(relative, (before, after))

    def test_round21_bridge_annotation_access_shape_order_and_value_mutations_fail(self) -> None:
        relative = "build-logic/convention/src/test/kotlin/AndroidLintConventionPluginTest.kt"
        names = [
            "MAIN_SOURCE",
            "TEST_ONLY_NEW_API",
            "MAIN_WARNING",
            "SECOND_WARNING",
            "NEW_ERROR_SOURCE",
            "REVIEWED_WARNING_BASELINE",
        ]
        mutation_count = 0
        for name in names:
            declaration = f"        internal val {name} ="
            mutations = [
                (declaration, f"        @JvmStatic\n{declaration}"),
                (declaration, f"        public val {name} ="),
                (declaration, f"        protected val {name} ="),
                (declaration, f"        private val {name} ="),
                (declaration, f"        val {name} ="),
                (declaration, f"        internal var {name} ="),
                (declaration, f"        internal const val {name} ="),
                (declaration, f"        internal lateinit var {name} ="),
                (declaration, f"        internal val {name}: String ="),
                (declaration, f"        internal val {name}_DRIFT ="),
            ]
            for before, after in mutations:
                with self.subTest(name=name, after=after):
                    self.assert_round21_source_mutation_fails(relative, (before, after))
                    mutation_count += 1

            def drift_value(source: str, member: str = name) -> str:
                pattern = re.compile(
                    rf"(^        internal val {re.escape(member)}\s*=\s*\"\"\")(.*?)(\"\"\"\.trimIndent\(\)\s*$)",
                    re.MULTILINE | re.DOTALL,
                )
                return pattern.sub(r"\1x\2\3", source, count=1)

            with self.subTest(name=name, mutation="value"):
                source = (ROOT / relative).read_text(encoding="utf-8")
                with self.assertRaisesRegex(
                    DecompositionError,
                    rf"bridge value differs: {name}",
                ):
                    round21_bridge_inventory_source(drift_value(source))
                self.assert_round21_source_mutation_fails(relative, drift_value)
                mutation_count += 1

            def drift_order(source: str, member: str = name) -> str:
                pattern = re.compile(
                    r"^        internal val (?:"
                    + "|".join(map(re.escape, names))
                    + r")\s*=\s*\"\"\".*?\"\"\"\.trimIndent\(\)\n?",
                    re.MULTILINE | re.DOTALL,
                )
                blocks = list(pattern.finditer(source))
                self.assertEqual(names, [match.group(0).split("internal val ", 1)[1].split()[0] for match in blocks])
                index = names.index(member)
                other = index + 1 if index < len(names) - 1 else 0
                first, second = blocks[index], blocks[other]
                if first.start() > second.start():
                    first, second = second, first
                return (
                    source[: first.start()]
                    + second.group(0)
                    + source[first.end() : second.start()]
                    + first.group(0)
                    + source[second.end() :]
                )

            with self.subTest(name=name, mutation="order"):
                source = (ROOT / relative).read_text(encoding="utf-8")
                with self.assertRaisesRegex(DecompositionError, "bridge declaration order differs"):
                    round21_bridge_inventory_source(drift_order(source))
                self.assert_round21_source_mutation_fails(relative, drift_order)
                mutation_count += 1

        self.assert_round21_source_mutation_fails(
            relative,
            (
                "    companion object {\n",
                "    companion object {\n        internal val EXTRA = \"drift\"\n",
            ),
        )
        mutation_count += 1
        self.assert_round21_source_mutation_fails(
            relative,
            ("return \"fixture\";", "return \"fixture-drift\";"),
        )
        mutation_count += 1
        self.assertEqual(74, mutation_count)

    def test_round21_other_support_access_mutations_fail_independently(self) -> None:
        mutations = {
            "build-logic/convention/src/test/kotlin/AndroidLintConventionPluginTest.kt": (
                "    protected fun newLintProject(",
                "    internal fun newLintProject(",
            ),
            "build-logic/convention/src/test/kotlin/GradlePluginTestHarnessTest.kt": (
                "internal abstract class GradlePluginTestHarnessSupport",
                "abstract class GradlePluginTestHarnessSupport",
            ),
            "build-logic/convention/src/test/kotlin/KotlinCompilerConventionPluginTest.kt": (
                "internal abstract class KotlinCompilerConventionTestSupport",
                "public abstract class KotlinCompilerConventionTestSupport",
            ),
            "build-logic/convention/src/test/kotlin/RoborazziConventionPluginTest.kt": (
                "internal abstract class RoborazziConventionTestSupport",
                "private abstract class RoborazziConventionTestSupport",
            ),
        }
        for relative, mutation in mutations.items():
            with self.subTest(relative=relative):
                self.assert_round21_source_mutation_fails(relative, mutation)

    def test_round21_helper_and_fixture_mutations_fail_independently(self) -> None:
        relative = "build-logic/convention/src/test/kotlin/AndroidLintConventionPluginTest.kt"
        mutations = [
            ("android-lint-gradle-user-home", "android-lint-gradle-user-home-drift"),
            ("val temporaryFolder = TemporaryFolder()", "val temporaryFolder = TemporaryFolder(File(\"drift\"))"),
        ]
        for before, after in mutations:
            with self.subTest(after=after), tempfile.TemporaryDirectory() as directory:
                copied = self.copied_contract_root(directory)
                path = copied / relative
                source = path.read_text(encoding="utf-8")
                self.assertIn(before, source)
                path.write_text(source.replace(before, after, 1), encoding="utf-8")
                contract = load_decomposition_contract(CONTRACT)
                source_row = next(
                    row
                    for row in contract["round21SourceClassRebalancing"]["sourceFiles"]
                    if row["path"] == relative
                )
                source_row["finalSourceSha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                with self.assertRaises(DecompositionError):
                    verify_decomposition_data(copied, contract)

    def test_round21_unchanged_method_and_round18_inverse_drift_fail_closed(self) -> None:
        contract = load_decomposition_contract(CONTRACT)
        with tempfile.TemporaryDirectory() as directory:
            copied = self.copied_contract_root(directory)
            path = copied / "build-logic/convention/src/test/kotlin/GasStationConventionPropertiesTest.kt"
            source = path.read_text(encoding="utf-8")
            method = "everyInvalidBooleanSpellingIsRejectedWithStableDiagnostic"
            self.assertIn(method, source)
            path.write_text(source.replace(method, method + "Renamed", 1), encoding="utf-8")
            with self.assertRaises(DecompositionError):
                verify_decomposition_data(copied, contract)

        round18 = copy.deepcopy(contract)
        round18["mappings"][0]["newMethod"] += "Renamed"
        with self.assertRaises(DecompositionError):
            verify_decomposition_data(ROOT, round18)

    def test_round21_import_envelope_rejects_extra_import_but_allows_reordering_and_whitespace(self) -> None:
        relative = "build-logic/convention/src/test/kotlin/AndroidLintConventionPluginTest.kt"
        import_mutations = [
            ("import java.io.File\n", "import java.io.File\nimport java.util.UUID\n"),
            ("import java.io.File\n", ""),
            ("import java.io.File\n", "import java.nio.file.Path\n"),
            ("import java.io.File\n", "import java.io.File as JvmFile\n"),
            ("import java.io.File\n", "import java.io.File\nimport java.io.File\n"),
        ]
        for before, after in import_mutations:
            with self.subTest(after=after):
                self.assert_round21_source_mutation_fails(relative, (before, after))

        with tempfile.TemporaryDirectory() as directory:
            copied = self.copied_contract_root(directory)
            path = copied / relative
            source = path.read_text(encoding="utf-8")
            first = "import java.io.File\n"
            second = "import org.gradle.testkit.runner.TaskOutcome\n"
            self.assertIn(first, source)
            self.assertIn(second, source)
            source = source.replace(first, "", 1).replace(second, first, 1)
            source = source.replace(first, second + first, 1)
            source = source.replace(second, "  import   org.gradle.testkit.runner.TaskOutcome   \n", 1)
            path.write_text(source, encoding="utf-8")
            contract = load_decomposition_contract(CONTRACT)
            source_row = next(
                row
                for row in contract["round21SourceClassRebalancing"]["sourceFiles"]
                if row["path"] == relative
            )
            source_row["finalSourceSha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
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
