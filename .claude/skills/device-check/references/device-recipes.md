# Device recipes

Keep `ANDROID_SERIAL` and `PACKAGE` explicit in every command. Run the foreground-package guard
from `device-check` immediately before each input event.

## Copy a database safely

Stop the source app, pull `cats_radar.db`, `cats_radar.db-wal` and `cats_radar.db-shm` with
`adb exec-out run-as "$PACKAGE" cat ...`, then checkpoint the host copy:

```bash
sqlite3 copy.db 'PRAGMA wal_checkpoint(TRUNCATE); PRAGMA journal_mode=DELETE;'
adb -s "$ANDROID_SERIAL" push copy.db /data/local/tmp/cats_radar.db
adb -s "$ANDROID_SERIAL" shell am force-stop "$PACKAGE"
adb -s "$ANDROID_SERIAL" shell </dev/null \
  "run-as '$PACKAGE' sh -c 'mkdir -p databases; cp /data/local/tmp/cats_radar.db databases/cats_radar.db; rm -f databases/cats_radar.db-wal databases/cats_radar.db-shm'"
```

Do this only on a suffixed package or throwaway AVD. An `exec-in` copy of the WAL can become a
zero-byte file; checkpoint and copy only the main database.

## Seed photos

```bash
adb -s "$ANDROID_SERIAL" push sample.jpg /sdcard/Pictures/sample.jpg
adb -s "$ANDROID_SERIAL" shell </dev/null \
  content call --method scan_volume --uri content://media --arg external_primary
```

Open the app's gallery action, then verify the system picker is the resumed activity. On the
android-37.1 image a selected photo is returned only after tapping **Done**. Do not automate any
permission prompt; **Don't allow** keeps the import flow usable.

## Place the widget

Open the launcher widget picker, search for Cats Radar, select the row for the intended install and
drag its preview to the home screen. Several packages share the same label, so prove which one was
placed:

```bash
adb -s "$ANDROID_SERIAL" shell dumpsys appwidget \
  | sed -n '/^Widgets:/,$p' | grep 'provider='
```

Continue only when the provider contains the exact `$PACKAGE/`.

## Zoom the map without a pinch gesture

Avoid clusters, then send a real double tap around the target:

```bash
adb -s "$ANDROID_SERIAL" shell </dev/null \
  "input tap X Y & sleep 0.12; input tap X Y"
```

Re-check `topResumedActivity` before every double tap and re-aim after each screenshot because the
target moves as the camera zooms.

## Firebase logcat

The suffix init script keeps Google Services resource generation on the registered base id. A
GoogleCertificates warning for the suffixed package is expected; Firebase still initializes.

```bash
adb -s "$ANDROID_SERIAL" shell setprop log.tag.FA VERBOSE
adb -s "$ANDROID_SERIAL" shell setprop log.tag.FA-SVC VERBOSE
adb -s "$ANDROID_SERIAL" shell setprop debug.firebase.analytics.app "$PACKAGE"
adb -s "$ANDROID_SERIAL" logcat -d -s FA-SVC FirebaseCrashlytics
```

Analytics events appear as `FA-SVC: Logging event`. For Crashlytics, enable its DEBUG tag and
observe an owner-approved test crash; do not crash another session's base package.
