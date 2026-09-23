# Walking mode

A cat seen on a walk should cost one tap. Walking mode puts an ongoing notification in the shade and
on the lock screen with a **Cat!** button, so the phone comes out of the pocket, gets tapped, and
goes back — no unlock, no app launch, no hunting for the right screen.

Started from the **Counter** — a chip with a walking figure, centred under the count, that reads
*Start a walk*, then *On a walk* — because that is
the screen someone is on when they set out. The same switch is in **Settings → Walking mode** for
finding it again later.

## One flag, one owner

Both controls do exactly one thing: write a stored flag. Neither posts the notification.

The notification is kept equal to that flag, and to the outing it is counting, by a single collector
that runs for as long as the process does. Anything that changes either — a cat tallied in the app,
a walk stopped from the shade, the outing closing on its own — moves the notification without a
screen being involved.

A screen that posted it itself would leave the shade empty while the flag still read on: after a
reboot, or after a force-stop. The control would say *On a walk* and the **Cat!** button would not
exist. (A swipe away is no longer one of those cases — it ends the walk outright, below.)

## It needs permission to post

Turning the mode on asks for `POST_NOTIFICATIONS` and, if refused, does not turn on. The
notification *is* the feature; a control reading *On a walk* over an empty shade would be a lie, and
a refusal that Android remembers is answered without a dialog, so the control simply will not
engage until notifications are allowed in system settings.

## It does not define an outing

Outings stay derived: `SessionSplitter` groups encounters by the gap between them exactly as before,
and walking mode changes none of that. It is a convenience surface over the same tally.

Making it authoritative was considered and rejected. "Am I on an outing" would then have two sources
of truth — a stored flag and the derived grouping — which disagree the moment someone forgets to turn
the mode off. It would also turn a derived concept into stored state, with a migration and a new way
for the statistics to be wrong.

## Not a foreground service

An ongoing notification survives the app leaving the foreground on its own, and the action's
broadcast starts the process again if it has been killed. A foreground service would add
`FOREGROUND_SERVICE`, a service type, and on newer Android a written justification for it — and buy
nothing the notification does not already do for a walk.

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

The icon beside that number, and in the shade, is the cat's face: the coat picker's head with the
eyes and nose cut out, the same silhouette as the themed launcher icon. Android draws a
notification's icon from its alpha alone, so the face's colours cannot carry over; the cut-outs are
what keep it a face rather than a blob with ears. How its paths stay equal to the face's is in
[app-shell.md](./app-shell.md#look).

### It does not come back after it is dismissed

Swiping the notification away ends the walk: its delete intent is the same **Done** action the
button uses, so the flag goes off, the Counter chip follows, and nothing reposts. Putting a Live
Update back after someone has just swiped it away is how an app gets its Live Updates permission
revoked — and it would also re-open the gap this feature exists to close, where the shade is empty
while the chip still reads *On a walk*.

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
  without unlocking is the whole feature. It says how many cats this outing, and nothing more. The
  visibility is set on the notification, not the channel: Android discards `VISIBILITY_PUBLIC` on a
  channel an app creates.
- **A tally from here is `origin = NOTIFICATION`**, distinct from the app, the widget and the two
  photo paths. An older build reading such a row falls back to `APP` rather than failing, because the
  Room converter maps an unknown name that way.
- **It still asks for a location fix.** A cat logged from the lock screen enqueues the same
  `AttachLocationWorker` as one logged in the app.

## Where the code lives

- `app/…/notification/WalkingNotifier.kt` — the notification, its channel and its actions
- `app/…/res/drawable/ic_notification_cat.xml` — its icon, the face's silhouette
- `app/…/photo/TakePhotoShortcut.kt` — the Photo intent, shared with the widget
- `app/…/notification/WalkingNotificationSync.kt` — holds it equal to the flag and the outing
- `app/…/notification/WalkingActionReceiver.kt` — the tally and the stop
- `app/…/permission/NotificationPermission.kt` — the permission-gated switch both screens use
- `domain/…/repository/SettingsRepository.kt` — `walkingMode`, so the switch survives a restart

## Not built yet

No automatic stop, so a mode left on stays on until it is turned off —
there is no rule yet for what "the walk ended" would mean that the gap-based outing does not already
answer. A reboot leaves the flag on but the shade empty until something starts the app again;
nothing listens for `BOOT_COMPLETED`.
