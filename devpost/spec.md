---
doc: spec
status: approved
---

# Smart-Senses — Technical Spec

Implements the approved `prd.md` inside `scope.md > The POC Boundary`: Ears only, end to end, on a real phone.

## How This Works, In Plain Language

Smart-Senses is one Android app made of three working parts that share one small database on the phone.

1. **The monitor.** This is a *foreground service*: background work that Android keeps alive as long as it shows an ongoing notification. Once a second, it asks the phone three questions:
   - Are headphones the output?
   - How loud is the media volume?
   - Is audio actually playing?

   It hands the answers to the rules.
2. **The rules.** This is plain Kotlin with no Android code. It keeps the timers (loud right now, very loud right now, loud today) and applies the cooldown, the daily cap, and snooze. It decides when a warning is due. Because it never touches Android, it can be unit-tested on the laptop and later moved into a Kotlin Multiplatform shared module.
3. **The warning card.** When the rules say a warning is due, the monitor draws a small card in an *Appear on top* window. That window floats over whatever app is open for about 5 seconds.

Everything the monitor sees is written to **Room**, Google's layer over the phone's built-in SQLite database. Think of it as a spreadsheet the app reads and writes. It saves:
- a reading every 10 seconds while you listen;
- one row per listening session;
- one row per event, meaning warnings and system-log lines.

**DataStore**, a small key-value file, keeps the settings. The screens (Dashboard, Trends, History, Settings, Insight) only *read*. They use the monitor's live status and Room's rows, and they update automatically when a new row arrives.

The app uses three kinds of Android permission:
- **Granted at install:** running the foreground service, and starting again after a phone restart.
- **Asked at runtime:** notifications, on Android 13 and later.
- **Special access:** *Appear on top*. Only the user can switch it on, in system Settings. The app checks again when the user comes back.

**Why this shape:** one app, one process, no server, no account. The only background piece is the one the product needs, the monitor. It's the smallest system that can prove "it noticed while I was doing something else."

```
┌──────────────────────────── the phone ────────────────────────────┐
│ AudioManager ──every 1 s──▶ MonitoringService ──▶ EarsRuleEngine   │
│ (headphones, volume,        (foreground service,  (plain Kotlin:   │
│  playing?)                   ongoing notification) timers + rules) │
│                                  │        ▲               │        │
│                every 10 s / on   │        └─ warning due ─┘        │
│                each event        ▼                 │               │
│                  Room: readings, sessions, events  ▼               │
│                                  │        WarningCardOverlay       │
│                                  │        (Appear on top window)   │
│                                  ▼                 │ tap            │
│  DataStore (settings) ◀──▶ Screens: Dashboard · Trends · History · │
│                            Settings · Insight  ◀───┘               │
└────────────────────────────────────────────────────────────────────┘
```

## The Core Journey Through the System
PRD ref: `prd.md > The Core Journey`.

1. **First open:** `MainActivity` reads DataStore and finds `onboardingDone = false`, so it shows `OnboardingFlow` → Welcome → **Get started**.
2. **Choose what to monitor:** the user switches Ears on (`monitoringEnabled`). Eyes and Device cards are display-only: "coming later."
3. **Permissions:**
   - **Notifications:** on Android 13+, the explainer's button asks with the system prompt (`POST_NOTIFICATIONS`).
   - **Appear on top:** **Continue to settings** opens `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` for Smart-Senses. When the user comes back, the screen re-checks `Settings.canDrawOverlays()` and shows `GRANTED` or `NOT GRANTED`.
   - **Skip for now** always moves on.
4. **Confirm setup:** the checklist reads the permission status and settings. **Finish** saves `onboardingDone = true` and starts `MonitoringService`.
   - The service immediately posts its ongoing notification, becomes a foreground service, and logs `monitoring started` to Room.
   - The dashboard shows `LiveStatus` (headphones, volume %, playing) within a second.
5. **The user leaves the app** and plays music through headphones at ≥ 80%. Every second:
   - `AudioReader` reports headphones, volume, and whether audio is playing.
   - `EarsRuleEngine` adds the elapsed time to the continuous timer and today's total.
   - Every 10 seconds, the service writes a `readings` row and updates the open `listening_sessions` row.
6. **After 15 minutes (1 in Demo mode)**, the engine returns a warning. It has already checked the cooldown, the daily cap, snooze, and that the screen is on and unlocked.
   - It inserts a `WARNING` row into `events`.
   - `WarningCardOverlay` slides the amber card down over the music app.
   - About 5 seconds later, the card slides away and its window is removed.
7. **What the user can do:**
   - **Lower the volume:** on the next tick the engine resets the continuous timer, keeps today's total, and the service logs `volume back under threshold`.
   - **Swipe the card up:** it's removed at once.
   - **Tap the card:** `MainActivity` opens with the warning's id and shows `InsightScreen`.
8. **Recorded:** the `events` row and the session's highest severity are already in Room. History, the System Log, Patterns, and Trends read Room through live queries, so they're up to date.
9. **Back in Smart-Senses**, they see:
   - the warning in History;
   - why it fired, in Insight;
   - today's loud stretches and minutes, in Patterns;
   - the volume line against the threshold, in Trends.

## Stack

| Piece | Choice (version) | Why | Docs |
| --- | --- | --- | --- |
| Language | Kotlin **2.4.0** via AGP's built-in Kotlin | Learner's choice: Kotlin + Compose, Android-only for this build | https://developer.android.com/build/migrate-to-built-in-kotlin |
| UI | Jetpack Compose, **BOM 2026.06.01** (UI 1.11.4, Material 3 1.4.0) | Learner's choice. Newer BOMs (2026.08+) need SDK 37, which isn't installed; this one builds with SDK 36. | https://developer.android.com/develop/ui/compose/bom/bom-mapping · https://developer.android.com/develop/ui/compose/designsystems/material3 |
| Compose compiler | `org.jetbrains.kotlin.plugin.compose` **2.4.0** | Must match the Kotlin version | https://developer.android.com/develop/ui/compose/compiler |
| Build | Android Gradle Plugin **9.1.0** + Gradle **9.3.1** | Both already cached on this machine; 9.3.1 is AGP 9.1's minimum | https://developer.android.com/build/releases/gradle-plugin |
| SDK levels | compileSdk **36**, targetSdk **36**, minSdk **26** | 36 is installed. 26 (Android 8) is where today's Appear-on-top window type starts. The A03 runs 33. | — |
| Readings and history | Room **2.8.5** + KSP **2.3.12** | Learner's choice. Live queries refresh screens. KSP generates Room's code (2.3.10+ supports Kotlin 2.4.0). | https://developer.android.com/training/data-storage/room · https://kotlinlang.org/docs/ksp-overview.html |
| Settings | DataStore Preferences **1.1.7** | Learner's choice; already cached, so no download | https://developer.android.com/topic/libraries/architecture/datastore |
| Service + lifecycles | `lifecycle-service` and `lifecycle-runtime-compose` **2.10.0**, `activity-compose` **1.13.0**, `core-ktx` **1.18.0** | `LifecycleService` provides coroutines and the lifecycle the overlay needs. All of these build with SDK 36 (lifecycle 2.11.0 needs SDK 37). | https://developer.android.com/reference/androidx/lifecycle/LifecycleService |
| Chart | Hand-drawn Compose `Canvas` | Learner's choice: no chart library | https://developer.android.com/develop/ui/compose/graphics/draw/overview |
| Tests | JUnit 4 on the laptop (JVM) | Tests the plain-Kotlin rules without a phone | — |
| Deliberately none | navigation library, dependency-injection framework, icon library, network library | A simple screen switch, one app container, and text glyphs are enough for a proof of concept | — |

