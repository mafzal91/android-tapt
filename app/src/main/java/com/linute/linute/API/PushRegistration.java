package com.linute.linute.API;

/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.linute.linute.UtilsAndHelpers.LinuteConstants;

public final class PushRegistration {

    private static final String TAG = "Registration";

    private PushRegistration() {
    }

    /**
     * Gets the FCM token and saves it. onDone runs on the main thread, even if that failed.
     */
    public static void register(Context context, final Runnable onDone) {
        final Context appContext = context.getApplicationContext();
        final SharedPreferences sharedPreferences = appContext.getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE);

        if (FirebaseApp.getApps(appContext).isEmpty()) {
            // Built without google-services.json: no push, but don't block launch.
            Log.w(TAG, "Firebase is not configured; push notifications are disabled");
            if (sharedPreferences.getString(QuickstartPreferences.OUR_TOKEN, null) == null) {
                sharedPreferences.edit().putString(QuickstartPreferences.OUR_TOKEN, "").apply();
            }
            onDone.run();
            return;
        }

        // Network on first call, cached afterwards. Rotations arrive in
        // MyFirebaseMessagingService.onNewToken.
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String token = task.getResult();
                sharedPreferences.edit().putString(QuickstartPreferences.OUR_TOKEN, token).apply();
                DeviceInfoSingleton.getInstance(appContext).setDeviceToken(token);
            } else {
                Log.d(TAG, "Failed to get FCM token", task.getException());
            }
            onDone.run();
        });
    }
}
