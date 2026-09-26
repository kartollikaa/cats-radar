# Build info

The last section of Settings, **About**, says which build is installed on which phone, and copies
the same facts, and a few more, for whoever is debugging a report.

## What it shows

| Row | Value |
|---|---|
| Version | `versionName (versionCode)`, e.g. `1.4.1-beta (7)` |
| Build | build type and commit, e.g. `release · 5989a92c1f3e` |
| Device | manufacturer and model, e.g. `Google Pixel 7` |
| Android | release and API level, e.g. `16 (API 36)` |

The device name drops a manufacturer the model already starts with (`Xiaomi` + `Xiaomi 13` reads
`Xiaomi 13`) and capitalises a lowercase one (`samsung` reads `Samsung`).

## The copy button

The icon in the section's header puts a plain-text report on the clipboard:

```
Cats Radar 1.4.1-beta (7)
Build type: release
Commit: 5989a92c1f3e
Application id: com.kartollika.catsradar
Installed by: com.google.android.packageinstaller
Device: Google Pixel 7 (panther)
Android: 16 (API 36)
ABIs: arm64-v8a
Locale: ru-RU
Time zone: Europe/Moscow
Database: 4
```

- **Its keys are English in every language**, so a developer can read a report from any phone; the
  labels on screen are translated. This is the one piece of text built in `:presentation` that is
  not a token (see [strings.md](./strings.md)).
- **Commit** is `git rev-parse --short=12 HEAD` of the tree the APK was built from, read by the
  application convention plugin into `BuildConfig.GIT_COMMIT`; outside a git checkout it is
  `unknown`. It is what maps a crash or a report to the code.
- **Installed by** is the package Android records as the installer — a browser, a file manager,
  `adb` shows as none. **Locale** is the app's own, **Time zone** the phone's. The report is read
  afresh on every tap, so it names the language and zone the phone has at that moment, even when
  they changed while Settings stayed open.
- **Database** is the Room schema version (`CATS_DATABASE_VERSION`), the first thing a backup or
  migration question needs. `DatabaseVersionTest` keeps it equal to the newest exported schema.
- Nothing personal is in it: no id, no location, no cat.
- Android 13 and later confirm every copy themselves, so the app shows a toast only below 13.

## Where the code lives

- `:domain` — `InstalledApp`, `DeviceInfo`, `BuildInfo` (`domain/about/`), the `BuildInfoReader` port.
- `:data` — `AndroidBuildInfoReader`, off the main thread: `Build`, the configuration's locale, the
  install source (`getInstallSourceInfo` from Android 11, `getInstallerPackageName` on 10).
- `:presentation` — `AboutStateMapper` builds the rows and the report; `SettingsStore` reads the
  build info for the rows when it starts and again for each copy, which it emits as `CopyBuildInfo(report)`.
- `:ui` — the About section in `SettingsScreen`; `SectionCard` has an `action` slot for the icon.
- `:app` — `InstalledApp` from `BuildConfig` in `dataModule`; `copyBuildInfo` writes the clipboard
  from `SettingsDestination`.
