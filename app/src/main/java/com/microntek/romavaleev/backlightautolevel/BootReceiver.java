package com.microntek.romavaleev.backlightautolevel;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent canserviceintent = new Intent(context, BacklightService.class);
        if (!isMyServiceRunning(context)) {
            canserviceintent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            //context.startService(canserviceintent);
            startService(context, canserviceintent);
        } else {
            context.stopService(canserviceintent);
            // context.startService(canserviceintent);
            startService(context, canserviceintent);
        }
    }

    private boolean isMyServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (BacklightService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT < 26) {
            context.startService(intent);
        } else {
            ContextCompat.startForegroundService(context, intent);
        }
    }
}
