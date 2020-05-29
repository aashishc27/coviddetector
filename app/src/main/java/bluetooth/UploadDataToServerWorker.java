package bluetooth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;


/**
 * @author Niharika
 */

public class UploadDataToServerWorker extends Worker {

    public UploadDataToServerWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
//        UploadDataUtil uploadDataUtil = new UploadDataUtil();
//        uploadDataUtil.start();
        return Result.success();
    }
}
