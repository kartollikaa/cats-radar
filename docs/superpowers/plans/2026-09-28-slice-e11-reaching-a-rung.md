# Slice E11 — reaching a rung

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** a count that lands on a milestone rung celebrates on the ring (bounce, full glowing ring, a filled
"100 cats!" pill in the outing's place) until the run closes, instead of a toast.

**Architecture:** `CounterStore` keeps the rung it is celebrating and the latest `Stats`, so it can end the moment
outside a stats emission; `CounterStateMapper.map(milestoneMoment = …)` turns the moment into state and, while it
lasts, drops the goal and the outing so the ring carries only the moment. `TallyBlock` draws the full ring with a
glow, bounces the cookie, and `RingTags` shows the pill. `CounterEffect.MilestoneReached` and the toast go.

**Tech Stack:** Compose `Animatable` on the motion scheme; `commonTest` Store tests with a virtual clock;
Robolectric UI tests in `:app`.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 2, *Reaching a rung*; map row E11.

## Global constraints

- Each rung once, persisted before it shows (`SettingsRepository.setLastSeenMilestone`).
- The count keeps its size with the moment showing.
- EN and RU strings; the `counter_milestone` plural carries the words.

## Tasks

### Task 1: The moment in the Store and the mapper

- `MilestoneMomentState(value: Int)`, `CounterState.milestoneMoment`.
- Mapper: `map(…, milestoneMoment: Int? = null)`; while set, `milestone` and `currentOuting` are null.
- Store: `announceMilestone` persists, then starts the moment; the run's close ends it; with no run open it ends
  after `Tuning.UNDO_VISIBLE`; a total below the rung takes it back and restores the rung below as last seen.
- Remove `CounterEffect.MilestoneReached`, `MilestoneAnnouncer`, the toast and their tests.
- Tests: `CounterStoreMilestoneMomentTest` (reach by a tap, persisted first; lasts until the run closes; taps keep it;
  undo below takes it back and re-reaching celebrates; a rung reached with no run lasts the window; a rung already
  seen stays quiet; the next launch does not repeat it); `CounterStateMapperTest` whole-state.

### Task 2: The ring

- `TallyBlock`: a full ring with a glow and no dot while the moment lasts; the bounce; the count's clearance
  unchanged. `RingTags`: the moment pill at the bottom, `primary` fill, a polite live region.
- Tests: `CounterMilestoneMomentTest` (the pill's words and live region, no goal tag, the count's size unchanged, the
  ring full at three o'clock in `primary`).

### Task 3: The record

`counting-cats.md` § Milestones; spec § 2 (done in the plan commit); map row E11 `in-review`.

### Task 4: Renders, review, gate

Renders (light, dark, font 1.5; at rest, the moment, the moment on a walk); draft PR; `/code-review`; the gate with
mutations (no persistence before showing; the run's close not ending it; undo not taking it back; the goal tag shown
during it; the pill not a live region); the audit; the look check by the owner.
