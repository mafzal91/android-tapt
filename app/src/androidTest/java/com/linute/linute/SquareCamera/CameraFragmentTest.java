package com.linute.linute.SquareCamera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraSelector;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.linute.linute.MainContent.EditScreen.Dimens;
import com.linute.linute.MainContent.EditScreen.EditFragment;
import com.linute.linute.MainContent.EditScreen.PostOptions;
import com.linute.linute.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Uses the post camera the same as a user would: tap for a photo, hold to record.
 */
@RunWith(AndroidJUnit4.class)
public class CameraFragmentTest {

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getTargetContext();

    @Before
    public void setUp() {
        mInstrumentation.getUiAutomation()
                .grantRuntimePermission(mContext.getPackageName(), Manifest.permission.CAMERA);
        mInstrumentation.getUiAutomation()
                .grantRuntimePermission(mContext.getPackageName(), Manifest.permission.RECORD_AUDIO);
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .remove(CameraFragment.KEY_LAST_CAMERA).commit();
    }

    @Test
    public void aTapTakesAPhotoFromTheTopOfThePreview() throws Exception {
        try (ActivityScenario<CameraActivity> scenario = ActivityScenario.launch(intent())) {
            waitForPreview(scenario);
            int[] previewWidth = {0};
            scenario.onActivity(activity ->
                    previewWidth[0] = activity.findViewById(R.id.camera_preview_view).getWidth());

            touch(scenario, MotionEvent.ACTION_DOWN);
            touch(scenario, MotionEvent.ACTION_UP);

            EditFragment edit = waitForEditScreen(scenario);
            assertEquals(PostOptions.ContentType.Photo, argument(edit, PostOptions.class).type);

            Dimens dimens = argument(edit, Dimens.class);
            assertEquals(previewWidth[0], dimens.width);
            assertEquals(ScreenSizeSingleton.getSingleton().mHasRatioRequirement ?
                    (int) (previewWidth[0] * 6f / 5f) : previewWidth[0], dimens.height);
        }
    }

    @Test
    public void holdingTheButtonRecordsAVideoThatExports() throws Exception {
        try (ActivityScenario<CameraActivity> scenario = ActivityScenario.launchActivityForResult(intent())) {
            waitForPreview(scenario);

            View button = captureButton(scenario);
            touch(button, MotionEvent.ACTION_DOWN);
            Thread.sleep(3600); // 600ms before it starts, then 3 seconds of video
            touch(button, MotionEvent.ACTION_UP);

            EditFragment edit = waitForEditScreen(scenario);
            assertEquals(PostOptions.ContentType.Video, argument(edit, PostOptions.class).type);

            MediaMetadataRetriever recorded = read(argument(edit, Uri.class));
            assertTrue(duration(recorded) >= 2000);
            assertEquals("yes", recorded.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
            recorded.release();

            Dimens dimens = argument(edit, Dimens.class);
            assertTrue(dimens.width > dimens.height);

            scenario.onActivity(activity -> {
                Toolbar toolbar = activity.findViewById(R.id.toolbar);
                assertTrue(toolbar.getMenu().performIdentifierAction(R.id.menu_item_done, 0));
            });

            assertEquals(Activity.RESULT_OK, scenario.getResult().getResultCode());
            Uri video = scenario.getResult().getResultData().getParcelableExtra("video");

            MediaMetadataRetriever exported = new MediaMetadataRetriever();
            exported.setDataSource(video.toString());
            Bitmap frame = exported.getFrameAtTime(0);
            exported.release();
            new File(video.toString()).delete();

            // Upright, scaled so the short side is at most 640, then cropped from the top
            int width = Math.min(dimens.height, 640);
            assertEquals(width, frame.getWidth());
            assertEquals(ScreenSizeSingleton.getSingleton().mHasRatioRequirement ?
                    (int) (width * 1.2) : width, frame.getHeight());
        }
    }

    @Test
    public void aShortHoldTakesAPhotoInstead() throws Exception {
        try (ActivityScenario<CameraActivity> scenario = ActivityScenario.launch(intent())) {
            waitForPreview(scenario);

            View button = captureButton(scenario);
            touch(button, MotionEvent.ACTION_DOWN);
            Thread.sleep(1500);
            touch(button, MotionEvent.ACTION_UP);

            assertEquals(PostOptions.ContentType.Photo,
                    argument(waitForEditScreen(scenario), PostOptions.class).type);
        }
    }

    @Test
    public void recordingStopsByItselfAtFifteenSeconds() throws Exception {
        try (ActivityScenario<CameraActivity> scenario = ActivityScenario.launch(intent())) {
            waitForPreview(scenario);

            touch(scenario, MotionEvent.ACTION_DOWN);

            EditFragment edit = waitForEditScreen(scenario);
            assertEquals(PostOptions.ContentType.Video, argument(edit, PostOptions.class).type);

            MediaMetadataRetriever recorded = read(argument(edit, Uri.class));
            long duration = duration(recorded);
            recorded.release();
            assertTrue("Lasted " + duration, duration > 14000 && duration < 15500);

            // Closing the camera deletes the video, and the edit screen's overlay thread crashes if it
            // is still reading it. That race is older than CameraX; wait it out rather than hit it.
            Thread.sleep(3000);
        }
    }

    @Test
    public void leavingMidRecordingDropsTheVideoAndTheCameraStillWorks() throws Exception {
        try (ActivityScenario<CameraActivity> scenario = ActivityScenario.launch(intent())) {
            waitForPreview(scenario);

            touch(scenario, MotionEvent.ACTION_DOWN);
            Thread.sleep(3000);

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);

            waitForPreview(scenario);
            scenario.onActivity(activity -> assertNull(
                    activity.getSupportFragmentManager().findFragmentByTag(EditFragment.TAG)));

            touch(scenario, MotionEvent.ACTION_DOWN);
            touch(scenario, MotionEvent.ACTION_UP);

            assertEquals(PostOptions.ContentType.Photo,
                    argument(waitForEditScreen(scenario), PostOptions.class).type);
        }
    }

