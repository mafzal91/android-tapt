package com.linute.linute.MainContent.Uploading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.service.notification.StatusBarNotification;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.linute.linute.API.API_Methods;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * Runs uploads through the real WorkManager, with a fake server in place of the network.
 */
@RunWith(AndroidJUnit4.class)
public class UploadWorkerTest {

    private static final long TIMEOUT_MS = 30000;

    private static final String DONE = "Upload complete";
    private static final String FAILED = "File failed to upload";

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final FakeServer mServer = new FakeServer();
    private OkHttpClient mRealClient;
    private File mImage;
    private File mVideo;

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                mContext.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);

        WorkManager workManager = WorkManager.getInstance(mContext);
        workManager.cancelUniqueWork(UploadWorker.UNIQUE_WORK).getResult().get();
        workManager.pruneWork().getResult().get();
        notificationManager().cancelAll();

        mRealClient = API_Methods.CLIENT;
        API_Methods.CLIENT = new OkHttpClient.Builder().addInterceptor(mServer).build();

        mImage = new File(mContext.getCacheDir(), "upload-test.png");
        Bitmap bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(mImage)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }

        mVideo = new File(mContext.getCacheDir(), "upload-test.mp4");
        Files.write(mVideo.toPath(), "not really a video".getBytes());
    }

    @After
    public void tearDown() {
        API_Methods.CLIENT = mRealClient;
        mImage.delete();
        mVideo.delete();
    }

    @Test
    public void postsEveryFieldAndSaysSo() throws Exception {
        UploadWorker.enqueue(mContext, post("a", mImage, mVideo));
        waitForUploads();

        assertEquals(1, mServer.bodies.size());
        assertEquals("/" + API_Methods.VERSION + "/events/", mServer.paths.get(0));

        JSONObject body = new JSONObject(mServer.bodies.get(0));
        assertEquals("a", body.getString("id"));
        assertEquals("college", body.getString("college"));
        assertEquals(1, body.getInt("privacy"));
        assertEquals(1, body.getInt("isAnonymousCommentsDisabled"));
        assertEquals("title", body.getString("title"));
        assertEquals(2, body.getInt("type"));
        assertEquals("owner", body.getString("owner"));
        assertEquals("trend", body.getString("trend"));
        assertEquals("sticker", body.getJSONArray("memes").getString(0));
        assertEquals("filter", body.getJSONArray("filters").getString(0));
        assertEquals(1, body.getJSONArray("images").length());
        assertEquals("not really a video",
                new String(Base64.decode(body.getJSONArray("videos").getString(0), Base64.DEFAULT)));

        Notification done = waitForNotification(DONE);
        assertEquals("Video has been posted", done.extras.getString(Notification.EXTRA_TEXT));
    }

    @Test
    public void staysInTheForegroundUntilTheUploadEnds() throws Exception {
        mServer.hold = new CountDownLatch(1);
        UploadWorker.enqueue(mContext, post("a", mImage, mVideo));
        assertTrue(mServer.reached.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        waitFor("foreground notification", this::isInForeground);

        mServer.hold.countDown();
        waitForNotification(DONE);
        waitFor("foreground notification to go", () -> !isInForeground());
    }

    @Test
    public void failedUploadCanBeRetriedFromItsNotification() throws Exception {
        mServer.codes.add(500);
        UploadWorker.enqueue(mContext, post("a", mImage, mVideo));
        waitForUploads();

        Notification failed = waitForNotification(FAILED);
        assertNull(findNotification(DONE));

        failed.contentIntent.send();
        waitForRequests(2);
        waitForUploads();

        assertEquals("a", new JSONObject(mServer.bodies.get(1)).getString("id"));
        waitForNotification(DONE);
    }

    @Test
    public void uploadsRunInOrderAndAFailureDoesNotStopTheNext() throws Exception {
        mServer.hold = new CountDownLatch(1);
        mServer.codes.add(500);
        UploadWorker.enqueue(mContext, post("a", mImage, mVideo));
        assertTrue(mServer.reached.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        UploadWorker.enqueue(mContext, post("b", mImage, mVideo));
        UploadWorker.enqueue(mContext, post("c", mImage, mVideo));
        mServer.hold.countDown();

        waitForRequests(3);
        waitForUploads();

        assertEquals("a", new JSONObject(mServer.bodies.get(0)).getString("id"));
        assertEquals("b", new JSONObject(mServer.bodies.get(1)).getString("id"));
        assertEquals("c", new JSONObject(mServer.bodies.get(2)).getString("id"));
        waitForNotification(FAILED);
        waitForNotification(DONE);
    }

    @Test
    public void stoppedUploadIsNotReportedAsFailed() throws Exception {
        mServer.hold = new CountDownLatch(1);
        mServer.dropConnection = true;
        UploadWorker.enqueue(mContext, post("a", mImage, mVideo));
        assertTrue(mServer.reached.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        WorkManager.getInstance(mContext).cancelUniqueWork(UploadWorker.UNIQUE_WORK).getResult().get();
        mServer.hold.countDown();

        Thread.sleep(1000);
        assertNull(findNotification(FAILED));
    }

    @Test
    public void droppedConnectionOffersRetry() throws Exception {
        mServer.dropConnection = true;
        UploadWorker.enqueue(mContext, post("a", mImage, mVideo));
        waitForUploads();

        waitForNotification(FAILED);
    }

    @Test
    public void missingFileFailsWithoutCallingTheServer() throws Exception {
        assertTrue(mVideo.delete());
        UploadWorker.enqueue(mContext, post("a", mImage, mVideo));
        waitForUploads();

        assertTrue(mServer.bodies.isEmpty());
        waitForNotification(FAILED);
    }

    @Test
    public void statusWithoutStickersOrVideoStillPosts() throws Exception {
        UploadWorker.enqueue(mContext, new PendingUploadPost("a", "college", 0, 0, "title", 0,
                Uri.fromFile(mImage).toString(), null, null, null, "owner", "token", null));
        waitForUploads();

        JSONObject body = new JSONObject(mServer.bodies.get(0));
        assertEquals(0, body.getJSONArray("memes").length());
        assertFalse(body.has("videos"));
        assertFalse(body.has("trend"));
        assertEquals("Status has been posted",
                waitForNotification(DONE).extras.getString(Notification.EXTRA_TEXT));
    }

    @Test
    public void videoTooLargeForMemoryOffersRetry() throws Exception {
        byte[] megabyte = new byte[1024 * 1024];
        try (FileOutputStream out = new FileOutputStream(mVideo)) {
            for (int i = 0; i < 100; i++) out.write(megabyte);
        }
        UploadWorker.enqueue(mContext, post("a", mImage, mVideo));
        UploadWorker.enqueue(mContext, new PendingUploadPost("b", "college", 0, 0, "title", 0,
                Uri.fromFile(mImage).toString(), null, null, null, "owner", "token", null));

        waitForRequests(1);
        waitForUploads();

        assertEquals("b", new JSONObject(mServer.bodies.get(0)).getString("id"));
        waitForNotification(FAILED);
    }

    private PendingUploadPost post(String id, File image, File video) {
        return new PendingUploadPost(id, "college", 1, 1, "title", 2,
                Uri.fromFile(image).toString(), video.getPath(),
                new ArrayList<>(Arrays.asList("sticker")), new ArrayList<>(Arrays.asList("filter")),
                "owner", "token", "trend");
    }

    private void waitForRequests(int count) throws InterruptedException {
        waitFor(count + " requests", () -> mServer.bodies.size() >= count);
    }

    private void waitForUploads() throws Exception {
        long end = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < end) {
            List<WorkInfo> uploads = WorkManager.getInstance(mContext)
                    .getWorkInfosForUniqueWork(UploadWorker.UNIQUE_WORK).get();
            boolean finished = !uploads.isEmpty();
            for (WorkInfo upload : uploads) {
                finished &= upload.getState().isFinished();
            }
            if (finished) return;
            Thread.sleep(50);
        }
        fail("uploads did not finish");
    }

    // Android posts them in the background, so one can be a moment behind the upload
    private Notification waitForNotification(String title) throws InterruptedException {
        waitFor("notification: " + title, () -> findNotification(title) != null);
        return findNotification(title);
    }

    private void waitFor(String what, BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                fail("timed out waiting for " + what);
            }
            Thread.sleep(50);
        }
    }

    private boolean isInForeground() {
        for (StatusBarNotification active : notificationManager().getActiveNotifications()) {
            if ((active.getNotification().flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) {
                return true;
            }
        }
        return false;
    }

    private NotificationManager notificationManager() {
        return mContext.getSystemService(NotificationManager.class);
    }

    private Notification findNotification(String title) {
        for (StatusBarNotification active : notificationManager().getActiveNotifications()) {
            Notification notification = active.getNotification();
            if (title.equals(notification.extras.getString(Notification.EXTRA_TITLE))) {
                return notification;
            }
        }
        return null;
    }

    private static class FakeServer implements Interceptor {
        final List<String> paths = Collections.synchronizedList(new ArrayList<>());
        final List<String> bodies = Collections.synchronizedList(new ArrayList<>());
        final Queue<Integer> codes = new ArrayDeque<>();
        final CountDownLatch reached = new CountDownLatch(1);
        volatile CountDownLatch hold = new CountDownLatch(0);
        volatile boolean dropConnection;

        @Override
        public Response intercept(Chain chain) throws IOException {
            Buffer buffer = new Buffer();
            chain.request().body().writeTo(buffer);

            reached.countDown();
            try {
                hold.await();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            if (dropConnection) {
                throw new IOException("connection dropped");
            }

            paths.add(chain.request().url().encodedPath());
            bodies.add(buffer.readUtf8());

            Integer code = codes.poll();
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code == null ? 200 : code)
                    .message("")
                    .body(ResponseBody.create(null, "{}"))
                    .build();
        }
    }
}
