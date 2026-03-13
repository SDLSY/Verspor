package com.example.newstart.service

import android.content.Context
import android.content.Intent

object DataCollectionServiceContract {
    const val ACTION_START_COLLECTION = "com.example.newstart.START_COLLECTION"
    const val ACTION_STOP_COLLECTION = "com.example.newstart.STOP_COLLECTION"
    const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
    const val EXTRA_DEVICE_NAME = "extra_device_name"

    private const val SERVICE_CLASS_NAME = "com.example.newstart.service.DataCollectionService"

    fun createStartIntent(
        context: Context,
        deviceAddress: String,
        deviceName: String
    ): Intent {
        return Intent(ACTION_START_COLLECTION)
            .setClassName(context, SERVICE_CLASS_NAME)
            .putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
            .putExtra(EXTRA_DEVICE_NAME, deviceName)
    }

    fun createStopIntent(context: Context): Intent {
        return Intent(ACTION_STOP_COLLECTION)
            .setClassName(context, SERVICE_CLASS_NAME)
    }
}
