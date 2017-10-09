package com.diasemi.bleremote.ui.main.logs;

public class ErrorEvent {

    private byte[] mPacket;

    public ErrorEvent(final byte[] packet) {
        mPacket = packet;
    }

    public byte[] getPacket() {
        return mPacket;
    }
}