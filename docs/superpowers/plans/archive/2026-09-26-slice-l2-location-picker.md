# Slice L2 — Set a cat's location on a map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The detail screen of a cat with no location offers *Set on map*, which opens a map with a pin fixed at its centre, *Where am I* and *Save*; saving gives the cat the point under the pin.

**Architecture:** `:domain` gains `WhereToLook` (the closest located cat in time, else the phone's last known position) and `LocatePhone` (a fresh fix, else a recent last known one, the same resolution `AttachLocation` uses, now shared). `LocationPickerStore` holds `Loading` / `Picking(start, locating, saving, moveTo)` and closes as soon as the cat is no longer without a location. `LocationPickerScreen` draws a MapLibre map in the app's style with the pin over its padded centre and reads the camera target on *Save*. `:app` adds the `LocationPicker(encounterId)` key, its destination with the location-permission launcher, and the screen name.

**Spec:** `docs/superpowers/specs/2026-09-25-location-by-hand-design.md` § The picker. Map: L2. Criteria: `location-by-hand-l2.md` in the acceptance directory.

## Global Constraints

- The map opens once, where `WhereToLook` says, and never moves by itself afterwards; only *Where am I* moves it, once per tap.
- No permission request on opening; the request comes from *Where am I* alone.
- EN and RU strings for every new text; the pin is decorative (no content description).
- `MapArea` is the one area type the map screens take; a street-sized area is built by one function for both.

---

### Task 1: Domain — where to look, where the phone is
`GeoPoint` public; `LocationProvider.resolveNow(now)` extracted from `AttachLocation` (fresh fix, else recent last known,
with the backstop); `LocatePhone`; `WhereToLook`; Koin factories. Tests: `LocatePhoneTest`, `WhereToLookTest`; `AttachLocationTest` unchanged and green.

### Task 2: Presentation — the picker Store and the detail's offer
`MapAreas.kt` (`areaAround`, shared with `MapStateMapper`); `LocationPickerState` / `Intent` / `Effect` / `StateMapper` /
`Store`; `EncounterDetailState.Loaded.setsLocation`, `EncounterDetailIntent.SetLocationClicked`,
`EncounterDetailEffect.OpenLocationPicker`. Tests: `LocationPickerStateMapperTest`, `LocationPickerStoreTest`,
`EncounterDetailStateMapperTest`, `EncounterDetailStoreTest`, `MapStateMapperTest` unchanged and green.

### Task 3: UI — the picker screen and the button
`MapStyle.kt` (the style the Map tab and the picker share); `ic_pin`, `ic_my_location`; `LocationPickerScreen` (map, pin,
hint, back, *Where am I*, *Save*, the unavailable message); *Set on map* in `WhereCard`; strings. Tests:
`LocationPickerControlsTest` (Robolectric: Save and Where am I disable while busy, dispatch once), `EncounterDetailScreenTest` (the button only for a cat with no location).

### Task 4: App — key, destination, wiring
`LocationPicker` key; the location-permission launcher shared with the Counter; `LocationPickerDestination`; the nav
entry and the detail's push; `AnalyticsScreen.LOCATION_PICKER`; Koin; `KoinRuntimeResolutionTest`. Tests:
`LocationPickerEffectHandlerTest`, `ScreenViewTrackerTest`, `EncounterDetailEntryTest` (the button pushes the picker).

### Task 5: Docs, check, device, review
`encounter-detail.md`, `location.md`, `map.md` (the second screen that fetches tiles), `analytics.md`; the map's L2
status; L1's plan archived; `./gradlew check`; the picker on the emulator (open, pan, save, the cat located);
`/code-review`; acceptance gate.
