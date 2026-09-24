# Firebase: crash reports and product analytics — design spec

Date: 2026-09-24. Status: v1.
Parent spec: [2026-09-21-cats-radar-design.md](./2026-09-21-cats-radar-design.md).

## 1. Summary

The app reports its crashes to Firebase Crashlytics and a fixed catalogue of product events to Google
Analytics for Firebase, so the owner can see what breaks and which features are used. Nothing that
places a cat — or the person counting it — ever leaves the phone.

### Goals

1. Every uncaught crash, in every build, reaches Crashlytics with the build type attached.
2. Failures the app swallows and retries on its own reach Crashlytics as non-fatals.
3. A typed event catalogue answers "which features are used, from where": cats logged by origin,
   coats, photos, imports, deletions, backups, walks, and which screens are opened.
4. By construction, no event can carry a coordinate, a place, a photo, an id, or a time.

### Non-goals

- A consent switch or an opt-in screen (owner: always on).
- Remote Config, Performance Monitoring, Cloud Messaging, A/B testing, BigQuery export.
- Funnels or dashboards in the Firebase console — that is console work, not code.
- iOS; the Firebase implementation is Android-only, behind a port a later iOS target can implement.
- Renaming the Kotlin packages away from `dev.catsradar.*`.

### Decisions taken with the owner (2026-09-24)

| Question | Decision |
|---|---|
| Application id | `com.kartollika.catsradar`, replacing the placeholder `dev.catsradar`. Kotlin packages and module namespaces stay `dev.catsradar.*`. |
| Firebase config | The owner creates the Firebase project and registers the Android app; `app/google-services.json` is committed (private repo; the key in it is restricted to the package). |
| Consent | Always on. No switch in Settings. |
| Which builds report | Every build, debug and release alike, tagged with `build_type`. |

## 2. The application id

`applicationId` becomes `com.kartollika.catsradar`. Android treats the application id and the Kotlin
package as independent, so no source file moves.

An installed app never updates across an application id change: the new build installs **beside** the
old one, with an empty database. Moving the cats is the existing backup round trip — export a ZIP from
the old app, import it into the new one, then remove the old app. `docs/reference/releasing.md` says so.

Everything already keyed by `${applicationId}` in the manifest (the `FileProvider` and startup
authorities) follows by itself. Intent action strings (`dev.catsradar.action.*`) are names, not ids,
and stay.

## 3. What leaves the phone

