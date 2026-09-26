---
name: device-check
description: Use when checking Cats Radar on an Android emulator or device, installing a branch build beside another session, walking a minified release, seeding device data, verifying Firebase, or automating taps without taking over someone else's emulator.
---

# Check Cats Radar on a device

## Overview

Device checks share emulator state with other sessions. Keep the package, database and UI target
explicit at every step; use a suffixed debug build or a throwaway AVD instead of replacing another
session's install.

The base package is `com.kartollika.catsradar`. Never pass `dev.catsradar` to `adb`, `run-as`,
`pidof` or `am`; it is only the Kotlin package inside the component name.

## Choose the device

- Shared `emulator-5554`: set `ANDROID_SERIAL=emulator-5554` and normally use a suffixed debug
  package. Never assume the app or database belongs to this branch.
- Fresh or isolated check: use `scripts/throwaway-avd.sh`. Only a source AVD whose `config.ini`
  names the `android-37.1` image is supported on this machine.
- Release walk, destructive data setup, permission-sensitive flow, or repeated taps: use a
  throwaway AVD, not the shared emulator.

After creating or booting an AVD, verify ownership before touching it:

```bash
export ANDROID_SERIAL=emulator-<port>
test "$(adb -s "$ANDROID_SERIAL" emu avd name | tr -d '\r')" = "$AVD_NAME"
```

## Build a suffixed debug APK

Use the shipped Groovy init script; a Kotlin init script cannot configure the AGP types. The
Google Services task must keep the registered base application id.

```bash
./gradlew -I .claude/skills/device-check/scripts/suffixed-debug.init.gradle \
  -PdeviceCheckSuffix=mycheck :app:assembleDebug --no-configuration-cache

AAPT2="$HOME/Library/Android/sdk/build-tools/$(ls "$HOME/Library/Android/sdk/build-tools" | sort -V | tail -1)/aapt2"
"$AAPT2" dump badging app/build/outputs/apk/debug/app-debug.apk | head -1
```

The package must be `com.kartollika.catsradar.mycheck`. Install and launch that exact package;
do not fall back to the base id when the check uses the suffix.

## Guard a shared install

Before `installDebug` or replacing the base package, compare Room versions. The branch version is
the greatest JSON filename under `data/schemas/dev.catsradar.data.db.CatsDatabase/`. Read bytes
60–63 of the device database as one big-endian integer:

```bash
PACKAGE=com.kartollika.catsradar
adb -s "$ANDROID_SERIAL" exec-out run-as "$PACKAGE" \
  od -An -t u1 -j 60 -N 4 databases/cats_radar.db
```

If the device version is newer, **do not install**. If the package or database exists but its
version cannot be read, do not guess: use a suffixed package or throwaway AVD. A branch with a
newer schema would upgrade shared data, so prefer isolation whenever the versions differ.

## Guard every interaction

Immediately before every tap, swipe, drag or key event:

```bash
adb -s "$ANDROID_SERIAL" shell dumpsys activity activities \
  | grep topResumedActivity | head -1
```

Continue only when the line contains the exact `$PACKAGE/`. Otherwise stop the sequence, bring
back `"$PACKAGE/dev.catsradar.app.MainActivity"`, and re-check. In a heredoc, every `adb shell`
gets `</dev/null` so it cannot consume the remaining commands.

Permission dialogs, Play Protect scans and lock-screen settings are owner decisions. Stop for the
owner or choose **Don't allow** when the scenario permits it. Never grant a permission with
`pm grant`, click **Allow** automatically, or enable/change a lock screen.

## Throwaway AVD lifecycle

```bash
bash .claude/skills/device-check/scripts/throwaway-avd.sh create MyCheck 5612 Release_Walk
bash .claude/skills/device-check/scripts/throwaway-avd.sh boot MyCheck 5612
export ANDROID_SERIAL=emulator-5612
adb -s "$ANDROID_SERIAL" emu avd name
# perform the guarded check
bash .claude/skills/device-check/scripts/throwaway-avd.sh delete MyCheck 5612
```

The helper copies configuration only, rejects a busy serial and verifies the AVD name before it
kills anything. Always delete the disposable AVD after qemu exits.

For database/photo seeding, widget placement, map zoom and Firebase logcat checks, read
[`references/device-recipes.md`](references/device-recipes.md).

## Common mistakes

| Mistake | Correct action |
|---|---|
| Installing the base debug APK on the shared emulator | Compare Room versions, then use a suffix when ownership is uncertain |
| Checking the foreground app once per script | Check immediately before every input event |
| Copying an entire `.avd` directory | Copy only `config.ini` and its `.ini` descriptor with the helper |
| Omitting `--no-configuration-cache` | Keep it on every suffixed build |
| Treating a permission prompt as automation | Stop for the owner or choose **Don't allow** |
