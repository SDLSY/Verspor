package com.example.newstart.ui.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.newstart.R
import com.example.newstart.databinding.FragmentDataPrivacyBinding
import com.example.newstart.repository.CloudAccountRepository

class DataPrivacyFragment : Fragment() {

    private var _binding: FragmentDataPrivacyBinding? = null
    private val binding get() = _binding!!
    private val accountRepository = CloudAccountRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDataPrivacyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnPrivacyBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnPrivacyLogout.setOnClickListener {
            accountRepository.logout()
            refreshAccountState()
            showToast(getString(R.string.profile_toast_logout_success))
        }
        binding.btnPrivacyClearLocal.setOnClickListener {
            clearLocalAccountAndPreferences()
            refreshAccountState()
            showToast(getString(R.string.profile_privacy_local_cleared))
        }
        refreshAccountState()
    }

    private fun refreshAccountState() {
        val session = accountRepository.getCurrentSession()
        binding.tvPrivacyAccountState.text = if (session == null) {
            getString(R.string.profile_privacy_not_logged_in)
        } else {
            getString(R.string.profile_privacy_logged_in, session.email)
        }
    }

    private fun clearLocalAccountAndPreferences() {
        accountRepository.logout()
        ProfileSettingsStore.clear(requireContext())
        requireContext().getSharedPreferences("demo_mode_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
