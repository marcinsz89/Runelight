# Sound latency investigation and output correction — 2026-10-21

The owner reported prompt Ambient with sound Off, but audio/LED misalignment and
apparently growing delay with sound On during live content. The first sound test
had a still red/blue picture and long tones; it proved color/intensity behavior,
not combined changing-video throughput or synchronization.

## Confirmed output failure mechanism

Audio changed master brightness every 25 ms while Ambient independently advanced
color smoothing every 33 ms. There was no combined output cadence. The driver
submitted every changed frame as a one-way vendor Binder command. The vendor
executes a shell command per four-zone frame, so accepted submissions could
accumulate faster than the service completed them.

The resumed, ordered diagnostic puts execution timestamps in the same one-way
stream as the tested frames. One second at 40 offered frames/s completed in
993.7 ms; one second at 70 offered frames/s took **1430.0 ms**. This confirms
backlog at a rate the old combined pipeline could approach. It explains why a
still-picture tone test can pass while live content falls behind, but does not
measure every game's exact input/output delay.

Earlier synchronous sentinels returned quickly because they did not establish
ordered completion of the preceding one-way work. Their 32/30 ms timings were
inconclusive, not evidence against backlog. An initial marker read failed because
the vendor creates root-only files; the repeated ordered test explicitly made
only those timestamp files readable. No system permission settings were changed.
Vendor binary/disassembly and raw device evidence remain ignored and local.

## Implementation

- `LatestValueWriter` keeps at most one blocking write and one replaceable latest
  frame, with a 40 ms minimum start interval: at most 25 submissions/s.
- `VendorLedOutput` owns one process-wide worker across controller lifetimes.
  It waits for synchronous vendor transaction completion off the service/UI
  thread. The driver no longer moves a FIFO of stale frames into the vendor.
- Color and sound update complete snapshots of the same four-zone state. Stop
  replaces pending colors with black; a later explicit Start may supersede that
  pending Stop. Closed drivers cannot submit new work or report stale completion.
- Frame completion remains associated with its effect. Identical pending frames
  can acknowledge a new effect after the existing write completes. Continuous
  new targets do not starve startup acknowledgement, and returning to an earlier
  color while another is in flight is not suppressed as already displayed.
- Static output still becomes idle. There is no output retry loop or idle polling.
- Audio buffering, 25 ms polling, envelope gain/attack/release and Ambient sampling
  were left unchanged to isolate the output correction.

Vendor transaction completion is not calibrated physical LED acknowledgement.
A stuck/dead vendor service remains a hardware/backend failure, not something
coalescing can cure. Long gameplay and physical timing acceptance remain needed.

## Validation

- `./scripts/dev.sh unit :app:assembleDebug :app:assembleDebugAndroidTest --offline`:
  **98 JVM tests pass**, debug and matching test assembly pass.
- Installed both APKs on the owner's Thor Max / Android 13.
- **27 Android tests pass** across LedOutputBackpressureTest, AnimationOutputTest,
  AmbientOutputTest, LightingServiceLifecycleTest, PlaybackAudioCaptureTest and
  RunelightActivityTest. New tests use a deliberately blocked local Binder to
  check coalescing, Stop/replacement, callback ownership and return-to-old-color.
- The explicitly opted-in hardware probe offers **70 frames/s for 20 seconds**
  through the actual production dispatcher/driver. It completed 497 selected
  frames, with no failed transactions. Median offer-to-command-completion times
  in successive five-second buckets: **32.5, 33, 32, 33 ms**; maxima 44, 43, 43,
  46 ms. The final frame completed in 55 ms. No increasing output age appeared.
- `./scripts/dev.sh release --offline`: **pass**, including shrinking/vital lint.
  A broader build also ran full lint: only the same two inherited errors remain
  (AudioAnalyzer and BifrostTileService; 409 warnings). Full lint is not a pass.
- Native Ambient + sound is being checked with a separate-UID fixture combining
  continuously changing hues and 100 ms tones every 500 ms. Its visual beat uses
  playback-head position. After replaying it, the owner reports that audible
  sound and the physical LEDs align, and colors are correct, with a small
  acceptable color lag. This is an owner-observed pass, not calibrated timing.

## Faster color response follow-up

The owner requested reducing the remaining color lag. The follow-up changes the
minimum capture sample interval from 100 to 40 ms, color smoothing's time constant
from 110 to 35 ms, and its active render tick from 33 to 16 ms. The grid remains
64 pixels on its longest axis. Output still goes through the same shared 25 Hz
writer; faster capture/math cannot create a vendor FIFO. Settled held colors do
not schedule redundant render ticks, and true black still converges to exact zero.

A pure response-budget regression checks that a color step reaches at least
240/255 within 100 ms of its sampled target, without snapping immediately, and
still fades to exact black. This measures the filter, not end-to-end hardware
latency. Zero delay is not promised: capture scheduling, the output cadence and
vendor execution remain. The follow-up passes 99 JVM tests, debug/test/release
assembly and seven focused Thor color/capture/writer/audio regressions. The owner
then confirmed that the faster-color build works well. This is qualitative
physical acceptance; no end-to-end millisecond claim is made.

Local evidence: ignored `local-test-results/2026-10-21-sound-latency/`, especially
`ordered-readable-timing.json`, `paced-output-summary.json`,
`paced-output-timing.json`, `writer-device-regressions.txt` and `writer-release.txt`.
The explicit vendor timing tests skip ordinary runs unless
`runelightVendorTiming=true` is passed. They use modest LED levels and finish with
black; marker files from successful runs are removed after collection.

## Remaining acceptance

Confirm representative live game/audio output, repeat sufficiently long to
detect drift, and test actual
Pathfinder plus sleep/wake during Ambient + sound. If a steady audio delay remains,
measure PCM age and playback timing before changing buffering or attack/release.
The intermittent white output after idle OLED cleanup is a separate open issue;
the owner subsequently observed correct recovery, and no OLED fix is claimed.

The owner approved the current result and requested a local commit. Regression
tests and bounded debug/test fixtures are retained; release manifest, R8 mapping
and DEX checks confirm that none of these probe/test classes ship in the release
APK. Raw diagnostics, vendor binaries and build artifacts remain ignored locally.
No dependency update, UI-layout change or push is included.
