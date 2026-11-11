# INTENT — @equationalapplications/expo-two-way-audio

**Read this file first.**

This file wins on *intent*: what this package is trying to do and the rules it
must keep. The specifications win on *detail*: exactly how a thing is built. The
README, the CHANGELOG, the TypeScript types in `src/`, and the native code in
`ios/` and `android/` are the specifications. If a specification contradicts
this file, the specification is wrong until this file is amended through a pull
request. Never design around a rule silently.

This file describes the target system. It carries no status, no progress and no
version history. Gaps between this file and the code are tracked in GitHub
issues and ordered in [docs/ROADMAP.md](./docs/ROADMAP.md).

## Why this package exists

Voice conversation apps need to do two things at once on a phone: listen to the
user through the microphone and play speech back through the speaker. Doing
both at the same time is hard. The speaker's output leaks back into the
microphone, so the app hears itself. Playback has to start the moment audio
arrives, or the conversation feels laggy. And when the user starts talking over
the app, the app has to stop talking right away and keep listening.

This package is an [Expo](https://expo.dev) module that does exactly that job
for React Native apps on iOS and Android. It captures echo-cancelled microphone
audio as raw PCM, plays raw PCM chunks back with low latency, and lets the app
interrupt playback instantly ("barge-in") while the microphone stays live. It
hands bytes to JavaScript and takes bytes from JavaScript. What the app does
with those bytes, such as streaming them to a speech service, is the app's
business.

The package began as `@speechmatics/expo-two-way-audio`, written by
Speechmatics (Cantab Research Ltd) and released under the MIT license. That
project stopped receiving changes in January 2025. Equational Applications
forked it to keep it building against current Expo releases and to fix the
crashes and threading bugs that its own production voice app ran into. The fork
credits Speechmatics as the original author. Speechmatics has not reviewed or
endorsed this fork, and nothing in this repository may suggest otherwise.

The first consumer of this package is a public, open-source Expo chat app
maintained by Equational Applications. The package is published so that anyone
building a voice conversation app can use it, and so that users of the original
package have a maintained place to go.

## Glossary

- **PCM (pulse-code modulation)**: uncompressed audio as a plain stream of
  sample values. This module speaks only PCM: 1 channel, 16-bit samples, at a
  single documented sample rate. No compressed formats.
- **Two-way audio (full duplex)**: capturing from the microphone and playing to
  the speaker at the same time, on the same audio session.
- **Echo cancellation (AEC, acoustic echo cancellation)**: removing the
  speaker's output from what the microphone hears, so the app does not hear its
  own speech. Done by the operating system's voice-processing audio path.
  Noise suppression travels with it. Neither works on simulators.
- **Barge-in**: the user starts talking while the app is still playing speech.
  The app must drop everything queued for playback at once and keep the
  microphone open. In this package that is `bargeIn()`.
- **Chunk**: one buffer of PCM bytes handed to `playPCMData()` or delivered by
  the `onMicrophoneData` event.
- **Engine**: the native audio session, microphone tap, playback queue and
  playback thread that `initialize()` creates and `tearDown()` releases.
- **Audio interruption**: the operating system taking the audio session away,
  for example for a phone call, an alarm or Siri, then giving it back.
- **Media-services reset**: iOS restarting its audio subsystem underneath a
  running app. The engine must survive this.
- **Native module**: code written in Swift and Kotlin that Expo links into the
  app binary. It needs a development build or an EAS build. It cannot run in
  Expo Go.
- **Upstream**: the original `speechmatics/expo-two-way-audio` repository and
  npm package.
- **Device check**: running the module on a physical iPhone or Android phone,
  not a simulator or emulator, and listening to the result.
- **Conventional Commits**: a commit message convention
  (`fix: ...`, `feat: ...`, `docs: ...`) used so that the changelog and version
  bumps follow from the history.
- **Trusted publishing**: npm publishing from a GitHub Actions workflow using a
  short-lived identity token instead of a stored secret, so each release carries
  a provenance record saying which commit and workflow built it.

## Rules

1. **Built for voice conversation, nothing broader.** Every feature serves the
   loop of listen, send, receive, play, interrupt. Capture is echo-cancelled by
   default; playback is a queue that starts on the first chunk; barge-in flushes
   that queue without touching the microphone.
   *Why:* a module that tries to be a general audio library will be worse at the
   one thing voice apps need, which is a clean, low-latency duplex path.

2. **The JavaScript layer is thin.** The functions in `src/` forward to the
   native module and add types, event helpers and React hooks. Audio behaviour
   lives in `ios/` and `android/`.
   *Why:* audio timing and session handling only work correctly in native code.
   A thin JavaScript layer keeps the public API small and keeps the two
   platforms honest with each other.

3. **One audio format, documented, the same in both directions.** The bytes
   the module emits from the microphone and the bytes it accepts for playback
   have the same layout, and the README states it. If the format ever becomes
   configurable, the default stays the documented one so existing apps keep
   working without changes.
   *Why:* apps wire this module to a speech service. A format that differs by
   direction, platform or release silently breaks that wiring.

4. **A runtime audio error never brings down the host app.** If the microphone
   tap, the playback thread or the audio session fails while running, the
   engine tears itself down and the app keeps running. Failing loudly is
   reserved for build-time and startup misconfiguration, such as a missing
   microphone usage description.
   *Why:* the module sits inside someone else's app. A crash in an audio path
   is a crash of their product.

5. **Lifecycle is safe to repeat.** `initialize()` can follow `tearDown()`,
   `tearDown()` can be called twice, and a JavaScript reload re-binds events
   without a native rebuild. Resources acquired by the engine, including audio
   effects, tracks and callbacks, are released when the engine goes away.
   *Why:* React apps mount, unmount and hot-reload constantly. Leaked native
   resources show up as "works the first time, breaks the second".

6. **Survive interruptions and resets, and come back in the same state.** After
   a call, alarm or media-services reset, the microphone returns to the state
   it had before, and a mute requested during the interruption is respected.
   *Why:* a voice app that silently stops listening after a phone call looks
   broken, and the user cannot tell why.

7. **Native changes are proven on real devices before they are claimed.**
   Continuous integration can typecheck and build the TypeScript, but it cannot
   run the Swift or Kotlin code. Every change under `ios/` or `android/` states
   which physical devices it was tested on. A fix that has not been heard on a
   device is described as untested, not as fixed.
   *Why:* echo cancellation, microphone modes and playback timing do not behave
   on simulators. Claims without device evidence are guesses, and this package
   makes its claims in public.

8. **A drop-in replacement for the upstream package, with documented
   exceptions.** The public API matches upstream wherever upstream's design was
   sound. Every departure, such as a removed or renamed function, is listed in
   the README migration guide with the replacement.
   *Why:* the people most likely to arrive here are upstream users following an
   issue thread. They should be able to switch by changing an import.

9. **Breaking changes are rare, explicit and carry a migration note.** A
   breaking change bumps the version according to semantic versioning and gets
   a "Breaking changes" entry in the CHANGELOG with what to do instead.
   *Why:* a native module forces a rebuild on every update. Surprises are
   expensive for the apps that depend on it.

10. **Importing the module never breaks a platform it does not support.** On
    web the module registers an empty stub so that a shared codebase still
    bundles. Unsupported platforms fail at call time, not at import time.
    *Why:* Expo apps commonly share one codebase across iOS, Android and web.

11. **Every release is published from a git tag by the publish workflow, with
    provenance.** The tag `v<version>` must match `package.json`. No release is
    published by hand from a laptop.
    *Why:* a release built by a public workflow from a public commit can be
    verified by anyone. A release pushed from a laptop cannot.

12. **Credit the origin; claim no endorsement.** The license keeps the Cantab
    Research Ltd copyright line, `package.json` lists Speechmatics as a
    contributor, and the README names them as the original author. Nothing in
    this repository, its package metadata or its issue replies may suggest that
    Speechmatics endorses, reviews or supports this fork.
    *Why:* the MIT license requires the credit, and honesty requires the
    distinction.

13. **Documentation is written for a stranger.** Public files in this
    repository explain jargon on first use, say what is shipped and what is
    not, and contain no private details: no host names, no private repository
    paths, no internal code names.
    *Why:* readers include non-specialists and upstream users with no context.
    This repository is public and is meant to stay readable without any other
    document.

## Out of scope (don't build these here)

### Permanent non-goals

- **Speech recognition, text-to-speech or any network transport.** The module
  moves PCM bytes between the hardware and JavaScript. Connecting to a speech
  service, choosing a protocol and encoding audio for the wire belong in the
  app.
- **Recording to files or playing audio files.** Use `expo-audio` or a similar
  library. This module handles live streams only.
- **Music or general media playback.** The playback path is tuned for speech
  on the voice-processing audio session.
- **Compressed or multi-channel audio formats.** The module speaks one
  uncompressed mono PCM layout.
- **User interface components.** The module exposes functions, events and
  hooks; the app owns the screen.
- **Expo Go support.** A native module cannot run in Expo Go. This is a
  property of Expo, not a gap.

### Not in the first version

These are candidates, tracked in GitHub issues and ordered in
[docs/ROADMAP.md](./docs/ROADMAP.md). None of them is built, and none has a
finished design.

- **Configurable sample rates.** Upstream users asked for rates other than the
  documented one. Rule 3 governs how such a parameter would have to behave.
- **A bare "stop playback" call.** `bargeIn()` covers interrupting speech.
  Whether a separate stop that does less is worth exposing is undecided.
- **A real web implementation.** The web entry is only a stub today so that
  imports do not break web builds.
- **Automated tests on physical devices.** Device checks are done by people.

## How changes are made

- **Behaviour changes start in an issue.** A bug report or feature request is
  opened at the repository's GitHub issues, with the Expo SDK version, platform
  and a reproduction for bugs. For anything that changes the public API or a
  rule above, the issue states the design before code is written.
- **Code changes arrive by pull request** from a branch off `main`, with
  Conventional Commit messages, passing typecheck, build and formatting, and,
  for native changes, a list of the physical devices tested. Maintainers
  review and merge.
- **Where decisions live.** The decision to do something, and the reasons, live
  in the GitHub issue and pull request that did it. The order in which work is
  taken up, and what has shipped under that order, live in
  [docs/ROADMAP.md](./docs/ROADMAP.md). What each released version contains
  lives in [CHANGELOG.md](./CHANGELOG.md). This file holds only the intent and
  the rules.
- **What gates a change to this file.** A rule here changes only through a
  pull request that edits this file, explains why the old rule was wrong, and
  is approved by a maintainer. Until that pull request merges, the old rule
  stands, and code or specifications that contradict it are the thing to fix.
- **Who can release.** Maintainers release by merging a version-bump pull
  request and pushing a signed tag. Contributors do not push tags. The
  recipe is in [CONTRIBUTING.md](./CONTRIBUTING.md).
