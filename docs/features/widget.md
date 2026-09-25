# Home-screen widget

A widget reading **6 cats today**, with a **Photo** tile beside it. Tapping the count logs a cat
without opening the app, and there is nothing smaller to aim at, because the whole tile is the button.
TalkBack announces it as **Log a cat**. Tapping Photo — **Photograph a cat** to TalkBack — opens the
app straight into the camera.

It is placed two cells wide. Once the widget is tall enough for two tiles stacked — two cells on an
upright phone, three rows in landscape — Photo goes under the count, whatever its width. Otherwise
Photo sits beside the count once there is room for two tiles side by side: two cells upright, and
even one in landscape, where cells are wide and short. Smaller than both, the widget is the count
alone, since two targets in one upright cell are two targets too small.

The sizes that switch the layout sit between the platform's reference cell sizes rather than on
them, because launchers round cells differently: a single upright cell must never read as two.

Each tile is rounded, and less so on the side it shares with the other one, across the gap between
them, so the two read as one widget split in two. Side by side, right to left, the corners mirror
with the tiles. A press ripples inside the tile's own outline (`WidgetTileShapeTest`;
`WidgetContentTest` names each layout's tiles). The outlines are shape drawables tinted with the
widget's colours rather than Glance's corner radius, which rounds all four corners alike and does
nothing before Android 12 — so the tiles are rounded on Android 10 and 11 too.

## What a tap does

It inserts the encounter, then hands the location to `AttachLocationWorker`. The row is written with
`origin = WIDGET`, so a cat logged here is distinguishable from one logged in the app, from the
notification, or from a photo.

The tap runs inside a broadcast, and Android gives a broadcast only a short window — which is why the
location is handed to a worker and never waited for.

**No undo.** The undo window belongs to the Counter, where there is a chip to show and a screen to
show it on; a mis-tap on the widget is undone by opening the app.

## What Photo does

It opens the app on the **Counter** — whichever tab was showing, and whatever was open above it — and
the camera straight after. From there it is the Counter's own Photo button: the same capture, the
same `origin = CAMERA`, the same "Photo not saved" if the picture cannot be read. Cancelling the
camera leaves the app open on the Counter with nothing logged.

The request reaches an app that is already running rather than starting a second copy of it, so Back
from the Counter still leaves the app instead of stepping into an older one. Once Photo has reached
the app's task, though, the launcher's own intent no longer matches that task, and Android would
stack a second copy on the next tap of the app icon; that copy closes itself at once, leaving
whatever was in front — the screen the app was on, or a camera still open. Only a copy on the app's
own task closes: another app opening Cats Radar inside its own task gets it as usual.

Tapping Photo while a camera the app opened earlier is still up closes that camera, which counts as
cancelled, and opens a fresh one. Each camera's answer is matched to the file that camera was given,
so the cancelled one can never take the new photo with it.

## Staying in step with the app

Four things redraw it, because no single one covers every case:

- **the tap itself**, the one redraw that is sure to happen before the process can go away;
- **its own session**, which Glance opens on an update or a tap and closes again after a while — not
  whenever the widget happens to be on screen;
- **`WidgetRefresh`** in the app process, for a cat logged or undone anywhere else — the app, the
  walking notification, an import;
- **the launcher's periodic update**, for the case where no process is alive at all.

A tap on the widget is usually redrawn twice — by itself and by `WidgetRefresh` — which costs nothing
visible, since both draw the same number.

`WidgetRefresh` also redraws once when a process starts. The first number it reads is not
necessarily what the widget shows: the process may be starting after midnight, or because a
lock-screen tap has just written a row.

A session never shows a placeholder. The count is read before the session starts, because Glance
publishes a session's first frame before a flow has answered, and a "0" drawn there would flash on
the home screen.

## Colours

Like the app, the widget follows the wallpaper on Android 12 and later and keeps the app's teal
below it, light or dark with the system. On Android 12+ its colours are Glance's resource-backed
dynamic ones rather than the app's scheme read at render time: the launcher resolves them itself, so
a new wallpaper recolours the widget without the app having to redraw it.

## Today, and whose today

The count is `ObserveTodayCount` — every encounter whose own day is today. An encounter's day is the
day it was on *where it happened*, from the offset stored with it, while "today" is the device's
current day. A cat logged at 01:00 three hours ahead of UTC belongs to that day there, not to the
day UTC is still on.

It is a separate use case from `ObserveStats`, which answers the same question but carries a clock
tick for elapsed times and rates. A bare count has no use for that, and the widget would have paid
for it in every process the app runs in.

## At the edges

- **Midnight is not an event.** Nothing wakes up to reset the count at 00:00, so a widget left
  untouched over midnight keeps yesterday's number until the periodic update, the next cat, or the
  next time the app starts. The number is never wrong about the data — only about the clock.
- **The same number on a new day is still news.** Yesterday's 1 and this morning's first cat are
  both "1", and the second one still redraws the widget.
- **A row that does not change today's number costs no redraw.** Importing an old photo, or logging
  a cat that lands on another day, leaves the widget alone.
- **The time zone is read each time the count is worked out**, not once when the app starts, so
  flying across zones mid-trip does not leave "today" pinned to the old one.
- **A cat deleted or undone after it was counted comes off the count** on the next redraw, because
  the count reads the same filtered query everything else does.
- **Reopening the app from recents never opens the camera.** Once Photo has reached the app, its task
  holds Photo's intent, and Android hands it back when the app is reopened from recents; that launch
  is told apart and ignored, and so is the same intent after a rotation or a process restart. A
  request not yet carried out when the activity is recreated survives the recreation.
- **After a force stop the first tap is lost**, on either tile. Force-stopping cancels everything the
  widget had armed; on the Android version this was checked on, that tap only wakes the app and
  redraws the widget, and the next one works.
- **The receiver is exported**, unlike the walking-mode one: the launcher hosts the widget and
  `AppWidgetManager` is what sends it `APPWIDGET_UPDATE`.
- **The caption is a plural, and carries no number.** The count is drawn above it in its own text, so
  the phrase is "cats today" rather than "%d cats today" — which is what lets Russian decline the
  noun without the number being written twice.

## Where the code lives

- `app/…/widget/CatsRadarWidget.kt` — what it draws, at each size; `res/drawable/widget_tile*.xml` (and
  `drawable-ldrtl/`) — the tiles' outlines and their ripples, `res/values/dimens.xml` — their radii
- `app/…/widget/TallyAction.kt` — what a tap on the count does
- `app/…/photo/TakePhotoShortcut.kt`, `CameraRequest.kt` — how Photo reaches the Counter's camera
- `app/…/photo/PendingCaptures.kt` — which camera a result belongs to
- `app/…/widget/WidgetRefresh.kt` — redrawing it when the app changes the count
- `app/…/widget/CatsRadarWidgetReceiver.kt`, `res/xml/cats_radar_widget_info.xml` — how the launcher
  finds it
- `domain/…/usecase/ObserveTodayCount.kt` — the number itself

## Not built yet

No coat choice from the widget — that grid needs a screen. A photo from the widget cannot be taken
without the app opening, because the camera returns its picture to an activity. The preview in the
widget picker is the app icon rather than a rendering of the widget.
