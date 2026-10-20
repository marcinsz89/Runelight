# AYN Thor LED Controller — Codex Project Handoff

**Prepared:** 2026-10-20  
**Status:** Discovery, comparison, and targeted upstream source review. **No fork has yet been created, compiled, deployed, or tested by ChatGPT.**  
**Target device:** AYN Thor Max (Android dual-display handheld; owner has 1 TB model).  
**Working name:** **TBD.** Candidate directions: *Prism*, *Runelight*. Do not hard-code an app name or final package ID until selected.  
**Primary starting point:** [Bifrost](https://github.com/Pollux-MoonBench/Bifrost) (archived, GPLv3).  
**Reference fork:** [DuoFrost](https://github.com/Tufein/DuoFrost) (Bifrost-derived, GPLv3).  
**Closed-source product benchmark:** [AmbiLuma](https://ambiluma.com/) / [Google Play](https://play.google.com/store/apps/details?id=com.bitmonkeygames.ambiodin).

> **Codex instructions:** Read this handoff in full before changing code. Separate **confirmed source observations**, **the owner's firsthand device observations**, **public claims**, and **hypotheses**. Start by reproducing the failures and measuring a baseline, not by blindly rewriting the entire application. Deliver a reliable, resource-efficient controller with a simple interface. Make incremental changes with tests and actual-device verification; do not report fixes as tested unless the tests ran.

---

## 1. Goal and philosophy

Create **one clean, polished AYN Thor LED utility** with the most valuable parts of three existing apps, keeping it simpler and more stable than any of them.

> **“Lite” refers to a lean engine and a minimal UX, *not* necessarily a small number of effects.**

The owner explicitly **wants multiple effects**. They primarily want **ambient analog-stick lighting that follows game content**, plus other useful animations and battery/status modes. The app should be easy to set up once, then run quietly without intervention while gaming.

### Design principles

1. **Reliability before features.** UI dismissal, recent-app cleaning, sleep/wake, a display switch, or a lost capture session must not cause inexplicable crashes or leave stale output.
2. **Smooth and accurate Ambient mode is the flagship.** Independent left/right stick colors, pleasing response, and configurable sampling. **Completely black sampled content should turn LEDs fully off** (explicit owner preference), not leave a faint glow.
3. **Background service is separate from UI.** Closing the Activity is not equivalent to stopping LED output. Explicit Stop always wins.
4. **Small resource footprint.** Games/emulators must not stutter because of sampling, binder calls, shell processes, allocations, or UI polling. Avoid unnecessary continuous work for static effects.
5. **Few sensible controls by default.** Effect, intensity, smoothness, speed when applicable, saturation, top/bottom capture source, left/right color where relevant; advanced settings stay hidden.
6. **Avoid feature creep.** No complex scheduler, plugins, Smart Scenes/rule priorities, massive preset manager, wallpaper engine, or elaborate import/undo workflows in the core product.
7. **Device-first engineering.** Target **AYN Thor Max** and test both screens. Avoid assuming Android emulator success proves Thor reliability.

### Target user journey

**Install → one-time necessary permissions → choose effect → adjust live → play.** No requirement to invent/save a preset just to change one color. A useful default configuration should work without visiting Advanced Settings.

### What counts as success

- Users can launch Ambient on the top screen, play games, dismiss the control UI, and retain lighting.
- Reliable screen selection (top vs bottom) independently of which display hosts the UI.
- No crash when creating/moving Activity on bottom display, or at least a documented/device-specific graceful fallback until fixed.
- On capture interruption, do not fake an active capture or loop indefinitely. Surface clear status; request new permission where Android requires it.
- Static effects are restored/reusable without reopening the app after normal UI dismissal.
- Black/near-black handling is configurable and reliably reaches true off.
- No measurable game stutter attributable to the LED app under repeatable tests; CPU/battery/power results reported rather than claimed.

---

## 2. Firsthand device observations (high-confidence user reports)

These are the owner's specific findings, **not** universally proven behavior for all devices:

- **Bifrost:** owner likes the idea and Ambient behavior, especially black-screen LEDs-off, but experiences crashes. We do **not** yet have an actual `logcat` crash trace.
- **DuoFrost:** initially reproduced a crash, including after the published fix. On further testing, **launch on the top screen was fine**; **launch on the bottom display crashed unless a particular setting was enabled**. With that setting, running on bottom is possible, but Ambient behavior remains unsatisfactory. **We need to identify the precise workaround setting and get logs.**
- **AmbiLuma:** owner tried the Google Play app. It is usable, but the product/output still has shortcomings. Owner suspects it handles background persistence more successfully; **this is a hypothesis to investigate, not a proven implementation fact**.
- **DuoFrost is not the desired architectural base.** It is a Bifrost fork and its recent feature growth did not resolve the user's key experience problems. Use its source only for targeted comparisons and promising small fixes.
- **The owner prefers lightweight execution over automation.** They corrected an initial proposal to remove effects: keep plenty of effects but trim orchestration, scheduling and preset complexity.
- Games and PC emulation run on this device; avoid driver tweaking unrelated to the LED controller, unnecessary foreground polling, and high-frequency writes that add latency or stutter.

### Questions to resolve with the owner/device

1. Firmware and Android release/build on Thor Max.
2. The **exact DuoFrost setting** that avoids bottom-display crash.
3. Exact Bifrost crash sequence and stacktrace; does it occur on launch, screen capture consent, starting Ambient, display move, or after backgrounding?
4. For Ambient defects, capture a sample that reveals: wrong hues, lag, flickering, black bars, bad left/right mapping, stale frames, or broken dark-scene handling.
5. Which display should drive Ambient when game is on top and frontend/secondary app is on bottom? Default expectation: **top**.
6. Confirm whether users need different *effect types* simultaneously on left/right rings, or just independently selected colors. Implement shared effect with different colors first unless otherwise requested.

---

## 3. Competitor / predecessor comparison

### 3.1 Overview

| App | Status | Strengths | Weaknesses / caveats |
|---|---|---|---|
| **Bifrost** | GPLv3; archived; upstream version **1.3.1** (2026-08-21) | Direct Thor LED integration; broad effect library; low-resolution sampling; left/right control; helpful codebase | Background lifecycle problems; complicated all-in-one UI/service; known random crashes; no longer maintained |
| **DuoFrost** | Active GPLv3 fork; **2.5.1** release notes dated 2026-10-16 | Background recovery work, LED test, brightness limits, mute, sleep timer, widgets, improved controls | Shares upstream lineage and problem classes; Smart Scenes, collections, schedulers create large state/complexity surface; owner's bottom-display crash and poor Ambient results persist |
| **AmbiLuma** | Proprietary; Google Play | Community praise for pleasing Ambient colors/transitions and convenience; Quick Settings; appears simple to activate | Source unavailable; technical details not independently verified; owner not satisfied overall; user reports still mention background/capture interruptions |

**Important:** “AmbiLuma has better background management” is **not** a conclusion from code inspection. It is a device/user impression to compare using Android diagnostics.

### 3.2 Feature inventory (verified vs unknown)

Legend: **Y** publicly documented or found in source; **?** insufficient evidence; **N** not part of documented design (not necessarily impossible).

| Feature | Bifrost | DuoFrost | AmbiLuma |
|---|:---:|:---:|:---:|
| Ambient screen color | Y | Y | Y |
| Audio Reactive | Y | Y | Y |
| Combined ambient/audio effects | Y | Y | Y |
| Static color | Y | Y | Y |
| Rainbow/cycling | Y | Y | Y |
| Breathing/pulse/fade | Y | Y | ? |
| Strobe/sparkle/chase/rave | Y | Y | ? |
| Battery indicator | Y | Y | Reported by users |
| CPU temperature mode | Y | Y | ? |
| Separate left/right colors | Y | Y | Y |
| One color across both sticks | Y | Y | ? |
| Adjustable smoothness/saturation/intensity | Y | Y | Broadly yes; exact knobs ? |
| Black-bar-aware/custom sampling | Y | Inherited/modified | ? |
| Explicit top/bottom source | Y | Y | ? |
| True black → LEDs off | In source | Likely inherited; test | ? |
| Simple saved presets | Y | Y | ? |
| Per-app automatic profiles | Y | Y | ? |
| App/rules/schedule automation | Y | Extensive | ? |
| Quick Settings start/stop | Y | Y | Y |
| Background foreground service | Y | Y | Appears to run in background; internals unknown |
| Persistence after Activity closure | Conditional | Claimed improvement | User reports positive; not guaranteed |
| Process death recovery | No sticky restart | Attempted | ? |
| Service diagnostics / LED tests | Limited | Y | ? |
| Widgets / mute / sleep timer | Limited | Y | ? |
| Themes/custom artwork/collections | Y (some) | Extensive | ? |
| Plugins and external integrations | Y | Inherited/expanded | ? |

**Bifrost effect enum observed in source:** `AMBIENT`, `AUDIO_REACTIVE`, `AMBIAURORA`, `BATTERY_INDICATOR`, `CPU_TEMPERATURE`, `STATIC`, `BREATH`, `RAINBOW`, `PULSE`, `STROBE`, `SPARKLE`, `FADE_TRANSITION`, `RAVE`, `CHASE`, `PIPBOY` (15 types).

**DuoFrost change log highlights (source, not independent certification):**

- Version 1 reported corrections to purple/pink ambient hues, custom sampling, screenshot cleanup, and stale callbacks.
- Version 2 claimed continued LED operation after closing UI / Clear All, service recovery, capture-resume controls, battery setting shortcuts, output limits, optional screen-off dimming, mute, timer, Quick Settings integration, LED self-test, widgets, and diagnostic status sharing.
- Version 2.5 added Smart Scenes (per-app/game groups/time/day/battery/charging, priority logic, holds), favorites/collections/search, preview, detailed import review/undo, and layout overhaul.
- Version 2.5.1 addressed a welcome dialog/window issue when Android recreates the UI on Thor display changes. **User reports some bottom-screen failures remain**.

**AmbiLuma limitations:** Do not reverse-engineer or guess internal architecture based on UX alone. Observe running services, foreground state, Android process importance, notification behavior, capture state and transitions via ADB. Some public reviews also report capture stopping unexpectedly.

### 3.3 What the community actually seems to use (qualitative, not statistics)

Across publicly discussed examples, the most concrete recurring uses are:

1. **Ambient matching** while playing (primary reason to install); especially attractive when both sticks reflect game visuals.
2. **Battery/charging indicator** (useful glanceable status on handheld, including when bottom display is off).
3. **Static RGB / Rainbow** (simple cosmetic choice).
4. **Audio Reactive / Ambi Aurora** (visually engaging in colorful or music-heavy games).
5. **Per-app profiles** for a smaller subset of enthusiasts, commonly to use one look in launcher and another in games.

Recurring complaints:

- Stops working when backgrounded, cleared from Recents, or after sleep.
- Capture permission lost, sample freezes, need to open UI to reactivate.
- Wrong colors, delayed changes, flicker, poor handling of dark areas/letterboxing.
- Too many menus/presets/rules just to select a color.
- Dual-display/UI relocation issues.
- Performance/power concerns.

**Evidence caution:** These are themes from forum/review discussions and the conversation, **not an exhaustive survey** and not measured feature telemetry. Validate assumptions with actual users before radically removing a popular feature. Relevant community starting point: [r/AynThor](https://www.reddit.com/r/AynThor/); [Bifrost farewell thread](https://www.reddit.com/r/AynThor/comments/1wnglc0/so_long_bifrost/). Owner supplied a thread screenshot about background persistence; see §4.3.

---

## 4. Confirmed Bifrost source findings (from repository, 2026-10-20)

Source references below use exact upstream file paths for Codex to inspect.

### 4.1 Toolchain, license, project state

- Repo: `https://github.com/Pollux-MoonBench/Bifrost`
- Last 1.3.1 release commit inspected: [`1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa`](https://github.com/Pollux-MoonBench/Bifrost/commit/1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa).
- The repository contains `LICENSE` with **GNU GPLv3**. Keep copyright attribution, notices, source access and GPL obligations for a distributed derivative. A different package ID/signing key should be used for new app builds.
- `app/build.gradle.kts`: Kotlin Android application, `minSdk = 33`, `targetSdk = 36`, `compileSdk = 36`, `versionName = 1.3.1`; release shrinking/obfuscation with R8 enabled.
- `app/src/main/AndroidManifest.xml`: foreground LED service; MediaProjection FGS permission; accessibility service; extra components such as wallpaper, scheduling receivers, plugin activity, tile, boot receiver, external API, wake lock, package observation.

### 4.2 Large code surfaces

- [`MainActivity.kt`](https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/MainActivity.kt): **~4,384 lines** at review; manages UI, themes, presets, capture permission, switches, app profiles, display-specific logic, etc.
- [`services/LEDService.kt`](https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/services/LEDService.kt): **~1,909 lines**; controls effect changes, projection lifecycle, app-profile resolution, external overrides, charging, brightness, etc.
- This complexity is a maintenance hazard, but **code size is not itself proof of a crash**. Refactor based on behavior/test evidence.

### 4.3 Background execution issues — directly verified

From `LEDService.kt`:

```kotlin
override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    if (!allowBackgroundRun) {
        cleanupAndStop()
    }
}
```

- The normal service `onStartCommand` path, including initial start, returns **`START_NOT_STICKY`**. This does **not** request Android to recreate the service after the system kills its process (subject to Android lifecycle conditions). Just changing to `START_STICKY` is **not** a complete capture recovery solution.
- `onStartCommand(null, ...)` stops the service.
- The `allowBackgroundRun` Intent flag in `MainActivity.createLedServiceIntent()` is calculated from an OR of `HeimdallStartupManager.isAutoStartEnabled(prefs)` and `ExternalApiGate.isMasterEnabled(prefs)` — **background persistence is coupled to features unrelated to keeping LEDs on**.
- `onTaskRemoved` may explicitly stop LEDs when the task is dismissed; this explains at least one class of “stops when I close it” reports. It does not establish the root cause of other crashes.
- Service already calls `startForeground(...)` for LED operation, using MediaProjection type when needed, or `specialUse` on supported APIs. **Foreground service exists already**; don't describe the problem as merely lacking an FGS.
- `onCreate` registers battery/screen brightness observers and posts periodic checks. There are multiple transitions, delayed callbacks, and external overrides to simplify.

Owner-supplied public Reddit screenshot described one user losing Bifrost several times daily and commenters recommending **Thor app whitelist, lock-in-Recents, checking permissions**, and in AmbiLuma's case locking it via control panel. These are legitimate *diagnostic/workaround steps*, not proof either app is kill-proof.

### 4.4 Screen capture and dual-screen behavior

From [`MainActivity.kt`](https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/MainActivity.kt):

- Preference key `thor_bottom_screen` controls where the UI launches.
- Separate preference `thor_ambient_bottom_screen` controls Ambient sampling target. `getAmbientTargetDisplayId()` returns secondary display ID if requested and available; otherwise the default display.
- A separate toggle `ambilight_use_media_projection` controls the capture method, with an Accessibility capture confirmation route.
- Changing Ambient display clears cached projection grant data and restarts the effect/service path.

From [`services/LEDService.kt`](https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/services/LEDService.kt):

- Ambient `createAnimation` chooses MediaProjection or accessibility capture based on preferences and mirror mode.
- The code comments explicitly say MediaProjection is used for the **default display**, while the secondary-display mirror route depends on Accessibility capture.
- Multiple deferred `clearMediaProjection()`, `replaceMediaProjection(...)`, and effect restart code paths exist. Reused or invalidated one-time grants and races must be carefully audited under Android 13–16 behavior.
- Capture-display logic must be independent of Activity-display logic. Bottom-screen Activity/dialog lifecycle and bottom-screen capture are **two separate possible failure sources**.

From [`services/BifrostAccessibilityService.kt`](https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/services/BifrostAccessibilityService.kt):

- Accessibility service keeps a static `instance` and watches specific Fallout companion events to track display ID.
- It is not sufficient on its own to explain all screenshot capture logic. Locate the `ScreenAnalyzer` implementation and its capture helper(s) before writing a replacement; file path may not match package import exactly.

From `AndroidManifest.xml`:

- Special activity `.StartupGuideActivity` exists with an explicit warning comment: if the first-run Activity isn't registered, release obfuscation can lead to `ActivityNotFoundException` on launch. This is an example of fragile launch/UI flows; don't confuse it with proof of the owner's particular crash.

### 4.5 Ambient render performance and LED hardware interface

From [`animations/AmbientAnimation.kt`](https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/animations/AmbientAnimation.kt):

- Instantiates `ScreenAnalyzer`; calculates target colors; smoothes left/right independently; applies gamma/brightness scaling.
- Black colors are immediately set to `Color.BLACK` instead of being interpolated; this supports the owner's desire for true-off black behavior, but validate thresholds and final hardware output.
- Rate limit: `now - lastLedUpdateAt < 16` ms returns early; avoid assuming this is ideal for the hardware and gameplay.
- Current implementation makes **two separate `setLedColor(...)` calls per changed sample**, one left, one right.

From [`tools/LedController.kt`](https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/tools/LedController.kt):

- Uses AYN vendor `PServerBinder` (via reflection on Android ServiceManager) for command execution.
- Builds commands writing to `/sys/class/sn3112l/led/brightness` and `/sys/class/sn3112r/led/brightness` with zone selectors `1` and `2`. Avoid independently accessing hidden vendor APIs before verifying the established path on the device.
- Already has **`setLedColorDual(...)`** for single-transaction left/right updates; its comment explicitly mentions reducing IPC/shell overhead under load.
- Candidate **small, measurable optimization**: use `setLedColorDual` in Ambient instead of separate calls, as long as all hardware zone behavior is preserved. Profile before/after.
- Ensure true LED-off values reach the hardware and aren't intercepted by output-minimum scaling.

From [`README.md`](https://github.com/Pollux-MoonBench/Bifrost/blob/main/README.md):

- Default Ambient capture is stated to use **2×1 pixel sampling**. Optional custom sampler uses a **32-pixel grid** to favor vivid colors and reduce pillarbox/letterbox interference.
- There is a risky **Ragnarok** performance mode that attempts overly aggressive LED updates and may cause latency/crashes; do **not** expose similarly unsafe “maximum speed” mode by default.
- The README itself warns that background restrictions and stopping notifications/capture can halt output.

---

## 5. What to learn from DuoFrost without copying its complexity

Reference docs:

- [DuoFrost README](https://github.com/Tufein/DuoFrost/blob/main/README.md)
- [Changelog](https://github.com/Tufein/DuoFrost/blob/main/CHANGELOG.md)
- [2.5 User Guide](https://github.com/Tufein/DuoFrost/blob/main/docs/2.5.0.md)

**Worth investigating/possibly extracting:**

- Service surviving UI dismissal and Clear All (look for independent `onTaskRemoved`/process restart handling).
- Foreground notification and Quick Settings tile with clear, synchronized Start/Stop state.
- LED hardware test: each ring/zone + primary colors; restore previous effect after test.
- Capture failure: no runaway retry loop; ignore callbacks from stale sessions; reason/status exposed.
- Combined left/right hardware writes for audio and Ambient.
- Output ceiling, optional battery saver, screen-off dimming, temporary mute without losing effect.
- Explicit diagnostic report of why lighting stopped.

**Avoid porting:**

- Smart Scenes and nested precedence rules for app/time/day/battery/charging.
- Timed overrides/holds and complex cross-priority machinery.
- Huge preset catalogs, favorite collections, artwork, theme engines, preview systems and import-review undo.
- Live wallpapers and plugin-driven game integrations as first-class complexity.
- Unnecessary repeated settings reads / UI refresh loops.

DuoFrost changelog is developer-authored; treat its claims of improvement as leads to test, not proof. The user observed its latest bottom-display crash regardless of stated release fix.

---

## 6. AmbiLuma background investigation (important early experiment)

Because AmbiLuma is **closed source**, do **not** assert it uses a particular restart policy, background service technique, or screen sampling algorithm without proof. Use it as an *empirical comparator*.

### Test procedure, one controller at a time

1. Stop/disable both other LED-control apps. Enable the same approximate brightness and mode across apps. Check Thor's **app whitelist**, Recents lock, battery restrictions and notification permissions. Record them for each app.
2. Launch AmbiLuma Ambient and inspect service state. Repeat with Bifrost and DuoFrost individually.
3. Record which services are foreground, active process state, notification state, task removal behavior, capture prompt behavior, post-sleep outcome.
4. Test: UI back/home, swipe away in Recents, Thor memory-cleaner button, turn top/bottom display off, sleep/wake, 30–60 minute gaming session, rotate/move Activity, explicit Force Stop. Distinguish user-action terminations from Android memory pressure.
5. Compare *actual* behavior and logs, then decide what to adapt from Bifrost/DuoFrost. No assumption that the closed-source app can bypass Android capture consent.

Useful read-only commands (adapt serial/package as appropriate):

```bash
adb devices
adb shell dumpsys activity services com.bitmonkeygames.ambiodin
adb shell dumpsys activity services com.moonbench.bifrost
adb shell dumpsys package com.bitmonkeygames.ambiodin
adb shell dumpsys package com.moonbench.bifrost
adb shell dumpsys activity processes
adb shell dumpsys power
adb logcat -b crash -v threadtime
```

For a focused reproducible crash, collect a bounded timestamped `adb logcat -v threadtime` file and filter process/tag while preserving crash traces. If the app is not running, `dumpsys activity services` may reveal nothing; **absence from that output does not prove it never uses a service**. APK manifest/package inspection may clarify declared components but does not prove actual lifecycle behavior.

**Android constraint:** a sticky service may be recreated after an eligible system kill, but a killed/invalidated MediaProjection session often needs **fresh consent**. Force Stop disables normal automatic restart until explicit user action. Avoid “always alive” promises.

Useful platform docs:

- [Android foreground services](https://developer.android.com/develop/background-work/services/fgs)
- [MediaProjection](https://developer.android.com/media/grow/media-projection)
- [Service.START_STICKY](https://developer.android.com/reference/android/app/Service#START_STICKY)
- [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)

---

## 7. Feature scope — proposed, not yet signed off

### 7.1 Must-have for first quality release

- **Effect modes:** Ambient, Static, Rainbow, Breath, Pulse, Fade, Audio Reactive, Ambi Aurora. Preserve other existing simple effects cheaply if reliable; don't burn time rebuilding them when Ambient crashes.
- **Two independent RGB stick outputs**; optionally a single shared color.
- **Ambient source:** explicit top/bottom target, with honest fallback/restriction status for Android capture methods.
- **Ambient controls:** overall brightness, smoothing/response, saturation, optional vivid/black-bar-aware sampling, black cutoff/true off. Expose advanced sampler controls only when needed.
- **Battery indicator** and optionally low-battery alert as first-class useful effects/status.
- **Service:** independent lifecycle, predictable Start/Stop, foreground notification, Quick Settings tile, persists on UI dismissal/normal task removal.
- **Low-overhead hardware output:** serialize and batch left/right updates; only update on change; static mode should be largely event-driven.
- **Restores last selected look/settings** across app UI relaunch.
- **Clear capture permission UX** and failure/status indicator without silent freeze or crash.
- **Basic self-test/diagnostics:** left/right ring, colors, active status, capture status, reason for stop.

### 7.2 Nice-to-have later, only after the core is stable

- A *small* list of favorite looks (not a preset library/catalog).
- Simple single-app → effect assignment (not complex Smart Scenes; optional Usage Access).
- Optional boot start (respect Android consent restrictions for capture).
- Screen-off dimming / battery-saver output limit.
- Temporary mute and maybe a very simple sleep timer if justified by usage.
- Import/export of basic settings for migration from Bifrost, after stable schema has been designed.
- CPU temperature effect if hardware access is reliable.

### 7.3 Excluded from initial implementation

- Complex schedules/cron/daypart rules and calendar automation.
- Plugins and plugin store; external API integrations and Fallout companion special cases unless the owner explicitly requests them.
- Smart Scenes priority arbitration; collections/search for large preset libraries.
- Preset artwork uploads, theme packs, live wallpapers, elaborate undo and backup workflows.
- Unsafe hardware update modes (e.g., Ragnarok) and options that encourage unstable write rates.
- Broad support promises for untested non-Thor devices.

**Important nuance:** eliminating an old code module may require extracting one small shared utility from it first. Remove dead dependencies with tests; don't gut the working LED binder protocol just for cleanliness.

---

## 8. Suggested technical architecture

Aim for a **small number of clearly owned components**. These names are illustrative, not implementation mandates.

```text
Activity / Settings UI
  └── LightingController API (StateFlow / Binder / explicit intents)
       └── LightingForegroundService  (only owner of active lighting state)
            ├── EffectEngine
            │    ├── Static / Rainbow / Breath / Pulse / Fade / Battery ...
            │    └── AmbientEffect / AudioEffect
            ├── CaptureManager
            │    ├── MediaProjection source (default screen, consent-managed)
            │    └── Accessibility screenshot source (device-supported display ID)
            ├── ColorSampler (pure/testable transformations)
            │    ├── Left/right region selection
            │    ├── Letterbox/black-bar handling
            │    ├── Brightness, saturation, smoothing, deadbands
            │    └── True-black / loss-of-frame behavior
            ├── LedHardwareDriver
            │    └── Existing AYN PServerBinder + batched zone commands
            ├── Preferences / last-used effect
            └── State/diagnostics (RUNNING, WAITING_FOR_PERMISSION, PAUSED, ERROR, STOPPED)
```

### Implementation practices

- **One active effect** owns the LED output at a time. Avoid independent effects writing concurrently; all hardware writes go through one serialized driver path.
- Use appropriate off-UI-thread processing for capture/pixel calculation; avoid a busy loop. A single background thread is a *preference for simplicity*, not a hard rule if platform callbacks require specific executors.
- Capture resources have a clearly defined lifecycle: start, frame callback, stop, cleanup, reconsent on invalidation. Ignore callbacks from old sessions via a generation token or equivalent.
- Avoid blindly restarting the whole service on each parameter edit. Render/effect parameter changes should be applied live when possible.
- For constant/static output, write once at activation/change; don't run a 60 Hz loop.
- Dynamic refresh: start conservatively at **~20–30 LED updates/s** and benchmark visual latency vs game performance before choosing a value. Capture frame rate and LED write rate needn't match exactly.
- **No duplicate writes:** compare final colors, optionally use a perceptual deadband/hysteresis after smoothing to avoid small flickers and noisy hardware updates.
- For black content: ensure black is sent as zero RGB to **both zones of the relevant sticks**, with an appropriate near-black threshold/hysteresis and test against dark game scenes. Distinguish black pixels from unavailable/invalid capture data.
- Frame loss should enter a controlled state rather than looping stale or random colors. Define whether stale colors fade to off after a short timeout.
- Treat screen target selection as **configuration on CaptureManager**, separate from Activity positioning.
- Log state transitions with bounded, non-sensitive diagnostics. **Never persist screenshots or record video/audio content**.
- Keep Android permissions minimal and mode-specific: static/rainbow should not need capture permissions, Internet, Usage Access, or broad Accessibility rights.
- Respect Accessibility API and user consent; prefer MediaProjection for supported default-display capture, only use Accessibility if required for bottom-display capture and demonstrably reliable.

### Service and recovery design decisions to confirm with tests

1. Change from `START_NOT_STICKY` to an appropriate policy **only when** background persistence is the intended mode. Persist effect choice and explicit enabled/disabled state.
2. Closing/dismissing the Activity **must never implicitly call `cleanupAndStop`**. Only explicit Stop should set stopped state.
3. A legitimately recreated service may reapply non-capture effects from saved state; **capture effects must wait for valid fresh consent** when needed.
4. Don't use `START_STICKY` to create an endless restart loop after a deterministic crash. Bound retries and surface errors.
5. Use Android 13+ foreground-service requirements/notification types correctly and test on the Thor's OS. Be careful with Android restrictions on starting FGS from the background.
6. Thor's vendor memory cleaner may kill whitelisted/background processes differently from standard Android. Document app whitelist as a **recommended one-time setup**, not a substitute for correct lifecycle handling.

---

## 9. Recommended UI/UX

### Main screen: direct and lightweight

- Clear **ON/OFF** state and active effect.
- A simple diagram/preview of left and right LED rings (not necessarily physically accurate), displaying current desired colors.
- Compact effect picker, grouped e.g. **Screen**, **Audio**, **Colors**, **Status**.
- Context-sensitive sliders: brightness globally; smoothness/saturation only for Ambient; speed for animation; audio sensitivity for Audio.
- Obvious **Top / Bottom** source selector only when applicable.
- Settings update immediately. No requirement to create/edit/save a preset first.
- One **Advanced** screen for sampling method, color cutoffs, background/battery options, permission diagnostics and LED test.
- Consistent layout on both Thor displays without display-specific popup crashes.

### Onboarding

1. Explain in one sentence what the app controls.
2. Let user choose any no-permission effect immediately.
3. Request screen capture when entering Ambient (and audio capture as needed), not at first app launch.
4. If bottom screen requires Accessibility capture, explain **why** and request permission only then; do not quietly switch between capture methods.
5. Offer one-time instructions for adding the app to AYN Thor whitelist and unrestricted battery settings where appropriate.
6. Allow user to close UI while service continues. Quick Settings and notification allow Start/Stop.

### Error UX

State should be honest and actionable: **Running**, **Waiting for capture permission**, **Capture lost — tap to reconnect**, **Paused by user**, **Stopped**, **Hardware unavailable**, etc. Do not label LEDs “running” solely because a service object exists.

---

## 10. Proposed phased work plan for Codex

### Phase 0 — Baseline and diagnosis (**do first**)

1. Clone Bifrost source at pinned commit `1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa`. Record build environment, toolchain and dependencies; ensure debug APK builds unchanged.
2. Determine the owner's Thor firmware/Android version and source-display behavior. Confirm no two LED controllers run together.
3. Reproduce original Bifrost launch/capture/background bugs on **both screens** with `adb logcat` and `dumpsys`; label stack traces by root cause.
4. Reproduce DuoFrost bottom-screen crash and identify its workaround setting; compare issue/fix notes but don't inherit DuoFrost automatically.
5. Measure baseline: capture CPU usage, memory, LED command frequency, per-frame allocations, responsiveness and game stutter on a fixed title/screen scenario.
6. Test AmbiLuma using ADB service/process diagnostics under identical scenarios (see §6). **Document evidence, don't speculate.**
7. Build an issue matrix: symptom → reproduction → stacktrace → cause hypothesis → test result. Prioritize reproducible crashes and service stoppages.

**Deliverables:** `docs/baseline.md`, ADB reproduction steps, logs redacted of personal/device secrets, risk list. No large refactor before this exists.

### Phase 1 — Stable LED engine and service lifecycle

1. Separate UI/engine/output ownership and simplify the service without dropping working LED functionality.
2. Remove coupling between background persistence and auto-start/external API. Explicit Start/Stop only.
3. Properly manage FGS, task removal, restart eligibility, saved effect/intent and notification state.
4. Centralize hardware writes and prove `setLedColorDual` equivalence with left/right/zone tests.
5. Implement a predictable effect state machine and diagnostic failure states.
6. Implement Static/Rainbow/Breath and other no-capture effects; confirm UI dismissal, Recents behavior, boot policy and long session stability.

**Release gate:** no-capture effects run with UI closed reliably; explicit Stop remains off; no hidden high-CPU loop.

### Phase 2 — Ambient capture and color fidelity (**flagship**)

1. Locate and examine Bifrost `ScreenAnalyzer`/capture helper(s) and DuoFrost's targeted capture fixes.
2. Build distinct MediaProjection and Accessibility capture paths as appropriate for top/bottom display.
3. Make first-frame, invalid-frame, app switch, display transition and stopped-projection behavior deterministic.
4. Implement/validate 2×1 and low-res custom sampling; correct split left/right, pillarbox handling, color saturation and gamma.
5. Implement true-black cutoff and separate 'no valid frame' handling; tune smoothing/hysteresis.
6. Ensure permission loss is recoverable through a clear user action, not silent background failure.
7. Benchmark effect smoothness/latency and hardware-write overhead on game content.

**Release gate:** 2-hour Ambient session passes on selected screen; no crash moving Activity; capture can recover cleanly after expected interruptions; visual correctness validated with curated image/video test frames.

### Phase 3 — Complete effects, polish and UX

1. Bring over remaining useful Bifrost effects; verify each has no unwanted permissions/capture dependency.
2. Implement direct UI controls and top/bottom display layout without complicated preset requirement.
3. Add battery indicator, Quick Settings tile, LED test, diagnostic status and last-used effect.
4. Test permissions/onboarding, Android background restrictions and setting retention.
5. Review for dead modules/dependencies/permissions, kill old scheduling/plugin/wallpaper subsystems if no longer referenced.
6. Review accessibility/privacy/legal requirements. Ensure build/release path signs APK with user's own keys and preserves GPL notices.

**Release gate:** a first-time user can use a static effect immediately; Ambient setup is understandable; error messages are actionable; there are no large orphaned modules.

### Phase 4 — Optional improvements after stability

Simple favorite looks, per-app mapping (if actually requested), output saver and basic configuration export. Do not rush these into v1.

---

## 11. Concrete test matrix / acceptance criteria

Create and maintain a table of actual results (PASS/FAIL/BLOCKED, firmware/build, logs, duration). Do not mark unrun tests PASS.

| Scenario | Expected behavior |
|---|---|
| Launch Activity on top screen | No exception; LED controls usable |
| Launch Activity on bottom screen | No crash/dialog leak; effect controllable |
| Move app UI top ↔ bottom | Controls survive recreation; running LED effect persists |
| Start Ambient from top | Correct intended top content, independent L/R colors |
| Start Ambient from bottom | Supported documented behavior or clear warning, no crash |
| Use bottom screen while top game runs | Top remains correct source if selected |
| Solid red/green/blue/black/gray test screens | Accurate colors, black truly off, no hue contamination |
| Mixed-color left/right test frame | Left/right stick colors assigned correctly |
| Pillarbox/letterbox content | Does not turn everything off or misread black bars |
| Rapid scene transitions | Responsive yet not flickering wildly |
| Activity Home/Back/dismissed | Lighting continues if user didn't press Stop |
| Clear All / AYN memory-clean button | Document behavior and whitelist sensitivity; no silent false-positive “running” |
| Lock/sleep/wake | No crash; correctly recover or request fresh capture permission |
| System revokes capture/other recorder takes over | Cleanly detach/reconnect via user consent; no busy retry loop |
| Explicit Stop then reboot | Stays off unless user explicitly enabled optional boot-start |
| Static effect 2 hours | Minimal CPU/writes; never requires Activity to remain open |
| Ambient effect 2 hours under gaming workload | No crash, leakage, accumulating work or visible game stutters |
| Change brightness/smoothness live | Update without tearing down valid projection unnecessarily |
| Two LED-control apps installed | Running only one at a time; no competing hardware writes |
| Vendor LED binder unavailable | Graceful error; no endless restart or fake success |

Performance instrumentation should report **actual** CPU %, memory, approximate writes/s, capture rate, stability duration, and game frame-time changes under controlled conditions. No arbitrary “zero overhead” claim.

---

## 12. Risks, non-goals, and explicit caveats

- **Android screen-recording consent cannot be bypassed.** A fresh MediaProjection session may need new user approval; do not persist or reuse an invalid consent token.
- Some vendor-specific AYN APIs or display behavior may change with firmware. Verify on the target device; avoid requiring root if existing Bifrost binder path works.
- Avoid promising automatic service resurrection after Force Stop / user-kill / severe vendor memory cleanup. Persist honest state and restore when permitted.
- Do not assume the original bottom-screen crash has the same root cause as background stopping or Ambient sampling defects.
- The Bifrost / DuoFrost source snapshots were inspected selectively, not fully audited. Further work must explore actual code dependencies/tests before deletion.
- AmbiLuma's proprietary internals and exact complete feature list remain **unknown**; don't present guesses as factual.
- Community usage ranking is **qualitative**, not survey-backed. Keep feedback loop open.
- **GPLv3 derivative:** preserve notices, credit original contributors, and follow source availability conditions when publishing modified binaries. No proprietary relicensing of copied code.
- Don't send crash logs, screenshots, media samples or device info to third-party analytics by default. Prefer fully local processing.

---

## 13. Naming / branding note (latest conversation)

The owner asked for a shorter, cleaner name for a simplified fork. Ideas discussed included **Glimr, Prism, Rune, Gleam, Eira, Hald, Runelight**. Initially **Glimr** seemed attractive, but subsequent public search found:

- Existing **Glimr** apps in mobile stores (including a dating app).
- Existing **[Glimmr](https://github.com/d8ahazard/glimmr)** open-source **ambient-lighting** software — especially close in category and spelling.

Therefore **do not settle on Glimr** without reconsideration. **Prism** and **Runelight** are only provisional candidates; validate naming conflicts and trademarks before publishing. Keep technical code names generic, configure package ID later.

---

## 14. Suggested Codex kickoff prompt

> Work on a lean, reliable AYN Thor LED controller derived from GPLv3 Bifrost. First read `ayn_thor_led_codex_handoff.md`. Pin the upstream baseline and inspect current source modules and Android lifecycle/capture behavior. **Start with diagnosis, dependency map and a minimal technical plan**; do not immediately rewrite the UI or adopt DuoFrost. Treat user-described crashes on the Thor Max top/bottom displays and unreliable Ambient/background behavior as the priority. Preserve Bifrost's useful effects. Architect a small independent foreground service, serialized batched LED driver, consent-aware capture manager and direct simple UI, removing scheduling, plugin, Smart Scenes and unneeded artwork/theme complexity. Keep feature parity checks and GPL attribution. Distinguish source-confirmed facts, test observations and hypotheses. Propose the smallest verifiable Phase 1 changes, build/test them, and report commands, results, unresolved device-specific questions and next steps.

---

## 15. Source and reference index

**Open-source upstream (direct original sources):**

- Bifrost repository: <https://github.com/Pollux-MoonBench/Bifrost>
- Bifrost README: <https://github.com/Pollux-MoonBench/Bifrost/blob/main/README.md>
- Bifrost license: <https://github.com/Pollux-MoonBench/Bifrost/blob/main/LICENSE>
- Bifrost release baseline: <https://github.com/Pollux-MoonBench/Bifrost/commit/1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa>
- Bifrost service: <https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/services/LEDService.kt>
- Bifrost Activity: <https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/MainActivity.kt>
- Bifrost Ambient animation: <https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/animations/AmbientAnimation.kt>
- Bifrost LED controller: <https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/tools/LedController.kt>
- Bifrost accessibility: <https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/services/BifrostAccessibilityService.kt>
- Bifrost effect types: <https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/java/com/moonbench/bifrost/animations/LedAnimationType.kt>
- Bifrost Android manifest: <https://github.com/Pollux-MoonBench/Bifrost/blob/main/app/src/main/AndroidManifest.xml>
- DuoFrost repository: <https://github.com/Tufein/DuoFrost>
- DuoFrost README: <https://github.com/Tufein/DuoFrost/blob/main/README.md>
- DuoFrost changelog: <https://github.com/Tufein/DuoFrost/blob/main/CHANGELOG.md>
- DuoFrost detailed guide: <https://github.com/Tufein/DuoFrost/blob/main/docs/2.5.0.md>

**Closed-source comparator / community:**

- AmbiLuma: <https://ambiluma.com/>
- AmbiLuma Play Store: <https://play.google.com/store/apps/details?id=com.bitmonkeygames.ambiodin>
- r/AynThor: <https://www.reddit.com/r/AynThor/>
- Bifrost archive/farewell discussion: <https://www.reddit.com/r/AynThor/comments/1wnglc0/so_long_bifrost/>

**Android platform documents:**

- Android foreground services: <https://developer.android.com/develop/background-work/services/fgs>
- MediaProjection: <https://developer.android.com/media/grow/media-projection>
- Android Service reference: <https://developer.android.com/reference/android/app/Service>
- AccessibilityService: <https://developer.android.com/reference/android/accessibilityservice/AccessibilityService>

**Naming conflicts:**

- Existing Glimmr ambient-lighting codebase: <https://github.com/d8ahazard/glimmr>
- Existing Glimr Google Play app: <https://play.google.com/store/apps/details?id=com.infilnt.glimr>

---

**Bottom line:** Build a small, durable lighting engine with an excellent Ambient mode and several valuable effects, wrapped in an unusually clear UI. Validate lifecycle and capture reliability with ADB on a real Thor Max. Keep the user-facing controls easy enough that people never need to learn a rule engine or manage dozens of presets. **Diagnose first, change minimally, test on-device, then polish.**
