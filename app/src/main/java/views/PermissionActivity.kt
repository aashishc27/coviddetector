package views

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import bluetooth.BluetoothScanningService
import com.example.coviddetector.BuildConfig
import com.example.coviddetector.R
import com.example.coviddetector.background_.CoronaApplication
import kotlinx.android.synthetic.main.activity_permission.*
import prefs.SharedPref
import prefs.SharedPrefsConstants
import utilities.Constants
import utilities.CorUtility
import utilities.CorUtility.Companion.arePermissionsGranted
import utilities.CorUtility.Companion.enableLocation
import utilities.LocalizationUtil.getLocalisedString
import utilities.LocationUtils


class PermissionActivity : AppCompatActivity(), SelectLanguageFragment.LanguageChangeListener {

    private var alertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)
        initViews()
        observeViewModel()
        CorUtility.enableBluetooth()
        //AnalyticsUtils.sendEvent(EventNames.EVENT_OPEN_PERMISSION)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (alertDialog != null && alertDialog!!.isShowing) {
            alertDialog!!.dismiss()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == BLUETOOTH_ENABLE_CODE) {
            configureLogin()
        } else if (requestCode == LocationUtils.LOCATION_REQUEST) {
            configureLogin()
        }
    }


    private fun initViews() {

        tv_permissions_title.text = getLocalisedString(this, R.string.permissions_title)

       // tv_permissions_detail.text = getLocalisedString(this, R.string.permissions_detail)

        tv_device_location.text = HtmlCompat.fromHtml(
            getLocalisedString(this, R.string.device_location),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )

        tv_location_text.text = getLocalisedString(this, R.string.location_text)


        tv_bluetooth.text = HtmlCompat.fromHtml(
            getLocalisedString(this, R.string.bluetooth),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )

        tv_bluetooth_text.text =
            getLocalisedString(this, R.string.monitors_your_device_s_proximity_within_6_feet_range)

        tv_data_sharing.text = HtmlCompat.fromHtml(
            getLocalisedString(this, R.string.data_sharing_with_the_ministry),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )

        tv_data_sharing_text.text = getLocalisedString(
            this,
            R.string.tracks_an_individual_s_touch_points_so_can_easily_find_others_who_came_in_close_contact
        )

        val htmlTextTnc = HtmlCompat.fromHtml(
            getLocalisedString(this, R.string.permission_info_tnc_text),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
        CorUtility.setTextViewHTML(tv_tnc_text, htmlTextTnc) {
            val bundle = Bundle()
            bundle.putBoolean(HomeActivity.EXTRA_ASK_PERMISSION, false)
            bundle.putBoolean(HomeActivity.DO_NOT_SHOW_BACK, false)
            CorUtility.openWebView(it, "", context = this, extrasBundle = bundle)
        }


          btn_start.text = getLocalisedString(this, R.string.contribute_to_a_safer_india)


    }


    /**
     * This method is used to Enable bluetooth if it's Disabled and Make Bluetooth discoverable to other.
     */
    private fun enableAndDiscoverableBluetooth(): Boolean {

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            showToast("No Bluetooth on this handset")
        } else {
            //let's make the user enable BT if it isn't already
            if (!bluetoothAdapter.isEnabled) {
                enableBluetooth()
                return false
            }
            if (bluetoothAdapter.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                makeBluetoothDiscoverable()
                return false
            }
        }
        return true
    }

    private fun enableBluetooth() {
        try {
            val enableBT = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBT, BLUETOOTH_ENABLE_CODE)
        } catch (ex: Exception) {
        }
    }

    private fun makeBluetoothDiscoverable() {
        try {
            val discoverableIntent: Intent =
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300000)
                }
            startActivityForResult(discoverableIntent, BLUETOOTH_ENABLE_CODE)
        } catch (ex: Exception) {
            //do nothing
        }
    }


    private fun observeViewModel() {

        btn_start.isEnabled = true
        if (true) {
            btn_start.setOnClickListener {
                val did = SharedPref.getStringParams(CoronaApplication.instance, SharedPrefsConstants.UNIQUE_ID, "")
                if ( !TextUtils.isEmpty(did)) { // AuthUtility.isSignedIn()
                    startBluetoothService()
                } else {
                    configureLogin()
                }
            }
        } else {
            showToast(Constants.ALREADY_CONTRIBUTING, Toast.LENGTH_LONG)
        }



    }

    private fun showToast(message: String, interval: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(
            CoronaApplication.instance,
            message,
            interval
        ).show()
    }

    /**
     * This method is used to prepare for login.
     * Grant all required permission.
     * Enable location and bluetooth and make bluetooth discoverable.
     */
    private fun configureLogin() {
        if (arePermissionsGranted(this)) {
            if (!CorUtility.isLocationOn(this)) {
                enableLocation(this) {
                    if (enableAndDiscoverableBluetooth()) {
                        login()
                    }
                }
            } else {
                if (enableAndDiscoverableBluetooth()) {
                    login()
                }
            }
        } else {
            CorUtility.requestPermissions(this, REQUEST_CODE_PERMISSION)
        }
    }

    /**
     * This method is used to start bluetooth service and start repeated task in the background.
     * This also move user to dashboard.
     */
    private fun startBluetoothService() {
        if (arePermissionsGranted(this)) {
            val uniqueId = SharedPref.getStringParams(
                CoronaApplication.getInstance(),
                SharedPrefsConstants.UNIQUE_ID,
                ""
            )
            if (uniqueId.isNotEmpty() && !isFinishing) {
                val intent = Intent(this, BluetoothScanningService::class.java)
                ContextCompat.startForegroundService(this, intent)
            }
            CorUtility.startBackgroundWorker()
            val intent = HomeActivity.getLaunchIntent("BuildConfig.WEB_URL", "", this)
            intent.putExtra(HomeActivity.EXTRA_ASK_PERMISSION, true)
            intent.putExtra(HomeActivity.DO_NOT_SHOW_BACK, true)
            startActivity(intent)
            finish()

        } else {
            showPermissionAlert()
        }
    }

    private fun showPermissionAlert() {

        val builder = AlertDialog.Builder(this)
        builder.setMessage(getLocalisedString(this, R.string.permission_alert_message))
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                openAppSettings()
            }
        alertDialog = builder.create()
        if (!isFinishing) {
            alertDialog?.show()
        }
    }

    private fun openAppSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts(Constants.PACKAGE, packageName, null)
        intent.data = uri
        try {
            startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
        }
    }


    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        var isPermissionGranted = true
        if (grantResults.isNotEmpty()) {
            for (x in permissions.indices) {
                val permission = permissions[x]
                if (grantResults[x] == PackageManager.PERMISSION_DENIED) {
                    val showRationale = shouldShowRequestPermissionRationale(permission)
                    if (!showRationale) {
                        isPermissionGranted = false
                        showPermissionAlert()
                        break
                    } else {
                        isPermissionGranted = false
                        break
                    }
                }
            }
        } else {
            isPermissionGranted = false
        }
        if (isPermissionGranted) {
            configureLogin()
        }
    }


    /**
     * This method is used to start Login process.
     */
    private fun login() {
//        CoronaApplication.warmUpLocation()
//        val bottomSheet = LoginBottomSheet()
//        bottomSheet.isCancelable = false
//        val fragmentTransaction =
//            supportFragmentManager.beginTransaction()
//        fragmentTransaction.add(bottomSheet, "LOGIN")
//        fragmentTransaction.commitAllowingStateLoss()

        val intent = HomeActivity.getLaunchIntent("BuildConfig.WEB_URL", "", this)
        intent.putExtra(HomeActivity.EXTRA_ASK_PERMISSION, true)
        intent.putExtra(HomeActivity.DO_NOT_SHOW_BACK, true)
        startActivity(intent)
        finish()
    }

    private fun showLanguageSelectionDialog() {
        SelectLanguageFragment.showDialog(supportFragmentManager, true)
    }

    interface LoginSuccess {
        fun loginSuccess()
        fun loginFailed()
    }

    override fun languageChange() {
        finish()
        startActivity(intent)
    }

    companion object {
        private const val BLUETOOTH_ENABLE_CODE = 123
        private const val REQUEST_CODE_PERMISSION = 642
    }
}
