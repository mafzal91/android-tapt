package com.linute.linute.MainContent.EditScreen.Tools;

import android.content.Context;
import android.net.Uri;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.bumptech.glide.Glide;
import com.linute.linute.MainContent.EditScreen.PostOptions.ContentType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public class OverlaysToolTest {

    /** The camera deletes its video when it closes, which can be before the edit screen reads it. */
    @Test
    public void aVideoDeletedBeforeItsFrameIsReadDoesNotCrash() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File deleted = new File(context.getCacheDir(), "deleted.mp4");
        deleted.delete();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> new OverlaysTool(
                Uri.fromFile(deleted), ContentType.Video, new FrameLayout(context), Glide.with(context)));

        // The frame is read on a background thread. If that throws, the app process dies.
        Thread.sleep(2000);
    }
}
