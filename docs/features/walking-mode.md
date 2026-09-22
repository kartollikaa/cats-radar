# Walking mode

A cat seen on a walk should cost one tap. Walking mode puts an ongoing notification in the shade
with a **Cat!** button, so the phone comes out of the pocket, gets tapped, and goes back — no
unlock, no app launch, no hunting for the right screen.

Turned on and off from **Settings → Walking mode**. The switch follows the stored value, so it is
still on when the app is reopened mid-walk.

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

## Not a Live Update

Android 16's Live Updates — a promoted ongoing notification with a status-bar chip and a place on the
always-on display — need API 36, and `minSdk` here is 29. The tally works identically without them:
promotion is prominence, not capability. It is its own slice, so the feature works everywhere first
and gets promoted where the platform allows.

## At the edges

- **The count is re-read, never remembered.** The process may have died between taps, and the outing
  is derived from the rows anyway, so each tap asks the statistics rather than keeping its own tally.
- **The receiver is not exported.** Only this app's own notification actions reach it; an `adb`
  broadcast from the shell is refused, which is the point of the flag.
- **The pending intent is immutable.** Nothing may rewrite where a lock-screen tap ends up.
- **The notification is `VISIBILITY_PUBLIC`** — its text shows on the lock screen, because tallying
  without unlocking is the whole feature. It says how many cats this outing, and nothing more.
- **A tally from here is `origin = NOTIFICATION`**, distinct from the app, the widget and the two
  photo paths. An older build reading such a row falls back to `APP` rather than failing, because the
  Room converter maps an unknown name that way.
- **It still asks for a location fix.** A cat logged from the lock screen enqueues the same
  `AttachLocationWorker` as one logged in the app.

## Where the code lives

- `app/…/notification/WalkingNotifier.kt` — the notification and its actions
- `app/…/notification/WalkingActionReceiver.kt` — the tally and the stop
- `domain/…/repository/SettingsRepository.kt` — `walkingMode`, so the switch survives a restart

## Not built yet

No entry point on the Counter — Settings is the only way in, which is one screen too far for
something you turn on as you leave the house. No Live Update promotion. No automatic stop, so a mode
left on stays on until it is turned off.
