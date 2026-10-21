# Playback sound overlay — 2026-10-21

Follows the [top Ambient increment](ambient-implementation-2026-10-21.md).
The owner's revised scope supports sound on Ambient, Solid and Rainbow; Breathing
and Pulse suspend sound while preserving its saved On/Off preference.

## Ownership and behavior

- `runelight_controls.soundEnabled` is the saved preference. The service owns the
  actual recorder, envelope and `SoundState`; saved On is not a recorder object.
  Unsupported modes disable/dim the sound tile, retaining its On label when armed.
  Returning to a supported mode resumes with existing valid consent/permission.
- The service owns one `TopScreenCapture` projection session. Ambient borrows a
  video lease; audio uses the same projection. Mode changes detach video's surface
  and close its reader while preserving the virtual display for later reuse.
  Android 14+ therefore needs no second `createVirtualDisplay()` call per grant.
- Switching sound off on Ambient releases audio only. On plain effects it also
  releases unused capture. While sound remains wanted during Breathing/Pulse, the
  recorder is released and brightness returns to the normal effect; the consented
  projection stays valid until Stop/revocation so returning does not prompt again.
  The OS may still show the retained capture authorization during this pause.
- Starting Ambient or sound after consent loss requests fresh consent in context.
  Sound additionally requests RECORD_AUDIO. No microphone input, Accessibility,
  automatic permission bypass, persisted grant or overlapping session is used.
- Explicit Stop invalidates pending permission/consent tickets before resource
  teardown. UI recreation/parameter sync never starts lighting. Sound can be armed
  while lighting is stopped; permission approval alone must not start the engine.
- Plain effects survive sound failure with normal brightness and an actionable
  retry/permission message. Ambient capture loss still clears/stops lighting.
  A failed recorder does not retry on brightness edits; toggle off/on to retry.

## Processing and timing

The existing serialized driver's master intensity applies the envelope after
selected-effect color/animation and independent side levels. It neither replaces
Solid/Rainbow/Ambient nor resets their animation phase/source. Black remains zero.

Playback uses AudioPlaybackCaptureConfiguration (media/game/unknown usages),
16 kHz mono PCM and nonblocking reads on the service's main looper every 25 ms.
Zero reads are valid temporarily; five seconds without PCM or any negative read
ends the session. Silence is valid zero-valued PCM, not a capture failure. Close
cancels polling and releases once; no blocking read, urgent retry loop or join.
Recorder/level callbacks carry a separate audio epoch across pause/Stop/replacement.

Initial fixed envelope: RMS, 0.003 noise floor, gain 6, 35 ms attack and 180 ms
release, snapping below 0.001. This adds no configuration screen. The planned
sensitivity control and real-game tuning remain follow-up acceptance work.

The subsequent [latency correction](sound-latency-2026-10-21.md) combines effect
and sound output in one latest-frame writer, capped at 25 submissions/s. It waits
for each vendor transaction on one process-wide worker, replacing the pending
frame rather than accumulating obsolete brightness/color commands. The capture
and audio smoothing rates above are unchanged.

Android's [playback capture requirements](https://developer.android.com/media/platform/av-capture)
still apply: source policy may prohibit capture, and some games can legitimately
produce silence. No microphone fallback is provided.

## Validation

- `./scripts/dev.sh unit --offline`: **94 JVM tests pass**, including envelope,
  silence, supported modes and bounded read-health regressions.
- `./scripts/dev.sh build :app:assembleDebugAndroidTest --offline`: **pass**;
  `./scripts/dev.sh release --offline`: **pass**, including shrinking/vital lint.
- `./scripts/dev.sh build :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug --offline`:
  assemblies pass; full lint fails on the two inherited errors in AudioAnalyzer
  and BifrostTileService. No new lint error remains; this is not a full-lint pass.
- Installed debug and matching test APKs on the owner's Thor Max / Android 13.
  **Eight focused Android tests pass**: two recorder failure/close tests,
  saved-On pause/recreation, sampling recreation, Ambient output/late callbacks,
  rapid Stop/Start, stale-consent rejection and plain-effect handoff. The first
  run caught a missing sound-tile refresh when selecting an effect while stopped;
  that refresh is fixed, and all eight passed on the rebuilt APK.
- Controlled separate-UID game-usage playback: the owner confirms physical red
  left/blue right, quieter/louder intensity and fully dark silence with Ambient.
  After Breathing → Pulse → Solid → Rainbow → Ambient, the owner confirms a
  changed blue-left/red-right scene and continuing sound reaction. This checks
  actual video reattachment rather than a retained old color alone.
- Aggregate device reports show zero active recorders in Breathing/Pulse, saved
  sound On throughout, one recorder on returning to supported modes, and the
  same virtual display through the tested round trip. Sound off/on on Ambient
  preserves that display and releases/recreates only the recorder. Sound Off on
  Solid releases projection while the native UI still reports Lighting On.
- Explicit Stop releases projection and leaves no active recorder. Canceling a
  fresh sound-enabled Solid start leaves Lighting stopped with saved sound On.
  A subsequent accepted start has one recorder and no Ambient virtual display.
  Canceling sound consent while Solid is already running leaves Lighting On,
  sound Paused and a reconnect action. Early interactive cancellation attempts
  overlapped owner input and were excluded; these passes were repeated with
  the owner leaving the controls untouched. Runtime audio-permission denial and
  system revocation remain untested.
- Closed the bounded playback fixtures, restored the owner's pre-test control
  settings byte-for-byte and left native controls on the lower panel with
  Lighting stopped. At that stage no commit, stash or push was performed; the
  later accepted sound/latency result is being committed at the owner's request.

The owner reported one white Ambient output that stayed white after the idle
OLED noise cleanup, then observed another cleanup recover its scene colors
correctly. This intermittent issue is **unresolved**; no capture/color fix is
claimed. Record source frames/output timing during a repeat before adding a
recovery workaround. Repeated OLED cleanup cycles are required acceptance.

The owner then reported growing audio/LED lag with sound On during live content.
The [timing investigation and correction](sound-latency-2026-10-21.md) confirms
vendor output backlog and replaces it with a paced latest-frame writer. The owner
subsequently reports aligned sound/LED pulses and correct colors on the changing
picture/beat fixture. Live-game and long-session acceptance remain pending.

Remaining acceptance:
representative game/audio policy, actual Pathfinder while audio/video are active,
revocation/permission denial, individual panel/sleep behavior, Android 14+ and long
performance sessions. Controlled tone playback does not establish every game's
capturability. Raw device evidence stays ignored and local.
