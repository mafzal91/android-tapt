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

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Login tokens expire after 7 days. The server then answers REST calls with
 * 401 and rejects the socket handshake with "Authentication error". Either
 * one logs the user out and returns to the login screen.
 */
public final class SessionExpiry {

    // the message socket.js sends when the handshake token is invalid or expired
    public static final String SOCKET_AUTH_ERROR = "Authentication error";

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
