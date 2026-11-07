package com.linute.linute.MainContent.Uploading;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.linute.linute.API.LSDKEvents;
import com.linute.linute.LoginAndSignup.PreLoginActivity;
import com.linute.linute.MainContent.MainActivity;
import com.linute.linute.R;
import com.linute.linute.UtilsAndHelpers.LinuteConstants;
import com.linute.linute.UtilsAndHelpers.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import okhttp3.Response;

/**
 * Created by QiFeng on 6/25/16.
 */
public class UploadWorker extends Worker {

    public static final String TAG = UploadWorker.class.getSimpleName();

    static final String UNIQUE_WORK = "uploads";

    private static final String CHANNEL_ID = "uploads";

    // A foreground notification can't use 0, and goes away when the work ends. The result needs
    // an id of its own to stay on screen.
    private static final int PROGRESS_ID = 1;
    private static final int DONE_ID = 2;

    private final NotificationManager mNotificationManager;
    private final NotificationCompat.Builder mBuilder;

    private int mLastPercent = -1;

    /**
     * Uploads run one at a time, oldest first, once there is a network. Each one holds a whole file
     * in memory, so running them together could run out.
     */
    public static void enqueue(Context context, PendingUploadPost post) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(UploadWorker.class)
                .setInputData(post.toData())
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // Required since Android 8. Low importance: progress updates shouldn't make a sound.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotificationManager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    "Uploads", NotificationManager.IMPORTANCE_LOW));
        }
        mBuilder = new NotificationCompat.Builder(context, CHANNEL_ID);
        mBuilder.setSmallIcon(android.R.drawable.stat_sys_upload);
    }

    //// TODO: 6/30/16 move processing of video to this service?

    // Android 11 and older run expedited work as a foreground service, and ask for this first
    @NonNull
    @Override
    public ForegroundInfo getForegroundInfo() {
        return foregroundInfo(preparingNotification());
    }

    @NonNull
    @Override
    public Result doWork() {
        PendingUploadPost p = PendingUploadPost.fromData(getInputData());

        boolean posted;
        try {
            posted = sendNextFile(p);
        } catch (IOException | OutOfMemoryError e) {
            // The whole video is held in memory as text, which a large one doesn't fit. WorkManager
            // would swallow the error, so nothing would tell the user it was lost.
            Log.w(TAG, "doWork: upload failed", e);
            posted = false;
        }

        // Lost the network or ran out of time. WorkManager runs this again later, so don't tell
        // the user it failed.
        if (!posted && isStopped()) {
            return Result.retry();
        }

        mNotificationManager.cancel(PROGRESS_ID);
        if (posted) {
            mBuilder.setContentTitle("Upload complete")
                    .setContentText("")
                    .setSmallIcon(R.drawable.ic_stat_untitled_4_01)
                    .setProgress(0, 0, false)
                    .setAutoCancel(true)
                    .setContentIntent(getIntent(p.getId(), p.getCollegeId()))
                    .setContentText(getPostText(p.getType()));
            mNotificationManager.notify(DONE_ID, mBuilder.build());
        } else {
            failedToPost(p);
        }

        // Also for a failed post, which the notification reports. Result.failure() would fail every
        // upload queued behind this one.
        return Result.success();
    }

    private boolean sendNextFile(PendingUploadPost p) throws IOException {
        Context context = getApplicationContext();

        Uri imageUri = Uri.parse(p.getImagePath());
        File file = new File(imageUri.getPath());
        if (!file.exists()) {
            Log.i(TAG, "sendNextFile: no image");
            return false;
        }

        Bitmap image = MediaStore.Images.Media.getBitmap(context.getContentResolver(), imageUri);

        mBuilder.setLargeIcon(Bitmap.createScaledBitmap(image, 100, (int) (100f * (float) image.getHeight() / image.getWidth()), false));
        startForeground(preparingNotification());

        HashMap<String, Object> params = new HashMap<>();

        params.put("id", p.getId());
        params.put("college", p.getCollegeId());
        params.put("privacy", p.getPrivacy());
        params.put("isAnonymousCommentsDisabled", p.getIsAnonymousCommentsDisabled());
        params.put("title", p.getTitle());
        params.put("memes", new JSONArray(p.getStickers()));
        params.put("filters", new JSONArray(p.getFilters()));

        JSONArray coord = new JSONArray();
        JSONObject jsonObject = new JSONObject();
        coord.put(p.getLatitude());
        coord.put(p.getLongitude());

        params.put("trends", new JSONArray(p.getTrends()));
        params.put("users", new JSONArray(p.getPeople()));

        if (p.getTrendId() != null) {
            params.put("trend", p.getTrendId());
        }

        try {
            jsonObject.put("coordinates", coord);
            params.put("geo", jsonObject);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        params.put("type", p.getType());
        params.put("owner", p.getOwner());
        JSONArray imageArray = new JSONArray();

        imageArray.put(
                p.getType() == 0 ?
                        Utils.encodeImageBase64HighRes(image) : Utils.encodeImageBase64(image));

        params.put("images", imageArray);
        if (p.getVideoPath() != null) {
            file = new File(p.getVideoPath());

            if (!file.exists()) {
                Log.d(TAG, "sendNextFile: no vid");
                return false;
            }

            JSONArray videoArray = new JSONArray();
            videoArray.put(Utils.encodeFileBase64(file));
            params.put("videos", videoArray);
        }

        final int pendingFiles = countPendingUploads();

        try (Response r = new LSDKEvents(context).postEvent(p.getUserToken(), params, new CountingRequestBody.Listener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength) {
                int percent = (int) (100 * bytesWritten / contentLength);
                // This is called for every 2KB, far more often than Android shows updates
                if (percent == mLastPercent) return;
                mLastPercent = percent;

                mBuilder.setContentTitle("Uploading content")
                        .setContentText(pendingFiles + (pendingFiles > 1 ? " files remaining" : " file remaining"))
                        .setProgress(100, percent, false);
                mNotificationManager.notify(PROGRESS_ID, mBuilder.build());
            }
        })) {
            return r.isSuccessful();
        } finally {
            image.recycle();
        }
    }

    private Notification preparingNotification() {
        return mBuilder.setContentTitle("Preparing for upload")
                .setContentText("")
                .setProgress(0, 0, true)
                .setContentIntent(null)
                .setAutoCancel(false)
                .build();
    }

    private ForegroundInfo foregroundInfo(Notification notification) {
        return new ForegroundInfo(PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }

    /**
     * Keeps a long upload from being stopped. Android 12 and newer refuse this if the app has gone
     * to the background by now. The upload goes ahead anyway, and only a very long one gets
     * stopped and started over.
     */
    private void startForeground(Notification notification) {
        try {
            setForegroundAsync(foregroundInfo(notification)).get();
        } catch (ExecutionException | InterruptedException | IllegalStateException e) {
            Log.w(TAG, "startForeground: not allowed", e);
            mNotificationManager.notify(PROGRESS_ID, notification);
        }
    }

    private int countPendingUploads() {
        try {
            int pending = 0;
            for (WorkInfo info : WorkManager.getInstance(getApplicationContext())
                    .getWorkInfosForUniqueWork(UNIQUE_WORK).get()) {
                if (!info.getState().isFinished()) pending++;
            }
            return pending;
        } catch (ExecutionException | InterruptedException e) {
            return 1;
        }
    }

    private PendingIntent getIntent(String eventID, String college) {
        Context context = getApplicationContext();
        Intent intent;
        boolean isLoggedIn = context.getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE).getBoolean("isLoggedIn", false);
        if (!isLoggedIn) {
            intent = new Intent(context, PreLoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else {
            intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("NOTIFICATION", LinuteConstants.FEED_DETAIL);
            intent.putExtra("show_update", false);
            intent.putExtra("event", eventID);
        }

        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }


    private String getPostText(int type) {
        String text;
        switch (type) {
            case 0:
                text = "Status";
                break;
            case 1:
                text = "Image";
                break;
            case 2:
                text = "Video";
                break;
            default:
                text = "Post";
                break;
        }
        return text + " has been posted";
    }


    private void failedToPost(final PendingUploadPost p) {
        Context context = getApplicationContext();
        int notificationId = p.getId().hashCode();
        Intent i = new Intent(context, UploadRetryReceiver.class);
        i.putExtra(PendingUploadPost.PENDING_POST_KEY, p);
        mBuilder.setProgress(0, 0, false)
                .setSmallIcon(R.drawable.ic_stat_untitled_4_01)
                .setContentTitle("File failed to upload")
                .setContentText("Tap to retry")
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getBroadcast(context, notificationId, i, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        mNotificationManager.notify(notificationId, mBuilder.build());
    }
}
