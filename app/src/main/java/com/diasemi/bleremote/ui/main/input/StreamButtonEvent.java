package com.diasemi.bleremote.ui.main.input;

public class StreamButtonEvent {

    private boolean mIsChecked;

    public StreamButtonEvent(final boolean isChecked) {
        mIsChecked = isChecked;
    }

    public boolean isChecked() {
        return mIsChecked;
    }
}