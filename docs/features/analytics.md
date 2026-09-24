# Crash reports and analytics

The app reports its crashes to Firebase Crashlytics, so a failure on a phone is seen without anyone
having to describe it. Every build reports, debug and release alike, and each report says which one
it came from. There is no switch to turn it off.

## What is sent

- **A crash.** Any exception nothing caught: its stack trace, the app version, the phone's model and
  Android version, and Crashlytics' own device state (free memory and disk, orientation). A crash is
  written to the phone as it happens and sent the next time the app starts.
- **A failure the app recovers from.** A few jobs are built to fail quietly and try again later; when
  one of them fails in a way nobody expected, the exception is sent as a *non-fatal* report and the
  job carries on exactly as it did before:
  - the two repairs that run at every start — tidying place cells, and rebuilding the app's own copy
    of a photo that has gone missing;
  - every background worker: attaching a location, naming places, purging deleted cats, importing
    photos, exporting and importing a backup.
  A worker that tries again later reports only its first failure, not one per retry. A job stopped
  by the system (cancelled, not failed) reports nothing.
- **`build_type`**, a custom key on every report: `debug` or `release`.

## What is never sent

No coordinate, geohash, place, country or city name; no photo or any part of one; no cat's id, time
or coat; the device id the backup format uses. Crashlytics is given no user id, no custom keys but
`build_type`, and no log lines. A stack trace names code, not data; an exception's *message*,
though, goes as whoever threw it wrote it — a file error from the platform names the path inside the
app's own storage it failed on.

## At the edges

- **Offline.** Reports wait on the phone and go when the network allows; nothing the user does waits
  for them.
- **No Play Services.** Crashlytics does not need them; reports still go.
- **A debug build installed from a computer** reports like any other, tagged `debug`.
- **The Firebase config** is `app/google-services.json`, registered for the application id
  `com.kartollika.catsradar`. A build under any other id fails at the google-services step rather
  than reporting into the wrong app.
- **Minify is off,** so stack traces arrive readable and there is no mapping file to upload.

## Where the code lives

- `build-logic/convention/src/main/kotlin/FirebaseConventionPlugin.kt` — `catsradar.firebase`:
  the google-services and Crashlytics Gradle plugins and the Firebase libraries. Only `:app`
  applies it.
- `app/google-services.json` — the Firebase project's config for this application id.
- `app/src/main/kotlin/dev/catsradar/app/reporting/` — `NonFatalReporter` (the port the app
  reports through, with `recordFailureOf`), `Crashlytics.kt` (its Crashlytics implementation and
  the `build_type` tag).
- `app/src/main/kotlin/dev/catsradar/app/StartupRepairs.kt` — runs the start-up repairs, each on
  its own, recording a failure instead of throwing it.
- `app/src/main/kotlin/dev/catsradar/app/worker/` — each worker's catch-all, and
  `WorkerFailures.kt` (`recordOnFirstAttempt`) for the ones that retry.
- Tests: `app/src/test/kotlin/dev/catsradar/app/StartupRepairsTest.kt`,
  `app/src/test/kotlin/dev/catsradar/app/worker/WorkerFailureReportingTest.kt`.

## Not handled yet

- Product usage events (which features are used, from where).
- A setting to turn reporting off (the owner chose always on).
