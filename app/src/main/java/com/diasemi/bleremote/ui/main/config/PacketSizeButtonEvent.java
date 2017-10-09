package com.diasemi.bleremote.ui.main.config;

public class PacketSizeButtonEvent {

    private int max;
    private int fixed;

    public PacketSizeButtonEvent(int max, int fixed) {
        this.max = max;
        this.fixed = fixed;
    }

    public int getMax() {
        return max;
    }

    public int getFixed() {
        return fixed;
    }
}
