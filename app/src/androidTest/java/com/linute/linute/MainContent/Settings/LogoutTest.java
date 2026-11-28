package com.linute.linute.MainContent.Settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Base64;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.linute.linute.API.API_Methods;
import com.linute.linute.API.DeviceInfoSingleton;
import com.linute.linute.LoginAndSignup.PreLoginActivity;
import com.linute.linute.R;
import com.linute.linute.Socket.TaptSocket;
import com.linute.linute.UtilsAndHelpers.LinuteConstants;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

@RunWith(AndroidJUnit4.class)
public class LogoutTest {

    @Test
    public void logOutEndsTheSessionAndReturnsToLogin() throws Exception {
        LinkedBlockingQueue<Request> requests = new LinkedBlockingQueue<>();
        OkHttpClient realClient = API_Methods.CLIENT;
        API_Methods.CLIENT = new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.add(chain.request());
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("")
                    .body(ResponseBody.create(API_Methods.JSON, "{\"ok\":true}"))
                    .build();
        }).build();

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        SharedPreferences preferences =
                context.getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE);
        preferences.edit().putBoolean("isLoggedIn", true).putString("userToken", "token").commit();
        TaptSocket.initSocketConnection(context);

        Instrumentation.ActivityMonitor monitor =
                instrumentation.addMonitor(PreLoginActivity.class.getName(), null, false);
        try (ActivityScenario<SettingActivity> scenario = ActivityScenario.launch(SettingActivity.class)) {
            scenario.onActivity(activity -> {
                PreferenceFragment settings = (PreferenceFragment)
                        activity.getFragmentManager().findFragmentById(R.id.setting_fragment);
                Preference logOut = settings.findPreference("logout");
                logOut.getOnPreferenceClickListener().onPreferenceClick(logOut);
            });

            Activity login = monitor.waitForActivityWithTimeout(15000);
            assertNotNull(login);
            login.finish();
            // the push token is removed with the login token the user had
            Request request = requests.poll(5, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("DELETE", request.method());
            assertTrue(request.url().toString(), request.url().encodedPath().endsWith("/devices/"));
            assertEquals("Basic " + Base64.encodeToString("token".getBytes(), Base64.NO_WRAP),
                    request.header("authorization"));
            Buffer body = new Buffer();
            request.body().writeTo(body);
            assertEquals(DeviceInfoSingleton.getInstance(context).getUdid(),
                    new JSONObject(body.readUtf8()).getString("udid"));
        } finally {
            instrumentation.removeMonitor(monitor);
            API_Methods.CLIENT = realClient;
        }

        assertFalse(preferences.getBoolean("isLoggedIn", false));
    }
}
