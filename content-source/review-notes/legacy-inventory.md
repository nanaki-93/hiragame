# Legacy CSV inventory — preservation and routing (F01 step 1.4)

Source: `backend/migration/question.csv`; preservation copy: `content-source/legacy/question.csv`. The source is headerless. Parsed with Python 3 `csv.reader` using UTF-8 and `newline=""` (not physical-line splitting); every record has ten fields: ID, Japanese, romanization, translation, topic, level, mode, timestamp, katakana flag, kanji flag. No CSV header was counted.

- SHA-256 (both files): `582da5344a9fcc77d0639b0b562cd989ff55bf4244188495c9e09311dbb65754`; byte length: 926,479.
- Parser records: **4,946**; ten-column records: **4,946**. Root `PLAN.md` reports 4,946: **no discrepancy**.
- Mode totals: `SIGN` **106**, `WORD` **812**, `SENTENCE` **4,028** (sum **4,946**).
- Distinct literal topic labels (column 5): **372**; distinct routing IDs: **337**. **35** case-variant alias groups (35 extra spellings).
- The explicit, exact-match mapping of **all** observed labels to stable routing IDs is `content-source/topic-map.json` (`labels`). There is no runtime case-folding, fallback slugging, or inferred topic for unmapped labels. The counts below use the raw field without trimming or merging.

## Special case and uncertainty

- All 106 `SIGN` records have literal topic `N5` **and** level `N5`. `N5` is a level-like source label, not a topic named “N5”: its explicit provisional routing ID is `kana-signs`. It must not be merged with other `N5`-level words or sentences. The field `level` is separate from `topic` and is not used to infer a topic.
- The mapping is for draft routing only; its `legacy-*` IDs and `kana-signs` do **not** assert reviewed curriculum topics or approved lessons. Identical IDs only group exact case variants (e.g. `Time` / `time`); similar but nonidentical semantic labels remain separate pending content review. Collision check: for every shared ID, all assigned labels have the same `casefold()` spelling; there are no conflicting alias groups in this source snapshot.
- CSV romanization, translations, classifications and kana/kanji flags are unreviewed source claims. For example, all 106 `SIGN` rows carry `katakana=false` and `kanji=false`; these flags alone cannot establish script coverage or authored readings. Provenance and redistribution permissions for the legacy material have not been established. Preserve it outside production resources and flag uncertainty in conversion; do not infer review, native-speaker certification, or publishable rights from this inventory.
- A full character-by-character `SIGN` audit (including historical kana, readings and omissions) belongs to step 2.2; these mode totals are not that audit.

## Observed alias groups (exact labels sharing an ID)

| Routing ID | Raw labels |
| --- | --- |
| `legacy-adjective` | `Adjective`, `adjective` |
| `legacy-adjectives` | `Adjectives`, `adjectives` |
| `legacy-animals` | `Animals`, `animals` |
| `legacy-asking-for-help` | `Asking for Help`, `asking for help` |
| `legacy-beverages` | `Beverages`, `beverages` |
| `legacy-body-part` | `Body part`, `body part` |
| `legacy-clothing` | `Clothing`, `clothing` |
| `legacy-color` | `Color`, `color` |
| `legacy-colors` | `Colors`, `colors` |
| `legacy-daily-activities` | `Daily Activities`, `Daily activities` |
| `legacy-daily-life` | `Daily Life`, `daily life` |
| `legacy-directions` | `Directions`, `directions` |
| `legacy-education` | `Education`, `education` |
| `legacy-emotion` | `Emotion`, `emotion` |
| `legacy-entertainment` | `Entertainment`, `entertainment` |
| `legacy-family` | `Family`, `family` |
| `legacy-fashion` | `Fashion`, `fashion` |
| `legacy-food` | `Food`, `food` |
| `legacy-location` | `Location`, `location` |
| `legacy-nationality` | `Nationality`, `nationality` |
| `legacy-nature` | `Nature`, `nature` |
| `legacy-number` | `Number`, `number` |
| `legacy-objects` | `Objects`, `objects` |
| `legacy-people` | `People`, `people` |
| `legacy-quantity` | `Quantity`, `quantity` |
| `legacy-religion` | `Religion`, `religion` |
| `legacy-social` | `Social`, `social` |
| `legacy-sports` | `Sports`, `sports` |
| `legacy-time` | `Time`, `time` |
| `legacy-transportation` | `Transportation`, `transportation` |
| `legacy-travel` | `Travel`, `travel` |
| `legacy-verb` | `Verb`, `verb` |
| `legacy-verbs` | `Verbs`, `verbs` |
| `legacy-weather` | `Weather`, `weather` |
| `legacy-work` | `Work`, `work` |

