package com.iflytek.aiui.demo.chat.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;

import com.iflytek.xrtcsdk.conference.ui.IXCloudVideoView;

public class RoundedVideoView extends IXCloudVideoView {

    private final Path mDrawPath = new Path();
    private final RectF mRect = new RectF();

    private final int[] mLastViewWidthHeight = new int[]{0, 0};

    public RoundedVideoView(Context context) {
        super(context);
        Log.d("RoundedVideoView", "RoundedSurfaceView 1:" + context);
        setBackgroundColor(Color.parseColor("#00FFFFFF"));  //设置一个背景透明
        getSurfaceView().setZOrderMediaOverlay(false);
    }

    public RoundedVideoView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public RoundedVideoView(Context context, SurfaceView surfaceView) {
        super(context, surfaceView);
    }

    public RoundedVideoView(Context context, AttributeSet attributeSet, SurfaceView surfaceView) {
        super(context, attributeSet, surfaceView);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float cornerRadius = 300;//444;
        //Path path = new Path();
        final int width = getWidth();
        final int height = getHeight();
        //Log.v("RoundedVideoView", "onDraw:" + width + "," + height);

        //path.addRoundRect(new RectF(0, 0, width, height), cornerRadius, cornerRadius, Path.Direction.CW);
        if (mLastViewWidthHeight[0] != width ||
                mLastViewWidthHeight[1] != height) {

            mRect.set(0, 0, width, height);
            mDrawPath.reset();
            mDrawPath.addRoundRect(mRect, cornerRadius, cornerRadius, Path.Direction.CW);

            mLastViewWidthHeight[0] = width;
            mLastViewWidthHeight[1] = height;
        }
        canvas.clipPath(mDrawPath);
    }
}
