# Slice E7b — The coat card and the coat sheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a cat's page the coat becomes a card: its face, its name and a tonal Change pill. The card opens a sheet over the detail, headed by the cat's own face, with the coat grid and a *No coat* tile. A tap on a coat sets it and closes the sheet; *No coat* clears it and closes; dismissing changes nothing. The inline strip and its opening-position rule go.

**Architecture:** The sheet is a Navigation 3 destination with its own Store, as the map's spot sheet is.
- **`:presentation`**, package `coatsheet`:
  - `CoatSheetState`: `Loading`, or `Open` with the coat and a hint token;
  - `CoatSheetIntent`: `CoatClicked(coat)` and `NoCoatClicked`;
  - `CoatSheetEffect.Close`;
  - `CoatSheetStateMapper` picks the hint;
  - `CoatSheetStore(catId, …)` observes the cat through `ObserveEncounter`. It writes with `SetCoat`, closes after the first answer, and closes when the cat goes. Once gone, a cat brought back does not reopen it.
- **The detail:**
  - `CoatPicked` gives way to `CoatCardClicked(catId)`, which emits `OpenCoatSheet(catId)` for a cat on the pages;
  - the Store stops writing the coat itself.
- **`:ui`:**
  - `CoatCard` on the page in place of the Coat section and its strip;
  - `CoatSheetContent`: E7a's `SheetHeader` with the cat's face, then `CoatGrid` with the *No coat* cell;
  - `CoatPicker` and its cell go.
- **`:app`:**
  - the `CoatSheet(catId)` key with `bottomSheet()` metadata;
  - its entry beside the detail's, and the destination collecting `Close`;
  - the detail destination pushes the key;
  - the Koin bindings and `KoinRuntimeResolutionTest`.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md`:
- § 3 *The coat card* and *The coat sheet*;
- *Behaviour changes* 1;
- map row E7b.

Prototype versions 5 and 14. Criteria: `expressive-e7b.md`. Stacked on E9 (#233).

## Decisions inside the spec

- **The card follows prototype version 5:**
  - `surfaceContainerLow` with `large` corners, 16 dp inside;
  - the face (54 dp) in its coat's 72 dp shape on `surfaceContainerHighest`, or the paw in no coat's shape;
  - the name in `titleMediumEmphasized` over "Coat" in `bodyMedium`/`onSurfaceVariant`;
  - with no coat, "Coat not noted" over "Add it from the sheet";
  - a tonal pill at the end: **Change**, or **Add** with no coat.
  - The whole card is one button, read as one item, and the pill is its visible cue, not a second control, as on the Where card.
- **The sheet's head:**
  - the cat's face in its coat's 64 dp shape on `primaryContainer`, or the paw in no coat's shape on `surfaceContainerHighest`;
  - "What coat was it?" in `headlineSmallEmphasized`;
  - under it, "Pick another, or “No coat”" with a coat, or "Tap the coat that fits" without, as prototype version 14 writes them.
- **The grid** is E4's `CoatGrid`: the cat's coat is ringed, and *No coat* (`coat_none`) is the twelfth cell, ringed when no coat is noted. A tap on the ringed coat sets it again and closes.
- **The first answer closes the sheet.** A second tap before it is gone writes nothing, and a failed write closes it as well: the card shows what is stored.
- **The spec's *Testing* line** "the ringed coat clears and closes" predates the owner's choice of the *No coat* tile, which § 3 records. It is corrected to "*No coat* clears and closes".
- **"Setting the coat while a photo attaches still keeps both"** moves to the sheet's Store, which writes the coat while the detail's attempt runs.

## Global Constraints

- The coat stored and when it is written do not change; only where the choice is made does.
- Gradle only through `ctx_execute`: `--rerun --no-build-cache`, results read from the JUnit XML, every log headed by its sha, and every commit compiling.

### Task 0: Freeze the criteria (`expressive-e7b.md`)
### Task 1: The sheet's Store — state, intent, effect, mapper, `CoatSheetStore`, tests first
### Task 2: The detail opens it — `CoatCardClicked` and `OpenCoatSheet`; `CoatPicked` goes; tests follow
### Task 3: The card and the sheet's content in `:ui`; `CoatPicker` goes; Robolectric tests first
### Task 4: The destination — key, entry, destination, Koin; an entry test from the card to the sheet and back
### Task 5: The record — `coat.md` § Changing it later, `encounter-detail.md`, spec *Testing* line, map row E7b
### Task 6: Renders, device, review, gate, acceptance gate
