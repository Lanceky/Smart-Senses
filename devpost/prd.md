---
doc: prd
status: approved
---

# Smart-Senses — Product Requirements

Smart-Senses is a native Android app for students and developers who spend hours in headphones. It quietly watches headphone volume in the background. When listening stays loud for too long, it slides a slim card over whatever app they're using. It also records their listening so they can see the bigger picture.
Source: `scope.md > The Unique Kernel`, `scope.md > Who It's For`, `scope.md > The POC Boundary`.

## The Core Journey
Source: `scope.md > The Core Loop`, `scope.md > What "Working" Looks Like`.

1. **First open:** a black Welcome screen shows the logo and "Your device. Your habits. Your senses. Stay aware before small habits become big problems." The user taps **Get started**.
2. **Choose what to monitor:** Ears, Eyes, and Device cards, each with one sentence. Ears is the feature for this version and is on by default. Eyes is an optional switch, off by default (added during the build). Device shows as "coming later."
3. **Permissions:** each permission is explained right before it's requested. Notifications support background monitoring. Appear on top ("Display over other apps") lets the card show over other apps. Each step offers **Continue to settings** and **Skip for now**. Readings stay on the device.
4. **Confirm setup:** a checklist shows which features are on, which permissions are missing, and whether monitoring is running. Monitoring starts, and the live dashboard opens with real readings.
5. The user leaves Smart-Senses and listens to music through headphones. The volume rises to 80% or more.
6. After 15 uninterrupted minutes at or above 80% (**1 minute in Demo mode**), an amber card slides down over the other app: "EARS: Volume high for 15 min. Consider lowering it." It stays about 5 seconds, then slides away.
7. The user can lower the volume, which stops the timer; the time already spent loud stays in today's total. They can also swipe the card up to dismiss it, or tap it to open the warning's insight.
8. The warning is recorded. It appears in History and the dashboard's system log, and it feeds Trends and the patterns.
9. Back in Smart-Senses, they can see the warning, why it fired, and patterns from their own recorded listening.

**Success:** steps 1–9 work on the phone with real readings. The card appears over another app, and the event is waiting in History.

## Screens and Layout

**Onboarding (first launch only):** Welcome → Choose what to monitor → one explainer per permission → Setup checklist.

**Main app**, with bottom navigation: **Dashboard · Trends · History · Settings**.

- **Dashboard**, top to bottom:
  1. **Top bar:** logo, app name, and monitoring status (`MONITORING: ACTIVE` / `MONITORING: OFF`), plus a `DEMO MODE` label while Demo mode is on.
  2. **Ears card**, the most prominent element: headphone connection, media volume %, listening duration, and warning status (e.g., `STATUS: NORMAL`, or the high-volume timer running).
  3. **Eyes card:** screen brightness % (auto or manual), room light in lux (DIM or LIT), and the bright-in-dim timer. While Eyes is off, it's a compact `EYES · OFF` row with **Turn on**. The **Device card** stays small: "coming later."
  4. **Patterns:** short, plain-language observations from recorded listening (scope: "shown on the dashboard").
  5. **System log:** a small terminal-style feed of meaningful events, such as monitoring started or a warning triggered.
- **Trends:** a Volume or Brightness chart for Today, Week, Month or 6 Months (see `Trends`).
- **History:** the event list (see `History`).
- **Settings:** Ears → Warning thresholds, Eyes → Bright screen in a dim room, Demo mode, and the monitoring on/off control.
- **Insight:** opened by tapping a warning card. It shows why the warning fired and offers **Snooze 1 hour**.
- **Warning card:** floats over other apps (see `The Warning Card`).

## Look and Feel
- **A live monitoring console:** technical, but readability comes first.
- **Palette:** pure black background, dark-green panels, neon-green highlights, thin borders, restrained glow effects, and small status indicators.
- **Type:** compact monospace. Concise system labels in caps, such as `MONITORING: ACTIVE` and `STATUS: NORMAL`.
- **Severity colors:** green = normal/informational (not a warning), amber = caution, red = danger. Never use color alone; always pair it with words and an icon.
- **Warning card:** keeps the dark-green surface and terminal type in every state. The border, severity icon, small status rail, and severity label switch to amber or red. It stays compact and uncluttered.
- **Logo:** a stylized eye formed from circuit lines, with a small sound wave at its center. Geometric, simple enough for an Android launcher icon, neon green on black.
- **Language:** honest and calm. Media volume is "only a proxy for potential exposure, not a sound-level measurement." No medical claims and no guilt.

## Features and Behavior

