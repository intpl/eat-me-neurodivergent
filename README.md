# Eat Me, Neurodivergent!

A timing-only meal reminder for Android. It never asks what you ate, only when.
Package `pl.gladecki.eatmen`, minSdk 26 (Android 8.0), targetSdk 35, Kotlin, Gradle,
plain Views, no Compose, no dependencies beyond AndroidX and Material Components.

This is v3.3.0, built from the project note in the vault
(`Projects/Eat-Me-Neurodivergent.md`); the change lists are in
`Projects/Eat-Me-Neurodivergent/Release-3.0.0.md`, `Release-3.1.0.md`,
`Release-3.2.0.md` and `Release-3.3.0.md`. v1 used a different brief and is archived at
`/home/b/dev/old/eat-me-neurodivergent-v1`.

## What it does

Two buttons on one screen.

- **I ate** asks when (now, 30m, 1h to 5h ago) and then which (meal or snack). The meal
  is logged, the next reminder becomes eaten-at + 4h for a meal or + 2h for a snack, and
  the front screen shows that time with a `change` link beside it.
- **I will eat** sets when the next reminder appears (30m, 1h to 4h, or tomorrow). It logs
  nothing and does not ask meal or snack.

One notification exists for the app's whole life. It counts down silently as a system
chronometer, turns into the alert when it is due, and goes back to counting down after a
repeat. The first alert of a run makes the bundled sound and vibrates; every repeat after
it appears without a sound, every ten minutes, until a meal is logged or tomorrow is set.
The alert carries **Ate** and **Not now**.

**Tomorrow** is a duration, not a clock time: twelve silent hours, then one silent
notification, then the first audible alert one meal gap later (sixteen hours after the
tap with the default four-hour meal gap).

Under the buttons, one strip draws the last 24 hours: meals as filled dots, snacks as
small hollow ones, the times printed above them, the night hours shaded, midnight a line
wherever it falls, and the right edge is now. It replaces the old Today list, so a day
that crossed midnight is one continuous picture instead of two halves. Nothing on it is
tappable and nothing is counted.

**The app does not open without three things.** Until the phone allows notifications,
exact reminders and do-not-disturb access, every screen is replaced by one gate: the
switches it is missing, each opening the phone's own page, and `Check again`. Nothing is
logged and nothing is planned until all three are on. Settings keeps a battery row and
**Send a test notification**: a real reminder-shaped notification, so the permission, the
channel and the sound can be checked in two seconds instead of a week.

A reminder speaks once, at the time it was set for. There is no earlier heads-up.

The front screen says `nothing logged` until the first event. There is no onboarding and
no nagging an empty screen.

## Build, test, verify

The toolchain lives outside the repo (JDK 17, Android SDK 35, Gradle 8.11.1) and
`env.sh` points at it:

    ./env.sh
    gradle :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
    gradle :app:assembleRelease        # signed, if the key below is in place
    gradle :core:test :app:testDebugUnitTest
    ./verify.sh                        # everything below, in one go

The release build signs with `/home/b/dev/.keys/eat-me-neurodivergent.properties` and the
keystore beside it, both outside the repo on the host mount. Without that file the release
build is unsigned and the debug APK is the one to install.

`./verify.sh` runs the unit tests, the Robolectric tests, the build, and the gates:

- the manifest carries exactly `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`,
  `SCHEDULE_EXACT_ALARM`, `ACCESS_NOTIFICATION_POLICY`, `VIBRATE` — and no `INTERNET`
- no `android.*` import anywhere in `core`
- no banned word in any user-visible string (see `tools/wording.py`)
- the three voice clips are inside the APK

### The screens without a phone

There is no emulator in the devbox (no KVM), so the screens are drawn by Robolectric with
native graphics and written out as PNGs:

    gradle :app:testDebugUnitTest --tests '*Screenshot*'
    ls app/build/shots

That covers light and dark mode, the front screen with meals, a day across midnight,
empty and stale, the "When?" sheet, edit history and settings. It is a render, not a
device: touch targets, fonts and the system's own chrome still need a phone.

## Install

    adb install -r app/build/outputs/apk/debug/app-debug.apk

Or copy the APK to the phone and open it. It is signed with the local debug key, so it
installs anywhere that allows unknown sources. Android 13+ asks once for notification
permission. On Android 12+ the system may also need "Alarms & reminders" allowed for exact
timing; without it the app falls back to inexact alarms and a reminder can arrive late.

## Data

One append-only file, `files/events.ndjson`, a JSON object per line. The whole app is a
replay of that file: meals, edits, deletions, reminders, alerts, settings, clock jumps.
Nothing is ever rewritten in place, and edits and deletions are new events that point at
an earlier one. The band adds no events and no fields: it is drawn from the meals and
settings the log already holds. `warning` is still a valid `alert_fired` kind so that 2.x
and 3.0 logs replay exactly as written; nothing writes it since 3.1.0.

**Copy JSON** in settings puts the entire log on the clipboard as one document
(`schema`, `exported_at`, `app_version`, `timezone`, `event_count`, `events`). It is the
only way data leaves the phone; the app has no network permission. **Erase everything**
deletes the file, so there is nothing left to replay and nothing left to export.

Times are stored in epoch milliseconds and displayed in the device's zone.

## The sound

Three clips in `res/raw`, generated in this devbox with espeak-ng 1.52 and trimmed with
ffmpeg: `eat_something` ("Eat something."), `food_oclock` ("Food o'clock.") and
`it_is_time` ("It is time. Eat."). They are generated, not licensed, so nothing about them
blocks a store listing later. The settings screen lists them by name; changing the choice
registers a new notification channel, because Android fixes a channel's sound when it is
created.

## Known limits

- **Do-not-disturb.** The app reads the interruption filter (needs the policy access the
  gate asks for) and posts on a silent channel while DND is on. Android does not
  let an app force its notifications through DND on its own: the phone also has to allow
  this app under *Do Not Disturb → Apps*. Without that, DND wins, which is the lesser lie.
- **Exact alarms.** Android 14 and newer do not grant `SCHEDULE_EXACT_ALARM` to a new
  install by default. With it granted the reminder is exact; without it the app uses
  `setAndAllowWhileIdle`, which can be minutes or more off in deep doze. The app refuses
  to open until the switch is flipped.
- **Battery.** An OEM that sleeps the app (Samsung's sleeping apps, Xiaomi's manager)
  cancels its alarms and can drop its ongoing notification. Settings offers the phone's
  battery page; the app cannot exempt itself without a permission this build refuses.
- **Never installed on hardware.** Everything green above is verification, not validation.
  The real test is a week of use.
- Without all three accesses the app does not open at all: the gate names what the phone
  withholds and opens the phone's page for it.

## Layout

    core/    pure JVM, no android.*: model, event log, reducer, planner, timeline, copy, export
    app/     Android: controller, notification centre, alarms, activities, sheets, the band
    tools/   the wording gate

`TRACEABILITY.md` maps every behavioural line in the vault note to the code that keeps
it and the test that proves it.
