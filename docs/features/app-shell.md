# App shell

`:app` hosts a single-activity Compose UI. `MainActivity` wraps one `CatsRadarNavHost` in
`CatsRadarTheme` inside a full-screen `Surface`, and turns the widget's Photo launch into a camera
request that sends the nav host back to the Counter (see [widget.md](./widget.md)). A launcher start
that Android stacks on the app's own task as a second copy finishes at once. The nav host owns a
Navigation 3 `NavDisplay` over a back stack rooted at `Counter`, with the selected bottom-navigation
tab above it and any detail above that. The tab-to-destination mapping is exhaustive over
`BottomNavTab`, so a new tab does not compile until it has a destination, and the selected tab is
read back out of the stack rather than stored — a detail pushed above a tab still reports the tab it
belongs to. `CatsRadarApplication.onCreate()` starts Koin with four modules (`domainModule`,
`dataModule`, `presentationModule`, `workerModule`) and then initializes `WorkManager` by hand with
a Koin-backed `WorkerFactory`, because the manifest disables WorkManager's own default initializer —
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

## System bars and insets

The app draws **edge to edge**: the window background and the app's own surface reach under the
status and navigation bars, and only the content is inset. `MainActivity` calls `enableEdgeToEdge()`
and fills the window; the `Scaffold` supplies the insets, each screen applies them through its own
`contentPadding`, and the bottom bar pads itself so its colour runs to the bottom of the screen with
the gesture indicator over it.

Two things this replaced, both wrong:

- `safeDrawingPadding()` on the root surface, which inset the *whole* app, so the app's colour never
  reached the bars and the window background showed through instead.
- `contentWindowInsets = WindowInsets(0)` on the `Scaffold`, which then had to be compensated for
  screen by screen.

The window background is a colour with a `values-night` variant rather than a theme parent: the
platform has no `Theme.Material.DayNight`, so a light parent would keep showing a light strip behind
the bars under a dark app, and a light flash before the first Compose frame.

## Look

**Colour.** Light and dark schemes set every colour role in `CatsRadarColors.kt`, on Material 3's
tones, seeded from the launcher icon's teal (`#4CAF93`) with a coral tertiary. Dynamic colour is off:
previews stay deterministic, and the home-screen widget reads the same two schemes rather than the
wallpaper's. The values come from `tools/make-palette.py`, which prints both schemes as Kotlin and
refuses to print one whose text would fall under WCAG AA; changing the palette means changing the
recipe and re-running it, not hand-editing one role.

`CatsRadarColorsTest` holds the palette to three things, in both themes: every text colour reads at
WCAG AA against the surface it is meant for; no role is left at Material's default, found by
reflection so a role added in a later Material version is caught too; and the primary is still a
saturated teal. Each check has been broken on purpose and caught.

The **window background** is the theme's surface in both modes, because the window is painted
before Compose draws its first frame; `WindowBackgroundTest` fails if the two drift apart.

**Shape and type.** Corners are rounder than Material's defaults at every size, and display and
headline styles are heavier. The font is the platform's; nothing is bundled.

**Rhythm.** Screens that hold rows — Statistics, Settings, an encounter's detail — group them in
titled cards (`SectionCard`) on the theme's low surface, with the title in the primary colour; a
headline number sits in a primary-container card of its own. The Encounters list is the one place
rows are cards individually, since each outing is one run of them. A value that does not fit beside
its label moves under it, end-aligned, rather than squeezing the label; a card's title is a heading
and each row reads as one item to TalkBack. A setting's whole row toggles it, not only its switch.

**Navigation.** The bottom bar is `ShortNavigationBar`, five tabs — Counter, Encounters, Map, Stats,
Settings — with Material Symbols Rounded icons (Apache 2.0). The selected tab is marked by the bar's
indicator pill; only the map and the settings gear also change to their filled form, because the
other glyphs have no separate filled version. The label is always shown and names the tab, so the
icons carry no content description of their own. Each label gets a fifth of the bar, which on a
360dp-wide phone is narrower than «Статистика», so the Russian stats tab says «Итоги».

**Why not `MaterialExpressiveTheme`.** In the stable material3 the app uses, it and `MotionScheme`
are internal — public only in the 1.5 alphas. The theme stays on `MaterialTheme`, and a screen that
wants springy motion gives its own animation a spring spec.

## At the edges

No key can appear on the back stack twice. `NavEntry.contentKey` defaults to the key itself and
navigation3-runtime has no uniqueness guard, so two entries sharing a key would silently share one
`ViewModelStore` — two tabs, one Store, state bleeding between them. `BottomNavBackStack` enforces
distinctness when it is constructed, which covers a restored stack as well as a freshly built one,
and keeps `Counter` as the root when it drops a repeat. It also exposes `List`, never
`MutableList`, so a bare push is not expressible. Because a host could still ignore the type
altogether, an architecture test asserts that `CatsRadarNavHost` takes its stack from
`rememberBottomNavBackStack()` and that no other `:app` source builds or remembers a raw
`NavBackStack` (`NavBackStackUsageTest`).

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
- `ui/src/main/kotlin/dev/catsradar/ui/theme/CatsRadarTheme.kt`, `CatsRadarColors.kt`,
  `tools/make-palette.py`
- `ui/src/main/kotlin/dev/catsradar/ui/navigation/CatsRadarBottomBar.kt`,
  `ui/src/main/res/drawable/ic_nav_*.xml`

## Not handled yet

The theme is the foundation of a design pass that is not finished: coats are drawn as cat faces
(`coat.md`) and the Counter's count springs and rolls (`counting-cats.md`), but the rhythm of the
list, detail and statistics screens is still to come. Until then, those screens wear the new colours on
their old layouts. The root-stack back rule the bottom bar enforces is covered in `browsing-cats.md`.
