package com.thundersoft.wearable.dial;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemProperties;

import com.android.internal.telephony.ITelephony;
import com.example.locationinfo.KWLocationUtils;
import com.tcl.dayanta.mylibrary.database.provider.SettingsProvider;
import com.tcl.dayanta.mylibrary.entity.SettingsDBEntity;
import com.tcl.dayanta.mylibrary.common.Constant;
import com.tcl.dayanta.mylibrary.NetWorkAccessApi;
import com.tcl.dayanta.mylibrary.networkaccessinterface.HttpCallBack;
import com.thundersoft.wearable.sos.presenter.LogUtil;
import com.thundersoft.wearable.sos.presenter.SosCallUtils;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DialHandle {
    private static final String LOG_TAG = DialHandle.class.getSimpleName();

    private static DialHandle sDialHandle;
    private KWLocationUtils mKWLocationUtils;
    private Handler mHandler;
    private Context mContext;
    public CallStateReceiver mCallStateReceiver;
    private NetWorkAccessApi mNetWorkAccessApi = null;

    private static final int DIAL_NEXT = 0;
    private static final int HANGUP_TIMEOUT = 1;
    //As same as GET_LBS_HASHMAP_ONLY_INCLUDE_MESSAGE in KWLocationUtils class.
    private static final int GET_LBS_MESSAGE = 3;
    //As same as GET_GPS_HASHMAP_ONLY_INCLUDE_MESSAGE in KWLocationUtils class.
    private static final int GET_GPS_MESSAGE = 6;
    //As same as GET_LOCATION_JSON_STRING in KWLocationUtils class.
    private static final int GET_JSON_STRING_LOCATION_INFO = 7;
    private static final int CHECK_CALL = 4;

    private static final String KEY_SOS_ALERT_NOTICE_KEY1 = "code";
/*Keep non-use for sos notify with location value
//    private static final String KEY_SOS_ALERT_GPS_WIFI_KEY2 = "value";
 */
    private static final String VALUE_SOS_ALERT_NOTICE = "104";
    private static final String HASHMAP_VALUE_LBS_MSG = "lbs_msg";
    private static final String HASHMAP_VALUE_GPS_MSG = "gps_msg";

    public String mGpsLocation;
    public String mLbsLocation;
    /*Keep non-use for sos notify with location value
    //public String mLocationInfoJson;
    */

    ////when alert, wait 10s.
    private static final int DELAY_ALERT_MILLIS = 10000;
    //when dial next call , we should wait the previous call activity finish ,so wait 2 seconds.
    private static final int DELAY_NEXT_DIAL = 2000;
    private static final int CHECK_CALL_DELAY = 6000;

    //emergency  members
    public int familyMembers = 0;

    //indicate current times that have dialed to emergency members.
    public int mDialTimes;
    //emergency members' numbers
    private List<String> mEmergencyListNumber;
    //telephony state
    public static final int IDLE = 0;
    public static final int ACTIVE = 1;
    public static final int HOLDING = 2;
    public static final int DIALING = 3;
    public static final int ALERTING = 4;
    public static final int INCOMING = 5;
    public static final int WAITING = 6;
    public static final int DISCONNECTED = 7;
    public static final int DISCONNECTING = 8;
    public static final int INVALID = 9;
    //current call state.
    public int mCallState = INVALID;
    //previous call state.
    private int mPreviousState = INVALID;

    public static final String SENT_SMS_ACTION = "SENT_SMS_ACTION";
    public static final String KEY_PHONENUM = "phoneNumber";
    public static final String KEY_SMS_TYPE = "type";
    //sms type to send.
    public static final int TYPE_GPS_SMS = 0;
    public static final int TYPE_LBS_WIFI_SMS = 1;

    private static final String SOS_MAIN_SERVICE_NAME = "com.thundersoft.wearable.SosCallMainService";
    private static final String INCALL_PACKAGE_NAME = "com.android.server.telecom";
    private static final String INCALL_ACTIVITY_NAME = "com.android.server.telecom.components.UserCallActivity";

    //check call wether launched.
    private boolean mIsStateChanged = false;
    private boolean mIsDialingEcc;
    private String mDialingNumber;

    public DialHandle(Context context){
        mContext = context;
    }

    /**
     *
     *   Handler for dial all family members' number, and deal alerting time out for dialing.
     *
     */
    public class MyHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            switch (msg.what) {
            case DIAL_NEXT:
                // after DELAY_NEXT_DIAL ,start the call activity.
                LogUtil.i(LOG_TAG, "handleMessage DIAL_NEXT , the previous mDialTimes is: "
                                + mDialTimes + "and dial next");
                mDialTimes++;
                dialEmergencyList(mDialTimes);
                break;
            case HANGUP_TIMEOUT:
                // after DELAY_DIAL_MILLIS,the state still is ALERTING, so end call.
                if (mCallState == ALERTING) {
                    LogUtil.i(LOG_TAG, "handleMessage HANGUP_TIMEOUT ,time out, end call.");
                    endCall();
                }
                break;
            case GET_LBS_MESSAGE:
               HashMap<String, String>  hashmapLbs = new HashMap<String, String>();
               hashmapLbs = (HashMap<String, String>) msg.obj;
                mLbsLocation = (String) hashmapLbs.get(HASHMAP_VALUE_LBS_MSG);
                LogUtil.i(LOG_TAG, "[DJP]GET_LBS_MESSAGE ... hashmap:  "
                        + hashmapLbs + ";mLbsLocation = " + mLbsLocation) ;
                sendSMS(false);
                break;
            case GET_GPS_MESSAGE:
                HashMap<String, String>  hashmapGps = new HashMap<String, String>();
                hashmapGps = (HashMap<String, String>) msg.obj;
                mGpsLocation = (String) hashmapGps.get(HASHMAP_VALUE_GPS_MSG);
                LogUtil.i(LOG_TAG, "[DJP]GET_GPS_MESSAGE ... hashmap:  "
                        + hashmapGps + ";mLbsLocation = " + mGpsLocation) ;
                sendSMS(true);
                break;
            case CHECK_CALL:
                checkCallLaunched();
                break;
/*Keep non-use for sos notify with location value
//            case GET_JSON_STRING_LOCATION_INFO:
//                mLocationInfoJson = (String) msg.obj;
//                String deviceId = getDeviceIdForSosAlert();
//                LogUtil.i(LOG_TAG, "GET_JSON_STRING_LOCATION_INFO ... mLocationInfoJson:  "
//                                + mLocationInfoJson + " ;deviceId = " + deviceId);
//                requestToSendSosAlertToCloud(deviceId, mLocationInfoJson);
//                break;
 */
            default:
                break;
            }
        }
    }

    /**
     *
     * receiver for deal with the phone behavior when call state changed.
     *
     */
    public class CallStateReceiver  extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
                mIsStateChanged = true;
                Bundle b = intent.getExtras();
                mCallState =  b.getInt("newState");
                LogUtil.i(LOG_TAG,"CallStateReceiver onReceive ,state changed , reiceive broadcast, mCallState is " + stateToString(mCallState));
                if ( mDialTimes < familyMembers) {//emregency number call
                    if (mCallState!=mPreviousState) {
                        LogUtil.i(LOG_TAG,"CallStateReceiver onReceive ,state changed and previous state is different from new state");
                        LogUtil.i(LOG_TAG,"CallStateReceiver onReceive ,newState : "+ stateToString(mCallState));
                        LogUtil.i(LOG_TAG,"CallStateReceiver onReceive ,previousState : "+ stateToString(mPreviousState));
                        handleCallStateChanged();
                    }
                }else {// ecc call.
                    if (mCallState == DISCONNECTED || mCallState == IDLE) {
                        LogUtil.i(LOG_TAG,"CallStateReceiver onReceive , ecc call disconnected,stop Sos Service");
                        stopSosService();
                    }
                }
        }
    }

    /**
     *
     * @param context
     * @param emergencyListNumber, all emergency members' numbers.
     * @return DialHandle instance.
     */
    public static synchronized DialHandle getInstance(Context context) {
        if (sDialHandle == null) {
            sDialHandle = new DialHandle(context);
        }
        return sDialHandle;
    }

