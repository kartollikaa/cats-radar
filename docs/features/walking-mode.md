# Walking mode

A cat seen on a walk should cost one tap. Walking mode puts an ongoing notification in the shade
with a **Cat!** button, so the phone comes out of the pocket, gets tapped, and goes back — no
unlock, no app launch, no hunting for the right screen.

Started from the **Counter** — a chip that reads *Start a walk*, then *On a walk* — because that is
the screen someone is on when they set out. The same switch is in **Settings → Walking mode** for
finding it again later.

## One flag, one owner

Both controls do exactly one thing: write a stored flag. Neither posts the notification.

The notification is kept equal to that flag, and to the outing it is counting, by a single collector
that runs for as long as the process does. Anything that changes either — a cat tallied in the app,
a walk stopped from the shade, the outing closing on its own — moves the notification without a
screen being involved.

A screen that posted it itself would leave the shade empty while the flag still read on: after a
reboot, after a force-stop, or after the notification was swiped away. The control would say *On a
walk* and the **Cat!** button would not exist.

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

## A Live Update on Android 16

The notification asks to be promoted, and where the platform agrees it becomes a **Live Update**: a
chip beside the clock carrying the count, and a place on the always-on display. Glancing at the
status bar then answers "how many so far" without unlocking anything.

Three things have to be true, and the slice needed all three — asking alone does nothing:

- `setRequestPromotedOngoing(true)` on the ongoing notification;
- `setShortCriticalText` — the count is what the chip shows, so it is a bare number rather than a
  sentence;
- `POST_PROMOTED_NOTIFICATIONS` in the manifest. It is `normal|appop`, so it is granted on install
  and the user can revoke it under **Live Updates**.

Channel importance is **not** one of them — the channel stays `IMPORTANCE_LOW`, and a walk still
never makes a sound.

Below Android 16 exactly the same notification posts, unpromoted: promotion is prominence, not
capability, which is why it could be a separate slice from the feature itself.

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
- **The pending intent is immutable.** Nothing may rewrite where a lock-screen tap ends up.
- **A revoked Live Updates permission is invisible to the app.** The notification posts as usual and
  is simply not promoted; there is nothing to report and nothing to retry.
- **The notification is `VISIBILITY_PUBLIC`** — its text shows on the lock screen, because tallying
  without unlocking is the whole feature. It says how many cats this outing, and nothing more.
- **A tally from here is `origin = NOTIFICATION`**, distinct from the app, the widget and the two
  photo paths. An older build reading such a row falls back to `APP` rather than failing, because the
  Room converter maps an unknown name that way.
- **It still asks for a location fix.** A cat logged from the lock screen enqueues the same
  `AttachLocationWorker` as one logged in the app.

## Where the code lives

- `app/…/notification/WalkingNotifier.kt` — the notification and its actions
- `app/…/notification/WalkingNotificationSync.kt` — holds it equal to the flag and the outing
- `app/…/notification/WalkingActionReceiver.kt` — the tally and the stop
- `app/…/permission/NotificationPermission.kt` — the permission-gated switch both screens use
- `domain/…/repository/SettingsRepository.kt` — `walkingMode`, so the switch survives a restart

## Not built yet

The chip's icon is the same placeholder the notification uses; it gets a real one in the design
pass. No automatic stop, so a mode left on stays on until it is turned off —
there is no rule yet for what "the walk ended" would mean that the gap-based outing does not already
answer. A reboot leaves the flag on but the shade empty until something starts the app again;
nothing listens for `BOOT_COMPLETED`.
