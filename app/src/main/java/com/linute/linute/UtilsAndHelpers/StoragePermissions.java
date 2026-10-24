package com.linute.linute.UtilsAndHelpers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

/**
 * Storage permissions differ by Android version:
 * - Up to 10 (API 29), saving to Pictures/Tapt needs WRITE_EXTERNAL_STORAGE.
 *   On 10 that only works because the manifest sets requestLegacyExternalStorage.
 * - From 11, an app may create media files in shared storage without a
 *   permission, and WRITE_EXTERNAL_STORAGE is never granted.
 * - From 13 (API 33), READ_EXTERNAL_STORAGE is replaced by READ_MEDIA_IMAGES
 *   and READ_MEDIA_VIDEO.
 */
public final class StoragePermissions {

    /** Permissions to request before saving media. Empty from Android 11. */
    public static final String[] WRITE = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
            ? new String[0]
            : new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

    /** Permissions to request before reading the user's photos and videos. */
    public static final String[] READ = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}
            : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};

    private StoragePermissions() {
    }

    public static boolean canWrite(Context context) {
        return hasAll(context, WRITE);
    }

    public static boolean canRead(Context context) {
        return hasAll(context, READ);
    }

    private static boolean hasAll(Context context, String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
