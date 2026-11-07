package com.linute.linute;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@RunWith(AndroidJUnit4.class)
public class RxErrorHandlingTest {

    // Without the handler in TaptApplication this ends the process, and the run with it
    @Test
    public void errorAfterDisposeDoesNotCrashTheApp() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        Disposable disposable = Completable.fromAction(() -> {
            started.countDown();
            try {
                Thread.sleep(10000);
            } finally {
                finished.countDown();
            }
        }).subscribeOn(Schedulers.io()).subscribe(() -> {
        }, e -> {
        });

        assertTrue(started.await(5, TimeUnit.SECONDS));
        disposable.dispose();
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        Thread.sleep(500);
    }
}
