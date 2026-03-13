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
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.newstart.R
import com.example.newstart.data.ConnectionState
import com.example.newstart.databinding.FragmentDeviceBinding

class DeviceFragment : Fragment() {

    private var _binding: FragmentDeviceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceListAdapter
    private var advancedExpanded = false

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
        }

        viewModel.isScanning.observe(viewLifecycleOwner) { scanning ->
            binding.layoutScanning.visibility = if (scanning) View.VISIBLE else View.GONE
            binding.btnScan.isEnabled = !scanning
        }

        viewModel.scannedDevices.observe(viewLifecycleOwner) { devices ->
            if (devices.isNotEmpty()) {
                binding.cardDeviceList.visibility = View.VISIBLE
                deviceAdapter.submitList(devices)
            } else {
                binding.cardDeviceList.visibility = View.GONE
            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
