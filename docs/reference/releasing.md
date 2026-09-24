# Releasing

A release is a GitHub pre-release tagged `v<versionName>` on the merge of a `tech/release-<version>`
pull request, with the release APK attached.

## The version

`app-versionCode` and `app-versionName` in `gradle/libs.versions.toml`. The code goes up by one every
release: Android refuses an update whose version code is not higher than the one installed.

## The signing key

A release build is signed with a key that never enters the repository. The build reads it from four
Gradle properties, normally kept in `~/.gradle/gradle.properties`:

```
catsradar.release.storeFile
catsradar.release.storePassword
catsradar.release.keyAlias
catsradar.release.keyPassword
```

Without them `assembleRelease` still builds, but leaves `app-release-unsigned.apk`, which no phone will
install. Nothing else changes, which is why CI builds and checks without a key.

The key is the app's identity. Every later version has to be signed with the same key to install as
an update over the last one; a build signed with any other key installs only after the old app is
removed. Keep the keystore file and its password backed up somewhere other than this machine.

A debug build is signed with the machine's debug key, not this one, so a phone that has a debug build
installed has to remove it before the first release build will install. Export a backup first:
removing the app removes its cats.

## What a release build is

`assembleRelease` runs R8: it removes the code and resources nothing reaches, optimises what is left,
and renames classes and members to short names. A debug build does none of this, so a debug run
proves nothing about a release one.

- **A release stack trace is unreadable without its mapping.** Every build writes
  `app/build/outputs/mapping/release/mapping.txt`, which fits only the APK built with it, so it is
  attached, zipped, to the GitHub release next to that APK. `retrace mapping.txt stacktrace.txt` (the
  `retrace` tool from the SDK's Command-line Tools) turns the short names back.
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
2. On that merge, `./gradlew :app:assembleRelease`.
3. `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` shows the release
   key's certificate, not `Android Debug`.
4. Install that APK and go through the paths that work by class name: a tap on the home-screen
   widget, a tally (a worker attaches its location), a backup export and its import, and a screen
   other than Counter coming back after the process is killed in the background.
5. `gh release create v<versionName> --prerelease --target <merge commit>`, with the APK attached as
   `cats-radar-<versionName>.apk` and its `mapping.txt` zipped as `cats-radar-<versionName>-mapping.zip`.
