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
| V2 | Open a camera photo in the gallery | The viewer's top bar opens the gallery original the app saved, when this install saved it and it is still there. | safe | ~500 | V1 | merged |
| V3 | Gallery link for imported and picked photos | Imports and gallery attachments remember the MediaStore item they came from, and the viewer opens it. | safe | ~600 | V2, #122 | merged |

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

- 2026-09-25: **V3 merged** as #139; the epic is complete. The picked item is read off the URI, never queried:
  the app may hold no permission to read the gallery. So a picked link opens without an existence check, and
  when Android refuses the read grant the view goes without it; what the gallery shows for a deleted item is its
  own. The field raised the backup format, so an app before it refuses a new archive rather than dropping the
  links. Review found that `AttachPhoto` recorded a link on a cat another install logged, which would open a
  different picture once the row went home; such a cat now keeps no link of either kind. It also found the
  backup reader's cut-off guard keyed on the current format only, which the bump would have lifted from
  format-2 archives; it now covers every format since walks.
- 2026-09-25: **V2 merged** as #131. The read grant moved to the shell after review: it always asks for it
  and sends the view again without it when Android refuses, which is also how V3's picked links open. The
  picker URIs V3 parses were captured on an API 37 AVD before any code: `picker_get_content/…/media/<id>`
  from the import, `picker/…/media/<id>` from "Choose from gallery", the id being the MediaStore `_id`.
- 2026-09-25: **V1 merged** as #128, brought up to date with main twice by merging it in. On the way it gained
  the `photo_viewer` analytics screen, which main's screen-view tracking requires of every `NavKey`. Left for
  the owner's phone: pinch zoom, and the dialog's bar colours below API 35.
- 2026-09-24: owner weighed gestures written with Compose modifiers alone against Telephoto and chose
  **Telephoto for the first version** — it brings the one-finger quick zoom and edge rubber-banding a
  hand-written viewer would have left out.
- 2026-09-24: owner asked for a fullscreen zoomable photo and a link back to the gallery item; chose the
  link for **camera photos and future imports** (over camera photos only), accepting the schema change and
  the picker-URI rule, and the action in the **viewer's top bar only**. Three slices, V1 → V2 → V3.
