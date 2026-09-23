# Compose Patterns

Conventions for Compose UI in Cats Radar. UI lives in `:ui`; it renders state produced in
`:presentation` (see [mvi-architecture.md](./mvi-architecture.md)) and never builds it.

---

## 1. File & component structure

- **One screen, one file.** Each screen (`CounterScreen`, `StatisticsScreen`) lives in its own
  file with its private composables below the entry point.
- **State-driven.** A screen takes a `state` object and renders it. Nothing else determines what is
  drawn.
- **Callbacks are parameters.** Every user action is a lambda parameter (`onTallyClick`,
  `onUndoClick`), defaulting to `{}` so previews stay one-liners.
- **Previews next to the component.** Each screen and major component has a `@ThemePreviews`
  preview in the same file — see [compose-preview-patterns.md](./compose-preview-patterns.md).

---

## 2. State handling

- **State is the single source of truth.** The composable is a pure function of `state`.
- **Sealed hierarchies** for screens with distinct modes (`Loading` / `Empty` / `Loaded`).
- **Immutable collections in State.** Collection fields are `ImmutableList` / `ImmutableSet` /
  `ImmutableMap` from `kotlinx.collections.immutable` (default `persistentListOf()`), never plain
  `List`. Convert incoming lists with `.toImmutableList()` in the mapper.
- **Never put a lambda in a State class.** No exceptions — see below.
- **Never map in a composable.** Building the item list, deciding visibility, converting a domain
  enum to an icon or label, formatting — all of it belongs in a mapper in `:presentation`.

### No lambdas in State — never

A `State` class must not have a function-typed property: not `onClick: () -> Unit`, not a lambda
behind a `typealias`, not in a private item model either. State describes *what to render*; a
callback is *what to do*, and it belongs in the composable's parameter list.

- **It breaks equality.** Two lambdas with identical bodies are different instances, so a `data
  class` holding one never equals a fresh copy; Compose can never skip, and `distinctUntilChanged`
  on the state flow stops working.
- **It breaks the reducer.** State is produced in the Store, which knows nothing about the view.
- **It leaks scope.** A captured lambda holds whatever built it inside an object that outlives
  recomposition.

Carry **identity** in the State and let the composable resolve the action:

```kotlin
// ❌ never — the callback is state
@Immutable
data class RegionRowState(val label: String, val count: Int, val onClick: () -> Unit)

// ✅ the State names which item this is; the callback stays a composable parameter
@Immutable
data class RegionRowState(val key: RegionKey, val label: String, val count: Int)

@Composable
fun RegionList(
  rows: ImmutableList<RegionRowState>,
  onRowClick: (RegionKey) -> Unit,
  modifier: Modifier = Modifier,
)
```

### No mapping in composables

Composables render state; they don't build it. Banned inside `@Composable` files:

- building the list of items to show;
- deciding visibility from domain fields (`if (source == NONE && permissionDenied)`);
- `when (domainEnum) -> icon / label / text`;
- formatting — dates, durations, plurals, rates.

A mapper is a plain class you unit-test with `assertEquals` on the whole result; the same logic in a
composable needs a UI test to reach.

```kotlin
// ❌ the composable decides
Text(if (rate >= 1.0) "%.1f cats/min".format(rate) else "%.0f cats/h".format(rate * 60))

// ✅ the mapper decides; State carries the answer; the composable draws it
Text(state.rateLabel)
```

**What legitimately stays in the composable:** view-only lookups with no domain meaning — a
`testTag`, a `Modifier`/theme choice derived from a state flag, `stringResource` lookups for static
labels.

### User-facing text: a token in State, the words in resources

No user-facing string is written in `:presentation` or `:ui` source. State never carries an English
sentence, because a sentence cannot be translated later without rewriting the mapper and its tests.

The mapper decides *which* label applies and puts a `:presentation` enum in State; the composable
resolves that enum to `stringResource`. That keeps the decision where it can be unit-tested and the
words where `values-ru` can translate them. `:ui` cannot see `:domain`, so the token is a
presentation type, never the domain enum itself.

```kotlin
// ❌ the mapper writes the words
LocationSource.LAST_KNOWN -> "Last known location"

// ✅ the mapper picks the case; strings.xml holds the words
LocationSource.LAST_KNOWN -> LocationLabel.LAST_KNOWN
```

A `when` over such a token inside a composable is the sanctioned exception to *No mapping in
composables* above: it resolves a token to text and decides nothing. Anything else in that `when` —
visibility, ordering, a second branch on another field — means the decision leaked into the view.

Cover the mapper's token choice with a test that asserts every case **and** that no two cases
collapse onto one token; a `when` arm pointing at the wrong token is otherwise invisible.

---

## 3. Theming

- Wrap every screen and preview in `CatsRadarTheme`. It configures Material 3
  `colorScheme`, `typography`, and `shapes`; reference them through `MaterialTheme.*`.
