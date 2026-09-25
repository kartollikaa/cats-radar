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

A cat stored without them anyway — by an earlier version, or by a restore that took the archive's
geohash and cell as written — gets them at the next launch. `RepairPlaceCells` runs at every process
start and gives each located cat the geohash and cell its coordinates imply. That includes deleted
cats, so an undo brings back a whole row. A missing cell is created pending, like any new one, so it
gets named. Only those two columns are written, every cat's in one transaction. `updatedAt` stays as it
was, because the cat itself has not changed. A cat whose coordinates changed while the repair was
running is left alone. If the repair fails, the next start runs it again, and the failure is reported
to Crashlytics as a non-fatal (`analytics.md`). Without the repair such a
cat would sit under "Not named yet" for good, because nothing ever looks up a cell that does not
exist.

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
- `domain/…/usecase/RepairPlaceCells.kt` — the launch-time repair of located cats with no cell
- `data/…/androidMain/platform/AndroidReverseGeocoder.android.kt` — the `Geocoder` call
- `app/…/worker/GeocodePendingCellsWorker.kt`, `GeocodeWorkScheduler.kt`, `PlaceNamingTrigger.kt`

## Browsing them

**Statistics → Places** opens the drill-down: countries, then cities, then areas, then the cats
themselves. Every level is sorted busiest first, and every place row opens the level below it:
tapping an area lists its cats. Tapping a cat opens that cat, the same screen as from Encounters (see
[encounter-detail.md](./encounter-detail.md)), pushed above the list it was tapped in: the bottom bar
still shows Stats, and back returns to that list, not to the top of the drill-down. The Stats tab,
like from any level of the drill-down, goes back to Stats itself.

**The back arrow.** A back arrow sits at the top of every level, pinned while the level scrolls
under it, whether the level is still loading, empty, a list of places or its cats. It works like
the one on a cat's detail. The arrow takes back only the level it sits on: a second tap arriving
after that level has gone does nothing, so a quick double tap never skips a level.

**What a level looks like.** Each level opens on a headline card naming it and counting its cats:
**Places** and every cat at the top, then the country, city or area tapped, with the count its row
showed one level up. The level's own node comes from the level above, so the two can never
disagree. A level the level above no longer lists opens without a headline instead of a wrong one.
Below the headline, the level's rows sit in one card titled **Countries**, **Cities** or **Areas**.
Each row shows its name, its count and a chevron, with a thin bar under the name that shows its
share of the level. The three rows that stand for no place (Not named yet, No city, No
location) have their names in the muted text colour. TalkBack reads a row as one item: its name,
its count, and that it opens.

**The cats** at the bottom are laid out the way the map's spot sheet and the Encounters list lay
them out: one card per cat with its coat face or photo, in a run of cards under each outing header.
This is never the Encounters grid, whatever the Settings switch says. The headline is the list's
first item and scrolls away with it. An outing header whose cats include a located one offers
**On the map**, which opens the Map tab on that outing, as it does from Encounters. The headers
group only the place's own cats, so an outing that also went elsewhere starts here at its first cat in
this place, while the map shows the whole outing and names it by its real start. A cat here has no
long press: there is nothing to select.

A level draws nothing until its cats have been read, so one sliding in never flashes as empty
first. A level that holds nothing says what it would have listed, under a location pin. The
countries read **No places yet**: no cat has been logged at all. That first-run case also says that
cats with a location are grouped here by country and city. Any other level of places — a country's
cities, the areas of a city, of No city or of Not named yet — reads **No places here**. An area or No location, whose
children are cats, reads **No cats here**. Below the countries, a level is empty only when its last
cat went away while it was open — deleted from its own detail, or undone from the walking
notification — or when it is rebuilt after its cats were deleted.

Two pseudo-nodes always come **last**, after every real place, and only when they hold something:

- **Not named yet** — cats with coordinates whose cell has no name (pending, failed, or no
  geocoder) or does not exist yet. It drills into areas like any country would.
- **No location** — cats without a location: no coordinates, coordinates off the globe, or a cat
  marked as having none. It drills straight to the cats.

A country's cities end the same way, with **No city**: the cats whose cell names that country but
neither a locality nor an admin area, which is what a geocoder answers at sea or in open country.
It drills into areas like a city does. It is not Not named yet: those cells have a name, just not a
city's, and a named cell is never looked up again.

Their counts are what make the tree honest: **the counts of every sibling add up to their
parent**, so a drill-down never quietly loses a cat. The countries, a country's cities, and the
areas of a city or of No city each have a test for it.

**An area lists exactly the cats its row counts.** It remembers what it was listed under — a city,
No city or Not named yet — and holds only that parent's cats, however many others share its patch:
a Barcelona area never lists a cat of L'Hospitalet, of No city or of Not named yet that falls in
the same few kilometres. The parent stays with the area in its navigation key, which is what a
restored screen is rebuilt from. An area under each of the three parents has a test for it, with
another parent's cat in the same patch.

An area with no `subLocality` anywhere shows its coordinates instead of a name — areas come from the
coordinates themselves, so they work with no network and even for cells that were never named or
never created. A cat with coordinates therefore always lands in an area, even before the repair above
has run. An area whose cells disagree takes the name most of them agree on.

## Not built yet

The `Geocoder` call uses the deprecated blocking overload because the listener-based one is API 33+
and this app supports 29.
