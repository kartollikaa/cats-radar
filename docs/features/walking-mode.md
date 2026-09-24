# Walking mode

A cat seen on a walk should cost one tap. Walking mode puts an ongoing notification in the shade and
on the lock screen with a **Cat!** button, so the phone comes out of the pocket, gets tapped, and
goes back — no unlock, no app launch, no hunting for the right screen.

Started from the **Counter** — a button with a small cat, centred under the count, that reads
*Start a walk*, then *Stop the walk* — because that is the screen someone is on when they set out.
The same switch is in **Settings → Walking mode** for finding it again later.

## How long the walk has lasted

While a walk is on, the Counter's button and the notification both say how long it has lasted, and
the cat on both of them walks.

- **It counts from the walk's start**, the moment the walk was turned on, not from the outing's
  first cat. The outing already has its own line on the Counter ("3 cats · 26 min"); the walk is
  the thing the button starts and stops.
- **On the button it is minutes**: the second line reads *32 min · press and hold* (*32 мин ·
  удерживайте*, shorter so it still fits at narrow widths), formatted like every other duration on
  the Counter. It moves within a few seconds of each minute, on the same tick as the outing line.
- **In the notification it is a chronometer** counting up from the start, `12:34` then `1:02:03`. The
  system ticks it, so the time moves with no repost and keeps moving while the app's process is
  dead. From API 37 the notification is a `MetricStyle` with two metrics, *Cats* and *Walk*, and
  the header leaves its own time out rather than show it twice.
- **The Live Update chip keeps the count.** A chip shows one thing, and the count is what the
  walk is for; the time is one glance further, in the card. `setShortCriticalText` outranks both
  the metrics and the chronometer as the chip's content, so nothing else can take its place there.
- **Until the walk exists there is no time.** The flag goes on first and the walk follows it a
  moment later; in between, the button shows the plain hint and the notification shows no time,
  rather than a clock started from a guess. A **Cat!** from the lock screen reads the walk's start
  again as it re-posts, so the time does not drop off the notification with the tap.
- **The cat walks only while a walk is on.** With no walk it stands on its first frame, legs
  straight down. The button's loop and the status-bar icon play the same eight frames at the same
  pace. The notification's icon moves only in the status bar: Android draws it static in the shade
  and on the always-on display.

## Stopping takes a hold

On the Counter a tap starts a walk but never stops one. A stop ends the walk and its recorded route,
and the button sits just under the count, where a thumb tallying cats can slip onto it, and so can
a phone going back into a pocket. So while a walk is on the button has to be held until a fill has
crossed it (`HoldToStop` in `WalkButton.kt`); the walk stops the moment it has, with a haptic, before
the finger lifts. Let go earlier, or drift off the button, and the fill drains back and nothing changes. The
button's second line says *press and hold* meanwhile, after the walk's time: a gesture nothing hints at
is one nobody finds.

Starting stays one tap, because a walk started by mistake loses nothing.

TalkBack's double tap stops the walk at once: the hold guards against touches nobody meant, and a
screen reader's double tap is always meant.

Only the Counter's button asks for the hold. The Settings switch and the notification's **Done**
still stop a walk in one action.

## One flag, one owner

Both controls do exactly one thing: write a stored flag. Neither posts the notification.

The notification is kept equal to that flag, and to the outing it is counting, by a single collector
that runs for as long as the process does. Anything that changes either — a cat tallied in the app,
a walk stopped from the shade, the outing closing on its own — moves the notification without a
screen being involved.

The walk itself follows the flag the same way. `FollowWalkingMode` opens a walk when the flag goes
on and ends it when the flag goes off, whichever control turned it off; a process that starts with
the flag on keeps the walk already open rather than opening a second.

A screen that posted it itself would leave the shade empty while the flag still read on: after a
reboot, or after a force-stop. The Counter would offer to stop a walk and the **Cat!** button would
not exist. (A swipe away is no longer one of those cases — it ends the walk outright, below.)

## It needs permission to post

Turning the mode on asks for `POST_NOTIFICATIONS` and, if refused, does not turn on. The
notification *is* the feature; a button offering to stop a walk over an empty shade would be a lie,
and a refusal that Android remembers is answered without a dialog, so the control simply will not
engage until notifications are allowed in system settings.

## It does not define an outing

Outings stay derived: `SessionSplitter` groups encounters by the gap between them exactly as before,
and walking mode changes none of that. It is a convenience surface over the same tally.

Making it authoritative was considered and rejected. "Am I on an outing" would then have two sources
of truth — a stored flag and the derived grouping — which disagree the moment someone forgets to turn
the mode off. It would also turn a derived concept into stored state, with a migration and a new way
for the statistics to be wrong.

