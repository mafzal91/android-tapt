package com.linute.linute.Socket;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.linute.linute.API.DeviceInfoSingleton;
import com.linute.linute.UtilsAndHelpers.LinuteConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

import io.socket.client.IO;
import io.socket.engineio.client.transports.WebSocket;

@RunWith(AndroidJUnit4.class)
public class TaptSocketTest {

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Before
    public void setUp() {
        TaptSocket.clear();
        mContext.getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("userToken", "session-token").commit();
        DeviceInfoSingleton.getInstance(mContext).setDeviceToken("push-token");
    }

    @After
    public void tearDown() {
        TaptSocket.clear();
    }

    @Test
    public void credentialsGoInTheAuthPayloadNotTheQuery() {
        TaptSocket.initSocketConnection(mContext);
        IO.Options options = TaptSocket.getInstance().getOptions();

        assertEquals("session-token", options.auth.get("token"));
        assertEquals("push-token", options.auth.get("deviceToken"));
        assertFalse(options.query, options.query.contains("token="));
        assertFalse(options.query, options.query.contains("session-token"));
        assertFalse(options.query, options.query.contains("push-token"));
        assertArrayEquals(new String[]{WebSocket.NAME}, options.transports);
    }

    @Test
    public void aNewPushTokenChangesTheSameAuthMap() {
        TaptSocket.initSocketConnection(mContext);
        IO.Options options = TaptSocket.getInstance().getOptions();
        Map<String, String> auth = options.auth;

        TaptSocket.getInstance().updateDeviceToken("rotated-token");

        // the socket holds on to this map, so a replaced map would never be sent
        assertSame(auth, options.auth);
        assertEquals("rotated-token", auth.get("deviceToken"));
        assertEquals("session-token", auth.get("token"));
    }
}
