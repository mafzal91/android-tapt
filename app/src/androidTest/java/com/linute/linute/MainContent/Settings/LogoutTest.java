package com.linute.linute.MainContent.Settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.linute.linute.LoginAndSignup.PreLoginActivity;
import com.linute.linute.R;
import com.linute.linute.Socket.TaptSocket;
import com.linute.linute.UtilsAndHelpers.LinuteConstants;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LogoutTest {

    @Test
    public void logOutEndsTheSessionAndReturnsToLogin() {
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
        } finally {
            instrumentation.removeMonitor(monitor);
        }

        assertFalse(preferences.getBoolean("isLoggedIn", false));
    }
}
