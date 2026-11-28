package com.linute.linute.API;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.linute.linute.LoginAndSignup.PreLoginActivity;
import com.linute.linute.Socket.TaptSocket;
import com.linute.linute.UtilsAndHelpers.LinuteConstants;
import com.linute.linute.UtilsAndHelpers.Utils;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Login tokens expire after 7 days. The server then answers REST calls with
 * 401 and rejects the socket handshake with a TOKEN_EXPIRED or TOKEN_INVALID
 * code. Either one logs the user out and returns to the login screen.
 */
public final class SessionExpiry {

    // the message socket.js sends when the handshake token is invalid or expired
    public static final String SOCKET_AUTH_ERROR = "Authentication error";

    // the codes it puts in the error's data: a correctly signed token past its
    // expiry, or anything else (missing, malformed, deleted user)
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String TOKEN_INVALID = "TOKEN_INVALID";

    private static Context sAppContext;

    private SessionExpiry() {
    }

    public static void init(Context context) {
        sAppContext = context.getApplicationContext();
    }

    public static final Interceptor INTERCEPTOR = new Interceptor() {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());
            if (response.code() == 401) {
                expire();
            }
            return response;
        }
    };

    /**
     * Whether a socket connect_error argument is the server refusing the login
     * token. Servers without the codes only send the message. Transport
     * failures arrive as exceptions instead, and the socket retries those.
     */
    public static boolean isSocketAuthError(Object error) {
        if (!(error instanceof JSONObject)) return false;
        JSONObject json = (JSONObject) error;
        JSONObject data = json.optJSONObject("data");
        String code = data == null ? "" : data.optString("code");
        return TOKEN_EXPIRED.equals(code)
                || TOKEN_INVALID.equals(code)
                || SOCKET_AUTH_ERROR.equals(json.optString("message"));
    }

    public static void expire() {
        if (sAppContext == null) return;

        SharedPreferences pref = sAppContext.getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE);
        synchronized (SessionExpiry.class) {
            // only once, and only when there is a session to end
            if (!pref.getBoolean("isLoggedIn", false)) return;
            Utils.resetUserInformation(pref);
        }
        Utils.deleteTempSharedPreference(sAppContext.getSharedPreferences(LinuteConstants.SHARED_TEMP_NAME, Context.MODE_PRIVATE));

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                TaptSocket socket = TaptSocket.getInstance();
                if (socket != null) {
                    socket.forceDisconnect();
                    TaptSocket.clear();
                }

                Toast.makeText(sAppContext, "Your session has expired. Please log in again.", Toast.LENGTH_LONG).show();

                Intent i = new Intent(sAppContext, PreLoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                sAppContext.startActivity(i);
            }
        });
    }
}
