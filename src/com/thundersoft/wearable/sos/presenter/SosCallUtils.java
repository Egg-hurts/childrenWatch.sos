package com.thundersoft.wearable.sos.presenter;

import android.app.PendingIntent;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import com.thundersoft.wearable.sos.presenter.model.EmergencyContactsList;
import java.util.ArrayList;
import java.util.List;

public class SosCallUtils {

    private static final String TAG = SosCallUtils.class.getSimpleName();

    //indicate network not available
    private static final int NETWORK_TYPE_UNKNOWN = 0;
    //indicate sim state is ready
    private static final int SIM_STATE_READY = 5;

    public static boolean hasSimCard(Context context) {
        TelephonyManager mgr = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        int simState = mgr.getSimState();
        if (simState != TelephonyManager.SIM_STATE_READY) {
            return false;
        }
        return true;
    }

    /**
    *
    * @return true if sim card is ready, false else.
    */
   public static boolean isSimReady(Context context){
       boolean isSimReady = false;
       TelephonyManager tm = (TelephonyManager) context
               .getSystemService(Context.TELEPHONY_SERVICE);
       int simState = tm.getSimState();
       LogUtil.i(TAG,"isSimReady ,simState is :" + simState);
       if (simState == SIM_STATE_READY) {
           LogUtil.i(TAG,"isSimReady ,simState is SIM_STATE_READY");
           isSimReady = true;
       }
       return isSimReady;
   }

    /**
    *
    * @return true if network is available, false else.
    */
    public static boolean isNetworkAvailable(Context context){
       TelephonyManager mTelephonyManager = (TelephonyManager) context
               .getSystemService(Context.TELEPHONY_SERVICE);
       int networkType = mTelephonyManager.getNetworkType();
       if(networkType == NETWORK_TYPE_UNKNOWN){
           LogUtil.i(TAG,"network not available");
           return false;
       }
       return true;
       }

    public static List<String>  readEmergencyContactsListNumberFromDB(Context context){
        EmergencyContactsList eC = new EmergencyContactsList(context);
        return eC.readEmergencyContactsListNumberFromDB();
    }

    public static void sendPositon(Context mContext, String phoneNumber,
            PendingIntent locationPi, String location) {
            SmsManager manager = SmsManager.getDefault();
            if (location.length() > 70) {
                LogUtil.i(TAG , "location.length() > 70.");
                ArrayList<String> locations = manager.divideMessage(location);
                ArrayList<PendingIntent> sentIntents =  new ArrayList<PendingIntent>();
                for(int i = 0;i<locations.size();i++){
                    sentIntents.add(locationPi);
                }
                manager.sendMultipartTextMessage(phoneNumber, null, locations, sentIntents, null);
            } else {
                LogUtil.i(TAG , "location.length() <= 70.");
                manager.sendTextMessage(phoneNumber, null, location, locationPi, null);
            }
            LogUtil.i(TAG , "sms send done.");
        }

    public static void setScreenOff(Context context){
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm.isScreenOn()) {
            LogUtil.i(TAG, "setScreenOff, screen is setting off");
            pm.goToSleep(SystemClock.uptimeMillis());
        }
    }

}
