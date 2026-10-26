---
doc: scope
status: approved
---

# Smart-Senses

A native Android app that quietly watches everyday device habits—starting with headphone volume—slides a slim green card over whatever app you're using when a habit could harm your hearing, and shows you the patterns in your listening over time.

## The Unique Kernel
Not another screen-time tracker or isolated alert: "a small, thoughtful intervention in the moment, backed by a bigger picture of how someone's habits change over time." Smart-Senses notices a potentially risky habit while you're busy doing something completely unrelated—no wellness app to open, no timer to start, nothing to log.

## Who It's For
A student or developer who spends hours coding or studying with music in their headphones—like the student in the library, deep in a coding assignment, whose volume has crept higher than when they started. They're "simply absorbed in what they're doing." Today nothing catches it: the changes are gradual, and they barely notice.

## The Core Loop
Smart-Senses runs quietly in the background while they work in other apps. When headphone volume stays high for a configured period, a slim green card slides down from the top of the screen: "EARS: Volume high for 20 min. Lower it to protect your hearing." They lower the volume or dismiss the card and get back to work. Meanwhile, Smart-Senses records their listening and picks out patterns in it, such as volume creeping higher than "when they started" or listening "at high volumes for hours." That bigger picture is waiting when they open the app, and it's why they come back.

## Inspiration & Identity
- Dark, terminal-inspired dashboard with neon-green accents.
- The card is "brief, noticeable, and actionable."
- "A thoughtful assistant looking out for you, not another app constantly demanding your attention"—no screaming, no guilt.
- Honest by design: Android's media-volume setting is "only an estimate of listening risk, not a direct measurement of the sound reaching someone's ears," and the warning language should say so.

## Why This Matters to the Learner
- Impact: help people "recognize risky patterns earlier and make better everyday choices"—"something meaningful that helps prevent avoidable hearing and vision damage."
- Learning: "the technical challenge of making this work reliably on Android"—device-level permissions, background services, system settings, and overlays, built with Kotlin and Jetpack Compose, as they move from backend engineering toward AI-assisted mobile development.

## What "Working" Looks Like
Install the app, complete a simple permission setup, enable background monitoring, then use the phone normally. The proof, in their words: "change the volume while listening to music, switch to another app, and see a real warning appear and get recorded."

**The "oh, that's cool" beat:** the green card slides down over a completely different app because Smart-Senses noticed on its own. Back in the app, the warning is in the history, next to patterns drawn from their own recorded listening: "actual collected readings rather than sample data," all stored on the device.

## The POC Boundary
Ears only, end to end:
- Detect connected headphones; track media volume and listening duration.
- Background monitoring that keeps working when the user switches apps.
- The green warning card over other apps (Android's "display over other apps" permission) when volume stays high for a configured period. Lowering the volume or dismissing the card works as expected.
- Permission onboarding and a way to turn monitoring on.
- A basic Compose dashboard with current listening status.
- Recording of listening usage on the device: when headphones connect and disconnect, volume over time, time spent at high volume, and each warning. It's shown in a simple history.
- Pattern detection over that recorded data, such as volume creeping up during a session or long stretches at high volume, shown on the dashboard.
- **Eyes, added during the build** at your request once the Ears flow worked outside the app: a bright screen in a dim room (screen brightness setting plus the phone's light sensor) gets its own amber card, History row and Settings section. It reuses the Ears foundation: the same service, card, gates and shared daily cap. Trends stay Ears-only.

## Later
Once the ears flow works reliably outside the app, "reuse that foundation for brightness and temperature."
- **Eyes extras:** brightness and room-light charts in Trends, Eyes patterns over several days, and Eyes-specific advice. (Tapping an Eyes card already opens the shared Insight page with its brightness and room light.)
- **Device — temperature caution** (battery temperature, thermal status): "less central to the core hearing-and-vision use case."
- Full trend charts and more advanced insights, such as tapping the card to see why it appeared.
- Screen-time breaks, text-size suggestions, personalized recommendations.
- Data controls such as clearing stored history.
- iOS via Kotlin Multiplatform: iOS doesn't allow cards over other apps and limits background monitoring, and iOS builds need a Mac with Xcode (this machine runs Linux).

## Explicitly Cut
- Diagnosing hearing problems or guaranteeing protection: the app "can't diagnose hearing or vision problems," and media volume only estimates risk.

## Open for the PRD
- What counts as "high" volume and how long "a while" is—and how the demo triggers a real warning within a minute or so.
- Which listening patterns to detect, and which of them the demo can show from real data recorded on demo day. Patterns across several days need several days of recordings.
