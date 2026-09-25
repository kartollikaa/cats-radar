# Dependency Injection

Koin, owned by `:app`. Every class receives its collaborators through its primary constructor and
never builds or fetches them itself. The Koin modules in `app/src/main/kotlin/dev/catsradar/app/di/`
are the **composition root**: the one place that obtains platform services and SDK singletons and
decides which implementation each interface gets. `CatsRadarApplication`, which starts Koin, belongs
to it too. `:domain`, `:data`, `:presentation` and `:ui` expose plain constructors and never see Koin
(see [module-structure.md](./module-structure.md)).

A class that fetches its own collaborator hides a dependency its constructor does not declare. A test
then cannot substitute it, the Koin graph checks cannot see it, and the fetch runs whenever the class
happens to be built — which may be before the thing it fetches exists.

## Constructor injection

```kotlin
// ❌ the class fetches its collaborator
class VibratorHaptics(context: Context) : Haptics {
    private val vibrator = context.applicationContext.getSystemService(Vibrator::class.java)
}

// ✅ the class declares it; the binding fetches it
class VibratorHaptics(private val vibrator: Vibrator?) : Haptics

single<Haptics> { VibratorHaptics(androidContext().getSystemService(Vibrator::class.java)) }
```

**Obtaining a collaborator** — banned outside the composition root:

- a system service: `getSystemService(...)`, `NotificationManagerCompat.from(...)`;
- an SDK singleton: `WorkManager.getInstance(...)`, `FirebaseAnalytics.getInstance(...)`,
  `FirebaseCrashlytics.getInstance()`, any `*Manager.getInstance(...)`;
- a client or store built from a `Context`: `LocationServices.get…Client(...)`, `Geocoder(context)`,
  `getSharedPreferences(...)`;
- a class the Koin graph provides: a `*StateMapper`, a `*Store`, or any class with its own binding.
  A mapper that needs another mapper takes it as a parameter.

**`Context` is a dependency, not a locator.** Using its own members — resources and strings, files
and directories, permission checks, `contentResolver`, starting a service — is fine. Using it to get
another service object is the case above.

**Not collaborators**, so they stay where they are:

- values and state a class owns and nothing else shares: builders, `ContentValues`, a
  `MessageDigest` per hash, a coroutine scope that lives exactly as long as its owner, a helper that
  holds one Store's per-screen state (`ReportedRun`);
- constructor defaults that are plain values or a dispatcher (`sdkInt = Build.VERSION.SDK_INT`,
  `ioDispatcher = Dispatchers.IO`, `timeZone = TimeZone.currentSystemDefault()`): they are already
  parameters a test replaces.

## Shapes a dependency can take

- **The instance** — the default.
- **`Lazy<T>`** — only when creating the instance is itself costly or has side effects, and the
  class may be built long before it needs it: building the Play Services location client reaches
  Play Services, which only a location call should do. The binding passes Koin's `inject()` and
  says in one line why; the class delegates a property to it, so its call sites read as if it held
  the instance:
  ```kotlin
  class FusedLocationProvider(context: Context, client: Lazy<FusedLocationProviderClient>, …) {
      private val client by client
  }
  ```
  `Lazy` is not a way around start-up order. `WorkManager` and the Firebase instances exist before
  anything resolves their users — `CatsRadarApplication` initializes WorkManager right after
  `startKoin()`, and Firebase starts before `Application.onCreate()` — so they are injected as
  instances, and a test that builds the graph starts them in the same order.
- **A supplier `() -> T`** — when every use needs a fresh instance: a `Geocoder` keeps the locale it
  was built with, so `AndroidReverseGeocoder` builds one per lookup.

A platform object more than one class uses gets its own `single` (`WorkManager`,
`NotificationManagerCompat`); one used by a single class is obtained inline in that class's binding.
A `SharedPreferences` file is opened in the binding; its name is where its data lives, so it never
changes.

## Classes Android builds

Android instantiates Activities, Services, BroadcastReceivers, the Glance widget and its
`ActionCallback`s by name, so they cannot take constructor parameters. They take collaborators from
Koin — `by inject()` in the class, `koinInject()` in a destination composable in `:app` — and still
never build them. Workers are the exception to the exception: `KoinWorkerFactory` builds them, so a
worker takes constructor parameters like any other class.

## Enforcement

- `DependencyLookupTest` (Konsist, `app/src/test/kotlin/dev/catsradar/app/architecture/`) fails
  `check` when a production file outside the composition root calls one of the lookups above, or
  constructs a `*StateMapper` or `*Store`.
- `KoinModulesTest` (`verify()`) proves every constructor parameter has a binding. It reads the
  constructor of the class a definition names; a definition bound to an interface
  (`single<Haptics> { … }`) is not reflected.
- `KoinRuntimeResolutionTest` starts the real modules, with WorkManager running as it is in the app,
  and resolves every type obtained by hand, so a missing binding fails a JVM test. It runs without
  `FirebaseApp`, where `FirebaseCrashlytics` cannot be created, so the class that takes it is bound
  by its class (`single { CrashlyticsNonFatalReporter(get()) } bind NonFatalReporter::class`) and
  `verify()` covers it instead.

No test sees a hand-built instance of a plain class with its own binding (`ImportBatches(context)`
inside a scheduler); review catches that one.

## Adding a dependency

1. Add it to the class's primary constructor.
2. Bind it in the module for its layer; a platform object is obtained there, never in the class.
3. Pass `Lazy<T>` only for an instance that is costly to create and may never be needed.
4. If the class is resolved by hand (`koin.get()`, `by inject()`, `koinInject()`, `inject()`), add it
   to `KoinRuntimeResolutionTest`.
