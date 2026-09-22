# Home-screen widget

A 1×1 widget reading **6 cats today**. Tapping it anywhere logs a cat — the phone does not have to be
unlocked into the app, and there is no button to aim at, because the whole widget is the button.

## What a tap does

The same two steps a tap in the app takes, in the same order: insert the encounter, then hand the
location to `AttachLocationWorker`. The row is written with `origin = WIDGET`, so a cat logged here
is distinguishable from one logged in the app, from the notification, or from a photo.

Glance kills an action callback that runs for more than a few seconds, which is why the location is
never waited for here — the same reason the app never waits for a fix before counting.

**No undo.** The undo window belongs to the Counter, where there is a chip to show and a screen to
show it on; a mis-tap on the widget is undone by opening the app.

## Staying in step with the app

Three things redraw it, because no single one covers every case:

- **its own collector**, while the widget is on screen and Glance is running a session for it;
- **`WidgetRefresh`** in the app process, for a cat logged or undone somewhere else — a widget nobody
  is looking at has no live collector, and the home screen keeps showing the last frame it was given;
- **a periodic refresh**, every half hour, for the case where no process is alive at all.

`WidgetRefresh` deliberately ignores the first value it sees: that is what the widget already drew,
and redrawing on every process start would be work with nothing behind it.

## Today, and whose today

The count is `ObserveTodayCount` — every encounter whose own day is today. An encounter's day is the
day it was on *where it happened*, from the offset stored with it, while "today" is the device's
current day. A cat logged at 01:00 three hours ahead of UTC belongs to that day there, not to the
day UTC is still on.

It is a separate use case from `ObserveStats`, which answers the same question but carries a
ten-second clock tick for elapsed times and rates. A bare count has no use for that, and the widget
would have paid for it on every process the app runs in.

## At the edges

- **Midnight is not an event.** Nothing wakes up to reset the count at 00:00, so a widget left
  untouched over midnight keeps yesterday's number until the periodic refresh, the next cat, or the
  next time the app runs. The number is never wrong about the data — only about the clock.
- **A row that does not change today's number costs no redraw.** Importing an old photo, or logging
  a cat that lands on another day, leaves the widget alone.
- **The receiver is exported**, unlike the walking-mode one: the launcher hosts the widget and
  `AppWidgetManager` is what sends it `APPWIDGET_UPDATE`.
- **The caption is a plural, and carries no number.** The count is drawn above it in its own text, so
  the phrase is "cats today" rather than "%d cats today" — which is what lets Russian decline the
  noun without the number being written twice.
- **A deleted cat stops counting immediately**, because the count reads the same filtered query
  everything else does.

## Where the code lives

- `app/…/widget/CatsRadarWidget.kt` — what it draws
- `app/…/widget/TallyAction.kt` — what a tap does
- `app/…/widget/WidgetRefresh.kt` — redrawing it when the app changes the count
- `app/…/widget/CatsRadarWidgetReceiver.kt`, `res/xml/cats_radar_widget_info.xml` — how the launcher
  finds it
- `domain/…/usecase/ObserveTodayCount.kt` — the number itself

## Not built yet

No resizing behaviour beyond what the launcher does on its own: the layout is one number and one
caption at every size. No coat choice from the widget — that grid needs a screen. The preview in the
widget picker is the app icon rather than a rendering of the widget.
