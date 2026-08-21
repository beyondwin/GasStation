#!/usr/bin/env python3
"""Generate the canonical Task-9 build-input policy from reviewed constants."""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.quality.build_inputs.contracts import canonical_json_bytes, sha256_file  # noqa: E402
from scripts.quality.build_inputs.local_colima_evidence import (  # noqa: E402
    CLEANUP_PHASES,
    CONFIG_DESCRIPTOR,
    CONTAINER_INHERITED_LABELS,
    CONTAINER,
    CONTEXT,
    DELETE_ARGV,
    IMAGE,
    INDEX_DESCRIPTOR,
    LAYER_DESCRIPTORS,
    MAIN_BASE_COMMIT,
    MAIN_BASE_REF,
    OWNED_LABEL_KEYS,
    PROFILE,
    REQUIRED_EVIDENCE_ROWS,
    SELECTED_MANIFEST_DESCRIPTOR,
    STORE_OBSERVATION,
    START_ARGV,
    STOP_ARGV,
    VOLUMES,
)


OUTPUT = ROOT / "config/quality/build-inputs.json"
STATIC_SOURCES = (
    ".github/actions/setup-build-inputs/action.yml",
    "build-logic/convention/src/test/kotlin/GradlePluginTestHarnessTest.kt",
    "build-logic/convention/src/test/kotlin/fixtures/GradlePluginTestProject.kt",
    "scripts/agent/verify-room-schemas.sh",
    "scripts/agent/verify.sh",
    "scripts/quality/build_inputs/android_repository.py",
    "scripts/quality/build_inputs/archive.py",
    "scripts/quality/build_inputs/contracts.py",
    "scripts/quality/build_inputs/docs_gradle_validation_bridge.py",
    "scripts/quality/build_inputs/downloader.py",
    "scripts/quality/build_inputs/generate_policy.py",
    "scripts/quality/build_inputs/local_colima_evidence.py",
    "scripts/quality/build_inputs/receipts.py",
    "scripts/quality/build_inputs/reproducibility.py",
    "scripts/quality/build_inputs/run_gradle.sh",
    "scripts/quality/build_inputs/runtime.py",
    "scripts/quality/build_inputs/workflow.py",
    "scripts/quality/device/execute_gmd_task.sh",
    "scripts/quality/device/run_api24_avd.sh",
    "scripts/quality/device/run_gmd_lane.sh",
    "scripts/quality/run_pitest.sh",
    "scripts/quality/verify_build_inputs.py",
)


def action(
    owner: str,
    repository: str,
    path: str,
    commit: str,
    source_tag: str,
    manifest_path: str,
    manifest_sha256: str,
    kind: str,
) -> dict[str, object]:
    return {
        "annotatedTag": False,
        "commit": commit,
        "kind": kind,
        "manifestPath": manifest_path,
        "manifestSha256": manifest_sha256,
        "officialRepositoryUrl": f"https://github.com/{owner}/{repository}",
        "owner": owner,
        "parentChains": [],
        "path": path,
        "peeledCommit": commit,
        "repository": repository,
        "sourceTag": source_tag,
    }


def entrypoint(
    identity: str,
    owner: str,
    relationship: str,
    argv: list[str],
    gradle_home_role: str,
) -> dict[str, object]:
    return {
        "argv": argv,
        "gradleHomeRole": gradle_home_role,
        "id": identity,
        "owner": owner,
        "relationship": relationship,
        "sourceSha256": sha256_file(ROOT / owner),
    }


def github_release_redirect(
    *,
    initial_url: str,
    filename: str,
    final_path: str,
    archive_size: int,
) -> dict[str, object]:
    return {
        "finalHeaders": {
            "acceptRanges": "bytes",
            "contentLength": archive_size,
            "contentType": "application/octet-stream",
        },
        "finalHost": "release-assets.githubusercontent.com",
        "finalPath": final_path,
        "finalStatus": 200,
        "fixedQueryValues": {
            "response-content-disposition": f"attachment; filename={filename}",
            "response-content-type": "application/octet-stream",
            "rscd": f"attachment; filename={filename}",
            "rsct": "application/octet-stream",
            "sks": "b",
            "skv": "2018-11-09",
            "sp": "r",
            "spr": "https",
            "sr": "b",
            "sv": "2018-11-09",
        },
        "initialStatus": 302,
        "initialUrl": initial_url,
        "jwtLength": 303,
        "queryKeys": [
            "jwt",
            "response-content-disposition",
            "response-content-type",
            "rscd",
            "rsct",
            "se",
            "sig",
            "ske",
            "skoid",
            "sks",
            "skt",
            "sktid",
            "skv",
            "sp",
            "spr",
            "sr",
            "sv",
        ],
        "redirectCount": 1,
        "signatureLength": 44,
        "timestampKeys": ["skt", "se", "ske"],
        "uuidKeys": ["skoid", "sktid"],
    }


