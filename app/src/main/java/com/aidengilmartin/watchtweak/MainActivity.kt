package com.aidengilmartin.watchtweak

import android.Manifest
import android.annotation.SuppressLint
//import android.R
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*


const val DEVICE_SCAN_ACTIVITY_REQUEST_CODE = 1
private const val FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE = 2

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request addition to battery optimization whitelist
        requestIgnoreBatteryOptimizations()

        if (!isNotificationServiceEnabled()) {
            val enableNotificationListenerAlertDialog = buildNotificationServiceAlertDialog()
            enableNotificationListenerAlertDialog?.show()
        }

        // Get target device address
        tvDeviceAddress.apply { text = "$targetDeviceName $targetDeviceAddress" }

        // UI Listeners
        btnDeviceScanActivity.setOnClickListener {
            val intent = Intent(this, DeviceScanActivity::class.java).apply {
                putExtra("MESSAGE_KEY", "This is a message from MainActivity intent.")
            }
            startActivityForResult(intent, DEVICE_SCAN_ACTIVITY_REQUEST_CODE)
        }

        btnStartBlueDeviceService.setOnClickListener {
            // Newer Android versions need you to request the Foreground service permission
            // but the system will grant it without prompting the user.
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.FOREGROUND_SERVICE),
                FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE
            )
            BlueDeviceService.startService(
                context = this,
                tgtDeviceName = targetDeviceName,
                tgtDeviceAddress = targetDeviceAddress
            )
        }

        btnStopBlueDeviceService.setOnClickListener {
            BlueDeviceService.stopService(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            DEVICE_SCAN_ACTIVITY_REQUEST_CODE -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        val deviceAddressReturned: String? = data?.getStringExtra("BT_MAC")
                        val deviceNameReturned: String? = data?.getStringExtra("BT_NAME")
                        tvDeviceAddress.apply {
                            text = "$deviceNameReturned ($deviceAddressReturned)"
                        }
                        // Set the target device address & name and save in SharedPreferences
                        targetDeviceAddress = deviceAddressReturned
                        targetDeviceName = deviceNameReturned
                    }
                    Activity.RESULT_CANCELED -> {
                        tvDeviceAddress.apply { text = "Activity Canceled" }
                    }
                }
            }
        }
    }

    private var targetDeviceAddress: String?
        get() {
            val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
            return sharedPref.getString("target_device_address", "00:00:00:00:00:00")
        }
        set(address) {
            val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
            with(sharedPref.edit()) {
                putString("target_device_address", address)
                commit()
            }
        }

    private var targetDeviceName: String?
        get() {
            val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
            return sharedPref.getString("target_device_name", "Unnamed")
        }
        set(name) {
            val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
            with(sharedPref.edit()) {
                putString("target_device_name", name)
                commit()
            }
        }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        // https://developer.android.com/training/monitoring-device-state/doze-standby#support_for_other_use_cases
        // https://stackoverflow.com/questions/39256501/check-if-battery-optimization-is-enabled-or-not-for-an-app
        // This method of directly adding application to whitelist is not allowed on the Play Store
        val intent = Intent()
        val wtPackageName: String = packageName
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(wtPackageName)) {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package: $wtPackageName")
            startActivity(intent)
        }
    }

    private fun buildNotificationServiceAlertDialog(): AlertDialog? {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle("Enable Notification Access")
        alertDialogBuilder.setMessage("To send notifications to the watch, the app needs access to the system notifications.")
        alertDialogBuilder.setPositiveButton("Yes",
            DialogInterface.OnClickListener { dialog, id ->
                startActivity(
                    Intent(
                        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
                    )
                )
            })
        alertDialogBuilder.setNegativeButton("No",
            DialogInterface.OnClickListener { dialog, id ->
                // If you choose to not enable the notification listener
                // the app. will not work as expected
            })
        return alertDialogBuilder.create()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":".toRegex()).toTypedArray()
            for (i in names.indices) {
                val cn = ComponentName.unflattenFromString(names[i])
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}