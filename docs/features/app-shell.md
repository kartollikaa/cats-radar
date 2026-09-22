# App shell

`:app` hosts a single-activity Compose UI. `MainActivity` wraps one `CatsRadarNavHost()` in
`CatsRadarTheme` inside a full-screen `Surface`; the nav host owns a Navigation 3 `NavDisplay` over
a back stack that starts at — and today only ever contains — the `Counter` entry.
`CatsRadarApplication.onCreate()` starts Koin with four modules (`domainModule`, `dataModule`,
`presentationModule`, `workerModule`) and then initializes `WorkManager` by hand with a
Koin-backed `WorkerFactory`, because the manifest disables WorkManager's own default initializer —
that provider would otherwise run before Koin has started.

Every screen follows the same minimal MVI shape through one base class,
`Store<State, Intent, Effect>`. A `Store` is a `ViewModel` holding a `MutableStateFlow<State>`
(exposed read-only as `state`) and a buffered `Channel<Effect>` (exposed as `effects`).
`dispatch(intent)` launches `handle(intent)` in `viewModelScope`; concrete stores implement
`handle` to call use cases, reduce state with `setState { copy(...) }`, and fire one-shot
`emit(effect)` calls for anything the screen shouldn't keep re-showing (navigation, haptics, a
permission request). There are no separate reducer/actor classes and no shared message bus — each
screen's Store is self-contained, registered with Koin as a `viewModelOf`, and created once per
Navigation 3 entry via `rememberViewModelStoreNavEntryDecorator`.

## At the edges

State updates are atomic under concurrent writers: `setState` goes through
`MutableStateFlow.update`, and 8 concurrent coroutines issuing 2,000 increments each land all
16,000 without a lost update (`StoreTest`, *concurrent setState calls do not lose updates*). An
effect is delivered to exactly one collector and is never replayed — a second collector attaching
after the first already consumed an effect sees nothing (*an effect is delivered exactly once and
is not replayed to a later collector*), and `emit` does not suspend when no collector is attached
at all, so a Store can safely emit from a code path nothing happens to be observing yet
(*emitting without a collector attached does not suspend*). Work started inside `handle` is
ordinary structured concurrency scoped to `viewModelScope`: clearing the owning `ViewModelStore`
cancels it, which is what lets `CounterStore`'s undo-timer coroutine stop cleanly when the screen
goes away (*work started in handle is cancelled when the scope closes*).

Dependency injection has a deliberately narrow single owner: only `:app`'s `di` package constructs
Koin `module {}` blocks; `:domain`, `:data`, and `:presentation` expose plain constructors and are
otherwise Koin-agnostic. Two different tests check the DI graph against two different failure
shapes: `KoinModulesTest` walks constructor parameters statically (`module.verify()`) to prove the
graph is *declarable*, but that check is blind to anything resolved by hand inside a lambda
binding or a composable (`androidContext()`, `koinInject<Haptics>()`) — nothing reflects a
constructor for those. `KoinRuntimeResolutionTest` closes that gap by actually starting Koin and
resolving exactly those hand-resolved types, so a deleted binding fails a JVM test instead of
surfacing on the user's first tap. A few singletons are deliberately lazy inside their own
constructor (`FusedLocationProvider`'s Play Services client, `WorkManagerLocationAttachScheduler`'s
`WorkManager` instance), specifically so Koin's own eager DI-graph resolution doesn't itself
trigger a Play Services connection or touch `WorkManager` before `WorkManager.initialize()` has
run.

## Where the code lives

- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/Store.kt`
- `app/src/main/kotlin/dev/catsradar/app/CatsRadarApplication.kt`, `MainActivity.kt`
- `app/src/main/kotlin/dev/catsradar/app/navigation/CatsRadarNavHost.kt`, `Counter.kt`,
  `CounterEffectHandler.kt`
- `app/src/main/kotlin/dev/catsradar/app/di/DomainModule.kt`, `DataModule.kt`,
  `PresentationModule.kt`, `WorkerModule.kt`
- `ui/src/main/kotlin/dev/catsradar/ui/theme/CatsRadarTheme.kt`

## Not handled yet

`Counter` and `Encounters` are the only two `NavKey`s behind the bottom `NavigationBar`; the
root-stack back rule it enforces is covered in `browsing-cats.md`. The other four screens the
design spec lists (`EncounterDetail`, `Statistics`, `Regions`, `RegionEncounters`, `Settings`)
don't exist yet. `CatsRadarTheme` sets only a light/dark `colorScheme`; it has no custom typography
or shapes, unlike the fuller theme surface `docs/rules/compose-patterns.md` §3 describes.
