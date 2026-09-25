# Photo viewer and gallery link — design

- **Date:** 2026-09-24
- **Status:** approved by the owner in chat, 2026-09-24
- **Decomposition:** [docs/tbd/decompositions/2026-09-24-photo-viewer.md](../../tbd/decompositions/2026-09-24-photo-viewer.md)
- **Builds on:** [2026-09-21-cats-radar-design.md](./2026-09-21-cats-radar-design.md) §2 F2 (photos), §5 (data model)

## What the owner asked for

1. A cat's photo opens fullscreen from its detail screen and behaves like a normal photo viewer:
   pinch and double-tap zoom, pan, fling.
2. From there, the photo opens in the device's gallery app — the original item it came from.

Owner decisions, 2026-09-24:

- The gallery link covers camera photos **and** photos imported or attached from the gallery from
  now on, accepting a schema change and a link derived from the picker's URI shape. Cats imported
  before the change get no link.
- "Open in gallery" lives in the viewer's top bar only, not as a second control on the detail screen.

## The viewer

**Opening it.** A tap on the photo of a cat that has one opens the viewer. The detail screen
dispatches `PhotoClicked`; its Store emits `OpenPhoto`; the destination pushes
`PhotoViewer(encounterId)`. A tally with no photo has nothing to tap.

**Where it lives.** `PhotoViewer` is a Navigation 3 key whose entry carries
`DialogSceneStrategy.dialog(DialogProperties(usePlatformDefaultWidth = false,
decorFitsSystemWindows = false))`. Navigation 3's own `DialogSceneStrategy` draws it in a dialog
window above the entries under it, so it covers the app `Scaffold`'s bottom bar without the shell
learning which keys hide it. It joins `BottomSheetSceneStrategy` in `sceneStrategies`, both before
the single-pane default. Back — the system gesture, predictive back, or the top bar's arrow —
pops it and uncovers the detail screen exactly as it was left.

**What it shows.** The app's full copy (`photoPath`, at most `Tuning.PHOTO_MAX_SIDE` on its long
side), on black, fitted to the screen. The original is not loaded: an import's original is not
readable by the app at all, and one image source for every cat keeps the viewer's behaviour the
same for all of them. The full-resolution original is what *Open in gallery* is for.

