package com.example.newstart.ui.device

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.newstart.core.common.R
import com.example.newstart.core.common.ui.cards.CardTone
import com.example.newstart.core.common.ui.cards.EvidenceCardModel
import com.example.newstart.core.common.ui.cards.MedicalCardRenderer
import com.example.newstart.core.common.ui.cards.RiskSummaryCardModel
import com.example.newstart.data.ConnectionState
import com.example.newstart.data.DeviceInfo
import com.example.newstart.feature.device.databinding.FragmentDeviceBinding

class DeviceFragment : Fragment() {

    private var _binding: FragmentDeviceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceViewModel by activityViewModels()
    private lateinit var deviceAdapter: DeviceListAdapter
    private var advancedExpanded = false
    private var latestDevice: DeviceInfo? = null
    private var latestScanning = false
    private var latestScannedCount = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startScan()
        } else {
            Toast.makeText(
                requireContext(),
                getString(R.string.device_permission_required_cn),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupUi()
        observeData()
        loadDeviceInfo()
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceListAdapter { device ->
            viewModel.connectToDevice(device)
        }
        binding.rvDeviceList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
    }

    private fun setupUi() {
        binding.btnConnect.setOnClickListener { viewModel.connect() }
        binding.btnDisconnect.setOnClickListener { viewModel.disconnect() }
        binding.btnScan.setOnClickListener { checkPermissionsAndScan() }
        binding.btnReadParams.setOnClickListener { viewModel.readWorkParams() }
        binding.btnStartBgCollection.setOnClickListener { viewModel.startBackgroundCollection() }
        binding.btnStopBgCollection.setOnClickListener { viewModel.stopBackgroundCollection() }
        binding.btnToggleDeviceAdvanced.setOnClickListener { toggleAdvancedTools() }
    }

    private fun observeData() {
        viewModel.currentDevice.observe(viewLifecycleOwner) { device ->
            latestDevice = device
            device?.let {
                binding.tvDeviceName.text = it.deviceName
                binding.tvConnectionStatus.text = it.connectionState.getDisplayName()
                binding.tvBatteryLevel.text = "${it.batteryLevel}%"
                binding.tvFirmwareVersion.text = it.firmwareVersion
                binding.tvLastSync.text = getLastSyncText(it.lastSyncTime)

                val colorRes = when (it.connectionState) {
                    ConnectionState.CONNECTED -> R.color.status_connected
                    ConnectionState.CONNECTING -> R.color.status_connecting
                    ConnectionState.DISCONNECTED -> R.color.status_disconnected
                    ConnectionState.SCANNING -> R.color.status_scanning
                }
                val indicatorColor = ContextCompat.getColor(requireContext(), colorRes)
                binding.viewConnectionIndicator.backgroundTintList = ColorStateList.valueOf(indicatorColor)

                val isConnected = it.connectionState == ConnectionState.CONNECTED
                val isConnecting = it.connectionState == ConnectionState.CONNECTING
                binding.btnConnect.isEnabled = !isConnected && !isConnecting
                binding.btnDisconnect.isEnabled = isConnected
                binding.layoutDeviceInfo.visibility = if (isConnected) View.VISIBLE else View.GONE
            }
            bindDeviceCards()
        }

        viewModel.isScanning.observe(viewLifecycleOwner) { scanning ->
            latestScanning = scanning
            binding.layoutScanning.visibility = if (scanning) View.VISIBLE else View.GONE
            binding.btnScan.isEnabled = !scanning
            bindDeviceCards()
        }

        viewModel.scannedDevices.observe(viewLifecycleOwner) { devices ->
            latestScannedCount = devices.size
            if (devices.isNotEmpty()) {
                binding.cardDeviceList.visibility = View.VISIBLE
                deviceAdapter.submitList(devices)
            } else {
                binding.cardDeviceList.visibility = View.GONE
            }
            bindDeviceCards()
        }

        viewModel.statusMessage.observe(viewLifecycleOwner) { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        viewModel.isBackgroundCollecting.observe(viewLifecycleOwner) { collecting ->
            binding.btnStartBgCollection.isEnabled = !collecting
            binding.btnStopBgCollection.isEnabled = collecting
        }
    }

    private fun toggleAdvancedTools() {
        advancedExpanded = !advancedExpanded
        binding.layoutDeviceAdvancedContent.visibility = if (advancedExpanded) View.VISIBLE else View.GONE
        binding.btnToggleDeviceAdvanced.text = getString(
            if (advancedExpanded) R.string.device_advanced_toggle_close
            else R.string.device_advanced_toggle_open
        )
    }

    private fun loadDeviceInfo() {
        if (viewModel.currentDevice.value == null) {
            viewModel.loadCurrentDevice()
        }
    }

    private fun checkPermissionsAndScan() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val allPermissionsGranted = permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            startScan()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun startScan() {
        viewModel.startScan()
    }

    private fun getLastSyncText(timestamp: Long): String {
        if (timestamp == 0L) return getString(R.string.device_sync_never)

        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (1000 * 60)

        return when {
            minutes < 1 -> getString(R.string.device_sync_just_now)
            minutes < 60 -> getString(R.string.device_sync_minutes_ago, minutes)
            minutes < 24 * 60 -> getString(R.string.device_sync_hours_ago, minutes / 60)
            else -> getString(R.string.device_sync_days_ago, minutes / (24 * 60))
        }
    }

    private fun bindDeviceCards() {
        val device = latestDevice
        val state = device?.connectionState ?: ConnectionState.DISCONNECTED
        val lastSyncText = device?.let { getLastSyncText(it.lastSyncTime) } ?: getString(R.string.device_sync_never)
        val evidenceCards = buildList {
            add(
                EvidenceCardModel(
                    title = "连接状态",
                    value = when {
                        latestScanning -> "正在扫描"
                        else -> state.getDisplayName()
                    },
                    note = device?.deviceName ?: getString(R.string.device_name),
                    badgeText = when {
                        latestScanning -> "扫描"
                        else -> "设备"
                    },
                    tone = when {
                        latestScanning -> CardTone.INFO
                        state == ConnectionState.CONNECTED -> CardTone.POSITIVE
                        state == ConnectionState.CONNECTING -> CardTone.WARNING
                        else -> CardTone.NEUTRAL
                    }
                )
            )
            add(
                EvidenceCardModel(
                    title = "同步状态",
                    value = lastSyncText,
                    note = if (latestScannedCount > 0) "附近发现 $latestScannedCount 台可连接设备" else "当前展示最近一次同步记录",
                    badgeText = "同步",
                    tone = if (device != null && device.lastSyncTime > 0L) CardTone.INFO else CardTone.NEUTRAL
                )
            )
            add(
                EvidenceCardModel(
                    title = "设备状态",
                    value = device?.let { "${it.batteryLevel}% / ${it.firmwareVersion}" } ?: "--",
                    note = if (state == ConnectionState.CONNECTED) "可继续读取参数和后台采集" else "连接后可查看电量、固件与采集状态",
                    badgeText = "硬件",
                    tone = if (state == ConnectionState.CONNECTED) CardTone.POSITIVE else CardTone.NEUTRAL
                )
            )
        }
        MedicalCardRenderer.renderEvidenceCards(binding.layoutDeviceEvidenceCards, evidenceCards)
        MedicalCardRenderer.renderRiskSummaryCard(
            binding.containerDeviceRiskCard,
            RiskSummaryCardModel(
                badgeText = when {
                    latestScanning -> "扫描中"
                    state == ConnectionState.CONNECTED -> "已连接"
                    state == ConnectionState.CONNECTING -> "连接中"
                    else -> "未连接"
                },
                title = when {
                    latestScanning -> "正在搜索可连接戒指"
                    state == ConnectionState.CONNECTED -> "设备已接入今日数据链路"
                    state == ConnectionState.CONNECTING -> "正在建立设备连接"
                    else -> "当前未接入设备数据"
                },
                summary = when {
                    latestScanning -> "先完成扫描和连接，再进入后台采集和参数读取。"
                    state == ConnectionState.CONNECTED -> "当前可以继续同步数据、读取参数并开启后台采集。"
                    state == ConnectionState.CONNECTING -> "保持蓝牙开启并靠近戒指，连接成功后会自动刷新状态。"
                    else -> "设备数据尚未进入今日建议和趋势分析，先从扫描或连接开始。"
                },
                supportingText = if (device == null) "当前显示默认设备占位信息。" else "最近同步：$lastSyncText",
                bullets = buildList {
                    if (latestScannedCount > 0) add("附近可连接设备：$latestScannedCount 台")
                    device?.let {
                        add("设备名称：${it.deviceName}")
                        add("当前电量：${it.batteryLevel}%")
                    }
                },
                tone = when {
                    latestScanning -> CardTone.INFO
                    state == ConnectionState.CONNECTED -> CardTone.POSITIVE
                    state == ConnectionState.CONNECTING -> CardTone.WARNING
                    else -> CardTone.NEUTRAL
                }
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

