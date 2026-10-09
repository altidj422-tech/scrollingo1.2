# Scrollingo

An Android app that turns doomscrolling into vocabulary practice. Scrollingo
watches for swipes in TikTok and Instagram Reels and, after every few videos,
drops a one-tap multiple-choice word quiz over the feed. Answer it and the feed
comes back.

Nothing about what you scroll is read, stored or uploaded — the accessibility
service only counts scroll events in the apps you pick.

## How it works

| Piece | File | Role |
| --- | --- | --- |
| Scroll detection | `ScrollDetectorService.kt` | Accessibility service; counts scroll events in the selected apps |
| Quiz overlay | `QuizOverlayManager.kt` | Draws the quiz over the feed (`SYSTEM_ALERT_WINDOW`) and blocks input behind it |
| Question building | `QuizGenerator.kt` | One correct answer + 3 distractors, same-category first so the choices stay plausible |
| Word choice | `QuizScheduler.kt` | Light spaced repetition: new words often, missed words sooner, mastered words rarely, never twice in a row |
| Word data | `Dictionary.kt` | Loads `assets/words_<code>.json`, preferring a newer downloaded pack |
| Background sync | `WordPackSync.kt` | Optional daily WiFi-only fetch of larger packs |
| Progress | `QuizProgress.kt` / `StatsNotifier.kt` | Today's accuracy, current streak, best streak, shown in a quiet notification |
| Pause switch | `ScrollingoTileService.kt` | Quick Settings tile to pause quizzes |
| Settings | `MainActivity.kt` / `Prefs.kt` | Language, videos-per-quiz (1–20, default 5), which apps, theme, notification toggle |

**Offline-first.** All five languages — Spanish, French, German, Italian,
Portuguese, 499 words each — ship inside the APK. A quiz never waits on the
network. If `WORDPACK_BASE_URL` is configured and a bigger pack downloads over
WiFi, it replaces the bundled one; if the host is unreachable or returns
something malformed, the app keeps using the words it already has.

## Requirements

- JDK 17
- Android SDK, `compileSdk` 34 (`minSdk` 28 — Android 9 and up)

## Build and run

```bash
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew :app:installDebug    # build and install onto a connected device
```

`./build.sh` is a convenience wrapper that sets `JAVA_HOME` / `ANDROID_HOME`
before calling Gradle.

On first launch the app asks for two permissions Android deliberately makes
manual: **Accessibility** (to notice scrolling) and **Display over other apps**
(to show the quiz).

## Tests

JVM unit tests, no device or emulator needed:

```bash
./gradlew testDebugUnitTest
```

They cover the logic that has no Android dependencies and the most expensive
things to get wrong:

- `QuizGeneratorTest` — the correct answer is always present and
  `correctIndex` always points at it, options never repeat, distractors prefer
  the same category, small packs degrade instead of crashing.
- `DictionaryParseTest` — the download gate. Valid packs parse; truncated,
  malformed, too-small or incomplete packs are rejected so a bad download can
  never replace a working bundled pack.
- `TargetAppsTest` — the package-name mapping. A missing package means
  Scrollingo silently stops firing on that app.
- `QuizProgressSnapshotTest` — accuracy maths, including the zero-answers case.

CI (`.github/workflows/android-ci.yml`) runs the unit tests and a debug build
on every push and pull request.

## Optional: hosting larger word packs

Set `WORDPACK_BASE_URL` in `app/build.gradle.kts` to a URL serving:

```
manifest.json      {"es": 3, "fr": 2, "de": 2, "it": 2, "pt": 2}
words_es.json      same shape as the bundled assets, plus a "version" field
words_fr.json ...
```

`tools/build_wordpacks.py` is the single source of truth for word data: it
checks for duplicate meanings and ambiguous answers, then writes
`words_<code>.json` for every language. Use it to produce hosted packs too, and
add a `manifest.json` with a bumped version per language. Leave the URL empty to
disable sync entirely.
