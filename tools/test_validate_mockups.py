"""Temporary-tree regression tests for the read-only mockup validator."""

from contextlib import redirect_stdout
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from validate_mockups import main, required_artifacts, validate_tree


HTML = '''<!doctype html>
<html lang="en"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Mockup test</title>
{head}
</head><body>{body}</body></html>
'''


class ValidatorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "mockups"
        self.root.mkdir()
        self.put("adoption-report.md", "Inventory only.\n")

    def put(self, name, content="asset"):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def html(self, name="index.html", body="", head=""):
        return self.put(name, HTML.format(head=head, body=body))

    def errors(self, stage="inventory"):
        return validate_tree(self.root, stage)

    def assert_error(self, expected, stage="inventory"):
        errors = self.errors(stage)
        self.assertTrue(any(expected in error for error in errors), errors)
        return errors

    def test_inventory_only_passes_early_stage_but_not_default(self):
        self.assertEqual(self.errors(), [])
        errors = validate_tree(self.root)
        self.assertTrue(any("design-system/palette.html" in error for error in errors))
        self.assertTrue(any("flows/backup-restore/03-result.html" in error for error in errors))

    def test_all_stages_are_cumulative_and_documented(self):
        previous = []
        stages = ("inventory", "visual-options", "tokens", "components", "screens",
                  "hub", "lesson", "backup", "complete")
        for stage in stages:
            required = required_artifacts(stage)
            self.assertEqual(required[:len(previous)], previous)
            for name in required:
                if name.endswith(".html"):
                    self.html(name)
                else:
                    self.put(name, "/* local CSS */" if name.endswith(".css") else "Inventory")
            self.assertEqual(self.errors(stage), [], stage)
            previous = required
        output = io.StringIO()
        with redirect_stdout(output), self.assertRaises(SystemExit) as exit_info:
            main([str(self.root), "--help"])
        self.assertEqual(exit_info.exception.code, 0)
        for stage in stages:
            self.assertIn(f"{stage}:", output.getvalue())
        for name in previous:
            self.assertIn(name, output.getvalue())

    def test_missing_root_and_invalid_stage(self):
        self.assertIn("not a directory", validate_tree(self.root / "absent", "inventory")[0])
        with self.assertRaises(ValueError):
            validate_tree(self.root, "not-a-stage")
        result = subprocess.run([sys.executable, str(Path(__file__).with_name("validate_mockups.py")),
                                 str(self.root), "--stage", "invalid"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn("invalid choice", result.stderr)

    def test_missing_required_artifact(self):
        (self.root / "adoption-report.md").unlink()
        self.assert_error("adoption-report.md: missing required artifact")

    def test_metadata_valid_and_missing_each_requirement(self):
        replacements = (
            ("<!doctype html>", "", "doctype"),
            ('lang="en"', "", "html[lang]"),
            ('lang="en"', 'lang="not a language"', "html[lang]"),
            ('<meta charset="utf-8">', "", "UTF-8"),
            ('charset="utf-8"', 'charset="iso-8859-1"', "UTF-8"),
            ('<meta name="viewport" content="width=device-width, initial-scale=1">', "", "viewport"),
            ('content="width=device-width, initial-scale=1"', 'content="initial-scale=1"', "viewport"),
            ("<title>Mockup test</title>", "<title> </title>", "nonempty <title>"),
            ("<head>", "", "exactly one <head>"),
            ("<body>", "", "exactly one <body>"),
        )
        for old, new, expected in replacements:
            with self.subTest(expected=expected, old=old):
                self.put("index.html", HTML.format(head="", body="").replace(old, new))
                self.assert_error(expected)
        self.html()
        self.assertEqual(self.errors(), [])

    def test_metadata_must_be_in_head(self):
        self.put("index.html", '<!doctype html><html lang="en"><head></head><body>'
                 '<meta charset="utf-8"><meta name="viewport" content="width=device-width">'
                 '<title>Outside head</title></body></html>')
        errors = self.errors()
        for expected in ("UTF-8", "viewport", "nonempty <title>"):
            self.assertTrue(any(expected in error for error in errors), errors)

    def test_nested_paths_queries_percent_encoding_and_fragments(self):
        self.html("pages/nested/start.html", body='''
<a href="?fixture=one#current">Current query</a><section id="current"></section>
<a href="../../other%20page.html?fixture=two#%E6%97%A5%E6%9C%AC%E8%AA%9E">Other</a>
<a href="../../other%20page.html#legacy">Named anchor</a>
<a href="">Same page</a><a href="#">Top</a>
<img src="../../assets/%E7%94%BB%E5%83%8F.png?v=1&amp;size=2" alt="">
''', head='<link rel="stylesheet" href="../../css/theme.css?v=2">')
        self.html("other page.html", body='<h1 id="日本語">Title</h1><a name="legacy"></a>')
        self.put("assets/画像.png")
        self.put("css/theme.css", '@import "nested/base.css?version=1"; '
                 'main { background-image: url("../assets/%E7%94%BB%E5%83%8F.png?size=2"); }')
        self.put("css/nested/base.css", "/* empty stylesheet */")
        self.assertEqual(self.errors(), [])

    def test_missing_file_has_file_line_and_reference(self):
        self.html(body='<img src="absent.png" alt="">')
        error = self.assert_error("missing local file: absent.png")[0]
        self.assertRegex(error, r"^index\.html:\d+: img\[src\] 'absent.png':")

    def test_missing_same_page_and_cross_page_fragments(self):
        self.html(body='<a href="#absent">Same</a><a href="other.html?q=1#missing">Other</a>')
        self.html("other.html", body='<h1 id="present">Other</h1>')
        errors = self.errors()
        self.assertTrue(any("missing fragment #absent in index.html" in e for e in errors))
        self.assertTrue(any("missing fragment #missing in other.html" in e for e in errors))

    def test_svg_asset_fragments_and_opaque_asset_fragments(self):
        self.put("icons.svg", '<svg xmlns="http://www.w3.org/2000/svg">'
                 '<symbol id="icon"/><filter id="blur"/></svg>')
        self.put("font.woff")
        self.put("theme.css", 'p { filter: url("icons.svg#blur"); } '
                 '@font-face { src: url("font.woff?#font-hint"); }')
        self.html(body='<svg><use href="icons.svg#icon"></use></svg>')
        self.assertEqual(self.errors(), [])
        self.html(body='<svg><use href="icons.svg#absent"></use></svg>')
        self.assert_error("missing fragment #absent in icons.svg")
        self.put("icons.svg", "<svg>malformed")
        self.assert_error("cannot inspect SVG fragment target")

    def test_duplicate_ids(self):
        self.html(body='<p id="one"></p><p id="one"></p>')
        self.assert_error("duplicate id 'one'")
        self.html(body='<a name="one"></a><p id="one"></p><a href="#one">Link</a>')
        self.assertEqual(self.errors(), [])

    def test_all_existing_documents_checked_even_at_inventory(self):
        self.put("future/screen.html", "<p>No metadata yet</p>")
        self.put("future/theme.css", "a { background: url(absent.png); }")
        self.assert_error("future/screen.html:1: missing")
        self.assert_error("future/theme.css:1: CSS url() 'absent.png'")

    def test_html_url_attributes(self):
        self.html(body='''<video poster="missing-poster.png" src="missing-video.mp4"></video>
<form action="missing-action.html"><button formaction="missing-form.html">Submit</button></form>
<object data="missing-object.svg"></object><iframe src="missing-frame.html"></iframe>
<blockquote cite="missing-citation.html"></blockquote><script src="missing-script.js"></script>
<svg><use href="missing-use.svg"></use><use xlink:href="missing-xlink.svg"></use></svg>''')
        errors = self.errors()
        for name in ("poster.png", "video.mp4", "action.html", "form.html", "object.svg",
                     "frame.html", "citation.html", "script.js", "use.svg", "xlink.svg"):
            self.assertTrue(any("missing-" + name in error for error in errors), name)

    def test_srcset_and_imagesrcset(self):
        self.put("small.png")
        self.put("large image.png")
        self.html(body='<img srcset="small.png 1x, large%20image.png 2x" alt="">',
                  head='<link rel="preload" imagesrcset="small.png 320w, large%20image.png 640w">')
        self.assertEqual(self.errors(), [])
        self.html(body='<img srcset="small.png 1x, missing.png 2x" alt="">')
        self.assert_error("missing local file: missing.png")
        self.html(body='<img srcset="small.png invalid" alt="">')
        self.assert_error("malformed img[srcset] descriptor")
        self.html(body='<img srcset="" alt="">')
        self.assert_error("empty img[srcset]")

    def test_external_and_embedded_urls_are_not_local_links(self):
        self.html(body='''<a href="https://example.invalid/page#id">External</a>
<a href="mailto:person@example.invalid">Mail</a><a href="tel:123">Phone</a>
<img src="//example.invalid/asset.png" alt="">
<img srcset="data:image/png;base64,AAAA 1x, data:image/png;base64,BBBB 2x" alt="">
<img src="data:image/png;base64,AAAA" alt="">''')
        self.put("theme.css", '@import url(https://example.invalid/theme.css); '
                 'a { background: url(data:image/png;base64,AAAA); }')
        self.assertEqual(self.errors(), [])

    def test_inline_css_and_style_attributes(self):
        self.put("assets/bg.png")
        self.put("css/base.css", "/* empty */")
        self.html(head='''<style>
@import "css/base.css";
main { background: url('assets/bg.png?v=1'); }
</style>''', body='<p style="background: url(&quot;assets/bg.png&quot;)">Inline</p>')
        self.assertEqual(self.errors(), [])
        self.html(head='<style>p { background: url(missing-style.png); }</style>',
                  body='<p style="background: url(missing-attribute.png)">Inline</p>')
        self.assert_error("missing local file: missing-style.png")
        self.assert_error("missing local file: missing-attribute.png")

    def test_css_import_variants_case_comments_and_strings(self):
        self.put("base.css", "/* no URLs */")
        self.put("asset(1).png")
        self.put("theme.css", '''/* url(missing-comment.png) */
@IMPORT /* comment */ 'base.css' screen;
@import url("base.css?version=2") layer(example);
a { background: URL('asset(1).png'); content: "url(missing-string.png)"; }
''')
        self.assertEqual(self.errors(), [])
        self.put("theme.css", '@import "missing-base.css"; @import url(missing-other.css);')
        self.assert_error("missing local file: missing-base.css")
        self.assert_error("missing local file: missing-other.css")

    def test_css_escaped_paths(self):
        self.put("space name.png")
        self.put("paren(1).png")
        self.put("theme.css", r'a { background: url(space\ name.png); } '
                 r'b { background: url("space\20 name.png"); } '
                 r'c { background: url(paren\(1\).png); }')
        self.assertEqual(self.errors(), [])

    def test_malformed_css_references(self):
        for content, expected in (
            ('a { background: url("asset.png"; }', "malformed CSS url()"),
            ('a { background: url(asset.png; }', "malformed CSS url()"),
            ('@import "not closed', "unterminated CSS string"),
            ('@import ;', "malformed CSS @import"),
            ('/* unterminated', "unterminated CSS comment"),
            ('a { background: url(a b.png); }', "malformed CSS url()"),
        ):
            with self.subTest(content=content):
                self.put("theme.css", content)
                self.assert_error(expected)

    def test_malformed_and_empty_html_urls(self):
        for body, expected in (
            ('<img src="">', "empty URL"),
            ('<img src>', "has no URL value"),
            ('<a href="page%ZZ.html">Bad</a>', "malformed percent encoding"),
            ('<a href="#bad%2">Bad</a>', "malformed percent encoding"),
            ('<a href="%FF.html">Bad</a>', "malformed local path"),
            ('<a href="%00.html">Bad</a>', "malformed decoded"),
            ('<a href="https://[broken">Bad</a>', "malformed URL"),
            ('<a href="a\\b.html">Bad</a>', "backslash"),
        ):
            with self.subTest(body=body):
                self.html(body=body)
                self.assert_error(expected)

    def test_directory_is_not_a_file_target(self):
        (self.root / "directory").mkdir()
        self.html(body='<a href="directory/">Directory</a>')
        self.assert_error("missing local file: directory")

    def test_base_element_rejected_instead_of_silently_misresolving(self):
        self.html(head='<base href="nested/">')
        self.assert_error("<base> is unsupported")

    def test_traversal_absolute_and_file_urls_rejected(self):
        outside = Path(self.temp.name) / "outside.html"
        outside.write_text(HTML.format(head="", body=""), encoding="utf-8")
        for url in ("../outside.html", "%2e%2e/outside.html", str(outside),
                    outside.as_uri(), "../../missing.html"):
            with self.subTest(url=url):
                self.html(body=f'<a href="{url}">Outside</a>')
                self.assert_error("escape")

    def test_symlink_escapes_for_links_artifacts_and_required_paths(self):
        outside = Path(self.temp.name) / "outside.html"
        outside.write_text(HTML.format(head="", body='<h1 id="target">Outside</h1>'), encoding="utf-8")
        (self.root / "alias.html").symlink_to(outside)
        self.html(body='<a href="alias.html#target">Outside</a>')
        self.assert_error("artifact escapes inspected tree through a symlink")
        self.assert_error("reference escapes inspected tree")
        (self.root / "adoption-report.md").unlink()
        (self.root / "adoption-report.md").symlink_to(outside)
        self.assert_error("required artifact escapes inspected tree")

    def test_internal_symlink_references_remain_source_relative(self):
        self.put("actual/theme.css", "p { background: url(asset.png); }")
        self.put("actual/asset.png")
        (self.root / "alias").mkdir()
        (self.root / "alias/theme.css").symlink_to(self.root / "actual/theme.css")
        self.assert_error("alias/theme.css:1: CSS url() 'asset.png': missing local file: alias/asset.png")
        self.put("alias/asset.png")
        self.assertEqual(self.errors(), [])

    def test_absolute_paths_inside_tree_are_not_portable(self):
        target = self.html("target.html")
        self.html(body=f'<a href="{target}">Absolute</a>')
        self.assert_error("absolute local path is not portable")

    def test_invalid_utf8_diagnostic(self):
        self.put("invalid.html").write_bytes(b"\xff\xfe")
        self.assert_error("invalid.html: cannot read UTF-8 artifact")

    def test_fragment_target_with_unreadable_html(self):
        self.put("invalid.html").write_bytes(b"\xff")
        self.html(body='<a href="invalid.html#target">Invalid</a>')
        self.assert_error("fragment target HTML could not be inspected")

    def test_artifacts_unchanged_on_success_and_failure(self):
        self.html(body='<h1 id="target">Target</h1><a href="#target">Link</a>')
        self.put("theme.css", 'a { background: url("asset.png"); }')
        self.put("asset.png")
        for fail in (False, True):
            if fail:
                self.html(body='<a href="missing.html">Broken</a>')
            before = {p.relative_to(self.root): (p.read_bytes(), p.stat().st_mtime_ns)
                      for p in self.root.rglob("*") if p.is_file()}
            errors = self.errors()
            self.assertEqual(bool(errors), fail)
            after = {p.relative_to(self.root): (p.read_bytes(), p.stat().st_mtime_ns)
                     for p in self.root.rglob("*") if p.is_file()}
            self.assertEqual(before, after)

    def test_cli_exit_codes_and_file_specific_output(self):
        script = Path(__file__).with_name("validate_mockups.py")
        command = [sys.executable, str(script), str(self.root)]
        result = subprocess.run(command + ["--stage", "inventory"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("stage: inventory", result.stdout)
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(result.returncode, 1)
        self.assertIn("missing required artifact for stage 'complete'", result.stdout)
        self.html(body='<a href="missing.html">Broken</a>')
        result = subprocess.run(command + ["--stage", "inventory"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 1)
        self.assertRegex(result.stdout, r"index\.html:\d+: a\[href\]")
        self.assertIn("missing local file: missing.html", result.stdout)


if __name__ == "__main__":
    unittest.main()
