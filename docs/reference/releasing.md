# Releasing

A release is a GitHub pre-release tagged `v<versionName>` on the merge of a `tech/release-<version>`
pull request, with the release APK attached.

## The version

`app-versionCode` and `app-versionName` in `gradle/libs.versions.toml`. The code goes up by one every
release: Android refuses an update whose version code is not higher than the one installed.

## The signing key

A release build is signed with a key that never enters the repository. The keystore and a small
signing file live together outside it (on this machine, in `~/Projects/Signing/`). The signing file is
shared by every app signed with that key, not specific to this one; it holds four values, with
`storeFile` resolved against the signing file's own folder:

```
storeFile=kartollika_key_store.jks
storePassword=…
keyAlias=kartollikaaps
keyPassword=…
```

`~/.gradle/gradle.properties` holds a single line pointing at it, as an absolute path:

```
kartollika.signingFile=/Users/<you>/Projects/Signing/signing.properties
```

Without that line `assembleRelease` still builds, but leaves `app-release-unsigned.apk`, which no phone will
install. Nothing else changes, which is why CI builds and checks without a key.

The key is the app's identity. Every later version has to be signed with the same key to install as
an update over the last one; a build signed with any other key installs only after the old app is
removed. Keep the keystore and the signing file backed up somewhere other than this machine.

A debug build is signed with the machine's debug key, not this one, so a phone that has a debug build
installed has to remove it before the first release build will install. Export a backup first:
removing the app removes its cats.

## The application id

The app installs as `com.kartollika.catsradar`. Earlier builds installed as `dev.catsradar`, and
Android treats a different application id as a different app: the new build installs **beside** the
old one, with no cats, rather than updating it. To move the cats across:

1. In the old app, Settings → Backup → Export.
2. Install the new build and, in it, Settings → Backup → Import that file.
3. Check the count, then remove the old app.

A backup holds the cats, their photos and their places — not the app around them. The Settings
switches start from their defaults, every permission is asked for again, and a home-screen widget
belongs to the old app and goes with it: add the new one's widget again.

## What a release build is

`assembleRelease` runs R8: it removes the code and resources nothing reaches, optimises what is left,
and renames classes and members to short names. A debug build does none of this, so a debug run
proves nothing about a release one.

- **A release stack trace is unreadable without its mapping.** Every build writes
  `app/build/outputs/mapping/release/mapping.txt`, which fits only the APK built with it, so it is
  attached, zipped, to the GitHub release next to that APK. `retrace mapping.txt stacktrace.txt` (the
  `retrace` tool from the SDK's Command-line Tools) turns the short names back. A release built on
  this machine also uploads its mapping to Crashlytics, so its crash reports arrive in real names; a
  build with the `CI` variable set (every GitHub Actions run) uploads nothing.
- **Code that creates a class from its name breaks only at run time.** R8 cannot see that use, so it
  drops the constructor or the class. Libraries ship rules for what they look up;
  `app/proguard-rules.pro` covers what theirs miss. No test runs the minified code, which is why a
  release is launched on a device before it is tagged.
- **Resources are shrunk in strict mode** (`app/src/main/res/raw/keep.xml`): a resource reached only
  through a name built at run time (`Resources.getIdentifier`) is removed. Reference resources
  through `R`.
- **It carries native libraries for `arm64-v8a` only, compressed.** A phone with a 32-bit ARM or an
  x86 CPU cannot install it. The libraries are unpacked when the app is installed, which costs
  storage on the phone and saves it on every download. A debug build keeps every ABI, uncompressed.

## Cutting one

1. Merge a `tech/release-<version>` pull request that bumps both version values and marks the epic's
   slices in its decomposition map.
2. On that merge, `./gradlew :app:assembleRelease` with `CI` unset: the same run uploads its mapping to
   Crashlytics. A rebuild can stamp a new mapping id, so the APK and mapping attached below come from
   this one run — a rebuild could ship an APK whose crash reports Crashlytics cannot read.
3. `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` shows the release
   key's certificate, not `Android Debug`.
4. Install that APK and go through the paths that work by class name: a tap on the home-screen
   widget, a tally (a worker attaches its location), a backup export and its import, and a screen
   other than Counter coming back after the process is killed in the background.
5. `gh release create v<versionName> --prerelease --target <merge commit>`, with the APK attached as
   `cats-radar-<versionName>.apk` and its `mapping.txt` zipped as `cats-radar-<versionName>-mapping.zip`.
