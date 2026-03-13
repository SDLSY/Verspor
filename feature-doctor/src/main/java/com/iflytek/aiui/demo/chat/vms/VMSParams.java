package com.iflytek.aiui.demo.chat.vms;


import android.view.ViewGroup;
import com.iflytek.xrtcsdk.conference.ui.IXCloudVideoView;
import java.io.Serializable;

public class VMSParams implements Serializable {
    protected VideoRotation rotation = null;
    protected VideoFillMode fillMode = null;
    protected VideoMirrorType mirrorType = null;
    protected VideoStreamType streamType = null;
    protected VolumeType volumeType = null;
    protected ViewGroup vmsContainer = null;
    protected IXCloudVideoView customVideoView = null;

    public static Builder builder() {
        return new Builder();
    }

    public VMSParams(Builder var1) {
        this.rotation = var1.rotation;
        this.fillMode = var1.fillMode;
        this.mirrorType = var1.mirrorType;
        this.streamType = var1.streamType;
        this.volumeType = var1.volumeType;
        this.vmsContainer = var1.vmsContainer;
        this.customVideoView = var1.customVideoView;
    }

    public ViewGroup getContainer() {
        return this.vmsContainer;
    }

    public static class Builder {
        private VideoRotation rotation = null;
        private VideoFillMode fillMode = null;
        private VideoMirrorType mirrorType = null;
        private VideoStreamType streamType = null;
        private VolumeType volumeType = null;
        private ViewGroup vmsContainer = null;
        private IXCloudVideoView customVideoView = null;

        public Builder() {
        }

        private Builder response() {
            return this;
        }

        public VMSParams build() {
            return new VMSParams(this);
        }

        public Builder videoStreamType(VideoStreamType var1) {
            this.streamType = var1;
            return this.response();
        }

        public Builder videoRotation(VideoRotation var1) {
            this.rotation = var1;
            return this.response();
        }

        public Builder videoFillMode(VideoFillMode var1) {
            this.fillMode = var1;
            return this.response();
        }

        public Builder videoMirrorType(VideoMirrorType var1) {
            this.mirrorType = var1;
            return this.response();
        }

        public Builder volumeType(VolumeType var1) {
            this.volumeType = var1;
            return this.response();
        }

        public Builder vmsContainer(ViewGroup var1) {
            this.vmsContainer = var1;
            return this.response();
        }

        public Builder customVideoView(IXCloudVideoView var1) {
            this.customVideoView = var1;
            return this.response();
        }
    }
}
