package com.moonbench.bifrost

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.services.LEDService
import kotlin.math.roundToInt

/** First native Quick tiles increment. Capture and sound await their own engine work. */
class RunelightActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("runelight_controls", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var lightingTile: MaterialCardView
    private lateinit var lightingState: TextView
    private lateinit var lightingAction: TextView
    private lateinit var status: TextView
    private lateinit var brightnessSlider: Slider
    private lateinit var speedSlider: Slider
    private var editingRight: Boolean? = null
    private var draftColor = Color.WHITE
    private var syncingColorEditor = false
    private var pendingNotificationStart = false
    private var startingAt = 0L
    private var startError = false

    private val effects = linkedMapOf(
        R.id.effectSolid to LedAnimationType.STATIC,
        R.id.effectRainbow to LedAnimationType.RAINBOW,
        R.id.effectBreathing to LedAnimationType.BREATH,
        R.id.effectPulse to LedAnimationType.PULSE
    )
    private var effect = LedAnimationType.STATIC
    private var brightness = 40
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

    private val observeService = object : Runnable {
        override fun run() {
            renderRuntime()
            handler.postDelayed(this, 300L)
        }
    }

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
        brightness = prefs.getInt("brightness", 40).coerceIn(0, 100)
        speed = prefs.getInt("speed", 50).coerceIn(0, 100)
        leftColor = prefs.getInt("leftColor", leftColor)
        rightColor = prefs.getInt("rightColor", rightColor)
        pendingNotificationStart = savedInstanceState?.getBoolean("notificationStart") ?: false
        startingAt = savedInstanceState?.getLong("startingAt") ?: 0L
        editingRight = if (savedInstanceState?.getBoolean("editingColor") == true) {
            savedInstanceState.getBoolean("editingRight")
        } else null
        draftColor = savedInstanceState?.getInt("draftColor") ?: leftColor

        lightingTile = findViewById(R.id.lightingTile)
        lightingState = findViewById(R.id.lightingState)
        lightingAction = findViewById(R.id.lightingAction)
        status = findViewById(R.id.quickStatus)
        brightnessSlider = findViewById(R.id.quickBrightness)
        speedSlider = findViewById(R.id.quickSpeed)

        lightingTile.setOnClickListener {
            if (LEDService.isRunning || pendingNotificationStart || startingAt != 0L) {
                pendingNotificationStart = false
                startingAt = 0L
                startError = false
                // Dispatch immediately: no Activity-owned debounce that pause
                // or a display move could cancel before Stop reaches the service.
                stopService(Intent(this, LEDService::class.java))
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
        findViewById<MaterialButton>(R.id.appearanceButton).setOnClickListener {
            val dark = !prefs.getBoolean("dark", true)
            prefs.edit { putBoolean("dark", dark) }
            delegate.localNightMode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        }

        val group = findViewById<MaterialButtonToggleGroup>(R.id.effectButtons)
        // ToggleGroup applies its enabled state when adding children, so disable
        // unavailable capture after inflation as well as in the XML.
        findViewById<MaterialButton>(R.id.effectAmbient).apply {
            isEnabled = false
            alpha = 0.45f
        }
        group.check(effects.entries.first { it.value == effect }.key)
        group.addOnButtonCheckedListener { _, checkedId, checked ->
            val next = effects[checkedId]
            if (checked && next != null && next != effect) {
                effect = next
                editingRight = null
                findViewById<View>(R.id.quickColorEditor).visibility = View.GONE
                saveControls()
                renderControls()
                if (LEDService.isRunning) startLighting()
            }
        }
        brightnessSlider.value = brightness.toFloat()
        brightnessSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                brightness = value.roundToInt()
                saveControls()
                renderBrightness()
                updateParameters()
            }
        }
        speedSlider.value = speed.toFloat()
        speedSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                speed = value.roundToInt()
                saveControls()
                updateParameters()
            }
        }
        findViewById<MaterialButton>(R.id.quickLeftColor).setOnClickListener { editColor(false) }
        findViewById<MaterialButton>(R.id.quickRightColor).setOnClickListener { editColor(true) }
        for (id in listOf(R.id.quickRed, R.id.quickGreen, R.id.quickBlue)) {
            findViewById<Slider>(id).addOnChangeListener { _, _, _ ->
                if (!syncingColorEditor) {
                    draftColor = Color.rgb(
                        findViewById<Slider>(R.id.quickRed).value.roundToInt(),
                        findViewById<Slider>(R.id.quickGreen).value.roundToInt(),
                        findViewById<Slider>(R.id.quickBlue).value.roundToInt()
                    )
                    findViewById<View>(R.id.quickColorPreview).setBackgroundColor(draftColor)
                }
            }
        }
        findViewById<MaterialButton>(R.id.quickApplyColor).setOnClickListener {
            when (editingRight) {
                true -> rightColor = draftColor
                false -> leftColor = draftColor
                null -> return@setOnClickListener
            }
            editingRight = null
            findViewById<View>(R.id.quickColorEditor).visibility = View.GONE
            saveControls()
            renderControls()
            updateParameters()
        }
        findViewById<MaterialButton>(R.id.quickCancelColor).setOnClickListener {
            editingRight = null
            findViewById<View>(R.id.quickColorEditor).visibility = View.GONE
        }
        if (editingRight != null) renderColorEditor()
        renderControls()
    }

    override fun onStart() {
        super.onStart()
        handler.post(observeService)
    }

    override fun onStop() {
        handler.removeCallbacks(observeService)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notificationStart", pendingNotificationStart)
        outState.putLong("startingAt", startingAt)
        outState.putBoolean("editingColor", editingRight != null)
        outState.putBoolean("editingRight", editingRight ?: false)
        outState.putInt("draftColor", draftColor)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacks(observeService)
        super.onDestroy()
    }

    private fun saveControls() {
        prefs.edit {
            putString("effect", effect.name)
            putInt("brightness", brightness)
            putInt("speed", speed)
            putInt("leftColor", leftColor)
            putInt("rightColor", rightColor)
        }
    }

    private fun startLighting() {
        startingAt = SystemClock.elapsedRealtime()
        startError = false
        try {
            ContextCompat.startForegroundService(this, parameterIntent().apply {
                putExtra("animationType", effect.name)
                putExtra("performanceProfile", "HIGH")
                putExtra(LEDService.EXTRA_ALLOW_BACKGROUND_RUN, true)
            })
        } catch (_: RuntimeException) {
            startingAt = 0L
            startError = true
        }
        renderRuntime()
    }

    private fun parameterIntent() = Intent(this, LEDService::class.java).apply {
        putExtra("animationColor", leftColor)
        putExtra("animationRightColor", rightColor)
        putExtra("brightness", (brightness * 255 / 100f).roundToInt())
        putExtra("speed", speed / 100f)
        putExtra("smoothness", 0.5f)
    }

    private fun updateParameters() {
        if (LEDService.isRunning) startService(parameterIntent().setAction(LEDService.ACTION_UPDATE_PARAMS))
    }

    private fun editColor(right: Boolean) {
        editingRight = right
        draftColor = if (right) rightColor else leftColor
        renderColorEditor()
        findViewById<ScrollView>(R.id.quickScroll).post {
            findViewById<ScrollView>(R.id.quickScroll).fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun renderColorEditor() {
        findViewById<View>(R.id.quickColorEditor).visibility = View.VISIBLE
        findViewById<TextView>(R.id.quickColorTitle).setText(
            if (editingRight == true) R.string.runelight_right_color else R.string.runelight_left_color
        )
        syncingColorEditor = true
        findViewById<Slider>(R.id.quickRed).value = Color.red(draftColor).toFloat()
        findViewById<Slider>(R.id.quickGreen).value = Color.green(draftColor).toFloat()
        findViewById<Slider>(R.id.quickBlue).value = Color.blue(draftColor).toFloat()
        syncingColorEditor = false
        findViewById<View>(R.id.quickColorPreview).setBackgroundColor(draftColor)
    }

    private fun renderControls() {
        findViewById<View>(R.id.quickColorControls).visibility = if (effect.needsColorSelection) View.VISIBLE else View.GONE
        findViewById<View>(R.id.quickSpeedControls).visibility = if (effect.supportsSpeed) View.VISIBLE else View.GONE
        for ((id, color) in listOf(R.id.quickLeftColor to leftColor, R.id.quickRightColor to rightColor)) {
            findViewById<MaterialButton>(id).strokeColor = ColorStateList.valueOf(color)
            findViewById<MaterialButton>(id).strokeWidth = (3 * resources.displayMetrics.density).roundToInt()
        }
        renderBrightness()
    }

    private fun renderBrightness() {
        findViewById<TextView>(R.id.quickBrightnessValue).text = getString(R.string.runelight_percent, brightness)
    }

    private fun renderRuntime() {
        val active = LEDService.runningEffect != null && LEDService.isRunning
        if (active) startingAt = 0L
        if (startingAt != 0L && SystemClock.elapsedRealtime() - startingAt > 5000L) {
            startingAt = 0L
            startError = true
        }
        val starting = pendingNotificationStart || startingAt != 0L || (LEDService.isRunning && !active)
        lightingState.setText(when {
            active -> R.string.runelight_on
            starting -> R.string.runelight_starting
            else -> R.string.runelight_stopped
        })
        val canStop = active || starting
        lightingAction.setText(if (canStop) R.string.runelight_stop else R.string.runelight_start)
        lightingTile.contentDescription = getString(if (canStop) R.string.runelight_stop else R.string.runelight_start)
        status.setText(when {
            LEDService.outputAvailable == false -> R.string.runelight_output_unavailable
            startError -> R.string.runelight_start_failed
            else -> R.string.runelight_ambient_pending
        })
    }
}
