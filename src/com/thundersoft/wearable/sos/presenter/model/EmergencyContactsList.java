package com.thundersoft.wearable.sos.presenter.model;

import java.util.ArrayList;
import java.util.List;

import com.thundersoft.wearable.sos.presenter.LogUtil;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;

public class EmergencyContactsList {

    private static final String TAG = EmergencyContactsList.class.getSimpleName();
    private Context mContext;

    private static final int EMERGENCY_FLAG = 0x100;

    public EmergencyContactsList(Context context) {
        this.mContext = context;
    }

    public List<String> readEmergencyContactsListNumberFromDB(){
        String rawContactId = null;
        List<String> emergencyListNumber = new ArrayList<String>();
        Cursor c = mContext.getContentResolver()
                .query(ContactsContract.Data.CONTENT_URI,new String[] {ContactsContract.Data.RAW_CONTACT_ID },
                ContactsContract.Data.MIMETYPE +"='"+Im. CONTENT_ITEM_TYPE+"' AND  data2='"+
                Integer.valueOf(EMERGENCY_FLAG).toString()+"'",null, null);
        try {
             while (c.moveToNext()) {
                 rawContactId = c.getString(c.getColumnIndex(Data.RAW_CONTACT_ID));
                 Cursor cur = mContext.getContentResolver().query(ContactsContract.Data.CONTENT_URI, new String[]{Phone.NUMBER},
                         ContactsContract.Data.RAW_CONTACT_ID + "='"+rawContactId+"' AND "+ContactsContract.Data.MIMETYPE +"='"+
                         Phone.CONTENT_ITEM_TYPE+"'", null, null);
                try {
                    while (cur.moveToNext()) {
                         String currentEmergenyNum = cur.getString(cur.getColumnIndex(Phone.NUMBER));
                         if (!TextUtils.isEmpty(currentEmergenyNum)) {
                             emergencyListNumber.add(currentEmergenyNum);
                         }
                     }
                } catch(Exception e){
                    LogUtil.e(TAG,"get number error.");
                }finally{
                    if (cur != null) {
                        cur.close();
                    }
                }
             }
        } catch(Exception e){
            LogUtil.e(TAG,"get raw contact id error.");
        }finally {
            if (c != null) {
                c.close();
            }
        }
        LogUtil.i(TAG,"emergencyListNumber is : " + emergencyListNumber);
        return emergencyListNumber;
    }

}
