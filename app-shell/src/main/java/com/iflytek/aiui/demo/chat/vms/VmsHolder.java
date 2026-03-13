package com.iflytek.aiui.demo.chat.vms;


import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import com.iflytek.xrtcsdk.conference.IXRTCCloud;
import com.iflytek.xrtcsdk.conference.IXRTCCloudDef;
import com.iflytek.xrtcsdk.conference.IXRTCCloudListener;
import com.iflytek.xrtcsdk.conference.ui.IXCloudVideoView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VmsHolder {
    private final String TAG = "VmsHolder";
    private final String XRTC_DEFAULT_UID = "vmsUid_";
    private String xrtcAppId = "1000000001";
    private String xrtcUserSig;
    private boolean isActivate;
    private VideoRotation rotation;
    private VideoFillMode fillMode;
    private VideoMirrorType mirrorType;
    private VideoStreamType streamType;
    private VolumeType volumeType;
    protected ViewGroup vmsContainer;
    private WeakReference<IXCloudVideoView> customVideoView;
//    private String streamUrl;
    private String vmsUid;
    private WeakReference<Context> mContext;
    private IXRTCCloud mIXRTCCloud;
    private IXCloudVideoView cloudVideoView;
    private XrtcListener xrtcListener;
    private ConcurrentLinkedQueue<Integer> handleIDQueue;
    private final IXRTCCloudListener ixrtcCloudListener;

    private VmsHolder() {
        this.xrtcUserSig = "";
        this.isActivate = false;
        this.rotation = VideoRotation.VIDEO_ROTATION_0;
        this.fillMode = VideoFillMode.VIDEO_RENDER_MODE_FIT;
        this.mirrorType = VideoMirrorType.VIDEO_MIRROR_TYPE_DISABLE;
        this.streamType = VideoStreamType.VIDEO_STREAM_TYPE_BIG;
        this.volumeType = VolumeType.SystemVolumeTypeAuto;
        this.vmsContainer = null;
//        this.streamUrl = "";
        this.vmsUid = null;
        this.mContext = null;
        this.xrtcListener = null;
        this.handleIDQueue = new ConcurrentLinkedQueue<Integer>();
        this.ixrtcCloudListener = new IXRTCCloudListener() {
            @Override
            public void onEnterRoom(int var1) {
                Log.d("VmsHolder", "onEnterRoom:" + var1);
                if (VmsHolder.this.xrtcListener != null) {
                    VmsHolder.this.xrtcListener.onEnterRoom(var1);
                }
            }

            @Override
            public void onExitRoom(int reason) {
                super.onExitRoom(reason);
                Log.d("VmsHolder", "onExitRoom:" + reason);
                if (VmsHolder.this.xrtcListener != null) {
                    VmsHolder.this.xrtcListener.onExitRoom(reason);
                }
            }

            @Override
            public void onRemoteUserEnterRoom(String userId) {
                super.onRemoteUserEnterRoom(userId);
                if (VmsHolder.this.mContext == null || VmsHolder.this.mContext.get() == null) {
                    Log.e(TAG, "no valid context, should provide it");
                }

                if (VmsHolder.this.getCustomView() == null) {
                    VmsHolder.this.cloudVideoView = new IXCloudVideoView(VmsHolder.this.mContext.get());
                    VmsHolder.this.cloudVideoView.getSurfaceView().setZOrderMediaOverlay(false);
                    if ((VmsHolder.this.vmsContainer) != null) {
                        VmsHolder.this.vmsContainer.addView(VmsHolder.this.cloudVideoView);
                    }
                } else if ((VmsHolder.this).vmsContainer != null) {
                    (VmsHolder.this.vmsContainer).addView(VmsHolder.this.getCustomView());
                }
            }

            @Override
            public void onRemoteUserLeaveRoom(String userId, int reason) {
                super.onRemoteUserLeaveRoom(userId, reason);
                if ((VmsHolder.this).vmsContainer != null) {
                    if (VmsHolder.this.cloudVideoView != null) {
                        (VmsHolder.this).vmsContainer.removeView(VmsHolder.this.cloudVideoView);
                        VmsHolder.this.cloudVideoView = null;
                    }

                    if (VmsHolder.this.getCustomView() != null) {
                        VmsHolder.this.vmsContainer.removeView(VmsHolder.this.getCustomView());
                    }
                }

            }

            @Override
            public void onUserVideoAvailable(String userId, boolean available) {
                Log.d("VmsHolder", "onUserVideoAvailable:" + userId + ",available:" + available);
                if (VmsHolder.this.mIXRTCCloud == null) {
                    Log.d("VmsHolder", "onUserVideoAvailable: XRTC is empty");
                } else {
                    if (available) {
                        if (VmsHolder.this.getCustomView() != null) {
                            VmsHolder.this.mIXRTCCloud.startRemoteView(userId, 0, VmsHolder.this.getCustomView());
                        } else {
                            VmsHolder.this.mIXRTCCloud.startRemoteView(userId, 0, VmsHolder.this.cloudVideoView);
                        }

                        final IXRTCCloudDef.IXRTCRenderParams renderParams = new IXRTCCloudDef.IXRTCRenderParams();
                        renderParams.rotation = VmsHolder.this.rotation.getValue();
                        renderParams.fillMode = VmsHolder.this.fillMode.getValue();
                        renderParams.mirrorType = VmsHolder.this.mirrorType.getValue();
                        VmsHolder.this.mIXRTCCloud.setRemoteRenderParams(userId, VmsHolder.this.streamType.getValue(), renderParams);
                        VmsHolder.this.mIXRTCCloud.setSystemVolumeType(VmsHolder.this.volumeType.getValue());
                    } else {
                        VmsHolder.this.mIXRTCCloud.stopRemoteView(userId, 0);
                    }

                    if (VmsHolder.this.xrtcListener != null) {
                        VmsHolder.this.xrtcListener.onUserVideoAvailable(userId, available);
                    }

                }
            }

            @Override
            public void onError(int errCode, String errMsg, Bundle extraInfo) {
                super.onError(errCode, errMsg, extraInfo);
                Log.e("VmsHolder", "onError:" + errCode + ",errMsg:" + errMsg);
                if (VmsHolder.this.xrtcListener != null) {
                    VmsHolder.this.xrtcListener.onError(errCode, errMsg, extraInfo);
                }

            }

            @Override
            public void onFirstVideoFrame(String userId, int streamType, int width, int height) {
                Log.i("VmsHolder", "onFirstVideoFrame:" + streamType + ":" + width + "," + height);
                if (VmsHolder.this.xrtcListener != null) {
                    VmsHolder.this.xrtcListener.onFirstVideoFrame(userId, streamType, width, height);
                }

            }

            @Override
            public void onNetworkQuality(IXRTCCloudDef.IXRTCQuality localQuality, ArrayList<IXRTCCloudDef.IXRTCQuality> remoteQuality) {
                if (VmsHolder.this.xrtcListener != null) {
                    VmsHolder.this.xrtcListener.onNetworkQuality(localQuality, remoteQuality);
                }

            }

            @Override
            public void onConnectionLost() {
                Log.e("VmsHolder", "onConnectionLost");
                if (VmsHolder.this.xrtcListener != null) {
                    VmsHolder.this.xrtcListener.onConnectionLost();
                }

            }

            @Override
            public void onTryToReconnect() {
                Log.e("VmsHolder", "onTryToReconnect");
                if (VmsHolder.this.xrtcListener != null) {
                    VmsHolder.this.xrtcListener.onTryToReconnect();
                }

            }

            @Override
            public void onConnectionRecovery() {
                Log.d("VmsHolder", "onConnectionRecovery");
                if (VmsHolder.this.xrtcListener != null) {
                    VmsHolder.this.xrtcListener.onConnectionRecovery();
                }

            }
        };
    }

    public static VmsHolder getInst() {
        return VmsHolder.Holder.instance;
    }

    private void startStream(String streamUrl) {
        if (TextUtils.isEmpty(streamUrl)) {
            return;
        }

        if (this.mIXRTCCloud == null) {
            Log.d("VmsHolder", "startStream: create sharedInstance");
            this.mIXRTCCloud = IXRTCCloud.sharedInstance(this.mContext.get());
        }

        int lastIndexOfSlash = streamUrl.lastIndexOf("/");
        String streamHttpUrl;
        if (streamUrl.startsWith("xrtcs:")) {
            streamHttpUrl = streamUrl.substring(0, lastIndexOfSlash).replace("xrtcs:", "https:");
            this.startStream(streamHttpUrl, streamUrl.substring( lastIndexOfSlash + 1));
        } else {
            streamHttpUrl = streamUrl.substring(0, lastIndexOfSlash).replace("xrtc:", "http:");
            this.startStream(streamHttpUrl, streamUrl.substring( lastIndexOfSlash + 1));
        }
    }

    private IXCloudVideoView getCustomView() {
        return (this.customVideoView != null && this.customVideoView.get() != null) ? this.customVideoView.get() : null;
    }

    private void startStream(String url, String roomId) {
        boolean consoleEnable = true;

        Log.d("VmsHolder", "startStream XRTC.url:" + url + ", room:" + roomId + ", consoleEnabled:" + consoleEnable);
        this.mIXRTCCloud.setConsoleEnabled(consoleEnable);
        this.mIXRTCCloud.setListener(this.ixrtcCloudListener);
        IXRTCCloudDef.IXRTCParams ixrtcParams = new IXRTCCloudDef.IXRTCParams();
        ixrtcParams.userId = this.vmsUid;
        ixrtcParams.userSig = this.xrtcUserSig;
        ixrtcParams.strSdkAppId = this.xrtcAppId;
        ixrtcParams.serverAddress = url;
        ixrtcParams.strRoomId = roomId;
        this.mIXRTCCloud.enterRoom(ixrtcParams, 1);
    }

    public void start(String streamUrl, String xrtcAppId, String xrtcUserSig, int id) {
        if (this.vmsContainer == null) {
            Log.e("VmsHolder", "vms container is null");
        }

        if (TextUtils.isEmpty(this.vmsUid)) {
            this.vmsUid = "vmsUid_" + System.currentTimeMillis();
        }

        this.xrtcAppId = xrtcAppId;
        this.xrtcUserSig = xrtcUserSig;
        this.startStream(streamUrl);
        this.handleIDQueue.add(id);
    }

    /**
     * 根据云端下发的 cbm_vms.event_type == stop 时候调用.
     */
    public void stop() {
        Log.d("VmsHolder", "stop");
        if ((this.mIXRTCCloud) != null) {
            Log.d(TAG, " --> enter stop");
            this.mIXRTCCloud.stopAllRemoteView();
            this.mIXRTCCloud.exitRoom();
            Log.d(TAG, " <-- exit stop");
        }
    }

    public void init(Context context, VMSParams vmsParams) {
        if (this.isActivate) {
            Log.e(TAG, "no need init again!");
            return;
        }
        this.isActivate = true;
        this.mIXRTCCloud = IXRTCCloud.sharedInstance(context);
        Log.d("VmsHolder", "init:" + this.mIXRTCCloud);
        this.setVmsParams(vmsParams);
        this.mContext = new WeakReference<>(context);
    }

    public void unInit() {
        if (!this.isActivate) {
            Log.e(TAG, "no need uninit again!");
            return;
        }
        this.isActivate = false;
        this.mIXRTCCloud = null;
        try {
            IXRTCCloud.destroySharedInstance();
        } catch (Exception ex) {
            Log.e(TAG, "failed on destroy IXRTCCloud.destroySharedInstance()", ex);
        }
        this.mContext = null;
        this.xrtcListener = null;
    }

    public boolean isEnable() {
        return this.isActivate;
    }

    public void setVmsParams(VMSParams vmsParams) {
        if (vmsParams == null) {
            return;
        }
        if ((vmsParams.rotation) != null) {
            this.rotation = vmsParams.rotation;
        }

        if ((vmsParams.fillMode) != null) {
            this.fillMode = vmsParams.fillMode;
        }

        if ((vmsParams.mirrorType) != null) {
            this.mirrorType = vmsParams.mirrorType;
        }

        if ((vmsParams.streamType) != null) {
            this.streamType = vmsParams.streamType;
        }

        if ((vmsParams.volumeType) != null) {
            this.volumeType = vmsParams.volumeType;
        }

        if ((vmsParams.vmsContainer) != null) {
            this.vmsContainer = vmsParams.vmsContainer;
            Log.d("VmsHolder", "vmsContainer:" + this.vmsContainer);
        }

        if (vmsParams.customVideoView != null) {
            this.customVideoView = new WeakReference<>(vmsParams.customVideoView);
            Log.d("VmsHolder", "customVideoView:" + this.customVideoView.get());
        }
    }


    /**
     * 结束交互会话 时候调用
     * @param id 同启动时候的id
     */
    public void destroyView(int id) {
        if (!this.handleIDQueue.contains(id)) {
            return;
        }
        this.handleIDQueue.remove(id);
        try {
            this.stop();
        } catch (Exception ex) {
            Log.e(TAG, "error on stop", ex);
        }
        Log.d("VmsHolder", "destroyView：" + id);
        if ((this.vmsContainer) != null) {
            if ((this.cloudVideoView) != null) {
                this.vmsContainer.removeView(this.cloudVideoView);
                this.cloudVideoView = null;
            }

            if (this.getCustomView() != null) {
                this.vmsContainer.removeView(this.getCustomView());
            }
        }
    }

    public void setXrtcListener(XrtcListener var1) {
        this.xrtcListener = var1;
    }

    public interface XrtcListener {
        void onUserVideoAvailable(String var1, boolean var2);

        void onError(int errCode, String errMsg, Bundle extraInfo);

        void onFirstVideoFrame(String userId, int streamType, int width, int height);

        void onNetworkQuality(IXRTCCloudDef.IXRTCQuality localQuality, ArrayList<IXRTCCloudDef.IXRTCQuality> remoteQuality);

        void onConnectionLost();

        void onTryToReconnect();

        void onConnectionRecovery();

        void onEnterRoom(int var1);

        void onExitRoom(int var1);

        void onRenderVideoFrame(String var1, int var2, IXRTCCloudDef.IXRTCVideoFrame var3);
    }

    private static class Holder {
        private static final VmsHolder instance = new VmsHolder();

        private Holder() {
        }
    }
}
