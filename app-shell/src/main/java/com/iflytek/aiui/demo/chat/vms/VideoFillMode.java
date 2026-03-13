package com.iflytek.aiui.demo.chat.vms;

public enum VideoFillMode implements Const {
    VIDEO_RENDER_MODE_FILL(0),
    VIDEO_RENDER_MODE_FIT(1);

    private final int value;

    VideoFillMode(int var3) {
        this.value = var3;
    }

    public int getValue() {
        return this.value;
    }
}