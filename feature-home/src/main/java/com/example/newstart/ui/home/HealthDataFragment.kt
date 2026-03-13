package com.example.newstart.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.newstart.core.common.R as CommonR
import com.example.newstart.data.ActivityLevel
import com.example.newstart.data.StressLevel
import com.example.newstart.feature.home.databinding.FragmentHealthDataBinding
import com.example.newstart.util.ActivityAnalyzer
import com.example.newstart.util.HRVAnalyzer
import com.example.newstart.util.StepsAnalyzer

class HealthDataFragment : Fragment() {

    private var _binding: FragmentHealthDataBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadMockData()
    }

    private fun loadMockData() {
        updateHrv(
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

    private fun updateHrv(currentHrv: Int, baselineHrv: Int, stressLevel: StressLevel) {
        binding.tvHrvValue.text = "$currentHrv ms"
        binding.tvHrvBaseline.text = "基线: $baselineHrv ms"
        binding.tvHrvStatus.text = stressLevel.getDisplayName()
        binding.tvHrvStatus.backgroundTintList = ColorStateList.valueOf(getStressColor(stressLevel))

        val recoveryRate = HRVAnalyzer.getRecoveryRate(currentHrv, baselineHrv)
        binding.tvHrvRecovery.text = "恢复率 ${String.format("%.0f", recoveryRate)}%"
        binding.progressHrv.progress = recoveryRate.toInt()
    }

    private fun updateActivity(
        activityLevel: ActivityLevel,
        lightMinutes: Int,
        moderateMinutes: Int,
        vigorousMinutes: Int
    ) {
        binding.tvActivityLevel.text = "${activityLevel.getIcon()} ${activityLevel.getDisplayName()}"
        binding.tvLightMinutes.text = "$lightMinutes 分钟"
        binding.tvModerateMinutes.text = "$moderateMinutes 分钟"
        binding.tvVigorousMinutes.text = "$vigorousMinutes 分钟"

        val calories = ActivityAnalyzer.estimateCalories(lightMinutes, moderateMinutes, vigorousMinutes)
        binding.tvActivityCalories.text = "消耗约 $calories 千卡"
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

        binding.tvStepsCount.text = current
        binding.tvStepsTarget.text = getString(CommonR.string.health_steps_target, target)
        binding.tvWeeklyAverage.text = getString(CommonR.string.health_weekly_avg, weekly)
        binding.tvStreak.text = getString(CommonR.string.health_streak_days, streakDays)
        binding.progressSteps.progress = StepsAnalyzer.getProgress(currentSteps, targetSteps).toInt()
    }

    private fun getStressColor(level: StressLevel): Int {
        val colorRes = when (level) {
            StressLevel.LOW -> CommonR.color.status_positive
            StressLevel.MODERATE -> CommonR.color.sleep_stage_rem
            StressLevel.HIGH -> CommonR.color.status_warning
            StressLevel.VERY_HIGH -> CommonR.color.status_negative
        }
        return color(colorRes)
    }

    private fun color(@ColorRes colorRes: Int): Int =
        ContextCompat.getColor(requireContext(), colorRes)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HealthDataFragment()
    }
}