def android_repository_inventory() -> dict[str, object]:
    return {
        "absentCoordinates": ["platforms;android-37"],
        "acceptedRecord": {
            "archive": {
                "relativeUrl": "platform-37.0_r02.zip",
                "repositorySha1": "ed8ebf7f8822a4de5686d427f237d2fa30ff7410",
                "resolvedUrl": "https://dl.google.com/android/repository/platform-37.0_r02.zip",
                "size": 67281901,
            },
            "channel": "channel-0",
            "coordinate": "platforms;android-37.0",
            "displayName": "Android SDK Platform 37.0",
            "layoutlibApi": 15,
            "revisionMajor": 2,
            "typeKind": "platformDetailsType",
            "typeDetails": {
                "apiLevel": "37.0",
                "baseExtension": True,
                "codename": "",
                "extensionLevel": 22,
            },
        },
        "repositorySha256": "386d7b5b908d9b0b2c297b6cd62a7e50e7426d0d7992cc4bac03493545e069b5",
        "repositoryUrl": "https://dl.google.com/android/repository/repository2-3.xml",
    }


def evidence_entrypoints() -> list[dict[str, object]]:
    """Return the finite governed direct/nested Gradle-process inventory."""

    android = ".github/workflows/android.yml"
    device = ".github/workflows/device-evidence.yml"
    scheduled = ".github/workflows/mutation-schedule.yml"
    cli = "scripts/quality/verify_build_inputs.py"
    fixture = "build-logic/convention/src/test/kotlin/fixtures/GradlePluginTestProject.kt"
    adversarial = "build-logic/convention/src/test/kotlin/GradlePluginTestHarnessTest.kt"
    rows = [
        entrypoint("android/agent-contracts/contracts", android, "direct", ["scripts/agent/check-contracts.sh", "--ci"], "job-fresh"),
        entrypoint("agent/check-contracts/docs-bridge", "scripts/agent/check_contracts.py", "nested", ["python3", "scripts/quality/build_inputs/docs_gradle_validation_bridge.py", "--check-gradle-tasks"], "job-fresh"),
        entrypoint("android/static-analysis/quality", android, "direct", ["scripts/quality/build_inputs/run_gradle.sh", "spotlessCheck", ":app:lintDemoDebug", ":app:lintProdDebug", "lint", ":core:model:checkKotlinAbi", ":core:observability:checkKotlinAbi", ":domain:location:checkKotlinAbi", ":domain:settings:checkKotlinAbi", ":domain:station:checkKotlinAbi", "verifyPublicApiBoundaries", "verifyModuleBoundaries", "productionDependencyInventory", "verifyNoDeprecatedComposeTestApis", "verifyCiRobolectricRuntime", "-Pgasstation.lintTestSources=false", "--warning-mode", "fail", "--continue"], "job-fresh"),
        entrypoint("android/static-analysis/convention-testkit", android, "direct", ["scripts/quality/build_inputs/run_gradle.sh", ":build-logic:convention:test", "--warning-mode", "fail"], "job-fresh"),
        entrypoint("android/lint-tests/test-sources", android, "direct", ["scripts/quality/build_inputs/run_gradle.sh", ":app:lintDemoDebug", ":app:lintProdDebug", "lint", "-Pgasstation.lintTestSources=true", "--warning-mode", "fail", "--continue"], "job-fresh"),
        entrypoint("android/unit-tests/all", android, "direct", ["scripts/quality/build_inputs/run_gradle.sh", ":domain:location:test", ":core:model:test", ":domain:station:test", ":domain:settings:test", ":core:database:testDebugUnitTest", ":core:database:compileDebugAndroidTestKotlin", ":core:datastore:testDebugUnitTest", ":core:designsystem:testDebugUnitTest", ":core:location:testDebugUnitTest", ":core:network:test", ":core:observability:test", ":data:settings:testDebugUnitTest", ":data:station:testDebugUnitTest", ":feature:settings:testDebugUnitTest", ":feature:station-list:testDebugUnitTest", ":feature:watchlist:testDebugUnitTest", ":app:testDemoDebugUnitTest", ":app:testProdDebugUnitTest", ":app:compileDemoDebugAndroidTestKotlin", ":tools:demo-seed:test", "--warning-mode", "fail"], "job-fresh"),
        entrypoint("android/unit-tests/room-schema-child", android, "nested", ["scripts/agent/verify-room-schemas.sh"], "job-fresh"),
        entrypoint("android/screenshot-tests/roborazzi", android, "direct", ["scripts/quality/build_inputs/run_gradle.sh", "verifyRoborazziDebug", "--warning-mode", "fail"], "job-fresh"),
        entrypoint("android/assemble/demo", android, "direct", ["scripts/quality/build_inputs/run_gradle.sh", ":app:assembleDemoDebug", "--warning-mode", "fail"], "job-fresh"),
        entrypoint("android/assemble/prod", android, "direct", ["scripts/quality/build_inputs/run_gradle.sh", ":app:assembleProdDebug", "--warning-mode", "fail"], "job-fresh"),
        entrypoint("android/assemble/benchmark", android, "direct", ["scripts/quality/build_inputs/run_gradle.sh", ":benchmark:assemble", "--warning-mode", "fail"], "job-fresh"),
        entrypoint("android/release-assemble/prod-release", android, "direct", ["scripts/quality/build_inputs/run_gradle.sh", ":app:assembleProdRelease", "--warning-mode", "fail"], "job-fresh"),
        entrypoint("android/coverage/report-and-gate", android, "direct", ["scripts/quality/build_inputs/run_gradle.sh", "coverageXmlReport", "verifyCoverageReport", "-Pgasstation.coverageSourceCommit={GITHUB_SHA}", "-Pgasstation.coverageEvent={GASSTATION_COVERAGE_EVENT}", "-Pgasstation.coverageBaseRef={GASSTATION_COVERAGE_BASE_REF}", "--warning-mode", "fail"], "job-fresh"),
        entrypoint("android/mutation/pull-request", android, "nested", ["scripts/quality/run_pitest.sh", "--event", "pull-request", "--base", "{pull_request.base.sha}", "--java-home-file", "build/quality/pitest-runtime/bootstrap/java-home.selector"], "job-fresh"),
        entrypoint("android/mutation/main", android, "nested", ["scripts/quality/run_pitest.sh", "--event", "main", "--java-home-file", "build/quality/pitest-runtime/bootstrap/java-home.selector"], "job-fresh"),
        entrypoint("android/mutation/tag", android, "nested", ["scripts/quality/run_pitest.sh", "--event", "tag", "--java-home-file", "build/quality/pitest-runtime/bootstrap/java-home.selector"], "job-fresh"),
        entrypoint("mutation-scheduled/mutation", scheduled, "nested", ["scripts/quality/run_pitest.sh", "--event", "schedule", "--java-home-file", "build/quality/pitest-runtime/bootstrap/java-home.selector"], "job-fresh"),
        entrypoint("device-pr-api28/gmd", device, "nested", ["scripts/quality/device/run_gmd_lane.sh", "--lane", "api28-pr-smoke"], "job-fresh"),
        entrypoint("device-scheduled-api24/connected", device, "nested", ["scripts/quality/device/run_api24_avd.sh", "--lane", "api24-scheduled"], "job-fresh"),
        entrypoint("device-scheduled-api28/gmd", device, "nested", ["scripts/quality/device/run_gmd_lane.sh", "--lane", "api28-scheduled"], "job-fresh"),
        entrypoint("device-scheduled-api36/gmd", device, "nested", ["scripts/quality/device/run_gmd_lane.sh", "--lane", "api36-scheduled"], "job-fresh"),
        entrypoint("cli/metadata-capture", cli, "direct", ["verify_build_inputs.py", "metadata-capture", "--policy", "config/quality/build-inputs.json"], "command-fresh"),
        entrypoint("cli/strict-matrix/complete", cli, "direct", ["verify_build_inputs.py", "strict-matrix", "--policy", "config/quality/build-inputs.json", "--group", "complete"], "command-fresh"),
        entrypoint("cli/strict-matrix/product-regressions", cli, "nested", ["verify_build_inputs.py", "strict-matrix", "--policy", "config/quality/build-inputs.json", "--group", "product-regressions"], "command-fresh"),
        entrypoint("cli/configuration-cache", cli, "direct", ["verify_build_inputs.py", "configuration-cache", "--policy", "config/quality/build-inputs.json"], "command-fresh"),
        entrypoint("cli/reproduce/copy-a", "scripts/quality/build_inputs/reproducibility.py", "nested", ["./gradlew", ":app:assembleProdRelease", "--no-build-cache", "--no-configuration-cache", "--rerun-tasks"], "reproduce-a"),
        entrypoint("cli/reproduce/copy-b", "scripts/quality/build_inputs/reproducibility.py", "nested", ["./gradlew", ":app:assembleProdRelease", "--no-build-cache", "--no-configuration-cache", "--rerun-tasks"], "reproduce-b"),
        entrypoint("cli/release-bind", cli, "receipt-consumer", ["verify_build_inputs.py", "release-bind", "--policy", "config/quality/build-inputs.json", "--receipt", "{receipt}", "--apk", "{apk}"], "none"),
        entrypoint("cli/evidence-session", cli, "nested", ["verify_build_inputs.py", "evidence-session", "--policy", "config/quality/build-inputs.json", "--", "{allowlisted-command}"], "command-fresh"),
        entrypoint("local-colima/aggregate", "scripts/quality/build_inputs/local_colima_evidence.py", "nested", ["python3", "scripts/quality/build_inputs/local_colima_evidence.py", "--policy", "config/quality/build-inputs.json", "--source-commit", "{sourceCommit}"], "command-fresh"),
        entrypoint("local-colima/third-release", "scripts/quality/build_inputs/local_colima_evidence.py", "nested", ["./gradlew", ":app:assembleProdRelease", "--no-build-cache", "--no-configuration-cache", "--rerun-tasks", "--project-cache-dir", "{thirdProjectCache}", "--dependency-verification", "strict", "-Dorg.gradle.java.installations.auto-detect=false", "-Dorg.gradle.java.installations.auto-download=false", "-Dorg.gradle.java.installations.paths={compileHome},{runtimeHome}"], "command-fresh"),
        entrypoint("testkit/shared-normal", fixture, "nested", ["GradleRunner.withArguments", "{fixture-arguments}", "--dependency-verification=strict"], "testkit-fresh"),
        entrypoint("testkit/shared-configuration-cache", fixture, "nested", ["GradleRunner.withArguments", "{fixture-arguments}", "--configuration-cache", "--configuration-cache-problems=fail", "--dependency-verification=strict"], "testkit-fresh"),
        entrypoint("testkit/adversarial", adversarial, "nested", ["GradleRunner.withArguments", "help", "--dependency-verification=strict"], "testkit-adversarial-fresh"),
    ]
    return sorted(rows, key=lambda row: row["id"])


