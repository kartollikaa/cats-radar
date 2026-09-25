# Updates

The **Updates** section of Settings has one button, *Check for updates*. It asks the app's GitHub
release list whether a version newer than the installed one has been published and, when there is
one, downloads it and installs it. The system's own confirmation screen is the last word: nothing
installs without the user saying yes there.

## Where releases come from

The GitHub REST API's release list of one repository, read anonymously:

```
GET https://api.github.com/repos/<owner>/<name>/releases?per_page=20
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
User-Agent: CatsRadar/<versionName>
```

- **The repository and the API are build settings**, `catsradar.updateRepository` and
  `catsradar.updateApi` in `gradle.properties`, which the application convention plugin puts into
  `BuildConfig.UPDATE_REPOSITORY` and `BuildConfig.UPDATE_API`. Pointing the app at another repository
  is one line; a verification build points the API at a local feed with `-Pcatsradar.updateApi=…`.
- **No token is ever sent.** A token built into the APK can be read out of it by anyone holding the file.
  A private repository therefore answers `404`, and the check reports the source as unavailable:
  checking needs a public repository, or a public one the releases are also published to.
- **The list, not `/releases/latest`.** GitHub's "latest" skips pre-releases, and every Cats Radar release
  so far is one ([releasing.md](../reference/releasing.md)). Drafts are never listed. A release counts only
  when it has an `.apk` asset; the `-mapping.zip` beside it is ignored.

## Which version is newer

A release's version is its tag without the leading `v`, ordered by
[SemVer 2.0 precedence](https://semver.org/#spec-item-11) (`AppVersion`):

- numbers compare as numbers: `1.9.0` < `1.10.0`;
- a pre-release ranks below the same numbers without one: `1.5.0-beta` < `1.5.0`;
- pre-release fields compare one by one, numbers as numbers and words as text: `1.5.0-beta.2` <
  `1.5.0-beta.10`, `1.5.0-beta` < `1.5.0-rc`;
- build metadata (`+7`) is ignored;
- a tag that is not a version (`nightly`) is skipped, as is a release without a package.

The newest release is offered when it is **higher** than the installed `versionName`
(`InstalledApp.isOlderThan`); the same version or a lower one is "up to date". Android itself refuses a
package whose `versionCode` is not higher, which the release process guarantees by raising it every time.

## The download

A newer release starts downloading at once — the owner's words were that the check downloads and
installs.

- **In a worker**, `DownloadUpdateWorker`, unique work with `KEEP` and a network constraint: leaving
  Settings or the app does not stop it, and a second tap while it runs starts nothing.
- **Into the app's cache**, `cacheDir/updates/<version>.apk`. The folder is cleared before every
  download, so there is only ever one package, and a half-written `.part` never survives a failure.
  No storage permission is involved. Android may clear the cache; that costs a download, nothing more.
- **Verified as it arrives** (`DownloadUpdate`): the file's SHA-256 is computed while it streams and must
  equal the `sha256:` digest GitHub reports for the asset, and its size the asset's size. A mismatch
  deletes the file and reports the download as failed; a truncated or swapped file never reaches the
  installer. An asset GitHub published without a digest is checked on its size alone.
- GitHub's download link redirects to where the file lives; the download follows it.
- Progress is written only when a new whole percent is reached, because each write is a database write.

## The install

- **A `PackageInstaller` session** (`MODE_FULL_INSTALL`, limited to this app's own package name) streams
  the file in and is committed with an explicit, mutable `PendingIntent` to `UpdateInstallReceiver`, which
  is not exported. The file never leaves the app through a content URI.
- **`STATUS_PENDING_USER_ACTION`** carries the system's confirmation screen, which the receiver starts.
  When the app has not been allowed to install apps, that screen is Android's refusal, with a button to
  the system page where the user allows Cats Radar.
- **On success** Android replaces the app and ends its process, so nothing reports success; the next
  launch is the new version. Everything the app keeps stays — the application id and the signing key are
  the same, so the database, the photos and the settings are the old ones.
- **A cancelled confirmation** (`STATUS_FAILURE_ABORTED`) goes back to offering *Install*; any other
  failure says the version was not installed and offers *Check for updates*, which downloads the package
  afresh — the same file could fail the same way forever (the cache cleared under it, a key that differs).
- `REQUEST_INSTALL_PACKAGES` is declared; without it Android refuses the session outright.
- A debug build is signed with another key than a release, so it cannot update to one: Android refuses
  the session.
- **Play Protect may step in** on a phone with Google Play: an app it has not seen before gets "App scan
  recommended" before the install, with *Scan app* (the APK goes to Google) or *Don't install app*. Declining
  ends the session as cancelled, so Settings offers *Install* again.

## Whether to install at once

A download this screen started installs as soon as it finishes — while the screen is started: its
download is read only then, because Android does not show the install confirmation to an app in the
background. A download that ends while the app is away installs when the user comes back to Settings. A download found finished by a Settings
opened later — after leaving the screen, or after Android ended the process — waits for *Install
<version>*. A finished download is reported by WorkManager again every time it is read; the run's id
keeps it from installing twice, and a package that is not newer than the installed version (the one just
installed) is ignored.

## What the section says

| State | Under the button | Button |
|---|---|---|
| nothing yet | what the button does | Check for updates |
| checking | a progress bar | unavailable |
| nothing newer | "You have the latest version" | Check for updates |
| downloading | "Downloading 1.5.0-beta · 45 %", a progress bar | unavailable |
| downloaded, not installed | "1.5.0-beta is downloaded and ready to install" | Install 1.5.0-beta |
| installing | "Installing 1.5.0-beta. Confirm it in the window Android shows" | unavailable |
| install failed | "1.5.0-beta wasn't installed" | Check for updates |
| download failed or damaged | the download didn't finish or arrived damaged | Check for updates |
| no connection | couldn't reach GitHub, check the connection | Check for updates |
| `404`, `403`, `429` or any other refusal | GitHub didn't share the releases, try later | Check for updates |
| an answer that is not a release list (a captive portal's page) | GitHub's answer couldn't be read | Check for updates |

The check runs only on the tap: nothing checks in the background and nothing notifies.

## Where the code lives

- `:domain` — `AppVersion`, `isOlderThan`; `PublishedRelease`, `ReleasePackage`, `ReleaseFeed`,
  `FeedFailure`, `DownloadResult` (`domain/update/`); the `UpdateSource` and `PackageDownloader` ports;
  `CheckForUpdate` and `DownloadUpdate`.
- `:data` — `parseGitHubReleases` (`commonMain`, kotlinx-serialization); `GitHubReleaseFeed` and
  `HttpPackageDownloader` (`androidMain`, `HttpURLConnection`). Their tests run against a local HTTP server.
- `:presentation` — `UpdateStateMapper` builds the status and the button; `SettingsStore` runs the check,
  follows the download and decides whether to install at once.
- `:ui` — the Updates section in `SettingsScreen`, which resolves the tokens to words.
- `:app` — `DownloadUpdateWorker`, `WorkManagerUpdateDownloadScheduler` and `toUpdateIntent`;
  `PackageInstallerUpdater`, `UpdateInstallReceiver` and `InstallResults`, which carries the session's
  ending from the receiver to the screen.