### Setup and Permissions
Source: `scope.md > The POC Boundary` ("permission onboarding").
No permission dumping, privacy first, no dead ends.
- [ ] On first launch, the Welcome screen appears; **Get started** moves to Choose what to monitor.
- [ ] Ears can be enabled. Eyes can be switched on too; it's off by default and runs with monitoring, so it needs Ears on. Device is visible, marked "coming later," and can't be switched on.
- [ ] Each permission has its own explainer that appears just before the request and says why it matters.
- [ ] For Appear on top, **Continue to settings** opens Smart-Senses' "Appear on top" page in system Settings. On return, the app shows whether it was granted.
- [ ] **Skip for now** always moves on. The app still opens, and it clearly says which features are unavailable and how to turn them on.
- [ ] The setup checklist shows enabled features, missing permissions, and monitoring status. Finishing it starts monitoring and opens the dashboard.
- [ ] Onboarding doesn't appear again on later launches.

### Background Monitoring
Source: `scope.md > The POC Boundary` ("keeps working when the user switches apps").
- [ ] While monitoring is on, an ongoing notification shows that Smart-Senses is monitoring.
- [ ] Monitoring keeps running after the user leaves the app or switches to another app.
- [ ] Only headphone and earphone outputs count (wired, USB, or Bluetooth). The phone's built-in speaker never counts.
- [ ] Only time with audio actually playing through headphones counts toward the timers, today's total, and listening duration. With headphones connected but music paused or stopped, no time is counted.
- [ ] The Ears card updates within a few seconds when headphones connect or disconnect, or when the volume changes.

### Warning Rules
Source: `scope.md > The Core Loop`, `scope.md > Open for the PRD`. These are product rules, not clinical safety thresholds.

| Rule | Trigger | Severity |
| --- | --- | --- |
| Continuous high volume | Headphones connected, volume ≥ 80% for 15 uninterrupted minutes (**1 minute in Demo mode**) | Amber |
| Very high volume | Headphones connected, volume ≥ 95% for 5 continuous minutes | Red |
| Daily cumulative | About 60 minutes total at ≥ 80% in one day | Amber |

