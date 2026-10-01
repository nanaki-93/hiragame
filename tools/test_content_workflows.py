"""Static checks for content and in-job deployment gates (no CI YAML dependency)."""

import re
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).resolve().parent.parent / ".github/workflows/content-check.yml"
DEPLOY_WORKFLOW = WORKFLOW.with_name("firebase-deploy.yml")
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


class FirebaseDeployWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = DEPLOY_WORKFLOW.read_text(encoding="utf-8")
        # All assertions below operate on the existing deploy job, not a separate job
        # that could run asynchronously or be skipped by the deploy job.
        cls.job = cls.source.split("jobs:\n  deploy:\n", 1)[1]
        cls.steps = re.split(r"(?m)^      - name: ", cls.job)[1:]

    def test_push_paths_cover_content_tools_sources_and_workflows(self):
        trigger = self.source.split("jobs:\n", 1)[0]
        self.assertIn("on:\n  push:\n    branches:\n      - main\n    paths:\n", trigger)
        paths = re.findall(r"(?m)^      - '([^']+)'$", trigger)
        self.assertEqual(set(paths), {
            "site/**", "shared/**", "tools/**", "content-source/**",
            "gradle/**", "gradlew", "gradle.properties", "build.gradle.kts",
            "settings.gradle.kts", ".github/workflows/firebase-deploy.yml",
            ".github/workflows/content-check.yml", "firebase.json",
        })
        self.assertEqual(len(paths), len(set(paths)))
        self.assertNotRegex(trigger, r"(?m)^\s*(?:paths-ignore|branches-ignore):")

    def test_gates_run_in_deploy_job_before_build_and_deployment(self):
        names = [step.splitlines()[0] for step in self.steps]
        self.assertEqual(names, [
            "Checkout code", "Make gradlew executable", "Set up Python 3.11",
            "Set up JDK 21", "Setup Gradle", "Test content tooling and workflow gates",
            "Validate reviewed canonical content", "Test shared content on Node",
            "Build project", "Deploy to Firebase",
        ])
        self.assertIn("uses: actions/setup-python@v5", self.steps[2])
        self.assertIn("python-version: '3.11'", self.steps[2])
        self.assertIn("uses: actions/setup-java@v5", self.steps[3])
        self.assertIn("java-version: '21'", self.steps[3])
        self.assertIn("uses: gradle/actions/setup-gradle@v5", self.steps[4])
        for step, command in zip(self.steps[5:9], EXPECTED_RUNS[2:] + ["./gradlew site:build"]):
            self.assertEqual(re.findall(r"(?m)^        run: (.+)$", step), [command])
        self.assertIn("uses: FirebaseExtended/action-hosting-deploy@v0", self.steps[9])
        self.assertIn("repoToken: '${{ secrets.GITHUB_TOKEN }}'", self.steps[9])
        self.assertIn("firebaseServiceAccount: '${{ secrets.FIREBASE_TOKEN }}'", self.steps[9])
        self.assertIn("channelId: live", self.steps[9])
        self.assertIn("projectId: hiragame", self.steps[9])

    def test_no_permissive_gates_or_shell_failure_masking(self):
        self.assertNotRegex(self.job, r"(?m)^\s*(?:if|continue-on-error|needs|strategy):")
        self.assertNotRegex(self.job, r"(?m)^\s*(?:run|shell):\s*[|>]\s*$")
        runs = re.findall(r"(?m)^        run: (.+)$", self.job)
        self.assertEqual(len(runs), 5)  # chmod, three gates, build
        for run in runs:
            self.assertNotRegex(run, r"\|\||&&|;|\$\{|\$\(|`")
        self.assertNotRegex(self.job, r"(?m)^\s*continue-on-error\s*:")


if __name__ == "__main__":
    unittest.main()
