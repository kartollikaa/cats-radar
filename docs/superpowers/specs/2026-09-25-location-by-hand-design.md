# Location by hand — design

- **Date:** 2026-09-25
- **Status:** approved by the owner in chat, 2026-09-25
- **Decomposition:** [docs/tbd/decompositions/2026-09-25-location-by-hand.md](../../tbd/decompositions/2026-09-25-location-by-hand.md)
- **Builds on:** [2026-09-21-cats-radar-design.md](./2026-09-21-cats-radar-design.md) §3.1 (encounter), §4.3
  (location); roadmap item 4, "editing time/location", for the one case below

## What the owner asked for

A cat whose location is unknown can be given one. The GPS may have found nothing when the cat was
logged, the permission may have been off, or the cat may have been imported from a photo without GPS;
today such a cat stays at `NONE` for good.

Owner decisions, 2026-09-25:

- The point is chosen **on a map**: a pin fixed in the centre, the map moved under it, *Save*.
- The map opens on the **located cat closest in time** to this one, at street size. A **"Where am I"**
  button moves it to the phone's own position.
- The point is **this cat's only**: no other cat of its outing receives it.
- Offered **only for a cat with no location**. Changing a location a cat already has stays on the
  roadmap.

## The data

**A new source.** `LocationSource.MANUAL`, "Set by hand" / «Указано вручную» wherever a cat's source is
named. It is stored and backed up by name like every other source, so Room needs no migration.

**The stamp.** The chosen point's coordinates; `accuracyMeters` null, since nothing measured it;
`locationFixedAt` the moment it was saved, the time the app learned where the cat was; `geohash` and
`placeCellId` as for any other point, the cell remembered through `PlaceCells.remember` so the
geocoder names it (`places.md`). From then on the cat is like any located cat: on the Map, in Places,
in an outing's cat-to-cat line.

**One writer wins.** A tally's own fix may still be on its way when the user opens the picker — up to
`LOCATION_TIMEOUT` for a fresh tally, and the worker is retried after a process death. Both writers
therefore write only a cat that is still `NONE`, and the check is the write itself:
`EncounterDao.attachLocation` gains `AND locationSource = 'NONE'` and reports whether it changed the
row. Whichever lands first stays; the other writes nothing. `AttachLocation` backfills the outing
only when its own write landed. Without the guard in SQL, a fix resolving after the user saved would
silently replace the chosen point.

**`SetLocationByHand(encounterId, lat, lon)`** is the use case. It refuses a point off the globe,
stamps `MANUAL`, and returns whether the cat now has that point — `false` when the cat is gone or was
located first.

**Backups.** The archive format rises to 5. An app before it would otherwise fail to parse a `MANUAL`
row and call the archive "not a backup"; with the bump it says the archive comes from a newer
version (`backup.md`). A format-4 archive still imports unchanged.

## The picker

**Opening it.** The **Where** section of a cat with no location offers *Set on map*. Its tap pushes
`LocationPicker(encounterId)` above the detail screen, as a plain entry: the bottom bar stays, the
tab stays selected. Back leaves without writing anything.

**What it shows.** A MapLibre map in the app's light or dark style, the same tiles as the Map tab and
without its cats, a pin fixed at the centre, *Save* at the bottom, a back arrow and *Where am I*
over the map. The pin's tip is the point saved: the camera's target at the moment of the tap.

**Where it opens**, decided once, when the screen first opens:

1. **The located cat closest in time** to this one, on a street-sized area around it — for a cat
   logged without a fix that is usually a cat of the same walk; for an old photo, a cat of the same
   day. Only live cats with coordinates on the globe count. A tie goes to the earlier cat. This is a
   pure `:domain` function.
2. Otherwise **the phone's last known position**, when the app may read it. The GPS is not asked and
   no permission is requested just for opening.
3. Otherwise **the whole world**.

The map does not move by itself afterwards: nothing arrives later to pull the view off where the
user has panned it.

**Where am I.** The tap asks for the location permission (the system answers at once, without a
dialog, when it is already granted). Granted, the button disables while the phone looks for a fresh
fix, falling back to the last known one, exactly as a tally does (`LocationPolicy`); found, the map
moves once to a street-sized area around it. Refused, or nothing found, a short message says the
position is unknown and the map stays where it is.

**Saving.** *Save* disables while the write runs. The picker closes on its own as soon as the cat is
no longer without a location — its own write landing, a fix landing first, or the cat deleted
elsewhere — so the detail screen underneath always ends up showing what the cat actually has. A
failed write keeps the picker open with *Save* enabled again.

**Store.** `LocationPickerStore(encounterId)` observes the cat and every encounter; it holds
`Loading`, then `Picking(start, locating, saving, moveTo)`. `start` is a `MapArea?` (null = world);
`moveTo` is the area *Where am I* found, cleared by a `MoveReached` intent once the camera got there —
the pattern the Map tab uses for a cat's coordinates. Its effects are `Close`,
`RequestLocationPermission` and `PositionUnknown`.

**Not built.** Search by address; changing an existing location; a dot for the phone's own position;
giving the point to other cats of the outing.

## Testing

- `:domain` — `SetLocationByHand` (stamps, refusals, the place cell), the closest-in-time choice (ties,
  deleted cats, points off the globe, no located cat), `AttachLocation` skipping its backfill when its
  own write did not land.
- `:data` — the DAO guard on a Robolectric database (a located cat is never overwritten, a deleted one
  never resurrected); a backup round trip of a `MANUAL` cat; format 5 refused by a format-4 reader's
  rule and format 4 still read.
- `:presentation` — the picker Store and mapper on fakes, with virtual time; the detail mapper offering
  *Set on map* only for `NONE`.
- `:ui` / `:app` — the picker screen and the detail button, the entry test for back and close.
