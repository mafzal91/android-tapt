package com.linute.linute.MainContent.DiscoverFragment;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.linute.linute.API.API_Methods;
import com.linute.linute.Socket.TaptSocket;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Tells the server which app a post was shared to. The share sheet calls this after the user picks
 * one. A service wouldn't do: by then the app is in the background, and Android refuses to start it.
 */
public class ShareReceiver extends BroadcastReceiver {

    public static final String TAG = ShareReceiver.class.getSimpleName();

    public static final int REQ_CODE = 25;
    public static final String EXTRA_POST_ID = "pos_id";


    @Override
    public void onReceive(Context context, Intent intent) {
        String mPostId = intent.getStringExtra(EXTRA_POST_ID);
        ComponentName mChosenComponent = intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT);

        try {
            JSONObject object = new JSONObject();

            object.put("post", mPostId);
            if (mChosenComponent != null) {
                object.put("type", mChosenComponent.getPackageName());
            } else {
                object.put("type", "undefined");
            }
            TaptSocket.getInstance().emit(API_Methods.VERSION + ":posts:social", object);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
