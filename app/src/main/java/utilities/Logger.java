package utilities;

import android.util.Log;




/**
 * @author chandrapal.yadav
 */

public class Logger {



    private static boolean enableLog = true;

    public static boolean isEnableLog() {
        return enableLog;
    }

    public static void setEnableLog(boolean enableLog) {
        Logger.enableLog = enableLog;
    }


    public static void d(String tag, String msg) {
        if (isEnableLog())
            Log.d(tag, msg);
    }

    public static void e(String tag, String msg) {
        if (isEnableLog())
            Log.e(tag, msg);
    }

    public static void i(String tag, String msg) {
        if (isEnableLog())
            Log.i(tag, msg);
    }

    public static void v(String tag, String msg) {
        if (isEnableLog())
            Log.v(tag, msg);
    }

    public static void w(String tag, String msg) {
        if (isEnableLog())
            Log.w(tag, msg);
    }
}
