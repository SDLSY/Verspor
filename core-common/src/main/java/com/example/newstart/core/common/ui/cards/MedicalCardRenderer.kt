package com.example.newstart.core.common.ui.cards

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.newstart.core.common.R
import com.example.newstart.core.common.databinding.ViewActionGroupCardBinding
import com.example.newstart.core.common.databinding.ViewEvidenceCardBinding
import com.example.newstart.core.common.databinding.ViewMetricRangeCardBinding
import com.example.newstart.core.common.databinding.ViewMiniTrendCardBinding
import com.example.newstart.core.common.databinding.ViewRiskSummaryCardBinding
import com.google.android.material.card.MaterialCardView

object MedicalCardRenderer {

    fun renderEvidenceCards(container: LinearLayout, cards: List<EvidenceCardModel>) {
        container.removeAllViews()
        container.isVisible = cards.isNotEmpty()
        cards.forEachIndexed { index, card ->
            val binding = ViewEvidenceCardBinding.inflate(
                LayoutInflater.from(container.context),
                container,
                false
            )
            val colors = toneColors(container.context, card.tone)
            binding.tvEvidenceBadge.isVisible = card.badgeText.isNotBlank()
            binding.tvEvidenceBadge.text = card.badgeText
            tintBadge(binding.tvEvidenceBadge, colors.badgeBackground, colors.accent)
            binding.tvEvidenceTitle.text = card.title
            binding.tvEvidenceValue.text = card.value
            binding.tvEvidenceNote.isVisible = card.note.isNotBlank()
            binding.tvEvidenceNote.text = card.note
            styleCard(binding.root, colors)
            container.addView(binding.root, buildCardLayoutParams(container, index))
        }
    }

    fun renderMetricRangeCards(container: LinearLayout, cards: List<MetricRangeCardModel>) {
        container.removeAllViews()
        container.isVisible = cards.isNotEmpty()
        cards.forEachIndexed { index, card ->
            val binding = ViewMetricRangeCardBinding.inflate(
                LayoutInflater.from(container.context),
                container,
                false
            )
            val colors = toneColors(container.context, card.tone)
            binding.tvMetricName.text = card.metricName
            binding.tvMetricValue.text = card.valueText
            binding.tvMetricRange.text = card.rangeText
            binding.tvMetricStatus.text = card.statusText
            binding.tvMetricHelper.isVisible = card.helperText.isNotBlank()
            binding.tvMetricHelper.text = card.helperText
            tintBadge(binding.tvMetricStatus, colors.badgeBackground, colors.accent)
            binding.progressMetricValue.progress = card.progressPercent.coerceIn(0, 100)
            binding.progressMetricValue.setIndicatorColor(colors.accent)
            styleCard(binding.root, colors)
            container.addView(binding.root, buildCardLayoutParams(container, index))
        }
    }

    fun renderMiniTrendCards(container: LinearLayout, cards: List<MiniTrendCardModel>) {
        container.removeAllViews()
        container.isVisible = cards.isNotEmpty()
        cards.forEachIndexed { index, card ->
            val binding = ViewMiniTrendCardBinding.inflate(
                LayoutInflater.from(container.context),
                container,
                false
            )
            val colors = toneColors(container.context, card.tone)
            binding.tvTrendTitle.text = card.title
            binding.tvTrendValue.text = card.valueText
            binding.tvTrendDelta.text = card.trendText
            binding.tvTrendSupporting.isVisible = card.supportingText.isNotBlank()
            binding.tvTrendSupporting.text = card.supportingText
            binding.progressTrendLevel.progress = card.progressPercent.coerceIn(0, 100)
            binding.progressTrendLevel.setIndicatorColor(colors.accent)
            binding.tvTrendDelta.setTextColor(colors.accent)
            styleCard(binding.root, colors)
            container.addView(binding.root, buildCardLayoutParams(container, index))
        }
    }

