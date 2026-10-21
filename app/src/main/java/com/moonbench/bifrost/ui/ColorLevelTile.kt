package com.moonbench.bifrost.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.moonbench.bifrost.R
import kotlin.math.abs
import kotlin.math.roundToInt

/** A color tile: tap to edit color, drag vertically to adjust this side's level. */
class ColorLevelTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var level: Int = 40
        set(value) {
            field = value.coerceIn(0, 100)
            stateDescription = context.getString(R.string.runelight_percent, field)
            invalidate()
        }
    var color: Int = Color.WHITE
        set(value) { field = value; updateSurface(); invalidate() }
    var label: String = ""
        set(value) { field = value; invalidate() }
    var colorEditable: Boolean = true
        set(value) { field = value; isClickable = value; invalidate() }
    var onLevelChanged: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Path()
    private val bounds = RectF()
    private var surfaceShader: Shader? = null
    private var fillShader: Shader? = null
    private var fillText = Color.BLACK
    private val darkAppearance = resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val fillTransform = Matrix()
    private val edgeColor = ContextCompat.getColor(context, R.color.runelight_edge)
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val regularTypeface = ResourcesCompat.getFont(context, R.font.runelight) ?: Typeface.DEFAULT
    private val boldTypeface = Typeface.create(regularTypeface, Typeface.BOLD)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var downLevel = 0
    private var dragging = false

    init {
        isFocusable = true
        isClickable = true
        stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(context, R.animator.runelight_tile_press)
        minimumHeight = (96 * density).roundToInt()
        level = 40
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        outline.reset()
        outline.addRoundRect(bounds, 16 * density, 16 * density, Path.Direction.CW)
        updateSurface()
    }

    private fun updateSurface() {
        if (width == 0 || height == 0) return
        var top = ColorUtils.blendARGB(ContextCompat.getColor(context, R.color.runelight_card_top), color, 0.10f)
        var bottom = ColorUtils.blendARGB(ContextCompat.getColor(context, R.color.runelight_card_bottom), color, 0.06f)
        if (darkAppearance) {
            top = ColorUtils.blendARGB(top, Color.WHITE, 0.08f)
            bottom = ColorUtils.blendARGB(bottom, Color.BLACK, 0.08f)
        }
        surfaceShader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        var fillTop = ColorUtils.blendARGB(color, Color.WHITE, if (darkAppearance) 0.18f else 0.10f)
        var fillBottom = ColorUtils.blendARGB(color, Color.BLACK, if (darkAppearance) 0.28f else 0.14f)
        fillText = if (ColorUtils.calculateContrast(Color.WHITE, color) >= 4.5) Color.WHITE else Color.BLACK
        // Keep the original color at an endpoint if shading would make labels
        // unreadable, especially for midtone gray selections.
        fun readableEndpoint(candidate: Int): Int {
            var endpoint = candidate
            if (darkAppearance) repeat(4) {
                if (ColorUtils.calculateContrast(fillText, endpoint) < 4.5)
                    endpoint = ColorUtils.blendARGB(endpoint, color, 0.5f)
            }
            return if (ColorUtils.calculateContrast(fillText, endpoint) >= 4.5) endpoint else color
        }
        fillTop = readableEndpoint(fillTop)
        fillBottom = readableEndpoint(fillBottom)
        fillShader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(fillTop, color, fillBottom), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val text = ContextCompat.getColor(context, R.color.runelight_text)
        val fillTop = height * (1 - level / 100f)
        canvas.save()
        canvas.clipPath(outline)
        paint.shader = surfaceShader
        canvas.drawRect(bounds, paint)
        paint.shader = null
        drawLabels(canvas, text)
        canvas.save()
        canvas.clipRect(0f, fillTop, width.toFloat(), height.toFloat())
        if (darkAppearance) {
            // Use the entire tonal range inside the visible fill. A low level
            // must not reveal only the nearly flat bottom of a full-tile shader.
            fillTransform.setScale(1f, (height - fillTop).coerceAtLeast(1f) / height.coerceAtLeast(1))
            fillTransform.postTranslate(0f, fillTop)
            fillShader?.setLocalMatrix(fillTransform)
        }
        paint.shader = fillShader
        canvas.drawRect(bounds, paint)
        paint.shader = null
        if (level in 1..99) {
            paint.color = 0x45FFFFFF
            canvas.drawRect(0f, fillTop, width.toFloat(), fillTop + density, paint)
        }
        drawLabels(canvas, fillText)
        canvas.restore()
        canvas.restore()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = ColorUtils.blendARGB(edgeColor, color, 0.35f)
        val halfStroke = density / 2
        canvas.drawRoundRect(halfStroke, halfStroke, width - halfStroke, height - halfStroke,
            16 * density, 16 * density, paint)
        paint.style = Paint.Style.FILL
        if (isFocused) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3 * density
            paint.color = ContextCompat.getColor(context, R.color.runelight_accent)
            canvas.drawRoundRect(2 * density, 2 * density, width - 2 * density, height - 2 * density, 16 * density, 16 * density, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawLabels(canvas: Canvas, textColor: Int) {
        paint.color = textColor
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = boldTypeface
        paint.textSize = 14 * scaledDensity
        canvas.drawText(label, width / 2f, 26 * density, paint)
        paint.textSize = 28 * scaledDensity
        val baseline = height / 2f - (paint.ascent() + paint.descent()) / 2
        canvas.drawText(context.getString(R.string.runelight_percent, level), width / 2f, baseline, paint)
        paint.typeface = regularTypeface
        paint.textSize = 11 * scaledDensity
        canvas.drawText(context.getString(if (colorEditable) R.string.runelight_drag_color_hint else R.string.runelight_drag_level_hint), width / 2f, height - 16 * density, paint)
    }

    private fun setFromUser(value: Int) {
        val bounded = value.coerceIn(0, 100)
        if (bounded != level) {
            level = bounded
            onLevelChanged?.invoke(level)
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x; downY = event.y; downLevel = level; dragging = false
                isPressed = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.y - downY) > slop || abs(event.x - downX) > slop) dragging = true
                if (dragging) setFromUser(downLevel + ((downY - event.y) * 100 / height.coerceAtLeast(1)).roundToInt())
            }
            MotionEvent.ACTION_UP -> {
                isPressed = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!dragging && colorEditable) performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            else -> return false
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, 100f, level.toFloat())
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (!isEnabled) return super.performAccessibilityAction(action, arguments)
        when (action) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                val value = arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) ?: return false
                if (!value.isFinite()) return false
                setFromUser(value.roundToInt())
            }
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> setFromUser(level + 5)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> setFromUser(level - 5)
            else -> return super.performAccessibilityAction(action, arguments)
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT -> setFromUser(level + 5)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT -> setFromUser(level - 5)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}