def docs_parent_edges() -> list[str]:
    """Return every governed parent chain that may reach the stable docs bridge."""

    return [
        ".github/workflows/android.yml agent-contracts -> check_contracts -> bridge",
        "scripts/agent/check_contracts.py -> bridge",
        "scripts/agent/verify.sh auto -> check_contracts -> bridge",
        "scripts/agent/verify.sh docs -> check_contracts -> bridge",
        "verify_build_inputs.py evidence-session -> bridge",
        "verify_build_inputs.py strict-matrix complete -> bridge",
    ]


def policy() -> dict[str, object]:
    generation_matrix = [
        ["./gradlew", "help"],
        [
            "./gradlew",
            ":build-logic:convention:captureTestKitDependencyVerificationMetadata",
            ":build-logic:convention:test",
        ],
        ["./gradlew", "spotlessCheck", ":app:lintDemoDebug", ":app:lintProdDebug", "lint", "-Pgasstation.lintTestSources=false", "--continue"],
        ["./gradlew", ":app:lintDemoDebug", ":app:lintProdDebug", "lint", "-Pgasstation.lintTestSources=true", "--continue"],
        ["./gradlew", ":domain:location:test", ":core:model:test", ":domain:station:test", ":domain:settings:test", ":core:database:testDebugUnitTest", ":core:database:compileDebugAndroidTestKotlin", ":core:datastore:testDebugUnitTest", ":core:designsystem:testDebugUnitTest", ":core:location:testDebugUnitTest", ":core:network:test", ":core:observability:test", ":data:settings:testDebugUnitTest", ":data:station:testDebugUnitTest", ":feature:settings:testDebugUnitTest", ":feature:station-list:testDebugUnitTest", ":feature:watchlist:testDebugUnitTest", ":app:testDemoDebugUnitTest", ":app:testProdDebugUnitTest", ":app:compileDemoDebugAndroidTestKotlin", ":tools:demo-seed:test"],
        ["./gradlew", ":core:database:kspDebugKotlin", "--rerun-tasks", "--no-build-cache"],
        ["./gradlew", "verifyRoborazziDebug"],
        ["./gradlew", ":app:assembleDemoDebug", ":app:assembleProdDebug", ":benchmark:assemble", ":app:assembleProdRelease"],
        ["./gradlew", "coverageXmlReport", "verifyCoverageReport", "-Pgasstation.coverageSourceCommit={sourceCommit}", "-Pgasstation.coverageEvent=local"],
        ["./gradlew", ":core:model:checkKotlinAbi", ":core:observability:checkKotlinAbi", ":domain:location:checkKotlinAbi", ":domain:settings:checkKotlinAbi", ":domain:station:checkKotlinAbi", "verifyPublicApiBoundaries", "verifyModuleBoundaries", "productionDependencyInventory", "verifyNoDeprecatedComposeTestApis", "verifyCiRobolectricRuntime"],
        ["./gradlew", "verifyPitestConfiguration", ":domain:station:pitestVerified", ":domain:location:pitestVerified", ":domain:settings:pitestVerified"],
        ["./gradlew", ":app:compileDemoDebugAndroidTestKotlin", ":core:database:compileDebugAndroidTestKotlin", ":core:location:compileDebugAndroidTestKotlin", "tasks", "--all"],
    ]
    actions = [
        action("actions", "checkout", "", "3d3c42e5aac5ba805825da76410c181273ba90b1", "v7", "action.yml", "d59219cb79590abdb877deaa14e3b65a00c05318bf5a6f3b989b9162b5d08c35", "node24"),
        action("actions", "download-artifact", "", "3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c", "v8", "action.yml", "e98559b7a31ba31be4709f20d22102dc2737fa630f69a339eb89981151e505fe", "node24"),
        action("actions", "upload-artifact", "", "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a", "v7", "action.yml", "c5979822866a72362e609844b6ebe77d4b7e759af68cc1c2c425dcf51481fab4", "node24"),
        action("codecov", "codecov-action", "", "fb8b3582c8e4def4969c97caa2f19720cb33a72f", "v7", "action.yml", "4577833b9005e94bc4bd8fa9489badc1295ead0f8433b09f987bcd0e33015e79", "composite"),
        action("gradle", "actions", "setup-gradle", "9c971963bec38e04b3d30dcc455b5382be2fdbfb", "v6", "setup-gradle/action.yml", "2349387452e549e19d18088c56dbde623896688ec624338cac09c1adfc754f45", "node24"),
    ]
    closure = actions + [
        action("actions", "github-script", "", "ed597411d8f924073f98dfc5c65a23a2325f34cd", "v8", "action.yml", "2155c7b84863afcfe81a73ab8eafcb2c2f304a995cbe282c31617aa847dff1d8", "node24"),
    ]
    closure[-1]["parentChains"] = [
        ["codecov/codecov-action@fb8b3582c8e4def4969c97caa2f19720cb33a72f"],
    ]
    return {
        "actions": {
            "transitiveUses": sorted(closure[-1:], key=lambda row: (row["owner"], row["repository"], row["path"])),
            "workflowUses": sorted(actions, key=lambda row: (row["owner"], row["repository"], row["path"])),
        },
        "android": {
            "buildTools": "36.0.0",
            "compileSdk": 37,
            "installedInventory": {
                "packageXmlFiles": [
                    {"coordinate": "build-tools;36.0.0", "mode": "0644", "ownerRole": "build-tools;36.0.0", "relativePath": "build-tools/36.0.0/package.xml"},
                    {"coordinate": "platform-tools", "mode": "0644", "ownerRole": "platform-tools", "relativePath": "platform-tools/package.xml"},
                    {"coordinate": "platforms;android-37.0", "mode": "0644", "ownerRole": "platforms;android-37.0", "relativePath": "platforms/android-37.0/package.xml"},
                ],
                "selectedBinaries": [
                    {"mode": "0755", "ownerRole": "build-tools;36.0.0", "relativePath": "build-tools/36.0.0/aapt2"},
                    {"mode": "0755", "ownerRole": "build-tools;36.0.0", "relativePath": "build-tools/36.0.0/apksigner"},
                    {"mode": "0755", "ownerRole": "build-tools;36.0.0", "relativePath": "build-tools/36.0.0/zipalign"},
                    {"mode": "0755", "ownerRole": "command-line-tools-archive", "relativePath": "cmdline-tools/latest/bin/avdmanager"},
                    {"mode": "0755", "ownerRole": "command-line-tools-archive", "relativePath": "cmdline-tools/latest/bin/sdkmanager"},
                    {"mode": "0755", "ownerRole": "platform-tools", "relativePath": "platform-tools/adb"},
                ],
            },
            "minSdk": 24,
            "packages": [
                {"coordinate": "build-tools;36.0.0", "revision": "36.0.0", "runtimeEvidence": "NOT RUN"},
                {"coordinate": "cmdline-tools;latest", "revision": "NOT RUN", "runtimeEvidence": "NOT RUN"},
                {"coordinate": "emulator", "revision": "NOT RUN", "runtimeEvidence": "NOT RUN"},
                {"coordinate": "platform-tools", "revision": "NOT RUN", "runtimeEvidence": "NOT RUN"},
                {"coordinate": "platforms;android-37.0", "revision": "2", "runtimeEvidence": "NOT RUN"},
                {"coordinate": "system-images;android-24;google_apis;x86_64", "logicalIdentity": "system-images;android-24;google_apis;x86_64", "revision": "27", "runtimeEvidence": "NOT RUN"},
                {"coordinate": "system-images;android-28;default;x86_64", "logicalIdentity": "system-images;android-28;aosp;x86_64", "revision": "4", "runtimeEvidence": "NOT RUN"},
                {"coordinate": "system-images;android-36;google_apis;x86_64", "logicalIdentity": "system-images;android-36;google;x86_64", "revision": "7", "runtimeEvidence": "NOT RUN"},
            ],
            "repositoryInventory": android_repository_inventory(),
            "targetSdk": 36,
        },
        "codecovCli": {
            "architecture": "x64",
            "binarySha256": "ca1d64196d2d34771084afe76ea657d581bf628e31d993ff8e52ea09cc88a56d",
            "binarySize": 10402464,
            "binaryUrl": "https://github.com/codecov/codecov-cli/releases/download/v11.3.1/codecovcli_linux",
            "os": "Linux",
            "version": "11.3.1",
        },
        "dependencyVerification": {
            "allowedInitScripts": [],
            "bypassDenylist": [
                "--dependency-verification " + "off|lenient",
                "-Dorg.gradle.dependency.verification=" + "off|lenient",
                "-" + "I|--init-" + "script",
                "ResolutionStrategy.disableDependency" + "Verification()",
                "disableDependency" + "Verification()",
            ],
            "checksumAlgorithms": ["sha256"],
            "configurationCache": [
                ["./gradlew", "verifyModuleBoundaries", "verifyPublicApiBoundaries", "verifyPitestConfiguration"],
                ["./gradlew", ":app:assembleProdRelease"],
            ],
            "generationMatrix": generation_matrix,
            "metadataPath": "gradle/verification-metadata.xml",
            "mode": "strict",
            "nestedTestKit": {
                "copyRootMetadata": True,
                "freshGradleHome": True,
                "rejectCallerOverrides": True,
                "sanitizedEnvironment": True,
            },
            "offlineRepresentative": ["./gradlew", "help"],
            "strictGroups": {
                "complete": [*generation_matrix, ["python3", "scripts/quality/build_inputs/docs_gradle_validation_bridge.py", "--check-gradle-tasks"]],
                "product-regressions": [["scripts/agent/test.sh"], ["scripts/agent/verify.sh", "auto"]],
            },
            "verifyMetadata": True,
        },
        "docsValidation": {
            "aggregateAlgorithm": "sha256(canonical-json(manifest)+LF)",
            "argv": ["python3", "scripts/quality/build_inputs/docs_gradle_validation_bridge.py", "--check-gradle-tasks"],
            "bridgeSha256": sha256_file(ROOT / "scripts/quality/build_inputs/docs_gradle_validation_bridge.py"),
            "bridgePath": "scripts/quality/build_inputs/docs_gradle_validation_bridge.py",
            "excludedRoots": ["scripts/docs/__pycache__", "scripts/docs/tests"],
            "facadeCallable": "validate_repository(root: pathlib.Path, *, discovered_gradle_tasks: frozenset[str] | None) -> list[str]",
            "facadePath": "scripts/docs/validate.py",
            "forbiddenRepositoryImportRoots": ["scripts/agent", "scripts/quality"],
            "loadedModuleRoots": ["scripts/docs"],
            "parentEdges": docs_parent_edges(),
            "receiptPath": "build/reports/build-inputs/docs-gradle-validation.json",
            "receiptSchemaVersion": 1,
            "sourceRoots": ["scripts/docs/extensions"],
        },
        "evidence": {
            "artifactName": "build-input-evidence-{sourceSha}-{runAttempt}",
            "fieldAllowlist": ["android", "attempt", "dependencyVerification", "evidenceFiles", "gradle", "jdks", "policySha256", "runner", "schemaVersion", "sourceCommit", "wrapper"],
            "receiptPath": "build/reports/build-inputs/build-input-receipt.json",
            "reproducibilityPath": "build/reports/build-inputs/reproducible-build.json",
            "retentionDays": 7,
            "root": "build/reports/build-inputs",
            "schemaVersion": 1,
        },
        "evidenceGradleEntrypoints": evidence_entrypoints(),
        "evidenceSessionCommands": sorted([
            ["scripts/agent/verify-room-schemas.sh"],
            ["scripts/agent/verify.sh", "auto"],
            ["scripts/agent/verify.sh", "docs"],
            ["python3", "scripts/quality/build_inputs/docs_gradle_validation_bridge.py", "--check-gradle-tasks"],
        ]),
        "gradleWrapper": {
            "distributionSha256": "9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14",
            "distributionUrl": "https://services.gradle.org/distributions/gradle-9.6.1-bin.zip",
            "networkTimeout": 10000,
            "retries": 0,
            "retryBackOffMs": 500,
            "validateDistributionUrl": True,
            "version": "9.6.1",
            "wrapperJarPath": "gradle/wrapper/gradle-wrapper.jar",
            "wrapperJarSha256": "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7",
        },
        "jdks": {
            "compile": {
                "architecture": "x64",
                "archiveRoot": "jdk-17.0.20+8",
                "archiveSha256": "be7668bc030d578b83d6d5ef9221d6d6729bbbca8cf94a7d52e16ac68b5a5a35",
                "archiveSize": 193273593,
                "archiveUrl": "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20%2B8/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20_8.tar.gz",
                "checksumUrl": "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20%2B8/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20_8.tar.gz.sha256.txt",
                "filename": "OpenJDK17U-jdk_x64_linux_hotspot_17.0.20_8.tar.gz",
                "releaseAssetRedirect": github_release_redirect(
                    initial_url="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20%2B8/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20_8.tar.gz",
                    filename="OpenJDK17U-jdk_x64_linux_hotspot_17.0.20_8.tar.gz",
                    final_path="/github-production-release-asset/372925194/fa1e0dc6-b748-4eaf-8e2d-0c47f9a31ffa",
                    archive_size=193273593,
                ),
                "major": 17,
                "os": "Linux",
                "packageType": "JDK",
                "vendor": "Eclipse Temurin",
                "version": "17.0.20+8",
                "vm": "HotSpot",
            },
            "runtime": {
                "architecture": "x64",
                "archiveRoot": "jdk-21.0.12.1+1",
                "archiveSha256": "ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94",
                "archiveSize": 207473347,
                "archiveUrl": "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz",
                "checksumUrl": "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz.sha256.txt",
                "filename": "OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz",
                "releaseAssetRedirect": github_release_redirect(
                    initial_url="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz",
                    filename="OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz",
                    final_path="/github-production-release-asset/602574963/be5ef440-7bad-40e3-9188-9e7648842040",
                    archive_size=207473347,
                ),
                "major": 21,
                "os": "Linux",
                "packageType": "JDK",
                "vendor": "Eclipse Temurin",
                "version": "21.0.12.1+1",
                "vm": "HotSpot",
            },
        },
        "localEvidenceHost": {
            "aggregateReceiptPath": "build/reports/build-inputs/local-linux-evidence-package.json",
            "allowedHostEnvironment": ["COLIMA_HOME", "DOCKER_CONFIG", "HOME", "LANG", "LC_ALL", "PATH", "TZ"],
            "attemptPattern": "attempt-[0-9]{6}",
            "bootstrapPackages": [
                {"archiveSha256": "6bac2a01979e210d9eac1d4d56747ec709ea60654744d66705dc3c36e7629e50", "archiveSize": 139430, "name": "ca-certificates", "version": "20260601~24.04.1"},
                {"archiveSha256": "dd809918a149964c9d248662a6937082ca46f8ed76bd6d875928566035e0342f", "archiveSize": 226504, "name": "curl", "version": "8.5.0-2ubuntu10.12"},
                {"archiveSha256": "099bb129f543adc4c14203334b0fa0a909f8bf038c4d56bc9cc7c774ebf78f87", "archiveSize": 3679758, "name": "git", "version": "1:2.43.0-1ubuntu7.3"},
                {"archiveSha256": "cdd2d347a357da6b9b1f2bd9e08c10a2a3a4686fad050791d30915d0ce0bb506", "archiveSize": 4231022, "name": "locales", "version": "2.39-0ubuntu8.8"},
                {"archiveSha256": "e691b9cc40841c41bbdc50bd794c876cb1b1801306ea27b06e9a1458180df1e9", "archiveSize": 23004, "name": "python3", "version": "3.12.3-0ubuntu2.1"},
                {"archiveSha256": "a505b9d491386167bd8e14e3383315a4a7d6539e4406745901ccf009a7988271", "archiveSize": 174454, "name": "unzip", "version": "6.0-28ubuntu4.1"},
                {"archiveSha256": "778edae086bc8f34d80f36f301bc8fb3eff2d906c146dfb533ea6840b6d64e00", "archiveSize": 267424, "name": "xz-utils", "version": "5.6.1+really5.4.5-1ubuntu0.3"},
            ],
            "cleanupPhases": list(CLEANUP_PHASES),
            "commandLineTools": {
                "archiveMemberCount": 141,
                "archiveMemberListingSha256": "b51105b72a8345fb59f33bbfeb72644d1ffc5f144349f95c501b667c68c56cb0",
                "archiveSha256": "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583",
                "archiveSize": 181833628,
                "archiveUrl": "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip",
                "sourceProperties": {
                    "coordinate": "cmdline-tools;22.0",
                    "fields": [
                        "Pkg.Revision=22.0",
                        "Pkg.Path=cmdline-tools;22.0",
                        "Pkg.Desc=Android SDK Command-line Tools",
                    ],
                    "mode": "0644",
                    "relativePath": "cmdline-tools/latest/source.properties",
                    "sha256": "166bcdfe54f73296b09e5e6aa6d96b9a752b78b418c56e9f3f3a13c15fac74e5",
                    "size": 86,
                    "storedMode": "100755",
                },
            },
            "container": CONTAINER,
            "context": CONTEXT,
            "deleteArgv": list(DELETE_ARGV),
            "dockerClient": {"path": "/opt/homebrew/bin/docker", "version": "29.4.0"},
            "effectiveConfig": {
                "arch": "aarch64",
                "autoActivate": False,
                "binfmt": False,
                "cpu": 8,
                "cpuType": "",
                "disk": 120,
                "diskImage": "",
                "docker": {},
                "env": {},
                "forwardAgent": False,
                "hostname": "",
                "kubernetes": {
                    "enabled": False,
                    "k3sArgs": ["--disable=traefik"],
                    "port": 0,
                    "version": "v1.35.0+k3s1",
                },
                "memory": 16,
                "modelRunner": "docker",
                "mountInotify": True,
                "mountType": "virtiofs",
                "mounts": None,
                "nestedVirtualization": False,
                "network": {
                    "address": False,
                    "dns": None,
                    "dnsHosts": {},
                    "gatewayAddress": "192.168.5.2",
                    "hostAddresses": False,
                    "interface": "en0",
                    "mode": "shared",
                    "preferredRoute": False,
                },
                "portForwarder": "ssh",
                "provision": None,
                "rootDisk": 40,
                "rosetta": True,
                "runtime": "docker",
                "sshConfig": False,
                "sshPort": 0,
                "vmType": "vz",
            },
            "hostMounts": [],
            "image": {
                "configDescriptor": CONFIG_DESCRIPTOR,
                "containerSelection": {
                    "configImage": IMAGE,
                    "image": INDEX_DESCRIPTOR["digest"],
                    "inheritedLabels": CONTAINER_INHERITED_LABELS,
                    "platform": "linux",
                },
                "indexDescriptor": INDEX_DESCRIPTOR,
                "indexReference": IMAGE,
                "layerDescriptors": list(LAYER_DESCRIPTORS),
                "platform": "linux/amd64",
                "selectedManifestDescriptor": SELECTED_MANIFEST_DESCRIPTOR,
                "storeObservation": STORE_OBSERVATION,
            },
            "localHostReceiptPath": "build/reports/build-inputs/local-linux-host.json",
            "mainBaseCommit": MAIN_BASE_COMMIT,
            "mainBaseRef": MAIN_BASE_REF,
            "ownedLabelKeys": list(OWNED_LABEL_KEYS),
            "profile": PROFILE,
            "requiredEvidenceRows": sorted(REQUIRED_EVIDENCE_ROWS),
            "sourceBundleRefs": ["HEAD", MAIN_BASE_REF],
            "startArgv": list(START_ARGV),
            "stopArgv": list(STOP_ARGV),
            "toolVersions": {
                "colima": "0.10.1",
                "dockerClient": "29.4.0",
                "dockerServer": "29.2.1",
                "lima": "2.1.1",
            },
            "transport": {
                "guestArchitecture": "aarch64",
                "innerArchitecture": "amd64",
                "qemuBinfmt": False,
                "rosetta": True,
                "vmType": "vz",
            },
            "volumes": list(VOLUMES),
        },
        "reproducibleArtifact": {
            "artifactName": "reproducible-prod-release-receipt-{sourceSha}",
            "buildCache": False,
            "configurationCache": False,
            "gradleHomes": 2,
            "outputGlob": "app/build/outputs/apk/prod/release/*.apk",
            "outputIdentity": "prod-release-unsigned.apk",
            "requiredCardinality": 1,
            "receiptPath": "build/reports/build-inputs/reproducible-prod-release-receipt.json",
            "receiptSchemaVersion": 1,
            "signingSecretsAllowed": False,
            "sourceCopies": 2,
            "strictDependencyVerification": True,
            "task": ":app:assembleProdRelease",
            "unsigned": True,
        },
        "runner": {"architecture": "x64", "label": "ubuntu-24.04", "mutableHostedImage": True, "os": "Linux"},
        "schemaVersion": 1,
        "staticSourceHashes": [
            {"path": path, "sha256": sha256_file(ROOT / path)} for path in STATIC_SOURCES
        ],
    }


def main() -> int:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(canonical_json_bytes(policy()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
