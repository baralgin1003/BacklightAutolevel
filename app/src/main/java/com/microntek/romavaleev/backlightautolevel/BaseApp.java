package com.microntek.romavaleev.backlightautolevel;

import android.app.Application;

/**
 * Created by Amiga-pc on 18.08.2018.
 */


public class BaseApp extends Application {
    public static BaseApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
}
