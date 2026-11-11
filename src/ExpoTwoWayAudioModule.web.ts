import { NativeModule, registerWebModule } from 'expo'

class ExpoTwoWayAudioModule extends NativeModule<{}> {}

export default registerWebModule(ExpoTwoWayAudioModule, 'ExpoTwoWayAudioModule')
