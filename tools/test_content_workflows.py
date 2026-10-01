"""Static checks for the secret-free PR/push content gate (no CI YAML dependency)."""

import re
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).resolve().parent.parent / ".github/workflows/content-check.yml"
EXPECTED_RUNS = [
    "cmp backend/migration/question.csv content-source/legacy/question.csv",
    "python3 tools/convert_legacy_content.py --input content-source/legacy/question.csv --topic-map content-source/topic-map.json --output content-source/drafts/legacy",
    "python3 -m unittest discover -s tools -p 'test_*content*.py'",
    "python3 tools/validate_content.py site/src/jsMain/resources/public/content --review-root content-source/review-notes",
    "./gradlew :shared:jsNodeTest",
]
EXPECTED_ACTIONS = [
    "actions/checkout@v5",
    "actions/setup-python@v5",
    "actions/setup-java@v5",
    "gradle/actions/setup-gradle@v5",
]


class ContentWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = WORKFLOW.read_text(encoding="utf-8")
        cls.lines = [line for line in cls.source.splitlines()
                     if line.strip() and not line.lstrip().startswith("#")]

    def test_unrestricted_push_and_pull_request(self):
        self.assertIn("on: [push, pull_request]", self.lines)
        self.assertEqual([line for line in self.lines if line.startswith("on:")],
                         ["on: [push, pull_request]"])
        self.assertNotRegex(self.source, r"(?m)^\s*(?:paths|paths-ignore|branches|branches-ignore|pull_request_target):")
        self.assertIn("  contents: read", self.lines)
        self.assertEqual([line for line in self.lines if re.fullmatch(r"  \w+:\s*", line)],
                         ["  content:"])
        self.assertIn("    runs-on: ubuntu-latest", self.lines)

    def test_python_java_and_gradle_managed_node(self):
        actions = [line.strip().removeprefix("uses: ") for line in self.lines
                   if line.strip().startswith("uses: ")]
        self.assertEqual(actions, EXPECTED_ACTIONS)
        self.assertIn("          python-version: '3.11'", self.lines)
        self.assertIn("          distribution: temurin", self.lines)
        self.assertIn("          java-version: '21'", self.lines)
        # Node is configured by shared/build.gradle.kts, not a browser or external service.
        shared_build = WORKFLOW.parents[2] / "shared/build.gradle.kts"
        self.assertIn("nodejs()", shared_build.read_text(encoding="utf-8"))

    def test_all_checks_run_in_order_with_fail_closed_shell_steps(self):
        runs = [line.strip().removeprefix("run: ") for line in self.lines
                if line.strip().startswith("run:")]
        self.assertEqual(runs, EXPECTED_RUNS)
        # Reject conditional/permissive YAML and shell escapes that could bypass a failure.
        self.assertNotRegex(self.source, r"(?m)^\s*(?:if|continue-on-error|needs|strategy|working-directory):")
        self.assertNotRegex("\n".join(self.lines), r"(?i)\b(?:secrets|firebase|deploy|setup-node|docker|browser)\b")
        for run in runs:
            self.assertNotRegex(run, r"\|\||&&|;|\$\{|\$\(|`|\n")


if __name__ == "__main__":
    unittest.main()
