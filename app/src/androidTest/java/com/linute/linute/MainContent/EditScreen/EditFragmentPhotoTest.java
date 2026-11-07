package com.linute.linute.MainContent.EditScreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import androidx.appcompat.widget.Toolbar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.linute.linute.MainContent.Uploading.PendingUploadPost;
import com.linute.linute.R;
import com.linute.linute.SquareCamera.CameraActivity;
import com.linute.linute.SquareCamera.CameraType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Takes a photo, or presses done on the edit screen, the same as a user would.
 */
@RunWith(AndroidJUnit4.class)
public class EditFragmentPhotoTest {

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getTargetContext();

    @Test
    public void doneReturnsThePhoto() throws IOException {
        Intent result = pressDone(CameraActivity.RETURN_URI);

        assertEquals(CameraActivity.IMAGE, result.getIntExtra("type", -1));
        Uri image = result.getParcelableExtra("image");
        assertTrue(new File(image.getPath()).exists());
    }

    @Test
    public void doneReturnsAPostToUpload() throws IOException {
        Intent result = pressDone(CameraActivity.SEND_POST);
        result.setExtrasClassLoader(PendingUploadPost.class.getClassLoader());

        PendingUploadPost post = result.getParcelableExtra(PendingUploadPost.PENDING_POST_KEY);
        assertNotNull(post);
        assertEquals(1, post.getType());
        assertTrue(new File(Uri.parse(post.getImagePath()).getPath()).exists());
    }

    @Test
    public void takingAPhotoOpensTheEditScreen() throws Exception {
        grantCamera();
        Intent intent = new Intent(mContext, CameraActivity.class)
                .putExtra(CameraActivity.EXTRA_CAMERA_TYPE, new CameraType(CameraType.CAMERA_PICTURE))
                .putExtra(CameraActivity.EXTRA_RETURN_TYPE, CameraActivity.RETURN_URI)
                .putExtra(CameraActivity.EXTRA_POST_OPTIONS, options());

        try (ActivityScenario<CameraActivity> scenario = ActivityScenario.launch(intent)) {
            // The preview takes a moment to start, and the button does nothing until it has
            long end = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
            boolean[] opened = {false};
            while (!opened[0] && System.currentTimeMillis() < end) {
                Thread.sleep(1000);
                scenario.onActivity(activity -> {
                    if (activity.getSupportFragmentManager().findFragmentByTag(EditFragment.TAG) != null) {
                        opened[0] = true;
                    } else {
                        activity.findViewById(R.id.capture_image_button).performClick();
                    }
                });
            }
            assertTrue(opened[0]);
        }
    }

    private Intent pressDone(int returnType) throws IOException {
        grantCamera();

        Bitmap bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        File input = new File(mContext.getCacheDir(), "edit-photo.png");
        try (FileOutputStream out = new FileOutputStream(input)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }

        PostOptions options = options();
        Intent intent = new Intent(mContext, CameraActivity.class)
                .putExtra(CameraActivity.EXTRA_RETURN_TYPE, returnType)
                .putExtra(CameraActivity.EXTRA_POST_OPTIONS, options);

        try (ActivityScenario<CameraActivity> scenario = ActivityScenario.launchActivityForResult(intent)) {
            scenario.onActivity(activity -> activity.launchFragment(
                    EditFragment.newInstance(Uri.fromFile(input), returnType, new Dimens(400, 400), options),
                    EditFragment.TAG));
            mInstrumentation.waitForIdleSync();

            scenario.onActivity(activity -> {
                Toolbar toolbar = activity.findViewById(R.id.toolbar);
                assertTrue(toolbar.getMenu().performIdentifierAction(R.id.menu_item_done, 0));
            });

            assertEquals(Activity.RESULT_OK, scenario.getResult().getResultCode());
            return scenario.getResult().getResultData();
        }
    }

    private PostOptions options() {
        return new PostOptions(PostOptions.ContentType.Photo, PostOptions.ContentSubType.Post, null);
    }

    private void grantCamera() {
        mInstrumentation.getUiAutomation()
                .grantRuntimePermission(mContext.getPackageName(), Manifest.permission.CAMERA);
    }
}