**Sent.** Crash stack traces and device/OS model (Crashlytics' own); the events and parameters in §5;
the user property `build_type`; what Firebase Analytics collects on its own — an app-instance id,
sessions, first open, app and OS updates, device model, OS version, country derived from the IP.

**Never sent.** Latitude, longitude, accuracy, geohash, place-cell ids, country/city/area names, photos
or any part of them, encounter/walk ids, encounter times, the sync device id; no event parameter holds
a file path or URI. A crash report carries the exception's message as it was thrown, so a platform
file error names the app-private path it failed on.

**Advertising.** The Advertising ID is not collected (`google_analytics_adid_collection_enabled` off),
the `com.google.android.gms.permission.AD_ID` permission the SDK merges in is removed, and ad
personalisation signals are off by default.

Events queue on the phone while offline and are sent when the network allows; logging never waits on
the network and never fails a user action.

## 4. Architecture

| Module | Holds |
|---|---|
| `:domain` | `AnalyticsEvent` — a sealed catalogue whose parameters are enums, booleans and counts only; `AnalyticsScreen` enum; `Analytics` port (`fun log(event: AnalyticsEvent)`). Use cases call the port after the fact they report has happened. |
| `:data` | `commonMain`: the pure encoding of an `AnalyticsEvent` into an event name and a parameter map. `androidMain`: `FirebaseAnalyticsReporter` implementing `Analytics` — encode, convert to a `Bundle`, hand to Firebase. |
| `:app` | Koin binding; the google-services and Crashlytics Gradle plugins; the `build_type` custom key and user property at process start; non-fatal recording at the app's swallow-and-retry sites; screen views from the Navigation 3 back stack. |

Why use cases and not Stores: a cat is logged from the Counter, the widget, the walking notification
and the camera flow; an import finishes in a worker. Only the use case sees every one of them, so each
fact is logged in exactly one place.

A Konsist rule keeps `com.google.firebase` imports inside `dev.catsradar.data..` and
`dev.catsradar.app..`.

The Firebase build wiring (google-services and Crashlytics plugins, the BoM) lives in a new
`catsradar.firebase` convention plugin that only `:app` applies; `catsradar.android.application` stays
Firebase-free, so another application module never needs a `google-services.json`.

## 5. Event catalogue

Names and parameter keys are `snake_case`; enum values are sent lowercase. Counts are integers.

| Event | Parameters | Logged by | When |
|---|---|---|---|
| `cat_logged` | `kind` (tally, photo), `origin` (app, widget, notification for a tally; camera for a photo), `has_coat` | `LogTally`, `LogPhoto` | the encounter is inserted |
| `tally_undone` | — | `UndoLastTally` | the tally is removed |
| `coat_set` | `coat` (a `CatCoat` value, or `none` when cleared) | `SetCoat` | the coat is written |
| `photo_attached` | `source` (camera, gallery) | `AttachPhoto` | the photo is attached |
| `photos_imported` | `added`, `duplicates`, `failed` | `ImportPhotos` | a batch finishes |
| `import_undone` | `count` | `UndoImport` | the batch is removed |
| `cats_deleted` | `count` | `DeleteEncounter`, `DeleteEncounters` | the deletion is written |
| `delete_undone` | `count` | `UndoDelete`, `UndoDeleteEncounters` | the deletion is reverted |
| `backup_exported` | — | `ExportBackup` | the archive is written |
| `backup_imported` | `added`, `updated`, `unchanged` | `ImportBackup` | a merge is written |
| `backup_rejected` | `reason` (a `BackupRejection` value) | `ImportBackup` | the archive is refused |
| `walk_started` | — | `StartWalk` | the walk is recorded |
| `walk_ended` | `minutes` | `EndWalk` | the walk is closed |
| `screen_view` | `screen_name` (counter, encounters, encounter_detail, statistics, regions, map, map_spot, settings) | `:app` navigation | the top of the back stack changes |

`screen_view` is Firebase's predefined event. Automatic screen reporting is turned off: the app is one
activity, so it would only ever report that activity. `regions` carries no level and no place.

Gallery photos are counted by `photos_imported`, not one `cat_logged` each. A failed action logs
nothing: an unreadable photo or a refused attach leaves the catalogue untouched; a refused backup is
the one failure with its own event.

## 6. Crashlytics

- Uncaught exceptions: automatic once the SDK is in the app.
- Custom key `build_type` (`debug` / `release`), set at process start before anything else can fail.
- Non-fatals: the startup repairs (`RepairPlaceCells`, `RegeneratePhotoCopies`) and every worker's
  catch-all branch record the exception they swallow. A worker that answers with a retry records only
  on its first attempt, so one persistent failure is one event, not one per backoff.
  `CancellationException` is never recorded.
- No user id, no custom logs containing user data.
- Minify is off in every build, so there is no mapping file to upload.

## 7. Testing

- `:domain` `commonTest`: a recording fake `Analytics`; each use case listed in §5 has a test asserting
  the exact event (and only it) on success, and no event on its failure path.
- `:data` `commonTest`: the encoding asserted with `assertEquals` for every event — name and full
  parameter map — plus a test that every event name and parameter key fits Firebase's limits (≤40
  characters, `[a-zA-Z][a-zA-Z0-9_]*`, no `firebase_`/`google_`/`ga_` prefix).
- `:app` test: the `NavKey → AnalyticsScreen` mapping for every key.
- Konsist: the `com.google.firebase` import rule, proven by breaking it once.
- On a device: a debug build's events appear in Firebase DebugView; a forced test crash appears in
  Crashlytics with `build_type = debug`.

## 8. Documentation

- New `docs/features/analytics.md`: what is sent, what never is, the catalogue, the edges (offline
  queueing, no switch, every build), where the code lives.
- `docs/features/map.md`: the map is no longer "the one screen that goes online".
- `docs/features/README.md`: index entry.
- `docs/reference/releasing.md`: the application id change and the backup round trip; the committed
  Firebase config.
- `docs/rules/module-structure.md` and `docs/rules/static-analysis.md`: the new Konsist rule.
- Parent spec: decisions table, §6.1, §6.5, §8, §10.

## 9. Risks

- **Gradle plugins on AGP 9.** The google-services and Crashlytics Gradle plugins must work with the
  project's AGP; verified first in the Crashlytics slice. If the Crashlytics plugin does not, the app
  still gets crash reports without it, because minify is off and there is no native code: the manifest
  sets `com.crashlytics.RequireBuildId` to `false`, without which the SDK refuses to start when the
  plugin's build id resource is missing.
- **Config mismatch.** The google-services plugin fails the build when `google-services.json` has no
  client for the application id; that is the desired failure, not one to work around.
