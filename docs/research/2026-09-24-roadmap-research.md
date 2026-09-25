# What comes next — roadmap research

Date: 2026-09-24. Builds on the [competitor scan](./2026-09-21-competitor-scan.md) and the spec's
[§9 Roadmap after v1](../superpowers/specs/2026-09-21-cats-radar-design.md#9-roadmap-after-v1).
Nothing here is decided. It is a ranked menu for the owner; an idea enters the spec, and gets a
decomposition map, only once he picks it.

Sizes: **S** one PR · **M** two to four PRs · **L** an epic with its own decomposition map.

## 1. Where the app stands

- v1 and the whole Map epic have shipped: map tab, clustering, outing routes, heatmap with a coat
  filter, walk tracks recorded and drawn on the map, distance walked and cats per km
  ([map epic](../tbd/decompositions/2026-09-23-map-epic.md)).
- Photos for logged cats and the coat question after a photo have shipped
  ([cat-photos map](../tbd/decompositions/2026-09-23-cat-photos.md)).
- Loose ends already written down: the design pass has not reached the list, detail and statistics
  screens ([app-shell.md](../features/app-shell.md)); the follow-ups in the cat-photos decision log;
  a cat's row at the bottom of Places does not open the cat ([places.md](../features/places.md)); a
  reboot mid-walk without location leaves the flag on ([walking-mode.md](../features/walking-mode.md));
  the release signing key ([releasing.md](../reference/releasing.md)).
- **One gap nobody wrote down:** `allowBackup` is false and `data_extraction_rules.xml` excludes
  every domain from both cloud backup and device-to-device transfer. A new phone starts with zero
  cats unless a ZIP was exported by hand first.

## 2. What the research found

### The market moved; the white space did not

A wave of "Pokémon GO for real cats" apps has appeared since the first scan:
[CatchCat](https://www.catchcat.lol/) (Android + iOS; cards with rarity, names and battle stats, XP,
streaks, a community map, an on-device check that the photo shows a live cat),
[Catemon](https://play.google.com/store/apps/details?id=app.catemon.catch) (catch a cat as a sticker;
personal, friends' and global maps), [Cat Collector](https://catcollector.app/) (iOS, no sign-in,
iMessage stickers, a per-neighbourhood "Top Cat"), [HoodCat](https://hood-cat.com/neighborhood-cats/)
(neighbourhood map; detects the cat in a photo and compares it with nearby sightings), and a
relaunched [catch](https://apps.apple.com/us/app/catch-cat-tracker/id6760037273) (breed ID, social
feed). [Whisker Tracker](https://whiskertrackerapp.com/) went further into rescue and colony
management.

Every one of them is social and online by default. None is a private, offline counter with rates,
outings or a walk track — the positioning from the first scan still holds.

### People remember individual cats

HoodCat's sighting matching, CatchCat naming every card, Cat Collector's "Top Cat",
[Neko Atsume](https://en.wikipedia.org/wiki/Neko_Atsume)'s named visitors, and real neighbourhood
celebrities such as Leiden's [Buurtpoes Bledder](https://en.wikipedia.org/wiki/Buurtpoes_Bledder) all
point one way: the regular cat on your street is what people talk about. The spec lists unique-cat
identity as a v1 non-goal; it is the strongest candidate to revisit (L1).

### Mechanics that work without a server

| Mechanic (where it comes from) | In Cats Radar |
|---|---|
| Patch and year lists ([eBird](https://support.ebird.org/en/support/solutions/articles/48001049078-patch-and-yard-lists-in-ebird)) | A list per year and per country; "my patch" from the region tree (N2) |
| Personal records, Big Day (eBird, Strava) | Best day, best outing, fastest ten cats (N1) |
| Personal heatmap ([Strava](https://support.strava.com/en-us/articles/15402028-personal-heatmaps)) | Already built (M7) |
| Daily postcard (Pikmin Bloom) | An outing postcard from the walk track and its photos (N4) |
| Year in review (Spotify Wrapped, Strava) | A year in cats, computed and rendered on the phone (N4) |
| Streak freeze (Duolingo) | A streak that forgives a sick day (N3) |
| Badges without an account ([Seek](https://www.inaturalist.org/pages/seek_app)) | Achievements derived from the log (N3) |
| Named visitors (Neko Atsume) | Named regulars, real ones (L1) |

One surface, not a parallel game: every item above ends in the statistics the app already keeps.

### Citizen science is a poor fit

GBIF's own [filtering guide](https://data-blog.gbif.org/post/gbif-filtering-guide/) treats domestic
and captive organisms as something to exclude, and iNaturalist
[argues over](https://forum.inaturalist.org/t/research-grade-domestic-animal/11345) whether domestic
cats belong in research grade at all. The free-roaming-cat studies that do want data
([Cat Tracker](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6262432/), TNR colony registers) track
colonies, caretakers and health, which this app does not collect. Not worth an export format.

### Technology check

| Idea | What exists | Verdict |
|---|---|---|
| "No cat in this photo" on import | ML Kit Image Labeling has a `Cat` label ([label map](https://developers.google.com/ml-kit/vision/image-labeling/label-map)), on device | fits now, S |
| Count and crop cats in a photo | ML Kit Object Detection has no animal class ([docs](https://developers.google.com/ml-kit/vision/object-detection)); MediaPipe's EfficientDet-Lite (COCO `cat`) does ([docs](https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector)), a few MB | fits later, M |
| Coat suggestion from a photo | No dataset matches the eleven coats; colour heuristic inside the cat's box first | fits later, M |
| Same cat again, from photos | [PetFace](https://dahlian00.github.io/PetFacePage/) benchmarks; a [DINOv2-small animal-ID](https://huggingface.co/AvitoTech/DINO-v2-small-for-animal-identification) model runs on device; street photos will score well below lab numbers | fits later, L, suggest-and-confirm only |
| Gemini Nano captions and recaps | ML Kit GenAI on a short device list; Prompt API still alpha ([blog](https://android-developers.googleblog.com/2025/08/the-latest-gemini-nano-with-on-device-ml-kit-genai-apis.html)) | not yet |
| Quick Settings tile, app shortcuts | `TileService`, static shortcuts ([docs](https://developer.android.com/develop/ui/views/quicksettings-tiles)) | fits now, S |
| Live Update for the walk | Already built, from API 36.1 | done |
| Lock-screen widgets | Still rolling out on phones | not yet; the walking notification already serves the lock screen |
| Wear OS tile and complication | Tiles + Data Layer sync; a second app surface | fits later, L |
| Walk into Health Connect | `ExerciseSessionRecord` with an `ExerciseRoute` ([docs](https://developer.android.com/health-and-fitness/health-connect/features/exercise-routes)) | fits now, S–M |
| Shareable image | Compose `GraphicsLayer.toImageBitmap()` + FileProvider; maplibre-compose has no snapshotter yet ([#28](https://github.com/maplibre/maplibre-compose/issues/28)) | fits now without map tiles, M |
| Keep cats across phones | [Auto Backup](https://developer.android.com/identity/data/autobackup) caps cloud at 25 MB per app; device-to-device transfer has its own rules — its limit is to verify before relying on it for photos | fits now, S |
| Two phones, no server | Backup ZIP to a folder both phones see (SAF), reusing the existing merge; Drive `appDataFolder` ([docs](https://developers.google.com/workspace/drive/api/guides/appdata)) means writing the sync loop; PowerSync, Automerge and the like need a backend or unofficial bindings | SAF fits, M; the rest do not |
| Offline map for a trip | PMTiles region download; MapLibre's offline packs do not cover PMTiles, so the app owns the download ([discussion](https://github.com/maplibre/maplibre-native/discussions/3764)) | fits later, M |
| Offline place names | GeoNames `cities1000`, ~12 MB ([example](https://github.com/AReallyGoodName/OfflineReverseGeocode)) | fits if "Not named yet" stays large |
| iOS | Compose Multiplatform, Navigation 3 ([port](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)), Room, Koin and maplibre-compose all run there; background walk tracking and the widget need native rewrites | fits later, L |
| Volume key as a tally button | Needs an `AccessibilityService`, which Play [restricts](https://support.google.com/googleplay/android-developer/answer/10964491) | does not fit |

## 3. How the ideas were ranked

The filter is the app's own pillars — the spec's goals and the owner's rulings:

1. **A tap never waits.** Nothing new may sit between the tap and the saved cat.
2. **Private and offline by default.** Anything that leaves the phone is an explicit user action.
3. **Statistics first.** A feature ends in a number or a picture of the log.
4. **Built for travel** — abroad, often without data.
5. **Two people walk together.**
6. **The data is yours.**

An idea that breaks a pillar is out, or opt-in. Among the rest, ideas that need no schema change and
no new permission come first.

## 4. Roadmap

### Now — finish and harden

| Item | Why now | Size |
|---|---|---|
| **Keep cats across phones** — include the database and settings in device transfer and cloud backup; photos in device transfer only if its limit allows | Losing the log on a phone switch is the one irreversible failure left (pillar 6) | S |
| **Design pass** on Encounters, detail and Statistics | The theme is done; three screens still wear old layouts | M |
| **Follow-ups** — resizer leftovers on a failed store, the Counter's double-tapped camera, no camera app crashing the launch, `CounterStore` at detekt's ceiling, the coat sheet's missing slide, Places' cat row, reboot mid-walk | Each is written down already; they are cheaper now than after N1–N4 build on those screens | S each |
| **Signed release** | Owner creates the key; then the owed upload | S |

### Next — high fit, no new infrastructure

Each is derived from data the app already stores; none changes the Room schema.

**N1 · Records and the calendar** — M
- A year calendar of cats per day, one square a day, shaded by count — the spec's "30-day
  cats-per-day chart" grown to a year; a day opens its cats.
- Personal records: best day, best outing (the best session exists), fastest ten cats, most cats in
  one area in a day, busiest hour of the day and day of the week; longest walk.
- A broken record is announced once, the way milestones are.
- Shape: pure additions to `StatsCalculator`, a Statistics section, one calendar composable.
- Owner call: which records.

**N2 · Travel log** — M
- Countries and cities with their first cat's date, cats and outings; a headline "cats in N
  countries".
- **Where the cats are:** cities ranked by cats per hour and per km. An outing counts toward
  the city most of its cats were in.
- **Trips:** consecutive days with cats outside the home country form a trip, with its own page —
  days, cities, coats, the map focused on it. Home is inferred as the country with the most cat
  days; the app never asks where you live.
- Owner call: is a trip a country change, or distance from the usual area?

**N3 · Coat collection and achievements** — S–M
- **Coat card:** which of the eleven coats have been seen per country, city, trip or month — "9 of
  11 in Istanbul". Collection framing that needs no identity.
- **Achievements** derived from the log and announced like milestones: all eleven coats in one day;
  ten cats in ten minutes; a cat in a fifth country; a hundred cats in a day; a cat after midnight;
  a cat on Cat Day (1 March in Russia, 8 August internationally, 22 February in Japan).
- **A forgiving streak:** a weekly streak beside the daily one, or a freeze earned at milestones.
  Cats depend on going out; a streak that breaks on a sick day punishes the wrong thing.
- Shape: pure functions, plus a DataStore set of what has been announced, as `lastSeenMilestone` is.
- Owner call: weekly streak or freeze; the achievement list.

**N4 · Postcards and the year in cats** — M
- **Outing postcard:** count, duration, rate, coat faces, up to four photos, and the walk's route
  drawn as a line from its own track points — no tiles, so no snapshotter needed.
- **Trip, month and year recaps**; the year recap covers total, best day, longest streak, top coat,
  countries, most-seen area.
- Rendered in Compose to a bitmap and handed to the system share sheet. Nothing leaves the phone
  unless the user shares it (pillar 2).
- Date-bound: a year recap is only worth building if it ships before the year ends.

**N5 · More one-tap surfaces** — S each
- Quick Settings tile: +1 from the shade.
- Static app shortcuts on the launcher icon: Cat!, Photo, Start walk.
- Widget variants: today with the streak; this week; a row of the most-used coats (the full grid
  still needs a screen, per [widget.md](../features/widget.md)).

**N6 · Automatic backups to a folder you choose** — S–M
- A persisted SAF folder and a periodic worker writing the existing ZIP there — a cloud drive's
  folder works through its document provider.
- It is also the seed of L2: two phones pointed at one folder can merge each other's archives with
  the merge rules [backup.md](../features/backup.md) already has.

### Later — bigger bets, each needing an owner ruling

**L1 · Regulars: named cats** — L, revisits a v1 non-goal
- A cat can be named; later encounters link to it; its page shows its photos, first and last
  sighting, times seen and its dots on the map.
- **"Seen again?" without ML first:** a new cat of the same coat near a named cat's usual spot is
  offered as that cat; the user confirms, nothing links by itself.
- **With ML second:** on-device image embeddings rank the top three look-alikes from photos.
  Confirmation stays mandatory — street-photo accuracy will be well below published benchmarks.
- Schema: a `Cat` table and `Encounter.catId`, a migration, a backup format bump and merge rules.

**L2 · Two spotters** — M–L
- **Today, with no code:** she exports, he imports; the merge already keeps both sets by id. A
  "Share backup" action through the share sheet makes it one step.
- **Then:** N6's shared folder, both phones merging it, or Nearby Connections for "sync now" while
  together.
- **Spotted by:** every row already carries `deviceId`; naming devices gives "who saw it" for free.
- **The same cat from both phones on a joint walk:** a rule to count it once in combined statistics
  (same coat, the other device, close in time and space), kept separate in each person's own.
- Owner call: is the second phone Android or an iPhone? That decides whether L6 moves up.

**L3 · Photo smarts, on device** — S → M
- Gallery import flags photos with no cat in them (S).
- The coat sheet pre-highlights a suggested coat; it never sets one by itself (M).
- Thumbnails cropped to the cat (M).
- "Three cats in this photo?" conflicts with strictly +1 — owner call.

**L4 · Travel without data** — M
- Download a trip's region as an offline map before leaving.
- GeoNames as a place-name fallback, the spec's §9 item 5, only if "Not named yet" stays large.

**L5 · Walks, extended** — S–M
- A walk written to Health Connect as an exercise session with its route (opt-in permission).
- A walk history list: each walk with its own distance and cats per km.

**L6 · iOS** — L. The spec's §9 item 1. Most of the stack is multiplatform-ready; background walk
tracking and the widget are native rewrites. Gated by L2's question.

**L7 · Wear OS** — L. +1 from the wrist during a walk; a second app with its own release loop.

### Not doing

| Idea | Why not |
|---|---|
| Shared or social map | Owner ruling; every competitor above already lives there |
| Breed identification | Street cats rarely are a breed; the coat answers what people ask |
| Citizen-science export | Biodiversity pipelines filter domestic cats out; TNR groups need colony and health fields |
| Volume-key tally | Needs an `AccessibilityService`; Play policy |
| Android Auto | A counter is not in any allowed category |
| Lock-screen widget | Not broadly available; the walking notification covers the lock screen |
| Gemini Nano captions and recaps | Short device list, alpha API — revisit |
| A sync backend | A server or unofficial bindings, out of proportion for two people |
| Several cats per tap | "Strictly +1" is an owner decision; revisit only alongside L3's detection |

## 5. Suggested order

1. **Keep cats across phones** — the one irreversible loss goes first.
2. **N1 Records and the calendar** — statistics are the reason the app exists, and it touches no
   schema.
3. **N3 Coats and achievements**, then **N4 Postcards and the year in cats** — N4 is date-bound.
4. **N2 Travel log**, then **N6 automatic backups**.
5. **L2 step one** (share a backup), then **L1 Regulars** — the biggest bet and the first schema
   change, after the cheap wins show which statistics get used.

## 6. Owner calls

1. Which Next epic first — suggested: N1.
2. Named cats: reverse the v1 non-goal on unique-cat identity?
3. The second spotter's phone: Android or iPhone?
4. What is a trip: a country change, or distance from the usual area?
5. A weekly streak, or a streak freeze?
6. Several cats in one photo: stay strictly +1?
