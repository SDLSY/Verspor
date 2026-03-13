package com.iflytek.aiui.demo.chat.vms;


public enum VideoStreamType implements Const {
    VIDEO_STREAM_TYPE_BIG(0),
    VIDEO_STREAM_TYPE_SMALL(1),
    VIDEO_STREAM_TYPE_SUB(2);

    private final int value;

    VideoStreamType(int var3) {
        this.value = var3;
    }

    public int getValue() {
        return this.value;
    }
}
