# Japanese study-space refresh

Requested 23 October 2026: improve the existing app's graphics and usability for a learner going from zero Japanese to daily life and software engineering work in Japan. Commit each implementation step.

## Findings

The curriculum already covers kana, vocabulary and grammar, daily life, and engineering communication. The existing interface uses a purple palette, oversized nested panels, repeated navigation, and technical save messages above the learning content. The next practice action is below the first viewport. Lesson cards repeat implementation details and label daily-life lessons as workplace content. Progress is mostly prose.

## Implementation sequence

1. Establish a warm paper / ink / vermilion palette, real brand navigation, current-page states, theme control, keyboard focus, and quieter healthy save status.
2. Build a welcoming dashboard with one next action, original Japanese landscape artwork, visible four-stage path, progress, and a compact reference desk.
3. Simplify the lesson library and practice/lesson/review screens with readable cards, search, visual progress, and responsive layouts.
4. Verify the production build, existing domain tests, keyboard and browser journeys, both themes, and narrow layouts; document evidence and commit any finishing fixes.

## Constraints

Keep content IDs, saved progress, answer checking, scheduling, offline behavior, and recovery paths intact. Japanese study text must retain its readings and translations, including withholding answer-revealing aids during exercises. All new artwork, icons and typography must work without external network requests. Completion describes practice, not proficiency.

## Delivered

- A locally bundled Japanese landscape, kana seal, line icons, warm paper surfaces, and ink/vermilion/green accents. Light and dark themes use the same canonical token source.
- My path leads with the next practice or unfinished lesson, followed by review and all four curriculum stages. The study desk and complete practice library remain available.
- Lessons has case-insensitive multiword search, daily-life/engineering filters, optional topic filtering, a starter order, and a suggested lesson. Compact cards separate preview from entering the lesson.
- Practice has large kana choices, real session progress, clear feedback, and quieter secondary information. Lessons have a six-step indicator and larger Japanese passages. Review has a helpful empty state and a focused reveal/rating flow.
- Active navigation, a skip link, minimum 44-pixel main navigation targets, visible focus, and focus transfer to each exercise, lesson step, preview and completion screen. Reduced-motion CSS remains supported.

## Verification — 23 October 2026

Automated checks passed:

| Check | Result |
| --- | --- |
| Python tool suite | 142 passed |
| Shared Kotlin/JS Node suite | 126 passed |
| Site Kotlin/JS Node suite | 194 passed |
| Offline policy suite | Passed |
| Content and mockup validators | Passed |
| Production distribution | Built successfully |
| Local runtime and release integrity validators | Passed |
| Git whitespace check | Passed |

The existing source-contract tests were updated for navigation moving into the shared shell, shorter UI labels, visual stage indicators, and CSS-based theme inheritance. The checks for validated content, local state, checkpoint recovery, Japanese answer aids and guarded transitions remain.

Browser verification used the production distribution in the Codex in-app browser on a separate loopback origin:

- Measured dashboard widths of 1280, 768, 390 and 320 pixels: no horizontal overflow; roadmap layout changed from four columns to two to one; main navigation targets remained at least 44 pixels tall.
- Tested both themes and persistence across navigation/reload. Primary/secondary text, links, accent-button text and focus colors exceed 4.5:1 in the checked canonical color pairs (minimum 5.21:1).
- Verified skip-to-content focus, exercise/feedback focus, preview focus, lesson-step focus, and review-completion focus.
- Submitted an empty practice answer, then answered all ten kana questions correctly. Verified feedback, progress, completion, and resuming exercise 2 after leaving and reloading.
- Ran phrase review through reveal, rating, advancement and completion.
- Verified case-insensitive multiword search (`CODE   review`), no-results recovery, daily-life filtering, topic selection, and lesson preview.
- Started the transport lesson, advanced into a Japanese dialogue, left, reloaded its nested route, and resumed the same dialogue item. Checked lesson and settings layout at 320 pixels, including backup access.
- Applied a downloaded production update while idle and confirmed the cached-release status. No browser console warnings or errors appeared during these checks.

Local screenshots are generated under ignored `build/ui-qa/`. Browser checks cover one browser engine; physical-device, assistive-technology and disconnected-network certification were not performed. Webpack still reports its advisory bundle-size warnings for the approximately 1.51 MiB JavaScript bundle.
