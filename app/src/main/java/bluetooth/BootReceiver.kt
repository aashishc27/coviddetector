package bluetooth


import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import bluetooth.BluetoothScanningService
import com.example.coviddetector.background_.CoronaApplication
import prefs.SharedPref
import prefs.SharedPrefsConstants
import utilities.Constants
import utilities.CorUtility

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, bootIntent: Intent?) {
        if (context == null) return
        if (bootIntent?.action == Intent.ACTION_BOOT_COMPLETED) {
            startService(context)
        }
    }

    private fun startService(context: Context) {
        if (true) return //check for login in
        if (arePermissionsGranted(context)) {
            configureService(context)
            CorUtility.startBackgroundWorker()
        }
    }

    private fun configureService(context: Context) {
        val uniqueId = SharedPref.getStringParams(
            CoronaApplication.getInstance(),
            SharedPrefsConstants.UNIQUE_ID,
            Constants.EMPTY
        )
        if (!BluetoothScanningService.serviceRunning && uniqueId.isNotEmpty()) {
            val intent = Intent(context, BluetoothScanningService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun arePermissionsGranted(context: Context): Boolean {
        val permission1 = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
        val permission2 =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
        val permission3 =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val permission4 =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (permission1 != PackageManager.PERMISSION_GRANTED
            || permission2 != PackageManager.PERMISSION_GRANTED
            || permission3 != PackageManager.PERMISSION_GRANTED
            || permission4 != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }

}