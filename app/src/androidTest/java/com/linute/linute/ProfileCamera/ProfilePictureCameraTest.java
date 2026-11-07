package com.linute.linute.ProfileCamera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.linute.linute.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * Takes a profile picture the same as a user would.
 */
@RunWith(AndroidJUnit4.class)
public class ProfilePictureCameraTest {

    @Test
    public void takingAPictureOpensTheConfirmScreenWithASquarePhoto() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        instrumentation.getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), Manifest.permission.CAMERA);

        Intent intent = new Intent(context, ProfileCameraActivity.class)
                .putExtra(ProfileCameraActivity.TYPE_KEY, ProfileCameraActivity.TYPE_CAMERA);

        try (ActivityScenario<ProfileCameraActivity> scenario = ActivityScenario.launch(intent)) {
            // The preview takes a moment to start, and the button does nothing until it has
            long end = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
            Fragment[] confirm = {null};
            while (confirm[0] == null && System.currentTimeMillis() < end) {
                Thread.sleep(1000);
                scenario.onActivity(activity -> {
                    Fragment shown = activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                    if (shown instanceof ConfirmProfilePicture) {
                        confirm[0] = shown;
                    } else {
                        activity.findViewById(R.id.capture_image_button).performClick();
                    }
                });
            }
            assertNotNull(confirm[0]);

            Uri image = confirm[0].requireArguments().getParcelable(ConfirmProfilePicture.IMAGE_URI);
            BitmapFactory.Options size = new BitmapFactory.Options();
            size.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(image.getPath(), size);

            assertTrue(size.outWidth > 0);
            assertEquals(size.outWidth, size.outHeight);
        }
    }
}
