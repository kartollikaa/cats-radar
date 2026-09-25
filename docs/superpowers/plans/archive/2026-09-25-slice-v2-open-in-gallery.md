# Slice V2 — Open a camera photo in the gallery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The viewer's top bar opens a cat's camera original in the device's gallery app, when this install saved it and MediaStore still holds it.

**Architecture:** `:domain` decides whether a cat has a link (`Encounter.galleryLink(thisInstall)`) and resolves a tap (`ResolveGalleryLink` → `Open` / `Gone` / `Unavailable`) through a `GalleryItems` platform interface; `:data/androidMain` answers it with a MediaStore query; `PhotoViewerStore` turns the answer into an effect; `:app` starts `ACTION_VIEW`.

**Tech Stack:** as V1; Robolectric for the MediaStore adapter and the intent.

**Spec:** `docs/superpowers/specs/2026-09-24-photo-viewer-and-gallery-link-design.md` § The gallery link. Criteria: `photo-viewer-v2.md` in the acceptance directory.

## Global Constraints

As V1 (`docs/superpowers/plans/archive/2026-09-24-slice-v1-photo-viewer.md`), plus:
- A platform capability is an interface in `:domain`, an implementation in `:data/androidMain`, a binding in `:app` (`docs/rules/module-structure.md`).
- The adapter never throws: a refused or malformed query reads as "gone".
- No `FLAG_GRANT_READ_URI_PERMISSION` on a URI the app cannot read — the platform throws `SecurityException` for it (V3's picked links).

---

### Task 1: The link and its resolution, in `:domain`

**Files:** create `domain/…/model/GalleryLink.kt`, `domain/…/platform/GalleryItems.kt`, `domain/…/usecase/ResolveGalleryLink.kt`; tests `domain/…/model/GalleryLinkTest.kt`, `domain/…/usecase/ResolveGalleryLinkTest.kt`; `FakeGalleryItems` in `domain/…/testing/FakePhotoPlatform.kt`.

**Produces:**
```kotlin
data class GalleryLink(val uri: String, val ownedByApp: Boolean)
fun Encounter.galleryLink(thisInstall: String): GalleryLink?   // galleryUri recorded by this install → owned link
interface GalleryItems { suspend fun exists(uri: String): Boolean }
sealed interface GalleryTarget { data class Open(val uri: String, val grantRead: Boolean); data object Gone; data object Unavailable }
class ResolveGalleryLink(encounterRepository, galleryItems, deviceIdProvider) { suspend operator fun invoke(encounterId: String): GalleryTarget }
```

- [ ] Tests: this install + `galleryUri` → owned link; no `galleryUri` → null; another install → null. Resolve: an owned item that exists → `Open(uri, true)`; one that does not → `Gone`; a cat with no link, or no live cat → `Unavailable`, and `exists` never asked.
- [ ] Implement; run `:domain` host tests; commit.

### Task 2: MediaStore answers `exists`

**Files:** `data/src/androidMain/…/platform/MediaStoreGalleryItems.android.kt`; test `data/src/androidHostTest/…/platform/MediaStoreGalleryItemsTest.kt` with a fake `media` provider registered through `ShadowContentResolver.registerProviderInternal`.

- [ ] Tests: a row → true; an empty cursor → false; a null cursor → false; a provider throwing `SecurityException` → false; `"not a uri"` → false.
- [ ] Implement: `query(uri, arrayOf(_ID))?.use { it.moveToFirst() } == true` on `Dispatchers.IO`, `runCatching` → false; bind `single<GalleryItems>` in `DataModule`; commit.

### Task 3: The Store offers and resolves it

**Files:** `presentation/…/viewer/` — `Showing(photoPath, opensInGallery = false)`, `OpenInGalleryClicked`, `OpenInGallery(uri, grantRead)`, `GalleryItemGone`; mapper takes `DeviceIdProvider`; Store takes `ResolveGalleryLink` and a `resolving` flag. Tests extend `PhotoViewerStateMapperTest`, `PhotoViewerStoreTest`.

- [ ] Tests: mapper `opensInGallery` true/false; tap → `OpenInGallery(uri, true)`; gone → `GalleryItemGone`; two taps → one effect; a tap while nothing is offered does nothing.
- [ ] Implement; `PresentationModule` passes `resolveGalleryLink`; `DomainModule` `factoryOf(::ResolveGalleryLink)`; commit.

### Task 4: The button

**Files:** `PhotoViewerScreen` top bar: a `Row` with the arrow and, when `opensInGallery`, `ic_photo_library` named `viewer_open_in_gallery`; `onOpenInGalleryClick`. Strings `viewer_open_in_gallery`, `viewer_gallery_gone`, `viewer_no_gallery_app` (EN/RU). Test: `PhotoViewerScreenTest` — shown only when offered, a tap reports.

### Task 5: The shell opens it

**Files:** `app/…/navigation/GalleryOpener.kt` (`fun interface GalleryOpener { fun open(uri: String, grantRead: Boolean): Boolean }`, `Context.openInGallery`, `rememberGalleryOpener()`); `handlePhotoViewerEffect(effect, onClose, galleryOpener, galleryGoneReporter, noGalleryAppReporter)` in `PhotoViewerDestination.kt`. Tests: `GalleryOpenerTest` (Robolectric `nextStartedActivity`, with and without the grant; `checkActivities(true)` with nothing to resolve → false), `PhotoViewerEffectHandlerTest`.

### Task 6: Docs, check, device

`photo-viewer.md` § Open in gallery (when offered, install rule, deleted original, no gallery app); `photos.md` pointer; map V2 status. `./gradlew check`; AC-14 on a throwaway AVD with the plain debug build.
