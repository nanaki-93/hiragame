"""Temporary-tree regression tests for the read-only mockup validator."""

from contextlib import redirect_stdout
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from validate_mockups import (COMPONENTS, MIRRORS, STAGE_ADDITIONS,
                              main, required_artifacts, required_fixtures, validate_tree)


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

    def shared_head(self, name):
        return "".join(f'<link rel="stylesheet" href="{os.path.relpath(self.root / "design-system" / sheet, (self.root / name).parent)}">'
                       for sheet in ("tokens.css", "components.css"))

    def artifacts(self, stage, fixtures=False):
        """Create a deterministic valid static tree, never production mockups."""
        bodies = {}
        if fixtures:
            for (name, fixture), coverage in required_fixtures().items():
                bodies[name] = bodies.get(name, "") + (
                    f'<section id="{fixture}" data-fixture="{fixture}" '
                    f'data-coverage="{" ".join(sorted(coverage))}">Simulated {fixture}</section>'
                )
                index = name if name == COMPONENTS else str(Path(name).parent / "index.html")
                target = os.path.relpath(name, str(Path(index).parent)) + "#" + fixture
                bodies[index] = bodies.get(index, "") + f'<a href="{target}">Fixture</a>'
            for name, owner in MIRRORS:
                target = os.path.relpath(str(Path(name).parent / "index.html"), owner)
                index = owner + "index.html"
                bodies[index] = bodies.get(index, "") + f'<a href="{target}">Mirror</a>'
        for name in required_artifacts(stage):
            if name.endswith(".html"):
                head = self.shared_head(name) if name.startswith(("screens/", "flows/")) else ""
                self.html(name, bodies.get(name, ""), head)
            elif name.endswith(".css"):
                self.put(name, ":root { --ink: #111; }" if name.endswith("tokens.css") else "")
            else:
                self.put(name, "Inventory")

    def fixture(self, identifier="example-state", coverage="C-01", **attrs):
        attributes = {"id": identifier, "data-fixture": identifier, "data-coverage": coverage, **attrs}
        return "<section " + " ".join(f'{key}="{value}"' for key, value in attributes.items()) + ">Fixture</section>"

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
            self.artifacts(stage, fixtures=stage == "complete")
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

    def test_external_navigation_and_inert_embedded_assets_are_allowed(self):
        self.html(body='''<a href="https://example.invalid/page#id">External</a>
<a href="mailto:person@example.invalid">Mail</a><a href="tel:123">Phone</a>
<img srcset="data:image/png;base64,AAAA 1x, data:image/png;base64,BBBB 2x" alt="">
<img src="data:image/png;base64,AAAA" alt="">''')
        self.put("theme.css", 'a { background: url(data:image/png;base64,AAAA); }')
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

    def test_css_token_scope_nested_fallbacks_and_literals(self):
        self.put("tokens.css", ":root { --ink: #111; --fallback: #222; --final: #333; }")
        self.put("base.css", '@import "tokens.css"; p { color: var(--ink, var(--fallback, var(--final))); }')
        self.html(head='<link rel="stylesheet" href="base.css"><style>main { --page: 1rem; padding: var(--page); }</style>',
                  body='<p style="color: var(--ink)">Test</p>')
        self.assertEqual(self.errors(), [])
        self.put("base.css", '@import "tokens.css"; p { color: var(--ink, var(--missing, var(--deep))); }')
        self.assert_error("undefined CSS custom property --missing")
        self.assert_error("undefined CSS custom property --deep")
        self.put("base.css", '@import "tokens.css"; /* var(--comment) */ p { content: "var(--string)"; color: var(--ink); }')
        self.assertEqual(self.errors(), [])
        self.html(body='<p style="color: var(--attribute-missing, red)">Inline</p>',
                  head='<style>p { color: var(--inline-missing); }</style>')
        self.assert_error("undefined CSS custom property --attribute-missing")
        self.assert_error("undefined CSS custom property --inline-missing")

    def test_tokens_not_satisfied_by_unrelated_documents(self):
        self.put("unlinked.css", ":root { --unlinked: #111; }")
        self.html(head='<style>p { color: var(--unlinked); }</style>')
        self.assert_error("undefined CSS custom property --unlinked")
        self.html(head='<link rel="stylesheet" href="unlinked.css">',
                  body='<p style="color: var(--unlinked)">Now linked</p>')
        self.assertEqual(self.errors(), [])

    def test_orphan_css_import_cycles_and_shared_consumer_scope(self):
        self.put("a.css", '@import "b.css"; p { color: var(--ink); }')
        self.put("b.css", '@import "a.css"; :root { --ink: #111; }')
        self.assertEqual(self.errors(), [])
        self.put("b.css", '@import "a.css";')
        self.assert_error("undefined CSS custom property --ink")
        self.put("tokens.css", ":root { --ink: #111; }")
        self.html(head='<link rel="stylesheet" href="tokens.css"><link rel="stylesheet" href="a.css">')
        self.assertEqual(self.errors(), [])
        self.html("missing-context.html", head='<link rel="stylesheet" href="a.css">')
        self.assert_error("style context: missing-context.html")

    def test_custom_property_case_escapes_and_multiline_diagnostic(self):
        self.put("theme.css", r':root { --in\6b: #111; }' + '\np { color: var(--ink); }\np { color: var(--Ink); }')
        self.assert_error("theme.css:3: undefined CSS custom property --Ink")
        self.put("theme.css", r':root { --in\6b: #111; } p { color: var(--ink); }')
        self.assertEqual(self.errors(), [])

    def test_screen_and_flow_pages_require_two_actual_shared_links(self):
        self.put("design-system/tokens.css", ":root { --ink: #111; }")
        self.put("design-system/components.css", "p { color: var(--ink); }")
        for name in ("screens/example/index.html", "screens/example/option.html",
                     "flows/example/page.html", "flows/example/index.html"):
            with self.subTest(name=name):
                self.html(name)
                self.assert_error(name + ": missing shared stylesheet link: design-system/tokens.css")
                self.assert_error(name + ": missing shared stylesheet link: design-system/components.css")
                self.html(name, head=self.shared_head(name))
        self.assertEqual(self.errors(), [])
        self.html("screens/example/option.html", head='<link rel="preload" href="../../design-system/tokens.css"><style>@import "../../design-system/components.css";</style>')
        self.assert_error("screens/example/option.html: missing shared stylesheet link")

    def test_remote_runtime_html_dependencies(self):
        cases = (
            '<img src="//example.invalid/a.png">',
            '<img srcset="https://example.invalid/a.png 1x">',
            '<video poster="https://example.invalid/a.png"></video>',
            '<script src="https://example.invalid/a.js"></script>',
            '<iframe src="https://example.invalid/"></iframe>',
            '<object data="https://example.invalid/a.svg"></object>',
            '<embed src="https://example.invalid/a.svg">',
            '<link rel="stylesheet" href="https://example.invalid/a.css">',
            '<link rel="preconnect" href="https://example.invalid/">',
            '<form action="https://example.invalid/api"></form>',
            '<button formaction="https://example.invalid/api">Submit</button>',
            '<a href="https://example.invalid/" ping="https://example.invalid/track">Link</a>',
            '<script src="data:text/javascript,alert(1)"></script>',
            '<iframe src="data:text/html,hello"></iframe>',
            '<svg><use href="https://example.invalid/s.svg#icon"></use></svg>',
        )
        for body in cases:
            with self.subTest(body=body):
                self.html(body=body)
                self.assert_error("runtime dependency is prohibited")

    def test_remote_css_imports_and_assets_in_all_style_locations(self):
        for css in ('@import "https://example.invalid/a.css";',
                    '@import url(//example.invalid/a.css);',
                    'p { background: url(https://example.invalid/a.png); }',
                    r'p { background: url("\68 ttps://example.invalid/a.png"); }'):
            for location in ("file", "inline", "attribute"):
                with self.subTest(css=css, location=location):
                    self.put("theme.css", css if location == "file" else "")
                    self.html(head=f'<style>{css}</style>' if location == "inline" else "",
                              body="<p style='" + css.replace("'", "&apos;") + "'>Test</p>" if location == "attribute" else "")
                    self.assert_error("runtime dependency is prohibited")

    def test_image_set_string_dependencies_in_all_style_locations(self):
        for function in ("image-set", "-webkit-image-set", "IMAGE-SET", r"image\2d set"):
            for target in ("https://example.invalid/image.png", "//example.invalid/image.png",
                           r"\68 ttps://example.invalid/image.png", "missing.png"):
                for location in ("file", "inline", "attribute"):
                    with self.subTest(function=function, target=target, location=location):
                        css = f'''p {{ background-image: {function}("local.png" 1x,
/* candidate */ '{target}' 2x type("image/png")); }}'''
                        self.put("local.png")
                        self.put("styles/theme.css", css if location == "file" else "")
                        self.put("styles/local.png")
                        self.html(head=f'<style>{css}</style>' if location == "inline" else "",
                                  body='<p style="' + css.replace('"', '&quot;') + '">Test</p>'
                                  if location == "attribute" else "")
                        expected = "missing local file" if target == "missing.png" else "runtime dependency is prohibited"
                        errors = self.assert_error(expected)
                        self.assertEqual(len(errors), 1, errors)
                        self.assertIn("CSS image-set()" if "webkit" not in function else
                                      "CSS -webkit-image-set()", errors[0])
                        if location == "file":
                            self.assertTrue(errors[0].startswith("styles/theme.css:2:"), errors)
                        elif location == "inline":
                            self.assertTrue(errors[0].startswith("index.html:7:"), errors)

    def test_image_set_first_string_candidate_fails_cli(self):
        for function in ("image-set", "-webkit-image-set"):
            for location in ("file", "inline"):
                with self.subTest(function=function, location=location):
                    css = f'p {{ background-image: {function}("https://example.invalid/image.png" 1x); }}'
                    self.put("theme.css", css if location == "file" else "")
                    self.html(head=f'<style>{css}</style>' if location == "inline" else "")
                    output = io.StringIO()
                    with redirect_stdout(output):
                        status = main([str(self.root), "--stage", "inventory"])
                    self.assertEqual(status, 1)
                    self.assertIn(f"CSS {function}()", output.getvalue())
                    self.assertIn("runtime dependency is prohibited", output.getvalue())

    def test_image_set_local_data_nested_images_and_non_url_strings(self):
        self.put("styles/local image.png")
        self.put("local image.png")
        self.put("vector.svg", '<svg xmlns="http://www.w3.org/2000/svg"><symbol id="icon"/></svg>')
        self.put("styles/vector.svg", '<svg xmlns="http://www.w3.org/2000/svg"><symbol id="icon"/></svg>')
        css = r'''/* image-set("https://example.invalid/comment.png" 1x) */
p { content: 'image-set("https://example.invalid/string.png" 1x)';
    background: image-set("local%20image.png?v=1" 1x type("image/png"),
        url("vector.svg#icon") 2x, linear-gradient(red, blue) 3x,
        "data:image/png;base64,AAAA" 4x),
        -webkit-image-set('local\20 image.png' 1x); }
'''
        self.put("styles/theme.css", css)
        self.html(head=f'<style>{css}</style>')
        self.assertEqual(self.errors(), [])
        # A nested image expression must not hide the next string candidate.
        self.put("styles/theme.css", 'p { background: image-set(linear-gradient(red, blue) 1x, '
                 '"https://example.invalid/next.png" 2x); }')
        self.assert_error("runtime dependency is prohibited")
        # Nor should the function's end turn unrelated strings into dependencies.
        self.put("styles/theme.css", 'p { background: image-set("local%20image.png" 1x); '
                 'content: "https://example.invalid/not-an-asset"; }')
        self.assertEqual(self.errors(), [])

    def test_prohibited_script_calls_inline_events_and_local_scripts(self):
        calls = (
            'fetch("/api")', 'window["fetch"]("/api")', 'new XMLHttpRequest()',
            'new WebSocket("wss://example.invalid")', 'new EventSource("/stream")',
            'navigator.sendBeacon("/log", "hello")', 'localStorage.getItem("learner")',
            'window["sessionStorage"].clear()', 'indexedDB.open("db")',
            'caches.match("lesson")', 'navigator.serviceWorker.register("sw.js")',
            'navigator["serviceWorker"].register("sw.js")', 'document.cookie = "x=1"',
            'document["cookie"]', 'navigator.storage.persist()', 'import("/module.js")',
            'import helper from "./helper.js"', 'import "./helper.js"',
            'export {helper} from "https://example.invalid/helper.js"',
            'new Worker("https://example.invalid/worker.js")', 'importScripts("worker.js")',
            '`result ${fetch("/api")}`',
        )
        for call in calls:
            with self.subTest(call=call):
                self.html(body=f'<script>\n{call};</script>')
                self.assert_error("prohibited mock script API/pattern")
                self.html(body=f"<button onclick='{call}'>Test</button>")
                self.assert_error("prohibited mock script API/pattern")
                self.html(body='<script src="local.js"></script>')
                self.put("local.js", call)
                self.assert_error("local.js:1: prohibited mock script")
                (self.root / "local.js").unlink()
        self.html(body='<a href="javascript:fetch(1)">Bad</a>')
        self.assert_error("javascript: URLs are prohibited")
        self.html(body='<iframe srcdoc="&lt;script&gt;alert(1)&lt;/script&gt;"></iframe>')
        self.assert_error("iframe[srcdoc]")
        self.html(head='<meta http-equiv="refresh" content="1;url=https://example.invalid/">')
        self.assert_error("meta refresh is prohibited")

    def test_data_stylesheet_imports_and_asset_urls_do_not_define_tokens(self):
        for css in ('@import "data:text/css,p{}";', '@import url("data:text/css,p{}");'):
            with self.subTest(css=css):
                self.put("theme.css", css)
                self.assert_error("runtime dependency is prohibited")
        self.put("fake-tokens.css", ":root { --not-loaded: #111; }")
        self.put("theme.css", 'p { background: url("fake-tokens.css"); color: var(--not-loaded); }')
        self.assert_error("undefined CSS custom property --not-loaded")

    def test_page_local_scripts_and_ignored_comments_are_valid(self):
        self.html(body='''<script>
// fetch("/api"); localStorage.clear();
/* navigator.serviceWorker.register("sw.js"); */
const fixture = {due: 3};
document.querySelector("button").addEventListener("click", () => {
  document.querySelector("p").textContent = "Simulated complete";
});
</script><script type="application/json">{"label": "localStorage"}</script>
<button onclick="this.disabled = true">Simulate</button><p></p>''')
        self.assertEqual(self.errors(), [])

    def test_fixture_identifier_coverage_and_matching_id(self):
        cases = (
            (self.fixture("Invalid_ID"), "invalid data-fixture identifier"),
            (self.fixture(id="other"), "requires matching id"),
            (self.fixture(coverage=""), "requires valid data-coverage"),
            (self.fixture(coverage="C-1"), "requires valid data-coverage"),
            (self.fixture(coverage="C-99"), "unknown coverage IDs"),
            (self.fixture() + self.fixture(), "duplicate fixture"),
        )
        for body, expected in cases:
            with self.subTest(expected=expected):
                self.html(body=body)
                self.assert_error(expected)
        self.html(body=self.fixture() + '<a href="#example-state">Fixture</a>')
        self.assertEqual(self.errors(), [])

    def test_disconnected_fixture_requires_direct_owning_index_fragment(self):
        name = "flows/example/page.html"
        self.put("design-system/tokens.css", "")
        self.put("design-system/components.css", "")
        self.html(name, body=self.fixture(), head=self.shared_head(name))
        self.assert_error("has no owning index")
        index = "flows/example/index.html"
        self.html(index, body='<a href="page.html">Page without fixture fragment</a>', head=self.shared_head(index))
        self.assert_error("unreachable fixture #example-state")
        self.html("elsewhere.html", body='<a href="flows/example/page.html#example-state">Wrong index</a>')
        self.assert_error("unreachable fixture #example-state")
        self.html(index, body='<a href="page.html?simulated=1#example%2Dstate">Fixture</a>', head=self.shared_head(index))
        self.assertEqual(self.errors(), [])
        self.html(index, body='<button data-target="page.html#example-state">Not a link</button>', head=self.shared_head(index))
        self.assert_error("unreachable fixture #example-state")

    def test_showcase_is_its_own_index_and_reused_ids_are_per_document(self):
        self.html(COMPONENTS, body=self.fixture() + '<a href="#example-state">Fixture</a>')
        self.html("second.html", body=self.fixture())
        self.html(body='<a href="second.html#example-state">Fixture</a>')
        self.assertEqual(self.errors(), [])
        self.html(COMPONENTS, body=self.fixture())
        self.assert_error("design-system/components.html needs a direct local fragment link")

    def test_partial_stage_checks_existing_fixtures_but_not_future_states(self):
        self.artifacts("screens")
        self.assertEqual(self.errors("screens"), [])
        self.assert_error("missing required artifact", "hub")
        name = "screens/f00-home/option-1.html"
        self.html(name, body=self.fixture(), head=self.shared_head(name))
        self.assert_error("unreachable fixture", "inventory")
        self.html("screens/f00-home/index.html", body='<a href="option-1.html#example-state">Fixture</a>',
                  head=self.shared_head("screens/f00-home/index.html"))
        self.assertEqual(self.errors("screens"), [])

    def test_complete_requires_all_states_coverage_and_mirror_links(self):
        self.artifacts("complete", fixtures=True)
        self.assertEqual(self.errors("complete"), [])
        name = "flows/learning-hub/04-settings.html"
        path = self.root / name
        text = path.read_text()
        self.put(name, text.replace('data-fixture="save-corrupt-preserved"', 'data-other="save-corrupt-preserved"'))
        self.assert_error("missing required fixture #save-corrupt-preserved", "complete")
        self.put(name, text.replace('data-coverage="C-37"', 'data-coverage="C-38"'))
        self.assert_error("fixture #save-corrupt-preserved missing required coverage: C-37", "complete")
        self.put(name, text)
        index = self.root / "flows/learning-hub/index.html"
        self.put("flows/learning-hub/index.html", index.read_text().replace('href="../../screens/f00-settings/index.html"', 'href="04-settings.html"'))
        self.assert_error("missing mirror index link: screens/f00-settings/index.html", "complete")
        self.artifacts("complete", fixtures=True)
        (self.root / "screens/f00-settings/option-sr.html").unlink()
        self.assert_error("missing required artifact", "complete")
        self.assert_error("missing required fixture #mirror-m-10", "complete")

    def test_fixture_registry_covers_all_contract_rows_and_mirrors(self):
        registry = required_fixtures()
        coverage = set().union(*registry.values())
        self.assertEqual(coverage, {f"C-{n:02}" for n in range(1, 57)} |
                         {f"M-{n:02}" for n in range(1, 12)})
        self.assertEqual(len(MIRRORS), 11)
        for n in range(8):
            self.assertIn(("flows/workplace-lesson/02-dialogue.html", f"dialogue-support-{n:03b}"), registry)
        self.assertEqual(registry[("flows/learning-hub/01-home.html", "home-resume-priority")], {"C-02", "C-04"})
        for n in range(1, 5):
            self.assertEqual(registry[(f"screens/f00-home/option-{n}.html", "home-due-review-priority")], {"C-03", "C-04"})

    def test_complete_tree_is_read_only_and_each_stage_rejects_missing_additions(self):
        self.artifacts("complete", fixtures=True)
        before = {p.relative_to(self.root): (p.read_bytes(), p.stat().st_mtime_ns)
                  for p in self.root.rglob("*") if p.is_file()}
        self.assertEqual(validate_tree(self.root), [])
        after = {p.relative_to(self.root): (p.read_bytes(), p.stat().st_mtime_ns)
                 for p in self.root.rglob("*") if p.is_file()}
        self.assertEqual(before, after)
        for stage, additions in STAGE_ADDITIONS.items():
            for name in additions:
                with self.subTest(stage=stage, name=name):
                    path = self.root / name
                    content = path.read_bytes()
                    path.unlink()
                    self.assert_error(name + ": missing required artifact", stage)
                    path.write_bytes(content)

    def test_new_failure_classes_have_nonzero_cli_status_and_do_not_modify_tree(self):
        cases = (
            ("index.html", HTML.format(head="<style>p { color: var(--absent); }</style>", body=""), "undefined CSS"),
            ("index.html", HTML.format(head="", body='<script>fetch("/api")</script>'), "prohibited mock script"),
            ("index.html", HTML.format(head="", body='<img src="https://example.invalid/a.png">'), "runtime dependency"),
            ("index.html", HTML.format(head="", body=self.fixture()), "unreachable fixture"),
            ("screens/test/index.html", HTML.format(head="", body=""), "missing shared stylesheet"),
        )
        for name, content, expected in cases:
            with self.subTest(expected=expected):
                self.put(name, content)
                before = {p.relative_to(self.root): (p.read_bytes(), p.stat().st_mtime_ns)
                          for p in self.root.rglob("*") if p.is_file()}
                output = io.StringIO()
                with redirect_stdout(output):
                    status = main([str(self.root), "--stage", "inventory"])
                self.assertEqual(status, 1)
                self.assertIn(expected, output.getvalue())
                after = {p.relative_to(self.root): (p.read_bytes(), p.stat().st_mtime_ns)
                         for p in self.root.rglob("*") if p.is_file()}
                self.assertEqual(before, after)
                (self.root / name).unlink()

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
