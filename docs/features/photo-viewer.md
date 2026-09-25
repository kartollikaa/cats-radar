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

## Several photos

The viewer holds every photo of the cat, oldest first, side by side: a swipe sideways moves to the
next or the previous one (`PhotoViewerScreenTest`, *a swipe moves to the next photo, and the gallery
button follows the photo on screen*), and pinch, double-tap and pan act on the photo on screen —
Telephoto hands a sideways drag to the pager once a zoomed photo cannot pan further that way. It opens on the photo it was
opened for, and on the cat's cover when it was opened for none or for one the cat no longer has
(`PhotoViewerStateMapperTest`, *the viewer opens on the photo it was opened for*; *opened for no photo,
or for one the cat does not have, the viewer opens on the cover*). While the cat has more than one, a
position — "2 / 3" — sits at the bottom and hides with the rest of the chrome; a cat with one shows none
(*a cat with one photo shows no position*). Which photo is on screen is the pager's own state, kept
when the screen is recreated (`PhotoViewerScreenTest`, *the photo on screen survives the screen being
recreated*). The time and day in the bar are the cat's, the same on every page.

## Chrome

A bar sits at the top over a dark scrim: the back arrow at the start, the gallery button at the end
when there is one (see *Open in gallery* below), both kept the other screens' content inset from the
edges (`PhotoViewerScreenTest`, *the bar's buttons keep the screens' content inset from the edges*),
and centred on the screen between them when the cat
was logged — the time, with the day under it. Both are the detail screen's own labels: the time where
the cat was logged, and the day it was there, "Today" and "Yesterday" included
(`PhotoViewerStateMapperTest`, *the day comes from the cat's own offset, not the phone's*;
`PhotoViewerScreenTest`, *the bar names when the photo was taken, the time over the day, centred on
the screen*). A single tap on the photo hides the bar together with the status and navigation bars;
the next tap brings all three back (`PhotoViewerScreenTest`, *a tap on the photo hides the top bar
and a second tap brings it back*). A single tap counts only once the
double-tap window has passed, since a second tap in it zooms instead. Until the photo has loaded,
Telephoto takes no taps at all, so a photo whose file cannot be read shows black with the arrow still
there to leave by. Whether the chrome shows is the screen's own view state, kept across a rotation;
the Store knows nothing of it.

## Where it sits

`PhotoViewer(encounterId, photoId)` is a Navigation 3 key drawn by Navigation 3's `DialogSceneStrategy` in a
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

The key holds only the cat's id and the photo it was opened for, so after the process is killed the
restored viewer loads the cat again. A key saved before cats had several photos carries no photo and
opens on the cover (`PhotoViewerSavedStateTest`, *a viewer key saved before
photo ids comes back opening on the cover*).

## Open in gallery

**When it is offered.** The button belongs to the photo on screen and changes as the user swipes: it
shows for a photo that has a link here and opens that photo's item, never the cover's
(`PhotoViewerStoreTest`, *the gallery opens the original of the photo on screen, not the cover's*). A
photo whose camera original the app saved to `Pictures/Cats Radar` (see
[photos.md](./photos.md#the-gallery-setting)) shows a gallery button at the other end of the top bar.
So does a cat whose photo was imported, or chosen from the gallery for it, when the pick named an item
in this phone's gallery (see [import.md](./import.md#the-gallery-item-it-came-from)) — the item the
user picked, not a copy (`GalleryLinkTest`, *a photo this install picked from the gallery is a link to
the item, one the app does not own*). A cat with both opens the original the app saved (*the original
the app saved wins over a picked item on the same cat*). A tap hands that item to whatever app the
phone opens images with — the user's default gallery, or the system's choice — with the app's own read
access passed on. If Android refuses to pass it on — for a picked item the user gave the app no access
to, and for a saved one deleted in the moment between the check and the tap reaching the gallery —
the view goes again without it rather than crashing, and the gallery opens the item with its own access
or says it cannot find it (`GalleryOpenerTest`, *a grant the app can no longer give is dropped and the
item still opens*). Whether that gallery lets the user swipe on to the photos around it is its own
behaviour. A camera photo taken with saving to the gallery turned off, a cloud-only pick, and a cat
imported before the app kept the picked item offer nothing: the app has no link to where they are
(`PhotoViewerStateMapperTest`, *a photo with no original in the gallery offers nothing there*).

**The install rule.** The button appears only on the installation that saved the original or made
the pick: each photo names the install that recorded its links (`GalleryLinkTest`, *an original recorded
by another install is never a link here*; *a photo another install picked is never a link here*). A photo
given on this phone to a cat another install logged — one a backup brought here — names this phone, so
it opens here and not back on the phone that logged the cat (`AttachPhotoTest`, *a photo given to a cat
another install logged opens its original here and not on that install*). A gallery item
is known by an id that is only meaningful on the phone that made it, so on another phone — after a
backup was restored there — the same id may be a different picture, possibly another cat the app saved
there. A reinstall is another installation too, and Android takes away an uninstalled app's hold on
the items it saved, so there is nothing the app could check either.

**A deleted original.** A saved original is checked at the tap, not when the viewer opens:
MediaStore shows an app only the items it owns, and hides one moved to the trash, so an item the query
no longer returns is gone. Then nothing opens and a message says the photo is no longer in the gallery
(`ResolveGalleryLinkTest`, *an original deleted from the gallery is gone*; `MediaStoreGalleryItemsTest`
— a refused query or an unparseable URI reads as gone, never as a crash). A picked item is never
checked: without access to the user's photos the app cannot see it, so a check would call a present
item gone. The gallery is opened all the same, and what it shows for an item the user has deleted since
— its library, or a message of its own — is its behaviour, not the app's (`ResolveGalleryLinkTest`, *a
picked item opens unchecked, since without photo access the app cannot see it*).

**No gallery app.** A phone with nothing that shows images gets a message that no app can show the
photo (`GalleryOpenerTest`, *with no app to show an image it reports so instead of crashing*).

**A second tap** while the first is being checked opens the gallery once (`PhotoViewerStoreTest`, *a
second tap while the first is being checked opens the gallery once*).

## Where the code lives

- `presentation/…/viewer/` — `PhotoViewerState`, `Intent`, `Effect`, `StateMapper`, `Store`
- `ui/…/viewer/PhotoViewerScreen.kt`; `ui/…/theme/ViewerColors.kt` — the black stage, whatever the
  app's theme; `ui/…/components/CenterAppBar.kt` — the bar, shared with the detail screen
- `app/…/navigation/PhotoViewer.kt` (the key and its dialog metadata), `PhotoViewerDestination.kt`,
  wired into `CatsRadarNavHost.kt` next to `EncounterDetail`; `GalleryOpener.kt` — `ACTION_VIEW`
- `domain/…/model/GalleryLink.kt` — whether a photo has a link here; `domain/…/usecase/ResolveGalleryLink.kt`;
  `domain/…/platform/GalleryItems.kt`, answered by `data/…/androidMain/platform/MediaStoreGalleryItems.android.kt`
- `data/…/androidMain/platform/MediaStoreItemLocator.android.kt` — which gallery item a picked photo is
  (see [import.md](./import.md#the-gallery-item-it-came-from))

## Not built yet

A link for cats imported before the app kept the picked item. Swiping down to close, swiping to the
next cat, sharing, and opening the viewer from an Encounters tile or the Map.
