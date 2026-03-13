package com.iflytek.aiui.demo.chat.vms;

public enum VideoMirrorType implements Const {
    VIDEO_MIRROR_TYPE_AUTO(0),
    VIDEO_MIRROR_TYPE_ENABLE(1),
    VIDEO_MIRROR_TYPE_DISABLE(2);

    private final int value;

    VideoMirrorType(int var3) {
        this.value = var3;
    }

    public int getValue() {
        return this.value;
    }
}