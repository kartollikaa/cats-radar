# MVI Architecture

One pattern for every screen, kept to the minimum that still gives a testable, single-source-of-truth
presentation layer. There are no separate Reducer / Actor / Bootstrapper classes and no message bus.

## Shape

Each screen has four types in `:presentation`, in one package:

```kotlin
data class CounterState(...)                       // what to render — immutable, no lambdas
sealed interface CounterIntent { ... }             // what the user (or the system) did
sealed interface CounterEffect { ... }             // one-shot: navigate, toast, haptic
class CounterStore(...) : Store<CounterState, CounterIntent, CounterEffect>(initialState)
```

`Store` is the only base class:

```kotlin
abstract class Store<State, Intent, Effect>(initial: State) : ViewModel() {
  val state: StateFlow<State>
  val effects: Flow<Effect>                 // buffered; each effect is delivered once
  fun dispatch(intent: Intent)              // launches handle(intent) in viewModelScope
  protected abstract suspend fun handle(intent: Intent)
  protected fun setState(reduce: State.() -> State)
  protected suspend fun emit(effect: Effect)
}
```

`handle` is where the work happens: call use cases, `setState { copy(...) }`, `emit(...)`. Continuous
inputs (Room `Flow`s, a ticking clock) are collected in `init` and fold into state with `setState`.

## Rules

- **State is data.** `data class`, immutable collections (`kotlinx.collections.immutable`), no
  function types, no platform types (`Uri`, `Bitmap`, `Context`). Strings are already formatted.
- **Mappers build state.** Domain → State conversion is a `*StateMapper` class with a plain `map`
  function, injected into the Store, unit-tested with `assertEquals` on the whole result. Formatting,
  labels, visibility decisions, ordering — all mapper work. See
  [compose-patterns.md](./compose-patterns.md) *No mapping in composables*.
- **Intents are facts, not commands to the UI.** `TallyClicked`, `UndoClicked`, `PhotoCaptured(uri)`,
  `PermissionResult(granted)`. Never `ShowToast`.
- **Effects are one-shot.** Navigation, snackbars, haptics, launching a system picker. Anything the
  screen must keep showing is State, not an Effect.
- **No business logic in the Store.** Rates, sessions, location precedence, import rules live in
  `:domain` as pure functions and use cases; the Store orchestrates and maps.
- **One Store per screen**, scoped to its Navigation 3 entry. Shared data flows through repositories,
  not through Stores talking to each other.

## Wiring

- `:ui` composables take `state` and callbacks; the screen entry in `:app` does
  `val state by store.state.collectAsStateWithLifecycle()` and forwards callbacks as
  `{ store.dispatch(CounterIntent.TallyClicked) }`.
- Effects are collected once per entry with `LaunchedEffect(store) { store.effects.collect { … } }`.
- Stores are created by Koin (`koinViewModel()`), one per Navigation 3 entry via
  `rememberViewModelStoreNavEntryDecorator`.

## Testing a Store

`runTest` + a fake use case + `turbine` on `state`/`effects`. Assert the whole `State` after each
intent; don't reach into private fields. A Store test that needs Android is a sign that platform work
leaked out of `:data`.
