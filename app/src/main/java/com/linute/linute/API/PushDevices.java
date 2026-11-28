package com.linute.linute.API;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.linute.linute.UtilsAndHelpers.LinuteConstants;

import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Tells the server which push token this phone has, over REST. The socket
 * handshake also sends it, but FCM usually rotates the token while the app is
 * in the background with no socket, and pushes would go to the old one until
 * the app was next opened.
 */
public final class PushDevices {

    private static final String TAG = "PushDevices";

    static final String REGISTER_WORK = "register-push-device";

    private PushDevices() {
    }

    /**
     * Sends the saved push token with PUT /devices once there is a network.
     * Only the latest call runs, and repeating it is safe.
     */
    public static void register(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(RegisterWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(REGISTER_WORK, ExistingWorkPolicy.REPLACE, request);
    }

    /**
     * Removes this phone's push token from the account with DELETE /devices.
     * Call it before clearing the login, which the request needs. It doesn't
     * wait for the answer. If the request fails, the server drops the token
     * when anyone next logs in on this phone.
     */
    public static void unregister(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE);
        String token = prefs.getString("userToken", null);
        if (TextUtils.isEmpty(token)) return;

        Map<String, Object> body = new HashMap<>();
        body.put("udid", DeviceInfoSingleton.getInstance(context).getUdid());
        API_Methods.delete("devices", API_Methods.getMainHeader(token), body, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.w(TAG, "DELETE devices failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful()) Log.w(TAG, "DELETE devices: " + response.code());
                response.close();
            }
        });
    }

    public static class RegisterWorker extends Worker {

        public RegisterWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            Context context = getApplicationContext();
            // read when it runs, so the login token isn't stored with the work
            SharedPreferences prefs = context.getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE);
            String token = prefs.getString("userToken", null);
            String deviceToken = prefs.getString(QuickstartPreferences.OUR_TOKEN, null);
            if (!prefs.getBoolean("isLoggedIn", false) || TextUtils.isEmpty(token) || TextUtils.isEmpty(deviceToken)) {
                // the handshake after the next login sends it
                return Result.success();
            }

            DeviceInfoSingleton device = DeviceInfoSingleton.getInstance(context);
            Map<String, Object> body = new HashMap<>();
            body.put("deviceToken", deviceToken);
            body.put("udid", device.getUdid());
            body.put("platform", device.getType());

            Request request = new Request.Builder()
                    .url(API_Methods.getURL("devices"))
                    .headers(Headers.of(API_Methods.getMainHeader(token)))
                    .put(RequestBody.create(API_Methods.JSON, new JSONObject(body).toString()))
                    .build();

            try (Response response = API_Methods.CLIENT.newCall(request).execute()) {
                if (response.isSuccessful()) return Result.success();
                Log.w(TAG, "PUT devices: " + response.code());
                // A 401 has already ended the session. Other 4xx won't change on a retry.
                return response.code() >= 500 ? Result.retry() : Result.failure();
            } catch (IOException e) {
                return Result.retry();
            }
        }
    }
}
