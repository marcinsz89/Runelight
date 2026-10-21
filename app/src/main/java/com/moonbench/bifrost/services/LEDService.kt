package com.moonbench.bifrost.services

import android.Manifest
import android.content.pm.PackageManager
import android.app.Notification
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.moonbench.bifrost.R
import com.moonbench.bifrost.RunelightActivity
import com.moonbench.bifrost.animations.*
import com.moonbench.bifrost.ambient.AmbientSamplingMode
import com.moonbench.bifrost.ambient.NativeAmbientAnimation
import com.moonbench.bifrost.ambient.ProjectionConsentGate
import com.moonbench.bifrost.ambient.TopScreenCapture
import com.moonbench.bifrost.audio.*
import com.moonbench.bifrost.tools.LedController

/** Owns the retained effects and hardware. UI lifetime and legacy policies have no authority. */
class LEDService : Service() {
    companion object {
        private const val TAG = "RunelightService"
        private val PLAIN_EFFECTS = setOf(LedAnimationType.STATIC, LedAnimationType.RAINBOW,
            LedAnimationType.BREATH, LedAnimationType.PULSE)
        internal val projectionRequests = ProjectionConsentGate()
        const val EXTRA_CAPTURE_GRANT = "runelight.captureGrant"
        const val EXTRA_CAPTURE_RESULT = "runelight.captureResult"
        const val EXTRA_CAPTURE_TICKET = "runelight.captureTicket"
        const val EXTRA_SOUND_ENABLED = "runelight.soundEnabled"
        const val EXTRA_AMBIENT_SAMPLING = "runelight.ambientSampling"
        const val ACTION_START = "io.github.marcinsz89.runelight.START"
        // Compilation compatibility for remaining legacy sources, not accepted runtime commands.
        const val CHANNEL_ID = "LEDServiceChannel"
        const val NOTIFICATION_ID = 4242
        const val ACTION_STOP = "com.moonbench.bifrost.STOP"
        const val ACTION_KILL = "com.moonbench.bifrost.KILL"
        const val ACTION_UPDATE_PARAMS = "com.moonbench.bifrost.UPDATE_PARAMS"
        const val EXTRA_LEFT_BRIGHTNESS_PERCENT = "leftBrightnessPercent"
        const val EXTRA_RIGHT_BRIGHTNESS_PERCENT = "rightBrightnessPercent"
        const val ACTION_FORCE_APP_PROFILE_RESOLUTION = "com.moonbench.bifrost.FORCE_APP_PROFILE_RESOLUTION"
        const val ACTION_SUPPLY_PROJECTION = "com.moonbench.bifrost.SUPPLY_PROJECTION"
        const val ACTION_EXTERNAL_DISPLAY = "com.moonbench.bifrost.EXTERNAL_DISPLAY"
        const val ACTION_EXTERNAL_CLEAR = "com.moonbench.bifrost.EXTERNAL_CLEAR"
        const val ACTION_EXTERNAL_PULSE = "com.moonbench.bifrost.EXTERNAL_PULSE"
        const val EXTRA_EXTERNAL_PULSE_KIND = "external.pulseKind"
        const val EXTRA_ALLOW_BACKGROUND_RUN = "allowBackgroundRun"
        const val EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED = "batteryOverrideWhenPlugged"
        const val EXTRA_LOW_BATTERY_ALERT_ENABLED = "lowBatteryAlertEnabled"
        const val EXTRA_LOW_BATTERY_ALERT_THRESHOLD = "lowBatteryAlertThreshold"
        const val EXTRA_DISABLE_LOW_BATTERY_ALERT_WHILE_CHARGING = "disableLowBatteryAlertWhileCharging"
        const val EXTRA_PERSISTENT_NOTIFICATION = "persistentNotification"
        const val EXTRA_ADAPTIVE_BRIGHTNESS = "adaptiveBrightness"
        const val EXTRA_EXTERNAL_CALLER_PACKAGE = "external.callerPackage"
        const val EXTRA_EXTERNAL_EFFECT = "external.effect"
        const val EXTRA_EXTERNAL_COLOR = "external.color"
        const val EXTRA_EXTERNAL_COLOR_RIGHT = "external.colorRight"
        const val EXTRA_EXTERNAL_INTENSITY = "external.intensity"
        const val EXTRA_EXTERNAL_SPEED = "external.speed"
        const val EXTRA_EXTERNAL_SMOOTHNESS = "external.smoothness"
        const val EXTRA_EXTERNAL_SENSITIVITY = "external.sensitivity"
        const val EXTRA_EXTERNAL_PRIORITY = "external.priority"
        const val EXTRA_EXTERNAL_TERMINATOR = "external.terminator"
        const val EXTRA_EXTERNAL_DURATION_MS = "external.durationMs"
        const val EXTRA_EXTERNAL_PHASE_SECONDS = "external.phaseSeconds"
        const val EXTRA_EXTERNAL_FLICKERING = "external.flickering"
        const val EXTRA_EXTERNAL_BURST_WALL_MS = "external.burstWallMs"
        const val TERMINATOR_DURATION = "DURATION"
        const val TERMINATOR_NEXT_COMMAND = "NEXT_COMMAND"
        const val TERMINATOR_EXPLICIT_CLEAR = "EXPLICIT_CLEAR"
        const val EXTRA_FADE_END_COLOR = "fadeEndColor"
        const val EXTRA_FADE_END_RIGHT_COLOR = "fadeEndRightColor"
        const val PREF_AMBILIGHT_USE_MEDIA_PROJECTION = "ambilight_use_media_projection"
        const val DEFAULT_AMBILIGHT_USE_MEDIA_PROJECTION = true

        private val mutableSound = MutableLiveData(SoundState())
        val soundState: LiveData<SoundState> get() = mutableSound
        val hasCaptureSession: Boolean get() = mutableSound.value?.captureAvailable == true
        private val mutableState = MutableLiveData(LightingState())
        val state: LiveData<LightingState> get() = mutableState
        val isRunning: Boolean get() = mutableState.value?.phase in
            setOf(LightingPhase.STARTING, LightingPhase.RUNNING)
        val runningEffect: LedAnimationType? get() = mutableState.value
            ?.takeIf { it.phase == LightingPhase.RUNNING }?.effect
        val outputAvailable: Boolean? get() = when {
            mutableState.value?.failure == LightingFailure.OUTPUT_UNAVAILABLE -> false
            mutableState.value?.phase == LightingPhase.RUNNING -> true
            else -> null
        }

        /** Clears durable intent even if there is no live service instance. */
        fun requestStop(context: Context) {
            projectionRequests.invalidate()
            LightingIntentStore(context).setWantedRunning(false)
            context.startService(Intent(context, LEDService::class.java).setAction(ACTION_STOP))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val runtime = LightingRuntime()
    private val intentStore by lazy { LightingIntentStore(this) }
    private var ledController: LedController? = null
    private var currentAnimation: LedAnimation? = null
    private var currentColor = Color.WHITE
    private var currentRightColor = Color.WHITE
    private var currentBrightness = 255
    private var currentSpeed = 0.5f
    private var currentSmoothness = 0.5f
    private var latestStartId = 0
    private var stopping = false
    private var ownsSession = false
    private var startupTimeout: Runnable? = null
    private var selectedEffect: LedAnimationType? = null
    private var capture: TopScreenCapture? = null
    private var captureEpoch = 0L
    private var audio: PlaybackAudioCapture? = null
    private var audioEpoch = 0L
    private var soundWanted = false
    private var soundPhase = SoundPhase.OFF
    private var audioFailed = false

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Lighting", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        when (intent?.action) {
            ACTION_STOP -> {
                projectionRequests.invalidate()
                intentStore.setWantedRunning(false)
                // A process may have died while the vendor retained its last frame.
                // Explicit Stop still attempts black without restarting an effect.
                if (ledController == null) ledController = LedController()
                cleanupAndStop()
            }
            ACTION_START -> startLighting(intent)
            ACTION_UPDATE_PARAMS -> {
                if (isRunning && !stopping) handleUpdateParams(intent)
                else stopSelf(startId)
            }
            else -> {
                // Old boot/profile/API/null starts cannot resurrect or replace lighting.
                // Satisfy the FGS contract if an obsolete caller used startForegroundService.
                if (!isRunning) {
                    promoteToForeground()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startLighting(intent: Intent) {
        val type = LedAnimationType.fromStoredName(intent.getStringExtra("animationType"))
        if (type !in PLAIN_EFFECTS && type != LedAnimationType.AMBIENT) {
            if (!isRunning) {
                promoteToForeground()
                ownsSession = true
                cleanupAndStop(failure = LightingFailure.START_FAILED)
            }
            return
        }
        type!!
        val ambient = type == LedAnimationType.AMBIENT
        if (ambient && capture == null && !intent.hasExtra(EXTRA_CAPTURE_GRANT)) {
            if (!isRunning) {
                promoteToForeground()
                ownsSession = true
                cleanupAndStop(failure = LightingFailure.CAPTURE_LOST)
            }
            return
        }
        // Only fresh, pending consent may create a session. Never replay grants.
        if (intent.hasExtra(EXTRA_CAPTURE_GRANT) &&
            !projectionRequests.consume(intent.getLongExtra(EXTRA_CAPTURE_TICKET, -1))) {
            if (!isRunning) { promoteToForeground(); ownsSession = true; cleanupAndStop(failure = LightingFailure.CAPTURE_LOST) }
            return
        }
        if (!intent.hasExtra(EXTRA_CAPTURE_GRANT)) projectionRequests.invalidate()
        stopping = false
        ownsSession = true
        val keepOutput = runtime.state.phase == LightingPhase.RUNNING &&
            currentAnimation != null && ledController?.isAvailable == true
        selectedEffect = type
        soundWanted = intent.getBooleanExtra(EXTRA_SOUND_ENABLED, soundWanted)
        audioFailed = false
        val token = runtime.begin(type, keepOutput)
        publishState()
        try {
            cancelStartupTimeout()
            ledController?.onOutputResult = null
            currentAnimation?.stopForHandoff()
            currentAnimation = null
            promoteToForeground(capture != null || intent.hasExtra(EXTRA_CAPTURE_GRANT))
            if (capture == null && intent.hasExtra(EXTRA_CAPTURE_GRANT)) acquireCapture(intent)
            if (!runtime.isCurrent(token)) return
            if (ambient && capture?.mediaProjection == null) {
                cleanupAndStop(failure = LightingFailure.CAPTURE_LOST)
                return
            }
            if (!intentStore.setWantedRunning(true)) {
                cleanupAndStop(failure = LightingFailure.START_FAILED)
                return
            }
            val controller = ledController ?: LedController().also { ledController = it }
            if (!controller.isAvailable) {
                cleanupAndStop(failure = LightingFailure.OUTPUT_UNAVAILABLE)
                return
            }
            currentColor = intent.getIntExtra("animationColor", Color.WHITE)
            currentRightColor = intent.getIntExtra("animationRightColor", currentColor)
            currentBrightness = intent.getIntExtra("brightness", 255).coerceIn(0, 255)
            currentSpeed = intent.getFloatExtra("speed", 0.5f).coerceIn(0f, 1f)
            currentSmoothness = intent.getFloatExtra("smoothness", 0.5f).coerceIn(0f, 1f)
            controller.setSideBrightness(intent.getIntExtra(EXTRA_LEFT_BRIGHTNESS_PERCENT, 100),
                intent.getIntExtra(EXTRA_RIGHT_BRIGHTNESS_PERCENT, 100))
            controller.onOutputResult = { accepted ->
                // Death notifications can arrive on a Binder thread. Capture the
                // session token before posting, so old results cannot affect a replacement.
                val result = Runnable {
                    if (runtime.isCurrent(token)) {
                        if (!accepted) cleanupAndStop(failure = LightingFailure.OUTPUT_UNAVAILABLE)
                        else if ((type != LedAnimationType.AMBIENT ||
                                (currentAnimation as? NativeAmbientAnimation)?.hasRenderedFrame == true) &&
                            runtime.frameAccepted(token)) {
                            cancelStartupTimeout()
                            publishState()
                        }
                    }
                }
                if (Looper.myLooper() == handler.looper) result.run() else handler.post(result)
            }
            val animation = when (type) {
                LedAnimationType.AMBIENT -> NativeAmbientAnimation(this, controller,
                    0, Intent(),
                    AmbientSamplingMode.fromStoredName(intent.getStringExtra(EXTRA_AMBIENT_SAMPLING)), onFailure = {
                    if (runtime.isCurrent(token)) cleanupAndStop(failure = LightingFailure.CAPTURE_LOST)
                }, captureFactory = { frame, _ -> checkNotNull(capture).videoLease(frame) })
                LedAnimationType.STATIC -> StaticAnimation(controller, currentColor, currentRightColor)
                LedAnimationType.RAINBOW -> RainbowAnimation(controller)
                LedAnimationType.BREATH -> BreathAnimation(controller, currentColor, currentRightColor)
                LedAnimationType.PULSE -> PulseAnimation(controller, currentColor, currentRightColor)
                else -> error("Unsupported effect")
            }
            currentAnimation = animation
            animation.setTargetBrightness(currentBrightness)
            animation.setSpeed(currentSpeed)
            animation.setLerpStrength(currentSmoothness)
            startupTimeout = Runnable {
                if (runtime.awaitingFrame(token))
                    cleanupAndStop(failure = if (ambient) LightingFailure.CAPTURE_LOST else LightingFailure.START_FAILED)
            }.also { handler.postDelayed(it, 5000L) }
            animation.start()
            if (runtime.isCurrent(token)) syncSound()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Lighting start failed", e)
            cleanupAndStop(failure = LightingFailure.START_FAILED)
        }
    }

    private fun handleUpdateParams(intent: Intent) {
        val animation = currentAnimation ?: return
        if (intent.hasExtra(EXTRA_SOUND_ENABLED)) {
            val next = intent.getBooleanExtra(EXTRA_SOUND_ENABLED, soundWanted)
            if (next != soundWanted) audioFailed = false
            soundWanted = next
        }
        if (intent.hasExtra(EXTRA_CAPTURE_GRANT)) {
            if (!projectionRequests.consume(intent.getLongExtra(EXTRA_CAPTURE_TICKET, -1))) return
            audioFailed = false
            if (capture == null) {
                try { promoteToForeground(true); acquireCapture(intent) }
                catch (_: RuntimeException) { releaseCapture(); soundPhase = SoundPhase.NEEDS_CONSENT; publishSound() }
            }
            if (stopping) return
        }
        if (intent.hasExtra(EXTRA_AMBIENT_SAMPLING))
            (animation as? NativeAmbientAnimation)?.setSamplingMode(
                AmbientSamplingMode.fromStoredName(intent.getStringExtra(EXTRA_AMBIENT_SAMPLING)))
        ledController?.setSideBrightness(
            if (intent.hasExtra(EXTRA_LEFT_BRIGHTNESS_PERCENT)) intent.getIntExtra(EXTRA_LEFT_BRIGHTNESS_PERCENT, 100) else null,
            if (intent.hasExtra(EXTRA_RIGHT_BRIGHTNESS_PERCENT)) intent.getIntExtra(EXTRA_RIGHT_BRIGHTNESS_PERCENT, 100) else null)
        if (intent.hasExtra("animationColor")) {
            currentColor = intent.getIntExtra("animationColor", currentColor)
            animation.setTargetColor(currentColor)
        }
        if (intent.hasExtra("animationRightColor")) {
            currentRightColor = intent.getIntExtra("animationRightColor", currentRightColor)
            animation.setTargetRightColor(currentRightColor)
        }
        if (intent.hasExtra("brightness")) {
            currentBrightness = intent.getIntExtra("brightness", currentBrightness).coerceIn(0, 255)
            animation.setTargetBrightness(currentBrightness)
        }
        if (intent.hasExtra("speed")) {
            currentSpeed = intent.getFloatExtra("speed", currentSpeed).coerceIn(0f, 1f)
            animation.setSpeed(currentSpeed)
        }
        if (intent.hasExtra("smoothness")) {
            currentSmoothness = intent.getFloatExtra("smoothness", currentSmoothness).coerceIn(0f, 1f)
            animation.setLerpStrength(currentSmoothness)
        }
        syncSound()
    }

    private fun acquireCapture(intent: Intent) {
        val grant = intent.getParcelableExtra(EXTRA_CAPTURE_GRANT, Intent::class.java) ?: return
        if (intent.getIntExtra(EXTRA_CAPTURE_RESULT, 0) != Activity.RESULT_OK) return
        val epoch = ++captureEpoch
        val session = TopScreenCapture(this, handler, { _, _, _ -> }, {
            if (epoch == captureEpoch) {
                if (selectedEffect == LedAnimationType.AMBIENT) cleanupAndStop(failure = LightingFailure.CAPTURE_LOST)
                else { releaseCapture(); soundPhase = SoundPhase.NEEDS_CONSENT; publishSound() }
            }
        })
        capture = session
        session.acquire(Activity.RESULT_OK, grant)
        publishSound()
    }

    private fun stopAudio() {
        audioEpoch++
        audio?.close(); audio = null
        ledController?.setMasterScale(1f)
    }

    private fun releaseCapture() {
        captureEpoch++
        stopAudio()
        val old = capture
        capture = null
        old?.close()
        if (!stopping && isRunning) promoteToForeground(false)
    }

    private fun syncSound() {
        if (stopping || currentAnimation == null) return
        when {
            !soundWanted -> {
                stopAudio()
                soundPhase = SoundPhase.OFF
                if (selectedEffect != LedAnimationType.AMBIENT) releaseCapture()
            }
            !SoundPolicy.supports(selectedEffect) -> { stopAudio(); soundPhase = SoundPhase.PAUSED }
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> {
                stopAudio(); soundPhase = SoundPhase.NEEDS_PERMISSION
            }
            capture?.mediaProjection == null -> { stopAudio(); soundPhase = SoundPhase.NEEDS_CONSENT }
            audioFailed -> { soundPhase = SoundPhase.FAILED }
            audio == null -> {
                val projection = capture?.mediaProjection ?: return
                val epoch = ++audioEpoch
                ledController?.setMasterScale(0f)
                if (stopping || capture?.mediaProjection !== projection) return
                val recorder = PlaybackAudioCapture(this, projection, handler, { level ->
                    if (epoch == audioEpoch && !stopping) ledController?.setMasterScale(level)
                }, { phase ->
                    if (epoch == audioEpoch && !stopping) {
                        soundPhase = phase
                        if (phase == SoundPhase.FAILED) {
                            audio = null
                            audioFailed = true
                            ledController?.setMasterScale(1f)
                        }
                        publishSound()
                    }
                })
                audio = recorder
                recorder.start()
            }
        }
        publishSound()
    }

    private fun publishSound() {
        val state = SoundState(soundPhase, capture?.mediaProjection != null)
        if (mutableSound.value != state) mutableSound.value = state
    }

    private fun publishState() {
        if (mutableState.value != runtime.state) mutableState.value = runtime.state
        BifrostTileService.refreshFrom(this)
    }

    private fun cancelStartupTimeout() {
        startupTimeout?.let(handler::removeCallbacks)
        startupTimeout = null
    }

    private fun cleanupAndStop(stopService: Boolean = true, failure: LightingFailure? = null) {
        runtime.end(failure) // Invalidate callbacks before any teardown, including off writes.
        publishState()
        stopping = true
        cancelStartupTimeout()
        val controller = ledController
        ledController = null
        controller?.onOutputResult = null
        runCatching { controller?.shutdown() }.onFailure { Log.w(TAG, "LED shutdown failed", it) }
        runCatching { currentAnimation?.stop() }.onFailure { Log.w(TAG, "Effect teardown failed", it) }
        currentAnimation = null
        releaseCapture()
        soundPhase = SoundPhase.OFF
        publishSound()
        selectedEffect = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (stopService) stopSelf(latestStartId)
    }

    override fun onDestroy() {
        // Destruction is not a user Stop. Preserve wanted intent, never persist
        // resources or automatically resurrect the service from UI/boot callbacks.
        if (ownsSession && !stopping) cleanupAndStop(stopService = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground(capturing: Boolean = false) {
        val type = (if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0) or
            (if (capturing) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
        startForeground(NOTIFICATION_ID, createNotification(), type)
    }

    private fun createNotification(): Notification {
        val stopPending = PendingIntent.getService(this, 1,
            Intent(this, LEDService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openPending = PendingIntent.getActivity(this, 0,
            Intent(this, RunelightActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.runelight_notification_title))
            .setContentText(getString(R.string.runelight_notification_body))
            .setSmallIcon(R.drawable.ic_runelight)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, getString(R.string.runelight_stop), stopPending)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }
}
