# App shell

`:app` hosts a single-activity Compose UI. `MainActivity` wraps one `CatsRadarNavHost` in
`CatsRadarTheme` inside a full-screen `Surface`, and turns the widget's Photo launch into a camera
request that sends the nav host back to the Counter (see [widget.md](./widget.md)). A launcher start
that Android stacks on the app's own task as a second copy finishes at once. The nav host owns a
Navigation 3 `NavDisplay` over a back stack rooted at `Counter`, with the selected bottom-navigation
tab above it and any detail or sheet above that. The tab-to-destination mapping is exhaustive over
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

**Colour.** The app follows Material You: on Android 12 and later it draws with the wallpaper's
colours (`dynamicLightColorScheme` / `dynamicDarkColorScheme`). There is no in-app switch.
`deviceColorScheme` in `:app` makes that choice; `:ui` never sees the platform, and `CatsRadarTheme`
called without a scheme — every preview — draws the app's own palette, so previews stay
deterministic.

The app's own palette is what Android 10 and 11 get. Light and dark schemes set every colour role in
`CatsRadarColors.kt`, on Material 3's tones, seeded from the launcher icon's teal (`#4CAF93`) with a
coral tertiary. The values come from `tools/make-palette.py`, which prints both schemes as Kotlin and
refuses to print one whose text would fall under WCAG AA; changing the palette means changing the
recipe and re-running it, not hand-editing one role. The launcher icon keeps its teal everywhere;
with themed icons on, the launcher tints its monochrome layer instead.

`DeviceColorSchemeTest` pins the choice: on Android 12+ both schemes take their primary from the
system's wallpaper palette, below it they are exactly the teal ones.

`CatsRadarColorsTest` holds the palette to four things, in both themes: every text colour reads at
WCAG AA against the surface it is meant for; no role is left at Material's default, found by
reflection so a role added in a later Material version is caught too; the primary is still a
saturated teal; and it is what the theme draws with when no scheme is given. Each check has been
broken on purpose and caught.

The **window background** is the surface the app draws with, in both modes, because the window is
painted before Compose draws its first frame. From Android 14 it is the system's own surface colour,
the one the wallpaper scheme reads, so it matches exactly; below Android 12 it is the teal surface.
On Android 12 and 13 Material computes the wallpaper surface's tone at runtime and no resource holds
it, so the window takes the nearest tone the system publishes: in dark mode a shade lighter than the
app, for the moment before the first frame. `WindowBackgroundTest` fails if the window and the app
drift apart on Android 14 or on Android 11.

**Launcher icon.** A ginger-and-white cat on a dark teal radar. The cat is the coat picker's face,
with the same paths and the ginger-and-white coat's colours. It has no outline, because the dark
background already gives it an edge. The rings and the sweep are the palette's seed teal, with a
blip just behind the sweep line. The cat is the adaptive icon's foreground and the radar its
background, so the launcher's parallax moves them apart. The themed (monochrome) layer is the head's
silhouette with the eyes and nose cut out, plus the blip. A vector drawable cannot read a Kotlin
constant, so the foreground and monochrome drawables carry their own copies of the face's paths.
The walking notification's icon ([walking-mode.md](./walking-mode.md#a-live-update-from-api-361)) is
another copy. `CatIconTest` fails if any copy stops matching `CatFacePaths`, so a change to the face
has to be copied into every one of them. The two inner rings stay inside the safe zone, so a
launcher shape with inward curves never cuts them. The third ring lies beyond the circle and shows
only in the corners of squarer shapes.

**Shape and type.** Corners are rounder than Material's defaults at every size, and display and
headline styles are heavier. The font is the platform's; nothing is bundled.

**Rhythm.** Screens that hold rows — Statistics, Settings, an encounter's detail, each level of
Places — group them in titled cards (`SectionCard`) on the theme's low surface, with the title in
the primary colour; a headline number sits in a primary-container card of its own, and a Places
level opens on one that names the place. Cat lists — the Encounters tab, the map's spot sheet, the
cats at the bottom of Places — are the one place rows are cards individually, since each outing is
one run of them. A value that does not fit beside
its label moves under it, end-aligned, rather than squeezing the label; a card's title is a heading
and each row reads as one item to TalkBack. A setting's whole row toggles it, not only its switch.

**Navigation.** The bottom bar is `ShortNavigationBar`, five tabs — Counter, Encounters, Map, Stats,
Settings — with Material Symbols Rounded icons (Apache 2.0). The selected tab is marked by the bar's
indicator pill; only the map and the settings gear also change to their filled form, because the
other glyphs have no separate filled version. The label is always shown and names the tab, so the
icons carry no content description of their own. Each label gets a fifth of the bar, which on a
360dp-wide phone is narrower than «Статистика», so the Russian stats tab says «Итоги».

