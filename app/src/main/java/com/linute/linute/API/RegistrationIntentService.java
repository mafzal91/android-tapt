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


import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.linute.linute.UtilsAndHelpers.LinuteConstants;

public class RegistrationIntentService extends IntentService {

    private static final String TAG = "Registration";

    public RegistrationIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences sharedPreferences = getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, MODE_PRIVATE);

        if (FirebaseApp.getApps(this).isEmpty()) {
            // Built without google-services.json: no push, but don't block launch.
            Log.w(TAG, "Firebase is not configured; push notifications are disabled");
            if (sharedPreferences.getString(QuickstartPreferences.OUR_TOKEN, null) == null) {
                sharedPreferences.edit().putString(QuickstartPreferences.OUR_TOKEN, "").apply();
            }
        } else {
            try {
                // Network on first call, cached afterwards. Rotations arrive in
                // MyFirebaseMessagingService.onNewToken.
                String token = Tasks.await(FirebaseMessaging.getInstance().getToken());
                sharedPreferences.edit().putString(QuickstartPreferences.OUR_TOKEN, token).apply();
                DeviceInfoSingleton.getInstance(this).setDeviceToken(token);
            } catch (Exception e) {
                Log.d(TAG, "Failed to get FCM token", e);
            }
        }

        Intent registrationComplete = new Intent(QuickstartPreferences.REGISTRATION_COMPLETE);
        LocalBroadcastManager.getInstance(RegistrationIntentService.this).sendBroadcast(registrationComplete);
    }
}
