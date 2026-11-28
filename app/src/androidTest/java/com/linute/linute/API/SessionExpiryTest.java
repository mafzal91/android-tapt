package com.linute.linute.API;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.socket.engineio.client.EngineIOException;

@RunWith(AndroidJUnit4.class)
public class SessionExpiryTest {

    @Test
    public void expiredAndInvalidCodesAreAuthErrors() throws Exception {
        assertTrue(SessionExpiry.isSocketAuthError(rejection("Authentication error", "TOKEN_EXPIRED")));
        assertTrue(SessionExpiry.isSocketAuthError(rejection("Authentication error", "TOKEN_INVALID")));
        // the code decides, whatever the message says
        assertTrue(SessionExpiry.isSocketAuthError(rejection("Token expired", "TOKEN_EXPIRED")));
    }

    @Test
    public void theMessageAloneStillCountsForServersWithoutCodes() throws Exception {
        assertTrue(SessionExpiry.isSocketAuthError(new JSONObject().put("message", "Authentication error")));
    }

    @Test
    public void otherRejectionsAndTransportFailuresAreNot() throws Exception {
        assertFalse(SessionExpiry.isSocketAuthError(rejection("Not allowed", "RATE_LIMITED")));
        assertFalse(SessionExpiry.isSocketAuthError(new JSONObject().put("message", "Not allowed")));
        assertFalse(SessionExpiry.isSocketAuthError(new EngineIOException("websocket error")));
        assertFalse(SessionExpiry.isSocketAuthError(null));
    }

    private static JSONObject rejection(String message, String code) throws Exception {
        return new JSONObject()
                .put("message", message)
                .put("data", new JSONObject().put("code", code));
    }
}
