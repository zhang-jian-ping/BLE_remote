package com.diasemi.bleremote.ui.main.input;

public class StreamControlEvent {

    private byte[] mPacket;

    public StreamControlEvent(final byte[] packet) {
        mPacket = packet;
    }

    public byte[] getPacket() {
        return mPacket;
    }
}