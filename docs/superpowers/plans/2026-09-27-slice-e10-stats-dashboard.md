# Slice E10 — Statistics as a dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Stats tab opens on its headline, then a per-day bar chart with a 7 / 30 days pill, six stat tiles, the coats with share bars in their fur colours, Places, and the outings as a two-column grid.

**Architecture:** `StatsCalculator` adds `byDay` (thirty `DayCount`s, oldest first, today last) from the same local dates as the windows. `StatisticsStore` keeps the chart's choice — the range and the picked day — in a `MutableStateFlow` combined with the stats, like `MapStore`'s choices; `StatisticsStateMapper` turns the series and the choice into `DayChartState` (the bars with their heights, axis labels and spoken labels, and the picked day's line). `:ui` draws it. A bar is keyed by its epoch day, because `:ui` does not see kotlinx-datetime.

**Tech Stack:** kotlinx-datetime, Compose Material 3 Expressive (`ToggleButton` in a connected pair), Robolectric `@GraphicsMode(NATIVE)` in `:app`.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 5 *Statistics*. Map: row E10 and *Slice E10*. Criteria: `expressive-e10.md`.

## Decisions inside the spec

- **Card headers stay `SectionCard`'s**: the title above the card, the pill in its `action` slot at the header's end. Every titled card in the app has that shape (app-shell.md *Rhythm*).
- **Bar heights.** A day with no cats draws a 4 dp stub; every other day rises from the stub, the tallest to the full height, so one cat among fifty never reads as none.
- **Axis labels.** Seven days: the short weekday under each bar. Thirty days: the short day and month under today's bar and every seventh bar before it; each label is centred under its bar and kept inside the chart.
- **The picked day** is kept by date: it survives a new day starting and a range switch that still shows it, and falls back to today when the range no longer does. The line reads "6 cats · Sat, Sep 26" in one line; TalkBack reads each bar as "6 cats, Sat, Sep 26".
- **The share bar** is the Places drill-down's, moved to `ui/components`, with the fill colour a parameter. A coat's fill is its fur alone, with no outline (owner, 2026-09-28: "remove that black outline"); *Not specified* is `outline`. The busiest row, *Not specified* included, fills the bar.
- **Numbers large, units small** in the tiles and the outings grid: the value's digit runs keep the value style and the words around them take a smaller one, applied to the label the resources already format ("14 h 20 min", "4.2 / h", "6 days").
- **Tile labels:** Today, Last 7 days, Last 30 days, With a photo, Streak, Longest streak. **Outings grid:** Outings, Time out, Cats per hour, Walked, Cats per km, Best outing (with its rate in the label, "Best outing · 1.3 / min"), two to a row in that order, the walked pair only when something was walked and the best outing only when there is one.
- The counts stay `Int` in State where a plural reads them (`currentStreak`, `longestStreak`, the bars' and the picked day's counts).

## Global Constraints

- Counter files (`ui/…/counter/`, `counting-cats.md`, `walking-mode.md`, `import.md`, spec § 2) are not touched.
- Gradle only through `ctx_execute`; `--rerun --no-build-cache`; results from the JUnit XML; every log starts with the sha; every commit compiles the touched modules' main and test sources.

---

### Task 0: Freeze the criteria (`expressive-e10.md`)

### Task 1: `Stats.byDay` (`:domain`, `commonTest`)

- [ ] **Failing tests** in `StatsCalculatorTest`: thirty entries oldest first with today last; a cat logged abroad counts on its own date; a day with no cats is zero; deleted and future-dated cats are in no bar; the last seven entries sum to `lastSevenDays` and all thirty to `lastThirtyDays`.
- [ ] **Implement** `data class DayCount(val date: LocalDate, val count: Int)` and `byDay` in `StatsCalculator`; update the two `Stats(...)` fixtures in `StatisticsStateMapperTest`.

### Task 2: Short dates in `DateTimeFormatter` (`:presentation`)

- [ ] **Failing tests** in `AndroidDateTimeFormatterTest` and `…RussianTest`: `weekday`, `dayMonth`, `weekdayDayMonth` for 2026-09-26 in English and Russian, no year anywhere.
- [ ] **Implement** with `DateFormat.getBestDateTimePattern(locale, "EEE" | "dMMM" | "EEEdMMM")`; `FakeDateTimeFormatter` returns tagged ISO dates.

### Task 3: The chart's state, mapper and Store (`:presentation`, `commonTest`)

- [ ] **Failing mapper tests** (whole-state or whole-chart `assertEquals`): the week chart (seven bars, weekdays, today last and marked, heights over the tallest, today picked); the month chart (thirty bars, dates at 0/7/14/21/28 days back); a picked day outside the range falls back to today; a day in range stays picked; no cats → every height 0; the coat shares over the busiest row, *Not specified* included; the streaks as counts.
- [ ] **Failing Store tests:** `RangePicked(MONTH)` gives thirty bars; `DayPicked` moves the line; back to the week with an old day picked names today.
- [ ] **Implement** `ChartRange`, `DayChartState`, `DayBarState`, `PickedDayState`, `CoatShareState.share`, `currentStreak`/`longestStreak`, the two intents and the Store's choice.

### Task 4: The screen (`:ui` + `:app` Robolectric)

- [ ] **Failing tests** `app/src/test/…/statistics/StatisticsScreenTest.kt` (`@GraphicsMode(NATIVE)`, `w411dp-h891dp`): seven bars for the week and thirty for the month; the pill reports each range; a bar tap reports its day; the line names the picked day and the tiles under it do not move when another day is picked; today's bar is `primary` and another `surfaceContainerHighest`; six tiles, each one item with its number and label; a coat row's fill is its share of the track; the outings figures two to a row with the best outing's rate in its label.
- [ ] **Failing JVM test** `ui/src/test/…/statistics/SmallUnitsTest.kt`: digit runs keep the base style, words take the unit style, a text with no digit stays whole.
- [ ] **Implement** the headline in `displayLargeEmphasized`; `DayChart` (bars, axis labels, picked line) in a `SectionCard` with `RangePill`; `StatTiles`; the coat rows with `ShareBar`; the outings grid; EN and RU strings; previews for week, month and no walks. Delete the strings nothing uses.
- [ ] **Wire** `StatisticsDestination`'s two callbacks to the intents.

### Task 5: The record

`statistics.md` (`byDay`, the screen: chart, pill, picked line, tiles, share bars, outings grid), `coat.md` § *In the statistics* (the share bar in fur colour), map row E10 `in-review`.

### Task 6: Renders, device, review and the gate

The render harness before and after (light, dark, font 1.5, week and month); the running app on `emulator-5554`; `/code-review`; the gate in a detached scratch worktree; the acceptance gate.
