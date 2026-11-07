package com.linute.linute.SquareCamera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.camera.core.ZoomState;
import androidx.core.content.ContextCompat;

/**
 * The preview for the post camera. Dragging up or down zooms, and tapping shows where it focuses.
 */
public class CustomCameraPreview extends CameraPreviewLayout {

    public static final String TAG = CustomCameraPreview.class.getSimpleName();

    //share of the zoom range covered by one unit of zoom()
    private static final float ZOOM_STEP = 0.01f;

    //on the screen, for the focus circle
    private float mLastTouchX;
    private float mLastTouchY;

    //in this view, for the camera
    private float mViewX;
    private float mViewY;

    // For focus
    private boolean mIsFocus;

    public CustomCameraPreview(Context context) {
        super(context);
    }

    public CustomCameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomCameraPreview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private float mMovement = 0;
    private float mYPosition = 0;
    private boolean mMoved = false;

    private int mMovementThreshold = 5;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIsFocus = true;
                mLastTouchX = event.getRawX();
                mLastTouchY = event.getRawY();
                mViewX = event.getX();
                mViewY = event.getY();
                mYPosition = mLastTouchY;
                mMovement = 0;
                mMoved = false;
                break;
            case MotionEvent.ACTION_UP:
                if (mIsFocus && mCamera != null && !mMoved) handleFocus();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                cancelFocus();
                mIsFocus = false;
                break;
            case MotionEvent.ACTION_MOVE:
                mMovement += mYPosition - event.getRawY();
                mYPosition = event.getRawY();
                if (Math.abs(mMovement) > mMovementThreshold) {
                    mMoved = true;
                    zoom(mMovement > 0 ? 2 : -2);
                    mMovement = 0;
                }
                break;
        }

        return true;
    }


    public void zoom(int deltaZoom) {
        if (mCamera == null) return;

        ZoomState state = mCamera.getCameraInfo().getZoomState().getValue();
        if (state == null) return;

        float newZoom = state.getLinearZoom() + deltaZoom * ZOOM_STEP;
        if (newZoom > 1 || newZoom < 0) return;

        mCamera.getCameraControl().setLinearZoom(newZoom);
    }


    private void handleFocus() {
        if (mOnFocus != null) mOnFocus.onFocusStart(mLastTouchX, mLastTouchY);
        focus(mViewX, mViewY).addListener(() -> {
            if (mOnFocus != null) mOnFocus.onFocusFinished();
        }, ContextCompat.getMainExecutor(getContext()));
    }

    private OnFocus mOnFocus;

    public void setOnTouched(OnFocus foc) {
        mOnFocus = foc;
    }

    public interface OnFocus {
        void onFocusStart(float x, float y);

        void onFocusFinished();
    }
}
