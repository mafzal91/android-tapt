package com.linute.linute;

import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.linute.linute.LoginAndSignup.PreLoginActivity;
import com.linute.linute.UtilsAndHelpers.LinuteConstants;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LaunchActivityTest {

    @Test
    public void movesOnToLoginWhenLoggedOut() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.getTargetContext()
                .getSharedPreferences(LinuteConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();

        Instrumentation.ActivityMonitor monitor =
                instrumentation.addMonitor(PreLoginActivity.class.getName(), null, false);
        try (ActivityScenario<LaunchActivity> ignored = ActivityScenario.launch(LaunchActivity.class)) {
            Activity login = monitor.waitForActivityWithTimeout(15000);
            assertNotNull(login);
            login.finish();
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }
}
