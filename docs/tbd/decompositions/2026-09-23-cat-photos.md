# Photos and coats for logged cats — PR Decomposition Map

- **Created:** 2026-09-23
- **Epic reference:** [docs/superpowers/specs/2026-09-21-cats-radar-design.md](../../superpowers/specs/2026-09-21-cats-radar-design.md)
  §2 F2/F6, §4.2 step 7, §4.2a
- **Trunk:** `main`
- **Size budgets:** target ≤600 reviewable lines, cap 1000 (see tbd:sizing-pull-requests). Excluded from the
  count: `*.md`, exported Room schemas (`data/schemas/**`), screenshot baselines.
- **Integration strategy:** both slices are **naturally safe** — each is a complete behaviour wired end
  to end on a screen that already exists, and neither changes the schema.

## Slices

| # | PR title | Purpose (one sentence) | Strategy | Size budget | Depends on | Status |
|---|----------|------------------------|----------|-------------|------------|--------|
| P1 | Photo for a logged cat | A cat without a photo gets one from the camera or the gallery on its detail screen, keeping its time, place and coat. | safe | ~600 | v1 | in-progress |
| P2 | Coat right after a photo | A photo from the camera asks for its coat in a bottom sheet over the Counter; dismissing leaves it unset. | safe | ~350 | — | planned |

Status values: `planned · in-progress · in-review · merged · dropped`

## Slice details

### Slice P1 — Photo for a logged cat
- **In scope:** `AttachPhoto` use case; a guarded `attachPhoto` write in the DAO and repository that
  touches only the photo columns of a live row with no photo; "Take photo" / "From gallery" on the
  detail of a cat with no photo, with a camera launcher shared with the Counter; "With photo" counted
  by `photoPath` rather than `kind`; EN/RU strings; `photos.md`, `encounter-detail.md`,
  `statistics.md`, `data-model.md`.
- **Out of scope:** replacing or removing a photo; the EXIF location of an attached photo.
- **Ships safely because:** additive UI on the detail screen; no schema change; the statistics
  change moves no number for any existing row, since every `PHOTO` has a `photoPath`.
- **Cleanup owed:** none.

### Slice P2 — Coat right after a photo
- **In scope:** a coat prompt in `CounterState` set by a logged camera photo; a bottom sheet with the
  thumbnail, `CoatPicker` and "Not now"; `SetCoat` on a pick; EN/RU strings; `coat.md`, `photos.md`.
- **Out of scope:** a coat for gallery imports; the sheet on the detail screen, where the picker is
  already on screen.
- **Ships safely because:** the photo is saved before the sheet opens, so the sheet can only add.
- **Cleanup owed:** none.

## Decision log

- 2026-09-23: owner asked for photos on cats, or a coat when a photo is taken, and chose **both, as
  two PRs**, with the coat asked in a bottom sheet after the shutter rather than by long-pressing a
  coat or by opening the new cat's detail. The Counter's own coat grid stays the only coat control on
  that screen for tallies; a photo has no coat by construction, and the moment after the shutter is
  when the user knows it.
