# Photo viewer

A tap on a cat's photo on its detail screen opens the photo fullscreen, on black, the way a gallery
shows one: pinch to zoom, double-tap to zoom in at the tapped point and again to zoom back out,
double-tap-and-drag to zoom with one finger, pan a zoomed photo, fling it. A cat with no photo has
nothing to tap (`EncounterDetailStoreTest`, *a tap on the photo opens the viewer*; *a cat without a
photo has no viewer to open*).

## What it shows

The app's own full copy — the one the detail screen shows, at most `Tuning.PHOTO_MAX_SIDE` on its
longest side (see [photos.md](./photos.md#the-apps-own-copies)). Never the original: a photo imported
from the gallery was never copied, and the app holds no permission to read it back, so one source for
every cat keeps the viewer behaving the same for all of them. How far it zooms is
[Telephoto](https://github.com/saket/telephoto)'s default limit, measured against the copy's own
pixels rather than the screen, since past those the copy has no more detail to show. The gestures
are Telephoto's `ZoomableAsyncImage` over the app's Coil; it
carries no native code, so the 16 KB page-size check has nothing new to look at.

## Chrome

A back arrow sits at the top over a dark scrim. A single tap on the photo hides it together with the
status and navigation bars; the next tap brings all three back (`PhotoViewerScreenTest`, *a tap on
the photo hides the top bar and a second tap brings it back*). A single tap counts only once the
double-tap window has passed, since a second tap in it zooms instead. Until the photo has loaded,
Telephoto takes no taps at all, so a photo whose file cannot be read shows black with the arrow still
there to leave by. Whether the chrome shows is the screen's own view state, kept across a rotation;
the Store knows nothing of it.

## Where it sits

`PhotoViewer(encounterId)` is a Navigation 3 key drawn by Navigation 3's `DialogSceneStrategy` in a
dialog window of its own, edge to edge, so it covers the bottom bar without the shell learning which
screens hide it (`PhotoViewerNavigationTest`, *the viewer opens in a window of its own over the
cat*). The detail screen stays drawn underneath.

## Closing

- **Back** — the system gesture, or the arrow — pops the viewer and nothing else, leaving the back
  stack exactly as it was before it opened (`PhotoViewerNavigationTest`, *back from the viewer
  uncovers the cat as it was*; `PhotoViewerEntryTest`, *the back arrow in the nav host's own viewer
  entry closes only the viewer*).
- **Tapping the arrow twice** closes once (`PhotoViewerStoreTest`, *back closes the viewer once*),
  and the entry pops only while the viewer is on top, so it can never take the detail screen with it.
- **A cat that disappears** — deleted elsewhere, purged — closes the viewer rather than leaving it
  black (`PhotoViewerStoreTest`, *a cat deleted while on screen closes the viewer, and back after it
  closes nothing more*). So does an id with no live cat or a cat with no photo, which the viewer
  never opens for but a restored back stack could hold (*an id nobody has seen closes the viewer*;
  `PhotoViewerEntryTest`, *the nav host's own viewer entry closes by itself for a cat with no
  photo*).

The key holds only the cat's id, so after the process is killed the restored viewer loads the cat
again and shows the same photo.

## Where the code lives

- `presentation/…/viewer/` — `PhotoViewerState`, `Intent`, `Effect`, `StateMapper`, `Store`
- `ui/…/viewer/PhotoViewerScreen.kt`; `ui/…/theme/ViewerColors.kt` — the black stage, whatever the
  app's theme
- `app/…/navigation/PhotoViewer.kt` (the key and its dialog metadata), `PhotoViewerDestination.kt`,
  wired into `CatsRadarNavHost.kt` next to `EncounterDetail`

## Not built yet

Opening the photo in the gallery app. Swiping down to close, swiping to the next cat, sharing, and
opening the viewer from an Encounters tile or the Map.
