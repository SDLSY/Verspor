package com.example.newstart.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.newstart.R
import com.example.newstart.databinding.FragmentProfileBinding
import com.example.newstart.repository.CloudAccountRepository
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val accountRepository = CloudAccountRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        refreshCloudAccountUi()
    }

    override fun onResume() {
        super.onResume()
        refreshCloudAccountUi()
    }

    private fun setupClickListeners() {
        binding.tvPersonalInfo.setOnClickListener {
            if (accountRepository.getCurrentSession() == null) {
                openCloudAuth(register = false)
            } else {
                findNavController().navigate(R.id.action_profile_to_personal_info)
            }
        }
        binding.tvNotificationSettings.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_notification_settings)
        }
        binding.tvDataPrivacy.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_data_privacy)
        }
        binding.tvAbout.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_about_project)
        }
        binding.tvRelaxCenter.setOnClickListener {
            findNavController().navigate(R.id.navigation_intervention_center)
        }
        binding.btnLogout.setOnClickListener {
            accountRepository.logout()
            refreshCloudAccountUi()
            showToast(getString(R.string.profile_toast_logout_success))
        }
        binding.btnCloudRegister.setOnClickListener {
            openCloudAuth(register = true)
        }
        binding.btnCloudLogin.setOnClickListener {
            openCloudAuth(register = false)
        }
    }

    private fun openCloudAuth(register: Boolean) {
        findNavController().navigate(
            R.id.action_profile_to_cloud_auth,
            bundleOf(
                CloudAuthFragment.ARG_MODE to if (register) {
                    CloudAuthFragment.MODE_REGISTER
                } else {
                    CloudAuthFragment.MODE_LOGIN
                }
            )
        )
    }

    private fun refreshCloudAccountUi() {
        val session = accountRepository.getCurrentSession()
        if (session == null) {
            renderLoggedOut()
            return
        }

        renderProfile(
            username = session.username,
            email = session.email,
            accountState = getString(R.string.cloud_account_logged_in, session.email)
        )

        lifecycleScope.launch {
            accountRepository.getUserProfile().onSuccess { profile ->
                renderProfile(
                    username = profile.username,
                    email = profile.email,
                    accountState = getString(R.string.cloud_account_logged_in, profile.email)
                )
            }
        }
    }

    private fun renderLoggedOut() {
        binding.tvCloudAccountState.text = getString(R.string.cloud_account_not_logged_in)
        binding.btnCloudRegister.isVisible = true
        binding.btnCloudLogin.isVisible = true
        binding.btnLogout.isVisible = false
        binding.tvProfileName.text = getString(R.string.profile_name)
        binding.tvProfileEmail.text = getString(R.string.profile_email)
        ProfileAvatarHelper.applyBadge(
            binding.tvProfileAvatar,
            getString(R.string.profile_name),
            getString(R.string.profile_email)
        )
    }

    private fun renderProfile(username: String, email: String, accountState: String) {
        binding.tvCloudAccountState.text = accountState
        binding.btnCloudRegister.isVisible = false
        binding.btnCloudLogin.isVisible = false
        binding.btnLogout.isVisible = true
        binding.tvProfileName.text = username
        binding.tvProfileEmail.text = email
        ProfileAvatarHelper.applyBadge(binding.tvProfileAvatar, username, email)
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
