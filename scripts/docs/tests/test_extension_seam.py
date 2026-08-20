from __future__ import annotations

import importlib.util
import inspect
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]


def load_validator(path: Path):
    spec = importlib.util.spec_from_file_location("gasstation_docs_validator", path)
    if spec is None or spec.loader is None:
        raise AssertionError("validator module cannot be loaded")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class DocumentationExtensionSeamTest(unittest.TestCase):
    def test_public_facade_signature_is_fixed_and_pure_for_supplied_tasks(self) -> None:
        validator = load_validator(ROOT / "scripts/docs/validate.py")

        signature = inspect.signature(validator.validate_repository)
        self.assertEqual(
            "(root: 'Path', *, discovered_gradle_tasks: 'frozenset[str] | None') -> 'list[str]'",
            str(signature),
        )
        issues = validator.validate_repository(
            ROOT,
            discovered_gradle_tasks=frozenset(validator.canonical_gradle_tasks(
                {
                    path: (ROOT / path).read_text(encoding="utf-8")
                    for path in validator.EXPECTED_LIVE_PATHS
                    if (ROOT / path).is_file() and path.endswith(".md")
                }
            )),
        )
        self.assertIsInstance(issues, list)

    def test_sorted_extensions_execute_once_in_default_and_discovered_task_modes(self) -> None:
        validator = load_validator(ROOT / "scripts/docs/validate.py")
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            (fixture / "scripts/docs/extensions").mkdir(parents=True)
            (fixture / "scripts/docs/extensions/a.py").write_text(
                "def validate(root, discovered_gradle_tasks):\n"
                "    return ['a:' + ('tasks' if discovered_gradle_tasks is not None else 'default')]\n",
                encoding="utf-8",
            )
            (fixture / "scripts/docs/extensions/b.py").write_text(
                "def validate(root, discovered_gradle_tasks):\n"
                "    return ['b:' + ('tasks' if discovered_gradle_tasks is not None else 'default')]\n",
                encoding="utf-8",
            )

            default = validator.run_extensions(fixture, discovered_gradle_tasks=None)
            loaded_default = [
                name for name in sys.modules if name.startswith("gasstation_docs_extension_")
            ]
            task_mode = validator.run_extensions(fixture, discovered_gradle_tasks=frozenset({"help"}))
            loaded_task_mode = [
                name for name in sys.modules if name.startswith("gasstation_docs_extension_")
            ]

        self.assertEqual(["a:default", "b:default"], default)
        self.assertEqual(["a:tasks", "b:tasks"], task_mode)
        self.assertEqual(loaded_default, loaded_task_mode)
        self.assertEqual(2, len(loaded_task_mode))


if __name__ == "__main__":
    unittest.main()
