package com.linute.linute.API;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.linute.linute.UtilsAndHelpers.LinuteConstants;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

@RunWith(AndroidJUnit4.class)
public class PushDevicesTest {

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final List<Request> mRequests = new CopyOnWriteArrayList<>();
    private volatile int mResponseCode = 200;
    private OkHttpClient mRealClient;

    private final Interceptor mServer = new Interceptor() {
        @Override
        public Response intercept(Chain chain) {
            mRequests.add(chain.request());
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(mResponseCode)
                    .message("")
                    .body(ResponseBody.create(API_Methods.JSON, "{\"ok\":true}"))
                    .build();
        }
    };

    @Before
    public void setUp() throws Exception {
        WorkManager workManager = WorkManager.getInstance(mContext);
        workManager.cancelUniqueWork(PushDevices.REGISTER_WORK).getResult().get();
        workManager.pruneWork().getResult().get();
        mRealClient = API_Methods.CLIENT;
        API_Methods.CLIENT = new OkHttpClient.Builder().addInterceptor(mServer).build();
    }

    @After
    public void tearDown() throws Exception {
        WorkManager.getInstance(mContext).cancelUniqueWork(PushDevices.REGISTER_WORK).getResult().get();
        API_Methods.CLIENT = mRealClient;
        prefs().edit().clear().commit();
    }

    @Test
    public void putsThePushTokenForThisPhone() throws Exception {
        logIn("session-token", "push-token");

        PushDevices.register(mContext);

        assertEquals(WorkInfo.State.SUCCEEDED, waitForRun().getState());
        assertEquals(1, mRequests.size());
        Request request = mRequests.get(0);
        assertEquals("PUT", request.method());
        assertTrue(request.url().toString(), request.url().encodedPath().endsWith("/devices/"));
        assertEquals("Basic " + Base64.encodeToString("session-token".getBytes(), Base64.NO_WRAP),
                request.header("authorization"));
        JSONObject body = body(request);
        assertEquals("push-token", body.getString("deviceToken"));
        assertEquals(DeviceInfoSingleton.getInstance(mContext).getUdid(), body.getString("udid"));
        assertEquals("android", body.getString("platform"));
        long sent = prefs().getLong(PushDevices.LAST_SENT, 0);
        assertTrue(System.currentTimeMillis() - sent < 60000);
    }

    @Test
    public void refreshesAtMostOnceADay() throws Exception {
        logIn("session-token", "push-token");

        PushDevices.refreshIfStale(mContext);
        WorkInfo first = waitForRun();
        assertEquals(WorkInfo.State.SUCCEEDED, first.getState());
        assertEquals(1, mRequests.size());

        PushDevices.refreshIfStale(mContext);

        // REPLACE would have queued a new job
        assertEquals(first.getId(), WorkManager.getInstance(mContext)
                .getWorkInfosForUniqueWork(PushDevices.REGISTER_WORK).get().get(0).getId());
        assertEquals(1, mRequests.size());
    }

    @Test
    public void refreshesAgainAfterADay() throws Exception {
        logIn("session-token", "push-token");
        prefs().edit().putLong(PushDevices.LAST_SENT,
                System.currentTimeMillis() - PushDevices.REFRESH_INTERVAL - 1000).commit();

        PushDevices.refreshIfStale(mContext);

        assertEquals(WorkInfo.State.SUCCEEDED, waitForRun().getState());
        assertEquals(1, mRequests.size());
    }

    @Test
    public void doesNotRefreshWhenLoggedOut() throws Exception {
        prefs().edit().clear().putString(QuickstartPreferences.OUR_TOKEN, "push-token").commit();

        PushDevices.refreshIfStale(mContext);

        assertTrue(WorkManager.getInstance(mContext)
                .getWorkInfosForUniqueWork(PushDevices.REGISTER_WORK).get().isEmpty());
    }

    @Test
    public void logOutForgetsTheLastRefreshSoTheNextLoginSends() throws Exception {
        logIn("session-token", "push-token");
        prefs().edit().putLong(PushDevices.LAST_SENT, System.currentTimeMillis()).commit();

        PushDevices.unregister(mContext);

        assertFalse(prefs().contains(PushDevices.LAST_SENT));
    }

    @Test
    public void doesNothingWhenLoggedOut() throws Exception {
        prefs().edit().clear().putString(QuickstartPreferences.OUR_TOKEN, "push-token").commit();

        PushDevices.register(mContext);

        assertEquals(WorkInfo.State.SUCCEEDED, waitForRun().getState());
        assertEquals(0, mRequests.size());
    }

    @Test
    public void triesAgainAfterAServerError() throws Exception {
        logIn("session-token", "push-token");
        mResponseCode = 503;

        PushDevices.register(mContext);

        WorkInfo info = waitForRun();
        assertEquals(WorkInfo.State.ENQUEUED, info.getState());
        assertEquals(1, mRequests.size());
    }

    @Test
    public void givesUpOnAClientError() throws Exception {
        logIn("session-token", "push-token");
        mResponseCode = 400;

        PushDevices.register(mContext);

        assertEquals(WorkInfo.State.FAILED, waitForRun().getState());
    }

    private void logIn(String token, String deviceToken) {
        prefs().edit()
                .putBoolean("isLoggedIn", true)
                .putString("userToken", token)
                .putString(QuickstartPreferences.OUR_TOKEN, deviceToken)
                .commit();
    }

    private android.content.SharedPreferences prefs() {
        return mContext.getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE);
    }

    // waits until the work has run once: finished, or waiting to retry
    private WorkInfo waitForRun() throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            List<WorkInfo> infos = WorkManager.getInstance(mContext)
                    .getWorkInfosForUniqueWork(PushDevices.REGISTER_WORK).get();
            if (!infos.isEmpty()) {
                WorkInfo info = infos.get(0);
                if (info.getState().isFinished()
                        || (info.getState() == WorkInfo.State.ENQUEUED && info.getRunAttemptCount() > 0)) {
                    return info;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("register work didn't run");
    }

    private static JSONObject body(Request request) throws IOException, org.json.JSONException {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return new JSONObject(buffer.readUtf8());
    }
}