/**
 * init dial times emergency number and family members.
 */
    public void init(List<String> emergencyListNumber){
        if (mCallStateReceiver == null) {
            LogUtil.i(LOG_TAG," mCallStateReceiver is null, create instance. ");
            mCallStateReceiver = new CallStateReceiver();
        }
        if (mHandler == null) {
            LogUtil.i(LOG_TAG," mHandler is null, create instance. ");
            mHandler = new MyHandler();
        }
        if (mKWLocationUtils == null) {
            LogUtil.i(LOG_TAG," mKWLocationUtils is null, create instance. ");
            mKWLocationUtils = new KWLocationUtils(mContext);
        }
        mEmergencyListNumber = emergencyListNumber;
        if (mEmergencyListNumber != null) {
            familyMembers = mEmergencyListNumber.size();
        }
        LogUtil.i(LOG_TAG," family members has : " + familyMembers);
        mDialTimes = 0;
    }

/**
 *  start the sos dial .
 */
    public void startDial(){
        if (SosCallUtils.isSimReady(mContext)) {
            cleanHandlerPool();
            dialEmergencyList(0);
        }else{
            //force set mDialTimes to family members( normally 4), so CallStateReceiver can just work for ecc.
            mDialTimes = familyMembers;
            dialEccNumber();
        }
    }

  /**
   * dial next sos call.
   */
    private void dialNext(){
        Message msgDialListen = Message.obtain();
        msgDialListen.what = DIAL_NEXT;
        LogUtil.i(LOG_TAG,"dialNext ,dial next");
        cleanHandlerPool();
        mHandler.sendMessageDelayed(msgDialListen, DELAY_NEXT_DIAL);
    }

    /**
     * send Message to Handler
     */
    public void prepareSend(){
        //send GET_LBS_MESSAGE to get LBS mesage.
        mKWLocationUtils.getMessage(mHandler, GET_LBS_MESSAGE);
       //send GET_GPS_MESSAGE to get GPS mesage.
        mKWLocationUtils.getMessage(mHandler, GET_GPS_MESSAGE);
    }

    /**
     * Due to notify cloud, so acquire the location info firstly.
     */
    public void getLocationInfoJsonAndSendToCloud(){
        LogUtil.i(LOG_TAG,"getLocationInfoJsonAndSendToCloud ... ");
/*Keep non-use for sos notify with location value
//        mKWLocationUtils.getMessage(mHandler, GET_JSON_STRING_LOCATION_INFO);
 */
        String deviceId = getDeviceIdForSosAlert();
        requestToSendSosAlertToCloud(deviceId);
    }

    /**
     * send SMS.
     */
    private void sendSMS(boolean isGps){
        LogUtil.i(LOG_TAG,"sendSMS, is sending GPS Location : " + isGps);
        int type;
        String location;
        PendingIntent locationPi;
        //if gps, we need the location info is not null
        if (isGps && !TextUtils.isEmpty(mGpsLocation)) {
            LogUtil.i(LOG_TAG, "GPS location sending");
            type = TYPE_GPS_SMS;
            location = mGpsLocation;
        }else if (!isGps) {
            LogUtil.i(LOG_TAG, "LBS location sending");
            type = TYPE_LBS_WIFI_SMS;
            location = mLbsLocation;
        }else{
            LogUtil.i(LOG_TAG,"gps location should be sent, but gps location is null, so abort sending. ");
            return;
        }
        LogUtil.i(LOG_TAG,"sendSMS ,send text is :  " + location);
        for (int i = 0; i < familyMembers; i++) {
            LogUtil.i(LOG_TAG,"sendSMS ,mEmergencyListNumber  " + i + " is :" + mEmergencyListNumber.get(i));
            if (TextUtils.isEmpty(mEmergencyListNumber.get(i))){
                LogUtil.i(LOG_TAG,"sendSMS ,sending SMS, but mEmergencyListNumber  " + i + " is empty");
                continue;
            }
            Intent itSend = new Intent(SENT_SMS_ACTION);
            itSend.putExtra(KEY_PHONENUM,mEmergencyListNumber.get(i));
            itSend.putExtra(KEY_SMS_TYPE, type);
            locationPi = PendingIntent.getBroadcast(mContext, i, itSend, PendingIntent.FLAG_ONE_SHOT);
            SosCallUtils.sendPositon(mContext, mEmergencyListNumber.get(i),locationPi,location);
        }
        LogUtil.i(LOG_TAG,"sendSMS ,send SMS done. ");
    }

    /**
     * dial emergency members' number
     * @param dialTimes, current times that for dialing to emergency members.
     */
    private void dialEmergencyList(int dialTimes){
        mDialTimes = dialTimes;
        LogUtil.i(LOG_TAG,"dialEmergencyList , current mDialTimes is  : "+ mDialTimes);
        if (mDialTimes < familyMembers){
            LogUtil.i(LOG_TAG,"dialEmergencyList ,mEmergencyListNumber  " +
                mDialTimes + " is :" + mEmergencyListNumber.get(mDialTimes));
            //if current emergency number is null or network is not available, just deal with next number
            if (TextUtils.isEmpty(mEmergencyListNumber.get(mDialTimes)) || !SosCallUtils.isNetworkAvailable(mContext)) {
                LogUtil.i(LOG_TAG,"dialEmergencyList ,number is null or network not available, and dial next");
                dialNext();
                return;
            }
            startCallActivity(mEmergencyListNumber.get(mDialTimes),false);
        }else{
            //EmergencyList no answer, so dial ecc number.
            LogUtil.i(LOG_TAG,"dialEmergencyList ,EmergencyList no answer, so dial ecc number" );
            dialEccNumber();
        }
    }

    /**
     * dial ecclist number
     */
    private void dialEccNumber() {
        StringBuffer eccNumbers = new StringBuffer();
        //read sos database
        String customNumbers = getSplitSos();
        LogUtil.i(LOG_TAG, "dialEccNumber ,customed ecclist is : " + customNumbers);
        //read ecclist
        String emergencyNumbers = SystemProperties.get("ril.ecclist", "");
        if (TextUtils.isEmpty(emergencyNumbers)) {
            // then read-only ecclist property since old RIL only uses this
            emergencyNumbers = SystemProperties.get("ro.ril.ecclist");
        }
        LogUtil.i(LOG_TAG,"dialEccNumber ,emergencyNumbers is : " + emergencyNumbers);

        //make new ecclist numbers.
        if (TextUtils.isEmpty(customNumbers)) {
            LogUtil.i(LOG_TAG, "dialEccNumber, customed ecclist is null");
            eccNumbers.append(emergencyNumbers);
        }else{
            eccNumbers.append(customNumbers);
            if (!TextUtils.isEmpty(emergencyNumbers)) {
                eccNumbers.append(",").append(emergencyNumbers);
            }
        }
        LogUtil.i(LOG_TAG,"dialEccNumber ,real ecclist  is : " + eccNumbers.toString());
        if (!TextUtils.isEmpty(eccNumbers)) {
            String [] eccNumbersSplit = eccNumbers.toString().split(",");
            String eccNumber = eccNumbersSplit[0];
            LogUtil.i(LOG_TAG,"dialEccNumber ,eccNumber  is : " + eccNumber);
            startCallActivity(eccNumber, true);
        }else{
            LogUtil.i(LOG_TAG,"dialEccNumber, the ecc number is empty, finish sos.");
            stopSosService();
            return;
        }
    }

    //split json string from cloud, like : “sos”: [“110”, “120”, “119”]
    private String getSplitSos() {
        SettingsDBEntity settingsDBEntity = new SettingsDBEntity();
        settingsDBEntity = SettingsProvider.getInstance(mContext).getSOS();
        if (settingsDBEntity == null) {
            LogUtil.i(LOG_TAG, "there is no sos info in databasel");
            return "";
        }
        String sos = settingsDBEntity.getSos();
        if (!TextUtils.isEmpty(sos)) {
            LogUtil.i(LOG_TAG, "sos is : " + sos);
            //split sos
            StringBuffer sosBuffer = new StringBuffer();
            String regex = "\\[|\\]|,|\"| ";
            String [] sosSplit = sos.split(regex); //split to   110 120 119
            LogUtil.i(LOG_TAG, "sosSplit is : " + sosSplit);
            for (String split : sosSplit) {
                LogUtil.i(LOG_TAG, "split is : " + split);
                if (!TextUtils.isEmpty(split)) {
                    sosBuffer.append(split).append(","); // 110,120,119,
                }
            }
            String sosLast ;
            if (sosBuffer.length() > 0) {
                sosLast = sosBuffer.substring(0, sosBuffer.length()-1).toString(); // remove the last char ","
            }else{
                sosLast = "";
            }
            LogUtil.i(LOG_TAG, "ecclist is : " + sosLast); //110,120,119
            return sosLast;
        }else{
            LogUtil.i(LOG_TAG, "sos is null");
            return "";
        }
    }

    /**
     *
     * @param context
     * @return telephony api interface.
     */
    private ITelephony getITelephony(Context context) {
        TelephonyManager mTelephonyManager = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        ITelephony iTelephony = null;
        Class c = TelephonyManager.class;
        Method getITelephonyMethod = null;
        try {
                getITelephonyMethod = c.getDeclaredMethod("getITelephony",
                        (Class[]) null);
                getITelephonyMethod.setAccessible(true);
        } catch (SecurityException e) {
                e.printStackTrace();
        } catch (NoSuchMethodException e) {
                e.printStackTrace();
        }
        try {
                iTelephony = (ITelephony) getITelephonyMethod.invoke(
                mTelephonyManager, (Object[]) null); // 获取实例
                return iTelephony;
        } catch (Exception e) {
                e.printStackTrace();
        }
        return iTelephony;
    }

    /**
     * end the call.
     */
    private void endCall(){
        ITelephony iTelephony = getITelephony(mContext.getApplicationContext());
        if (iTelephony != null) {
            try {
                    LogUtil.i(LOG_TAG,"endCall ,iTelephony.endCall");
                    iTelephony.endCall();
            } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
            }
        }
    }

    /**
     * handle the call state change.
     */
    private void handleCallStateChanged(){
        //state from DIALING/ALERTING to !ALERTING/!ACTIVE, the call is  hanged up, so dial nex
        if ((mCallState != ALERTING && mPreviousState == DIALING) || (mCallState != ACTIVE && mPreviousState == ALERTING)) {
            LogUtil.i(LOG_TAG,"handleCallStateChanged ,state from ALERTING to !ACTIVE, the call is  hanged up, so dial next");
            dialNext();
            mPreviousState = mCallState;
            return ;
        }
        //alerting, so start calculate DELAY_ALERT_MILLIS.
        if (mCallState == DIALING) {
            LogUtil.i(LOG_TAG,"handleCallStateChanged ,mCallState is DIALING ");
        }
        //alerting, so start calculate DELAY_ALERT_MILLIS.
        if (mCallState == ALERTING) {
            Message msgAlertListen = Message.obtain();
            msgAlertListen.what = HANGUP_TIMEOUT;
            LogUtil.i(LOG_TAG,"handleCallStateChanged ,mCallState is ALERTING , waiting 10s");
            mHandler.sendMessageDelayed(msgAlertListen, DELAY_ALERT_MILLIS);
        }
        //state from active to disconnected, or state is active , then end the whole call process.
        if ( mCallState == ACTIVE) {
            LogUtil.i(LOG_TAG,"handleCallStateChanged ,mCallState is ACTIVE ");
            mPreviousState = mCallState;
            cleanHandlerPool();
        }
        //state from active to disconneted.
        if ((mCallState == DISCONNECTING || mCallState == DISCONNECTED) && mPreviousState == ACTIVE) {
            LogUtil.i(LOG_TAG,"handleCallStateChanged ,emergency call disconnect from active state , stop Sos Service");
            stopSosService();
        }
        //record current state.
        mPreviousState = mCallState;
    }

    private String stateToString(int state){
        String stateString = null;
        switch (state) {
            case IDLE:
                stateString = "IDLE";
             break;
            case ACTIVE:
                stateString = "ACTIVE";
                break;
            case HOLDING:
                stateString = "HOLDING";
                break;
            case DIALING:
                stateString = "DIALING";
                break;
            case ALERTING:
                stateString = "ALERTING";
                break;
            case INCOMING:
                stateString = "INCOMING";
                break;
            case WAITING:
                stateString = "WAITING";
                break;
            case DISCONNECTED:
                stateString = "DISCONNECTED";
                break;
            case DISCONNECTING:
                stateString = "DISCONNECTING";
                break;
            default:
                break;
        }
        return stateString;
    }

    /**
     * remove the handler Message.
     */
    private void cleanHandlerPool () {
        if (mHandler.hasMessages(HANGUP_TIMEOUT)) {
            LogUtil.i(LOG_TAG,"cleanHandlerPool ,remove  message HANGUP_TIMEOUT");
            mHandler.removeMessages(HANGUP_TIMEOUT);
        }
        if (mHandler.hasMessages(DIAL_NEXT)) {
            LogUtil.i(LOG_TAG,"cleanHandlerPool ,remove  message DIAL_NEXT");
            mHandler.removeMessages(DIAL_NEXT);
        }
        if (mHandler.hasMessages(CHECK_CALL)) {
            LogUtil.i(LOG_TAG,"cleanHandlerPool ,remove  message CHECK_CALL");
            mHandler.removeMessages(CHECK_CALL);
        }
    }

    private void stopSosService(){
        Intent intent = new Intent();
        ComponentName component = new ComponentName(mContext.getPackageName() ,SOS_MAIN_SERVICE_NAME);
        if(component != null){
            intent.setComponent(component);
            mContext.stopService(intent);
        }else{
            LogUtil.e(LOG_TAG,"the component is null, can't stop the service");
        }
    }

    /**
     *
     * @param number, the number dial.
     * @param isEcc.  emergency call if true, emergency members call else.
     */
    private void startCallActivity(String number , boolean isEcc){
        mIsDialingEcc = isEcc;
        mDialingNumber = number;
        //complete intent for dial
        Intent intent = new Intent();
        //intent.setAction(Intent.ACTION_CALL);
        intent.putExtra("tel:", number);
        Uri data = Uri.parse("tel:" + number);
        intent.setData(data);
        if (isEcc) {
            intent.setAction(Intent.ACTION_CALL_EMERGENCY);
            intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK);
            LogUtil.i(LOG_TAG," start emergency  incall activity");
            mContext.startActivity(intent);
        }else{
            ComponentName component = new ComponentName(INCALL_PACKAGE_NAME ,INCALL_ACTIVITY_NAME);
            if(component != null){
                intent.setComponent(component);
                intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK);
                LogUtil.i(LOG_TAG," start emergency member incall activity");
                mContext.startActivity(intent);
            }else{
                LogUtil.i(LOG_TAG,"the component is null, can't start current incall activity, dial next.");
                dialNext();
                return;
            }
        }
        mIsStateChanged = false;
        Message msgDialListen = Message.obtain();
        msgDialListen.what = CHECK_CALL;
        mHandler.sendMessageDelayed(msgDialListen, CHECK_CALL_DELAY);
    }

