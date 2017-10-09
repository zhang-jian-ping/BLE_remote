package com.diasemi.bleremote.ui.start;

/**
 * ScanItem object
 * Created by techwolf12 on 3/1/16.
 */
public class ScanItem {
    public int scanIcon;
    public String scanName;
    public String scanDescription;
    public String btAddress;
    public String btName;
    public int scanSignal;
    public boolean paired;

    public ScanItem(int scanIcon, String scanName, String scanDescription, int scanSignal, String btAddress, String btName, boolean paired) {
        this.scanIcon = scanIcon;
        this.scanName = scanName;
        this.scanDescription = scanDescription;
        this.scanSignal = scanSignal;
        this.btAddress = btAddress;
        this.btName = btName;
        this.paired = paired;
    }
}