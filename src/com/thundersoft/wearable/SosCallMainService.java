package com.thundersoft.wearable;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.incallui.ScreenFlagService;
import com.thundersoft.wearable.dial.DialHandle;
import com.thundersoft.wearable.sos.presenter.LogUtil;
import com.thundersoft.wearable.sos.presenter.SosCallPresenter;
import com.thundersoft.wearable.sos.presenter.SosCallUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SosCallMainService extends Service{

    private static final String LOG_TAG = SosCallMainService.class.getSimpleName();

    private Context mContext;
    private DialHandle mDialHandle ;
    private List<String> emergencyListNumber;
    private static final int SEND_LOCATION_WITH_SMS = 0;
    private static final int CALL_EMERGENCY_FAMILY = 1;
    private static final int CALL_DELAY = 3000;
    private static final long VIBRATE_TIME = 500;

    private static final String CALL_STATE_CHANGED_ACTION = "android.intent.action.thundersoft.newState";
    private static final String SOS_ENDED_ACTION = "android.intent.action.thundersof.sosEnded";

    private SmsSendResultReceiver mSmsReceiver;
    private List<Map<String, Object>> mFailList; //the failed sender list

    private final Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CALL_EMERGENCY_FAMILY:
                    LogUtil.i(LOG_TAG, "Get contacts done.. ready to dial.");
                    mDialHandle.startDial();
                    break;
                case SEND_LOCATION_WITH_SMS:
                    mDialHandle.prepareSend();
                    break;
                default:
                    break;
            }
        }
    };

    private class SmsSendResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String phoneNum = intent.getStringExtra(DialHandle.KEY_PHONENUM);
            int type = intent.getIntExtra(DialHandle.KEY_SMS_TYPE, DialHandle.TYPE_GPS_SMS);
            Map<String, Object> map = new HashMap<String, Object>();
            map.put(DialHandle.KEY_PHONENUM, phoneNum);
            map.put(DialHandle.KEY_SMS_TYPE, type);
            // TODO Auto-generated method stub
            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    LogUtil.i(LOG_TAG, "Send Message to " + phoneNum + " success!");
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                case SmsManager.RESULT_ERROR_NULL_PDU:
                default:
                    LogUtil.i(LOG_TAG, "Send Message to " + phoneNum + " fail!");
                    mFailList.add(map);
                    break;
            }
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.i(LOG_TAG, "onCreate...");
        mContext = SosCallMainService.this;

        vibrate();
        SosCallUtils.setScreenOff(mContext);

        emergencyListNumber =  SosCallPresenter.getInstance().readEmergencyContactsListNumberFromDB(mContext);
        mDialHandle = DialHandle.getInstance(mContext);

        mDialHandle.init(emergencyListNumber);
        IntentFilter callIntentFilter = new IntentFilter(CALL_STATE_CHANGED_ACTION);
        mContext.registerReceiver(mDialHandle.mCallStateReceiver, callIntentFilter);

        mSmsReceiver = new SmsSendResultReceiver();
        IntentFilter smsResultFilter = new IntentFilter(
                DialHandle.SENT_SMS_ACTION);
        mContext.registerReceiver(mSmsReceiver, smsResultFilter);
        mFailList = new ArrayList<Map<String, Object>>();

        //To send a notice to Mobile App via cloud, which needs call network api.
        mDialHandle.getLocationInfoJsonAndSendToCloud();

        startSosMultiWork();
    }

    /**
     * do sos work, send sms, call emergency.
     */
    private void startSosMultiWork() {
        if (!SosCallUtils.isSimReady(mContext) || emergencyListNumber == null
                || emergencyListNumber.size() == 0) {
            LogUtil.i(LOG_TAG, "sim is not ready or emergencylist is null, just call emergency call.");
            mHandler.sendEmptyMessage(CALL_EMERGENCY_FAMILY);
        }else {
            mHandler.sendEmptyMessage(SEND_LOCATION_WITH_SMS);
            //delay 3 seconds for send location sms, it needs data.
            mHandler.sendEmptyMessageDelayed(CALL_EMERGENCY_FAMILY, CALL_DELAY);
        }
        ScreenFlagService.mIsSosStart = true;
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        ScreenFlagService.mIsSosStart = false;
        LogUtil.i(LOG_TAG, "onDestroy, mIsSosStart : " + ScreenFlagService.mIsSosStart);
        mContext.unregisterReceiver(mDialHandle.mCallStateReceiver);
        mContext.unregisterReceiver(mSmsReceiver);
        reSendFailedSms();
        //send broadcast to PhoneWindowManager to enable Power key.
        sendBroadcastEnablePowerkey();
    }

    private void vibrate(){
        Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(VIBRATE_TIME);
    }



    private void sendBroadcastEnablePowerkey(){
        Intent i = new Intent();
        i.setAction(SOS_ENDED_ACTION);
        LogUtil.i(LOG_TAG,"sos ending, send broadcast to PhoneWindowManager to enable Power key");
        mContext.sendBroadcastAsUser(i,UserHandle.ALL);
    }

    //reSend failed sms.
    private void reSendFailedSms() {
        // TODO Auto-generated method stub
        LogUtil.i(LOG_TAG, "reSendFailedSms, retry send sms and finish");
        if (mFailList != null && mFailList.size() > 0) {
            LogUtil.i(LOG_TAG, "there is failed sms");
            // reSend failed sms.
            for (int i = 0; i < mFailList.size(); i++) {
                Map<String, Object> map = new HashMap<String, Object>();
                map = mFailList.get(i);
                String phoneNum = (String) map.get(DialHandle.KEY_PHONENUM);
                int type = (Integer) map.get(DialHandle.KEY_SMS_TYPE);
                switch (type) {
                    case DialHandle.TYPE_GPS_SMS:
                        if (!TextUtils.isEmpty(mDialHandle.mGpsLocation)) {
                            LogUtil.i(LOG_TAG, "reSend sms for number " + phoneNum + " and type is gps");
                            SosCallUtils.sendPositon(mContext,phoneNum,null,mDialHandle.mGpsLocation);
                        }
                        break;
                    case DialHandle.TYPE_LBS_WIFI_SMS:
                        if (!TextUtils.isEmpty(mDialHandle.mLbsLocation)) {
                            LogUtil.i(LOG_TAG, "reSend sms for number " + phoneNum + " and type is lbs+wifi");
                            SosCallUtils.sendPositon(mContext,phoneNum,null,mDialHandle.mLbsLocation);
                        }
                        break;
                    default:
                        LogUtil.i(LOG_TAG, "reSend sms for number " + phoneNum + " but type is wrong, don't send.");
                        break;
                }
            }
        }
    }

}
