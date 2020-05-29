package bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import bluetooth.BluetoothScanningService
import com.example.coviddetector.background_.CoronaApplication
import prefs.SharedPref
import prefs.SharedPrefsConstants
import utilities.Constants
import utilities.CorUtility

/**
 *  @author Damanpreet.Singh
 * @author Niharika.Arora
 */
class BackgroundWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    private val mContext: Context = context

    override fun doWork(): Result {
        val intent =
            Intent(mContext, BluetoothScanningService::class.java)
        intent.putExtra(Constants.FROM_MY_WORKER, true)

        startService(intent)

        if (CorUtility.isBluetoothAvailable()) {
            BluetoothAdapter.getDefaultAdapter().startDiscovery()
        }
        CorUtility.remove30DaysOldData()
        return Result.success()

    }

    private fun startService(intent: Intent) {
        val uniqueId = SharedPref.getStringParams(
            CoronaApplication.getInstance(),
            SharedPrefsConstants.UNIQUE_ID,
            Constants.EMPTY
        )
        if (!BluetoothScanningService.serviceRunning && uniqueId.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mContext.startForegroundService(intent)
            } else {
                mContext.startService(intent)
            }
        }
    }

    companion object {
        val UNIQUE_WORK_NAME = BackgroundWorker::class.java.simpleName
    }
}