/**
 * if call is not launched, redial.
 */
    private void checkCallLaunched() {
        //call is not launched
        if ((mPreviousState == mCallState) && !mIsStateChanged) {
            LogUtil.i(LOG_TAG,"checkCallLaunched, call is not launched, redialing");
            startCallActivity(mDialingNumber,mIsDialingEcc);
        }
    }

    private String getDeviceIdForSosAlert() {
        String deviceId = "";
        TelephonyManager telephonyManager = (TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE);
        deviceId = telephonyManager.getDeviceId();
        LogUtil.i(LOG_TAG, "getDeviceIdForSosAlert  deviceId = " + deviceId);
        return deviceId;
    }

    private void requestToSendSosAlertToCloud(String deviceId) {
        if (!deviceId.isEmpty()) {
            String alert_URL = Constant.BASEURL + Constant.VERSION + "/device/"
                    + deviceId + "/notify";
            Map<String, String> alertTypeMap = new HashMap<String, String>();
            alertTypeMap.put(KEY_SOS_ALERT_NOTICE_KEY1, VALUE_SOS_ALERT_NOTICE);
/*Keep non-use for sos notify with location value
//            alertTypeMap.put(KEY_SOS_ALERT_GPS_WIFI_KEY2, locationInfo);
 */
            LogUtil.i(LOG_TAG, "requestToSendSosAlertToCloud  alert_URL = " + alert_URL);
            mNetWorkAccessApi = NetWorkAccessApi.getInstance(mContext);
            mNetWorkAccessApi.uploadStatus(alert_URL, alertTypeMap, null,
                    new HttpCallBack() {
                        @Override
                        public void onSuccess(Object object) {
                            LogUtil.i(LOG_TAG, "requestToSendSosAlertToCloud..onSuccess.");
                        }

                        @Override
                        public void onError() {
                            LogUtil.i(LOG_TAG, "requestToSendSosAlertToCloud..onError.");
                        }
                    });
        }
    }

}