## Complete observed topic-label inventory

| Raw CSV topic label | Records | Routing ID |
| --- | ---: | --- |
| `Adjective` | 7 | `legacy-adjective` |
| `Adjectives` | 19 | `legacy-adjectives` |
| `Adverb` | 4 | `legacy-adverb` |
| `Airport and Flight Travel` | 23 | `legacy-airport-and-flight-travel` |
| `Animals` | 2 | `legacy-animals` |
| `Animals and Pets` | 14 | `legacy-animals-and-pets` |
| `Appliances and Electronics` | 26 | `legacy-appliances-and-electronics` |
| `Arts and Crafts` | 19 | `legacy-arts-and-crafts` |
| `Asking Questions` | 1 | `legacy-asking-questions` |
| `Asking for Help` | 19 | `legacy-asking-for-help` |
| `Asking for Help, Education` | 1 | `legacy-asking-for-help-education` |
| `Bargaining` | 53 | `legacy-bargaining` |
| `Basic Courtesy Phrases` | 9 | `legacy-basic-courtesy-phrases` |
| `Basic Foods and Ingredients` | 9 | `legacy-basic-foods-and-ingredients` |
| `Beliefs` | 10 | `legacy-beliefs` |
| `Beverages` | 9 | `legacy-beverages` |
| `Beverages and Drinks` | 83 | `legacy-beverages-and-drinks` |
| `Body part` | 23 | `legacy-body-part` |
| `Books and Reading` | 17 | `legacy-books-and-reading` |
| `Children and Parenting` | 37 | `legacy-children-and-parenting` |
| `Clothing` | 11 | `legacy-clothing` |
| `Clothing and Fashion` | 13 | `legacy-clothing-and-fashion` |
| `Color` | 9 | `legacy-color` |
| `Colors` | 1 | `legacy-colors` |
| `Colors and Clothing` | 1 | `legacy-colors-and-clothing` |
| `Colors and Fruits` | 2 | `legacy-colors-and-fruits` |
| `Colors and Shapes` | 14 | `legacy-colors-and-shapes` |
| `Colors, Clothing, Numbers` | 1 | `legacy-colors-clothing-numbers` |
| `Common Illnesses and Symptoms` | 6 | `legacy-common-illnesses-and-symptoms` |
| `Cooking Methods` | 31 | `legacy-cooking-methods` |
| `Cooking Methods and Techniques` | 26 | `legacy-cooking-methods-and-techniques` |
| `Cooking Techniques` | 33 | `legacy-cooking-techniques` |
| `Current Events` | 4 | `legacy-current-events` |
| `Daily Activities` | 11 | `legacy-daily-activities` |
| `Daily Life` | 1 | `legacy-daily-life` |
| `Daily Objects` | 1 | `legacy-daily-objects` |
| `Daily activities` | 1 | `legacy-daily-activities` |
| `Days of the Month` | 1 | `legacy-days-of-the-month` |
| `Days of the Months` | 1 | `legacy-days-of-the-months` |
| `Days of the Week` | 10 | `legacy-days-of-the-week` |
| `Days of the Week and Months` | 6 | `legacy-days-of-the-week-and-months` |
| `Describing People` | 1 | `legacy-describing-people` |
| `Describing People's Appearance` | 4 | `legacy-describing-people-s-appearance` |
| `Dietary Restrictions` | 25 | `legacy-dietary-restrictions` |
| `Directions` | 5 | `legacy-directions` |
| `Directions and Navigation` | 7 | `legacy-directions-and-navigation` |
| `Distance` | 3 | `legacy-distance` |
| `Education` | 5 | `legacy-education` |
| `Emotion` | 4 | `legacy-emotion` |
| `Entertainment` | 3 | `legacy-entertainment` |
| `Exercise and Fitness` | 82 | `legacy-exercise-and-fitness` |
| `Existence Sentence` | 1 | `legacy-existence-sentence` |
| `Extended Family and Relatives` | 10 | `legacy-extended-family-and-relatives` |
| `Family` | 3 | `legacy-family` |
| `Family Members` | 21 | `legacy-family-members` |
| `Fashion` | 2 | `legacy-fashion` |
| `Fish` | 17 | `legacy-fish` |
| `Food` | 1 | `legacy-food` |
| `Food Preferences` | 43 | `legacy-food-preferences` |
| `Food Preferences and Dietary Restrictions` | 34 | `legacy-food-preferences-and-dietary-restrictions` |
| `Food Shopping and Grocery Stores` | 24 | `legacy-food-shopping-and-grocery-stores` |
| `Friendship` | 28 | `legacy-friendship` |
| `Friendship and Social Relationships` | 41 | `legacy-friendship-and-social-relationships` |
| `Fruits` | 2 | `legacy-fruits` |
| `Fruits and Vegetables` | 11 | `legacy-fruits-and-vegetables` |
| `Furniture and Home Decor` | 18 | `legacy-furniture-and-home-decor` |
| `Future Plans` | 20 | `legacy-future-plans` |
| `Games and Puzzles` | 141 | `legacy-games-and-puzzles` |
| `Garden and Yard` | 16 | `legacy-garden-and-yard` |
| `Generations` | 70 | `legacy-generations` |
| `Greetings and Introductions` | 3 | `legacy-greetings-and-introductions` |
| `History and Historical Events` | 20 | `legacy-history-and-historical-events` |
| `Holidays` | 4 | `legacy-holidays` |
| `Holidays and Special Occasions` | 27 | `legacy-holidays-and-special-occasions` |
| `Hotels and Accommodation` | 15 | `legacy-hotels-and-accommodation` |
| `Household Chores and Cleaning` | 17 | `legacy-household-chores-and-cleaning` |
| `Innovation` | 22 | `legacy-innovation` |
| `International Cuisines` | 22 | `legacy-international-cuisines` |
| `Jobs and Professions` | 6 | `legacy-jobs-and-professions` |
| `Kitchen and Cooking Utensils` | 13 | `legacy-kitchen-and-cooking-utensils` |
| `Location` | 9 | `legacy-location` |
| `Marriage and Partnerships` | 20 | `legacy-marriage-and-partnerships` |
| `Meat` | 8 | `legacy-meat` |
| `Meat, Fish, and Proteins` | 37 | `legacy-meat-fish-and-proteins` |
| `Medical Appointments and Healthcare` | 36 | `legacy-medical-appointments-and-healthcare` |
| `Medications and Treatments` | 23 | `legacy-medications-and-treatments` |
| `Mental Health and Emotions` | 32 | `legacy-mental-health-and-emotions` |
| `Modes of Transportation` | 7 | `legacy-modes-of-transportation` |
| `Money and Banking` | 16 | `legacy-money-and-banking` |
| `Months` | 13 | `legacy-months` |
| `Months and Numbers` | 1 | `legacy-months-and-numbers` |
| `Movies and Television` | 13 | `legacy-movies-and-television` |
| `Music and Musical Instruments` | 22 | `legacy-music-and-musical-instruments` |
| `N5` | 106 | `kana-signs` |
| `Nationality` | 1 | `legacy-nationality` |
| `Natural Landscapes` | 4 | `legacy-natural-landscapes` |
| `Natural Landscapes (mountains, rivers, etc.)` | 28 | `legacy-natural-landscapes-mountains-rivers-etc` |
| `Nature` | 1 | `legacy-nature` |
| `Navigation` | 32 | `legacy-navigation` |
| `Neighborhood and Community` | 26 | `legacy-neighborhood-and-community` |
| `Noun` | 11 | `legacy-noun` |
| `Nouns` | 31 | `legacy-nouns` |
| `Number` | 6 | `legacy-number` |
| `Numbers` | 7 | `legacy-numbers` |
| `Numbers and Counting` | 9 | `legacy-numbers-and-counting` |
| `Objects` | 4 | `legacy-objects` |
| `Office Supplies and Equipment` | 17 | `legacy-office-supplies-and-equipment` |
| `Parts of the Body` | 17 | `legacy-parts-of-the-body` |
| `Parts of the House` | 24 | `legacy-parts-of-the-house` |
| `Parts of the House & Basic Foods` | 1 | `legacy-parts-of-the-house-basic-foods` |
| `People` | 1 | `legacy-people` |
| `Personal Information` | 15 | `legacy-personal-information` |
| `Personality Traits` | 28 | `legacy-personality-traits` |
| `Plants and Trees` | 21 | `legacy-plants-and-trees` |
| `Politics` | 62 | `legacy-politics` |
| `Politics and Government` | 98 | `legacy-politics-and-government` |
| `Prices` | 85 | `legacy-prices` |
| `Protein` | 10 | `legacy-protein` |
| `Quantity` | 3 | `legacy-quantity` |
| `Religion` | 53 | `legacy-religion` |
| `Restaurant Dining and Ordering` | 17 | `legacy-restaurant-dining-and-ordering` |
| `School Subjects` | 5 | `legacy-school-subjects` |
| `School Subjects and Education` | 11 | `legacy-school-subjects-and-education` |
| `Seasons` | 4 | `legacy-seasons` |
| `Shopping at Different Stores` | 8 | `legacy-shopping-at-different-stores` |
| `Skills and Qualifications` | 36 | `legacy-skills-and-qualifications` |
| `Social` | 1 | `legacy-social` |
| `Social Issues` | 54 | `legacy-social-issues` |
| `Social Media and Internet` | 19 | `legacy-social-media-and-internet` |
| `Social Relationships` | 41 | `legacy-social-relationships` |
| `Special Occasions` | 3 | `legacy-special-occasions` |
| `Sports` | 4 | `legacy-sports` |
| `Sports and Physical Activities` | 7 | `legacy-sports-and-physical-activities` |
| `Taste` | 5 | `legacy-taste` |
| `Technology` | 43 | `legacy-technology` |
| `Technology and Innovation` | 131 | `legacy-technology-and-innovation` |
| `Time` | 18 | `legacy-time` |
| `Time and Clock Reading` | 12 | `legacy-time-and-clock-reading` |
| `Tourist Attractions and Sightseeing` | 40 | `legacy-tourist-attractions-and-sightseeing` |
| `Traditions` | 69 | `legacy-traditions` |
| `Traditions and Customs` | 18 | `legacy-traditions-and-customs` |
| `Traffic` | 1 | `legacy-traffic` |
| `Transportation` | 13 | `legacy-transportation` |
| `Travel` | 1 | `legacy-travel` |
| `Travel Planning and Booking` | 28 | `legacy-travel-planning-and-booking` |
| `University and Higher Education` | 101 | `legacy-university-and-higher-education` |
| `Utilities and Bills` | 10 | `legacy-utilities-and-bills` |
| `Vegetables and Colors` | 1 | `legacy-vegetables-and-colors` |
| `Vegetables, Colors` | 1 | `legacy-vegetables-colors` |
| `Verb` | 1 | `legacy-verb` |
| `Verbs` | 8 | `legacy-verbs` |
| `Weather` | 4 | `legacy-weather` |
| `Weather and Seasons` | 10 | `legacy-weather-and-seasons` |
| `Weather, Colors` | 1 | `legacy-weather-colors` |
| `Weather/Feeling` | 1 | `legacy-weather-feeling` |
| `Work` | 1 | `legacy-work` |
| `Workplace Vocabulary` | 15 | `legacy-workplace-vocabulary` |
| `Yes/No Questions` | 4 | `legacy-yes-no-questions` |
| `Yes/No Questions and Responses` | 19 | `legacy-yes-no-questions-and-responses` |
| `accident` | 27 | `legacy-accident` |
| `accommodation` | 1 | `legacy-accommodation` |
| `action` | 6 | `legacy-action` |
| `activities` | 2 | `legacy-activities` |
| `activity` | 6 | `legacy-activity` |
| `adjective` | 11 | `legacy-adjective` |
| `adjectives` | 7 | `legacy-adjectives` |
| `advice` | 1 | `legacy-advice` |
| `age` | 3 | `legacy-age` |
| `agreement` | 1 | `legacy-agreement` |
| `animal` | 1 | `legacy-animal` |
| `animals` | 3 | `legacy-animals` |
| `appearance` | 23 | `legacy-appearance` |
| `art` | 7 | `legacy-art` |
| `asking for help` | 56 | `legacy-asking-for-help` |
| `astronomy` | 1 | `legacy-astronomy` |
| `bathroom` | 8 | `legacy-bathroom` |
| `bedroom` | 7 | `legacy-bedroom` |
| `behavior` | 2 | `legacy-behavior` |
| `beverages` | 29 | `legacy-beverages` |
| `body part` | 77 | `legacy-body-part` |
| `books` | 2 | `legacy-books` |
| `break` | 1 | `legacy-break` |
| `building` | 1 | `legacy-building` |
| `business` | 1 | `legacy-business` |
| `career` | 8 | `legacy-career` |
| `career aspirations` | 15 | `legacy-career-aspirations` |
| `career goals` | 2 | `legacy-career-goals` |
| `career plans` | 1 | `legacy-career-plans` |
| `childhood` | 1 | `legacy-childhood` |
| `chores` | 6 | `legacy-chores` |
| `cleaning` | 2 | `legacy-cleaning` |
| `clothing` | 3 | `legacy-clothing` |
| `color` | 5 | `legacy-color` |
| `colors` | 1 | `legacy-colors` |
| `communication` | 19 | `legacy-communication` |
| `community involvement` | 3 | `legacy-community-involvement` |
| `concept` | 58 | `legacy-concept` |
| `cooking` | 3 | `legacy-cooking` |
| `cost` | 1 | `legacy-cost` |
| `creative aspirations` | 3 | `legacy-creative-aspirations` |
| `custom` | 2 | `legacy-custom` |
| `daily` | 1 | `legacy-daily` |
| `daily activity` | 7 | `legacy-daily-activity` |
| `daily life` | 17 | `legacy-daily-life` |
| `dance` | 1 | `legacy-dance` |
| `day` | 4 | `legacy-day` |
| `decoration` | 2 | `legacy-decoration` |
| `description` | 8 | `legacy-description` |
| `desire` | 1 | `legacy-desire` |
| `dining room` | 6 | `legacy-dining-room` |
| `direction` | 8 | `legacy-direction` |
| `directions` | 1 | `legacy-directions` |
| `disaster` | 45 | `legacy-disaster` |
| `drink` | 6 | `legacy-drink` |
| `drinks` | 8 | `legacy-drinks` |
| `driving` | 1 | `legacy-driving` |
| `earthquake` | 11 | `legacy-earthquake` |
| `eating` | 1 | `legacy-eating` |
| `education` | 17 | `legacy-education` |
| `education plans` | 2 | `legacy-education-plans` |
| `emergency` | 11 | `legacy-emergency` |
| `emotion` | 1 | `legacy-emotion` |
| `entertainment` | 29 | `legacy-entertainment` |
| `entrance hall` | 7 | `legacy-entrance-hall` |
| `environment` | 10 | `legacy-environment` |
| `event` | 9 | `legacy-event` |
| `exercise` | 2 | `legacy-exercise` |
| `existence` | 5 | `legacy-existence` |
| `family` | 14 | `legacy-family` |
| `family aspirations` | 1 | `legacy-family-aspirations` |
| `family goals` | 2 | `legacy-family-goals` |
| `farewell` | 3 | `legacy-farewell` |
| `fashion` | 1 | `legacy-fashion` |
| `feeling` | 34 | `legacy-feeling` |
| `feelings` | 9 | `legacy-feelings` |
| `festival` | 5 | `legacy-festival` |
| `finance` | 4 | `legacy-finance` |
| `financial goals` | 1 | `legacy-financial-goals` |
| `flood` | 2 | `legacy-flood` |
| `food` | 90 | `legacy-food` |
| `forget` | 1 | `legacy-forget` |
| `free time` | 1 | `legacy-free-time` |
| `friends` | 2 | `legacy-friends` |
| `garden` | 24 | `legacy-garden` |
| `general` | 3 | `legacy-general` |
| `gift` | 6 | `legacy-gift` |
| `gifts` | 1 | `legacy-gifts` |
| `gratitude` | 1 | `legacy-gratitude` |
| `greeting` | 8 | `legacy-greeting` |
| `greetings` | 2 | `legacy-greetings` |
| `guest room` | 3 | `legacy-guest-room` |
| `habit` | 1 | `legacy-habit` |
| `health` | 15 | `legacy-health` |
| `health goals` | 1 | `legacy-health-goals` |
| `hobby` | 2 | `legacy-hobby` |
| `holiday` | 1 | `legacy-holiday` |
| `home` | 2 | `legacy-home` |
| `hospital` | 2 | `legacy-hospital` |
| `house` | 9 | `legacy-house` |
| `housework` | 3 | `legacy-housework` |
| `housing` | 3 | `legacy-housing` |
| `important` | 1 | `legacy-important` |
| `ingredients` | 6 | `legacy-ingredients` |
| `injury` | 17 | `legacy-injury` |
| `introduction` | 7 | `legacy-introduction` |
| `introductions` | 3 | `legacy-introductions` |
| `jewelry` | 1 | `legacy-jewelry` |
| `kitchen` | 4 | `legacy-kitchen` |
| `landslide` | 2 | `legacy-landslide` |
| `language` | 9 | `legacy-language` |
| `language learning` | 2 | `legacy-language-learning` |
| `leisure` | 11 | `legacy-leisure` |
| `library/study` | 1 | `legacy-library-study` |
| `life` | 7 | `legacy-life` |
| `life goals` | 3 | `legacy-life-goals` |
| `lifestyle goals` | 3 | `legacy-lifestyle-goals` |
| `living room` | 8 | `legacy-living-room` |
| `location` | 14 | `legacy-location` |
| `loss` | 1 | `legacy-loss` |
| `medical` | 29 | `legacy-medical` |
| `meeting` | 1 | `legacy-meeting` |
| `memory` | 1 | `legacy-memory` |
| `miscellaneous` | 2 | `legacy-miscellaneous` |
| `mishap` | 2 | `legacy-mishap` |
| `money` | 3 | `legacy-money` |
| `movie` | 1 | `legacy-movie` |
| `movies` | 1 | `legacy-movies` |
| `moving` | 66 | `legacy-moving` |
| `music` | 13 | `legacy-music` |
| `musical aspirations` | 1 | `legacy-musical-aspirations` |
| `name` | 2 | `legacy-name` |
| `nationality` | 4 | `legacy-nationality` |
| `nature` | 10 | `legacy-nature` |
| `number` | 5 | `legacy-number` |
| `object` | 18 | `legacy-object` |
| `objects` | 5 | `legacy-objects` |
| `observations` | 1 | `legacy-observations` |
| `occupation` | 3 | `legacy-occupation` |
| `outing` | 1 | `legacy-outing` |
| `party` | 1 | `legacy-party` |
| `payment` | 86 | `legacy-payment` |
| `people` | 3 | `legacy-people` |
| `person` | 8 | `legacy-person` |
| `personal goals` | 9 | `legacy-personal-goals` |
| `pet` | 2 | `legacy-pet` |
| `pets` | 3 | `legacy-pets` |
| `photography` | 3 | `legacy-photography` |
| `place` | 11 | `legacy-place` |
| `places` | 4 | `legacy-places` |
| `plan` | 1 | `legacy-plan` |
| `plans` | 3 | `legacy-plans` |
| `polite` | 1 | `legacy-polite` |
| `possession` | 1 | `legacy-possession` |
| `preference` | 3 | `legacy-preference` |
| `preparation` | 6 | `legacy-preparation` |
| `price` | 3 | `legacy-price` |
| `problem` | 2 | `legacy-problem` |
| `profession` | 3 | `legacy-profession` |
| `purchase` | 1 | `legacy-purchase` |
| `quantity` | 3 | `legacy-quantity` |
| `question` | 39 | `legacy-question` |
| `questions` | 8 | `legacy-questions` |
| `reading` | 9 | `legacy-reading` |
| `reason` | 2 | `legacy-reason` |
| `relaxation` | 2 | `legacy-relaxation` |
| `religion` | 2 | `legacy-religion` |
| `request` | 6 | `legacy-request` |
| `rest` | 4 | `legacy-rest` |
| `room` | 6 | `legacy-room` |
| `safety` | 8 | `legacy-safety` |
| `scene` | 1 | `legacy-scene` |
| `schedule` | 3 | `legacy-schedule` |
| `school` | 10 | `legacy-school` |
| `season` | 5 | `legacy-season` |
| `self-improvement` | 3 | `legacy-self-improvement` |
| `selling` | 1 | `legacy-selling` |
| `shopping` | 28 | `legacy-shopping` |
| `sightseeing` | 20 | `legacy-sightseeing` |
| `skill` | 2 | `legacy-skill` |
| `sleep` | 17 | `legacy-sleep` |
| `social` | 9 | `legacy-social` |
| `social goals` | 1 | `legacy-social-goals` |
| `sport` | 4 | `legacy-sport` |
| `sports` | 3 | `legacy-sports` |
| `statement` | 4 | `legacy-statement` |
| `study` | 21 | `legacy-study` |
| `suggestion` | 1 | `legacy-suggestion` |
| `sympathy` | 1 | `legacy-sympathy` |
| `time` | 85 | `legacy-time` |
| `toilet` | 7 | `legacy-toilet` |
| `tradition` | 1 | `legacy-tradition` |
| `transport` | 1 | `legacy-transport` |
| `transportation` | 21 | `legacy-transportation` |
| `travel` | 34 | `legacy-travel` |
| `travel aspirations` | 1 | `legacy-travel-aspirations` |
| `travel plans` | 7 | `legacy-travel-plans` |
| `tsunami` | 4 | `legacy-tsunami` |
| `typhoon` | 1 | `legacy-typhoon` |
| `utensil` | 1 | `legacy-utensil` |
| `utensils` | 12 | `legacy-utensils` |
| `vehicle` | 10 | `legacy-vehicle` |
| `vehicles` | 1 | `legacy-vehicles` |
| `verb` | 15 | `legacy-verb` |
| `verbs` | 6 | `legacy-verbs` |
| `volcano` | 1 | `legacy-volcano` |
| `wants` | 1 | `legacy-wants` |
| `weather` | 54 | `legacy-weather` |
| `weekend` | 1 | `legacy-weekend` |
| `work` | 20 | `legacy-work` |
| `world goals` | 1 | `legacy-world-goals` |
| `writing` | 5 | `legacy-writing` |
| `yard` | 51 | `legacy-yard` |