- [ ] Volume dropping below the threshold, or headphones disconnecting, cancels the continuous timer. Time already spent at high volume stays in today's total. A fresh timer starts only when the condition returns.
- [ ] Paused music stops the clock: paused time never counts. (Assumption: pausing stops the timer without resetting it, so short gaps between tracks don't restart the 15 minutes.)
- [ ] **Cooldown:** after a warning, the same warning type can't show again for 10 minutes.
- [ ] **Daily cap:** at most 3 warning cards per day, across all signal types.
- [ ] **Snooze:** while snoozed (1 hour), no cards appear. Monitoring and recording continue.
- [ ] If the condition still holds after the cooldown, a later reminder can appear if the daily cap hasn't been reached.

### The Warning Card
Source: `scope.md > The Core Loop`, `scope.md > Inspiration & Identity`.
- [ ] When a rule triggers, a slim card drops down from just below the status bar with a buoyant bounce, over whatever app is open.
- [ ] Amber card text: "EARS: Volume high for 15 min. Consider lowering it." The minutes shown match the actual rule, so Demo mode says 1 min.
- [ ] The card is visible for about 5 seconds and then slides away by itself.
- [ ] Swiping the card up dismisses it immediately. Dismissing doesn't stop monitoring and doesn't mark the episode as resolved.
- [ ] Tapping the card opens Smart-Senses on that warning's Insight.
- [ ] The card shows the severity with a colored border, an icon, and a label, not color alone.

### Insight and Snooze
Source: `scope.md > Later` (tap to see why), brought into this build by the learner during the PRD.
- [ ] Insight shows why the warning fired: rule, volume %, how long, time, and output type.
- [ ] An Eyes warning's Insight shows brightness %, room light in lux, how long, and time, with a note that these are phone readings, not an eye-health measurement.
- [ ] It includes a one-line honest note that volume % is an estimate, not a decibel measurement.
- [ ] **Snooze 1 hour** suppresses cards for an hour. The dashboard shows that snooze is active.

### History
Source: `scope.md > The POC Boundary` ("shown in a simple history").
Each row shows **Time · Signal (EARS or EYES) · Reading (volume % or brightness %) · Duration/context (how long it lasted, plus the headphone connection or the room light) · Severity (green/amber/red)**.
- [ ] Every warning appears as a row with time, volume %, listening duration, output type, and severity.
- [ ] Every Eyes warning appears as an `EYES` row with the brightness %, room light in lux, how long it lasted, and amber severity.
- [ ] Listening sessions (from headphones connected to disconnected) appear as green rows unless a warning occurred.
- [ ] Rows are newest first and remain after the app or phone restarts.
- [ ] With no events yet, History says so in the console style instead of showing an empty screen.

### System Log
- [ ] The dashboard shows a short feed of recent meaningful events, such as monitoring started or stopped, headphones connected or disconnected, a warning triggered, and volume back under the threshold.

### Patterns
Source: `scope.md > The POC Boundary` ("pattern detection over that recorded data"). The patterns are simple rule-based comparisons with no medical claims.
- [ ] **Frequent or prolonged high-volume listening:** after high-volume time is recorded today, the dashboard summarizes it in plain words, e.g., how many high-volume stretches and how many minutes above the threshold today.
- [ ] **Volume trending upward over several days:** compares recent days with earlier ones. Until there are enough days of readings, it shows "Not enough data yet."
- [ ] Every pattern comes from real recorded readings; there is no sample data.

### Trends
Source: `scope.md > Later` (full charts stay later). The learner added a minimal Ears chart during the PRD, then the brightness toggle and longer ranges during the build.
- [ ] A **Volume / Brightness** toggle picks the signal. The chart shows it over time with its line marked (the volume threshold, 80% by default, or the Eyes brightness level, 70% by default), plus the minutes over that line: at or above the volume threshold, or bright in a dim room.
- [ ] **Today**, **Week**, **Month** and **6 Months** change the window: today's 10-second readings, one point per day for 7 or 30 days, or one point per week for 26 weeks.
- [ ] Brightness is recorded only while Eyes is on and the screen is on and unlocked.
- [ ] With too few readings, the chart area shows "Not enough data yet."

### Eyes: Bright Screen in a Dim Room
Source: added during the build at your request, once the Ears flow worked outside the app (`scope.md > The POC Boundary`). It reuses the Ears foundation.
- [ ] Eyes reads the screen brightness setting and the room light from the phone's light sensor. Neither needs a permission.
- [ ] A timer runs while the phone is unlocked with the screen on, brightness is at or above 70%, and the room is under 20 lux. It shows on the dashboard as `▲ BRIGHT IN DIM ROOM mm:ss / 10:00`.
- [ ] After 10 minutes (**1 minute in Demo mode**), an amber card drops over the current app: "EYES: Bright screen in a dim room for 10 min. Consider lowering brightness." Between 22:00 and 06:00 it says "a dark room late at night."
- [ ] Lowering the brightness or a brighter room resets the timer after a 5-second grace, so a passing shadow doesn't restart the count. Locking the phone or switching the screen off resets it at once. Each reset is logged with its reason.
- [ ] Eyes uses the same 10-minute cooldown, snooze and screen gates as Ears. The 3-cards-a-day cap is shared. It never replaces a card already on screen.
- [ ] Eyes cards are always amber. There's no red level: it's a nudge, not a health claim.
- [ ] Without a light sensor, the card says Eyes can't tell a dim room and never warns.

### Settings and Demo Mode
Source: `scope.md > Open for the PRD` ("how the demo triggers a real warning within a minute").
- [ ] **Settings → Ears → Warning thresholds** has: high-volume threshold (80%), continuous warning (15 min), and daily cumulative warning (60 min).
- [ ] **Demo mode** is clearly labeled. While it's on, the continuous warning fires after 1 minute instead of 15. The headphone requirement and the 80% threshold stay the same, so the demo uses the real monitoring path. Turning it off returns to normal settings.
- [ ] **Settings → Eyes** has the Eyes switch, the brightness threshold (70%), the dim-room light level (20 lux), and the warning time (10 min). Demo mode shortens the Eyes timer to 1 minute too.
- [ ] Turning Demo mode on resets the cooldowns and today's card count, so earlier test runs never block a card during the recording.
- [ ] Everything recorded in Demo mode is added to History like any other data. Nothing is wiped.
- [ ] Monitoring can be turned on and off.
- [ ] All settings remain after the app or phone restarts.

## States and Boundaries
- **First use / no data:** the dashboard shows live status. History shows "no events yet," and Trends and the patterns show "Not enough data yet."
- **Headphones not connected:** the Ears card says so. No timers run, and the speaker never triggers warnings.
- **Headphones connected, nothing playing:** the Ears card shows them as connected, but the timers, today's total, and listening duration don't advance.
- **Appear on top not granted:** cards can't appear. The dashboard says the warning card is unavailable and offers a way to fix it. Monitoring and recording continue.
- **Notifications not granted:** monitoring still runs. The checklist and dashboard mark notifications as missing.
- **Monitoring off:** the top bar shows `MONITORING: OFF`. No readings are recorded and no cards appear.
- **Demo mode on:** the top bar shows `DEMO MODE` so nobody confuses it with normal timing.
- **Snoozed:** the dashboard shows that cards are snoozed and until when.
- **Persistence:** readings, events, and settings stay on the device across app and phone restarts. There's no account and no cloud.

## Product Decisions
- **High volume = ≥ 80% of the device's media-volume range, headphone outputs only.** It's a proxy for exposure, not decibels, because headphones differ in loudness.
- **Amber after 15 continuous minutes; daily amber at about 60 minutes; red at ≥ 95% for 5 minutes.** These are product rules, not clinical thresholds.
- **Thresholds are configurable** under Settings → Ears → Warning thresholds.
- **Demo mode shortens only the Ears continuous timer and the Eyes timer to 1 minute**, so the demo still exercises real monitoring.
- **Turning Demo mode on resets the cooldowns and the daily card count; demo data is added to History, never wiped.** This way, test runs can't block the recorded demo.
- **Paused music doesn't count.** Only time with audio actually playing through headphones counts as listening.
- **The card shows for about 5 seconds; swipe up to dismiss; tap to open its insight.** Snooze lives in the insight, so the floating card stays uncluttered.
- **There's a 10-minute cooldown per warning type and at most 3 cards per day across all signals**, so the app keeps monitoring "without nagging the user."
- **Lowering the volume cancels the continuous timer but keeps the exposure** in today's total.
- **Green means normal; amber and red carry warnings; color is never used alone.**
- **Onboarding has no permission dumping, is privacy first, and has no dead ends.** Skipped permissions never block the app.
- **The dashboard is Ears-first.** Eyes sits under it, and Device stays a placeholder until it's built.
- **Patterns are rule-based comparisons with no medical claims.**
- **Trends stays simple:** one chart at a time (Volume or Brightness), Today/Week/Month/6 Months, and an empty state. It started as one Ears chart with Day/Week; the brightness toggle and longer ranges were added during the build at your request.
- **Assumptions to confirm:**
  - Copy for the red and daily cards reuses your earlier wording: red ends "Lower it to protect your hearing," and daily ends "Give your ears a break."
  - A red warning replaces amber for the same loud stretch.
  - Warnings suppressed by cooldown, the cap, or snooze aren't shown or logged as separate events.
  - Bluetooth audio counts as headphones, because Android can't always tell headphones from a Bluetooth speaker.
  - The red rule isn't configurable in this build.

## What We're Building
Order follows your scope priority: "make the first milestone a complete, working headphone-volume warning flow," then "add full trend charts and more advanced insights after the first warning works end to end."

1. **Core warning flow (must work for the demo):**
   - Setup and Permissions
   - Background Monitoring
   - Ears card
   - Continuous amber rule plus Demo mode
   - The Warning Card over other apps
   - Cooldown and daily cap
   - History with persistence
   - Settings
2. **Then:**
   - Insight and Snooze
   - Red and daily cumulative rules
   - System Log
   - Patterns
   - Trends
   - The circuit-eye logo as the launcher icon
3. **Added during the build:** Eyes, a bright screen in a dim room (your request after the Ears flow worked).
4. **Added during the build:** the Trends Volume/Brightness toggle and the Month and 6 Months ranges (your request after slice 8).

## Deferred From the POC
- **Eyes extras:** Eyes patterns over several days (such as a late-night habit) and Eyes-specific advice (`scope.md > Later`). The Eyes card, rule, History rows and its Insight (the shared page) are built.
- **Device/Heat (temperature, thermal status)**, including the charging-heat pattern (`scope.md > Later`).
- **Temperature and room-light charts in Trends.** Temperature arrives with Device/Heat. Volume and brightness charts are built.
- **Data controls such as clearing history** (`scope.md > Later`).

## Possible Later Enhancements
- Fuller trend charts and advanced insights beyond the one Ears chart.
- Screen-time breaks, text-size suggestions, and personalized recommendations.
- iOS via Kotlin Multiplatform. iOS doesn't allow cards over other apps, and building for it needs a Mac with Xcode.

## Non-Goals
- **Diagnosing hearing problems or guaranteeing protection.** The app "can't diagnose hearing or vision problems."
- **Measuring real sound levels in decibels.** Media volume is a proxy.
- **Monitoring the built-in speaker.** Only headphone outputs count.
- **Changing the volume for the user.** The user decides (`scope.md > The Core Loop`: they "lower the volume or dismiss the card").
- **Accounts, cloud sync, or sending data off the device.** Everything stays local.

## Open Questions
Resolved at review: Demo mode resets the cooldowns and the daily cap and adds its data to History; paused music doesn't count.

1. **Multi-day upward trend in the demo** (can wait). Tonight it will honestly show "Not enough data yet," because it needs several days of readings. The exact number of days and the comparison rule can be set in `4-spec`.
2. **After a phone restart** (can wait). Should monitoring resume automatically, or when the user next opens the app?
