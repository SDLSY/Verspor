package com.example.newstart.ui.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.R
import com.example.newstart.feature.profile.databinding.FragmentCloudAuthBinding
import com.example.newstart.repository.CloudAccountRepository
import com.example.newstart.repository.DemoBootstrapCoordinator
import com.example.newstart.network.models.AuthData
import kotlinx.coroutines.launch

class CloudAuthFragment : Fragment() {

    private var _binding: FragmentCloudAuthBinding? = null
    private val binding get() = _binding!!
    private val accountRepository = CloudAccountRepository()
    private val demoBootstrapCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        DemoBootstrapCoordinator(requireContext().applicationContext)
    }

    private var mode: String = MODE_LOGIN
    private var pendingConfirmationEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = arguments?.getString(ARG_MODE).orEmpty().ifBlank { MODE_LOGIN }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCloudAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions()
        updateModeUi()
        refreshSessionHint()
    }

    private fun setupActions() {
        binding.btnAuthBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnAuthDemoEntry.setOnClickListener { submitDemoLogin() }
        binding.btnAuthPrimary.setOnClickListener { submit() }
        binding.btnAuthSwitchMode.setOnClickListener {
            pendingConfirmationEmail = null
            mode = when (mode) {
                MODE_REGISTER -> MODE_LOGIN
                MODE_RESET -> MODE_LOGIN
                else -> MODE_REGISTER
            }
            updateModeUi()
        }
        binding.btnAuthAuxAction.setOnClickListener {
            pendingConfirmationEmail = null
            mode = if (mode == MODE_RESET) MODE_LOGIN else MODE_RESET
            updateModeUi()
        }
        binding.btnAuthResend.setOnClickListener { resendConfirmation() }
        binding.btnAuthOpenEmail.setOnClickListener { openEmailApp() }
        binding.btnAuthBackToLogin.setOnClickListener {
            pendingConfirmationEmail = null
            mode = MODE_LOGIN
            updateModeUi()
        }
    }

    private fun updateModeUi() {
        val pendingEmail = pendingConfirmationEmail
        if (!pendingEmail.isNullOrBlank()) {
            renderPendingConfirmation(pendingEmail)
            return
        }

        val isRegister = mode == MODE_REGISTER
        val isReset = mode == MODE_RESET
        val showDemoEntry = !isReset && accountRepository.getCurrentSession() == null
        binding.cardAuthForm.isVisible = true
        binding.cardAuthPending.isVisible = false

        binding.tvAuthHeroTitle.text = getString(
            when {
                isRegister -> R.string.cloud_auth_hero_register_title
                isReset -> R.string.cloud_auth_hero_reset_title
                else -> R.string.cloud_auth_hero_login_title
            }
        )
        binding.tvAuthHeroBody.text = getString(
            when {
                isRegister -> R.string.cloud_auth_register_body
                isReset -> R.string.cloud_auth_reset_body
                else -> R.string.cloud_auth_login_body
            }
        )
        binding.tvAuthFormTitle.text = getString(
            when {
                isRegister -> R.string.cloud_auth_form_register_title
                isReset -> R.string.cloud_auth_form_reset_title
                else -> R.string.cloud_auth_form_login_title
            }
        )
        binding.etAuthPasswordLayout.isVisible = !isReset
        binding.etAuthUsernameLayout.isVisible = isRegister
        binding.btnAuthPrimary.text = getString(
            when {
                isRegister -> R.string.cloud_auth_submit_register
                isReset -> R.string.cloud_auth_submit_reset
                else -> R.string.cloud_auth_submit_login
            }
        )
        binding.btnAuthSwitchMode.text = getString(
            when {
                isRegister -> R.string.cloud_auth_switch_to_login
                isReset -> R.string.cloud_auth_switch_to_register
                else -> R.string.cloud_auth_switch_to_register
            }
        )
        binding.btnAuthAuxAction.isVisible = true
        binding.btnAuthAuxAction.text = getString(
            if (isReset) R.string.cloud_auth_back_to_login else R.string.cloud_auth_forgot_password
        )
        binding.btnAuthDemoEntry.isVisible = showDemoEntry
        binding.btnAuthDemoEntry.text = getString(R.string.cloud_demo_quick_entry)
        binding.tvAuthStatus.isVisible = false
        binding.tvAuthStatus.text = ""
    }

    private fun refreshSessionHint() {
        val session = accountRepository.getCurrentSession()
        binding.tvAuthCurrentSession.text = if (session == null) {
            getString(R.string.cloud_auth_not_signed_in)
        } else {
            getString(R.string.cloud_auth_status_signed_in, session.email)
        }
    }

    private fun submit() {
        val email = binding.etAuthEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etAuthPassword.text?.toString()?.trim().orEmpty()
        val username = binding.etAuthUsername.text?.toString()?.trim().orEmpty()

        val validationError = validateInputs(email, password, username)
        if (validationError != null) {
            renderStatus(validationError, isError = true)
            return
        }

        val finalUsername = if (username.isNotEmpty()) username else email.substringBefore("@")
        setLoading(true)
        lifecycleScope.launch {
            when (mode) {
                MODE_REGISTER -> {
                    accountRepository.register(email, password, finalUsername)
                        .onSuccess { handleAuthResult(it, isRegister = true) }
                        .onFailure { handleFailure(it.message, R.string.cloud_auth_register_failed_generic) }
                }

                MODE_RESET -> {
                    accountRepository.requestPasswordReset(email)
                        .onSuccess {
                            mode = MODE_LOGIN
                            updateModeUi()
                            renderStatus(getString(R.string.cloud_auth_reset_sent_hint), isError = false)
                        }
                        .onFailure { handleFailure(it.message, R.string.cloud_auth_reset_failed_generic) }
                }

                else -> {
                    accountRepository.login(email, password)
                        .onSuccess { handleAuthResult(it, isRegister = false) }
                        .onFailure { handleFailure(it.message, R.string.cloud_auth_login_failed_generic) }
                }
            }

            setLoading(false)
            refreshSessionHint()
        }
    }

    private fun submitDemoLogin() {
        if (accountRepository.getCurrentSession() != null) {
            renderStatus(getString(R.string.cloud_demo_quick_entry_success), isError = false)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            accountRepository.loginWithDemoAccount()
                .onSuccess { handleAuthResult(it, isRegister = false) }
                .onFailure { handleFailure(it.message, R.string.cloud_demo_quick_entry_failed) }

            setLoading(false)
            refreshSessionHint()
        }
    }

    private suspend fun handleAuthResult(authData: AuthData, isRegister: Boolean) {
        if (authData.authState == "PENDING_CONFIRMATION") {
            pendingConfirmationEmail = authData.email
            renderPendingConfirmation(authData.email)
            return
        }

        val bootstrapResult = demoBootstrapCoordinator.bootstrapForAuth(authData).getOrElse {
            handleFailure(
                it.message,
                if (isRegister) R.string.cloud_auth_register_failed_generic else R.string.cloud_auth_login_failed_generic
            )
            return
        }

        Toast.makeText(
            requireContext(),
            when {
                bootstrapResult.isDemoAccount && bootstrapResult.message.isNotBlank() -> bootstrapResult.message
                else -> getString(
                    if (isRegister) {
                        R.string.profile_toast_register_success
                    } else {
                        R.string.profile_toast_login_success
                    }
                )
            },
            Toast.LENGTH_SHORT
        ).show()
        findNavController().navigateUp()
    }

    private fun handleFailure(message: String?, fallbackRes: Int) {
        val safeMessage = message.orEmpty()
        val displayMessage = when {
            safeMessage.contains("account already exists", ignoreCase = true) ->
                getString(R.string.cloud_auth_account_exists_hint)
            safeMessage.isNotBlank() -> safeMessage
            else -> getString(fallbackRes)
        }
        renderStatus(displayMessage, isError = true)
    }

    private fun validateInputs(email: String, password: String, username: String): String? {
        if (email.isEmpty()) {
            return getString(R.string.profile_toast_email_password_required)
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return getString(R.string.profile_toast_email_invalid)
        }
        if (mode == MODE_REGISTER && (email.endsWith("@example.com") || email.endsWith("@test.com"))) {
            return getString(R.string.profile_toast_email_real_required)
        }
        if (mode != MODE_RESET && password.isEmpty()) {
            return getString(R.string.profile_toast_email_password_required)
        }
        if (mode != MODE_RESET && password.length < 6) {
            return getString(R.string.profile_toast_password_short)
        }
        if (mode == MODE_REGISTER && username.length > 32) {
            return getString(R.string.cloud_auth_username_too_long)
        }
        return null
    }

    private fun setLoading(loading: Boolean) {
        binding.progressAuth.isVisible = loading
        binding.btnAuthDemoEntry.isEnabled = !loading
        binding.btnAuthPrimary.isEnabled = !loading
        binding.btnAuthSwitchMode.isEnabled = !loading
        binding.btnAuthAuxAction.isEnabled = !loading
        binding.btnAuthResend.isEnabled = !loading
        binding.btnAuthOpenEmail.isEnabled = !loading
        binding.btnAuthBackToLogin.isEnabled = !loading
        binding.etAuthEmail.isEnabled = !loading
        binding.etAuthPassword.isEnabled = !loading
        binding.etAuthUsername.isEnabled = !loading
        binding.btnAuthPrimary.text = getString(
            when {
                loading && mode == MODE_REGISTER -> R.string.cloud_auth_submitting_register
                loading && mode == MODE_RESET -> R.string.cloud_auth_submitting_reset
                loading -> R.string.cloud_auth_submitting_login
                mode == MODE_REGISTER -> R.string.cloud_auth_submit_register
                mode == MODE_RESET -> R.string.cloud_auth_submit_reset
                else -> R.string.cloud_auth_submit_login
            }
        )
        binding.btnAuthDemoEntry.text = getString(
            if (loading) {
                R.string.cloud_demo_quick_entry_loading
            } else {
                R.string.cloud_demo_quick_entry
            }
        )
    }

    private fun renderPendingConfirmation(email: String) {
        binding.cardAuthForm.isVisible = false
        binding.cardAuthPending.isVisible = true
        binding.tvAuthHeroTitle.text = getString(R.string.cloud_auth_pending_title)
        binding.tvAuthHeroBody.text = getString(R.string.cloud_auth_pending_body)
        binding.tvAuthPendingEmail.text = email
        binding.tvAuthPendingBody.text = getString(R.string.cloud_auth_confirmation_required_hint)
        binding.tvAuthStatus.isVisible = false
    }

    private fun resendConfirmation() {
        val email = pendingConfirmationEmail.orEmpty()
        if (email.isBlank()) {
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            accountRepository.resendConfirmation(email)
                .onSuccess {
                    renderStatus(getString(R.string.cloud_auth_resend_success), isError = false)
                    binding.cardAuthPending.isVisible = true
                }
                .onFailure { handleFailure(it.message, R.string.cloud_auth_resend_failed_generic) }
            setLoading(false)
        }
    }

    private fun openEmailApp() {
        val fallbackIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${pendingConfirmationEmail.orEmpty()}"))
        val primaryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL)
        try {
            startActivity(primaryIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(fallbackIntent)
            } catch (_: ActivityNotFoundException) {
                renderStatus(getString(R.string.cloud_auth_open_email_failed), isError = true)
            }
        }
    }

    private fun renderStatus(message: String, isError: Boolean) {
        binding.tvAuthStatus.isVisible = message.isNotBlank()
        binding.tvAuthStatus.text = message
        binding.tvAuthStatus.setTextColor(
            requireContext().getColor(
                when {
                    isError -> R.color.status_negative
                    else -> R.color.status_positive
                }
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_MODE = "mode"
        const val MODE_REGISTER = "register"
        const val MODE_LOGIN = "login"
        const val MODE_RESET = "reset"

        fun args(mode: String) = bundleOf(ARG_MODE to mode)
    }
}

