# Competitor scan — cat encounter logging apps

Date: 2026-09-21. Scope: apps that count, log, or map cats a person meets; adjacent
observation-logging apps whose patterns are worth borrowing.

## Direct competitors

| App | Platforms | What it does | Gaps relative to Cats Radar |
|---|---|---|---|
| [catch (cat tracker)](https://apps.apple.com/us/app/catch-cat-tracker/id6760037273) | iOS only (iOS 17+) | Closest analogue. "Log every cat you meet": encounter = photo + GPS + caption; on-device AI breed identification; social "discover" feed of other people's cats; collection framing. Free with Premium ($2.99/mo, $24.99/yr). 25 ratings, 4.5★. Ads + UGC. | No Android. Photo is mandatory — no one-tap tally. No rate/velocity statistics. Social-first, not stats-first. |
| [Whisker Tracker — Cat Finder](https://whiskertrackerapp.com/cat-app/) ([iOS](https://apps.apple.com/us/app/whisker-tracker-cat-finder/id6651852332), [Android](https://play.google.com/store/apps/details?id=com.whiskertracker.app)) | iOS + Android | Cat profiles, colonies, walk tracking, map of sightings, cat facial recognition to match lost-pet reports. | Rescue/TNR tool; heavy data entry per cat. No casual counter, no aggregate personal stats. |
| [StraySync](https://apps.apple.com/us/app/straysync-find-stray-cat-dog/id6742747753) | iOS | Crowdsourced map of stray cats and dogs: pin + photo + directions. | Community welfare map, not a personal log. Requires network. |
| [CatCompass](https://news.ycombinator.com/item?id=43348175) | Web/mobile | Crowdsourced stray-cat sightings; snap a photo, location auto-tagged. | Same: social map, no personal statistics. |
| [Purry](http://fifilaw.com/purry.php) | Mobile | Track stray/feral cats near you: photo, status note, current location. | Small hobby project; neighbourhood-scoped. |

Not a competitor but confirms the tap-tally UX: [SRR Counter for Dogs & Cats](https://apps.apple.com/us/app/srr-counter-for-dogs-cats/id1086456790) — one big button, tap per event, rate computed over a fixed window.

## Adjacent patterns worth borrowing

- **eBird vs iNaturalist** — eBird logs a *checklist with effort* (duration, distance) which makes
  rates meaningful; iNaturalist logs *casual observations* with no effort denominator. Cats Radar
  needs a denominator for "cats per minute" without asking the user to start a checklist →
  derive sessions automatically from encounter timestamps (see spec §5).
- **Pokémon / collection framing** — the top catch review: "almost just like Pokémon". Photos as
  a collection, not as records. Drives the Encounters screen design (grid of photos).
- **Rate over a window** (SRR Counter) — compute rate only where the window is long enough to be
  meaningful; show the window alongside the number.

## Positioning

White space: **one-tap tally + personal statistics with a rate metric + Android**. Every
existing app is either a social feed (catch, CatCompass, StraySync) or a rescue database
(Whisker Tracker). None offers a frictionless counter or velocity stats, and the closest
analogue has no Android build.

## Technology check (stack requested by the owner)

- Compose Multiplatform for iOS — Stable since 1.8.0 (May 2025);
  [1.9.x current](https://blog.jetbrains.com/kotlin/2025/09/compose-multiplatform-1-9-0-compose-for-web-beta/).
- [Jetpack Navigation 3 — stable 1.0.0](https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html)
  (Nov 2025). JetBrains multiplatform port:
  [`org.jetbrains.androidx.navigation3:navigation3-ui`](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
  — needs polymorphic serialization of destination keys for non-JVM targets. Recipes:
  [android/nav3-recipes](https://github.com/android/nav3-recipes),
  [terrakok/nav3-recipes](https://github.com/terrakok/nav3-recipes) (multiplatform).
- [Room KMP](https://developer.android.com/kotlin/multiplatform/room) — supported since 2.7.0 via
  `BundledSQLiteDriver`; [Room 3.0](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html)
  (alpha, Mar 2026) is KMP-first.
- Camera and location have no first-party KMP API — `expect/actual` over CameraX / system camera
  intent and FusedLocationProvider on Android, AVFoundation / CLLocationManager on iOS.
  Community options: [moko-permissions](https://github.com/icerockdev/moko-permissions),
  [moko-geo](https://github.com/icerockdev/moko-geo), [peekaboo](https://github.com/onseok/peekaboo).
- Offline reverse geocoding fallback: GeoNames `cities1000` → SQLite (~12 MB), e.g.
  [OfflineReverseGeocode](https://github.com/AReallyGoodName/OfflineReverseGeocode). Rejected for v1
  (bundle size); platform geocoder + geohash buckets instead.
