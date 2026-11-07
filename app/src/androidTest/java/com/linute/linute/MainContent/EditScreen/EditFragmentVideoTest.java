package com.linute.linute.MainContent.EditScreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.appcompat.widget.Toolbar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.linute.linute.R;
import com.linute.linute.SquareCamera.CameraActivity;
import com.linute.linute.SquareCamera.ScreenSizeSingleton;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Presses done on the edit screen with a gallery video, the same as a user would, and checks the
 * video it hands back. sample.mp4 is stored as 1280x720 with 90 degrees rotation.
 */
@RunWith(AndroidJUnit4.class)
public class EditFragmentVideoTest {

    @Test
    public void doneExportsTheVideoAndReturnsIt() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), Manifest.permission.CAMERA);

        File input = new File(context.getCacheDir(), "sample.mp4");
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("sample.mp4");
             FileOutputStream out = new FileOutputStream(input)) {
            in.transferTo(out);
        }

        PostOptions options = new PostOptions(PostOptions.ContentType.UploadedVideo, PostOptions.ContentSubType.Post, null);
        Dimens dimens = new Dimens(1280, 720);
        dimens.setRotation(90);
        dimens.setNeedsCropping(90);

        Intent intent = new Intent(context, CameraActivity.class)
                .putExtra(CameraActivity.EXTRA_RETURN_TYPE, CameraActivity.RETURN_URI)
                .putExtra(CameraActivity.EXTRA_POST_OPTIONS, options);

        try (ActivityScenario<CameraActivity> scenario = ActivityScenario.launchActivityForResult(intent)) {
            scenario.onActivity(activity -> activity.launchFragment(
                    EditFragment.newInstance(Uri.fromFile(input), CameraActivity.RETURN_URI, dimens, options),
                    EditFragment.TAG));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                Toolbar toolbar = activity.findViewById(R.id.toolbar);
                assertTrue(toolbar.getMenu().performIdentifierAction(R.id.menu_item_done, 0));
            });

            assertEquals(Activity.RESULT_OK, scenario.getResult().getResultCode());
            Intent result = scenario.getResult().getResultData();
            Uri video = result.getParcelableExtra("video");
            assertNotNull(result.getParcelableExtra("image"));

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(video.toString());
            Bitmap frame = retriever.getFrameAtTime(0);
            retriever.release();
            new File(video.toString()).delete();

            // Scaled so the short side is 640, then cropped from the top
            assertEquals(640, frame.getWidth());
            assertEquals(ScreenSizeSingleton.getSingleton().mHasRatioRequirement ? 768 : 640, frame.getHeight());
        }
    }
}
