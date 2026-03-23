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
import com.example.newstart.core.common.R
import com.example.newstart.core.common.ui.cards.ActionGroupCardModel
import com.example.newstart.core.common.ui.cards.CardTone
import com.example.newstart.core.common.ui.cards.EvidenceCardModel
import com.example.newstart.core.common.ui.cards.MedicalCardRenderer
import com.example.newstart.core.common.ui.cards.RiskSummaryCardModel
import com.example.newstart.feature.profile.databinding.FragmentProfileBinding
import com.example.newstart.repository.CloudAccountRepository
import com.example.newstart.repository.DemoBootstrapCoordinator
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val accountRepository = CloudAccountRepository()
    private val demoBootstrapCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        DemoBootstrapCoordinator(requireContext().applicationContext)
    }

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
        bindQuickEntryCards()
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
        binding.btnCloudDemoLogin.setOnClickListener {
            loginWithDemoAccount()
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
            }.onFailure {
                if (accountRepository.getCurrentSession() == null) {
                    renderLoggedOut()
                }
            }
        }
    }

    private fun renderLoggedOut() {
        setCloudAuthActionsEnabled(true)
        binding.tvCloudAccountState.text = getString(R.string.cloud_account_not_logged_in)
        binding.btnCloudRegister.isVisible = true
        binding.btnCloudLogin.isVisible = true
        binding.btnCloudDemoLogin.isVisible = true
        binding.btnLogout.isVisible = false
        binding.tvProfileName.text = getString(R.string.profile_name)
        binding.tvProfileEmail.text = getString(R.string.profile_email)
        ProfileAvatarHelper.applyBadge(
            binding.tvProfileAvatar,
            getString(R.string.profile_name),
            getString(R.string.profile_email)
        )
        bindProfileCards(
            isLoggedIn = false,
            username = getString(R.string.profile_name),
            email = getString(R.string.profile_email),
            accountState = getString(R.string.cloud_account_not_logged_in)
        )
    }

    private fun renderProfile(username: String, email: String, accountState: String) {
        binding.tvCloudAccountState.text = accountState
        binding.btnCloudRegister.isVisible = false
        binding.btnCloudLogin.isVisible = false
        binding.btnCloudDemoLogin.isVisible = false
        binding.btnLogout.isVisible = true
        binding.tvProfileName.text = username
        binding.tvProfileEmail.text = email
        ProfileAvatarHelper.applyBadge(binding.tvProfileAvatar, username, email)
        bindProfileCards(
            isLoggedIn = true,
            username = username,
            email = email,
            accountState = accountState
        )
    }

    private fun bindProfileCards(isLoggedIn: Boolean, username: String, email: String, accountState: String) {
        val notificationSettings = ProfileSettingsStore.getNotificationSettings(requireContext())
        val evidenceCards = listOf(
            EvidenceCardModel(
                title = "账户状态",
                value = if (isLoggedIn) "已连接云端" else "仅本地模式",
                note = accountState,
                badgeText = "同步",
                tone = if (isLoggedIn) CardTone.POSITIVE else CardTone.NEUTRAL
            ),
            EvidenceCardModel(
                title = "语音播报",
                value = if (notificationSettings.avatarSpeechEnabled) "已开启" else "已关闭",
                note = if (notificationSettings.avatarSpeechEnabled) {
                    "进入页面和点击机器人时都会播报。"
                } else {
                    "当前仅显示文字提示，不自动播音。"
                },
                badgeText = "机器人",
                tone = if (notificationSettings.avatarSpeechEnabled) CardTone.INFO else CardTone.NEUTRAL
            ),
            EvidenceCardModel(
                title = "应用内通知",
                value = if (notificationSettings.notificationsEnabled) "已开启" else "已关闭",
                note = if (notificationSettings.notificationsEnabled) {
                    "报告提醒与干预提醒可继续生效。"
                } else {
                    "应用内提醒已关闭，可在通知设置里调整。"
                },
                badgeText = "提醒",
                tone = if (notificationSettings.notificationsEnabled) CardTone.INFO else CardTone.WARNING
            )
        )
        MedicalCardRenderer.renderEvidenceCards(binding.layoutProfileEvidenceCards, evidenceCards)
        MedicalCardRenderer.renderRiskSummaryCard(
            binding.containerProfileRiskCard,
            RiskSummaryCardModel(
                badgeText = if (isLoggedIn) "已同步" else "待登录",
                title = if (isLoggedIn) "云端账户已就绪" else "当前仅使用本地模式",
                summary = if (isLoggedIn) {
                    "个人资料、问诊摘要和报告结果可以继续在多设备间同步。"
                } else {
                    "登录后可同步云端账户、报告分析、问诊摘要和个人资料。"
                },
                supportingText = if (isLoggedIn) "$username · $email" else "未登录时仍可浏览本地页面与部分功能。",
                bullets = if (isLoggedIn) {
                    listOf("可进入个人信息页面维护资料", "通知与语音设置仍在本地可调", "可继续进入干预中心查看主流程")
                } else {
                    listOf("注册或登录后可同步更多数据", "当前状态不会删除本地已有内容", "可先从干预中心和首页继续体验")
                },
                tone = if (isLoggedIn) CardTone.POSITIVE else CardTone.WARNING
            )
        )
    }

    private fun bindQuickEntryCards() {
        MedicalCardRenderer.renderActionGroupCards(
            binding.layoutProfileQuickActionCards,
            listOf(
                ActionGroupCardModel(
                    category = "快捷入口",
                    headline = "进入干预中心",
                    supportingText = "继续查看症状自查、报告分析和干预执行主线。",
                    detailLines = listOf("适合直接回到今天的主要操作流程"),
                    actionLabel = "打开",
                    enabled = true,
                    tone = CardTone.INFO
                ),
                ActionGroupCardModel(
                    category = "项目信息",
                    headline = "查看关于项目",
                    supportingText = "了解版本信息、项目定位和当前核心能力。",
                    detailLines = listOf("适合写材料或快速核对功能范围"),
                    actionLabel = "查看",
                    enabled = true,
                    tone = CardTone.NEUTRAL
                )
            )
        ) { card ->
            when (card.headline) {
                "进入干预中心" -> findNavController().navigate(R.id.navigation_intervention_center)
                "查看关于项目" -> findNavController().navigate(R.id.action_profile_to_about_project)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun loginWithDemoAccount() {
        setCloudAuthActionsEnabled(false)
        lifecycleScope.launch {
            accountRepository.loginWithDemoAccount()
                .onSuccess { authData ->
                    demoBootstrapCoordinator.bootstrapForAuth(authData)
                        .onSuccess { bootstrapResult ->
                            showToast(
                                when {
                                    bootstrapResult.isDemoAccount && bootstrapResult.message.isNotBlank() -> bootstrapResult.message
                                    else -> getString(R.string.cloud_demo_quick_entry_success)
                                }
                            )
                            refreshCloudAccountUi()
                        }
                        .onFailure {
                            showToast(it.message ?: getString(R.string.cloud_demo_quick_entry_failed))
                        }
                }
                .onFailure {
                    showToast(it.message ?: getString(R.string.cloud_demo_quick_entry_failed))
                }

            setCloudAuthActionsEnabled(true)
        }
    }

    private fun setCloudAuthActionsEnabled(enabled: Boolean) {
        binding.btnCloudRegister.isEnabled = enabled
        binding.btnCloudLogin.isEnabled = enabled
        binding.btnCloudDemoLogin.isEnabled = enabled
        binding.btnLogout.isEnabled = enabled
        binding.btnCloudDemoLogin.text = getString(
            if (enabled) {
                R.string.cloud_demo_quick_entry
            } else {
                R.string.cloud_demo_quick_entry_loading
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

