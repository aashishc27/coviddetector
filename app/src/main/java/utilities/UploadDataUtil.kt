package utilities

import android.os.Bundle
import android.text.TextUtils
import com.example.coviddetector.BuildConfig
import com.example.coviddetector.background_.CoronaApplication
import db.FightCovidDB
import models.BluetoothData
import models.BulkDataObject
import models.DataPoint
import models.EncryptedInfo
import prefs.SharedPref
import prefs.SharedPrefsConstants
import java.util.concurrent.Callable

/**
 * @author Punit Chhajer
 */

class UploadDataUtil(
    private val uploadType: String? = null,
    private val listener: UploadListener? = null
) {

    interface UploadListener {
        fun onUploadSuccess()
        fun onUploadFailure(uploadType: String)
    }

    private val CHUNK_SIZE = if (BuildConfig.DEBUG) 5 else 500

    private var mDbInstance: FightCovidDB? = null
    private var mOffset = 0

    private val mDecryptionUtils = DecryptionUtil()

    init {
        mDbInstance = FightCovidDB.getInstance()
    }

    fun start() {
        val bundle = Bundle()

        val bulkDataObject = getBulkDataObject()
        mDbInstance?.bluetoothDataDao?.allNearbyDevices
        uploadDataInChunks(bulkDataObject, mDbInstance?.bluetoothDataDao?.rowCount ?: 0)
    }

    fun startInBackground() {
        ExecutorHelper.getThreadPoolExecutor().execute {
            start()
        }
    }

    private fun uploadDataInChunks(
            bulkDataObj: BulkDataObject,
            rowCount: Long
    ) {

        uploadSuccess();


//        try {
//            val response =
//                    hitNetworkRequest(bulkDataObj) ?: return
//            if (response.isSuccessful) {
//                val mFetchedRows =
//                        if (bulkDataObj.data == null) 0 else bulkDataObj.data.size
//                mOffset += mFetchedRows
//                Logger.d(TAG, "uploadDataInChunks $mOffset <+ $rowCount")
//                if (mOffset.toLong() < rowCount) {
//                    val bulkDataObject = getBulkDataObject()
//                    uploadDataInChunks(bulkDataObject, rowCount)
//                } else {
//                    uploadSuccess()
//                    AnalyticsUtils.sendEvent(EventNames.EVENT_UPLOAD_SUCCESS, null)
//                }
//            } else {
//                uploadFailure()
//                val bundle = Bundle()
//                bundle.putString(
//                        EventParams.PROP_ERROR,
//                        "Response Error: ${response.errorBody()?.string()}"
//                )
//                AnalyticsUtils.sendEvent(EventNames.EVENT_UPLOAD_FAILED, bundle)
//            }
//        } catch (ignore: Exception) {
//            uploadFailure()
//            val bundle = Bundle()
//            bundle.putString(EventParams.PROP_ERROR, "Exception: ${ignore.localizedMessage}")
//            AnalyticsUtils.sendEvent(EventNames.EVENT_UPLOAD_FAILED, bundle)
//        }
    }


    private fun getBulkDataObject(): BulkDataObject {
        Logger.d(TAG, "getBulkDataObject from $mOffset")
        val bulkDataObject = BulkDataObject()
        val bluetoothDataList: List<BluetoothData> = if (mOffset == 0) {
            mDbInstance?.bluetoothDataDao?.getFirstXNearbyDeviceInfo(CHUNK_SIZE) ?: emptyList()
        } else {
            mDbInstance?.bluetoothDataDao
                ?.getXNearbyDeviceInfoWithOffset(CHUNK_SIZE, mOffset) ?: emptyList()
        }
        Logger.d(TAG, "getBulkDataObject data found  ${bluetoothDataList.size}")
        val executor = ExecutorHelper.getThreadPoolExecutor()
        val callables: HashSet<Callable<List<DataPoint>>> = HashSet()
        for (chunk in bluetoothDataList.chunked(getChunkSize(bluetoothDataList.size))) {
            callables.add(Callable<List<DataPoint>> { decryptChunk(chunk) })
        }
        val results = executor.invokeAll(callables)
        val dataPointList: MutableList<DataPoint> = ArrayList()
        for (result in results) {
            dataPointList.addAll(result.get())
        }
        executor.shutdown()
        bulkDataObject.data = dataPointList
        uploadType?.let {
            bulkDataObject.uploadType = it
        }
        bulkDataObject.d = SharedPref.getStringParams(
            CoronaApplication.getInstance(),
            SharedPrefsConstants.UNIQUE_ID,
            Constants.EMPTY
        )
        Logger.d(TAG, "getBulkDataObject data decrypted  ${bluetoothDataList.size}")
        return bulkDataObject
    }

    private fun getChunkSize(size: Int): Int = if (size / 25 <= 10) 25 else 50

    private fun decryptChunk(bluetoothDataList: List<BluetoothData>): List<DataPoint> {
        val dataPointList: MutableList<DataPoint> = ArrayList()
        for (bluetoothData in bluetoothDataList) {
            val dataLatitudeEnc = bluetoothData.latitudeenc
            val dataLongitudeEnc = bluetoothData.longitudeenc
            var decLatitude: String? = "";
            var decLongitude: String? = ""
            if(dataLatitudeEnc!=null&&dataLongitudeEnc!=null) {
                decLatitude = getDecryptedData(dataLatitudeEnc)
                decLongitude = getDecryptedData(dataLongitudeEnc)
            }
            if (!TextUtils.isEmpty(decLatitude) && !TextUtils.isEmpty(decLongitude)) {
                val dataPoint = DataPoint(bluetoothData, decLatitude, decLongitude)
                dataPointList.add(dataPoint)
            }
        }
        return dataPointList
    }

    private fun getDecryptedData(encryptedInfo: EncryptedInfo): String? {
        try {
            return mDecryptionUtils.decryptData(encryptedInfo)
        } catch (e: Exception) {
            //do nothing
        }
        return Constants.EMPTY
    }


    private fun uploadSuccess() {
        listener?.onUploadSuccess()
    }

    private fun uploadFailure() {
        uploadType?.let {
            listener?.onUploadFailure(it)
        }
    }

    companion object {
        private val TAG = UploadDataUtil::class.java.simpleName
    }
}