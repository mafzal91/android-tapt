package com.linute.linute.UtilsAndHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.linute.linute.SquareCamera.ImageUtility;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs real exports on a device. sample.mp4 is two seconds at 60fps, with audio, stored as 1280x720
 * with 90 degrees rotation. It displays as 720x1280 in four solid quarters:
 * blue and red on top, yellow and lime below.
 */
@RunWith(AndroidJUnit4.class)
public class VideoExportTest {

    private static final int BLUE = Color.rgb(0, 0, 255);
    private static final int RED = Color.rgb(255, 0, 0);
    private static final int YELLOW = Color.rgb(255, 255, 0);
    private static final int LIME = Color.rgb(0, 255, 0);

    private interface Export {
        void start(VideoExport.Listener listener);
    }

    private Context mContext;
    private Uri mInput;
    private String mOutput;

    @Before
    public void setUp() throws IOException {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input = new File(mContext.getCacheDir(), "sample.mp4");
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("sample.mp4");
             FileOutputStream out = new FileOutputStream(input)) {
            in.transferTo(out);
        }
        mInput = Uri.fromFile(input);
        mOutput = ImageUtility.getVideoUri();
        assertNotNull(mOutput);
    }

    @After
    public void tearDown() {
        new File(mOutput).delete();
    }

    @Test
    public void editScalesCropsFromTheTopLeftAndDrawsTheOverlay() throws Exception {
        export(listener -> VideoExport.startEdit(mContext, mInput, mOutput, 640, 1138, false,
                640, 768, whiteTopLeftCorner(), listener));

        Bitmap frame = firstFrame();
        assertEquals(640, frame.getWidth());
        assertEquals(768, frame.getHeight());
        assertColor(Color.WHITE, frame, 40, 40);
        assertColor(BLUE, frame, 160, 300);
        assertColor(RED, frame, 480, 300);
        assertColor(YELLOW, frame, 160, 700);
        assertColor(LIME, frame, 480, 700);
        assertStreams(mOutput);
    }

    @Test
    public void editMirrors() throws Exception {
        export(listener -> VideoExport.startEdit(mContext, mInput, mOutput, 640, 1138, true,
                640, 1138, whiteTopLeftCorner(), listener));

        Bitmap frame = firstFrame();
        assertEquals(1138, frame.getHeight());
        assertColor(Color.WHITE, frame, 40, 40);
        assertColor(RED, frame, 160, 300);
        assertColor(BLUE, frame, 480, 300);
    }

    @Test
    public void shareAddsHeaderAboveTheVideo() throws Exception {
        Bitmap header = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);
        new Canvas(header).drawColor(Color.WHITE);

        export(listener -> VideoExport.startShare(mContext, mInput, mOutput, 720, 1280, header, listener));

        Bitmap frame = firstFrame();
        assertEquals(720, frame.getWidth());
        assertEquals(1640, frame.getHeight());
        assertColor(Color.WHITE, frame, 360, 180);
        assertColor(BLUE, frame, 180, 500);
        assertColor(RED, frame, 540, 500);
        assertColor(YELLOW, frame, 180, 1400);
        assertColor(LIME, frame, 540, 1400);
        assertStreams(mOutput);
    }

    /** Transparent, apart from an opaque white top left corner. */
    private static Bitmap whiteTopLeftCorner() {
        Bitmap overlay = Bitmap.createBitmap(320, 400, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(overlay);
        canvas.clipRect(0, 0, 50, 50);
        canvas.drawColor(Color.WHITE);
        return overlay;
    }

    private void export(final Export export) throws InterruptedException {
        final CountDownLatch finished = new CountDownLatch(1);
        final Exception[] failure = new Exception[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> export.start(new VideoExport.Listener() {
            @Override
            public void onDone() {
                finished.countDown();
            }

            @Override
            public void onFailed(Exception e) {
                failure[0] = e;
                finished.countDown();
            }
        }));
        assertTrue("export timed out", finished.await(2, TimeUnit.MINUTES));
        assertNull(failure[0]);
    }

    private Bitmap firstFrame() throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(mOutput);
        Bitmap frame = retriever.getFrameAtTime(0);
        retriever.release();
        return frame;
    }

    private static void assertColor(int expected, Bitmap frame, int x, int y) {
        int actual = frame.getPixel(x, y);
        String message = String.format("at %d,%d: expected %08x, got %08x", x, y, expected, actual);
        assertTrue(message, Math.abs(Color.red(expected) - Color.red(actual)) < 50
                && Math.abs(Color.green(expected) - Color.green(actual)) < 50
                && Math.abs(Color.blue(expected) - Color.blue(actual)) < 50);
    }

    /** The output must be H.264, keep its audio, and have dropped about half of the 120 frames. */
    private static void assertStreams(String path) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(path);
        boolean hasAudio = false;
        int videoTrack = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) hasAudio = true;
            if (mime.startsWith("video/")) {
                assertEquals("video/avc", mime);
                videoTrack = i;
            }
        }
        assertTrue(hasAudio);
        assertTrue(videoTrack >= 0);

        extractor.selectTrack(videoTrack);
        int frames = 0;
        do {
            frames++;
        } while (extractor.advance());
        extractor.release();
        assertTrue("got " + frames + " frames", frames >= 30 && frames <= 70);
    }
}
