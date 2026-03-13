package com.iflytek.aiui.demo.chat.vms;


public enum VideoRotation implements Const {
    VIDEO_ROTATION_0(0),
    VIDEO_ROTATION_90(1),
    VIDEO_ROTATION_180(2),
    VIDEO_ROTATION_270(3);

    private final int value;

    VideoRotation(int var3) {
        this.value = var3;
    }

    public int getValue() {
        return this.value;
    }
}
