package com.linute.linute.Socket;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import androidx.core.app.ActivityCompat;
import android.util.Log;

import com.linute.linute.API.API_Methods;
import com.linute.linute.API.DeviceInfoSingleton;
import com.linute.linute.UtilsAndHelpers.LinuteConstants;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.WebSocket;

/**
 * Created by QiFeng on 9/29/16.
 */
public class TaptSocket {

    private static final String TAG = TaptSocket.class.getSimpleName();
    private Socket mSocket;
    private IO.Options mOptions;

    // counts how many Activities are using this connection
    // in Activity onResume, increment this
    // in Activity onStop, decrement this
    // if 0 connections are left, we disconnect from socket
    private int mConnections;

    private static TaptSocket mTaptSocket;


    private TaptSocket(Context context) {
        try {
            IO.Options op = new IO.Options();
            DeviceInfoSingleton device = DeviceInfoSingleton.getInstance(context);
            String token = context.getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE).getString("userToken", null);
            if (token == null) throw new NullPointerException("No user token");

            JSONArray coord = new JSONArray();

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                Location loca = ((LocationManager) context.getSystemService(Context.LOCATION_SERVICE)).getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                if (loca != null) {
                    try {
                        coord.put(loca.getLatitude());
                        coord.put(loca.getLongitude());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            op.query = "token=" + encode(token) +
                    "&deviceToken=" + encode(device.getDeviceToken()) +
                    "&udid=" + encode(device.getUdid()) +
                    "&version=" + encode(device.getVersionName()) +
                    "&build=" + encode(device.getVersionCode()) +
                    "&os=" + encode(device.getOS()) +
                    "&platform=" + encode(device.getType()) +
                    "&api=" + encode(API_Methods.VERSION) +
                    "&model=" + encode(device.getModel()) +
                    "&geo=" + encode(coord.toString());

            op.reconnectionDelay = 5;
            op.secure = true;
            op.transports = new String[]{WebSocket.NAME};

            mOptions = op;
            mSocket = IO.socket(API_Methods.getURL(), op);/*R.string.DEV_SOCKET_URL*/
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    // Query values such as the device model ("Pixel 8") and geo ("[40.7,-74.0]")
    // contain characters that are not valid in a URI.
    private static String encode(String value) {
        if (value == null) return "";
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    // NOTE: CLEAR SOCKET IF PERSON IS LOGGED OUT
    public static void clear() {
        mTaptSocket = null;
    }

    public static void initSocketConnection(Context context) {
        if (mTaptSocket == null)
            mTaptSocket = new TaptSocket(context);
    }

    public void connectSocket() {
        if (mSocket == null) return;
        if (!mSocket.connected()) {
            Log.d(TAG, "connectSocket: ");
            mSocket.connect();
        }
        mConnections++;
    }

    public void disconnectSocket() {
        if (mSocket == null) return;

        mConnections--;
        // disconnect when no more activities need it
        if (mConnections < 1) {
            Log.d(TAG, "disconnectSocket: ");
            mSocket.disconnect();
        }
    }


    public void forceDisconnect() {
        mConnections = 0;
        mSocket.disconnect();
        Log.d(TAG, "forceDisconnect: ");
    }


    // The server registers the push token from the handshake query, so a new
    // token only reaches it on the next connection.
    public void updateDeviceToken(String deviceToken) {
        if (mSocket == null) return;
        // The Manager reads these options again on every (re)connect.
        mOptions.query = mOptions.query.replaceFirst("deviceToken=[^&]*", "deviceToken=" + encode(deviceToken));
        if (mSocket.connected()) {
            mSocket.disconnect();
            mSocket.connect();
        }
    }

    public static TaptSocket getInstance() {
        return mTaptSocket;
    }

    public void on(String event, Emitter.Listener listener) {
        mSocket.on(event, listener);
    }

    public void off(String event, Emitter.Listener listener) {
        mSocket.off(event, listener);
    }

    // In socket.io-client 2.x reconnect events come from the Manager, not the Socket.
    public void onReconnect(Emitter.Listener listener) {
        mSocket.io().on(Manager.EVENT_RECONNECT, listener);
    }

    public void offReconnect(Emitter.Listener listener) {
        mSocket.io().off(Manager.EVENT_RECONNECT, listener);
    }

    public void emit(String event, Object arg) {
        mSocket.emit(event, arg);
    }

    public void emit(String event) {
        mSocket.emit(event);
    }

    public boolean socketConnected() {
        return mSocket != null && mSocket.connected();
    }

}
