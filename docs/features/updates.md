# Updates

The **Updates** section of Settings has one button, *Check for updates*. It asks the app's GitHub
release list whether a version newer than the installed one has been published, and says what it
found under the button.

## Where releases come from

The GitHub REST API's release list of one repository, read anonymously:

```
GET https://api.github.com/repos/<owner>/<name>/releases?per_page=20
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
User-Agent: CatsRadar/<versionName>
```

- **The repository is a build setting**, `catsradar.updateRepository` in `gradle.properties`, which the
  application convention plugin puts into `BuildConfig.UPDATE_REPOSITORY`. Pointing the app at another
  repository is that one line.
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

The newest release is offered when it is **higher** than the installed `versionName`; the same version
or a lower one is "up to date". Android itself refuses a package whose `versionCode` is not higher,
which the release process guarantees by raising it every time.

## What the section says

| After the tap | Under the button |
|---|---|
| nothing yet | what the button does |
| checking | a progress bar; the button is unavailable, so a second tap asks nothing more |
| a newer release | "Version 1.5.0-beta is available" |
| nothing newer | "You have the latest version" |
| no connection | couldn't reach GitHub, check the connection |
| `404`, `403`, `429` or any other refusal | GitHub didn't share the releases, try later |
| an answer that is not a release list (a captive portal's page) | GitHub's answer couldn't be read |

The check runs only on the tap: nothing checks in the background and nothing notifies.

## Where the code lives

- `:domain` — `AppVersion`; `PublishedRelease`, `ReleasePackage`, `ReleaseFeed`, `FeedFailure`
  (`domain/update/`); the `UpdateSource` port; `CheckForUpdate`, which picks the newest release with a
  package and compares it with `InstalledApp`.
- `:data` — `parseGitHubReleases` (`commonMain`, kotlinx-serialization) keeps an asset's `sha256:` digest
  for the download to verify; `GitHubReleaseFeed` (`androidMain`, `HttpURLConnection`) maps a refused
  answer, a lost connection and an unreadable body to the three failures. `GitHubReleaseFeedTest`
  runs against a local HTTP server and pins the request.
- `:presentation` — `UpdateStateMapper` turns a result into an `UpdateStatus` token; `SettingsStore`
  ignores a tap while a check runs.
- `:ui` — the Updates section in `SettingsScreen`, which resolves the token to words.
