# Location by hand — PR Decomposition Map

- **Created:** 2026-09-25
- **Epic reference:** [docs/superpowers/specs/2026-09-25-location-by-hand-design.md](../../superpowers/specs/2026-09-25-location-by-hand-design.md)
- **Trunk:** `main`
- **Size budgets:** target ≤600 reviewable lines, cap 1000 (see tbd:sizing-pull-requests). Excluded from the
  count: `*.md`, exported Room schemas (`data/schemas/**`), screenshot baselines.
- **Integration strategy:** every slice is **naturally safe** — L1 adds a source nothing produces yet and
  narrows a write to the rows it was always meant for; L2 adds a button where there was none.

## Slices

| # | PR title | Purpose (one sentence) | Strategy | Size budget | Depends on | Status |
|---|----------|------------------------|----------|-------------|------------|--------|
| L1 | A location set by hand | `MANUAL` source, `SetLocationByHand`, the `NONE` guard on every location write, backup format 5. | safe | ~400 | — | merged |
| L2 | Set a cat's location on a map | The picker screen with *Where am I*, opened from the detail of a cat with no location. | safe | ~700 | L1 | merged |

Status values: `planned · in-progress · in-review · merged · dropped`

## Slice details

### Slice L1 — A location set by hand
- **In scope:** `LocationSource.MANUAL` and its label token and EN/RU words; `EncounterDao.attachLocation`
  written only to a `NONE` row and reporting whether it did, through `EncounterRepository`;
  `AttachLocation` backfilling only after its own write landed; `SetLocationByHand`; the closest-in-time
  located cat as a `:domain` function; backup format 5; `location.md`, `backup.md`, the base spec's §3.1.
- **Out of scope:** any screen.
- **Ships safely because:** nothing produces `MANUAL` yet, and the only writers of a location already
  wrote only `NONE` cats — the guard moves that rule from a read before the write into the write.
- **Cleanup owed:** none.

### Slice L2 — Set a cat's location on a map
- **In scope:** `LocationPicker` key and destination with the permission launcher; `LocationPickerState` /
  `Intent` / `Effect` / `Store` / mapper; `LocationPickerScreen` (map, centre pin, *Save*, *Where am I*);
  *Set on map* in the detail's **Where** section for a `NONE` cat; the analytics screen name; Koin; EN/RU
  strings; `encounter-detail.md`, `location.md`, `map.md`.
- **Out of scope:** changing an existing location; address search.
- **Ships safely because:** additive — the **Where** section of a `NONE` cat offered nothing.
- **Cleanup owed:** none.

## Decision log

- 2026-09-26: **L1 merged** as #170 and **L2** as #175; **the epic is complete**. Review before L2 merged made
  *Save* and *Where am I* wait for a loaded, placed map, since until then MapLibre's camera sits at 0°, 0°; its gate
  added a message for a failed save and wraps a longitude panned past the antimeridian. Left for the owner's phone:
  the picker end to end, and *Where am I* against a real fix.
- 2026-09-26: **L1 opened as #170**, reviewed and gated green; L2 stacked on it. On the emulator the picker
  opened on a street around the cat logged closest in time, and *Save* gave the cat the point under the pin
  (the Map tab then drew it there). *Where am I* refused was not checked on the device: other sessions kept
  their builds in front of the shared emulator; it rests on `LocationPickerStoreTest`.
- 2026-09-25: owner asked for a way to give a cat with no location one. Chose a map with a centre pin and
  a *Where am I* button, opening on the located cat closest in time (then the last known position, then
  the world); the point for this cat only; offered only for `NONE`. Two slices, L1 → L2.
