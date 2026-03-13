package com.example.newstart.ui.widget

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * 按压缩放效果触摸监听器
 * 按下时缩小，松开时恢复
 */
class ScaleTouchListener(
    private val scaleDown: Float = 0.96f,
    private val duration: Long = 100L
) : View.OnTouchListener {

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                animateScale(view, scaleDown)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animateScale(view, 1.0f)
                if (event.action == MotionEvent.ACTION_UP) {
                    view.performClick()
                }
            }
        }
        return true
    }

    private fun animateScale(view: View, scale: Float) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, scale)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, scale)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            this.duration = this@ScaleTouchListener.duration
            interpolator = DecelerateInterpolator()
            start()
        }
    }
}

/**
 * View 扩展函数，便于使用
 */
fun View.addScaleEffect(scaleDown: Float = 0.96f, duration: Long = 100L) {
    setOnTouchListener(ScaleTouchListener(scaleDown, duration))
}
