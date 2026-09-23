# Place names

Coordinates are not a place. This is what turns "41.39864, 2.17842" into "Barcelona, Spain" so the
statistics can group cats by country, city and area.

## Cells, not points

Every located encounter belongs to a **place cell** — its geohash truncated to
`Tuning.PLACE_CELL_PRECISION`, a patch a few hundred metres across. Cells are what get named, not
encounters: a street's worth of cats share one lookup instead of one each, and the cell is stored
once no matter how many cats fall in it.

`PlaceCells.remember` creates the cell as `PENDING` the first time a cat lands in it and never
touches it again — resolving it is the worker's job. A second cat in the same cell does **not** queue
it a second time.

Every path that gives an encounter a geohash goes through it, whatever produced the coordinates:
a location fix (`AttachLocation`), or a photo's own EXIF, whether the camera just took it
(`LogPhoto`) or it came from the gallery (`ImportPhotos`) — and a cat restored from a backup
(`ImportBackup`), whose cell the archive may not carry. Coordinates without a cell would be a cat
that knows exactly where it was and still reads as "no location" in this screen.

A cell's centre is the point the geocoder is asked about. Every path that creates a cell takes it
from the cell's id — an import too, which derives it from the id again rather than reading it from
the archive. A cell imported before that rule keeps whatever centre it was stored with; nothing
rewrites it.

## Naming them

`GeocodePendingCellsWorker` names cells in two passes. Both wait on a **network constraint**, so a cat
logged in a basement gets its name the next time the phone is online. It does not fail right away
and retry on a timer.

- **A new cell is named as soon as the phone is online.** `PlaceNamingTrigger` watches for cells
  nobody has looked up yet for as long as the process lives. When one appears, it enqueues a one-time
  pass. It also enqueues one when the process starts if a cell is already waiting, which covers a
  process that died between writing the cell and asking. The periodic job cannot do this on its own:
  WorkManager never runs periodic work ahead of its slot, so a cat would read "Not named yet" for
  hours.
- **This pass only touches cells never looked up.** A cell that already failed is left for the
  periodic retry, so however often cells appear, a flaky geocoder cannot use up a cell's attempts in
  one walk. That is also why a request made while a pass is running starts it over (`REPLACE`)
  rather than being dropped: the running pass may already be past the new cell, and starting again
  costs at most the one lookup that was in flight. At most one such pass is ever queued, however
  many cells appear while the phone is offline.
- **A failed lookup is retried periodically.** This work is unique and enqueued with `KEEP`, because
  re-enqueuing on every launch would reset both the period and the backoff. A cell that keeps
  failing would then be retried far more often than intended.

A pass pages through pending cells by id, starting each page after the last cell it saw. An offset
would not work: the pass moves the cells it names out of the pending set, so every page would skip
as many cells as the one before it had named.

The two passes can run at the same time, and a restore can write a cell while a lookup is in flight.
A pass therefore writes its result only if the cell is still exactly as it read it. If anything
changed meanwhile, the other writer's version stands. Without this rule, a lookup that failed could
overwrite a name another pass had just found.

Each cell ends in one of four states:

- **RESOLVED** — it has a name. An address that comes back completely empty is treated as a
  failure, not a resolution: retiring the cell with nothing to show would be worse than trying again.
- **PENDING** — the lookup failed and there are attempts left. It stays pending precisely so the
  next run picks it up.
- **FAILED** — it has failed `MAX_GEOCODE_ATTEMPTS` times. Some points genuinely have no address,
  and retrying them forever costs battery for a name that will never arrive.
- **UNAVAILABLE** — there is no geocoder on this device at all.

Every state but RESOLVED is this device's own verdict: a cell restored from a backup without a name
arrives `PENDING` and untried, whatever the device that exported it concluded — see `backup.md`. A
cell imported before that rule keeps whatever state it was stored with; nothing resets it.

## No geocoder at all

`Geocoder.isPresent()` is false on devices with no geocoding backend, typically ones without Google
services. That is not a failure to retry: the run **stops at the first cell** rather than marching
through the rest to learn the same thing, and the worker cancels its own periodic schedule. Waking
up every few hours to rediscover that the device cannot geocode is pure battery cost.

Those cells still hold coordinates, so the area level of the region tree — which is derived from the
geohash and needs no network — keeps working. Only country and city names are missing.

## Where the code lives

- `domain/…/platform/ReverseGeocoder.kt` — the interface and its three outcomes
- `domain/…/usecase/ResolvePendingPlaces.kt` — the state machine, both passes
- `domain/…/usecase/ObserveUntriedPlaceCells.kt` — which cells are waiting for their first lookup
- `data/…/androidMain/platform/AndroidReverseGeocoder.android.kt` — the `Geocoder` call
- `app/…/worker/GeocodePendingCellsWorker.kt`, `GeocodeWorkScheduler.kt`, `PlaceNamingTrigger.kt`

## Browsing them

**Statistics → Places** opens the drill-down: countries, then cities, then areas, then the cats
themselves — a plain list of one row per cat under its outing header, not the Encounters grid.
Every level is sorted busiest first.

Two pseudo-nodes always come **last**, after every real place, and only when they hold something:

- **Not named yet** — cats with coordinates whose cell has no name (pending, failed, or no
  geocoder). It drills into areas like any country would.
- **No location** — cats with no coordinates at all. It drills straight to the cats.

Their counts are what make the tree honest: **the counts of every sibling add up to their
parent**, so a drill-down does not quietly lose a cat, with the two exceptions below. The countries,
a country's cities and a city's areas each have a test for it.

An area with no `subLocality` anywhere shows its coordinates instead of a name — areas come from the
geohash, so they work with no network and even for cells that were never named. An area whose cells
disagree takes the name most of them agree on.

## Not built yet

A cat whose cell names a country but neither a locality nor an admin area counts toward its country
and toward no city, so that country's cities add up to less than it. And a cat with coordinates but
no geohash counts toward its city or Not named yet and toward none of its areas; the app never
writes such a row, but a hand-edited one can hold it.

The `Geocoder` call uses the deprecated blocking overload because the listener-based one is API 33+
and this app supports 29.
