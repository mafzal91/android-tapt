package com.linute.linute.UtilsAndHelpers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

/**
 * From Android 12 a request for ACCESS_FINE_LOCATION alone is ignored; it
 * must come with ACCESS_COARSE_LOCATION, and the user may grant only the
 * coarse one. The app only reads NETWORK_PROVIDER locations, so either is
 * enough.
 */
public final class LocationPermissions {

    public static final String[] REQUEST = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private LocationPermissions() {
    }

    public static boolean granted(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** True if a permission result granted either location permission. */
    public static boolean granted(String[] permissions, int[] grantResults) {
        for (int i = 0; i < permissions.length && i < grantResults.length; i++) {
            if ((Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i])
                    || Manifest.permission.ACCESS_COARSE_LOCATION.equals(permissions[i]))
                    && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }
}
