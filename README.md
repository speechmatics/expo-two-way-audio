# @equationalapplications/expo-two-way-audio

[![GitHub Tag](https://img.shields.io/github/v/tag/equationalapplications/expo-two-way-audio?label=github%20tag)](https://github.com/equationalapplications/expo-two-way-audio/tags)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](./LICENSE)
[![npm version](https://img.shields.io/npm/v/%40equationalapplications%2Fexpo-two-way-audio?label=%40equationalapplications%2Fexpo-two-way-audio)](https://www.npmjs.com/package/@equationalapplications/expo-two-way-audio)
[![npm downloads](https://img.shields.io/npm/dm/%40equationalapplications%2Fexpo-two-way-audio?label=downloads)](https://www.npmjs.com/package/@equationalapplications/expo-two-way-audio)

Expo module for real-time, two-way PCM audio on iOS and Android, built for voice conversation
apps:

- Echo-cancelled microphone capture: PCM, 1 channel, 16-bit, 16 kHz
- Low-latency playback of PCM chunks in the same format
- **Barge-in**: `bargeIn()` stops queued playback instantly while the mic stays live, so users can
  interrupt
- Input and output volume levels (0–1)
- Microphone permission helpers and a React hook
- iOS: read the microphone mode and prompt the user to pick one

This is a maintained fork of
[`@speechmatics/expo-two-way-audio`](https://github.com/speechmatics/expo-two-way-audio), which has
been inactive since January 2025. It adds barge-in, builds on Expo SDK 55+, and fixes a number of
threading, teardown and interruption bugs. See [CHANGELOG.md](./CHANGELOG.md).

## Coming from `@speechmatics/expo-two-way-audio`?

This package replaces it with one API change. It needs Expo SDK 55 or newer.

1. Swap the package:

   ```sh
   npm uninstall @speechmatics/expo-two-way-audio
   npx expo install @equationalapplications/expo-two-way-audio
   ```

2. Change imports from `@speechmatics/expo-two-way-audio` to
   `@equationalapplications/expo-two-way-audio`.
3. If you called `restart()` to interrupt playback, call `bargeIn()` instead. It stops queued audio
   immediately and keeps the microphone running.
4. Rebuild the native app (`npx expo prebuild --clean`, or a new EAS build).

What you get: iOS builds on Expo SDK 55+ (the `EXFatal` and `promise.resolver` compile errors are
fixed), no iOS crash from a duplicate audio tap after a media-services reset, and thread-safe
Android playback. See [CHANGELOG.md](./CHANGELOG.md) for the full list.

## Installation

```sh
npx expo install @equationalapplications/expo-two-way-audio
```

This is a native module, so it needs a development build (`npx expo run:ios` /
`npx expo run:android` or EAS Build). It doesn't work in Expo Go. Requires Expo SDK 55 or newer.

### Permissions

iOS: add a microphone usage description, for example in `app.json`:

```json
{ "expo": { "ios": { "infoPlist": { "NSMicrophoneUsageDescription": "Talk to the assistant" } } } }
```

Android: add `RECORD_AUDIO` and `MODIFY_AUDIO_SETTINGS`:

```json
{ "expo": { "android": { "permissions": ["RECORD_AUDIO", "MODIFY_AUDIO_SETTINGS"] } } }
```

## Usage

```tsx
import { useCallback, useEffect } from 'react'
import {
  bargeIn,
  initialize,
  playPCMData,
  tearDown,
  toggleRecording,
  useExpoTwoWayAudioEventListener,
  useMicrophonePermissions,
  type MicrophoneDataCallback,
} from '@equationalapplications/expo-two-way-audio'

export function useVoice() {
  const [permission, requestPermission] = useMicrophonePermissions()

  // 1. Initialize once before using any audio function.
  useEffect(() => {
    initialize()
    return () => {
      tearDown()
    }
  }, [])

  // 2. Receive mic samples (Uint8Array of 16 kHz, 16-bit mono PCM).
  useExpoTwoWayAudioEventListener(
    'onMicrophoneData',
    useCallback<MicrophoneDataCallback>((event) => {
      // send event.data to your speech service
    }, []),
  )

  return {
    permission,
    requestPermission,
    start: () => toggleRecording(true), // unmute the mic
    stop: () => toggleRecording(false),
    // 3. Play a chunk of 16 kHz, 16-bit mono PCM.
    play: (pcm: Uint8Array) => playPCMData(pcm),
    // 4. The user started talking over playback: drop queued audio, keep the mic on.
    interrupt: () => bargeIn(),
  }
}
```

## API

| Function | Description |
|---|---|
| `initialize(): Promise<boolean>` | Set up the audio engine. Call before anything else. |
| `toggleRecording(on: boolean): boolean` | Mute or unmute the microphone. Returns the new recording state. |
| `isRecording(): boolean` | Whether the mic is currently recording. |
| `playPCMData(data: Uint8Array)` | Queue a PCM chunk for playback. |
| `bargeIn()` | Flush queued playback immediately; the mic and engine keep running. Emits no events. |
| `bypassVoiceProcessing(bypass: boolean)` | Turn echo cancellation and noise suppression off (`true`) or on. |
| `tearDown()` | Release the audio engine. Safe to call more than once. |
| `getMicrophonePermissionsAsync()` / `requestMicrophonePermissionsAsync()` | Read or request mic permission. |
| `useMicrophonePermissions()` | Hook: `[permission, requestPermission, getPermission]`. |
| `useIsRecording()` | Hook: current recording state. |
| `getMicrophoneModeIOS()` / `setMicrophoneModeIOS()` | iOS only: read the mic mode, or show the system mic-mode picker. |
| `addExpoTwoWayAudioEventListener(name, cb)` / `useExpoTwoWayAudioEventListener(name, cb)` | Subscribe to an event. |

### Events

| Event | `event.data` |
|---|---|
| `onMicrophoneData` | `Uint8Array`: 16 kHz, 16-bit mono PCM |
| `onInputVolumeLevelData` | `number`: mic level, 0–1 |
| `onOutputVolumeLevelData` | `number`: playback level, 0–1 |
| `onRecordingChange` | `boolean`: recording on or off |
| `onAudioInterruption` | `string`: `"began"`, `"ended"` or `"blocked"` (iOS); `"blocked"` (Android) |

## Notes

Echo cancellation, noise suppression and iOS microphone modes don't work on simulators. Test on a
real device:

```sh
npx expo run:ios --device --configuration Release
npx expo run:android --device --variant release
```

On web the module registers an empty stub, so importing it doesn't break web builds. Its
functions are not implemented there.

## License

MIT. Originally created by Speechmatics (Cantab Research Ltd); maintained by Equational
Applications.

---

Made with ❤️ by Equational Applications LLC. [https://equationalapplications.com/](https://equationalapplications.com/)
