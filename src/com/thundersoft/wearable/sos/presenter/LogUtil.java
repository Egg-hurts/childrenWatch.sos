package com.thundersoft.wearable.sos.presenter;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;


public class LogUtil {

    private static boolean IS_LOG_ON = false;
    private static boolean IS_LOGFILE_ON = false;
    private static String TAG = "Sos";
    private static String LOG_FILE_DIR = "com.tcl.dayanta";
    private static String LOG_FILE_NAME = "log_file.txt";

    /**
     * app config log
     *
     * @param appContext
     * @param isLogOn
     * @param isLogFileOn
     */
    public static void configLog(Context appContext, boolean isLogOn, boolean isLogFileOn) {
//        try {
//            TAG = appContext.getPackageName() + appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
//        } catch (PackageManager.NameNotFoundException exp) {
//            e(TAG, "configLog exception package not found,packageName " + appContext.getPackageName());
//        }
        IS_LOG_ON = isLogOn;
        IS_LOGFILE_ON = isLogFileOn;
        LOG_FILE_DIR = appContext.getPackageName() + File.separator + "log" + File.separator;
    }

    public static void v(String info) {
        v(null, info);
    }

    public static void v(String tag, String info) {
        if (isShowLog(Log.VERBOSE)) {
            Log.v(TAG, createSubTagInfo(tag, info));
            logcatTOSDCard(info);
        }
    }

    public static void d(String info) {
        d(null, info);
    }

    public static void d(String tag, String info) {
        if (isShowLog(Log.DEBUG)) {
            Log.d(TAG, createSubTagInfo(tag, info));
            logcatTOSDCard(info);
        }
    }

    public static void i(String info) {
        i(null, info);
    }

    public static void i(String tag, String info) {
        if (isShowLog(Log.INFO)) {
            Log.d(TAG, createSubTagInfo(tag, info));
            logcatTOSDCard(info);
        }
    }

    public static void w(String info) {
        w(null, info);
    }

    public static void w(String tag, String info) {
        if (isShowLog(Log.WARN)) {
            Log.w(TAG, createSubTagInfo(tag, info));
            logcatTOSDCard(info);
        }
    }

    public static void e(String info) {
        e(null, info, null);
    }

    public static void e(String tag, String info) {
        e(tag, info, null);
    }

    public static void e(String tag, String info, Throwable t) {
        Log.e(TAG, createSubTagInfo(tag, info), t);
        logcatTOSDCard(info + (t != null ? t.getMessage() : ""), true);
    }

    public static void e(Throwable t) {
        e(t.toString());
    }

    public static void e(String info, Throwable t) {
        e(info + t.toString());
    }


    private static String createSubTagInfo(String tag, String info) {
        return "[" + (TextUtils.isEmpty(tag) ? "" : tag) + "]" + info;
    }

    private static boolean isShowLog(int d) {
        return IS_LOG_ON || Log.isLoggable(TAG, d);
    }

    private static void logcatTOSDCard(String log) {
        logcatTOSDCard(log, false);
    }

    private static void logcatTOSDCard(String log, boolean exception) {
        if (IS_LOGFILE_ON || exception) {
            writeToSDCard(LOG_FILE_NAME,
                    "[" + getDateTime() + "]" + "----->" + log + "\n");
        }
    }

    private static String getDateTime() {
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        return ts.toString();
    }

    private static void writeToSDCard(String fileName, String text) {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Log.e(TAG, text);
            File file = newFileByName(fileName);
            writeToSDCard(file, text);
        }
    }

    private static void writeToSDCard(File file, String text) {
        FileOutputStream fileOutputStream = null;
        try {
            Log.e(TAG, text);
            fileOutputStream = new FileOutputStream(file, true);
            fileOutputStream
                    .write(("[" + getDateTime() + "]" + "\n").getBytes());
            fileOutputStream.write(text.getBytes());
        } catch (Exception e) {
            Log.e(TAG, "writeToSDCard", e);
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "writeToSDCard", e);
                }
            }
        }
    }

    private static File newFileByName(String name) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        File file = new File(Environment.getExternalStorageDirectory(),
                LOG_FILE_DIR + name);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                file = null;
                Log.e(LogUtil.class.getSimpleName(), "name= " + name, e);
            }
        }
        return file;
    }
}
