package com.thundersoft.wearable.sos.presenter;

import android.content.Context;
import java.util.List;

public class SosCallPresenter {

    private static final String TAG = SosCallPresenter.class.getSimpleName();

    private static SosCallPresenter sSosCallPresenter;

    public static synchronized SosCallPresenter getInstance() {
        if (sSosCallPresenter == null) {
            sSosCallPresenter = new SosCallPresenter();
        }
        return sSosCallPresenter;
    }

    public List<String>  readEmergencyContactsListNumberFromDB(Context context){
        LogUtil.i(TAG, "read emergency members' number");
        return SosCallUtils.readEmergencyContactsListNumberFromDB(context);
    }

}
