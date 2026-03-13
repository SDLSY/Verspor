package com.iflytek.aiui.demo.chat.vms;

public enum VolumeType implements Const {
    SystemVolumeTypeAuto(0),
    SystemVolumeTypeMedia(1),
    SystemVolumeTypeVOIP(2);

    private final int value;

    VolumeType(int var3) {
        this.value = var3;
    }

    public int getValue() {
        return this.value;
    }
}
