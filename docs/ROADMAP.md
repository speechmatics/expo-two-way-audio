# Roadmap

This file is the status carrier for `@equationalapplications/expo-two-way-audio`.
It says what gets built, in what order, what each step waits on, and what has
actually shipped under it. The rules the work must keep are in
[INTENT.md](../INTENT.md), which this file never overrides.

Steps are ordered by gates, not dates. A gate is a condition that must be true
before the step can start. Each step says why it sits where it does and cites
its source: an issue number in the upstream repository
([speechmatics/expo-two-way-audio](https://github.com/speechmatics/expo-two-way-audio/issues),
written `upstream #N`) or a fact about this repository.

## Promise classes

Every step not yet started carries one of these classes instead of a progress
mark:

- **BINDING**: ruled or promised. The project has committed to it, and
  [INTENT.md](../INTENT.md) or a maintainer decision requires it.
- **PLANNED**: designed or intended, but not built. The project means to do it
  and knows roughly how. It is not shipped.
- **EXPLORATORY**: a candidate. Not designed, not committed. It may be dropped.

When a step ships, its class is replaced by a dated, past-tense progress mark
in the form `Done — merged YYYY-MM-DD, PR #N` or `Done — verified YYYY-MM-DD on
<device>`. There are no forecasts in this file.

## Where things stand

Facts that already hold, stated here so the steps below make sense:

- The fork's first release, 0.2.0, and a docs-only follow-up, 0.2.1, are on
  npm and tagged `v0.2.0` and `v0.2.1`. Their contents are listed in
  [CHANGELOG.md](../CHANGELOG.md).
- 0.2.0 was published by hand, without provenance. 0.2.1 was published by the
  tag-triggered workflow with provenance. That workflow is the release process
  from now on.
- The fork has shipped fixes for iOS builds on Expo SDK 55+, the iOS
  duplicate-audio-tap crash, Android playback thread safety and repeatable
  teardown, and interruption recovery. Of these, the Android playback and echo
  cancellation behaviour has not been verified on a physical device by the
  maintainers against the specific upstream reports below. The steps in lane 1
  exist to close that gap.
- Upstream stopped receiving changes in January 2025. On 2026-11-11 the
  maintainer opened upstream #18 asking Speechmatics to point users at this
  fork. No response has been received as of that date.
- No step below has shipped yet, so no step carries a progress mark.

## Standing items

Timeless disciplines that apply to every step and never close.

### S1. Native changes get a physical-device check before merge — BINDING

Any change under `ios/` or `android/` is run on at least one real phone per
platform touched, and the pull request names the devices. Simulators do not
count.
*Why here:* continuous integration only runs the TypeScript (`ci.yml`:
format, typecheck, build, pack). Echo cancellation, microphone modes and
playback timing do not work on simulators. Source: CONTRIBUTING.md and
`.github/workflows/ci.yml` in this repository; INTENT.md rule 7.

### S2. Releases go through the tag recipe — BINDING

Version-bump pull request with a CHANGELOG section, merge, push a signed tag
`v<version>` that matches `package.json`. The publish workflow does the rest.
*Why here:* the recipe has been run end to end (0.2.1) and gives every release
a provenance record. Source: `.github/workflows/publish.yml`; INTENT.md rule
11.

### S3. Public claims match device evidence — BINDING

The README and CHANGELOG describe a native fix as fixed only when someone has
heard it work on a device. Until then the wording says what was changed and
that it is unverified.
*Why here:* two upstream reports (#9, #12) are plausibly addressed by shipped
code but have not been verified, and the fork's claims are public. Source:
upstream #9 and #12; INTENT.md rule 7.

## Lane 1 — Verify the fixes the fork already claims

**Gate:** a maintainer has an Android 13 or newer phone and an iPhone to hand,
with a development build of a test app.

Correctness and verification come before any new feature. The fork has shipped
code that should resolve two upstream reports, and the only honest next step is
to find out whether it does.

### 1.1 Verify Android echo cancellation and playback start on a device — PLANNED

Run the test app on a physical Android 13 or newer device. Check whether echo
cancellation is complete and whether playback starts without crackling. Record
the device, Android version and result. If the report still reproduces, open an
issue in this repository with the recording and treat it as a bug, not a
feature.
*Why here:* upstream #12 reports incomplete echo cancellation and crackling at
playback start on Android 13. The fork made playback thread-safe and teardown
repeatable in 0.2.0, which may or may not touch the echo cancellation quality
the reporter heard. Nobody has checked. This is the highest-value unknown in
the project. Source: upstream #12; CHANGELOG 0.2.0 Android fixes.

### 1.2 Verify realtime chunked playback and document chunk sizing — PLANNED

On both platforms, stream small PCM chunks (tens of milliseconds, not seconds)
through `playPCMData()` as they arrive from a websocket or a timer, and listen
for the choppy "talkie talkie" effect. Record the smallest chunk size that
plays cleanly on each platform. Then either document the recommended chunk size
and buffering guidance in the README, or, if small chunks still stutter, open
an issue with the measurements.
*Why here:* upstream #9 reports that a user had to queue about six seconds of
audio before playback sounded clean. The fork's 0.2.0 lost-wakeup fix (a chunk
queued just as playback ended could sit unplayed until the next chunk arrived)
plausibly addresses part of this, but it has not been verified on a device.
Realtime chunk streaming is the whole point of the module, so this ranks right
behind 1.1. Source: upstream #9; CHANGELOG 0.2.0 ("a lost-wakeup race is
fixed").

### 1.3 Update the README and CHANGELOG wording from the lane 1 results — PLANNED

After 1.1 and 1.2, change any README or CHANGELOG sentence that overstates
what was verified, and add the device results to this file as progress marks.
*Why here:* standing item S3 requires it, and it can only be done once the
evidence exists. Source: upstream #9 and #12; INTENT.md rule 7.

## Lane 2 — Features captured from upstream

**Gate:** lane 1 complete. Adding features on a base whose core behaviour is
unverified multiplies the device matrix that has to be re-checked.

### 2.1 Configurable sample rate — EXPLORATORY

Today the sample rate is fixed at 16 kHz on both platforms. A parameter, for
example on `initialize()`, would let an app ask for 24 kHz or 48 kHz. This step
becomes PLANNED only once the issue in this repository holds a design that
answers: the API shape, that the default stays 16 kHz so existing apps are
unchanged (INTENT.md rule 3), which rates each platform's voice-processing path
actually supports, and how the chosen rate is reported to JavaScript so the
app can tell its speech service.
*Why here:* upstream #10 asks for it and it is the most-requested upstream
feature. It is a pure feature with no correctness pressure, so it waits for
lane 1. It is not implemented in the fork. Source: upstream #10.

### 2.2 Decide whether to expose a bare `stopPlayer()` alongside `bargeIn()` — EXPLORATORY

Upstream pull request #14 proposes a `stopPlayer` method. The fork's
`bargeIn()` already drops queued playback while the mic stays live, which is a
superset of what #14 does. The decision to make: is there a real case for a
stop that does less than `bargeIn()`, for example stopping playback without the
barge-in semantics? The outcome is either a small addition with a README entry,
or a note in the README that `bargeIn()` is the answer and the idea is closed.
*Why here:* it is an evaluation, not a build, and it only matters once lane 1
has confirmed `bargeIn()` behaves on devices. Source: upstream PR #14;
CHANGELOG 0.2.0 (`bargeIn()`).

## Lane 3 — Community and documentation

**Gate:** none. These can proceed at any time and do not touch native code.

### 3.1 Reply on upstream #13 that the duplicate-tap crash is fixed in the fork — PLANNED

Post a short, courteous reply on upstream #13 saying that the `nullptr ==
Tap()` crash during audio-session resets is fixed in
`@equationalapplications/expo-two-way-audio` 0.2.0, link the GitHub release,
and credit the reporter's `removeTap` before `installTap` approach. Make clear
that this is an independent fork, not a Speechmatics release.
*Why here:* the fix is already shipped (CHANGELOG 0.2.0: "recovering from a
media-services reset no longer crashes on a duplicate audio tap"), so the only
work left is telling the people who hit it. Upstream PRs #15 and #17, which the
fork also carries, need no reply: #15 is subsumed by the fork's SDK 55 fix and
#17 was opened by this fork's maintainer and is left open as documentation.
Source: upstream #13, #15, #17; CHANGELOG 0.2.0.

### 3.2 Follow up on the upstream announcement — PLANNED

Upstream #18 asks Speechmatics for a README note or an `npm deprecate` message
pointing to this fork. If they respond, update the README migration guide to
reflect whatever they do, and keep the wording free of any suggestion that
Speechmatics endorses this fork. If there is no response, leave the issue open
and do nothing further; the fork does not depend on it.
*Why here:* the response, if any, comes from outside this project, so the step
cannot be scheduled. It sits in this lane so it is not forgotten. Source:
upstream #18; INTENT.md rule 12.

## Changing this file

Add a step only with a source (an upstream issue number or a fact about this
repository) and a "why here". Move a step between lanes or classes through a
pull request that says why. When a step ships, replace its class with a dated
past-tense progress mark and keep the step text for the record.
