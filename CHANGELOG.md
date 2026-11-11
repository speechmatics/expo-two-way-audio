# Changelog

## 0.2.0 (2026-11-11)

First release as `@equationalapplications/expo-two-way-audio`, a maintained fork of
[`@speechmatics/expo-two-way-audio`](https://github.com/speechmatics/expo-two-way-audio) 0.1.2.
Upstream has been inactive since January 2025.

### Breaking changes

- `restart()` is removed. Use `bargeIn()` to interrupt playback.
- Requires Expo SDK 55 or newer.

### Features

- `bargeIn()`: flushes queued playback immediately while keeping the microphone and audio engine
  running, so a user can talk over speech that is still playing.
- Web: the module registers a stub, so importing it no longer crashes web builds.

### Fixes

- iOS: builds with Expo SDK 55+. Microphone permission functions pass `promise.legacyResolver` to
  the Objective-C permission delegate.
- iOS: a missing `NSMicrophoneUsageDescription` raises `fatalError` instead of the removed
  `EXFatal`.
- Android: playback is thread-safe. The playback flags are `@Volatile`, the sample queue is
  locked, and a lost-wakeup race is fixed: a chunk queued just as playback ended could sit
  unplayed until the next chunk arrived.
- Android: `tearDown()` releases everything and can be called twice. It stops playback, clears the
  queue, shuts down the playback thread, releases the `AudioTrack`, and unregisters the
  audio-device callback. Echo-cancellation and noise-suppressor effects are released on every
  mic toggle instead of leaking.
- Android: an error in the mic tap no longer crashes the app; the engine tears itself down
  instead. Audio focus `DELAYED` counts as success, resuming the app can't crash in the audio
  focus request, and a failed `initialize()` restores the audio mode and releases resources.
- Android and iOS: the module tears the engine down when it is destroyed, and `initialize()`
  rebinds event callbacks, so events keep arriving after a JS reload.
- iOS: after an interruption (call, alarm, Siri) the mic returns to its state from before the
  interruption, and muting during the interruption is respected. Unknown interruption types log
  a message instead of calling `fatalError`.
- iOS: recovering from a media-services reset no longer crashes on a duplicate audio tap.
