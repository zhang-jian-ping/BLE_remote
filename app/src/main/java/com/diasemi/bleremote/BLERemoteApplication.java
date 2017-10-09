package com.diasemi.bleremote;

import android.app.Application;

import com.diasemi.bleremote.ui.start.ScanItem;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.Iconics;

/**
 * Global application
 * Created by techwolf12 on 12/6/16.
 */

public class BLERemoteApplication extends Application {
    public ScanItem scanItem;
    public String version;

    @Override
    public void onCreate() {
        Iconics.registerFont(new GoogleMaterial());
        version = "Unknown";
        super.onCreate();
    }

    public void resetToDefaults() {
        scanItem = null;
        version = "Unknown";
    }
}
