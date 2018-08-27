package com.microntek.romavaleev.backlightautolevel;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

public class ScreenOnOffService extends Service {
    private BootReceiver mScreenReceiver;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        registerScreenStatusReceiver();
    }

    @Override
    public void onDestroy() {
        unregisterScreenStatusReceiver();
    }

    private void registerScreenStatusReceiver() {
        mScreenReceiver = new BootReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        registerReceiver(mScreenReceiver, filter);
    }

    private void unregisterScreenStatusReceiver() {
        try {
            if (mScreenReceiver != null) {
                unregisterReceiver(mScreenReceiver);
            }
        } catch (IllegalArgumentException e) {}
    }
}