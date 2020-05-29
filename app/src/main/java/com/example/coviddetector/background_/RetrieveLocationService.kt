package com.example.coviddetector.background_

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import db.DBManager
import models.BluetoothData
import utilities.Constants
import utilities.Logger


/**
 * Created by Aman Bansal on 23/03/20.
 */

class RetrieveLocationService {
    companion object {
        private var TAG = RetrieveLocationService::class.java.simpleName
        private const val UPDATE_INTERVAL: Long = 30 * 60 * 1000  /* 30 min */
        private const val FASTEST_INTERVAL: Long = 5 * 60 * 1000 /* 5 min */
        private const val DISPLACEMENT = 100f //100m
    }


    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private val context = CoronaApplication.getInstance()
    private var isServiceRunning = false


    private var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {

            locationResult?.let {
                if (it.lastLocation != null) {
                    val usersLocationData =
                        BluetoothData(
                            Constants.EMPTY,
                            0,
                            Constants.EMPTY,
                            Constants.EMPTY
                        )
                    usersLocationData.latitude = it.lastLocation.latitude
                    usersLocationData.longitude = it.lastLocation.longitude

                    CoronaApplication.getInstance().setBestLocation(it.lastLocation)

                    Logger.d(
                        "Retreive location service",
                        usersLocationData.latitude.toString() + " - " + usersLocationData.longitude.toString()
                    )
                    DBManager.insertNearbyDetectedDeviceInfo(listOf(usersLocationData))
                }
            }

        }
    }


    fun startService() {
        if (isServiceRunning) {
            return
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        getLocation()
    }


    private fun getLocation() {
        // ---------------------------------- LocationRequest ------------------------------------
        // Create the location request to start receiving updates
        val mLocationRequestHighAccuracy = LocationRequest()
        mLocationRequestHighAccuracy.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        mLocationRequestHighAccuracy.interval = UPDATE_INTERVAL
        mLocationRequestHighAccuracy.fastestInterval = FASTEST_INTERVAL
        mLocationRequestHighAccuracy.smallestDisplacement = DISPLACEMENT



        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }


        mFusedLocationClient.requestLocationUpdates(
            mLocationRequestHighAccuracy, locationCallback,
            Looper.myLooper()
        )

        isServiceRunning = true
    }

    fun stopService() {

        if (isServiceRunning) {
            mFusedLocationClient.removeLocationUpdates(locationCallback).addOnSuccessListener {
                isServiceRunning = false
            }
        }
    }

}