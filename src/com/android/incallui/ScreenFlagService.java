package com.android.incallui;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import com.thundersoft.wearable.sos.presenter.LogUtil;

public class ScreenFlagService extends Service{

    private static final String LOG_TAG = ScreenFlagService.class.getSimpleName();
    //true if sos starts. used for set screen off when sos call.
    public static boolean mIsSosStart = false;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        LogUtil. i ( LOG_TAG , "OnBind" );
        return new MyBinder();
    }

    private class MyBinder extends IScreenFlagService.Stub{
        @Override
        public boolean getScreenFlag () throws RemoteException{
            return mIsSosStart;
       }
   }

}
