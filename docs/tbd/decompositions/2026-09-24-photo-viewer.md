# Photo viewer and gallery link — PR Decomposition Map

- **Created:** 2026-09-24
- **Epic reference:** [docs/superpowers/specs/2026-09-24-photo-viewer-and-gallery-link-design.md](../../superpowers/specs/2026-09-24-photo-viewer-and-gallery-link-design.md)
- **Trunk:** `main`
- **Size budgets:** target ≤600 reviewable lines, cap 1000 (see tbd:sizing-pull-requests). Excluded from the
  count: `*.md`, exported Room schemas (`data/schemas/**`), screenshot baselines.
- **Integration strategy:** every slice is **naturally safe** — V1 adds a screen behind a tap that did
  nothing, V2 adds an action to that screen, V3 adds a nullable column that only new photos fill.

## Slices

| # | PR title | Purpose (one sentence) | Strategy | Size budget | Depends on | Status |
|---|----------|------------------------|----------|-------------|------------|--------|
| V1 | Fullscreen photo viewer | Tapping a cat's photo opens it fullscreen with pinch, double-tap, pan and fling, above the bottom bar. | safe | ~550 | — | merged |
| V2 | Open a camera photo in the gallery | The viewer's top bar opens the gallery original the app saved, when this install saved it and it is still there. | safe | ~500 | V1 | in-review |
| V3 | Gallery link for imported and picked photos | Imports and gallery attachments remember the MediaStore item they came from, and the viewer opens it. | safe | ~600 | V2, #122 | planned |

Status values: `planned · in-progress · in-review · merged · dropped`

## Slice details

### Slice V1 — Fullscreen photo viewer
- **In scope:** Telephoto `zoomable-image-coil3` in the catalog and `:ui`; `PhotoViewer` key with dialog
  metadata and `DialogSceneStrategy` in the nav display; `PhotoViewerState`/`Intent`/`Effect`/`Store` and
  mapper; `PhotoViewerScreen` with the top bar and the chrome toggle; the detail photo clickable
  (`PhotoClicked` → `OpenPhoto`); Koin registration; EN/RU strings; `encounter-detail.md`, `photos.md`,
  a new `photo-viewer.md`.
- **Out of scope:** any gallery action; swipe-to-dismiss; the viewer from lists or the Map.
- **Ships safely because:** additive — the photo on the detail screen did nothing when tapped.
- **Cleanup owed:** none.

### Slice V2 — Open a camera photo in the gallery
- **In scope:** `Encounter.galleryLink(thisInstall)` (owned kind only), `GalleryItems` platform interface
  and `MediaStoreGalleryItems`, `ResolveGalleryLink`; the viewer's action, its effect and messages;
  `ACTION_VIEW` in `:app` with a read grant; EN/RU strings; `photo-viewer.md`, `photos.md`.
- **Out of scope:** links for imports and gallery attachments (V3).
- **Ships safely because:** additive action in the viewer; no schema change.
- **Cleanup owed:** none.

### Slice V3 — Gallery link for imported and picked photos
- **In scope:** the picked-URI → MediaStore rule in `:data/androidMain` behind a `:domain` interface;
  `sourceMediaUri` on the entity, the domain model, `PhotoStamp` and backups; database v3 by an automatic
  migration with its test; `ImportPhotos` and `AttachPhoto` from the gallery recording it; `galleryLink`
  gaining the picked kind (no existence check, no grant); `import.md`, `photos.md`, `data-model.md`,
  `backup.md`, `photo-viewer.md`.
- **Out of scope:** back-filling cats imported before the change.
- **Ships safely because:** a nullable column only new rows fill; old rows read as "no link".
- **Depends on #122** (`GET_CONTENT` import), which changes the URIs imports arrive with.
- **Cleanup owed:** none.

## Decision log

- 2026-09-25: **V1 merged** as #128, brought up to date with main twice by merging it in. On the way it gained
  the `photo_viewer` analytics screen, which main's screen-view tracking requires of every `NavKey`. Left for
  the owner's phone: pinch zoom, and the dialog's bar colours below API 35.
- 2026-09-24: owner weighed gestures written with Compose modifiers alone against Telephoto and chose
  **Telephoto for the first version** — it brings the one-finger quick zoom and edge rubber-banding a
  hand-written viewer would have left out.
- 2026-09-24: owner asked for a fullscreen zoomable photo and a link back to the gallery item; chose the
  link for **camera photos and future imports** (over camera photos only), accepting the schema change and
  the picker-URI rule, and the action in the **viewer's top bar only**. Three slices, V1 → V2 → V3.
