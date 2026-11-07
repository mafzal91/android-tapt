package com.linute.linute.UtilsAndHelpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.OptIn;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.Crop;
import androidx.media3.effect.FrameDropEffect;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Writes edited videos with Media3 Transformer. Sizes are as displayed, after the input's rotation
 * is applied. Transformer only works from one looper thread, so use this from the main thread.
 * Listener methods are called on the main thread.
 */
@OptIn(markerClass = UnstableApi.class)
public final class VideoExport {

    // Not a hard limit: only whole frames get dropped, closest to this rate. 60fps input comes out
    // near 30fps, and 30fps input stays as it is.
    private static final float TARGET_FRAME_RATE = 24;
    private static final long PROGRESS_INTERVAL_MS = 500;

    public interface Listener {
        default void onProgress(int percent) {
        }

        void onDone();

        void onFailed(Exception e);
    }

    /**
     * Scales the video to width x height, mirrors it if asked, crops it to cropWidth x cropHeight
     * from the top left, then draws overlay over it at the cropped width.
     */
    public static VideoExport startEdit(Context context, Uri input, String output,
                                        int width, int height, boolean mirror,
                                        int cropWidth, int cropHeight, Bitmap overlay,
                                        Listener listener) {
        List<Effect> effects = new ArrayList<>();
        effects.add(FrameDropEffect.createDefaultFrameDropEffect(TARGET_FRAME_RATE));
        effects.add(Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_STRETCH_TO_FIT));
        if (mirror) {
            effects.add(new ScaleAndRotateTransformation.Builder().setScale(-1, 1).build());
        }
        effects.add(new Crop(-1, 2f * cropWidth / width - 1, 1 - 2f * cropHeight / height, 1));
        effects.add(topLeftOverlay(overlay, cropWidth));
        return new VideoExport(context, input, output, effects, listener);
    }

    /**
     * Adds room above the video and draws header there, scaled to the video's width.
     */
    public static VideoExport startShare(Context context, Uri input, String output,
                                         int videoWidth, int videoHeight, Bitmap header,
                                         Listener listener) {
        int headerHeight = scaledHeight(header, videoWidth);

        List<Effect> effects = new ArrayList<>();
        effects.add(FrameDropEffect.createDefaultFrameDropEffect(TARGET_FRAME_RATE));
        // A crop past the top edge makes the frame taller
        effects.add(new Crop(-1, 1, -1, 1 + 2f * headerHeight / videoHeight));
        effects.add(topLeftOverlay(header, videoWidth));
        return new VideoExport(context, input, output, effects, listener);
    }

    private static Effect topLeftOverlay(Bitmap bitmap, int width) {
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, scaledHeight(bitmap, width), true);
        StaticOverlaySettings settings = new StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(-1, 1)
                .setOverlayFrameAnchor(-1, 1)
                .build();
        return new OverlayEffect(Collections.singletonList(
                BitmapOverlay.createStaticBitmapOverlay(scaled, settings)));
    }

    private static int scaledHeight(Bitmap bitmap, int width) {
        return Math.round((float) width / bitmap.getWidth() * bitmap.getHeight());
    }


    private final Transformer mTransformer;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ProgressHolder mProgress = new ProgressHolder();
    private final Runnable mPollProgress;

    private VideoExport(Context context, Uri input, String output, List<Effect> effects,
                        final Listener listener) {
        mTransformer = new Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(new Transformer.Listener() {
                    @Override
                    public void onCompleted(Composition composition, ExportResult result) {
                        mHandler.removeCallbacks(mPollProgress);
                        listener.onDone();
                    }

                    @Override
                    public void onError(Composition composition, ExportResult result, ExportException e) {
                        mHandler.removeCallbacks(mPollProgress);
                        listener.onFailed(e);
                    }
                })
                .build();

        mPollProgress = new Runnable() {
            @Override
            public void run() {
                if (mTransformer.getProgress(mProgress) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    listener.onProgress(mProgress.progress);
                }
                mHandler.postDelayed(this, PROGRESS_INTERVAL_MS);
            }
        };

        mTransformer.start(new EditedMediaItem.Builder(MediaItem.fromUri(input))
                .setEffects(new Effects(Collections.emptyList(), effects))
                .build(), output);
        mHandler.postDelayed(mPollProgress, PROGRESS_INTERVAL_MS);
    }

    public void cancel() {
        mHandler.removeCallbacks(mPollProgress);
        mTransformer.cancel();
    }
}
