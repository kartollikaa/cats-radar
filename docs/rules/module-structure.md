# Module Structure

Layered modules; every arrow points down. `:domain`, `:data`, `:presentation` are Kotlin
Multiplatform libraries with only the Android target configured today. `:ui` and `:app` are Android.

```
:app            Application: Navigation 3 host, Koin modules, WorkManager workers, Glance widget,
                FileProvider, MediaStore/SAF glue, string resources.
  └─ :ui        Compose: CatsRadarTheme, components, screens, previews. Renders State; builds nothing.
       └─ :presentation   MVI: Store base, one Store per screen, State/Intent/Effect, StateMappers,
                          DateTimeFormatter interface. No Compose, no Android.
            └─ :domain    Models, Tuning, pure functions (Geohash, SessionSplitter, StatsCalculator,
                          LocationPolicy, ImportRules), repository + platform interfaces, use cases.
                          Depends on kotlinx only.
:data ─────────► :domain  Room, DataStore, repository implementations, mappers; androidMain holds the
                          platform implementations (location, EXIF, geocoder, MediaStore, digest, resize).
:build-logic    Gradle convention plugins: kmp-library, android-library, android-application,
                compose, detekt. Modules apply plugins, never configure Gradle themselves.
```

Rules, enforced by a Konsist test in `:app`:

- `:domain` imports nothing from `android.*`, `androidx.*`, or any other module.
- `:presentation` does not import `androidx.compose.*` or `:data`.
- `:ui` does not import `:data` or `:domain` use cases — it sees `:presentation` types only.
- `:data` does not import `:presentation` or `:ui`.
- Only `:app` knows Koin modules exist; the other modules expose constructors.

Source-set convention inside a KMP module: `commonMain` is the default home; `androidMain` holds
only the implementations that need the platform and the `actual` declarations; `commonTest` runs
with fakes; `androidHostTest` is for Robolectric-backed tests (Room DAOs, migrations).

Adding a screen: State/Intent/Effect/Store + mapper in `:presentation`, composables in `:ui`,
`NavKey` + entry + Koin registration in `:app`. Adding a platform capability: interface in
`:domain`, implementation in `:data/androidMain`, binding in `:app`.
