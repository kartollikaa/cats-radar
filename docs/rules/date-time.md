# Date & Time

Domain, data, and presentation are Kotlin Multiplatform modules, so **no `java.time`, `java.util.Date`,
`SimpleDateFormat`, or `Calendar` outside an `androidMain` source set.** The shared vocabulary is
`kotlin.time` plus `kotlinx-datetime`.

## Types — one per concept

| Concept | Type | Notes |
|---|---|---|
| A point in time | `kotlin.time.Instant` | Import the `kotlin.time` one; kotlinx-datetime's `Instant` is an alias for it. Stored in Room as epoch milliseconds (`Long`). |
| Now | `kotlin.time.Clock` | Injected. `Clock.System` appears only in DI wiring; tests pass a fixed clock. |
| A span | `kotlin.time.Duration` | Never a raw `Long` of minutes or a `Double` of hours. `30.minutes`, `duration.inWholeMinutes`. |
| A calendar day | `kotlinx.datetime.LocalDate` | What "today", streaks, and per-day counts are keyed by. |
| Wall-clock date and time | `kotlinx.datetime.LocalDateTime` | Only when both parts are needed for display. |
| An offset from UTC | `kotlinx.datetime.UtcOffset` | An encounter stores the device offset at capture time (`tzOffsetMinutes`). |
| A zone | `kotlinx.datetime.TimeZone` | `TimeZone.currentSystemDefault()` for "the device's today"; injected where tests need control. |

### Local date of an encounter

An encounter's day is the day it was on **where it happened**, not where the phone is now:

```kotlin
fun Encounter.localDate(): LocalDate =
  occurredAt.toLocalDateTime(UtcOffset(minutes = tzOffsetMinutes).asTimeZone()).date
```

"Today" is `clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date`. Both live in
`:domain`; nothing else re-derives them.

## Formatting for humans

Formatting is presentation work: it happens in a `*StateMapper` in `:presentation`, and the result
reaches the composable as a `String` in State. Never format in a composable, never in `:domain`.

- **Fixed, locale-independent patterns** (`HH:mm`, ISO dates in export files): the kotlinx-datetime
  `Format` builder, declared once as a top-level `val` next to its use.
  ```kotlin
  private val HourMinute = LocalTime.Format {
      hour()
      char(':')
      minute()
  }
  ```
  One call per line — detekt's `NoSemicolons` rejects the single-line form.
- **Locale-aware output** — month names, weekday names, "today"/"yesterday", relative time,
  durations like "1 h 20 min" — goes through the `DateTimeFormatter` interface in `:presentation`.
  Its Android implementation in `androidMain` formats dates and times with
  `java.time.format.DateTimeFormatter` at the device locale, and reads the handful of words that
  are not a date — "today", "yesterday", the hour and minute units — from its own Android string
  resources, which is why it takes a `Context`. iOS will use `NSDateFormatter`. Mappers take the
  interface as a constructor dependency.
  ```kotlin
  interface DateTimeFormatter {
    fun dayHeader(date: LocalDate, today: LocalDate): String
    fun time(instant: Instant, offset: UtcOffset): String
    fun duration(duration: Duration): String
  }
  ```
- **Rates** (`cats/h`, `cats/min`) are numbers with a unit, not dates — they still belong to the
  mapper, with the unit chosen there (see the spec's rate display rule).

## Don't

```kotlin
// ❌ platform API in shared code
val day = java.time.Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()

// ❌ formatting in a composable
Text(SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(state.occurredAt)))

// ❌ raw units
val durationMinutes: Long
```

## Testing

- Pass a fixed `Clock` (`object : Clock { override fun now() = Instant.parse("2026-09-21T10:00:00Z") }`)
  and an explicit `TimeZone`; never rely on the machine's zone.
- Cover a day boundary and a zone change in every calculation keyed by `LocalDate` (streaks,
  "today", sessions spanning midnight).
