package com.diasemi.bleremote.ui.main.remotecontrol;

public class KeyPressEvent {

    private byte[] mPacket;

    public KeyPressEvent(final byte[] packet) {
        mPacket = packet;
    }

    public byte[] getPacket() {
        return mPacket;
    }
}
