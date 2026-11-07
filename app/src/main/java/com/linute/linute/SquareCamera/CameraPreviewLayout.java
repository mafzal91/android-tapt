package com.linute.linute.SquareCamera;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.Preview;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LiveData;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * The camera preview, held at 3:4 with the top left of the image pinned to the top left of the
 * view, because photos are cropped from the top. Tapping focuses on that spot.
 *
 * PreviewView is final, so this wraps it instead of extending it.
 */
public class CameraPreviewLayout extends FrameLayout {

    private static final double ASPECT_RATIO = 3.0 / 4.0;

    private final PreviewView mPreview;

    protected Camera mCamera;

    private float mLastTouchX;
    private float mLastTouchY;
    private boolean mIsFocus;

    public CameraPreviewLayout(Context context) {
        this(context, null);
    }

    public CameraPreviewLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraPreviewLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mPreview = new PreviewView(context);
        // getBitmap() needs a TextureView, and the default mode can pick a SurfaceView
        mPreview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        mPreview.setScaleType(PreviewView.ScaleType.FILL_START);
        addView(mPreview, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);

        final boolean isPortrait =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        if (isPortrait) {
            if (width > height * ASPECT_RATIO) {
                width = (int) (height * ASPECT_RATIO + 0.5);
            } else {
                height = (int) (width / ASPECT_RATIO + 0.5);
            }
        } else {
            if (height > width * ASPECT_RATIO) {
                height = (int) (width * ASPECT_RATIO + 0.5);
            } else {
                width = (int) (height / ASPECT_RATIO + 0.5);
            }
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    public void setCamera(Camera camera) {
        mCamera = camera;
    }

    public Preview.SurfaceProvider getSurfaceProvider() {
        return mPreview.getSurfaceProvider();
    }

    public LiveData<PreviewView.StreamState> getPreviewStreamState() {
        return mPreview.getPreviewStreamState();
    }

    /** What the preview is showing, at the size of this view. Must be called on the main thread. */
    @Nullable
    public Bitmap getBitmap() {
        return mPreview.getBitmap();
    }

    /** @param x, y position in this view */
    protected ListenableFuture<FocusMeteringResult> focus(float x, float y) {
        return mCamera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(
                mPreview.getMeteringPointFactory().createPoint(x, y)).build());
    }

    protected void cancelFocus() {
        if (mCamera != null) mCamera.getCameraControl().cancelFocusAndMetering();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIsFocus = true;
                mLastTouchX = event.getX();
                mLastTouchY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                if (mIsFocus && mCamera != null) focus(mLastTouchX, mLastTouchY);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                cancelFocus();
                mIsFocus = false;
                break;
        }

        return true;
    }
}