- **JDK:** Gradle runs on the machine's default JDK 25, the same family as Android Studio's bundled JBR 25. If a tool rejects it, run with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` (JDK 17 is installed).
- **Check early (first build step):** compile an empty Compose screen plus one Room entity through KSP with built-in Kotlin.
  - If KSP and built-in Kotlin conflict, set `android.builtInKotlin=false` and apply `org.jetbrains.kotlin.android` 2.4.0 (cached).
  - Keep `org.gradle.jvmargs` modest (about 2 GB): the laptop has 7 GB RAM and about 5 GB free disk.

## Where It Runs and How Someone Tries It

- **Runtime:** a native Android app on the learner's Samsung Galaxy A03 (SM-A035F, Android 13, One UI 5.1), connected by USB with debugging on.
  - No server, network, API keys, or account.
  - The emulator works too, but RAM is tight and it has no real headphones, so the phone is the test and demo device.
- **Build machine:** Linux, with the Android SDK at `~/Android/Sdk` (platform 36, build-tools 36) and the Gradle wrapper (9.3.1).
- **Build and install:** `./gradlew installDebug` from the repo root, or **Run** in Android Studio. Then tap the Smart-Senses icon, or run:
  `adb shell am start -n com.lanceky.smartsenses/.MainActivity`
- **Rules tests:** `./gradlew testDebugUnitTest`
- **Check the A03's volume steps:** `adb shell cmd media_session volume --stream 3 --get` prints the current step and range, such as `[0..15]`. With 15 steps:
  - 80% = step 12;
  - red (≥ 95%) needs step 15.
- **Demo prep on the phone (once):**
  - Settings → Apps → Smart-Senses → Battery → **Unrestricted**, and keep Smart-Senses out of Sleeping apps (see Important Failure Modes).
  - Sounds → Volume → Media volume limiter **off**.
  - Plug in or pair headphones and open a music app. Accept the safe-volume prompt once when raising the volume.
  - In Smart-Senses, turn on Settings → **Demo mode**.
- **Showing onboarding on video:** `adb uninstall com.lanceky.smartsenses` before `installDebug` gives a fresh first launch. It also wipes recorded data, so Trends will honestly say "Not enough data yet."
- **Record the demo:** `scrcpy --no-audio --record=smart-senses-demo.mp4`.
  - This mirrors the phone on the laptop and saves the video.
  - `--no-audio` keeps the phone's sound going to the headphones. scrcpy's audio capture would otherwise take over playback.
  - To include the music, use `--audio-dup` (Android 13+), which keeps playback on the phone.
  - Docs: https://github.com/Genymobile/scrcpy/blob/master/doc/recording.md · https://github.com/Genymobile/scrcpy/blob/master/doc/audio.md
- **Demo script (for `6-ship`):**
  1. Onboarding, then the live dashboard.
  2. Switch to the music app with headphones and set the volume to 80% or more.
  3. After about 1 minute, the amber card slides over the music app.
  4. Swipe it away, or tap it to open Insight.
  5. Show the History row, Patterns, Trends, and the System Log.
- **Public repository:** https://github.com/Lanceky/Smart-Senses (pushed by the learner at submission).
- **Deployment:** none. Submission is the demo video plus the public GitHub repo. Attaching a debug APK to a GitHub release is possible later but isn't planned.

## Look and Feel
Carries forward `prd.md > Look and Feel` and `scope.md > Inspiration & Identity` into Compose.

- **Palette** (`ui/theme/Color.kt`):

  | Token | Color | Used for |
  | --- | --- | --- |
  | `Black` | `#000000` | Background |
  | `Panel` | `#06140A` | Panels; also the warning-card surface |
  | `PanelRaised` | `#0A1F11` | Raised panels |
  | `Border` | `#1F5C33` | 1 dp borders |
  | `Neon` | `#39FF14` | Highlights, active status, key numbers |
  | `Text` | `#C9F7D3` | Body text |
  | `TextDim` | `#6FA27C` | Labels, secondary text |
  | `Amber` | `#FFB300` | Caution |
  | `Red` | `#FF5252` | Danger |

