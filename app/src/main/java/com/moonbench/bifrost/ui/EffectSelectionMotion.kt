package com.moonbench.bifrost.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.R

/** Animates native button colors; keeps Material's shape, ripple and checked semantics. */
class EffectSelectionMotion {
    private val animations = mutableMapOf<MaterialButton, ValueAnimator>()
    private val easing = AccelerateDecelerateInterpolator()

    fun select(button: MaterialButton) {
        val background = ContextCompat.getColorStateList(button.context, R.color.runelight_effect_background)!!
        val stroke = ContextCompat.getColorStateList(button.context, R.color.runelight_effect_stroke)!!
        val targetFill = background.getColorForState(button.drawableState, background.defaultColor)
        val targetEdge = stroke.getColorForState(button.drawableState, stroke.defaultColor)
        val fromFill = button.backgroundTintList?.getColorForState(button.drawableState, targetFill) ?: targetFill
        val fromEdge = button.strokeColor?.getColorForState(button.drawableState, targetEdge) ?: targetEdge
        animations.remove(button)?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled() || !button.isLaidOut) {
            button.backgroundTintList = background
            button.strokeColor = stroke
            return
        }
        // The native checked state has already changed. Start at the previous
        // endpoint when no animation was in flight, rather than reading its new tint.
        val startFill = if (fromFill == targetFill) ContextCompat.getColor(button.context,
            if (button.isChecked) R.color.runelight_card else R.color.runelight_effect_selected) else fromFill
        val startEdge = if (fromEdge == targetEdge) ContextCompat.getColor(button.context,
            if (button.isChecked) R.color.runelight_edge else R.color.runelight_effect_edge) else fromEdge
        val animation = ValueAnimator.ofFloat(0f, 1f)
        animation.duration = 180
        animation.interpolator = easing
        animation.addUpdateListener {
            val fraction = it.animatedValue as Float
            button.backgroundTintList = ColorStateList.valueOf(ColorUtils.blendARGB(startFill, targetFill, fraction))
            button.strokeColor = ColorStateList.valueOf(ColorUtils.blendARGB(startEdge, targetEdge, fraction))
        }
        animation.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator) {
                if (animations[button] === animator) {
                    animations.remove(button)
                    button.backgroundTintList = background
                    button.strokeColor = stroke
                }
            }
        })
        animations[button] = animation
        animation.start()
    }

    fun finish() {
        animations.values.toList().forEach { it.end() }
    }
}