**Gestures.** [Telephoto](https://github.com/saket/telephoto) `zoomable-image-coil3`
(`ZoomableAsyncImage`) over the app's Coil 3: pinch, double-tap to zoom in and out, pan within the
image's bounds, fling, rubber-banding at the edges. It is pure Kotlin — no native code, so nothing
new for the 16 KB page-size check. Hand-rolled `detectTransformGestures` was rejected: bounds,
fling and zoom-to-the-tapped-point are exactly what it gets wrong.

**Chrome.** A top bar with a back arrow (and, from slice V2, *Open in gallery*) over a scrim. A
single tap on the photo hides the bar and the system bars together; another tap brings them back.
Whether the chrome shows is view-only state (`rememberSaveable` in `:ui`), not Store state.

**Store.** `PhotoViewerStore(encounterId)` observes the encounter. State is `Loading`, or `Showing`
with the resolved photo path. When the encounter disappears (deleted elsewhere, purged) or has no
photo, the Store emits `Close` once and the destination pops the viewer — the viewer never shows an
empty black screen pretending to be a photo.

**Not built.** Swipe down to dismiss; swiping between cats; share; the viewer from an Encounters
tile or the Map.

## The gallery link

### Two kinds of link

| | `galleryUri` (exists) | `sourceMediaUri` (new, slice V3) |
|---|---|---|
| What it is | The gallery item the app wrote: a camera original saved to `Pictures/Cats Radar` | The MediaStore item a picked photo came from |
| Set by | `LogPhoto`, `AttachPhoto` from the camera, when saving originals is on | `ImportPhotos`, `AttachPhoto` from the gallery |
| Can the app read it | Yes — MediaStore lets an app see the rows it owns | No — the app holds no permission to read the gallery |
| Checked before opening | Yes: still there, or *No longer in the gallery* | No: a deleted item is the gallery app's own "not found" |
| Read grant on `ACTION_VIEW` | `FLAG_GRANT_READ_URI_PERMISSION` | Refused with `SecurityException`, so the view goes without it (the opener's fallback) |

They stay two columns because they are two different promises. `galleryUri` is also what
`RegeneratePhotoCopies` reads the original back from; filling it with a URI the app cannot open
would turn every import into a failed rebuild.

### The install rule

A link is offered only on the installation that recorded it: `encounter.deviceId` equals this
install's `DeviceIdProvider.deviceId`. MediaStore ids are per device, so the same id on another phone
is a different picture — possibly another photo the app itself saved there, which the ownership
check alone would pass. `allowBackup="false"` means a reinstall gets a fresh id, and MediaStore
orphans an uninstalled app's rows (`onPackageOrphaned` clears `owner_package_name`), so a camera
link from before a reinstall could not be checked anyway.

On one device a link can never open a different picture: the `files` table's `_id` is
`INTEGER PRIMARY KEY AUTOINCREMENT`, which SQLite never reuses.

Accepted false negative: a tally restored from another install's backup and given a photo on this
one keeps the other install's `deviceId`, so its new photo offers no link. `AttachPhoto` therefore records
no link at all on such a cat: back on the install that logged it, the row would pass the rule and name a
different picture. A camera original still goes to the gallery; the cat just does not point at it.

### Opening

`Encounter.galleryLink(thisInstall)` in `:domain` is the one decision — a `GalleryLink(uri)` or null
(V3 adds whether the app owns the item) — used both by the viewer's mapper (show the action or not)
and by the use case that resolves a tap. `ResolveGalleryLink(encounter)` takes the cat the Store is
showing, applies the rule, and asks `GalleryItems.exists(uri)` of a link the app owns; it returns
`Open(uri)`, `Gone`, or `Unavailable`. A second tap while one is being resolved opens nothing, so the
gallery never opens twice. The Store turns those into `OpenInGallery(uri)` or a `GalleryItemGone`
message. `:app` starts `ACTION_VIEW` with the image MIME type and `FLAG_GRANT_READ_URI_PERMISSION`;
when the platform refuses that grant — the app no longer holds the item, or never did — it sends the
view again without it, since the gallery can open the item with its own access. No activity to
handle it is a *No app can show this photo* message, never a crash.

The chooser is the system's: the user's default gallery handles it, or the system asks. Whether the
gallery then lets the user swipe to neighbouring photos is that app's behaviour, not ours.

### Deriving a picked photo's MediaStore item (slice V3)

From the URI the picker or `GET_CONTENT` handed over, at import or attach time, in
`:data/androidMain`:

- `content://media/picker/<user>/<local authority>/media/<id>` and
  `content://media/picker_get_content/<user>/<local authority>/media/<id>` — for the local
  (on-device) provider the trailing id is the MediaStore `_id` (AOSP `ExternalDbFacade` syncs
  `_ID AS id`), so the item is `MediaStore.Images.Media` external content URI + id. Only for the
  current user's profile. The authority and URI shape are not public API; the rule is pinned by
  tests against URIs captured on a device and checked against AOSP `PickerUriResolver` /
  `LocalUriMatcher`.
- `content://com.android.providers.media.documents/document/image:<id>` —
  `MediaStore.getMediaUri(context, uri)`, the documented conversion.
- `content://media/<volume>/images/media/<id>` — already a MediaStore item; kept as that item, without
  any query it came with.
- Anything else — a cloud-only item (its authority is the cloud provider's), another app's
  provider, a file — no link.

No public API converts a picker URI (`MediaStore.getMediaUri` supports only `ExternalStorageProvider`
and `MediaDocumentsProvider` URIs), which is why the shape rule is isolated in one tested function.

### Schema and backups (slice V3)

- `EncounterEntity.sourceMediaUri: String?` — database version 2 → 3 by an automatic migration that
  only adds the nullable column; the migration test opens a version-2 database with a cat in it.
- `Encounter.sourceMediaUri`; `PhotoStamp.sourceMediaUri` for `attachPhoto`, which writes it with
  the other photo columns and keeps its guard.
- Backups carry the field; an archive without it reads as null. The install rule makes a restored
  link on another phone inert rather than wrong.

## Edge cases

- **The gallery item was deleted** — owned: *No longer in the gallery*; picked: the gallery app's
  own error, since the app cannot look.
- **Saving originals was off when the photo was taken** — no `galleryUri`, no action.
- **No app handles `ACTION_VIEW` for images** — a message, no crash.
- **The cat is deleted while the viewer is open** — the viewer closes; the detail screen underneath
  already shows its "removed" state.
- **Process death in the viewer** — the key is `@Serializable` and holds only the encounter id; the
  Store reloads the cat.
- **A cloud-only photo imported from the picker** — no link, like any photo whose URI matches no rule.

## Testing

- `:presentation` — `PhotoViewerStore` and its mapper with fakes and Turbine; the detail Store's
  `PhotoClicked` → `OpenPhoto`.
- `:domain` — `galleryLink` over every combination of the two columns and the install rule;
  `ResolveGalleryLink` with a fake `GalleryItems`.
- `:data` — `MediaStoreGalleryItems.exists` under Robolectric; the URI rule as a table test on
  real captured URIs; the v2 → v3 migration; backup round trip with and without the field.
- `:app` — the viewer entry is drawn in a dialog over the detail and back uncovers it
  (Robolectric, like `BottomSheetNavigationTest`); the effect handler starts `ACTION_VIEW` with and
  without the grant, and survives `ActivityNotFoundException`.
- On a device — zoom, pan, double-tap and the chrome toggle; *Open in gallery* for a camera photo, an
  imported one, and one deleted from the gallery.
