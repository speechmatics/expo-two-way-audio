package expo.modules.twowayaudio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Executors
import kotlin.math.pow


class AudioEngine (context: Context) {
    private val SAMPLE_RATE = 16000
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

    private lateinit var audioRecord: AudioRecord
    private lateinit var audioManager: AudioManager
    private lateinit var audioTrack: AudioTrack
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioSampleQueue: Queue<ByteArray> = LinkedList()
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private val executorServiceMicrophone = Executors.newFixedThreadPool(1)
    private val executorServicePlayback = Executors.newFixedThreadPool(1)
    private var speakerDevice: AudioDeviceInfo? = null
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null

    var isRecording = false
    private var isRecordingBeforePause = false
    @Volatile var isPlaying = false
    @Volatile private var playbackGeneration = 0
    @Volatile private var abortPlayback = false

    // Callbacks
    var onMicDataCallback: ((ByteArray) -> Unit)? = null
    var onInputVolumeCallback: ((Float) -> Unit)? = null
    var onOutputVolumeCallback: ((Float) -> Unit)? = null
    var onAudioInterruptionCallback: ((String) -> Unit)? = null

    init {
        initializeAudio(context)
    }

    @SuppressLint("NewApi")
    private fun initializeAudio(context:Context) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        requestAudioFocus()

        // Route audio to external device if connected, otherwise route to speaker
        updateAudioRouting()

