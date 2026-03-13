package com.example.newstart.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.newstart.R
import com.example.newstart.data.ActivityLevel
import com.example.newstart.data.StressLevel
import com.example.newstart.util.ActivityAnalyzer
import com.example.newstart.util.HRVAnalyzer
import com.example.newstart.util.StepsAnalyzer

/**
 * 健康数据展示 Fragment
 * 显示 HRV、实时活动和步数统计。
 */
class HealthDataFragment : Fragment() {

    private lateinit var tvHrvValue: TextView
    private lateinit var tvHrvBaseline: TextView
    private lateinit var tvHrvStatus: TextView
    private lateinit var tvHrvRecovery: TextView
    private lateinit var progressHrv: ProgressBar

    private lateinit var tvActivityLevel: TextView
    private lateinit var tvLightMinutes: TextView
    private lateinit var tvModerateMinutes: TextView
    private lateinit var tvVigorousMinutes: TextView
    private lateinit var tvActivityCalories: TextView

    private lateinit var tvStepsCount: TextView
    private lateinit var tvStepsTarget: TextView
    private lateinit var tvWeeklyAverage: TextView
    private lateinit var tvStreak: TextView
    private lateinit var progressSteps: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_health_data, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        loadMockData()
    }

    private fun initViews(view: View) {
        tvHrvValue = view.findViewById(R.id.tvHrvValue)
        tvHrvBaseline = view.findViewById(R.id.tvHrvBaseline)
        tvHrvStatus = view.findViewById(R.id.tvHrvStatus)
        tvHrvRecovery = view.findViewById(R.id.tvHrvRecovery)
        progressHrv = view.findViewById(R.id.progressHrv)

        tvActivityLevel = view.findViewById(R.id.tvActivityLevel)
        tvLightMinutes = view.findViewById(R.id.tvLightMinutes)
        tvModerateMinutes = view.findViewById(R.id.tvModerateMinutes)
        tvVigorousMinutes = view.findViewById(R.id.tvVigorousMinutes)
        tvActivityCalories = view.findViewById(R.id.tvActivityCalories)

        tvStepsCount = view.findViewById(R.id.tvStepsCount)
        tvStepsTarget = view.findViewById(R.id.tvStepsTarget)
        tvWeeklyAverage = view.findViewById(R.id.tvWeeklyAverage)
        tvStreak = view.findViewById(R.id.tvStreak)
        progressSteps = view.findViewById(R.id.progressSteps)
    }

    private fun loadMockData() {
        updateHRV(
            currentHrv = 45,
            baselineHrv = 52,
            stressLevel = HRVAnalyzer.calculateStressLevel(45, 52)
        )

        updateActivity(
            activityLevel = ActivityLevel.LIGHT,
            lightMinutes = 85,
            moderateMinutes = 65,
            vigorousMinutes = 20
        )

        updateSteps(
            currentSteps = 8524,
            targetSteps = 10000,
            weeklyAverage = 7856,
            streakDays = 3
        )
    }

    private fun updateHRV(currentHrv: Int, baselineHrv: Int, stressLevel: StressLevel) {
        tvHrvValue.text = "$currentHrv ms"
        tvHrvBaseline.text = "基线: $baselineHrv ms"

        tvHrvStatus.text = stressLevel.getDisplayName()
        tvHrvStatus.backgroundTintList = ColorStateList.valueOf(getStressColor(stressLevel))

        val recoveryRate = HRVAnalyzer.getRecoveryRate(currentHrv, baselineHrv)
        tvHrvRecovery.text = "恢复率: ${String.format("%.0f", recoveryRate)}%"
        progressHrv.progress = recoveryRate.toInt()
    }

    private fun updateActivity(
        activityLevel: ActivityLevel,
        lightMinutes: Int,
        moderateMinutes: Int,
        vigorousMinutes: Int
    ) {
        tvActivityLevel.text = "${activityLevel.getIcon()} ${activityLevel.getDisplayName()}"
        tvLightMinutes.text = "$lightMinutes 分"
        tvModerateMinutes.text = "$moderateMinutes 分"
        tvVigorousMinutes.text = "$vigorousMinutes 分"

        val calories = ActivityAnalyzer.estimateCalories(lightMinutes, moderateMinutes, vigorousMinutes)
        tvActivityCalories.text = "消耗: $calories 千卡"
    }

    private fun updateSteps(
        currentSteps: Int,
        targetSteps: Int,
        weeklyAverage: Int,
        streakDays: Int
    ) {
        val current = String.format("%,d", currentSteps)
        val target = String.format("%,d", targetSteps)
        val weekly = String.format("%,d", weeklyAverage)

        tvStepsCount.text = current
        tvStepsTarget.text = getString(R.string.health_steps_target, target)
        tvWeeklyAverage.text = getString(R.string.health_weekly_avg, weekly)
        tvStreak.text = getString(R.string.health_streak_days, streakDays)

        val progress = StepsAnalyzer.getProgress(currentSteps, targetSteps)
        progressSteps.progress = progress.toInt()
    }

    private fun getStressColor(level: StressLevel): Int {
        val colorRes = when (level) {
            StressLevel.LOW -> R.color.status_positive
            StressLevel.MODERATE -> R.color.sleep_stage_rem
            StressLevel.HIGH -> R.color.status_warning
            StressLevel.VERY_HIGH -> R.color.status_negative
        }
        return color(colorRes)
    }

    private fun color(@ColorRes colorRes: Int): Int = ContextCompat.getColor(requireContext(), colorRes)

    companion object {
        fun newInstance() = HealthDataFragment()
    }
}
