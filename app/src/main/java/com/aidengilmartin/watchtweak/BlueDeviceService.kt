package com.aidengilmartin.watchtweak

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.util.*


private const val NOTIFICATION_SERVICE_CHANNEL_ID = "BlueDeviceNotificationServiceChannelID"
private const val NOTIFICATION_SERVICE_CHANNEL_NAME = "BlueDeviceServiceNotificationChannelName"
private const val NOTIFICATION_ONGOING_ID = 1
private const val NOTIFICATION_ERROR_ID = 2

private val SG2_SERVICE_UUID: UUID = UUID.fromString("14701820-620a-3973-7c78-9cfff0876abd")
private val SG2_CMD_CHARACTERISTIC_UUID: UUID =
    UUID.fromString("14702856-620a-3973-7c78-9cfff0876abd")
private val SG2_NOTIFICATION_CHARACTERISTIC_UUID: UUID =
    UUID.fromString("14702853-620a-3973-7c78-9cfff0876abd")

class BlueDeviceService : Service() {
    private val serviceStartMode: Int = START_STICKY // Service will restart if it is stopped.
    private val allowRebind: Boolean = true   // Indicates whether onRebind should be used
    private var targetDeviceName: String? = null // Value from MainActivity
    private var targetDeviceAddress: String? = null // Value from MainActivity
    private var targetDeviceObject: BluetoothDevice? = null // Device object from BLE scan

    private var persistentNotificationUpdateDisabled = false

    companion object {
        fun startService(context: Context, tgtDeviceName: String?, tgtDeviceAddress: String?) {
            val startIntent = Intent(context, BlueDeviceService::class.java)
            startIntent.putExtra("target_device_name", tgtDeviceName)
            startIntent.putExtra("target_device_address", tgtDeviceAddress)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, BlueDeviceService::class.java)
            context.stopService(stopIntent)
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_SERVICE_CHANNEL_ID,
            NOTIFICATION_SERVICE_CHANNEL_NAME,
            // INFO: IMPORTANCE is the equivalent of PRIORITY in Android 7.1 or lower
            // https://developer.android.com/training/notify-user/channels
            NotificationManager.IMPORTANCE_MIN
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager!!.createNotificationChannel(serviceChannel)
    }

    private fun getServiceNotification(statusText: String? = null): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        val status: String = when (statusText) {
            null -> "Starting"
            else -> statusText
        }

        return NotificationCompat.Builder(this, NOTIFICATION_SERVICE_CHANNEL_ID)
            .setContentTitle("Status: $status")
            .setContentText("Device: $targetDeviceName ($targetDeviceAddress)")
            .setSmallIcon(R.drawable.ic_notification_white)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(statusText: String) {
        // Only allow updating of the notification if a flag hasn't been toggled stating
        // that is should not be. This counters the edge case when stopping the service that
        // bluetooth encounters a condition where it updates the notification, thereby
        // causing the notification to reappear despite the service being stopped.
        if (!persistentNotificationUpdateDisabled) {
            val notification: Notification = getServiceNotification(statusText)

            val mNotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mNotificationManager.notify(NOTIFICATION_ONGOING_ID, notification)
        }
    }

    private fun cancelNotification() {
        val mNotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.cancelAll()
    }

