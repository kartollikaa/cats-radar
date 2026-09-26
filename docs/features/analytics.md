# Crash reports and analytics

The app reports its crashes to Firebase Crashlytics, so a failure on a phone is seen without anyone
having to describe it, and counts which screens are opened and what is done on them in Google Analytics for Firebase, so
it is clear which parts of the app are used and from where. Every build reports, debug and release alike, and each report
says which one it came from. There is no switch to turn it off. The app also reads its feature switches from
Firebase Remote Config.

## What is sent

### To Crashlytics

- **A crash.** Any exception nothing caught: its stack trace, the app version, the phone's model and
  Android version, and Crashlytics' own device state (free memory and disk, orientation). A crash is
  queued on the phone as it happens and sent moments later by a job the system starts on its own, or
  whenever the network next allows.
- **A failure the app recovers from.** A few jobs are built to fail quietly and try again later; when
  one of them fails in a way nobody expected — an exception, or an error such as running out of
  memory — it is sent as a *non-fatal* report, and the job ends the way it does for any unexpected
  failure: a repair waits for the next start, a one-off job fails, a job that retries tries again later:
  - the repair that runs at every start, tidying place cells;
  - every background worker: attaching a location, naming places, purging deleted cats, importing
    photos, exporting and importing a backup.
  A worker that tries again later reports only its first failure, not one per retry. A job stopped
  by the system (cancelled, not failed) reports nothing.
- **An app that stops responding** (an ANR), reported like a crash.
- **App sessions.** Crashlytics' session tracking sends when the app starts and moves between the
  foreground and the background, so a crash can be counted against how often the app is used.

### To Analytics

- **A screen view** each time a different screen comes to the top: `screen_view` with `screen_name`
  one of `counter`, `encounters`, `encounter_detail`, `photo_viewer`, `statistics`, `regions`, `map`,
  `map_spot`, `settings`. The same screen again with nothing in between is not counted twice; going back to a
  screen after another counts it again, and so does coming back to the app from the background;
  turning the phone, which rebuilds the screen, does not.
- **What was done**, one event per fact, logged only after the fact is written — a failed action
  logs nothing:

  | Event | Parameters | When |
  |---|---|---|
  | `cat_logged` | `kind` (`tally`, `photo`), `origin` (`app`, `widget`, `notification` for a tally; `camera` for a photo), `has_coat` (`true`/`false`) | a cat is saved |
  | `tally_undone` | — | the Undo chip removes a tally |
  | `coat_set` | `coat` (one of the eleven, or `none` when cleared) | a coat is written; setting the same coat again logs nothing |
  | `photo_attached` | `source` (`camera`, `gallery`) | a logged cat gets a photo |
  | `photos_imported` | `added`, `duplicates`, `failed` | a gallery import finishes (one event per batch, not per photo; a batch stopped midway logs nothing) |
  | `import_undone` | `count` | an import is undone |
  | `cats_deleted` | `count` | one cat or a selection is deleted |
  | `delete_undone` | `count` | a deletion is undone |
  | `backup_exported` | — | an archive is written |
  | `backup_imported` | `added`, `updated`, `unchanged` | a backup is merged |
  | `backup_rejected` | `reason` (`too_new`, `unreadable`) | a backup is refused |
  | `walk_started` | — | a walk starts (not when one is already open) |
  | `walk_ended` | `minutes` (whole) | a walk is ended; a walk cut off by the process dying logs nothing |

  Every parameter is a word from a fixed list or a count. A Konsist rule fails `check` if an event
  type ever gains a text, fraction or time field.
- **What Analytics collects on its own:** first open, sessions and time in the app, app and Android
  updates, the phone's model and Android version, and the country the phone's network address places
  it in.

### To Remote Config

- **A request for the app's switches**, made when Settings opens and the copy Remote Config keeps is older
  than its fetch interval, and a real-time channel held open for their changes while Settings stays open.
  The request carries what Remote Config sends to decide conditions: the installation id and its token, the
  app's id, package name and version, the SDK's version, the phone's language, country, time zone and
  Android version, and — since Analytics is present — the time of the app's first open and its user
  properties (`build_type`). Nothing the app records is in it. The one switch today is `in_app_updates`
  ([updates.md](./updates.md)).

### To both

- **A Firebase installation id**, random and made on the phone at install time; it ties one
  install's reports together and is not tied to a person or an account.
