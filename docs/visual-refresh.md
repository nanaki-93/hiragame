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
