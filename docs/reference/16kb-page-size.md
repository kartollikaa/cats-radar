# 16 KB page size

Devices from Android 15 onwards may run with 16 KB memory pages instead of 4 KB. An app whose native
libraries were built for 4 KB runs in a compatibility mode, and on a debuggable system image the
platform says so in a dialog before the first screen:

> This app isn't 16 KB compatible. RELRO alignment check failed. … The following libraries are not
> 16 KB aligned: • lib/arm64-v8a/libdatastore_shared_counter.so : RELRO segment not aligned

The app ships no native code of its own. Everything in `lib/` arrives inside an AndroidX AAR, so the
only lever is which version of that AAR is on the classpath.

## What was actually wrong, and what was not

Three things have to hold, and only the third ever failed here:

| | Requirement | State |
|---|---|---|
| APK packaging | `.so` entries stored uncompressed, at 16 KB-aligned offsets | already correct — AGP does this |
| ELF load segments | every `PT_LOAD` has `p_align` ≥ 16384 | already correct in every dependency |
| ELF relocation-read-only segment | `PT_GNU_RELRO` fits the `PT_LOAD` that holds it | **failed** in one dependency |

So nothing in `build.gradle.kts`, the packaging block, or the version catalog's AGP entry needed
changing, and none of the usual advice about `useLegacyPackaging` or `zipalign -P 16` applied. Two of
the three were green all along, which is why measuring each one separately mattered more than
following the error message's suggestion to "recompile the application".

`androidx.datastore:datastore-core-android:1.2.0` was the one bad library. Its `PT_GNU_RELRO` covers
`[49152, 53248)` while the `PT_LOAD` holding it ends at `49744` — 3504 bytes of the read-only
relocation region fall outside the segment it belongs to. In **1.2.1** the two coincide exactly, at
`[37952, 49152)`. Upgrading is the whole fix.

The dialog also names `libsqliteJni.so` and `libandroidx.graphics.path.so`, each with "Unknown
error". Neither is at fault: once the datastore version was raised the dialog stopped appearing
while both of those libraries were untouched. Reading those two lines as three problems would have
sent the work after `androidx.graphics:graphics-path`, whose newest release (1.1.0) changes nothing
here and which cannot be dropped anyway — it is how `androidx.compose.ui:ui-graphics` iterates a
`Path` below API 34.

## How to check it

The platform's own verdict is the check, and the emulator this project uses is a 16 KB, API 37
image. Install, launch, and read the log:

```bash
adb logcat -c && adb shell am force-stop com.kartollika.catsradar && adb shell monkey -p com.kartollika.catsradar -c android.intent.category.LAUNCHER 1 && sleep 6 && adb logcat -d | grep -c PageSizeMismatchDialog
```

`0` is a pass. Anything else prints the offending libraries in the dialog itself.

Two cautions, both of which produced a wrong reading while this was being diagnosed:

- **`force-stop` does not close the dialog.** It belongs to `system_server`, so a stale one from the
  previous build stays on screen and reads like a fresh failure. Dismiss it, *then* relaunch.
- **A dismissed warning stays dismissed.** "Don't Show Again" suppresses it per package, after which
  the screen proves nothing and only the log does. Tap *OK*.

A pass is worth only as much as the matching failure, so confirm the check can still fail: put the
old version back, install, launch, and watch the dialog return.

## What `./gradlew check` does not cover

Nothing in the build detects this — not detekt, not Lint, not Konsist. `p_align` and zip offsets
could be checked offline, but neither was ever the failing condition, and the condition that did
fail is the platform's own and is not documented. A version bump that reintroduces it will be caught
by launching the app on a 16 KB device and not before.