    fun renderRiskSummaryCard(container: ViewGroup, card: RiskSummaryCardModel?) {
        container.removeAllViews()
        container.isVisible = card != null
        card ?: return
        val binding = ViewRiskSummaryCardBinding.inflate(
            LayoutInflater.from(container.context),
            container,
            false
        )
        val colors = toneColors(container.context, card.tone)
        binding.tvRiskBadge.text = card.badgeText
        tintBadge(binding.tvRiskBadge, colors.badgeBackground, colors.accent)
        binding.tvRiskTitle.text = card.title
        binding.tvRiskSummary.text = card.summary
        binding.tvRiskSupporting.isVisible = card.supportingText.isNotBlank()
        binding.tvRiskSupporting.text = card.supportingText
        binding.tvRiskBullets.isVisible = card.bullets.isNotEmpty()
        binding.tvRiskBullets.text = card.bullets.joinToString("\n") { "• $it" }
        styleCard(binding.root, colors)
        container.addView(binding.root)
    }

    fun renderActionGroupCards(
        container: LinearLayout,
        cards: List<ActionGroupCardModel>,
        onActionClick: ((ActionGroupCardModel) -> Unit)? = null
    ) {
        container.removeAllViews()
        container.isVisible = cards.isNotEmpty()
        cards.forEachIndexed { index, card ->
            val binding = ViewActionGroupCardBinding.inflate(
                LayoutInflater.from(container.context),
                container,
                false
            )
            val colors = toneColors(container.context, card.tone)
            binding.tvActionCategory.text = card.category
            tintBadge(binding.tvActionCategory, colors.badgeBackground, colors.accent)
            binding.tvActionHeadline.text = card.headline
            binding.tvActionSupporting.isVisible = card.supportingText.isNotBlank()
            binding.tvActionSupporting.text = card.supportingText
            binding.tvActionDetails.isVisible = card.detailLines.isNotEmpty()
            binding.tvActionDetails.text = card.detailLines.joinToString("\n")
            val hasAction = card.actionLabel.isNotBlank()
            binding.btnActionPrimary.isVisible = hasAction
            binding.btnActionPrimary.text = card.actionLabel
            binding.btnActionPrimary.isEnabled = card.enabled
            if (hasAction && onActionClick != null) {
                binding.btnActionPrimary.setOnClickListener { onActionClick(card) }
            } else {
                binding.btnActionPrimary.setOnClickListener(null)
            }
            styleCard(binding.root, colors)
            container.addView(binding.root, buildCardLayoutParams(container, index))
        }
    }

    private fun buildCardLayoutParams(container: ViewGroup, index: Int): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (index > 0) {
            params.topMargin = dp(container.context, 10)
        }
        return params
    }

    private fun styleCard(cardView: MaterialCardView, colors: ToneColors) {
        cardView.setCardBackgroundColor(colors.surface)
        cardView.strokeColor = colors.stroke
        cardView.strokeWidth = dp(cardView.context, 1)
    }

    private fun tintBadge(view: View, backgroundColor: Int, textColor: Int) {
        view.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        if (view is android.widget.TextView) {
            view.setTextColor(textColor)
        }
    }

    private fun toneColors(context: Context, tone: CardTone): ToneColors {
        val accent = when (tone) {
            CardTone.POSITIVE -> color(context, R.color.status_positive)
            CardTone.WARNING -> color(context, R.color.status_warning)
            CardTone.NEGATIVE -> color(context, R.color.status_negative)
            CardTone.INFO -> color(context, R.color.md_theme_light_primary)
            CardTone.NEUTRAL -> color(context, R.color.text_secondary)
        }
        val baseSurface = color(context, R.color.surface_card_subtle)
        val surface = blend(baseSurface, accent, 0.05f)
        val stroke = blend(color(context, R.color.card_stroke), accent, 0.28f)
        val badgeBackground = blend(accent, Color.WHITE, 0.86f)
        return ToneColors(accent, surface, stroke, badgeBackground)
    }

    private fun color(context: Context, colorRes: Int): Int {
        return ContextCompat.getColor(context, colorRes)
    }

    private fun blend(colorA: Int, colorB: Int, ratio: Float): Int {
        val safeRatio = ratio.coerceIn(0f, 1f)
        val inverse = 1f - safeRatio
        return Color.argb(
            (Color.alpha(colorA) * inverse + Color.alpha(colorB) * safeRatio).toInt(),
            (Color.red(colorA) * inverse + Color.red(colorB) * safeRatio).toInt(),
            (Color.green(colorA) * inverse + Color.green(colorB) * safeRatio).toInt(),
            (Color.blue(colorA) * inverse + Color.blue(colorB) * safeRatio).toInt()
        )
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private data class ToneColors(
        val accent: Int,
        val surface: Int,
        val stroke: Int,
        val badgeBackground: Int
    )
}
