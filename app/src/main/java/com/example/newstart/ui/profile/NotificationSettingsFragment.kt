package com.example.newstart.ui.profile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.newstart.R
import com.example.newstart.databinding.FragmentNotificationSettingsBinding

class NotificationSettingsFragment : Fragment() {

    private var _binding: FragmentNotificationSettingsBinding? = null
    private val binding get() = _binding!!
    private var bindingSwitches = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshSystemStatus()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnNotificationBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnOpenNotificationSettings.setOnClickListener { openSystemNotificationSettings() }
        binding.btnRequestNotificationPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        loadSettings()
        bindSwitchListeners()
        refreshSystemStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshSystemStatus()
    }

    private fun loadSettings() {
        bindingSwitches = true
        val settings = ProfileSettingsStore.getNotificationSettings(requireContext())
        binding.switchNotificationsEnabled.isChecked = settings.notificationsEnabled
        binding.switchReportAlerts.isChecked = settings.reportAlertsEnabled
        binding.switchInterventionReminders.isChecked = settings.interventionRemindersEnabled
        binding.switchAvatarSpeech.isChecked = settings.avatarSpeechEnabled
        bindingSwitches = false
    }

    private fun bindSwitchListeners() {
        val listener = CompoundButton.OnCheckedChangeListener { _, _ ->
            if (!bindingSwitches) {
                ProfileSettingsStore.saveNotificationSettings(
                    requireContext(),
                    NotificationSettings(
                        notificationsEnabled = binding.switchNotificationsEnabled.isChecked,
                        reportAlertsEnabled = binding.switchReportAlerts.isChecked,
                        interventionRemindersEnabled = binding.switchInterventionReminders.isChecked,
                        avatarSpeechEnabled = binding.switchAvatarSpeech.isChecked
                    )
                )
            }
        }
        binding.switchNotificationsEnabled.setOnCheckedChangeListener(listener)
        binding.switchReportAlerts.setOnCheckedChangeListener(listener)
        binding.switchInterventionReminders.setOnCheckedChangeListener(listener)
        binding.switchAvatarSpeech.setOnCheckedChangeListener(listener)
    }

    private fun refreshSystemStatus() {
        val enabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        binding.tvNotificationSystemStatus.text = getString(
            if (enabled) R.string.profile_notification_system_enabled else R.string.profile_notification_system_disabled
        )
        binding.btnRequestNotificationPermission.visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !enabled) View.VISIBLE else View.GONE
    }

    private fun openSystemNotificationSettings() {
        val context = requireContext()
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        val fallbackIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        runCatching { startActivity(intent) }.getOrElse { startActivity(fallbackIntent) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