## Recording the route

With precise location allowed, the walk's route is recorded. The notification is then carried by
`WalkRecordingService`, a foreground service of type `location`, which asks for fixes for as long as
it runs and offers each one to the walk; which of them the route keeps is in `data-model.md`.
Without location permission there is no service, and the notification is the plain ongoing one it
always was: it outlives the app leaving the screen on its own, and its buttons start the process
again if it has been killed. Approximate location alone counts as none here: its fixes are too rough
for any of them to join a route, so running a location service for it would record nothing.

- **It starts only while the app is on screen.** Android gives a service location access only when
  it starts in front of the user; one already running keeps it after the app leaves. So recording
  starts when the mode is turned on in the app, or the next time the app is in front with the mode
  on, which is also how a walk started before location was allowed begins recording once it is. A
  permission dialog that stays up for more than a moment counts as leaving the app, so allowing
  location from the prompt a tally raises usually starts the recording as the dialog closes, and
  otherwise with the next cat.
- **Stopping the walk stops the service**, from the app, the **Done** button or a swipe, and takes
  the notification away with it. The stop is sent to the service rather than done to it: a service
  stopped from outside before it has gone foreground takes the app down with it.
- **Done ends the walk there and then.** The walk otherwise follows the flag from inside the app's
  process, and a process woken just to handle Done may be gone before that catches up, which would
  end the walk only whenever the app next started.
- **A recording cut off ends the walk.** Killed along with the app, or by a reboot, the service is
  not restarted: from the background it would get no location. The next start of the app ends the
  walk at its route's last point, or at its start when it has none, rather than at that moment, and
  turns the mode off, which is what the empty shade already said. The service leaves a mark while it
  runs and clears it when it stops; a mark still there at the next start is how a recording cut off
  is told from one stopped.
- **A walk without location keeps the old behaviour** across process deaths: nothing records, so
  nothing is cut off, and it ends only when it is stopped.
- **Refused anyway** — the app left the screen between the request and the service starting — the
  notification goes up plain and the walk goes on unrecorded until the app is next in front.

The manifest asks for `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION`. The second only counts
once location is allowed, so for someone who has not allowed it nothing changes.

## A Live Update, from API 36.1

The notification asks to be promoted, and where the platform agrees it becomes a **Live Update**: a
chip beside the clock carrying the count, and a place on the always-on display. Glancing at the
status bar then answers "how many so far" without unlocking anything.

**API 36.1, not 36.** `setShortCriticalText` exists in Android 16.0, but
`setRequestPromotedOngoing`, the extra it writes, and the permission below all arrive in 16 QPR1.
A phone on plain 16.0 gets the same notification as one on 29 — posted, never promoted, nothing
thrown.

Two things are required for promotion to happen at all:

- `setRequestPromotedOngoing(true)` on the ongoing notification;
- `POST_PROMOTED_NOTIFICATIONS` in the manifest. It is `normal|appop`, so it is granted on install
  and the user can revoke it under **Live Updates**.

`setShortCriticalText` is not one of them — it decides what the chip *says*. Without it the
notification is promoted just the same and the chip carries only the icon, which is why the count
goes in as a bare number rather than a sentence.

Channel importance is not one of them either; it matters for the lock screen instead (below).

Promotion is prominence, not capability — which is why it could be a separate slice from the feature
itself.

The icon beside that number, and in the shade, is the walking cat: an `animation-list` of the same
frames the Counter's button plays, which the status bar runs as a loop. Android draws a
notification's icon from its alpha alone, so the cat is a plain silhouette. The frames come from
`tools/make-walking-cat.py`; `WalkingCatTest` fails if the button's frame list or pace stops
matching the animation-list.

### It does not come back after it is dismissed

Swiping the notification away ends the walk: its delete intent is the same **Done** action the
button uses, so the flag goes off, the Counter's button follows, and nothing reposts. Putting a Live
Update back after someone has just swiped it away is how an app gets its Live Updates permission
revoked — and it would also re-open the gap this feature exists to close, where the shade is empty
while the Counter still offers to stop a walk.

Demotion is a different gesture, and the platform offers no callback for it. A user who demotes the
chip but leaves the notification up will see the chip return on the next tally; whether the system
honours a renewed request after a demotion is its decision, not something the app can read.

## It shows on the lock screen

Android's lock screen leaves out *silent* notifications unless the user turns **Show silent
notifications** on (older versions offer the same choice as *Hide silent conversations and
notifications*), and a channel below `IMPORTANCE_DEFAULT` is what makes a notification silent. On a
Low channel the **Cat!** button existed only after an unlock.

So the channel is Default, with no sound and no vibration, and every post is still `setSilent`: the
notification sits among the alerting ones, which the lock screen keeps, and a walk never makes a
sound. A user who demotes the channel to Silent in system settings hides it again, by their choice.