**Motion.** Screens change the way Material's transition patterns describe, and every change is
short: `NavTransitionTimingTest` drives the host's own `NavDisplay` on the test clock and fails if a
tab switch, a step forward or a step back runs past the motion's duration and the frame that ends
it. Moving between tabs *fades through*: the old tab fades out before the new one fades in and
settles from slightly smaller, so two layouts never show on top of each other. Opening a detail — an
encounter, a level of the places drill-down — moves along the *horizontal axis*: the new screen
slides in from the right as the old one slides away to the left, and going back reverses it. Only a
one-level step moves along the axis. Leaving a detail for another tab, or tapping a tab from two
levels down its stack, fades through like any tab switch, although the stack underneath only pushed
or popped. The motion is decided from the two screens alone: each tab's entry carries a tab-root
marker in its Navigation 3 metadata, and a detail is recognised by what sits directly under it, a
sheet in between not counting as a level (`NavMotionTest`). The test builds the nav host's own
entry for every `BottomNavTab`, so a tab added without the marker fails rather than silently sliding
like a detail.

**The back gesture** follows the finger. The current screen shrinks toward the side the finger is
moving to and fades as it goes; the screen it returns to starts fading in once the current one is
mostly gone, so the two barely overlap. A back that starts from no edge — the Back button's own
predictive back — shrinks the screen toward its centre. Releasing plays the rest; dragging back to
the edge cancels and restores the screen. The app sets all three specs because Navigation 3's
defaults are a long cross-fade for every change and a back gesture that shrinks the screen without
fading it, leaving it fully opaque over the one coming in until it vanishes at the end.

**Sheets are destinations.** A bottom sheet is an entry on the back stack whose metadata carries
`BottomSheetSceneStrategy.bottomSheet()`; the strategy draws it in a `CatsRadarBottomSheet` over the
entries under it, and dismissing the sheet pops it. A screen opened from a sheet goes on top of
it, and the sheet stays on the stack under that screen. Navigation 3 would take the sheet as the
screen a back gesture returns to, and the sheet's window then opens over the screen being dragged
away and takes the gesture from it, so no navigation happens. While a screen covers it, the
strategy draws a sheet as the scene under it instead: the gesture uncovers that scene, and the sheet
slides back up once the gesture lands (`BottomSheetNavigationTest`).

**A sheet opens all the way.** Every sheet — these, and the two a screen opens itself, the coat
choice over the map and the coat question after a photo — is a `CatsRadarBottomSheet`, which has no
half-open stop (`BottomSheetNavigationTest`). A sheet taller than half the screen opens at its full
height rather than halfway (*a sheet taller than half the screen opens at its full height*), a
shorter one at its own height (*a sheet shorter than half the screen opens at its own height*), and a
drag part of the way down closes it instead of parking it half open (*dragging a sheet part of
the way down from its full height closes it rather than stopping half open*). Material's own
sheets — `ModalBottomSheet`, `BottomSheetScaffold` and their states — stop a tall sheet halfway by
default, so no other file uses them (`BottomSheetUsageTest`).

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
otherwise Koin-agnostic. Every class receives its collaborators through its constructor — a
`Vibrator`, a `SharedPreferences` file, `WorkManager`, the Firebase instances, the Play Services
location client, another mapper — and only the Koin modules and `CatsRadarApplication` obtain them
([docs/rules/dependency-injection.md](../rules/dependency-injection.md), guarded by
`DependencyLookupTest`). Two different tests check the DI graph against two different failure
shapes: `KoinModulesTest` walks constructor parameters statically (`module.verify()`) to prove the
graph is *declarable*, but that check is blind to anything resolved by hand inside a lambda
binding or a composable (`androidContext()`, `koinInject<Haptics>()`) — nothing reflects a
constructor for those. `KoinRuntimeResolutionTest` closes that gap by actually starting Koin and
resolving exactly those hand-resolved types, so a deleted binding fails a JVM test instead of
surfacing on the user's first tap. It has WorkManager running before it resolves anything, as the
app does: every scheduler takes the `WorkManager` instance, which exists from
`WorkManager.initialize()` on, right after `startKoin()`. It also reads the three preference files
back through the real bindings, so renaming one — which would lose what installed versions stored
there — fails a test. Only the Play Services client reaches `FusedLocationProvider` as `Lazy<T>`,
because building it reaches Play Services, which only a real location call should do.

## Where the code lives

- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/Store.kt`
- `app/src/main/kotlin/dev/catsradar/app/CatsRadarApplication.kt`, `MainActivity.kt`
- `app/src/main/kotlin/dev/catsradar/app/navigation/CatsRadarNavHost.kt`, `NavMotion.kt`,
  `BottomSheetSceneStrategy.kt`, `Counter.kt`, `CounterEffectHandler.kt`
- `app/src/main/kotlin/dev/catsradar/app/di/DomainModule.kt`, `DataModule.kt`,
  `PresentationModule.kt`, `WorkerModule.kt`
- `ui/src/main/kotlin/dev/catsradar/ui/theme/CatsRadarTheme.kt`, `CatsRadarColors.kt`,
  `tools/make-palette.py`
- `ui/src/main/kotlin/dev/catsradar/ui/components/CatsRadarBottomSheet.kt`
- `ui/src/main/kotlin/dev/catsradar/ui/navigation/CatsRadarBottomBar.kt`,
  `ui/src/main/res/drawable/ic_nav_*.xml`

## Not handled yet

The theme is the foundation of a design pass that is not finished: coats are drawn as cat faces
(`coat.md`) and the Counter's count springs and rolls (`counting-cats.md`), but the rhythm of the
list, detail and statistics screens is still to come. Until then, those screens wear the new colours on
their old layouts. The root-stack back rule the bottom bar enforces is covered in `browsing-cats.md`.