- **Type:** `FontFamily.Monospace` everywhere (the phone's own monospace font; nothing bundled). It's compact:
  - caps labels at 11 sp with wider letter spacing;
  - body text at 13–14 sp;
  - the volume % at about 32 sp.
- **Panels:** 4 dp corners, 1 dp `Border`, `Panel` fill, and a caps header label (`EARS`, `SYSTEM LOG`).
- **System labels:**

  | Label | Style | Meaning |
  | --- | --- | --- |
  | `MONITORING: ACTIVE` | Neon, with a pulsing dot | Monitoring is running |
  | `MONITORING: OFF` | Dim | Monitoring is switched off |
  | `MONITORING: STOPPED` | Amber | Monitoring is switched on but isn't running; see Important Failure Modes |
  | `DEMO MODE` | Amber outlined chip | Demo mode is on |
  | `SNOOZED UNTIL 20:15` | — | Cards are snoozed |

- **Severity, never by color alone:**

  | Severity | Glyph | Color |
  | --- | --- | --- |
  | `NORMAL` | ● | Neon |
  | `CAUTION` | ▲ | Amber |
  | `DANGER` | ■ | Red |

  The glyph shape and the word carry the meaning without color. No icon library.
- **Restrained glow:** a soft colored shadow only on the Ears card and the warning card, plus the pulsing status dot. No scanlines or CRT effects.
- **The warning card:**
  - Full width minus 8 dp margins, about 72 dp tall.
  - `Panel` surface, a 1.5 dp border, and a 4 dp left status rail, both in amber or red.
  - The glyph with `CAUTION` or `DANGER`, then the one-line message.
  - A dim hint: `tap for details · swipe up to dismiss`.
- **Bottom navigation:** Material 3 `NavigationBar` restyled black with caps monospace labels `DASHBOARD · TRENDS · HISTORY · SETTINGS`. The selected tab is neon.
- **Copy:** calm and honest, with no guilt. System labels are in caps; messages are in sentence case. The "estimate, not a decibel measurement" note appears in Insight and Settings.
- **Window:** black window background in `themes.xml` (no white flash at launch), edge-to-edge with dark system bars.
- **Logo:** one vector drawable of a circuit-line eye with a small sound wave as the pupil, neon on black. It's used in the top bar, as the notification icon, and as the adaptive launcher icon (black background layer).
- **Limits of the stack:** Compose can do all of this. True CRT glow and custom fonts are skipped by choice (see What Was Simplified and Why).

## Components

### App Shell and Navigation
Files: `SmartSensesApp.kt`, `MainActivity.kt`, `ui/AppScaffold.kt`.

- **`SmartSensesApp`** (the Application class) creates `AppDatabase` and `SettingsStore` once. Every component gets them from there; there's no dependency-injection framework.
- **`MainActivity`** is the only activity.
  - It waits for settings to load on a black screen, so onboarding never flashes.
  - Then it shows `OnboardingFlow` or `AppScaffold`.
  - It reads a `warningId` extra (in `onCreate` and `onNewIntent`) and opens `InsightScreen`.
- **Self-restart:** when the app comes to the foreground with `monitoringEnabled` but `LiveStatus.running == false`, `MainActivity` starts `MonitoringService` again and logs `monitoring resumed`. This is the fallback when Android or Samsung kills the monitor.
- **`AppScaffold`** shows the bottom navigation. A `rememberSaveable` tab state switches between the screens (no navigation library). Insight sits on top of the current tab; Back returns to the tab.

PRD ref: `prd.md > Screens and Layout`.

### Onboarding and Permissions
Files: `ui/onboarding/OnboardingFlow.kt`, `permissions/Permissions.kt`.

- **The steps** live in a `rememberSaveable` step state: Welcome → Choose what to monitor → Notifications explainer (Android 13+ only) → Appear on top explainer → Setup checklist.
- **Ears vs. monitoring:** Ears is the core monitor. Its switch and Settings' Monitoring switch control the same `monitoringEnabled` setting. Eyes (added during the build) has its own switch, `eyesEnabled`, off by default; it runs inside the same service, so it's only enabled while Ears is on. The Device card says `COMING LATER` and has no switch.
- **The notifications explainer:**
  - Its button requests `POST_NOTIFICATIONS` with `rememberLauncherForActivityResult(RequestPermission())`.
  - If Android won't show the prompt again, the button opens the app's notification settings (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`).
- **The Appear on top explainer:**
  - **Continue to settings** opens `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` with `package:` + the app id.
  - On `ON_RESUME`, it re-checks `Settings.canDrawOverlays()` and shows the result.
- **Skip for now** is on every permission step.
- **The checklist** shows:
  - `EARS: ON/OFF`
  - `NOTIFICATIONS: GRANTED/MISSING`
  - `APPEAR ON TOP: GRANTED/MISSING (warning card unavailable)`
  - `MONITORING: will start`

  **Finish** saves `onboardingDone = true`, starts monitoring if Ears is on, and opens the dashboard.
- **`Permissions.kt`** holds the status checks (`canDrawOverlays`, notifications granted) and the intents to each Settings page. The dashboard reuses them for its **FIX** links.
- **Install-time permissions** (manifest only): `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`.
  - `POST_NOTIFICATIONS` and `SYSTEM_ALERT_WINDOW` are also declared in the manifest.
  - Volume and headphone checks need no permission.
- **Modify system settings** (`WRITE_SETTINGS`, special access, added with Limits) isn't part of onboarding. Settings → Limits asks for it only when the brightness limit is on: an amber prompt with **[ ALLOW ]** opens `Settings.ACTION_MANAGE_WRITE_SETTINGS` for the app, and `rememberCanWriteSettings()` re-checks on `ON_RESUME`.

PRD ref: `prd.md > Setup and Permissions`, `prd.md > States and Boundaries`.

### Monitoring Service
File: `monitor/MonitoringService.kt` (a `LifecycleService`).

**Manifest entry.** The service is declared with `android:foregroundServiceType="specialUse"` and a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property ("Monitors headphone media volume to warn about prolonged loud listening"). It isn't exported.

**Start and stop.**
- `MonitoringService.start(context, reason)` and `stop(context)`.
- `start` wraps `ContextCompat.startForegroundService` in a try/catch. A refused start leaves `LiveStatus.running = false`, so the dashboard shows the truth.
- Starts happen only from the visible app (onboarding finish, the Settings switch, app open) or from `BootReceiver`.

**`onStartCommand`, in order:**
1. Post the notification first: `ServiceCompat.startForeground(...)`. Pass `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on Android 14+; on Android 13 and older, the type comes from the manifest.
2. Start the loop once.
3. Return `START_STICKY`, so Android re-creates the service if it kills it.

**On first start (seeding):**
- Close any session left open, using its last reading's time.
- Seed the engine from Room and DataStore:
  - today's loud time = the sum of `highMs` in today's readings;
  - cards shown today, counting warnings after `max(start of today, capResetAt)`, and the last warning per rule, counting warnings after `capResetAt`;
  - whether today's daily warning already fired.
- If headphones are connected, open a new session.
- Log `monitoring started`, or `monitoring resumed` when it's a system or boot restart.

**The loop:** every 1 s:
1. Hold any committed limits (`enforceLimits()`, added with Limits; see **Limits**).
2. `AudioReader.read()`.
3. Check whether the screen is usable: on and unlocked (`PowerManager.isInteractive` and not `KeyguardManager.isKeyguardLocked`).
4. `engine.onTick(sample, config, gates)`.
5. Apply the result.

**Applying a tick's result:**
- Update `LiveStatus`.
- Log transitions to `events`:
  - headphones connected or disconnected;
  - a loud stretch starting: `HIGH_VOLUME_STARTED`, "volume 85% ≥ 80%: high-volume timer started" (Patterns counts these);
  - volume back under the threshold.
- **Sessions:**
  - A connect opens a session.
  - A disconnect closes it. A session with no playing time is deleted.
- **Readings:** every 10 s, if any playing time accumulated, insert a `readings` row and add to the session's totals. Also flush on a disconnect or stop.
- **Brightness readings** (added with the Trends toggles): while Eyes is on and the screen is usable, each tick adds its time (clamped to 5 s, like Ears), brightness × time, bright-in-dim time and the lux sample. Every 10 s, on its own timer, a `brightness_readings` row is written if there was any screen-on time. Also flushed in `onDestroy`.
- **When the engine returns a warning:**
  1. Insert a `WARNING` event and get its id.
  2. Raise the session's `maxSeverity`.
  3. Call `WarningCardOverlay.show(...)` if Appear on top is granted. Otherwise the event is still recorded, and the dashboard says the card is unavailable.

**Watching settings.** The service collects the DataStore flow:
- New thresholds or Demo mode go to the engine.
- A new `capResetAt` calls `engine.resetCooldownsAndCap()`.
- `monitoringEnabled = false` calls `stopSelf()`.

**`onDestroy`:** flush both readings, close the session, unregister the limit listeners, log `monitoring stopped`, set `LiveStatus.running = false`, and remove any card.

PRD ref: `prd.md > Background Monitoring`, `prd.md > Warning Rules`.

### Audio Reader
File: `monitor/AudioReader.kt`. It needs no permission. It returns `AudioSample(output, volumePercent, playing)`.

- **Headphone output:** `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` filtered to these types, in order:
  1. `TYPE_WIRED_HEADPHONES` and `TYPE_WIRED_HEADSET` → `WIRED`
  2. `TYPE_USB_HEADSET` → `USB`
  3. `TYPE_BLUETOOTH_A2DP` and `TYPE_BLE_HEADSET` (API 31+) → `BLUETOOTH`

  The built-in speaker is never a match, so there's no output to count.
- **Volume %:** `round(100 × (v − min) / (max − min))`, using `getStreamVolume`, `getStreamMinVolume` and `getStreamMaxVolume` for `STREAM_MUSIC`.
- **Playing:** `AudioManager.isMusicActive()` checks that audio is actually playing, not paused.
- **Docs:** https://developer.android.com/reference/android/media/AudioManager · https://developer.android.com/reference/android/media/AudioDeviceInfo

PRD ref: `prd.md > Background Monitoring` (headphone outputs only; paused music never counts).

### Ears Rule Engine
Files: `rules/EarsModels.kt`, `rules/EarsRuleEngine.kt`. Plain Kotlin with no `android.*` imports, so a later KMP shared module can reuse it.

**Inputs on each tick:**
- **The sample:**
  - `elapsedRealtime` ms and wall-clock ms;
  - the local day number (epoch day). The service computes it, so the engine has no time-zone code and no `java.time`.
  - the output (or none), volume %, and whether audio is playing.
- **The config:** threshold % (80), continuous minutes (15), daily minutes (60), and Demo mode.
- **Gates:** snooze-until time, and whether the screen is usable.

**State it keeps:**
- `continuousHighMs`, `veryHighMs`, `todayHighMs`, today's date;
- the last time each rule fired;
- `cardsToday`, `dailyFiredToday`, `redFiredThisStretch`.

**Each tick:**
1. `delta = min(now − lastTick, 5 s)`, so a sleeping phone never counts as listening.
2. A new day resets today's loud time, `cardsToday` and `dailyFiredToday`.
3. **No headphones, or volume below the threshold:** the stretch ends. Both timers and `redFiredThisStretch` reset, and the result reports `stretchEnded` (the service logs `volume back under threshold` when the volume dropped).
4. **Volume below 95%:** `veryHighMs` resets.
5. **Headphones + playing + volume at or above the threshold:** add `delta` to `continuousHighMs` and `todayHighMs`. If the volume is ≥ 95%, also add it to `veryHighMs`. The first counted tick of a stretch reports `stretchStarted`.
6. **Headphones + paused:** the timers hold. Nothing is added and nothing resets.

**Rules, checked in priority order:**

| Rule | Due when | Card | Severity |
| --- | --- | --- | --- |
| `VERY_HIGH` | `veryHighMs ≥ 5 min` | "EARS: Volume very high for 5 min. Lower it to protect your hearing." | Red |
| `CONTINUOUS` | `continuousHighMs ≥ 15 min` (1 min in Demo mode), and red hasn't fired this stretch | "EARS: Volume high for N min. Consider lowering it." N is the real stretch length: 15 (or 1) the first time, longer on reminders. | Amber |
| `DAILY` | `todayHighMs ≥ 60 min`, and not fired today | "EARS: 60 min at high volume today. Give your ears a break." | Amber |

**A due warning fires only if all of these hold:**
- its rule's 10-minute cooldown has passed;
- `cardsToday < 3`;
- `now ≥ snoozeUntil`;
- the screen is usable.

Otherwise nothing is shown or logged. The timers keep running, so it fires on a later tick if the condition still holds. When it fires, the engine:
- records the fire time;
- adds 1 to `cardsToday`;
- sets `redFiredThisStretch` or `dailyFiredToday` as relevant;
- returns a `Warning(rule, severity, volumePercent, durationMs, demoMode)`.

**`resetCooldownsAndCap()`** clears the last fire times and `cardsToday`. Demo mode calls it.

**Unit tests** (`EarsRuleEngineTest`):
- amber fires at 15:00, not 14:59;
- Demo mode fires at 1:00;
- pausing holds the timer;
- lowering the volume resets the timer but keeps today's total;
- disconnecting resets the timer;
- the cooldown blocks for 10 minutes, then a reminder fires;
- the cap of 3 holds;
- snooze blocks;
- red wins over amber;
- the daily card fires once;
- midnight resets the counts;
- Demo mode clears the cooldown and the cap;
- the 5-second delta limit holds.

PRD ref: `prd.md > Warning Rules`, `prd.md > Product Decisions`.

### Eyes Reader and Rule (added during the build)
Files: `monitor/EyesReader.kt`, `rules/EyesModels.kt`, `rules/EyesRuleEngine.kt`. The rules are plain Kotlin, like Ears.

- **Reader:** no permission needed.
  - Brightness % = `Settings.System.SCREEN_BRIGHTNESS` (0–255) × 100 / 255, plus `SCREEN_BRIGHTNESS_MODE` for auto or manual. On the A03, the system slider moves linearly with this value (measured: 64 → 59 of 250, 128 → 123, 255 → 250).
  - Room light: a `Sensor.TYPE_LIGHT` listener (`SENSOR_DELAY_NORMAL`, non-wake-up). It's registered only while Eyes is on, and unregistered when the service stops. The A03 has `light_tmd2712`.
- **Rule (`EyesRuleEngine`), ticked by the service right after Ears:**
  - A bright-in-dim stretch is timed while Eyes is on, the screen is usable (on and unlocked), brightness ≥ `eyesBrightnessPercent` (70) and lux < `eyesDimLux` (20).
  - A brighter room or lower brightness resets it after a 5-second grace (`BREAK_GRACE_MS`). A locked or dark screen, or Eyes switched off, resets it at once.
  - The card is due at `eyesMinutes` (10), or 1 minute in Demo mode. It's always `CAUTION`; the message switches to "a dark room late at night" between 22:00 and 06:00.
  - **Gates:**
    - the same 10-minute cooldown (rule `BRIGHT_IN_DIM`, seeded from Room after `capResetAt`), snooze and screen gates as Ears;
    - `blocked` when the shared cap of 3 is reached (`EarsRuleEngine.cardsToday`), when Ears fired this tick, or while a card is on screen (`WarningCardOverlay.isShowing`).
  - When an Eyes card fires, the service calls `engine.countOtherCard()`, so Ears sees the shared cap.
- **Card:** both `Warning` (Ears) and `EyesWarning` implement `CardWarning` (severity, message, demoMode), which is all `WarningCard` needs.
- **Records:** the warning goes into `events` with `signal = EYES`, `rule = BRIGHT_IN_DIM`, `brightnessPercent`, `lux` and `durationMs`. `EYES_TIMER_STARTED` and `EYES_TIMER_RESET` (with the reason) go to the System Log.
- **Unit tests** (`EyesRuleEngineTest`, 12): 10-minute firing, no timer in a lit room, at low brightness or without a sensor, flicker grace vs. reset, screen off, switched off, Demo mode, cooldown then reminder, blocked and snoozed, seeded cooldown, Demo reset, late-night wording, and Ears counting Eyes cards toward the shared cap.
- **Docs:** https://developer.android.com/reference/android/hardware/SensorManager · https://developer.android.com/reference/android/provider/Settings.System

PRD ref: `prd.md > Eyes: Bright Screen in a Dim Room`.

### Limits (added during the build)
Files: `rules/Limits.kt` (plain Kotlin), `monitor/AudioReader.kt` (`capVolume`), `monitor/EyesReader.kt` (`capBrightness`), `monitor/MonitoringService.kt` (`enforceLimits`, `onLimitHeld`).

- **Snap back, don't block.** The app can't stop a volume key or the brightness slider. It writes the level back right after the change.
  - **Volume** (only with headphones connected): `AudioManager.setStreamVolume(STREAM_MUSIC, allowed, 0)`. No permission is needed.
  - **Brightness:** `Settings.System.putInt(SCREEN_BRIGHTNESS, limit × 255 / 100)`. This needs the **Modify system settings** special access (`WRITE_SETTINGS`), and the reader checks `Settings.System.canWrite` first.
- **The allowed level:**
  - `Limits.maxVolumeIndex(limit, min, max)` is the highest volume step whose % (rounded the same way as the reader) is at or under the limit. With 15 steps, 60% is step 9.
  - `Limits.maxBrightnessRaw(limit)`: 60% is 153 of 255.
- **Triggers:**
  - the 1-second loop;
  - plus, for an instant snap-back, a receiver for `android.media.VOLUME_CHANGED_ACTION`;
  - and a `ContentObserver` on `SCREEN_BRIGHTNESS`.

  Our own write fires the observer again, but the value is already within the limit, so nothing loops. All three require `monitoringEnabled`.
- **The nudge:**
  - `LimitNudger` allows one card per signal every 15 s (`NUDGE_GAP_MS`) and rotates five lines per signal, for example "We'd talked about this… volume stays at 60%."
  - Each hold logs `LIMIT_HELD` with the level the user tried.
  - When the screen is usable, a green `LimitNudge` card (heading `● LIMIT HELD`) is shown through `WarningCardOverlay`, or a toast if Appear on top is missing.
  - A tap opens the app. Limit cards aren't warnings: no History row, no cooldown, and not part of the daily cap.
- **Unit tests** (`LimitsTest`, 4):
  - the exact volume step for every limit from 0 to 100 on 15 steps;
  - brightness never reads above the limit;
  - the 15-second gap per signal;
  - the lines name the limit and rotate.

PRD ref: `prd.md > Limits: Your Commitment`.

### Live Status
File: `monitor/LiveStatus.kt`. One in-memory `StateFlow<Live>` that the service writes and the screens read. The service and the screens run in the same process.

- **Fields:**
  - `running`
  - `output`
  - `volumePercent`
  - `playing`
  - `continuousHighMs`
  - `veryHighMs`
  - `todayHighMs`
  - `sessionListeningMs`
  - `status`: `NORMAL`, `HIGH`, or `VERY_HIGH`
- **Not in Room:** it's live state. It resets when the process dies, and the service re-seeds it from Room.

PRD ref: `prd.md > Screens and Layout` (Ears card), `prd.md > Background Monitoring`.

### Warning Card Overlay
Files: `overlay/WarningCardOverlay.kt` (the window), `overlay/WarningCard.kt` (the Compose card).

**The window.**
- `WindowManager.addView(composeView, params)`.
- Params:
  - type `TYPE_APPLICATION_OVERLAY`;
  - flags `FLAG_NOT_FOCUSABLE`: touches outside the card go to the app underneath, and the keyboard is never taken;
  - width `MATCH_PARENT`, height `WRAP_CONTENT`, gravity `TOP`;
  - format `TRANSLUCENT`.
- The card is drawn just below the status bar (status-bar inset). Check this on the A03.

**Compose outside an activity.** A `ComposeView` outside an activity needs a lifecycle owner and a saved-state owner on its view tree. A small `OverlayOwner`:
- implements `LifecycleOwner` and `SavedStateRegistryOwner`;
- is `RESUMED` while shown and `DESTROYED` on removal;
- is set with `setViewTreeLifecycleOwner` and `setViewTreeSavedStateRegistryOwner`.

**Behavior.**
- **Show:** `AnimatedVisibility` drops the card straight down from under the status bar on a bouncy spring (damping 0.5, stiffness 120): it overshoots, bobs back up and settles in about a second, with no fade. The window keeps 28 dp of extra height while the card is dropping, so the overshoot isn't clipped.
- **Auto-hide:** after 5 s, `LaunchedEffect` slides it out and then removes the view.
- **Swipe up:** a vertical drag of more than about 40 dp upward dismisses it immediately.
- **Tap:** removes the card and starts `MainActivity` with `FLAG_ACTIVITY_NEW_TASK` and `warningId`. This is allowed because the app holds Appear on top and its window is visible (see the docs link).
- **One card at a time:** a new card replaces the old one.
- **Not allowed:** if `Settings.canDrawOverlays()` is false, `show` does nothing. The warning is already recorded.

**Docs:**
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY
- https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_OVERLAY_PERMISSION
- https://developer.android.com/guide/components/activities/background-starts

PRD ref: `prd.md > The Warning Card`.

### Monitoring Notification
File: `monitor/MonitoringNotification.kt`.

- **Channel:** `monitoring`, low importance, no sound.
- **Notification:** ongoing, with a monochrome small icon (`ic_stat_ears`).
  - Text: "Ears: watching headphone volume. Readings stay on this phone."
  - Tapping it opens the app.
- **If notifications are denied** (Android 13+), it isn't shown, but monitoring keeps running. Android lists the app under the notification drawer's active-apps (Task Manager) button.

Docs: https://developer.android.com/develop/ui/views/notifications/notification-permission

PRD ref: `prd.md > Background Monitoring`, `prd.md > States and Boundaries`.

### Boot Receiver
File: `monitor/BootReceiver.kt`. Exported. It filters `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`, which are system-only broadcasts.

- It uses `goAsync()` to read DataStore.
- If `onboardingDone` and `monitoringEnabled`, it calls `MonitoringService.start(context, reason)`, which logs `monitoring resumed (boot)` after a restart or `monitoring resumed (app update)` after `MY_PACKAGE_REPLACED`.
- Both broadcasts are allowed to start a foreground service from the background. The Android 15 boot limits don't include `specialUse`.
- `MY_PACKAGE_REPLACED` also brings monitoring back after `./gradlew installDebug`, which replaces the app.

Resolves `prd.md > Open Questions` #2: monitoring resumes automatically after a phone restart (learner's decision).

### Room Database
Files: `data/Entities.kt`, `data/Daos.kt`, `data/AppDatabase.kt`. Tables are in **Data Model**. Version 3 with `exportSchema = false`. `MIGRATION_1_2` adds the Eyes columns to `events` with `ALTER TABLE`, so existing history is kept and old rows become `EARS`. `MIGRATION_2_3` creates the empty `brightness_readings` table and its `epochDay` index, with SQL identical to Room's generated schema.

- **`ReadingDao`**
  - `insert`
  - `observeDay(day)`
  - `observeDailyStats(fromDay)`: per day, the play-time-weighted average volume, `SUM(highMs)`, `SUM(playingMs)` and the reading count, returned as the plain-Kotlin `rules.DayStat`
  - `highMsOnDay(day)`
  - `lastTimeForSession(id)`
- **`SessionDao`**
  - `insert`, `update`, `delete`
  - `openSessions()`
  - `observeAll()`, newest first
- **`EventDao`**
  - `insert` (returns its id)
  - `observeRecent(limit)`
  - `observeWarnings()`
  - `observe(id)`: a Flow, so Insight updates if the row arrives late
  - `warningsSince(from)`
  - `lastWarningTime(rule, from)`
  - `countKindSince(kind, from)`: a Flow
- **`BrightnessDao`** (added with the Trends toggles)
  - `insert`
  - `observeDay(day)`
  - `observeDailyStats(fromDay)`: per day, the screen-on-weighted average brightness, `SUM(brightInDimMs)`, `SUM(screenOnMs)` and the count, in the same `DayStat` shape as volume

Screens use the `Flow` queries, so Room pushes new rows to them.

Docs: https://developer.android.com/training/data-storage/room

PRD ref: `prd.md > History`, `prd.md > States and Boundaries` (persistence).

### Settings Store (DataStore)
File: `data/SettingsStore.kt`. A Preferences DataStore exposes `Flow<Settings>` plus suspend setters.

| Key | Default | Notes |
| --- | --- | --- |
| `onboardingDone` | `false` | |
| `monitoringEnabled` | `false` | Ears on = monitoring on in this build |
| `eyesEnabled` | `false` | Eyes, inside the same service |
| `eyesBrightnessPercent` | `70` | 50–100, steps of 5 |
| `eyesDimLux` | `20` | 5–100, steps of 5 |
| `eyesMinutes` | `10` | 5–60 |
| `thresholdPercent` | `80` | 50–95, steps of 5. The 95 cap keeps "very high" inside a loud stretch. |
| `continuousMinutes` | `15` | 5–60 |
| `dailyMinutes` | `60` | 15–240 |
| `demoMode` | `false` | |
| `capResetAt` | `0` | Set to *now* when Demo mode turns on |
| `snoozeUntil` | `0` | |
| `volumeLimitEnabled` | `false` | Limits (added during the build) |
| `volumeLimitPercent` | `60` | 20–95, steps of 5 |
| `brightnessLimitEnabled` | `false` | Needs Modify system settings |
| `brightnessLimitPercent` | `60` | 20–95, steps of 5 |

`setDemoMode(true)` does three things in one edit:
- sets `demoMode`;
- sets `capResetAt = now`;
- clears `snoozeUntil`.

The service sees `capResetAt` change and calls `engine.resetCooldownsAndCap()` and `eyesEngine.resetCooldown()`.

Docs: https://developer.android.com/topic/libraries/architecture/datastore

PRD ref: `prd.md > Settings and Demo Mode`.

### Dashboard
File: `ui/dashboard/DashboardScreen.kt`. It reads `LiveStatus`, `SettingsStore` and Room.

**1. Top bar.**
- The logo and `SMART-SENSES`.
- `MONITORING: ACTIVE`, `OFF` or `STOPPED`.
- `DEMO MODE` and `SNOOZED UNTIL hh:mm` chips when they apply.

**2. Notices**, one line each, shown only when relevant:
- `WARNING CARD: UNAVAILABLE · Appear on top is off [FIX]`
- `NOTIFICATIONS: MISSING [FIX]`

**3. Ears card** (largest, glowing).
- **Headphones:** `HEADPHONES: CONNECTED (BLUETOOTH)` or `HEADPHONES: NOT CONNECTED (speaker never counts)`.
- **Volume:** a big `VOLUME 87%` with a thin bar showing the threshold mark.
- **Playback:** `PLAYING` or `PAUSED: timers hold`.
- **Times:** `LISTENING 23 min` and `TODAY ABOVE 80% 12 min`.
- **Status:**
  - `● STATUS: NORMAL`;
  - `▲ HIGH VOLUME: 03:12 / 15:00`, a timer running toward the rule (`/ 01:00` in Demo mode);
  - `■ VERY HIGH: 01:40 / 05:00`.

**4. Eyes card:** `BRIGHTNESS 85%` with `AUTO`/`MANUAL`, a bar with the threshold mark, `ROOM LIGHT: 6 LUX · DIM (UNDER 20)` or `· LIT` (or `NO SENSOR`), then `● STATUS: NORMAL` or `▲ BRIGHT IN DIM ROOM: 00:42 / 10:00`. While Eyes is off: a compact `EYES · OFF` row with `[ TURN ON ]`. Below it, a small dim `DEVICE · COMING LATER` card.

**5. Patterns** (see Patterns).

**6. System Log** (see System Log).

PRD ref: `prd.md > Screens and Layout`, `prd.md > States and Boundaries`.

### Patterns
Files: `rules/Patterns.kt` (plain Kotlin), shown by `DashboardScreen`.

**Today.** Uses today's `HIGH_VOLUME_STARTED` count and `SUM(highMs)` for today.
- Example: "Today: 3 high-volume stretches, 24 min at or above 80%."
- With none yet: "No high-volume listening recorded today."

**Upward trend** (learner's rule; resolves `prd.md > Open Questions` #1).
- **Data:** daily stats for the last 30 days. A *listening day* is a day with playing time.
- **Not enough data:** with fewer than 4 listening days, show "Volume trend: Not enough data yet (n of 4 listening days)."
- **Comparison:** the average of the 2 most recent listening days against the average of the 2 before them. Each day's average is weighted by play time.
- **Rise of 5 points or more:** "Volume trending up: 78% → 86% over your last 4 listening days." Shown amber with ▲.
- **Otherwise:** "No upward volume trend (82% → 80%)."

No medical wording, and real rows only.

**Unit tests** (`PatternsTest`):
- 3 days → not enough data;
- a rise of +5 → trending up; +4 → not trending up;
- only the latest 4 listening days are used;
- days with no playing time are skipped.

PRD ref: `prd.md > Patterns`.

### System Log
Shown in `DashboardScreen` from `EventDao.observeRecent(8)`. Each line: `> 14:02:11  WARNING ▲ EARS 85% for 1 min [DEMO]`, monospace and dim, with the warning lines colored by severity.

**Event kinds:**
- `MONITORING_STARTED`, `MONITORING_RESUMED`, `MONITORING_STOPPED`
- `HEADPHONES_CONNECTED`, `HEADPHONES_DISCONNECTED`
- `HIGH_VOLUME_STARTED`, `VOLUME_BACK_UNDER`
- `WARNING`
- `DEMO_MODE_ON`
- `SNOOZED`
- `LIMIT_HELD` (added with Limits): "volume held at 60% (tried 67%)" or "brightness held at 60% (tried 100%)"

PRD ref: `prd.md > System Log`.

### History Screen
File: `ui/history/HistoryScreen.kt`. Shows warnings (`events` of kind `WARNING`) and listening sessions together, newest first.

- **Columns:** `TIME · SIGNAL · READING · DURATION/CONTEXT · SEVERITY`.
- **Warning row:**
  - Example: `21:04  EARS  85%  high for 15 min · BLUETOOTH  ▲ CAUTION`.
  - Demo-mode warnings get a `DEMO` tag.
  - Tapping it opens Insight.
- **Session row:**
  - Example: `20:31  EARS  peak 87%  listened 23 min · WIRED  ● NORMAL`.
  - It shows the highest severity reached during the session. An unfinished session says `ongoing`.
- **Empty:** `> no events yet_` with a short hint.

PRD ref: `prd.md > History`.

### Trends Screen and Trend Chart
Files: `ui/trends/TrendsScreen.kt`, `ui/trends/TrendChart.kt` (Compose `Canvas`, no library; it was `EarsChart.kt` until brightness joined), `rules/TrendBuckets.kt` (plain Kotlin).

- **Toggles:** two segmented rows, `[VOLUME | BRIGHTNESS]` and `[TODAY | WEEK | MONTH | 6 MONTHS]`. They're weighted rows, so four options fit a 360 dp screen.
- **Signal:**
  - Volume: `readings`, the line at `thresholdPercent` (80), minutes at or above it, `LISTENED` and `AVERAGE VOLUME`.
  - Brightness: `brightness_readings`, the line at `eyesBrightnessPercent` (70), minutes bright in a dim room, `SCREEN ON` and `AVERAGE BRIGHTNESS`.
- **Today:**
  - Today's 10-second readings as a neon line. The x-axis runs from the first reading to now and spans at least 10 minutes.
  - A dashed amber line at the signal's level, and a 0–100% y-axis with a few labels.
  - A gap of more than 25 s (paused music, screen off) breaks the line, so that time isn't drawn.
  - Below it: `TODAY ABOVE 80%: 12 min` or `TODAY BRIGHT IN A DIM ROOM: 3 min`.
- **Week and Month:** one point per day for 7 or 30 days (the day's average, weighted by active time), with bars of minutes over the line per day. Week labels every weekday and numbers each bar; Month labels about one date a week and drops the per-bar numbers so 30 bars stay readable.
- **6 Months:** 26 one-week buckets ending today, each a time-weighted average with summed minutes. Month names mark where a new month starts.
- **`TrendBuckets.of(stats, today, count, daysPerBucket)`** groups the daily stats. Days with no active time are ignored, and an empty bucket has no point.
- **Totals:** every range shows the minutes over the line in total, the active minutes and the weighted average, plus how many days or weeks have data.
- **Empty:** fewer than 2 readings in the window shows `Not enough data yet.` For Brightness with Eyes off, it says to turn Eyes on.
- **Unit tests** (`TrendBucketsTest`, 3): daily buckets end today with gaps left empty; a weekly bucket's weighted average and summed minutes; days outside the window or with no active time are ignored.

PRD ref: `prd.md > Trends`.

### Insight and Snooze
File: `ui/insight/InsightScreen.kt`. Opened by tapping the card or a History warning row; loads the `events` row by id.

- **Shows:**
  - severity glyph and label;
  - rule name;
  - volume %;
  - how long (`high for 15 min`);
  - time;
  - output type;
  - a `DEMO` tag if it applies.
- **Why:** "Volume stayed at or above 80% for 15 minutes while audio played through headphones."
- **Eyes warnings** use the same screen: brightness %, room light in lux, how long, and time. Why: "Screen brightness stayed at or above 70% while the room was darker than 20 lux, for 10 min." Note: "Brightness % and room light are phone readings, not an eye-health measurement."
- **Demo note:** for the two rules Demo mode shortens (continuous Ears, Eyes), a line says the wait was shortened to 1 minute.
- **Routing:** `MainActivity` keeps the open warning id in state (read in `onCreate` and `onNewIntent`, then removed from the intent). History warning rows set the same id. A bottom-nav tap or Back closes Insight.
- **Honest note:** "Volume % is an estimate of exposure, not a decibel measurement; headphones differ in loudness."
- **Snooze 1 hour:** sets `snoozeUntil = now + 1 h` and logs `SNOOZED`. When snoozed, the button becomes `SNOOZED UNTIL hh:mm`.

PRD ref: `prd.md > Insight and Snooze`.

### Settings Screen and Demo Mode
File: `ui/settings/SettingsScreen.kt`.

**Monitoring.** `MONITORING [on/off]` starts or stops the service.

**EARS / WARNING THRESHOLDS.** Steppers (− value +) for:
- the high-volume threshold;
- the continuous warning;
- the daily cumulative warning;
- read-only `Demo duration: 1 min`.

The red rule (≥ 95% for 5 min) is shown read-only.

**LIMITS / YOUR COMMITMENT** (added during the build), between Eyes and Demo mode:
- `Volume limit (headphones)` and `Brightness limit` switches, each with a max stepper (20–95%);
- an amber prompt with **[ ALLOW ]** while the brightness limit is on without Modify system settings;
- a note that adaptive brightness may fight the limit, and that limits are not medical advice.

The dashboard's Ears and Eyes threshold lines add `· limit 60% (committed)` while a limit is on.

**DEMO MODE [on/off].**
- Turning it on resets the cooldowns, today's card count and the snooze, and logs `DEMO_MODE_ON`.
- The dashboard then shows `DEMO MODE`.
- Turning it off returns to the normal timer. Data is never wiped.

**About.** "Readings stay on this phone. No account, no internet." Also the honest volume note.

PRD ref: `prd.md > Settings and Demo Mode`.

### Theme and Logo
Files:
- `ui/theme/Color.kt`, `ui/theme/Type.kt`, `ui/theme/Theme.kt`: a Material 3 dark color scheme mapped to the palette, plus monospace typography.
- `ui/components/Console.kt`: `Panel`, `CapsLabel`, `SeverityTag`, `StatusDot` (pulsing), `ConsoleButton`.
- Resources:
  - `res/drawable/ic_logo.xml`: the circuit-eye with a sound-wave pupil;
  - `ic_launcher_foreground.xml`;
  - `ic_stat_ears.xml`: monochrome, for the notification;
  - `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`: adaptive icons with a black background;
  - `values/themes.xml`: black window.

Docs: https://developer.android.com/develop/ui/views/launch/icon_design_adaptive

PRD ref: `prd.md > Look and Feel`.

## Data Model

Everything lives on the phone, in the app's private storage. There's no account, no cloud, and no internet permission. Backup is off (`android:allowBackup="false"`), so readings never leave the phone.

### Room tables (`smart-senses.db`)

**`readings`**: one row per 10 seconds of listening.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | Long | Primary key, auto-generated |
| `time` | Long | Epoch ms |
| `epochDay` | Long | Local day |
| `volumePercent` | Int | Average volume while playing in the 10 s |
| `outputType` | String | `WIRED`, `USB`, or `BLUETOOTH` |
| `playingMs` | Long | |
| `highMs` | Long | Time at or above the threshold |
| `sessionId` | Long | |

- Written by the service only when `playingMs > 0`, so paused time and the speaker never create rows.
- Read by Trends, Patterns, and the engine's seeding of today's loud time.

**`brightness_readings`**: one row per 10 seconds of screen-on time while Eyes is on. Added in v3, with the Trends toggles.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | Long | Primary key, auto-generated |
| `time` | Long | Epoch ms |
| `epochDay` | Long | Local day (indexed) |
| `brightnessPercent` | Int | Average brightness setting while the screen was on in the 10 s |
| `lux` | Int? | Average room light; `null` without a light-sensor reading |
| `screenOnMs` | Long | Screen on and unlocked |
| `brightInDimMs` | Long | Time at or above the Eyes brightness level with the room under the dim level |

- Written only while Eyes is on and only when `screenOnMs > 0`, so a locked screen or Eyes switched off never creates rows.
- Read by Trends.

**`listening_sessions`**: from headphones connected to disconnected.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | Long | Primary key |
| `startedAt` | Long | |
| `endedAt` | Long? | `null` while ongoing |
| `epochDay` | Long | |
| `outputType` | String | |
| `listeningMs` | Long | |
| `highMs` | Long | |
| `peakVolumePercent` | Int | |
| `maxSeverity` | String | `NORMAL`, `CAUTION`, or `DANGER` |

- **Created** on connect. **Updated** each flush and when a warning raises the severity. **Closed** on disconnect or stop.
- A session with zero listening time is deleted when it closes.
- A session left open by a killed process is closed at the next start, using its last reading's time.

**`events`**: warnings and System Log lines.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | Long | Primary key |
| `time` | Long | |
| `kind` | String | See System Log |
| `message` | String | |
| `rule` | String? | `CONTINUOUS`, `VERY_HIGH`, `DAILY`, or `BRIGHT_IN_DIM` (Eyes) |
| `severity` | String? | |
| `volumePercent` | Int? | |
| `durationMs` | Long? | |
| `outputType` | String? | |
| `demoMode` | Boolean | |
| `signal` | String | `EARS` (default) or `EYES`. Added in v2. |
| `brightnessPercent` | Int? | Eyes warnings. Added in v2. |
| `lux` | Int? | Eyes warnings. Added in v2. |

- Only ever inserted.
- Read by History (`kind = WARNING`), the System Log, Insight (by `id`), Patterns, and the engine seeding:
  - warnings after `capResetAt` give the cooldowns, so one at 23:55 still counts at 00:02;
  - warnings after `max(start of today, capResetAt)` give today's card count;
  - today's `DAILY` warning shows whether the daily card already fired.

### DataStore (`settings.preferences_pb`)

The keys and defaults are in **Settings Store**. Settings are written by the onboarding, Settings, and Insight screens. The screens, the service, and BootReceiver read them as a Flow.

### In memory only

- `LiveStatus` and the engine's timers.
- **When the process dies:** the current stretch's continuous and very-high timers restart from zero, and up to 10 s of unsaved listening time can be lost. Today's loud time, the cooldowns, the card count, and whether the daily card fired are rebuilt from Room when monitoring starts.

### Leaving and coming back

Everything survives closing the app, switching apps, and restarting the phone. Monitoring itself resumes on boot and whenever the app opens. Uninstalling the app deletes all of it.

## File Structure

```
Smart-Senses/
├── app/
│   ├── build.gradle.kts            # Android app module: SDK levels, Compose, KSP/Room, dependencies
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # permissions, MainActivity, MonitoringService (specialUse), BootReceiver; no INTERNET; allowBackup=false
│       │   ├── java/com/lanceky/smartsenses/
│       │   │   ├── SmartSensesApp.kt          # Application: creates AppDatabase + SettingsStore once
│       │   │   ├── MainActivity.kt            # single activity: onboarding vs app, warningId → Insight, restarts monitoring if needed
│       │   │   ├── rules/                     # PLAIN KOTLIN, no android.* (future KMP shared module)
│       │   │   │   ├── EarsModels.kt          # AudioSample, EarsConfig, Gates, Warning, Rule, Severity, TickResult
│       │   │   │   ├── EarsRuleEngine.kt      # timers, red/amber/daily rules, cooldown, cap, snooze
│       │   │   │   ├── EyesModels.kt          # EyesSample, EyesConfig, EyesWarning (added during the build)
│       │   │   │   ├── EyesRuleEngine.kt      # bright-in-dim timer, grace, cooldown, shared cap
│       │   │   │   ├── Patterns.kt            # today summary + 4-day upward-trend rule
│       │   │   │   ├── TrendBuckets.kt        # day and week buckets for Trends (added with the toggles)
│       │   │   │   └── Limits.kt              # committed max volume/brightness, nudge lines, 15 s gap (added during the build)
│       │   │   ├── monitor/                   # the background part
│       │   │   │   ├── MonitoringService.kt   # foreground service: 1 s loop, Room writes, overlay calls
│       │   │   │   ├── AudioReader.kt         # headphones? volume %? playing?
│       │   │   │   ├── EyesReader.kt          # brightness setting + light sensor (lux)
│       │   │   │   ├── LiveStatus.kt          # in-memory StateFlow for the dashboard
│       │   │   │   ├── MonitoringNotification.kt # channel + ongoing notification
│       │   │   │   └── BootReceiver.kt        # resume after reboot / app update
│       │   │   ├── overlay/
│       │   │   │   ├── WarningCardOverlay.kt  # Appear-on-top window, OverlayOwner, show/hide/tap
│       │   │   │   └── WarningCard.kt         # the Compose card (amber/red)
│       │   │   ├── data/
│       │   │   │   ├── Entities.kt            # Reading, BrightnessReading, ListeningSession, Event
│       │   │   │   ├── Daos.kt                # ReadingDao, BrightnessDao, SessionDao, EventDao
│       │   │   │   ├── AppDatabase.kt         # Room database v3 + MIGRATION_1_2, MIGRATION_2_3
│       │   │   │   └── SettingsStore.kt       # DataStore keys, defaults, setters
│       │   │   ├── permissions/
│       │   │   │   └── Permissions.kt         # status checks + Settings intents (FIX links)
│       │   │   └── ui/
│       │   │       ├── AppScaffold.kt         # bottom nav + Insight on top
│       │   │       ├── theme/{Color,Type,Theme}.kt
│       │   │       ├── components/Console.kt  # Panel, CapsLabel, SeverityTag, StatusDot, ConsoleButton
│       │   │       ├── onboarding/OnboardingFlow.kt
│       │   │       ├── dashboard/DashboardScreen.kt  # top bar, notices, Ears card, Patterns, System Log
│       │   │       ├── trends/{TrendsScreen,TrendChart}.kt
│       │   │       ├── history/HistoryScreen.kt
│       │   │       ├── insight/InsightScreen.kt
│       │   │       └── settings/SettingsScreen.kt
│       │   └── res/
│       │       ├── drawable/              # ic_logo, ic_launcher_foreground, ic_stat_ears (vectors)
│       │       ├── mipmap-anydpi-v26/     # adaptive ic_launcher + ic_launcher_round
│       │       └── values/                # strings, colors, themes (black window)
│       └── test/java/com/lanceky/smartsenses/rules/
│           ├── EarsRuleEngineTest.kt      # JVM tests for every rule transition
│           ├── EyesRuleEngineTest.kt      # JVM tests for the Eyes rule
│           ├── PatternsTest.kt
│           ├── TrendBucketsTest.kt
│           └── LimitsTest.kt
├── gradle/
│   ├── libs.versions.toml          # all versions from Stack in one place
│   └── wrapper/                    # Gradle 9.3.1 wrapper
├── build.gradle.kts                # plugin versions (apply false)
├── settings.gradle.kts             # repositories + :app
├── gradle.properties               # AndroidX on, modest JVM memory
├── gradlew, gradlew.bat
├── README.md                       # what it is, how to build/install, demo prep, honesty note
├── devpost/                        # scope.md, prd.md, spec.md (+ checklist.md during build); learner-profile.md is git-ignored
├── .agents/, .claude/, skills-lock.json  # Devpost Learn skill pack
└── .gitignore                      # build outputs, local.properties, .gradle, IDE files, learner-profile.md
```

The `agent/` folder is the skill pack's copy for another agent, so it's committed with `.agents/` and `.claude/` (see `checklist.md > Revisions`).

## External Services and Dependencies

- **No external services.** There are no APIs, servers, keys, rate limits, or costs.
- **Android system services** (built into the phone):

  | Service | Calls | Permission |
  | --- | --- | --- |
  | `AudioManager` | `getDevices(GET_DEVICES_OUTPUTS)`, `getStreamVolume`, `getStreamMinVolume`, `getStreamMaxVolume` (`STREAM_MUSIC`), `isMusicActive()` | None |
  | `WindowManager` | `addView` / `removeView` with `TYPE_APPLICATION_OVERLAY` | Appear on top |
  | `PowerManager.isInteractive`, `KeyguardManager.isKeyguardLocked` | Screen on and unlocked? | None |
  | `NotificationManager` | Monitoring channel and notification | `POST_NOTIFICATIONS` on 13+ |
  | `Settings` | `canDrawOverlays()`, `ACTION_MANAGE_OVERLAY_PERMISSION`, `ACTION_APP_NOTIFICATION_SETTINGS` | — |

- **Build dependencies** come from Google Maven and Maven Central. Versions and docs are in **Stack**; foreground-service docs:
  - https://developer.android.com/develop/background-work/services/fgs
  - https://developer.android.com/develop/background-work/services/fgs/service-types
- **Dev tools:**
  - `adb` (Android SDK platform-tools);
  - scrcpy 3.3.4 for mirroring and recording (docs linked in Where It Runs).

## Important Failure Modes

**Samsung battery management kills or freezes the monitor** (Sleeping apps, Deep sleeping apps, Optimized battery, and the A03's small RAM). User sees:
- Monitoring may stop while the app is closed. `START_STICKY` asks Android to bring it back.
- Opening Smart-Senses restarts it and logs `monitoring resumed`.
- If it's down, the top bar honestly says `MONITORING: STOPPED` instead of pretending.
- Demo prep sets Battery → **Unrestricted** (https://dontkillmyapp.com/samsung).
- There's no in-app battery-exemption request in this build.

**Android foreground-service restrictions:**
- **Deadline:** `startForeground` must run within seconds of `startForegroundService`, or Android crashes the app. It's the first call in `onStartCommand`.
- **Starting from the background** is banned on Android 12+. The app starts only from the visible app or `BootReceiver`, and catches `ForegroundServiceStartNotAllowedException`; the user sees `STOPPED` instead of a crash. Docs: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- **A type is required on 14+:** `specialUse`, with its manifest property.
- **Restart uncertainty:** a system restart of the sticky service may not be allowed to call `startForeground`. If it throws, the service stops quietly and the app restarts it on open.
- **Denied notifications** hide the notification but don't stop monitoring.
- **Android 13's active-apps button** lets the user stop the app. The top bar then shows `STOPPED` until they reopen it.
- **Doze:** if Android pauses the loop with the screen off, the 5-second delta limit means missed time isn't counted. It under-counts and never over-counts.

**Appear on top missing or revoked:**
- No card can be drawn, but warnings are still recorded in History and count toward the cap.
- The dashboard shows `WARNING CARD: UNAVAILABLE` with **FIX**, which opens the Appear-on-top page.

**Samsung's volume protections make the demo seem to fail:**
- The safe-volume prompt can block raising the volume past about 60–70% until accepted.
- The media volume limiter can cap the volume below 80%.
- Demo prep covers both.
- With 15 volume steps, 80% is step 12, and red (≥ 95%) needs step 15.

**KSP with built-in Kotlin** can fail at build time. The first build step proves it. The fallback is in **Stack**.

**Limits (added during the build):**
- **Snap-back is not a hard block.** Samsung's volume panel may briefly show the higher step before it drops back.
- **The instant path is undocumented.** `VOLUME_CHANGED_ACTION` is a hidden broadcast. If a phone doesn't send it, the 1-second loop still holds the limit, just a little later.
- **Adaptive brightness** may raise `SCREEN_BRIGHTNESS` itself. The limit then pulls it back down and may show a nudge the user didn't cause. This hasn't been tested; the A03 runs on manual brightness.
- **Without Modify system settings,** the brightness write throws `SecurityException`, which is caught. Nothing is held, and Settings shows the prompt.
- **A limit under a warning line hides that warning.** A 60% volume limit means the 80% Ears rule can't fire; turn the limits off to demo the warning cards.
- **Limits hold only while monitoring runs.** If Samsung kills the service, nothing holds until it's back.

## What Was Simplified and Why

- **Tab switch** instead of the Navigation library: five screens don't need routes or a back stack. The fuller version would add the navigation-compose dependency and typed routes.
- **One app container** instead of a dependency-injection framework (Hilt): only two shared objects exist, the database and the settings. The fuller version would add Hilt modules and annotations.
- **Hand-drawn Canvas chart** instead of a chart library (learner's choice): one simple chart, no dependency. The fuller version would bring zoom, tooltips, and more chart types.
- **System monospace font and Unicode glyphs** instead of a bundled font and icon pack: no assets and readable everywhere. The fuller version would bundle a font such as JetBrains Mono.
- **10-second readings** instead of raw 1-second samples: plenty for charts, and storage stays small.
- **Room `exportSchema = false`, hand-written migrations:** v1→2 (Eyes columns) and v2→3 (brightness history) were written by hand, checked against Room's generated SQL, and installed over real data on the phone. A real release would export schemas and add migration tests.
- **Media volume % as the exposure proxy** instead of measured decibels: Android doesn't expose what reaches the ear. The UI says so.
- **Bluetooth counts as headphones** (PRD assumption): Android can't reliably tell headphones from a Bluetooth speaker.
- **A documented battery setting** instead of an in-app exemption request: the honest `STOPPED` status covers the gap.
- **Ears first, Eyes minimal:** Eyes was added during the build with one rule, no red level and no patterns. Its brightness chart in Trends came later, with the toggles. Device is still a placeholder (`prd.md > Deferred From the POC`).
- **Never faked:** Demo mode shortens one real timer. No sample data exists anywhere; empty states say "Not enough data yet."

## Decisions and Open Issues

### Learner decisions (agreed in `4-spec`)

1. **Android-only, Kotlin + Jetpack Compose. The rules are plain Kotlin** with no `android.*`, so a Kotlin Multiplatform module can reuse them later.
   - Why: the learner's learning goal and a real background-capable app.
   - Tradeoff: no iOS or shared module tonight.
2. **Room for readings and history, DataStore for settings.**
   - Why: live queries refresh the screens, and settings survive restarts.
   - Tradeoff: KSP setup risk, proven in build step 1.
3. **Foreground service with 1-second checks, a reading every 10 seconds, and automatic resume after restart.**
   - Why: "keeps working when the user switches apps," and the dashboard updates within seconds.
   - Tradeoff: a small battery cost and an ongoing notification. Resolves `prd.md > Open Questions` #2.
4. **Hand-drawn Compose chart.**
   - Why: one simple chart, no library.
   - Tradeoff: more drawing code; no zoom or tooltips.
5. **Upward trend needs at least 4 listening days and a rise of 5 points** (the average of the 2 most recent listening days against the 2 before).
   - Why: simple, explainable, and honest.
   - Tradeoff: tonight's demo shows "Not enough data yet." Resolves `prd.md > Open Questions` #1.
6. **Foreground-service restrictions and Samsung battery behavior are documented as failure modes**, not solved with extra features.
   - Tradeoff: there's a manual demo-prep step.

All product rules come from the approved `prd.md`: thresholds, timers, cooldown, cap, Demo mode, and colors.

### Implementation details derived from those decisions (agent-filled; change any)

- **Service type `specialUse`:** no standard foreground-service type fits watching media volume. `mediaPlayback` is for apps that play media, and `health` needs body-sensor permissions.
- **Privacy you can verify:** no `INTERNET` permission and `allowBackup="false"`.
- **Day numbers:** `epochDay` columns, and the engine receives day numbers instead of reading time zones.
- **Stretch counting:** loud stretches are counted with `HIGH_VOLUME_STARTED` events, which also make the System Log readable.
- **Threshold range 50–95%,** so "very high" (≥ 95%) is always inside a loud stretch. Continuous warning 5–60 min; daily 15–240 min.

### Assumptions filling gaps in the PRD (agent-filled; not learner decisions)

- **Screen off or locked:** a due card waits until the screen is on and unlocked, if the condition still holds. Nothing is logged while it waits.
- **Daily card:** fires at most once per day. Reminders after the cooldown apply only to the continuous and red rules.
- **After red:** once red fires in a loud stretch, amber reminders are skipped for the rest of that stretch. This extends the PRD's "red replaces amber."
- **Reminder text:** reminder cards show the real stretch length ("Volume high for 25 min").
- **Cap counts recorded warnings:** the daily cap counts recorded warnings even when the card couldn't be drawn because Appear on top was off.
- **One card at a time:** a new card replaces the old one.
- **Self-restart:** monitoring restarts itself when the app is opened, as a fallback for system kills.
- **Demo mode and snooze:** turning Demo mode on also clears an active snooze. This serves the PRD's stated purpose: "earlier test runs never block a card during the recording." It doesn't reset whether today's daily card fired.
- **Session rows:** History's session rows show the session's peak volume as the reading, and their highest severity.
- **Trends empty state:** "Not enough data yet" means fewer than 2 readings in the chosen window.

### The useful unknown

- **The question:** the learner's stated goal is to understand device-level permissions, background services, system settings, and overlays. The unfamiliar part was how Android's three kinds of permission differ (granted at install, asked at runtime, special access in system Settings), and how a foreground service keeps working after switching apps.
- **What clarified it:** a plain-language explanation during the spec interview. The learner confirmed "the permissions explanation is clear."
- **Evidence to collect on the A03 during the build:**
  1. Deny notifications in onboarding. Confirm monitoring still runs (dashboard `ACTIVE`, the app listed in Task Manager).
  2. Grant Appear on top on its Settings page. Confirm the explainer shows `GRANTED` on return without restarting the app.
  3. Confirm monitoring survives about 15 minutes with the screen off and battery set to Unrestricted.

### Still open (check during the build, fallback already chosen)

- Does a system restart of the sticky service on Android 13 / One UI 5.1 succeed, or does it throw? Fallback: restart when the app opens.
- Exact card placement below the status bar on the A03.
- KSP together with AGP 9.1's built-in Kotlin. Fallback in **Stack**.
- The A03's real number of media-volume steps. Check with `adb shell cmd media_session volume --stream 3 --get`; 80% and 95% map to steps.

## Build Order

Follows `prd.md > What We're Building`. `5-build` turns this into its checklist.

1. **Toolchain proof:** Gradle wrapper, Compose "hello" in the theme colors, and one Room entity through KSP. `installDebug` on the A03; check the volume steps.
2. **Rules first:**
   - `EarsRuleEngine`, `Patterns`, and the JVM tests.
   - All three rules live here because they share one timer, so the red and daily rules from the PRD's second tier are only wired into the app later.
3. **Data:** Room entities and DAOs, `SettingsStore`, `SmartSensesApp`.
4. **Monitoring service:** `MonitoringService`, `AudioReader`, `LiveStatus`, the notification, and a basic dashboard Ears card. Check it on the phone with headphones.
5. **Warning card over other apps:** `WarningCardOverlay`, recording the warning, tap → `MainActivity`, Demo mode's 1-minute timer.
6. **Onboarding and permissions:** explainers, Skip, checklist, dashboard notices with FIX.
7. **History and Settings:** thresholds, Demo mode reset, monitoring switch. **The first tier ends here:** the full warning flow works on the phone.
8. **Second tier:**
   - Insight and Snooze;
   - the red and daily cards (wired into the app);
   - the System Log;
   - Patterns;
   - the Trends chart;
   - the circuit-eye logo and launcher icon;
   - `BootReceiver`;
   - the failure-mode checks on the A03.