- No hard-coded colors in screens. A color that isn't in the scheme goes into the theme first.
- Dynamic color comes from `:app`, never from `:ui`: the activity passes the wallpaper's scheme on
  Android 12+ as `CatsRadarTheme(colorScheme = …)`. Without an argument the theme uses the explicit
  teal light and dark schemes, so previews are deterministic and look the same on every machine.
- **Insets.** A screen hosted under the app `Scaffold` gets system-bar insets from it. A screen
  hosted in a bottom sheet or a dialog handles its own: `Modifier.navigationBarsPadding()` on a
  `Column`, or merged into `contentPadding` on a `LazyColumn` (a `Modifier` insets the viewport,
  not the space the items scroll through). `imePadding()` only where there are text fields.

---

## 4. Layout & spacing

Prefer paddings and arrangements over `Spacer`:

- uniform gap between children → `Arrangement.spacedBy(x.dp)`;
- one-off gap before/after one element → that element's own `Modifier.padding(...)`;
- container edge inset → `Modifier.padding(...)` or `contentPadding`;
- push siblings apart → `Arrangement.SpaceBetween`, not a weighted `Spacer`.

```kotlin
// ❌
Column {
  Text(title)
  Spacer(Modifier.height(8.dp))
  Text(subtitle)
}

// ✅
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
  Text(title)
  Text(subtitle)
}
```

`Spacer` is for the rare layout no `Arrangement` expresses.

---

## 5. Callbacks

- **Explicit lambdas**, one per action, defaulting to `{}`.
- **Interaction wrappers.** When a callback carries more than one value, wrap the payload in an
  `*Interaction` data class instead of a multi-parameter lambda. Call sites lose parameter names
  otherwise, and every new field churns every signature. An `Interaction` is a payload, never a
  callback holder.
- **Pass callbacks down unchanged.** Intermediate composables forward lambdas as-is. Wrap only at
  the level that owns data the child doesn't know (a pager adding its `page`), or to hand a
  zero-parameter lambda to a child.
- `BackHandler` intercepts system back where a screen needs it; navigation itself stays in `:app`.

---

## 6. Best practices

- **Stateless composables.** Local `remember` is for view-only state (scroll, animation,
  transient expansion) — never for anything the Store should know.
- **`modifier: Modifier = Modifier` is the first optional parameter.**
- **`contentDescription`** on every meaningful image and icon; `null` on purely decorative ones.
- **Consistent naming.** `Screen` suffix for entry points; `Loaded`, `Loading`, `Empty` for
  state-specific content.

---

## 7. Dimensions and design tokens

### Default: write the value where it's used

A `Dp`, `Shape` or `PaddingValues` that appears **once** goes inline, at the call site. A file that
opens with a dozen single-use `private val`s costs the reader a jump for a number that was already
next to the layout.

### Extract only for one of two reasons

1. **Two or more uses that must stay equal** — changing one and not the other would be a bug. Two
   values that merely coincide do not qualify.
2. **It's part of a component's API** — a `*Defaults` object or a value a caller passes back in.

A token shared across screens belongs in `CatsRadarTheme` or a `*Defaults` object, not at the top of
one screen file. detekt's `MagicNumber` ignores named arguments and `**/ui/**`, so extraction to
satisfy the linter is extraction for a rule that isn't running.

### Naming, once extraction is earned

Following the Compose API guidelines, top-level or `object` values that are UI tokens — `Dp`,
`Color`, `Shape`, `PaddingValues`, durations, alphas, sizes — use **PascalCase**, even as
`const val`. Plain constants that are not UI tokens use `UPPER_SNAKE_CASE`. Regular top-level `val`s
holding behavior use camelCase.

```kotlin
// ❌
private val ICON_SIZE = 24.dp
private const val DEFAULT_ALPHA = 0.74f

// ✅
private val IconSize = 24.dp
private const val DefaultAlpha = 0.74f
private const val MAX_IMPORT_ITEMS = 100     // not a UI token
```

---

## 8. Avoiding redundant recomposition work

- A private `@Composable` whose whole job is to return a cached value is named
  `remember<Thing>()` so callers know it is stable and safe to hoist.
- Call `remember`-backed composables once above a loop and pass the result down; per-iteration calls
  allocate a slot each and, for item-specific values without `key(item.id)`, can reattach a
  remembered instance to the wrong item after a reorder.

---

## 9. Example

```kotlin
@Composable
fun CounterScreen(
  state: CounterState,
  modifier: Modifier = Modifier,
  onTallyClick: () -> Unit = {},
  onUndoClick: () -> Unit = {},
  onCameraClick: () -> Unit = {},
) {
  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(state.totalLabel, style = MaterialTheme.typography.displayLarge)
    TallyButton(onClick = onTallyClick, modifier = Modifier.fillMaxWidth())
    if (state.undoVisible) UndoChip(onClick = onUndoClick)
    CameraButton(onClick = onCameraClick)
  }
}

@ThemePreviews
@Composable
private fun CounterScreenPreview() {
  CatsRadarTheme {
    Surface { CounterScreen(state = CounterState(totalLabel = "42", undoVisible = true)) }
  }
}
```
