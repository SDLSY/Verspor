package com.example.newstart.ui.doctor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.newstart.core.common.R
import com.example.newstart.feature.doctor.databinding.ActivityDoctorLiveAvatarBinding
import com.example.newstart.xfyun.virtual.XfyunVirtualHumanController

class DoctorLiveAvatarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDoctorLiveAvatarBinding
    private lateinit var virtualHumanController: XfyunVirtualHumanController
    private var isInteracting = false

    private val requestRecordAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startInteraction()
            } else {
                Toast.makeText(this, getString(R.string.doctor_live_avatar_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDoctorLiveAvatarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        virtualHumanController = XfyunVirtualHumanController(
            host = this,
            container = binding.containerDoctorLiveAvatar,
            onTranscript = ::appendTranscript,
            onStatus = ::renderStatus
        )

        binding.btnDoctorLiveAvatarBack.setOnClickListener { finish() }
        binding.btnDoctorLiveAvatarStart.setOnClickListener { requestStartInteraction() }
        binding.btnDoctorLiveAvatarStop.setOnClickListener { stopInteraction() }
        binding.btnDoctorLiveAvatarSendText.setOnClickListener { sendTextQuery() }
        binding.btnDoctorLiveAvatarClear.setOnClickListener {
            binding.tvDoctorLiveAvatarTranscript.text = getString(R.string.doctor_live_avatar_transcript_empty)
        }
        binding.chipDoctorLiveAvatarSleep.setOnClickListener {
            binding.etDoctorLiveAvatarText.setText(R.string.doctor_live_avatar_quick_question_sleep)
        }
        binding.chipDoctorLiveAvatarNavigation.setOnClickListener {
            binding.etDoctorLiveAvatarText.setText(R.string.doctor_live_avatar_quick_question_navigation)
        }
        binding.chipDoctorLiveAvatarReport.setOnClickListener {
            binding.etDoctorLiveAvatarText.setText(R.string.doctor_live_avatar_quick_question_report)
        }

        val configured = virtualHumanController.isConfigured()
        renderStatus(
            if (configured) {
                getString(R.string.doctor_live_avatar_status_ready)
            } else {
                getString(R.string.doctor_live_avatar_status_not_configured)
            }
        )
        binding.btnDoctorLiveAvatarStart.isEnabled = configured
        binding.btnDoctorLiveAvatarStop.isEnabled = false
        binding.btnDoctorLiveAvatarSendText.isEnabled = configured
    }

    override fun onDestroy() {
        if (::virtualHumanController.isInitialized) {
            virtualHumanController.destroy()
        }
        super.onDestroy()
    }

    private fun requestStartInteraction() {
        if (!virtualHumanController.isConfigured()) {
            renderStatus(getString(R.string.doctor_live_avatar_status_not_configured))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startInteraction()
        } else {
            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startInteraction() {
        virtualHumanController.startInteraction()
        isInteracting = true
        binding.btnDoctorLiveAvatarStart.isEnabled = false
        binding.btnDoctorLiveAvatarStop.isEnabled = true
    }

    private fun stopInteraction() {
        virtualHumanController.stopInteraction()
        isInteracting = false
        binding.btnDoctorLiveAvatarStart.isEnabled = virtualHumanController.isConfigured()
        binding.btnDoctorLiveAvatarStop.isEnabled = false
        renderStatus(getString(R.string.doctor_live_avatar_status_stopped))
    }

    private fun sendTextQuery() {
        if (!virtualHumanController.isConfigured()) {
            renderStatus(getString(R.string.doctor_live_avatar_status_not_configured))
            return
        }
        val text = binding.etDoctorLiveAvatarText.text?.toString().orEmpty().trim()
        if (text.isBlank()) {
            Toast.makeText(this, getString(R.string.doctor_live_avatar_text_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val sent = virtualHumanController.sendTextQuery(text)
        if (sent) {
            binding.etDoctorLiveAvatarText.text?.clear()
        } else {
            Toast.makeText(this, getString(R.string.doctor_live_avatar_text_send_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun appendTranscript(line: String) {
        if (line.isBlank()) return
        val current = binding.tvDoctorLiveAvatarTranscript.text?.toString().orEmpty()
        val emptyState = getString(R.string.doctor_live_avatar_transcript_empty)
        binding.tvDoctorLiveAvatarTranscript.text = buildString {
            if (current.isNotBlank() && current != emptyState) {
                append(current)
                append("\n\n")
            }
            append(line.trim())
        }
    }

    private fun renderStatus(text: String) {
        binding.tvDoctorLiveAvatarStatus.text = text
        binding.tvDoctorLiveAvatarHint.isVisible = !virtualHumanController.isConfigured()
        binding.btnDoctorLiveAvatarSendText.isEnabled = virtualHumanController.isConfigured()
        if (isInteracting && text != getString(R.string.doctor_live_avatar_status_stopped)) {
            binding.btnDoctorLiveAvatarStop.isEnabled = true
        }
    }
}

