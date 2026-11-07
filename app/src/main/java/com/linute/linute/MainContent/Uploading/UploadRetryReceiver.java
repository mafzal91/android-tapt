package com.linute.linute.MainContent.Uploading;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Queues a failed upload again when its notification is tapped.
 */
public class UploadRetryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingUploadPost post = intent.getParcelableExtra(PendingUploadPost.PENDING_POST_KEY);
        if (post != null) {
            UploadWorker.enqueue(context, post);
        }
    }
}