    // BLE

    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        // As this is a variable, not a function. By calling bluetoothManager.adapter, the variable
        // it set to the adapter.
        bluetoothManager.adapter
    }

    private var bleScanRunning = false

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val bleScanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private fun startBLEScan() {
        if (bluetoothAdapter.isEnabled) {
            updateNotification("Scanning")
            bleScanner.startScan(null, bleScanSettings, bleScanCallback)
            bleScanRunning = true
        } else {
            Log.e("startBLEScan", "Bluetooth not enabled")
            updateNotification("Error (Bluetooth Off)")
        }
    }

    private fun stopBLEScan() {
        bleScanner.stopScan(bleScanCallback)
        bleScanRunning = false
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.address == targetDeviceAddress) {
                Log.i(
                    "bleScanCallback (onScanResult)",
                    "Target Device Found = ${result.device.address}"
                )
                updateNotification("Scanning (Device Found)")
                stopBLEScan()
                // Set the targetDeviceObject to the object retrieved by the scan
                targetDeviceObject = result.device
                with(result.device) {
                    Log.i("bleScanCallback (onScanResult)", "Connecting to $address")
                    updateNotification("Connecting")
                    connectGatt(applicationContext, true, bleGattCallback)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("bleScanCallback (onScanFailed)", "code $errorCode")
            stopBLEScan()
        }
    }

    private var bleGatt: BluetoothGatt? = null

    private val bleGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(
                        "bleGattCallback (onConnectionStateChange)",
                        "status = BluetoothGatt.GATT_SUCCESS"
                    )
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.i(
                                "bleGattCallback (onConnectionStateChange)",
                                "newState = BluetoothProfile.STATE_CONNECTED"
                            )
                            updateNotification("Connected")
                            // Store reference to Gatt
                            bleGatt = gatt
                            Handler(Looper.getMainLooper()).post {
                                bleGatt?.discoverServices()
                            }
                        }
                        BluetoothProfile.STATE_CONNECTING -> {
                            Log.i(
                                "bleGattCallback (onConnectionStateChange)",
                                "newState = BluetoothProfile.STATE_CONNECTING"
                            )
                            updateNotification("Connecting")
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.e(
                                "bleGattCallback (onConnectionStateChange)",
                                "newState = BluetoothProfile.STATE_DISCONNECTED"
                            )
                            updateNotification("Disconnected")
                        }
                        BluetoothProfile.STATE_DISCONNECTING -> {
                            Log.e(
                                "bleGattCallback (onConnectionStateChange)",
                                "newState = BluetoothProfile.STATE_DISCONNECTING"
                            )
                            updateNotification("Disconnecting")
                        }
                        else -> {
                            Log.e(
                                "bleGattCallback (onConnectionStateChange)",
                                "Unhandled Status ($newState)"
                            )
                            updateNotification("Error (Unhandled State)")
                        }
                    }
                }
                8 -> {
                    // INFO: This may be "Device went out of range"
                    // https://stackoverflow.com/questions/45056566/android-ble-gatt-connection-change-statuses
                    Log.e(
                        "bleGattCallback (onConnectionStateChange)",
                        "Device out of range? ($newState) - Reconnecting"
                    )
                    updateNotification("Disconnected")
                    reconnectBle()
                }
                19 -> {
                    // INFO: This may be "Disconnected by device"
                    Log.e(
                        "bleGattCallback (onConnectionStateChange)",
                        "Disconnected by device? ($newState) - Reconnecting"
                    )
                    updateNotification("Disconnected")
                    reconnectBle()
                }
                133 -> {
                    // INFO: This may be "Device not found"
                    Log.e(
                        "bleGattCallback (onConnectionStateChange)",
                        "Device not found? ($newState) - Retrying"
                    )
                    updateNotification("Disconnected")
                    reconnectBle()
                }
                0 -> {
                    // INFO: This may be "Programmatically disconnected"
                    Log.e(
                        "bleGattCallback (onConnectionStateChange)",
                        "Programmatically disconnected? ($newState) - Retrying"
                    )
                    updateNotification("Disconnected")
                    reconnectBle()
                }
                else -> {
                    Log.e("bleGattCallback (onConnectionStateChange)", "Unhandled Status ($status)")
                    updateNotification("Error (Unhandled Status)")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            with(gatt) {
                Log.i(
                    "bleGattCallback (onServicesDiscovered)",
                    "${services.size} services for ${device.address}"
                )
                services.forEach {
                    if (it.uuid == SG2_SERVICE_UUID) {
                        // Target Service Found
                        val characteristicData: BluetoothGattCharacteristic = it.getCharacteristic(
                            SG2_NOTIFICATION_CHARACTERISTIC_UUID
                        )
                        for (descriptor in characteristicData.descriptors) {
                            // Write data to "SG2 Notification Characteristic" to enable notifications
                            // The SG2 expects 0x0100 to be written to the main notification characteristic
                            // to enable notifications
                            descriptor.value = byteArrayOf(0x01, 0x00)
                            bleGatt?.writeDescriptor(descriptor)
                            Log.i(
                                "bleGattCallback (onServicesDiscovered)",
                                " $descriptor.value.toString()"
                            )
                        }
                        bleGatt?.setCharacteristicNotification(characteristicData, true)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            Log.i(
                "bleGattCallback (onCharacteristicChanged)",
                "Characteristic Changed, UUID = ${characteristic?.uuid}"
            )
            characteristic?.value?.toHex()?.let { Log.i("${characteristic.uuid}", it) }

            if (characteristic != null) {
                val message: ByteArray = characteristic.value
                val cmdCode: Byte = message[0]

                // Playback Controls
                val playbackCMD: Byte = 0x65 // Byte 0 indicates what function is used
                val playbackPlayPause: Byte = 0x01
                val playbackVolUp: Byte = 0x20
                val playbackVolDown: Byte = 0x10
                val playbackTrackNext: Byte = 0x08
                val playbackTrackPrevious: Byte = 0x04

                if (cmdCode == playbackCMD) {
                    Log.i(
                        "bleGattCallback (onCharacteristicChanged)",
                        "Playback control notification received"
                    )
                    val mAudioManager: AudioManager =
                        getSystemService(Context.AUDIO_SERVICE) as AudioManager

                    when (message[1]) {
                        playbackPlayPause -> {
                            if (!mAudioManager.isMusicActive) {
                                val event =
                                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                                mAudioManager.dispatchMediaKeyEvent(event)

                            } else {
                                val event =
                                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
                                mAudioManager.dispatchMediaKeyEvent(event)
                            }
                        }
                        playbackTrackNext -> {
                            val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
                            mAudioManager.dispatchMediaKeyEvent(event)
                        }
                        playbackTrackPrevious -> {
                            val event =
                                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                            mAudioManager.dispatchMediaKeyEvent(event)
                        }
                        playbackVolUp -> {
                            val currentVol =
                                mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            mAudioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                currentVol + 1,
                                AudioManager.FLAG_SHOW_UI
                            )
                        }
                        playbackVolDown -> {
                            val currentVol =
                                mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            mAudioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                currentVol - 1,
                                AudioManager.FLAG_SHOW_UI
                            )
                        }
                    }
                }

                // Find phone function
                val findPhoneCMD: Byte = 0x61
                if (cmdCode == findPhoneCMD) {
                    Log.i(
                        "bleGattCallback (onCharacteristicChanged)",
                        "Find phone notification received"
                    )
                    val sound: Uri =
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val findPing =
                        RingtoneManager.getRingtone(applicationContext, sound)
                    findPing.play()
                }

                // Camera function
                val cameraCMD: ByteArray = byteArrayOf(0x65, 0x29)
                if (message.contentEquals(cameraCMD)) {
                    val builder = NotificationCompat.Builder(
                        this@BlueDeviceService,
                        NOTIFICATION_SERVICE_CHANNEL_ID
                    )
                        .setSmallIcon(R.drawable.ic_notification_white)
                        .setContentTitle("Error")
                        .setContentText("Remote camera not implemented")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                    val mNotificationManager =
                        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    mNotificationManager.notify(NOTIFICATION_ERROR_ID, builder.build())
                    val sound: Uri =
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val findPing =
                        RingtoneManager.getRingtone(applicationContext, sound)
                    findPing.play()

                    syncDateTime()
                }
            }

            super.onCharacteristicChanged(gatt, characteristic)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            Log.i("bleGattCallback (onCharacteristicWrite)", "Fired")
            super.onCharacteristicWrite(gatt, characteristic, status)
        }
    }

    private fun closeBleConnection() {
        bleGatt?.disconnect() // Disconnect to be sure that Gatt instance is done?
        bleGatt?.close() // Close connection & release resources
        bleGatt = null // Nullify
    }

    private fun reconnectBle() {
        bleGatt?.disconnect() // Disconnect to be sure that Gatt instance is done?
        bleGatt?.connect() // Try to reconnect
    }

    private fun syncDateTime() {
        bleGatt?.services?.forEach {
            if (it.uuid == SG2_SERVICE_UUID) {
                // Sync Time
                val cmdCharacteristicData: BluetoothGattCharacteristic =
                    it.getCharacteristic(
                        SG2_CMD_CHARACTERISTIC_UUID
                    )

                val cal = Calendar.getInstance()
                val timeSettingCode: Byte = 0x09
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)
                val second = cal.get(Calendar.SECOND)
                val dateSettingsCode: Byte = 0x08
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val month = cal.get(Calendar.MONTH)

                // Time
                cmdCharacteristicData.value = byteArrayOf(
                    timeSettingCode,
                    hour.toByte(),
                    minute.toByte(),
                    second.toByte()
                )
                val x = bleGatt?.writeCharacteristic(cmdCharacteristicData)
                Log.i("TIME", "$cmdCharacteristicData.value.toHex() = $x")

                // Date
                cmdCharacteristicData.value = byteArrayOf(
                    dateSettingsCode,
                    0x01, // ?
                    0x01, // ?
                    month.toByte(),
                    day.toByte()
                )
                val y = bleGatt?.writeCharacteristic(cmdCharacteristicData)
                Log.i("DATE", "$cmdCharacteristicData.value.toHex() = $y")
            }
        }
    }

    // END BLE

    override fun onCreate() {
        // The service is being created
        Log.i("BlueDeviceService", "onCreate called")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service is starting, due to a call of startService()
        Log.i("BlueDeviceService", "onStartCommand called")

        targetDeviceName = intent?.getStringExtra("target_device_name")
        targetDeviceAddress = intent?.getStringExtra("target_device_address")

        // Start Foreground service
        createNotificationChannel()
        startForeground(NOTIFICATION_ONGOING_ID, getServiceNotification())

        // Start BLE Scan
        startBLEScan()

        // Register to event bus
        EventBus.getDefault().register(this)

        return serviceStartMode
    }

    override fun onBind(intent: Intent): IBinder {
        // A client is binding to the service with bindService()
        Log.i("BlueDeviceService", "onBind called")
        TODO("Not yet implemented")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // All clients have unbound with unbindService()
        // Return a boolean indicating if it is allowed to rebind
        Log.i("BlueDeviceService", "onUnbind called")
        return allowRebind
    }

    override fun onRebind(intent: Intent?) {
        // A client is binding to the service with bindService()
        // after onUnbind() has already been called
        Log.i("BlueDeviceService", "onRebind called")
    }

    override fun onDestroy() {
        // The service is no longer used and is being destroyed
        Log.i("BlueDeviceService", "onDestroy called")
        persistentNotificationUpdateDisabled = true

        // Unregister from event bus
        EventBus.getDefault().unregister(this)

        // INFO: https://stackoverflow.com/questions/35986549/totally-disconnect-a-bluetooth-low-energy-device
        closeBleConnection()
    }

    // EventBus
    @Subscribe(sticky = false)
    fun handleNotificationEvent(event: NotificationEvent) {
        Log.i(
            "BlueDeviceService (handleNotificationEvent)",
            "(EventBus) Package = ${event.notification.packageName}"
        )
        // Prevent event from being redelivered.
        //EventBus.getDefault().removeStickyEvent(event)

        val packageWhitelist: Array<String> = arrayOf("com.google.android.apps.messaging", "com.zoho.mail")
        val notificationPackageName: String = event.notification.packageName

        // Send SMS Remind Command
        bleGatt?.services?.forEach {
            if (it.uuid == SG2_SERVICE_UUID) {
                if (packageWhitelist.contains(notificationPackageName)) {
                    // Package is in the notification whitelist
                    val notificationTitle = event.notification.notification.extras.getString("android.title")
                    val notificationText = event.notification.notification.extras.getString("android.text")
                    Log.i(
                        "BlueDeviceService (handleNotificationEvent)",
                        "Google Messages: Title = $notificationTitle, Text = $notificationText "
                    )
                    sendSMSReminder()
                } else {
                    // Package not in whitelist
                    Log.i("BlueDeviceService (handleNotificationEvent)", "$notificationPackageName not in whitelist")
                }
            }
        }
    }

    // Watch Functions
    private fun sendSMSReminder() {
        bleGatt?.services?.forEach {
            if (it.uuid == SG2_SERVICE_UUID) {
                val cmdCharacteristicData: BluetoothGattCharacteristic =
                    it.getCharacteristic(
                        SG2_CMD_CHARACTERISTIC_UUID
                    )
                val smsRemindCMD: Byte = 0x7
                val smsRemindVAL: Int = -86
                cmdCharacteristicData.value = byteArrayOf(smsRemindCMD, smsRemindVAL.toByte())
                val x = bleGatt?.writeCharacteristic(cmdCharacteristicData)
                Log.i(
                    "BlueDeviceService (handleNotificationEvent)",
                    "Writing SMS Reminder = $cmdCharacteristicData.value.toHex() = $x"
                )
            }
        }
    }


    // Extensions

    private fun ByteArray.toHex(): String {
        return joinToString { "%02x".format(it) }
    }
}
