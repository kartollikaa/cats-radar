# Photos and coats for logged cats — PR Decomposition Map

- **Created:** 2026-09-23
- **Epic reference:** [docs/superpowers/specs/2026-09-21-cats-radar-design.md](../../superpowers/specs/2026-09-21-cats-radar-design.md)
  §2 F2/F6, §4.2 step 7, §4.2a, §5
- **Trunk:** `main`
- **Size budgets:** target ≤600 reviewable lines, cap 1000 (see tbd:sizing-pull-requests). Excluded from the
  count: `*.md`, exported Room schemas (`data/schemas/**`), screenshot baselines.
- **Integration strategy:** every slice is **naturally safe** — P1a is a use case nothing calls yet, P1b
  and P2 are complete behaviours on screens that already exist, and none changes the schema.

## Slices

| # | PR title | Purpose (one sentence) | Strategy | Size budget | Depends on | Status |
|---|----------|------------------------|----------|-------------|------------|--------|
| P1a | Attaching a photo, in domain and data | `AttachPhoto` and a guarded write that touches only the photo columns of a live cat with no photo; "With photo" counted by the photo itself. Nothing calls it yet. | safe | ~450 | v1 | in-review |
| P1b | Photo for a logged cat, on screen | "Take photo" / "From gallery" on the detail of a cat with no photo, through a camera launcher shared with the Counter. | safe | ~450 | P1a | planned |
| P2 | Coat right after a photo | A photo from the camera asks for its coat in a bottom sheet over the Counter; dismissing leaves it unset. | safe | ~350 | P1b | planned |

Status values: `planned · in-progress · in-review · merged · dropped`

## Slice details

### Slice P1a — Attaching a photo, in domain and data
- **In scope:** `AttachPhoto` use case with `PhotoSource` and `AttachResult`; `PhotoStamp`; a guarded
  `attachPhoto` DAO query and repository method returning whether it wrote; `ImageResizer.store`'s
  second parameter renamed from `encounterId` to `baseName`, since an attached photo's files are not
  named by the cat; "With photo" counted by `photoPath` rather than `kind`; the domain fake's
  `observeById` hiding soft-deleted rows as the DAO does; `statistics.md`, `data-model.md`.
- **Out of scope:** any UI; the Koin binding (P1b, where it is first used).
- **Ships safely because:** nothing calls the use case; the statistics change moves no number for
  any existing row, since every `PHOTO` has a `photoPath`.
- **Cleanup owed:** none.

### Slice P1b — Photo for a logged cat, on screen
- **In scope:** detail State/Intent/Effect/Store and mapper for adding a photo; "Take photo" / "From
  gallery" in place of the photo on a cat with none; the camera launcher extracted from the Counter
  and shared; the detail destination in its own file with a tested effect handler; EN/RU strings; the
  `AttachPhoto` binding; `photos.md`, `encounter-detail.md`.
- **Out of scope:** replacing or removing a photo; the EXIF location of an attached photo.
- **Ships safely because:** additive UI on the detail screen; no schema change.
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
