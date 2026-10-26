# Smart-Senses

An Android app that watches two everyday habits while you're busy in other apps: **loud headphone listening** and a **bright screen in a dark room**. When one lasts too long, a small terminal-style card drops down from the status bar over whatever you're doing. Every warning is recorded, and your own listening history turns into patterns and trends.

Built for the student or developer who codes or studies for hours with music in their headphones and doesn't notice the volume creeping up.

> Smart-Senses is a nudge, not a medical device. Android's media-volume setting only **estimates** listening risk. It doesn't measure the sound that reaches your ears, and the thresholds below are product rules, not clinical limits.

## What it does

- **EARS: headphone volume.** With headphones connected (wired, USB or Bluetooth) and music actually playing:

  | Rule | Default | Card |
  | --- | --- | --- |
  | Volume high | ≥ 80% for 15 continuous minutes | Amber |
  | Very high volume | ≥ 95% for 5 continuous minutes | Red, replacing amber for that stretch |
  | Long day | 60 minutes at or above 80% in a day | Amber, once a day |

  Paused music doesn't count, and lowering the volume resets the continuous timer. The phone's built-in speaker never counts.
- **EYES: bright screen in a dim room.** Screen brightness ≥ 70% while the light sensor reads under 20 lux, for 10 minutes, gives an amber card. Between 22:00 and 06:00 the wording mentions the late hour.
- **The warning card** drops in with a small bounce, stays about 5 seconds, and can be swiped up to dismiss. Tap it to open **Insight**: why it fired (rule, reading, duration, time, output) and **Snooze 1 hour**. Cards don't nag:
  - a 10-minute cooldown per rule;
  - at most 3 cards a day across EARS and EYES;
  - no card while the screen is off or locked.
- **Dashboard:** live Ears and Eyes status, today's patterns, and a System Log of meaningful events (monitoring started, warnings, volume back under the threshold).
- **History:** every listening session and warning, with time, signal, reading, duration/context and severity.
- **Trends:** Ears volume over time with the 80% line marked, plus minutes above it per day, for a day or a week. With too little data it says "Not enough data yet" instead of showing sample data.
- **Patterns:** today's high-volume stretches and minutes, plus an upward trend once there are 4 listening days (the 2 latest days average at least 5 points above the 2 before).
- **Settings:** the thresholds and timers, Eyes on/off, monitoring on/off, and **Demo mode**.
- **Keeps working:** a foreground service checks every second and saves a reading every 10 seconds. It resumes on its own after a phone restart or an app update.

## Privacy

Everything stays on the phone. The app has **no `INTERNET` permission**, no account and no analytics, and `allowBackup` is off. Readings and warnings are stored in a local Room database. Settings are kept in DataStore.

## Build and install

You need Android Studio (or the Android SDK with platform 36) and a phone running Android 8.0 (API 26) or newer. It was tested on a Samsung Galaxy A03 running Android 13.

```bash
./gradlew installDebug          # build and install on the connected phone
./gradlew testDebugUnitTest     # run the rule tests
```

Or open the folder in Android Studio and press **Run**.

## First launch and permissions

Setup explains each permission before Android asks for it:

1. **Notifications** (Android 13+): shows the ongoing "monitoring" notification. If you deny it, monitoring still runs.
2. **Appear on top** (a special-access setting; the app opens the page for you): lets the card show over other apps. Without it, warnings are still recorded, and the dashboard shows `WARNING CARD: UNAVAILABLE` with a **FIX** button.
3. **Samsung phones:** set Settings → Apps → Smart-Senses → Battery → **Unrestricted**, and keep the app out of Sleeping apps, so One UI doesn't stop the monitor. If monitoring does stop, the top bar shows `MONITORING: STOPPED`, and opening the app starts it again.

## Try it in about a minute (Demo mode)

Demo mode shortens the 15-minute volume timer and the 10-minute Eyes timer to **1 minute**. The thresholds, the red rule (5 minutes) and the daily total stay the same, and nothing is faked: the card still needs real headphones, real playback and a real light reading. Turning Demo mode on also resets cooldowns, the daily card count and any snooze, so earlier tests can't block the next card.

- **EARS:** plug in headphones and play music. Raise the media volume to 80% or more (step 12 of 15 on the A03; accept Samsung's safe-volume prompt and turn off the Media volume limiter). Switch to any other app. About a minute later, the amber card drops down.
- **EYES:** in a dark room, set brightness to 70% or more and keep the screen on and unlocked. About a minute later, the Eyes card appears.

Then open Smart-Senses to see the warning in History, the Insight page, Patterns, Trends and the System Log.

## How it's built

Kotlin, Jetpack Compose and Material 3, with a single activity. The pieces:

| Piece | Where | Role |
| --- | --- | --- |
| Rules | `rules/` | Plain Kotlin with no Android imports (`EarsRuleEngine`, `EyesRuleEngine`, `Patterns`), so the rules can be unit-tested and reused later |
| Monitoring service | `monitor/MonitoringService.kt` | A foreground service (`specialUse`) running a 1-second loop: read the sensors, run the rules, show cards, save readings and events |
| Readers | `monitor/AudioReader.kt`, `monitor/EyesReader.kt` | Headphone output, volume and playback from `AudioManager`; brightness setting and light sensor |
| Warning card | `overlay/` | A Compose card in a `TYPE_APPLICATION_OVERLAY` window, with spring-drop and swipe-to-dismiss |
| Resume | `monitor/BootReceiver.kt` | Restarts monitoring after `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` if it was on |
| Data | `data/` | Room (`readings`, `listening_sessions`, `events`) and DataStore settings |
| Screens | `ui/` | Onboarding, Dashboard, Trends (a hand-drawn Canvas chart), History, Insight, Settings |

There are 35 unit tests for the rules in `app/src/test/`: 17 Ears, 12 Eyes and 6 Patterns.

## Known limitations

- **Volume % is a proxy:** the same setting is louder on some headphones than others. Android also can't tell Bluetooth headphones from a Bluetooth speaker, so both count.
- **Android only:** iOS doesn't allow cards over other apps.
- **Battery managers can stop background work:** Samsung's sleeping apps, for example. The app shows `STOPPED` honestly rather than pretending.
- **Not counted while Android pauses the loop:** if Android pauses monitoring (Doze), the missed time isn't counted. It may under-count, but never over-counts.
- **Not built yet:** Eyes charts and multi-day Eyes patterns, device temperature (the Device card is a placeholder), and clearing history.

## How it was planned and built

This app was built from an empty folder for Devpost's **Build With AI: Basics** hackathon, using the [Devpost Learn skill pack](https://github.com/challengepost/learn-ai-basics) with GitHub Copilot CLI. Planning came before code, and every decision is in the repo:

- [`devpost/scope.md`](devpost/scope.md): the idea, who it's for, and the proof-of-concept boundary.
- [`devpost/prd.md`](devpost/prd.md): behaviors, warning rules, states and edge cases.
- [`devpost/spec.md`](devpost/spec.md): stack, components, data model and failure modes.
- [`devpost/checklist.md`](devpost/checklist.md): the build in verified, committed slices, plus every change made along the way.
