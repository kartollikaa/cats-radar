# Slice E7a — the coat question after a photo

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** the Counter's coat sheet after a photo keeps its photo in the header through a count, names its mode
with a *One cat · Several* connected group that also leads back to one tap, counts cats in their coat shapes,
and calls the paw *No coat*.

**Architecture:** one new intent, `CounterIntent.CoatPrompt.OneCatClicked`, which the prompt reducer
(`CoatCounting.kt`) answers by dropping the count. Everything else is `CoatPromptSheet.kt`: the header, a
connected group of two `ToggleButton`s, the tray in `coatShapeFor` shapes, and the paw's label passed to
`CoatGrid`.

**Tech Stack:** Compose Material 3 1.5.0-alpha27 (`ToggleButton`, `ButtonGroupDefaults.connected*ButtonShapes`,
`headlineSmallEmphasized`), Robolectric Compose tests in `:app`, `commonTest` for the Store.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 2, *The coat question after a
photo*; map row E7a.

## Global constraints

- What a coat tap, the paw, a tray tap, Save and dismissing write is unchanged (`CoatQuestion`).
- The map's filter keeps *Not specified*; only the prompt's paw tile and tray read *No coat*.
- EN and RU strings; no text in `:presentation` or `:ui` source.

## Tasks

### Task 1: One cat drops the count

- `CounterIntent.CoatPrompt.OneCatClicked`; `CoatPromptState.after` → `copy(counting = null)`.
- `CounterStorePhotoPromptTest`: *one cat after several empties the tray, and a coat then sets it and closes the
  prompt*; *one cat while asking changes nothing*.

### Task 2: The sheet

- `CoatPromptSheet.kt`: the header keeps the photo (or the paw in no coat's shape) and changes only its words;
  `OneOrSeveral` connected group with a check on the chosen button, `Role.RadioButton`, `selectableGroup`;
  the tray as shaped faces with a × badge and the empty line; *Not now* and *Save N cats* at the end.
  `CoatPromptAction.OneCatClicked`; `CounterDestination` maps it.
- `CoatGrid(unspecifiedLabel = …)`, defaulting to *Not specified*.
- Strings: `counter_coat_prompt_one`, `counter_coat_count_tray_empty`, `coat_none`.
- `CoatPromptCountingTest`: the group, its checked state, switching both ways, the photo in the header while
  counting, the shaped tray with Remove, *No coat*, the empty line; `CoatSheetsTest` keeps the map's *Not
  specified*.

### Task 3: The record

`coat.md` § Asked after a photo and § Several cats on the photo (the group, the way back, the header, *No coat*);
map row E7a `in-review`.

### Task 4: Renders, review, gate

Renders before and after (light, dark, font 1.5; asking, counting empty, counting four, full, no photo); draft
PR; `/code-review`; the gate with mutations (One cat keeps the tray; the photo back in the tray; the paw reads
*Not specified*; the group without a checked state); the acceptance audit; the look check by the owner.