Android lets an app lower a channel's importance but never raise it, so the raised channel has a new
id and the old `walking` channel, created Low, is deleted on start. Settings may count it among
deleted categories; that is the platform's bookkeeping, not a leftover. A choice the user made on
the old channel — silenced or blocked — does not carry over: the new one starts at Default.

## Tapping it opens the app

A tap on the notification itself, rather than on a button, opens the app the way its icon does: it
comes back on the screen it was on, even after its process was killed, and a fresh start opens the
Counter.
The intent is the launcher's own — same action, category and activity — because Android brings a
running task forward only for the intent that started it; a bare intent for the activity stacks a
second copy of the app on top. When Photo started the app, that second copy closes itself exactly as it
does for a tap on the icon ([widget.md](./widget.md#what-photo-does)). From a locked phone the
unlock comes first.

## Photo

The third button, between **Cat!** and **Done**, is the widget's Photo: it opens the app on the
Counter with the camera straight away, with the same `origin = CAMERA` and the same handling of an
app already running — see [widget.md](./widget.md#what-photo-does). Unlike **Cat!**, it launches an
activity, so from a locked phone Android asks for the unlock first and the camera opens after it.

## At the edges

- **The count is re-read, never remembered.** The process may have died between taps, and the outing
  is derived from the rows anyway, so each tap asks the statistics rather than keeping its own tally.
- **A walk started mid-outing shows the cats already logged**, not zero — it joins the outing in
  progress rather than pretending to begin one.
- **A process that starts with the mode off clears the notification.** That is how a stale one,
  left in the shade by a process that was killed, goes away.
- **Nothing observes the encounters while the mode is off**, which is nearly always: the statistics
  are only subscribed to for the length of a walk.
- **The receiver is not exported.** Only this app's own notification actions reach it; an `adb`
  broadcast from the shell is refused, which is the point of the flag.
- **Every pending intent is immutable.** Nothing may rewrite where a lock-screen tap ends up.
- **A revoked Live Updates permission costs the chip, not the notification.** The post still
  succeeds, unpromoted. The app could ask — `canPostPromotedNotifications()` answers it from API 36
  — but nothing is done with the answer: there is no degraded mode to fall back to and nothing
  useful to say about a setting the user just chose.
- **The notification is `VISIBILITY_PUBLIC`** — its text shows on the lock screen, because tallying
  without unlocking is the whole feature. It says how many cats this outing and how long the walk has
lasted, and nothing more. The
  visibility is set on the notification, not the channel: Android discards `VISIBILITY_PUBLIC` on a
  channel an app creates.
- **A tally from here is `origin = NOTIFICATION`**, distinct from the app, the widget and the two
  photo paths. An older build reading such a row falls back to `APP` rather than failing, because the
  Room converter maps an unknown name that way.
- **It still asks for a location fix.** A cat logged from the lock screen enqueues the same
  `AttachLocationWorker` as one logged in the app.

## Where the code lives

- `app/…/notification/WalkingNotifier.kt` — the notification, its channel and its actions
- `ui/…/res/drawable/ic_cat_walking.xml` and `cat_walk_*.xml` — its icon and the button's cat,
  generated by `tools/make-walking-cat.py`; `ui/…/counter/WalkingCat.kt` — the button's loop
- `app/…/photo/TakePhotoShortcut.kt` — the Photo intent, shared with the widget
- `app/…/notification/WalkingNotificationSync.kt` — holds it equal to the flag and the outing
- `app/…/notification/WalkRecordingService.kt` — carries it while the route is recorded
- `domain/…/usecase/FollowWalkingMode.kt` — holds the walk equal to the flag;
  `EndInterruptedWalk.kt` — settles a recording cut off; `RecordWalk.kt` — sends fixes to the route
- `data/…/platform/SharedPreferencesWalkRecordingState.kt` — the mark a running recording leaves
- `app/…/notification/WalkingActionReceiver.kt` — the tally and the stop
- `app/…/permission/NotificationPermission.kt` — the permission-gated switch both screens use
- `ui/…/counter/WalkButton.kt` — the Counter's button and its hold
- `domain/…/usecase/ObserveWalkElapsed.kt` — how long the open walk has lasted, for the button
- `domain/…/repository/SettingsRepository.kt` — `walkingMode`, so the switch survives a restart

## Not built yet

No automatic stop, so a mode left on stays on until it is turned off —
there is no rule yet for what "the walk ended" would mean that the gap-based outing does not already
answer. A reboot during a walk without location leaves the flag on but the shade empty until
something starts the app again; nothing listens for `BOOT_COMPLETED`.