- **`build_type`** — `debug` or `release` — as a custom key on every crash report and a user property
  on every analytics event.

## What is never sent

No coordinate, geohash, place, country or city name the app knows; no photo or any part of one; no
cat's id, and no encounter time from the database — a cat's coat goes only as the word for it; the
device id the backup format uses. Analytics does stamp every event with the moment it was logged, so a
`cat_logged` from a tap says roughly when that cat was counted — never where. A screen view names the screen, never
what is on it: `regions` does not say which country or city was open. Neither service is given a user
id, and Crashlytics gets no custom keys but `build_type` and no log lines. A stack trace names code,
not data; an exception's *message*, though, goes as whoever threw it wrote it — a file error from the
platform names the path or URI it failed on, which is the app's own storage or a file the user picked
(a backup, a gallery photo).

**No advertising.** The Advertising ID is not collected; the permissions the Analytics SDK brings in
to read an ad id (Google's or Android's Privacy Sandbox one) and to report ad attribution are removed;
ad personalisation, ad storage and ad user data are off by default.

## At the edges

- **Offline.** Reports and events wait on the phone and go when the network allows; nothing the user
  does waits for them.
- **No Play Services.** Neither service needs them; reports and events still go.
- **A debug build installed from a computer** reports like any other, tagged `debug`.
- **The Firebase config** is `app/google-services.json`, registered for the application id
  `com.kartollika.catsradar`. A build under any other id fails at the google-services step rather
  than reporting into the wrong app.
- **Release builds are shrunk and renamed by R8.** A release built on this machine uploads its
  mapping to Crashlytics, so its crash reports arrive in real names with file names and line numbers;
  a CI build (the `CI` variable set) uploads nothing. Debug builds are not minified.
- **Only `:data` and `:app` may touch Firebase.** A Konsist rule fails `check` if a file in
  `:domain`, `:presentation` or `:ui` imports `com.google.firebase`; those layers see only the
  `Analytics` port.

## Where the code lives

- `build-logic/convention/src/main/kotlin/FirebaseConventionPlugin.kt` — `catsradar.firebase`:
  the google-services and Crashlytics Gradle plugins and the Firebase libraries. Only `:app`
  applies it; `:data` adds the Analytics library to its `androidMain` itself.
- `app/google-services.json` — the Firebase project's config for this application id.
- `app/src/main/AndroidManifest.xml` — the advertising switches, the removed `AD_ID` permission, and
  automatic screen reporting turned off (one activity hosts every screen, so it would only ever name
  that activity).
- `domain/src/commonMain/kotlin/dev/catsradar/domain/analytics/Analytics.kt` — the `Analytics` port,
  the `AnalyticsEvent` catalogue and `AnalyticsScreen`; each use case in the table above takes the
  port and logs after its write.
- `data/src/commonMain/kotlin/dev/catsradar/data/analytics/EncodedEvent.kt` — an event turned into
  Firebase's name and parameters; `data/src/androidMain/…/FirebaseAnalyticsReporter.kt` hands it over.
- `app/src/main/kotlin/dev/catsradar/app/navigation/ScreenViewTracker.kt` — which key is which
  screen, and not counting the same screen twice in a row; `CatsRadarNavHost` feeds it the top of the
  back stack.
- `app/src/main/kotlin/dev/catsradar/app/reporting/` — `NonFatalReporter` (the port the app
  reports through, with `recordFailureOf`), `CrashlyticsNonFatalReporter`, and `BuildTypeTag.kt`
  (`build_type` on both services, set first thing at start).
- `app/src/main/kotlin/dev/catsradar/app/StartupRepairs.kt` — runs the start-up repairs, each on
  its own, recording a failure instead of throwing it.
- `app/src/main/kotlin/dev/catsradar/app/worker/` — each worker's catch-all, and
  `WorkerFailures.kt` (`recordOnFirstAttempt`) for the ones that retry.
- Tests: `StartupRepairsTest`, `WorkerFailureReportingTest`, `ScreenViewTrackerTest` (`:app`),
  `AnalyticsEncodingTest` (`:data`), `AnalyticsEventsTest` (`:domain`), and the Firebase and
  event-field rules in `ModuleBoundaryTest`.

## Not handled yet

- A setting to turn reporting off (the owner chose always on).
