package com.aidengilmartin.watchtweak

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.android.synthetic.main.activity_device_scan.*

private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 1
private const val BLUETOOTH_ADMIN_PERMISSION_REQUEST_CODE = 2
private const val ACCESS_FINE_LOCATION_PERMISSION_REQUEST_CODE = 3
private const val ACCESS_COURSE_LOCATION_PERMISSION_REQUEST_CODE = 4
const val ENABLE_BLUETOOTH_REQUEST_CODE = 5

class DeviceScanActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_scan)
        // UI
        val message = intent.getStringExtra("MESSAGE_KEY")
        if (message != null) {
            Log.i("Intent message: ", message)
        }
        //UI Listeners
        btnCheckPermissions.setOnClickListener {
            obtainNecessaryPermissions()
        }
        // Start BLE Scan
        startBLEScan()
        // Setup Recycler View
        setupRecyclerView()
    }

    // BLE

    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        // As this is a variable, not a function. By calling bluetoothManager.adapter, the variable
        // it set to the adapter.
        bluetoothManager.adapter
    }

    private fun enableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBTIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBTIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
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
            obtainNecessaryPermissions()
            bleScanResults.clear()
            bleScanResultAdapter.notifyDataSetChanged()
            bleScanner.startScan(null, bleScanSettings, bleScanCallback)
            bleScanRunning = true
        } else {
            enableBluetooth()
        }

    }

    private fun stopBLEScan() {
        bleScanner.stopScan(bleScanCallback)
        bleScanRunning = false
    }

    private val bleScanResults = mutableListOf<ScanResult>()

    private val bleScanResultAdapter: BleScanResultAdapter by lazy {
        BleScanResultAdapter(bleScanResults) { result ->
            // User tapped on a scan result
            if (bleScanRunning) {
                stopBLEScan()
            }
            with(result.device) {
                Log.w("bleScanResultAdapter", "User tapped $address")

                // Return a result from this intent for testing and finish activity.
                val resultIntent = Intent()
                resultIntent.putExtra("BT_MAC", address)
                resultIntent.putExtra("BT_NAME", name)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val indexQuery =
                bleScanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) {
                // A scan result already exists with the same address
                bleScanResults[indexQuery] = result
                bleScanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with(result.device) {
                    Log.i(
                        "bleScanCallback",
                        "Found BLE device. Name: ${name ?: "Unnamed"}, address: $address"
                    )
                }
                bleScanResults.add(result)
                bleScanResultAdapter.notifyItemChanged(bleScanResults.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("bleScanCallback", "onScanFailed: code $errorCode")
        }
    }

    // END BLE

    private fun setupRecyclerView() {
        scan_results_recycler_view.apply {
            adapter = bleScanResultAdapter
            layoutManager = LinearLayoutManager(
                this@DeviceScanActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        val animator = scan_results_recycler_view.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    override fun onBackPressed() {
        // Handle back button press
        stopBLEScan()
        // This can be seen as the user cancelling the activity so will return that and finish activity.
        val resultIntent = Intent()
        setResult(Activity.RESULT_CANCELED, resultIntent)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Handle the result of permission requests.
        when (requestCode) {
            BLUETOOTH_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    // TODO: Show a dialog informing the user as to why it's required
                    obtainNecessaryPermissions()
                }
            }
            BLUETOOTH_ADMIN_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    // TODO: Show a dialog informing the user as to why it's required
                    obtainNecessaryPermissions()
                }
            }
            ACCESS_FINE_LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    // TODO: Show a dialog informing the user as to why it's required
                    obtainNecessaryPermissions()
                }
            }
            ACCESS_COURSE_LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    // TODO: Show a dialog informing the user as to why it's required
                    obtainNecessaryPermissions()
                }
            }
        }
    }

    private fun obtainNecessaryPermissions() {
        if (!isBluetoothPermissionGranted) {
            requestPermission(
                Manifest.permission.BLUETOOTH,
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
        }
        if (!isBluetoothAdminPermissionGranted) {
            requestPermission(
                Manifest.permission.BLUETOOTH_ADMIN,
                BLUETOOTH_ADMIN_PERMISSION_REQUEST_CODE
            )
        }
        if (!isAccessFineLocationPermissionGranted) {
            requestPermission(
                Manifest.permission.ACCESS_FINE_LOCATION,
                ACCESS_FINE_LOCATION_PERMISSION_REQUEST_CODE
            )
        }
        if (!isAccessCourseLocationPermissionGranted) {
            requestPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                ACCESS_COURSE_LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission),
            requestCode
        )
    }

    private fun isPermissionGranted(permission: String): Boolean {
        when (ContextCompat.checkSelfPermission(this, permission)) {
            PackageManager.PERMISSION_GRANTED -> return true
            PackageManager.PERMISSION_DENIED -> return false
        }
        return false
    }

    private val isBluetoothPermissionGranted
        get() = isPermissionGranted(Manifest.permission.BLUETOOTH)

    private val isBluetoothAdminPermissionGranted
        get() = isPermissionGranted(Manifest.permission.BLUETOOTH_ADMIN)

    private val isAccessFineLocationPermissionGranted
        get() = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    private val isAccessCourseLocationPermissionGranted
        get() = isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
}