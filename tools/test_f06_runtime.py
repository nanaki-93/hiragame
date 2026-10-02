"""Static navigation contract for the F06 Topics route (full execution at final gate)."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGES = ROOT / 'site/src/jsMain/kotlin/com/github/nanaki_93/pages'


class TopicsNavigationTests(unittest.TestCase):
    def test_logical_route_and_framework_aware_links(self):
        home = (PAGES / 'Index.kt').read_text(encoding='utf-8')
        topics = (PAGES / 'Topics.kt').read_text(encoding='utf-8')
        config = (ROOT / 'site/build.gradle.kts').read_text(encoding='utf-8')
        self.assertIn('configAsKobwebApplication("hiragame")', config)
        self.assertRegex(topics, r'@Page\("/topics"\)\s*@Composable\s*fun TopicsPage\(')
        for source, destination in ((home, '/topics'), (topics, '/')):
            self.assertIn('import com.varabyte.kobweb.silk.components.navigation.Link', source)
            self.assertRegex(source, rf'Link\(path\s*=\s*"{re.escape(destination)}"\)')
            # Kobweb Link prepends the configured base path. Never prepend it a second time.
            self.assertNotIn('/hiragame', source)
        # Navigation must precede practice state branching, not disappear in empty/error/loading.
        self.assertLess(home.index('Link(path = "/topics")'), home.index('when (val current = state)'))


if __name__ == '__main__':
    unittest.main()
