package com.linute.linute;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import com.linute.linute.API.API_Methods;
import com.linute.linute.API.SessionExpiry;
import com.linute.linute.UtilsAndHelpers.LinuteConstants;
import com.linute.linute.UtilsAndHelpers.VideoClasses.SingleVideoPlaybackManager;

import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

/**
 * Created by QiFeng on 8/11/16.
 */
public class TaptApplication extends MultiDexApplication {

    private static final String TAG = TaptApplication.class.getSimpleName();

    @Override
    protected void attachBaseContext(Context base) {
        MultiDex.install(base);
        super.attachBaseContext(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        final SharedPreferences sharedPreferences = getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, MODE_PRIVATE);

        if(API_Methods.IS_DEV_BUILD){
            boolean isLive = sharedPreferences.getBoolean("is_live", true);
            API_Methods.HOST = isLive ? API_Methods.HOST_LIVE : API_Methods.HOST_DEV;
            API_Methods.VERSION = isLive ? API_Methods.VERSION_LIVE : API_Methods.VERSION_DEV;
        }else{
            API_Methods.HOST = API_Methods.HOST_LIVE;
            API_Methods.VERSION = API_Methods.VERSION_LIVE;
        }

        SingleVideoPlaybackManager.initManager(this);
        SessionExpiry.init(this);

        // An error after a dispose has nowhere to go, like the camera failing while the screen
        // closes. RxJava 1 dropped those, but RxJava 3 would crash the app.
        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                Log.w(TAG, "Error after dispose", e);
            } else {
                Thread thread = Thread.currentThread();
                thread.getUncaughtExceptionHandler().uncaughtException(thread, e);
            }
        });
    }

}
