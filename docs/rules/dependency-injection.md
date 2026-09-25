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
- **`Lazy<T>`** — when Koin can build the class before the dependency can exist. `WorkManager` is
  initialized after Koin starts; building a Play Services client reaches Play Services;
  `FirebaseCrashlytics` throws in a process where `FirebaseApp` never started. The binding passes
  Koin's `inject()`, or `lazy { … }` for a one-off, and says in one line why. The class delegates a
  property to it, so its call sites read as if it held the instance:
  ```kotlin
  class WorkManagerBackupScheduler(workManager: Lazy<WorkManager>) : BackupScheduler {
      private val workManager by workManager
  }
  ```
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
- `KoinRuntimeResolutionTest` starts the real modules, without `WorkManager.initialize()` and without
  `FirebaseApp`, and resolves every type obtained by hand — so a binding that is missing, or a class
  that reaches for a dependency too early, fails a JVM test.

No test sees a hand-built instance of a plain class with its own binding (`ImportBatches(context)`
inside a scheduler); review catches that one.

## Adding a dependency

1. Add it to the class's primary constructor.
2. Bind it in the module for its layer; a platform object is obtained there, never in the class.
3. If Koin can build the class before the object exists, pass `Lazy<T>`.
4. If the class is resolved by hand (`koin.get()`, `by inject()`, `koinInject()`, `inject()`), add it
   to `KoinRuntimeResolutionTest`.
