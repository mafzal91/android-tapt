package com.linute.linute.MainContent;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.linute.linute.API.API_Methods;
import com.linute.linute.MainContent.Chat.RoomsActivityFragment;
import com.linute.linute.Socket.TaptSocket;
import com.linute.linute.UtilsAndHelpers.LinuteConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Glide ties each fragment's RequestManager to that fragment. Destroying it by
 * hand in onDestroyView made the second destroy throw ("Cannot unregister not
 * yet registered manager"), which crashed the app when a screen's view was
 * destroyed after it had been recreated, e.g. log out after visiting Messenger.
 */
@RunWith(AndroidJUnit4.class)
public class GlideLifecycleTest {

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private OkHttpClient mRealClient;
    private String mRealHost;

    @Before
    public void setUp() {
        // MainActivity asks for these on start, and the dialog would pause it
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        for (String permission : new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}) {
            automation.grantRuntimePermission(mContext.getPackageName(), permission);
        }

        mContext.getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean("isLoggedIn", true)
                .putString("userToken", "token")
                .putString("userID", "user")
                .putString("collegeId", "college")
                .putString("collegeName", "College")
                .commit();

        // every REST call answers an empty 200, and the socket has nowhere to connect
        mRealClient = API_Methods.CLIENT;
        API_Methods.CLIENT = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("")
                .body(ResponseBody.create(API_Methods.JSON, "{}"))
                .build()).build();
        mRealHost = API_Methods.HOST;
        API_Methods.HOST = "127.0.0.1";
        TaptSocket.clear();
    }

    @After
    public void tearDown() {
        API_Methods.CLIENT = mRealClient;
        API_Methods.HOST = mRealHost;
        TaptSocket.clear();
        mContext.getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void closingAfterTheFeedViewWasRecreatedDoesNotCrash() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);

        // Messenger replaces the feed (its view is destroyed), back recreates it
        scenario.onActivity(activity -> {
            activity.addFragmentToContainer(RoomsActivityFragment.getInstance(), RoomsActivityFragment.TAG);
            activity.getSupportFragmentManager().executePendingTransactions();
            activity.getSupportFragmentManager().popBackStackImmediate();
        });

        // destroys the feed's view a second time, then the fragment
        scenario.close();
    }
}
