# Module Structure

Layered modules; every arrow points down. `:domain`, `:data`, `:presentation` are Kotlin
Multiplatform libraries with only the Android target configured today. `:ui` and `:app` are Android.

```
:app            Application: Navigation 3 host, Koin modules, WorkManager workers, Glance widget,
                FileProvider, MediaStore/SAF glue, string resources.
  └─ :ui        Compose: CatsRadarTheme, components, screens, previews. Renders State; builds nothing.
       └─ :presentation   MVI: Store base, one Store per screen, State/Intent/Effect, StateMappers,
                          DateTimeFormatter interface. No Compose; androidMain holds the formatter's
                          Android implementation and the four date words it cannot get from java.time.
            └─ :domain    Models, Tuning, pure functions (Geohash, SessionSplitter, StatsCalculator,
                          LocationPolicy, ImportRules), repository + platform interfaces, use cases.
                          Depends on kotlinx only.
:data ─────────► :domain  Room, DataStore, repository implementations, mappers; androidMain holds the
                          platform implementations (location, EXIF, geocoder, MediaStore, digest, resize).
:build-logic    Gradle convention plugins: kmp-library, android-library, android-application,
                compose, detekt. Modules apply plugins, never configure Gradle themselves.
```

Rules, enforced by a Konsist test in `:app` (`app/src/test/kotlin/dev/catsradar/app/architecture/`):

- `:domain` files do not import `android.*` or `androidx.*` (any depth).
- `:presentation` files do not import `androidx.compose.*` or `dev.catsradar.data`.
- `:ui` files do not import `dev.catsradar.data`.
- `:ui` files do not import Material's dynamic colour schemes: the wallpaper's colours are `:app`'s
  choice, and `CatsRadarTheme` without a scheme stays deterministic for previews.
- `:data` files do not import `dev.catsradar.presentation` or `dev.catsradar.ui`.
- `:domain`, `:presentation` and `:ui` files do not import `com.google.firebase`: analytics reaches
  them only as the `Analytics` port, and Firebase stays in `:data` and `:app`.
- Classes in `dev.catsradar.domain.analytics` take no `String`, `Double`, `Float`, `Instant` or
  `Duration` in their constructors: an event carries enums, booleans and counts, so it cannot carry a
  place, a name or a time.
- Each module's physical files declare that module's package (`dev.catsradar.<module>` or a
  subpackage) — otherwise the rules above, which key on the declared package rather than the
  physical module, would silently stop covering a mis-packaged file.

`:domain` depending on nothing but `kotlinx`, and `:ui` never depending on `:domain`, are
guaranteed by the Gradle module graph today — no `implementation(projects.domain)` declares that
edge from `:ui`, and `:domain`'s `build.gradle.kts` declares no project dependency at all — rather
than by a Konsist test. A future dependency edit could add either without a test catching it.

- Only `:app` knows Koin modules exist; the other modules expose constructors.

Source-set convention inside a KMP module: `commonMain` is the default home; `androidMain` holds
only the implementations that need the platform and the `actual` declarations; `commonTest` runs
with fakes; `androidHostTest` is for Robolectric-backed tests (Room DAOs, migrations).

Adding a screen: State/Intent/Effect/Store + mapper in `:presentation`, composables in `:ui`,
`NavKey` + entry + Koin registration in `:app`. Adding a platform capability: interface in
`:domain`, implementation in `:data/androidMain`, binding in `:app`. A capability only `:app`
code calls — the non-fatal crash reporter its workers and start-up repairs use — keeps its
interface and implementation in `:app`; it moves to `:domain` the day a use case needs it.
