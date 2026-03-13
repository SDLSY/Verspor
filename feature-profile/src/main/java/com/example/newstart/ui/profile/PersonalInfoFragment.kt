package com.example.newstart.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.R
import com.example.newstart.feature.profile.databinding.FragmentPersonalInfoBinding
import com.example.newstart.network.models.UserProfile
import com.example.newstart.network.models.UserProfileRequest
import com.example.newstart.repository.CloudAccountRepository
import kotlinx.coroutines.launch

class PersonalInfoFragment : Fragment() {

    private var _binding: FragmentPersonalInfoBinding? = null
    private val binding get() = _binding!!
    private val accountRepository = CloudAccountRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGenderOptions()
        setupActions()
        val session = accountRepository.getCurrentSession()
        if (session == null) {
            findNavController().navigate(
                R.id.navigation_cloud_auth,
                CloudAuthFragment.args(CloudAuthFragment.MODE_LOGIN)
            )
            return
        }
        applyHeader(session.username, session.email)
        binding.etProfileEmail.setText(session.email)
        loadProfile()
    }

    private fun setupGenderOptions() {
        val adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.profile_gender_options,
            android.R.layout.simple_list_item_1
        )
        binding.etProfileGender.setAdapter(adapter)
    }

    private fun setupActions() {
        binding.btnProfileBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnProfileSave.setOnClickListener { saveProfile() }
    }

    private fun loadProfile() {
        setLoading(true)
        lifecycleScope.launch {
            accountRepository.getUserProfile()
                .onSuccess { populateProfile(it) }
                .onFailure { showToast(it.message ?: getString(R.string.profile_load_failed)) }
            setLoading(false)
        }
    }

    private fun populateProfile(profile: UserProfile) {
        applyHeader(profile.username, profile.email)
        binding.etProfileUsername.setText(profile.username)
        binding.etProfileEmail.setText(profile.email)
        binding.etProfileAge.setText(profile.age?.toString().orEmpty())
        binding.etProfileGender.setText(mapGenderValueToLabel(profile.gender), false)
        binding.tvProfileStatus.isVisible = false
    }

    private fun saveProfile() {
        val username = binding.etProfileUsername.text?.toString()?.trim().orEmpty()
        val ageText = binding.etProfileAge.text?.toString()?.trim().orEmpty()
        val genderLabel = binding.etProfileGender.text?.toString()?.trim().orEmpty()

        if (username.isBlank()) {
            binding.tvProfileStatus.isVisible = true
            binding.tvProfileStatus.text = getString(R.string.profile_username_required)
            return
        }

        val age = if (ageText.isBlank()) null else ageText.toIntOrNull()
        if (age != null && (age < 1 || age > 120)) {
            binding.tvProfileStatus.isVisible = true
            binding.tvProfileStatus.text = getString(R.string.profile_age_invalid)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            accountRepository.updateUserProfile(
                UserProfileRequest(
                    username = username,
                    age = age,
                    gender = mapGenderLabelToValue(genderLabel)
                )
            ).onSuccess {
                populateProfile(it)
                showToast(getString(R.string.profile_save_success))
            }.onFailure {
                binding.tvProfileStatus.isVisible = true
                binding.tvProfileStatus.text = it.message ?: getString(R.string.profile_save_failed)
            }
            setLoading(false)
        }
    }

    private fun applyHeader(name: String, email: String) {
        binding.tvProfileDetailName.text = name.ifBlank { getString(R.string.profile_name) }
        binding.tvProfileDetailEmail.text = email.ifBlank { getString(R.string.profile_email) }
        ProfileAvatarHelper.applyBadge(binding.tvProfileAvatar, name, email)
    }

    private fun mapGenderValueToLabel(value: String?): String {
        return when (value?.lowercase()) {
            "male" -> getString(R.string.profile_gender_male)
            "female" -> getString(R.string.profile_gender_female)
            "other" -> getString(R.string.profile_gender_other)
            else -> getString(R.string.profile_gender_unspecified)
        }
    }

    private fun mapGenderLabelToValue(label: String): String? {
        return when (label) {
            getString(R.string.profile_gender_male) -> "male"
            getString(R.string.profile_gender_female) -> "female"
            getString(R.string.profile_gender_other) -> "other"
            else -> null
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressProfile.isVisible = loading
        binding.btnProfileSave.isEnabled = !loading
        binding.etProfileUsername.isEnabled = !loading
        binding.etProfileAge.isEnabled = !loading
        binding.etProfileGender.isEnabled = !loading
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

