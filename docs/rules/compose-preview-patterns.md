# Compose Preview Patterns

## 1. Annotations

- `@ThemePreviews` (in `:ui`) is a multipreview that renders light and dark. Use it for every
  screen and major component.
- Always wrap preview content in `CatsRadarTheme` — the only required wrapper.
- Add `@Preview(widthDp = …, heightDp = …)` variants only when a layout genuinely depends on size.

```kotlin
@ThemePreviews
@Composable
private fun RegionRowPreview() {
  CatsRadarTheme {
    RegionRow(state = sampleRegionRow, modifier = Modifier.padding(16.dp))
  }
}
```

## 2. Sample data

- Sample state objects are `private val`s at the bottom of the component file, built with realistic
  values (a real-looking city name, a plausible count), not `"title"` / `0`.
- Previews are the place to exercise the states a mapper can produce: `Loading`, `Empty`, `Loaded`,
  long text, "Unresolved" / "No location" rows, rate `—`.

## 3. Backgrounds — `Surface` is optional

Render the component directly under `CatsRadarTheme`. **Don't wrap in `Surface` by default** — a
background-free preview shows the component in isolation. Add `Surface { … }` (or
`showBackground = true`) only for a whole screen or a component whose look depends on the surface
behind it.

```kotlin
// component — no background
@ThemePreviews
@Composable
private fun UndoChipPreview() {
  CatsRadarTheme { UndoChip(modifier = Modifier.padding(16.dp)) }
}

// screen — opt-in background
@ThemePreviews
@Composable
private fun StatisticsScreenPreview() {
  CatsRadarTheme { Surface { StatisticsScreen(state = sampleStatisticsLoaded) } }
}
```

## 4. Multiple states

Group the states of one component in a `Column` when they are small; give large states their own
preview function.

```kotlin
@ThemePreviews
@Composable
private fun RateBlockPreview() {
  CatsRadarTheme {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
      RateBlock(state = sampleRatePerHour)
      RateBlock(state = sampleRatePerMinute)
      RateBlock(state = sampleRateUnavailable)
    }
  }
}
```

## 5. Naming and placement

- Previews live in the same file as the component, after it, `private`.
- `ComponentNamePreview`; `ComponentNameLoadingPreview`, `ComponentNameEmptyPreview` for
  state-specific ones.
- Callbacks use the defaults (`{}`); a preview never wires behavior.
