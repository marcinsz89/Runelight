package com.moonbench.bifrost

import android.app.Activity
import android.app.StatusBarManager
import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.Icon
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.ambient.AmbientSamplingMode
import com.moonbench.bifrost.audio.SoundPolicy
import com.moonbench.bifrost.audio.SoundPhase
import com.moonbench.bifrost.audio.SoundMode
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.services.BifrostTileService
import com.moonbench.bifrost.services.LightingPhase
import com.moonbench.bifrost.services.LightingFailure
import com.moonbench.bifrost.services.LightingIntentStore
import com.moonbench.bifrost.ui.ColorLevelTile
import com.moonbench.bifrost.ui.EffectSelectionMotion
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlin.math.roundToInt

/** Native Quick tiles; the service owns lighting and authorized capture. */
class RunelightActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("runelight_controls", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val effectMotion = EffectSelectionMotion()
    private var controlsMenu: PopupMenu? = null
    private lateinit var lightingTile: MaterialCardView
    private lateinit var lightingState: TextView
    private lateinit var status: TextView
    private lateinit var leftLevelTile: ColorLevelTile
    private lateinit var rightLevelTile: ColorLevelTile
    private lateinit var speedTile: ColorLevelTile
    private var editingRight: Boolean? = null
    private var draftColor = Color.WHITE
    private var draftBrightness = 40
    private var previewAnimator: ValueAnimator? = null
    private var pendingColorPreview: Runnable? = null
    private var colorPreviewSent = false
    private var previewSuppressed = false
    private var uiVisible = false
    private val closeColorEditor = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            clearColorPreview(restore = true)
            editingRight = null
            renderControls()
        }
    }
    private var pendingNotificationStart = false
    private var captureTicket = -1L
    private var captureUpdatesOnly = false
    private var pendingAudioStart = false
    private var soundMode = SoundMode.OFF
    private val soundEnabled: Boolean get() = soundMode.enabled
    private var ambientSampling = AmbientSamplingMode.EDGES
    private val waitingForCapture: Boolean get() = LEDService.projectionRequests.isPending(captureTicket)

    private val captureConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (waitingForCapture && (effect == LedAnimationType.AMBIENT || (soundEnabled && SoundPolicy.supports(effect)))) {
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                if (captureUpdatesOnly && LEDService.isRunning) {
                    startService(parameterIntent().apply {
                        action = LEDService.ACTION_UPDATE_PARAMS
                        putExtra(LEDService.EXTRA_CAPTURE_GRANT, result.data)
                        putExtra(LEDService.EXTRA_CAPTURE_RESULT, result.resultCode)
                        putExtra(LEDService.EXTRA_CAPTURE_TICKET, captureTicket)
                    })
                } else if (!captureUpdatesOnly) startLighting(result.resultCode, result.data, skipAudioPermission = true)
            } else {
                LEDService.projectionRequests.cancel(captureTicket)
                captureTicket = -1L
                // Cancellation keeps a previously running plain effect usable.
                LEDService.runningEffect?.takeIf { !captureUpdatesOnly && it != effect }?.let { previous ->
                    effect = previous
                    findViewById<MaterialButtonToggleGroup>(R.id.effectButtons)
                        .check(effects.entries.first { it.value == previous }.key)
                    saveControls()
                    renderControls()
                }
                renderRuntime()
            }
        }
    }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (waitingForCapture) {
            LEDService.projectionRequests.cancel(captureTicket)
            captureTicket = -1L
            val start = pendingAudioStart
            pendingAudioStart = false
            if (start) startLighting(skipAudioPermission = true)
            else if (LEDService.isRunning) enableRunningSound()
        }
        renderRuntime()
    }
    private var startingAt = 0L
    private var startError = false
    private var runtimeRendered = false

    private val effects = linkedMapOf(
        R.id.effectAmbient to LedAnimationType.AMBIENT,
        R.id.effectSolid to LedAnimationType.STATIC,
        R.id.effectRainbow to LedAnimationType.RAINBOW,
        R.id.effectBreathing to LedAnimationType.BREATH,
        R.id.effectPulse to LedAnimationType.PULSE
    )
    private var effect = LedAnimationType.STATIC
    private var leftBrightness = 40
    private var rightBrightness = 40
    private var speed = 50
    private var leftColor = Color.rgb(182, 154, 244)
    private var rightColor = Color.rgb(116, 212, 213)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Denial does not prevent an Android 13 foreground service. Explicit Stop
        // cancels the request; recreating the host never issues a Start by itself.
        if (pendingNotificationStart) {
            pendingNotificationStart = false
            startLighting()
        }
    }

    private val startTimeout = Runnable { renderRuntime() }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = if (prefs.getBoolean("dark", true)) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_runelight)
        // Android owns the task's display. In particular, do not relaunch/finish
        // the task when Pathfinder or Android moves and recreates this Activity.
        effect = LedAnimationType.fromStoredName(prefs.getString("effect", null))
            ?.takeIf { it in effects.values } ?: LedAnimationType.STATIC
        // Seed both sides from the previous global level without losing existing settings.
        val previousBrightness = prefs.getInt("brightness", 40)
        leftBrightness = prefs.getInt("leftBrightness", previousBrightness).coerceIn(0, 100)
        rightBrightness = prefs.getInt("rightBrightness", previousBrightness).coerceIn(0, 100)
        ambientSampling = AmbientSamplingMode.fromStoredName(prefs.getString("ambientSampling", null)
            ?: if (prefs.getBoolean("wholeScreen", false)) "HALVES" else "EDGES")
        captureTicket = savedInstanceState?.getLong("captureTicket", -1L) ?: -1L
        captureUpdatesOnly = savedInstanceState?.getBoolean("captureUpdatesOnly") ?: false
        pendingAudioStart = savedInstanceState?.getBoolean("audioStart") ?: false
        soundMode = SoundMode.fromStoredName(prefs.getString("soundMode", null), prefs.getBoolean("soundEnabled", false))
        speed = prefs.getInt("speed", 50).coerceIn(0, 100)
        leftColor = prefs.getInt("leftColor", leftColor)
        rightColor = prefs.getInt("rightColor", rightColor)
        pendingNotificationStart = savedInstanceState?.getBoolean("notificationStart") ?: false
        startingAt = savedInstanceState?.getLong("startingAt") ?: 0L
        editingRight = if (savedInstanceState?.getBoolean("editingColor") == true) {
            savedInstanceState.getBoolean("editingRight")
        } else null
        draftColor = savedInstanceState?.getInt("draftColor") ?: leftColor
        draftBrightness = savedInstanceState?.getInt("draftBrightness") ?: if (editingRight == true) rightBrightness else leftBrightness

        lightingTile = findViewById(R.id.lightingTile)
        lightingState = findViewById(R.id.lightingState)
        status = findViewById(R.id.quickStatus)
        leftLevelTile = findViewById(R.id.quickLeftColor)
        rightLevelTile = findViewById(R.id.quickRightColor)
        leftLevelTile.label = getString(R.string.runelight_left)
        rightLevelTile.label = getString(R.string.runelight_right)
        onBackPressedDispatcher.addCallback(this, closeColorEditor)
        speedTile = findViewById(R.id.quickSpeed)
        speedTile.label = getString(R.string.runelight_speed)
        speedTile.colorEditable = false
        speedTile.color = ContextCompat.getColor(this, R.color.runelight_accent)

        lightingTile.setOnClickListener {
            if (LEDService.isRunning || pendingNotificationStart || waitingForCapture || startingAt != 0L) {
                previewSuppressed = true
                clearColorPreview(restore = false)
                pendingNotificationStart = false
                pendingAudioStart = false
                startingAt = 0L
                startError = false
                // Dispatch immediately: no Activity-owned debounce that pause
                // or a display move could cancel before Stop reaches the service.
                LEDService.requestStop(this)
                renderRuntime()
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                pendingNotificationStart = true
                renderRuntime()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startLighting()
            }
        }
        findViewById<View>(R.id.soundTile).setOnClickListener {
            if (!SoundPolicy.supports(effect)) return@setOnClickListener
            soundMode = soundMode.next()
            saveControls()
            updateParameters()
            if (soundEnabled) {
                if (!hasAudioPermission()) requestAudioPermission(startLighting = false)
                else if (LEDService.isRunning) enableRunningSound()
            } else if (captureUpdatesOnly) {
                LEDService.projectionRequests.cancel(captureTicket)
                captureTicket = -1L
            }
            renderRuntime()
        }
        status.setOnClickListener {
            if (soundEnabled && SoundPolicy.supports(effect) && LEDService.isRunning) {
                if (!hasAudioPermission()) requestAudioPermission(startLighting = false)
                else enableRunningSound()
            }
        }
        findViewById<MaterialButton>(R.id.appearanceButton).setOnClickListener {
            val dark = !prefs.getBoolean("dark", true)
            prefs.edit { putBoolean("dark", dark) }
            delegate.localNightMode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        }
        findViewById<View>(R.id.quickMenuButton).setOnClickListener(::showControlsMenu)

        val group = findViewById<MaterialButtonToggleGroup>(R.id.effectButtons)
        group.check(effects.entries.first { it.value == effect }.key)
        group.addOnButtonCheckedListener { _, checkedId, checked ->
            val next = effects[checkedId]
            if (next != null) effectMotion.select(findViewById(checkedId))
            if (checked && next != null && next != effect) {
                LEDService.projectionRequests.cancel(captureTicket)
                captureTicket = -1L
                pendingAudioStart = false
                effect = next
                editingRight = null
                findViewById<View>(R.id.quickColorEditor).visibility = View.GONE
                saveControls()
                renderControls()
                if (LEDService.isRunning) startLighting()
                renderRuntime()
            }
        }
        findViewById<View>(R.id.quickAmbientSampling).setOnClickListener {
            ambientSampling = ambientSampling.next()
            saveControls()
            renderControls()
            updateParameters()
        }
        leftLevelTile.level = leftBrightness
        rightLevelTile.level = rightBrightness
        leftLevelTile.onLevelChanged = { value ->
            leftBrightness = value
            saveControls()
            updateParameters()
        }
        rightLevelTile.onLevelChanged = { value ->
            rightBrightness = value
            saveControls()
            updateParameters()
        }
        speedTile.level = speed
        speedTile.onLevelChanged = { value ->
            speed = value
            saveControls()
            updateParameters()
        }
        leftLevelTile.setOnClickListener { if (effect.needsColorSelection) editColor(false) }
        rightLevelTile.setOnClickListener { if (effect.needsColorSelection) editColor(true) }
        findViewById<ColorPickerView>(R.id.quickColorPicker).apply {
            setLifecycleOwner(this@RunelightActivity)
            setColorListener(ColorEnvelopeListener { envelope, fromUser ->
                if (fromUser && editingRight != null) {
                    draftColor = envelope.color
                    renderColorPreview(animate = true)
                }
            })
        }
        findViewById<MaterialButton>(R.id.quickApplyColor).setOnClickListener {
            clearColorPreview(restore = false)
            when (editingRight) {
                true -> {
                    rightColor = draftColor
                    rightBrightness = draftBrightness
                    rightLevelTile.level = rightBrightness
                }
                false -> {
                    leftColor = draftColor
                    leftBrightness = draftBrightness
                    leftLevelTile.level = leftBrightness
                }
                null -> return@setOnClickListener
            }
            editingRight = null
            findViewById<View>(R.id.quickColorEditor).visibility = View.GONE
            saveControls()
            renderControls()
            updateParameters()
        }
        findViewById<MaterialButton>(R.id.quickCancelColor).setOnClickListener {
            clearColorPreview(restore = true)
            editingRight = null
            renderControls()
        }
        findViewById<MaterialButton>(R.id.quickCopyOther).setOnClickListener {
            if (editingRight == null) return@setOnClickListener
            draftColor = if (editingRight == true) leftColor else rightColor
            draftBrightness = if (editingRight == true) leftBrightness else rightBrightness
            findViewById<ColorPickerView>(R.id.quickColorPicker).setInitialColor(draftColor)
            renderColorPreview(animate = true)
        }
        if (editingRight != null) renderColorEditor()
        renderControls()
        LEDService.state.observe(this) { renderRuntime() }
        LEDService.soundState.observe(this) { renderRuntime() }
    }

    override fun onStart() {
        super.onStart()
        uiVisible = true
        renderRuntime()
        if (startingAt != 0L) handler.postDelayed(startTimeout,
            (5001L - (SystemClock.elapsedRealtime() - startingAt)).coerceAtLeast(0L))
        requestColorPreview()
    }

    override fun onStop() {
        controlsMenu?.dismiss()
        controlsMenu = null
        effectMotion.finish()
        uiVisible = false
        clearColorPreview(restore = true)
        handler.removeCallbacks(startTimeout)
        super.onStop()
    }

    private fun showControlsMenu(anchor: View) {
        controlsMenu?.dismiss()
        controlsMenu = PopupMenu(this, anchor).also { menu ->
            menu.inflate(R.menu.runelight_controls)
            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.addQuickSettingsShortcut -> requestQuickSettingsShortcut()
                    R.id.notificationSettings -> try {
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                    } catch (_: android.content.ActivityNotFoundException) {
                        Toast.makeText(this, R.string.runelight_notification_settings_unavailable, Toast.LENGTH_LONG).show()
                    }
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            menu.setOnDismissListener { if (controlsMenu === menu) controlsMenu = null }
            menu.show()
        }
    }

    private fun requestQuickSettingsShortcut() {
        val manager = getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            showShortcutResult(R.string.runelight_shortcut_manual)
            return
        }
        try {
            // A deliberate foreground action only; never prompt on launch/wake.
            manager.requestAddTileService(ComponentName(this, BifrostTileService::class.java),
                getString(R.string.app_name), Icon.createWithResource(this, R.drawable.ic_runelight),
                mainExecutor) { result ->
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                        BifrostTileService.refreshFrom(this)
                        showShortcutResult(R.string.runelight_shortcut_added)
                    }
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> Unit // Respect cancellation quietly.
                    else -> showShortcutResult(R.string.runelight_shortcut_manual)
                }
            }
        } catch (_: RuntimeException) {
            showShortcutResult(R.string.runelight_shortcut_manual)
        }
    }

    private fun showShortcutResult(message: Int) {
        if (!isDestroyed && !isFinishing && uiVisible)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("captureUpdatesOnly", captureUpdatesOnly)
        outState.putBoolean("audioStart", pendingAudioStart)
        outState.putLong("captureTicket", captureTicket)
        outState.putBoolean("notificationStart", pendingNotificationStart)
        outState.putLong("startingAt", startingAt)
        outState.putBoolean("editingColor", editingRight != null)
        outState.putBoolean("editingRight", editingRight ?: false)
        outState.putInt("draftColor", draftColor)
        outState.putInt("draftBrightness", draftBrightness)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        effectMotion.finish()
        clearColorPreview(restore = false)
        previewAnimator?.cancel()
        handler.removeCallbacks(startTimeout)
        if (isFinishing) LEDService.projectionRequests.cancel(captureTicket)
        super.onDestroy()
    }

    private fun saveControls() {
        prefs.edit {
            putString("soundMode", soundMode.name)
            remove("soundEnabled")
            putString("ambientSampling", ambientSampling.name)
            remove("wholeScreen")
            putString("effect", effect.name)
            putInt("leftBrightness", leftBrightness)
            putInt("rightBrightness", rightBrightness)
            putInt("speed", speed)
            putInt("leftColor", leftColor)
            putInt("rightColor", rightColor)
        }
    }

    private fun hasAudioPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestAudioPermission(startLighting: Boolean) {
        if (waitingForCapture) return
        pendingAudioStart = startLighting
        captureUpdatesOnly = !startLighting
        captureTicket = LEDService.projectionRequests.begin()
        audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun enableRunningSound() {
        if (!soundEnabled || !SoundPolicy.supports(effect) || !hasAudioPermission()) return
        if (!LEDService.hasCaptureSession) requestCapture(updateOnly = true)
        else updateParameters()
    }

    private fun requestCapture(updateOnly: Boolean) {
        if (waitingForCapture) return
        captureUpdatesOnly = updateOnly
        captureTicket = LEDService.projectionRequests.begin()
        startError = false
        renderRuntime()
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val request = if (Build.VERSION.SDK_INT >= 34)
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            else manager.createScreenCaptureIntent()
            captureConsent.launch(request)
        } catch (_: RuntimeException) {
            LEDService.projectionRequests.cancel(captureTicket)
            startError = true
            renderRuntime()
        }
    }

    private fun startLighting(resultCode: Int = 0, grant: Intent? = null, skipAudioPermission: Boolean = false) {
        val wantsAudio = soundEnabled && SoundPolicy.supports(effect)
        if (wantsAudio && !hasAudioPermission() && !skipAudioPermission) {
            requestAudioPermission(startLighting = true)
            return
        }
        if (grant == null && (effect == LedAnimationType.AMBIENT || (wantsAudio && hasAudioPermission())) && !LEDService.hasCaptureSession) {
            requestCapture(updateOnly = false)
            return
        }
        if (grant == null && LEDService.runningEffect == effect) { updateParameters(); return }
        previewSuppressed = false
        startingAt = SystemClock.elapsedRealtime()
        startError = false
        try {
            ContextCompat.startForegroundService(this, parameterIntent().apply {
                action = LEDService.ACTION_START
                putExtra("animationType", effect.name)
                if (grant != null) {
                    putExtra(LEDService.EXTRA_CAPTURE_GRANT, grant)
                    putExtra(LEDService.EXTRA_CAPTURE_RESULT, resultCode)
                    putExtra(LEDService.EXTRA_CAPTURE_TICKET, captureTicket)
                }
            })
            handler.removeCallbacks(startTimeout)
            handler.postDelayed(startTimeout, 5001L)
        } catch (_: RuntimeException) {
            startingAt = 0L
            startError = true
        }
        renderRuntime()
    }

    private fun parameterIntent() = Intent(this, LEDService::class.java).apply {
        putExtra(LEDService.EXTRA_SOUND_MODE, soundMode.name)
        putExtra(LEDService.EXTRA_AMBIENT_SAMPLING, ambientSampling.name)
        putExtra("animationColor", leftColor)
        putExtra("animationRightColor", rightColor)
        putExtra("brightness", 255)
        putExtra(LEDService.EXTRA_LEFT_BRIGHTNESS_PERCENT, leftBrightness)
        putExtra(LEDService.EXTRA_RIGHT_BRIGHTNESS_PERCENT, rightBrightness)
        putExtra("speed", speed / 100f)
        putExtra("smoothness", 0.5f)
    }

    private fun updateParameters() {
        if (LEDService.isRunning) startService(parameterIntent().setAction(LEDService.ACTION_UPDATE_PARAMS))
    }

    private fun editColor(right: Boolean) {
        editingRight = right
        draftColor = if (right) rightColor else leftColor
        draftBrightness = if (right) rightBrightness else leftBrightness
        renderColorEditor()
    }

    private fun requestColorPreview() {
        if (!uiVisible || editingRight == null || previewSuppressed || !LEDService.isRunning || pendingColorPreview != null) return
        pendingColorPreview = Runnable {
            pendingColorPreview = null
            val right = editingRight
            if (uiVisible && right != null && !previewSuppressed && LEDService.isRunning && effect.needsColorSelection) {
                startService(parameterIntent().setAction(LEDService.ACTION_UPDATE_PARAMS).apply {
                    putExtra(if (right) "animationRightColor" else "animationColor", draftColor)
                    putExtra(if (right) LEDService.EXTRA_RIGHT_BRIGHTNESS_PERCENT else LEDService.EXTRA_LEFT_BRIGHTNESS_PERCENT, draftBrightness)
                })
                colorPreviewSent = true
            }
        }.also { handler.postDelayed(it, 32) }
    }

    private fun clearColorPreview(restore: Boolean) {
        pendingColorPreview?.let(handler::removeCallbacks)
        pendingColorPreview = null
        if (colorPreviewSent && restore) updateParameters()
        colorPreviewSent = false
    }

    private fun renderEditorVisibility() {
        val editing = editingRight != null
        findViewById<View>(R.id.effectButtons).visibility = if (editing) View.GONE else View.VISIBLE
        findViewById<View>(R.id.quickConfiguration).visibility = if (editing) View.GONE else View.VISIBLE
        findViewById<View>(R.id.quickColorEditor).visibility = if (editing) View.VISIBLE else View.GONE
        closeColorEditor.isEnabled = editing
        if (!editing) previewAnimator?.cancel()
    }

    private fun renderColorEditor() {
        renderEditorVisibility()
        findViewById<TextView>(R.id.quickColorTitle).setText(
            if (editingRight == true) R.string.runelight_right_color else R.string.runelight_left_color
        )
        findViewById<MaterialButton>(R.id.quickCopyOther).apply {
            setText(if (editingRight == true) R.string.runelight_copy_left else R.string.runelight_copy_right)
            contentDescription = getString(if (editingRight == true) R.string.runelight_copy_left_settings else R.string.runelight_copy_right_settings)
        }
        findViewById<ColorPickerView>(R.id.quickColorPicker).setInitialColor(draftColor)
        renderColorPreview()
    }

    private fun renderColorPreview(animate: Boolean = false) {
        val preview = findViewById<View>(R.id.quickColorPreview)
        val background = (preview.background as? GradientDrawable) ?: GradientDrawable().also {
            it.cornerRadius = 16 * resources.displayMetrics.density
            preview.background = it
        }
        previewAnimator?.cancel()
        val previous = background.color?.defaultColor ?: draftColor
        findViewById<TextView>(R.id.quickColorHex).text = String.format("#%06X", draftColor and 0xFFFFFF)
        findViewById<TextView>(R.id.quickColorLevel).text = getString(R.string.runelight_percent, draftBrightness)
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            previewAnimator = ValueAnimator.ofArgb(previous, draftColor).apply {
                duration = 100
                addUpdateListener { background.setColor(it.animatedValue as Int) }
                start()
            }
        } else background.setColor(draftColor)
        requestColorPreview()
    }

    private fun renderControls() {
        renderEditorVisibility()
        val ambient = effect == LedAnimationType.AMBIENT
        speedTile.visibility = if (effect.supportsSpeed && !ambient) View.VISIBLE else View.GONE
        findViewById<View>(R.id.quickAmbientSampling).apply {
            visibility = if (ambient) View.VISIBLE else View.GONE
            isActivated = true
            contentDescription = getString(R.string.runelight_sampling_description, samplingLabel())
        }
        findViewById<TextView>(R.id.quickAmbientSamplingState).text = samplingLabel()
        listOf(R.id.samplingEdgeDot, R.id.samplingHalvesDot, R.id.samplingFullDot).forEachIndexed { index, id ->
            findViewById<View>(id).alpha = if (index == ambientSampling.ordinal) 1f else 0.25f
        }
        leftLevelTile.colorEditable = effect.needsColorSelection
        rightLevelTile.colorEditable = effect.needsColorSelection
        leftLevelTile.color = if (effect.needsColorSelection) leftColor else ContextCompat.getColor(this, R.color.runelight_accent)
        rightLevelTile.color = if (effect.needsColorSelection) rightColor else ContextCompat.getColor(this, R.color.runelight_accent)
    }

    private fun samplingLabel() = getString(when (ambientSampling) {
        AmbientSamplingMode.EDGES -> R.string.runelight_sampling_edges
        AmbientSamplingMode.HALVES -> R.string.runelight_sampling_halves
        AmbientSamplingMode.FULL_SCREEN -> R.string.runelight_sampling_full
    })

    private fun renderRuntime() {
        val runtime = LEDService.state.value
        val sound = LEDService.soundState.value
        val supportsSound = SoundPolicy.supports(effect)
        val soundUnavailable = LEDService.isRunning && sound?.phase in setOf(SoundPhase.NEEDS_CONSENT, SoundPhase.NEEDS_PERMISSION, SoundPhase.FAILED)
        findViewById<View>(R.id.soundTile).apply {
            isEnabled = supportsSound
            alpha = if (supportsSound) 1f else 0.4f
            isActivated = soundEnabled && (!supportsSound || !LEDService.isRunning || sound?.phase == SoundPhase.ACTIVE)
            contentDescription = getString(if (!supportsSound) R.string.runelight_sound_paused_description else R.string.runelight_sound_toggle_description,
                getString(soundModeLabel()))
        }
        findViewById<TextView>(R.id.soundState).setText(when {
            soundEnabled && supportsSound && soundUnavailable -> R.string.runelight_paused
            soundEnabled && supportsSound && LEDService.isRunning && sound?.phase == SoundPhase.STARTING -> R.string.runelight_starting
            else -> soundModeLabel()
        })
        listOf(R.id.soundOffDot, R.id.soundLevelDot, R.id.soundBeatDot).forEachIndexed { index, id ->
            findViewById<View>(id).alpha = if (index == soundMode.ordinal) 1f else 0.25f
        }
        val active = runtime?.phase == LightingPhase.RUNNING
        if (active && editingRight != null && !colorPreviewSent) requestColorPreview()
        if (lightingTile.isActivated != active) {
            val surface = lightingTile.getChildAt(0).background as? StateListDrawable
            val duration = if (runtimeRendered && ValueAnimator.areAnimatorsEnabled()) 160 else 0
            surface?.setEnterFadeDuration(duration)
            surface?.setExitFadeDuration(duration)
            lightingTile.isActivated = active
        }
        if (!runtimeRendered) {
            lightingTile.stateListAnimator?.jumpToCurrentState()
            lightingTile.getChildAt(0).jumpDrawablesToCurrentState()
        }
        runtimeRendered = true
        if (active || runtime?.phase == LightingPhase.FAILED) {
            startingAt = 0L
            handler.removeCallbacks(startTimeout)
        }
        if (startingAt != 0L && SystemClock.elapsedRealtime() - startingAt > 5000L) {
            startingAt = 0L
            startError = true
        }
        val starting = pendingNotificationStart || (waitingForCapture && !captureUpdatesOnly) || startingAt != 0L || (LEDService.isRunning && !active)
        lightingState.setText(when {
            active -> R.string.runelight_on
            starting -> R.string.runelight_starting
            else -> R.string.runelight_stopped
        })
        val canStop = active || starting
        lightingTile.contentDescription = getString(if (canStop) R.string.runelight_stop else R.string.runelight_start)
        val error = when {
            runtime?.failure == LightingFailure.OUTPUT_UNAVAILABLE -> R.string.runelight_output_unavailable
            runtime?.failure == LightingFailure.CAPTURE_LOST -> R.string.runelight_capture_lost
            runtime?.failure == LightingFailure.START_FAILED || startError -> R.string.runelight_start_failed
            soundEnabled && supportsSound && sound?.phase == SoundPhase.NEEDS_PERMISSION -> R.string.runelight_sound_permission
            soundEnabled && supportsSound && sound?.phase == SoundPhase.NEEDS_CONSENT -> R.string.runelight_sound_consent
            soundEnabled && supportsSound && sound?.phase == SoundPhase.FAILED -> R.string.runelight_sound_failed
            runtime?.phase == LightingPhase.STOPPED && !starting && LightingIntentStore(this).wantedRunning -> R.string.runelight_interrupted
            else -> null
        }
        status.visibility = if (error == null) View.GONE else View.VISIBLE
        error?.let(status::setText)
    }

    private fun soundModeLabel() = when (soundMode) {
        SoundMode.OFF -> R.string.runelight_off
        SoundMode.LEVEL -> R.string.runelight_sound_flow
        SoundMode.BEAT -> R.string.runelight_beat
    }
}
