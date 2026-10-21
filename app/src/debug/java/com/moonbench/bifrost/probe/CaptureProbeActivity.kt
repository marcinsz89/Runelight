package com.moonbench.bifrost.probe

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.*

/** Debug only: explicit commands and observation; no ownership of capture resources. */
class CaptureProbeActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var displays: Spinner
    private lateinit var sound: CheckBox
    private var ids = emptyList<Int>()
    private var pending: Intent? = null
    private var promptOutstanding = false
    private val refresh = object : Runnable {
        override fun run() {
            status.text = "UI display=${display?.displayId}\n" +
                runCatching { java.io.File(filesDir, "probe-status.json").readText() }.getOrDefault("Idle")
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ids = getSystemService(DisplayManager::class.java).displays.map { it.displayId }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 12) }
        column.addView(TextView(this).apply { text = "Runelight debug capture probe • no LED output\nProjection source is NOT selectable by display ID; selected ID sets requested dimensions only. Accessibility uses the selected ID." })
        displays = Spinner(this).apply { adapter = ArrayAdapter(this@CaptureProbeActivity, android.R.layout.simple_spinner_dropdown_item, ids.map { "Logical display $it" }) }
        column.addView(displays)
        sound = CheckBox(this).apply { text = "Playback audio (never microphone)" }
        column.addView(sound)
        fun button(label: String, command: String) = column.addView(Button(this).apply {
            text = label
            setOnClickListener { execute(Intent().putExtra("command", command).putExtra("source_display", ids.getOrElse(displays.selectedItemPosition) { 0 }).putExtra("audio", sound.isChecked)) }
        })
        button("Projection • explicit consent", "projection")
        button("Accessibility • one sample", "snapshot")
        button("Accessibility • 500 ms cadence", "accessibility")
        column.addView(Button(this).apply { text = "Enable debug Accessibility in Settings"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
        button("Sound off", "audio_off")
        button("Stop", "stop")
        status = TextView(this).apply { textSize = 12f }
        column.addView(status)
        setContentView(ScrollView(this).apply { addView(column) })
        pending = savedInstanceState?.getParcelable("pending", Intent::class.java)
        promptOutstanding = savedInstanceState?.getBoolean("prompt_outstanding", false) ?: false
        if (savedInstanceState == null) execute(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); execute(intent) }
    override fun onSaveInstanceState(outState: Bundle) { outState.putParcelable("pending", pending); outState.putBoolean("prompt_outstanding", promptOutstanding); super.onSaveInstanceState(outState) }
    override fun onStart() { super.onStart(); main.post(refresh) }
    override fun onStop() { main.removeCallbacks(refresh); super.onStop() }

    private fun execute(input: Intent) {
        val command = input.getStringExtra("command") ?: return
        val source = input.getIntExtra("source_display", ids.firstOrNull() ?: 0)
        ids.indexOf(source).takeIf { it >= 0 }?.let { displays.setSelection(it) }
        sound.isChecked = input.getBooleanExtra("audio", false)
        if (command == "status") return
        val request = Intent(this, CaptureProbeService::class.java).putExtras(input.extras ?: Bundle())
        if (command == "stop" || command == "audio_off") {
            pending = null
            startService(request)
            return
        }
        if (command !in listOf("projection", "accessibility", "snapshot")) return
        if (pending != null || promptOutstanding) { Toast.makeText(this, "Finish the pending consent request first", Toast.LENGTH_SHORT).show(); return }
        pending = request
        promptOutstanding = true
        if (sound.isChecked && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 71)
        } else requestCapture()
    }

    private fun requestCapture() {
        val request = pending ?: run { promptOutstanding = false; return }
        if (request.getStringExtra("command") == "projection" || request.getBooleanExtra("audio", false)) {
            startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 72)
        } else {
            pending = null
            promptOutstanding = false
            startForegroundService(request)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 71) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) requestCapture()
            else { pending = null; promptOutstanding = false; Toast.makeText(this, "Playback audio permission denied", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 72) return
        val request = pending
        pending = null
        promptOutstanding = false
        if (request != null && resultCode == RESULT_OK && data != null) {
            startForegroundService(request.putExtra("grant_code", resultCode).putExtra("grant", data))
        } else Toast.makeText(this, "Capture consent cancelled", Toast.LENGTH_LONG).show()
    }
}