        // Everything below can throw (buffer-size query, AudioTrack build,
        // device-callback registration). The engine gets discarded on any
        // failure — so restore the system-audio state mutated above instead of
        // leaving MODE_IN_COMMUNICATION + held audio focus behind.
        var track: AudioTrack? = null
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AUDIO_FORMAT
            )

            // Build first, THEN play: if play() throws, `track` is already
            // set and the catch block can release it.
            val newTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                audioManager.generateAudioSessionId()
            )
            track = newTrack
            newTrack.play()
            audioTrack = newTrack

            // Listen for changes in audio routing (stored so tearDown() can
            // unregister — previously the callback accumulated per session and
            // kept mutating system audio routing after the engine was gone).
            // Registered AFTER the AudioTrack is built: if construction above
            // throws, initialize() catches it and the engine is discarded — a
            // callback registered before that point would leak with no owner.
            audioDeviceCallback = object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    Log.d("AudioEngine", "onAudioDevicesAdded")
                    super.onAudioDevicesAdded(addedDevices)
                    updateAudioRouting()
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    Log.d("AudioEngine", "onAudioDevicesRemoved")
                    super.onAudioDevicesRemoved(removedDevices)
                    updateAudioRouting()
                }
            }
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (e: Exception) {
            try {
                track?.release()
            } catch (releaseError: Exception) {
                Log.e("AudioEngine", "Failed releasing AudioTrack during init failure", releaseError)
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
            }
            throw e
        }
    }

    private fun updateAudioRouting() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var isExternalDeviceConnected = false
        var selectedDevice: AudioDeviceInfo? = null

        for (device in devices) {
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                speakerDevice = device
            }
            if (device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                isExternalDeviceConnected = true
                selectedDevice = device
                break
            } else if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                selectedDevice = device
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Use the modern API for Android S and above
            try {
                selectedDevice?.let {
                    audioManager.setCommunicationDevice(it)
                }
            }catch (e:Exception){
                Log.e("AudioEngine", "Error setting communication device. Using speaker")
                speakerDevice?.let {
                    audioManager.setCommunicationDevice(it)
                }
            }

        } else {
            // Fall back to deprecated method for older Android versions
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = !isExternalDeviceConnected
        }
    }

    @SuppressLint("NewApi")
    private fun requestAudioFocus() {
        // Abandon any previous request first — resume used to leak one per
        // background/foreground cycle.
        audioFocusRequest?.let { request ->
            audioManager.abandonAudioFocusRequest(request)
        }
        val focusRequest =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            Log.d("AudioEngine", "Audio focus lost")
                            onAudioInterruptionCallback?.let { it("blocked") }
                        }
                    }
                }
                .build()

        audioFocusRequest = focusRequest
        val result = audioManager.requestAudioFocus(focusRequest)

        // DELAYED is a normal outcome given setAcceptsDelayedFocusGain(true);
        // treating it as failure used to throw out of the Activity onResume
        // path (resumeRecordingAndPlayer) and crash the app.
        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            throw RuntimeException("Audio focus request failed")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private fun startRecording(){
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            throw RuntimeException("Audio Record can't initialize!")
        }

        if (AcousticEchoCanceler.isAvailable()){
            echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)
            if (echoCanceler != null) {
                echoCanceler?.enabled = true
                Log.i("AudioEngine", "Echo Canceler enabled")
            }
        }

        if (NoiseSuppressor.isAvailable()){
            noiseSuppressor = NoiseSuppressor.create(audioRecord.audioSessionId)
            if (noiseSuppressor != null) {
                noiseSuppressor?.enabled = true
                Log.i("AudioEngine", "Noise Suppressor enabled")
            }
        }

        audioRecord.startRecording()
        isRecording = true
        startMicSampleTap()
    }

    private fun startMicSampleTap(){
        executorServiceMicrophone.execute {
            val buffer = ByteArray(1024)
            try {
                while (isRecording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val data = buffer.copyOf(read)
                        val micVolume = calculateRMSLevel(data)
                        onInputVolumeCallback?.invoke(micVolume)
                        onMicDataCallback?.invoke(data)
                    }
                }
                Log.d("AudioEngine", "Mic sample tap stopped.")
            } catch (e: Exception) {
                Log.e("AudioEngine", "Error reading mic sample data", e)
                isRecording = false
                tearDown()
                // NO rethrow: this closure runs inside executorServiceMicrophone,
                // so a throw here hits the thread's default uncaught-exception
                // handler and KILLS THE APP. tearDown() is idempotent and marks
                // the engine torn-down; the module drops a torn-down engine and
                // the next initialize() builds a fresh one.
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop()
            audioRecord.release()
        }
        // Release the audio effects (created per startRecording) — previously
        // leaked on every mic toggle.
        echoCanceler?.enabled = false
        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.enabled = false
        noiseSuppressor?.release()
        noiseSuppressor = null
        onInputVolumeCallback?.invoke(0.0F)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun toggleRecording(value: Boolean): Boolean {
        if (value == isRecording) return isRecording

        if (value) {
            startRecording()
        } else {
            stopRecording()
        }

        isRecording = value
        return isRecording
    }

    fun playPCMData(data: ByteArray) {
        // The start decision and the isPlaying claim are made under the queue
        // lock so exactly one playback loop is ever started for a non-empty
        // queue (see the invariant in playAudioFromSampleQueue).
        val generation: Int
        val startPlayback: Boolean
        synchronized(audioSampleQueue) {
            audioSampleQueue.add(data)
            generation = playbackGeneration
            startPlayback = !isPlaying
            if (startPlayback) isPlaying = true
        }
        if (startPlayback) {
            playAudioFromSampleQueue(generation)
        }
    }

    private fun playAudioFromSampleQueue(generation: Int = playbackGeneration) {
        executorServicePlayback.execute {
            // Early return: isPlaying may stay true here with a non-empty
            // queue. This is safe ONLY because every generation bump pairs
            // with a barge-in task queued behind this one (which restarts the
            // drain, see the tail of clearPlaybackQueueForBargeIn) — except
            // tearDown, which shuts this executor down. Do not bump the
            // generation anywhere else without preserving one of the two.
            if (generation != playbackGeneration || abortPlayback) {
                synchronized(audioSampleQueue) {
                    if (audioSampleQueue.isEmpty()) isPlaying = false
                }
                return@execute
            }
            // isPlaying was already claimed under the queue lock by whichever
            // path started this loop (playPCMData, the barge-in restart, or
            // the drain re-kick) — do not set it here unlocked.
            try {
                while (!abortPlayback && generation == playbackGeneration) {
                    val data: ByteArray?
                    synchronized(audioSampleQueue) {
                        data = audioSampleQueue.poll()
                        // Invariant: loop termination is decided atomically
                        // with the empty check, so a chunk enqueued after this
                        // poll() is either consumed by this loop or, seeing
                        // isPlaying == false under the same lock, starts a new
                        // one. No chunk can be stranded between polls.
                        if (data == null) isPlaying = false
                    }
                    if (data == null) break
                    if (abortPlayback || generation != playbackGeneration) break
                    playSample(data)
                    val audioVolume = calculateRMSLevel(data)
                    onOutputVolumeCallback?.invoke(audioVolume)
                }
            } catch (e: Exception) {
                Log.e("AudioEngine", "Error playing audio", e)
                e.printStackTrace()
            } finally {
                synchronized(audioSampleQueue) {
                    if (audioSampleQueue.isEmpty() ||
                        generation != playbackGeneration || abortPlayback) {
                        isPlaying = false
                    } else {
                        // Chunks arrived for the current generation while this
                        // loop was exiting: keep draining them.
                        playAudioFromSampleQueue(generation)
                    }
                }
                onOutputVolumeCallback?.invoke(0.0F)
            }
        }
    }

    private fun playSample(data: ByteArray) {
        audioTrack.write(data, 0, data.size)
    }

    fun bypassVoiceProcessing(bypass: Boolean) {
        if (bypass) {
            echoCanceler?.enabled = false
            noiseSuppressor?.enabled = false
        } else {
            echoCanceler?.enabled = true
            noiseSuppressor?.enabled = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun pauseRecordingAndPlayer() {
        isRecordingBeforePause = isRecording
        isRecording = toggleRecording(false)
        audioTrack.pause()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun resumeRecordingAndPlayer() {
        // Reachable from the host Activity's onResume path (via
        // ExpoTwoWayAudioLifeCycleListener) — nothing here may crash the app,
        // so every failure degrades to a log.
        try {
            requestAudioFocus()
        } catch (e: Exception) {
            Log.e("AudioEngine", "Audio focus request failed on resume", e)
        }
        try {
            isRecording = toggleRecording(isRecordingBeforePause)
            audioTrack.play()
        } catch (e: Exception) {
            Log.e("AudioEngine", "Audio resume failed", e)
        }
    }

    /** Stop scheduled agent audio immediately while keeping the mic alive (barge-in). */
    fun clearPlaybackQueueForBargeIn() {
        abortPlayback = true
        synchronized(audioSampleQueue) {
            playbackGeneration += 1
            audioSampleQueue.clear()
        }
        // All AudioTrack mutations must run on the playback executor so we never
        // pause/flush while another task on that thread is mid-write().
        executorServicePlayback.execute {
            try {
                audioTrack.pause()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioTrack.flush()
                }
                audioTrack.play()
            } catch (e: Exception) {
                Log.e("AudioEngine", "Error clearing playback queue", e)
            }
            abortPlayback = false
            onOutputVolumeCallback?.invoke(0.0F)
            // Decide restart-vs-idle under the same lock playPCMData uses for
            // its start decision, so a concurrent enqueue can never start a
            // duplicate loop (isPlaying has a single lock-guarded writer path).
            val restartPlayback: Boolean
            synchronized(audioSampleQueue) {
                if (audioSampleQueue.isNotEmpty()) {
                    isPlaying = true
                    restartPlayback = true
                } else {
                    isPlaying = false
                    restartPlayback = false
                }
            }
            if (restartPlayback) {
                playAudioFromSampleQueue()
            }
        }
    }

    /** True once tearDown() has run; a torn-down engine is dead and must be discarded. */
    @Volatile
    var isTornDown: Boolean = false
        private set

    @SuppressLint("NewApi")
    fun tearDown() {
        // Idempotent: a double tearDown (mic-tap failure path + module OnDestroy,
        // or two JS tearDown calls) must not pause/stop/release an already
        // released AudioTrack — the IllegalStateException used to escape
        // Function("tearDown") and leave the module stuck with a dead engine.
        if (isTornDown) return
        isTornDown = true
        // Abort any running playback loop and drop queued agent audio BEFORE
        // releasing native resources — otherwise the old loop keeps draining
        // queued audio and firing volume callbacks after the engine reference
        // is gone (same abort+clear+bump shape as clearPlaybackQueueForBargeIn).
        abortPlayback = true
        synchronized(audioSampleQueue) {
            playbackGeneration += 1
            audioSampleQueue.clear()
        }
        stopRecording()
        audioTrack.pause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioTrack.flush()
        }
        audioTrack.stop()
        audioTrack.release()
        audioManager.mode = AudioManager.MODE_NORMAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        audioFocusRequest?.let { request ->
            audioManager.abandonAudioFocusRequest(request)
        }
        executorServiceMicrophone.shutdownNow()
        executorServicePlayback.shutdownNow()
        audioDeviceCallback?.let { callback ->
            audioManager.unregisterAudioDeviceCallback(callback)
        }
        audioDeviceCallback = null
    }


    private fun calculateRMSLevel(buffer: ByteArray): Float {
        val epsilon = 1e-5f // To avoid log(0)

        // Convert ByteArray to FloatArray by treating each pair of bytes as a single 16-bit PCM sample
        val floatBuffer = FloatArray(buffer.size / 2)
        for (i in floatBuffer.indices) {
            // Combine two bytes into a 16-bit signed integer
            val sample = (buffer[i * 2].toInt() or (buffer[i * 2 + 1].toInt() shl 8)).toShort()
            // Normalize sample to -1.0 to 1.0 range for FloatArray
            floatBuffer[i] = sample / 32768.0f
        }

        // Calculate RMS value
        val rmsValue = kotlin.math.sqrt(floatBuffer.fold(0f) { acc, sample -> acc + sample * sample } / floatBuffer.size)

        // Convert to decibels
        val dbValue = 20 * kotlin.math.log10(maxOf(rmsValue, epsilon))

        // Normalize decibel value to 0-1 range
        // Assuming minimum audible is -80dB and maximum is 0dB
        val minDb = -80.0f
        val normalizedValue = maxOf(0.0f, minOf(1.0f, (dbValue - minDb) / kotlin.math.abs(minDb)))

        // Optional: Apply exponential factor to push smaller values down
        val expFactor = 2.0f // Adjust this value to change the curve
        val adjustedValue = normalizedValue.pow(expFactor)

        return adjustedValue
    }

}