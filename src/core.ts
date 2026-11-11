import { type PermissionResponse } from 'expo-modules-core'
import ExpoTwoWayAudioModule from './ExpoTwoWayAudioModule'

export async function initialize() {
  return await ExpoTwoWayAudioModule.initialize()
}

export function playPCMData(audioData: Uint8Array) {
  return ExpoTwoWayAudioModule.playPCMData(audioData)
}

export function bypassVoiceProcessing(bypass: boolean) {
  return ExpoTwoWayAudioModule.bypassVoiceProcessing(bypass)
}

export function toggleRecording(val: boolean): boolean {
  return ExpoTwoWayAudioModule.toggleRecording(val)
}

export function isRecording(): boolean {
  return ExpoTwoWayAudioModule.isRecording()
}

export function tearDown() {
  return ExpoTwoWayAudioModule.tearDown()
}

/**
 * Barge-in: flush queued playback immediately while keeping the mic/engine
 * alive, so the user can interrupt speech that is still playing. Emits no
 * events. Replaces upstream's `restart()`.
 */
export function bargeIn() {
  return ExpoTwoWayAudioModule.bargeIn()
}

export async function getMicrophonePermissionsAsync(): Promise<PermissionResponse> {
  return ExpoTwoWayAudioModule.getMicrophonePermissionsAsync()
}

export async function requestMicrophonePermissionsAsync(): Promise<PermissionResponse> {
  return ExpoTwoWayAudioModule.requestMicrophonePermissionsAsync()
}

export function getMicrophoneModeIOS() {
  return ExpoTwoWayAudioModule.getMicrophoneModeIOS()
}

export function setMicrophoneModeIOS() {
  return ExpoTwoWayAudioModule.setMicrophoneModeIOS()
}