    @Test
    public void switchingToTheFrontCameraStillTakesAPhotoAndIsRemembered() throws Exception {
        try (ActivityScenario<CameraActivity> scenario = ActivityScenario.launch(intent())) {
            waitForPreview(scenario);

            scenario.onActivity(activity -> activity.findViewById(R.id.reverse_container).performClick());
            Thread.sleep(500);
            waitForPreview(scenario);

            touch(scenario, MotionEvent.ACTION_DOWN);
            touch(scenario, MotionEvent.ACTION_UP);

            assertNotNull(waitForEditScreen(scenario));
            assertEquals(CameraSelector.LENS_FACING_FRONT, PreferenceManager
                    .getDefaultSharedPreferences(mContext).getInt(CameraFragment.KEY_LAST_CAMERA, -1));
        }
    }

    private Intent intent() {
        return new Intent(mContext, CameraActivity.class)
                .putExtra(CameraActivity.EXTRA_CAMERA_TYPE,
                        new CameraType(CameraType.CAMERA_PICTURE | CameraType.CAMERA_VIDEO))
                .putExtra(CameraActivity.EXTRA_RETURN_TYPE, CameraActivity.RETURN_URI)
                .putExtra(CameraActivity.EXTRA_POST_OPTIONS,
                        new PostOptions(PostOptions.ContentType.Photo, PostOptions.ContentSubType.Post, null));
    }

    private void touch(ActivityScenario<CameraActivity> scenario, int action) {
        touch(captureButton(scenario), action);
    }

    /** Not through onActivity, which waits for the progress animation to leave the app idle. */
    private void touch(View button, int action) {
        mInstrumentation.runOnMainSync(() -> {
            long now = SystemClock.uptimeMillis();
            MotionEvent event = MotionEvent.obtain(now, now, action, 0, 0, 0);
            button.dispatchTouchEvent(event);
            event.recycle();
        });
    }

    private View captureButton(ActivityScenario<CameraActivity> scenario) {
        View[] button = {null};
        scenario.onActivity(activity -> button[0] = activity.findViewById(R.id.capture_image_button));
        return button[0];
    }

    private void waitForPreview(ActivityScenario<CameraActivity> scenario) throws InterruptedException {
        long end = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        boolean[] streaming = {false};
        while (!streaming[0] && System.currentTimeMillis() < end) {
            Thread.sleep(200);
            scenario.onActivity(activity -> {
                CameraPreviewLayout preview = activity.findViewById(R.id.camera_preview_view);
                streaming[0] = preview != null
                        && preview.getPreviewStreamState().getValue() == PreviewView.StreamState.STREAMING;
            });
        }
        assertTrue(streaming[0]);
    }

    private EditFragment waitForEditScreen(ActivityScenario<CameraActivity> scenario) throws InterruptedException {
        long end = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        Fragment[] edit = {null};
        while (edit[0] == null && System.currentTimeMillis() < end) {
            Thread.sleep(200);
            scenario.onActivity(activity ->
                    edit[0] = activity.getSupportFragmentManager().findFragmentByTag(EditFragment.TAG));
        }
        assertNotNull(edit[0]);
        return (EditFragment) edit[0];
    }

    private <T> T argument(Fragment fragment, Class<T> type) {
        for (String key : fragment.requireArguments().keySet()) {
            Object value = fragment.requireArguments().get(key);
            if (type.isInstance(value)) return type.cast(value);
        }
        throw new AssertionError("No " + type.getSimpleName() + " passed to the edit screen");
    }

    private MediaMetadataRetriever read(Uri video) throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(video.getPath());
        return retriever;
    }

    private long duration(MediaMetadataRetriever retriever) {
        return Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
    }
}
