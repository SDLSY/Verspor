package com.example.newstart.ui.profile

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import kotlin.math.absoluteValue

object ProfileAvatarHelper {

    fun applyBadge(textView: TextView, primaryName: String, email: String) {
        val initials = buildInitials(primaryName, email)
        val seed = (primaryName.ifBlank { email }).hashCode().absoluteValue
        val color = avatarColor(seed)
        textView.text = initials
        textView.setTextColor(Color.WHITE)
        textView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun buildInitials(primaryName: String, email: String): String {
        val source = primaryName.trim().ifBlank { email.substringBefore("@").trim() }.ifBlank { "NS" }
        val tokens = source.split(" ", "-", "_").filter { it.isNotBlank() }
        if (tokens.size >= 2) {
            return (tokens.first().first().toString() + tokens.last().first().toString()).uppercase()
        }
        return source.take(2).uppercase()
    }

    private fun avatarColor(seed: Int): Int {
        val palette = intArrayOf(
            Color.parseColor("#0E8AA8"),
            Color.parseColor("#2D5C8A"),
            Color.parseColor("#1E8E72"),
            Color.parseColor("#D07B12"),
            Color.parseColor("#5969E8")
        )
        return palette[seed % palette.size]
    }
}
