package com.linute.linute.MainContent.DiscoverFragment;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.ContextThemeWrapper;
import android.widget.ImageView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.linute.linute.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class ImageLoadingTest {

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = new ContextThemeWrapper(mInstrumentation.getTargetContext(),
            androidx.appcompat.R.style.Theme_AppCompat_Light);

    @Test
    public void sharedImageIsTheHeaderAboveThePost() throws Exception {
        File image = writeImage("share-post.png", 400, 200, Color.BLUE);
        File profile = writeImage("share-profile.png", 50, 50, Color.RED);

        Post post = new Post(Uri.fromFile(image).toString(), "post", "user", "Name");
        setUserImage(post, Uri.fromFile(profile).toString());

        Result result = share(post);

        assertNull(result.error.get());
        Bitmap shared = BitmapFactory.decodeFile(result.uri.get().getPath());
        assertNotNull(shared);
        // The post is scaled to the header's width and sits at the bottom
        int postHeight = shared.getWidth() / 2;
        assertTrue(shared.getHeight() > postHeight);
        int pixel = shared.getPixel(shared.getWidth() / 2, shared.getHeight() - postHeight / 2);
        // It's saved as a JPEG, so the colour can be slightly off
        assertTrue(Integer.toHexString(pixel), Color.blue(pixel) > 240 && Color.red(pixel) < 16);
    }

    @Test
    public void shareFailsWhenTheImageCannotLoad() throws Exception {
        File profile = writeImage("share-profile.png", 50, 50, Color.RED);
        Post post = new Post(Uri.fromFile(new File(mContext.getCacheDir(), "missing.png")).toString(), "post", "user", "Name");
        setUserImage(post, Uri.fromFile(profile).toString());

        Result result = share(post);

        assertNotNull(result.error.get());
        assertNull(result.uri.get());
    }

    @Test
    public void typingIndicatorAnimates() throws Exception {
        AtomicReference<ImageView> view = new AtomicReference<>();
        mInstrumentation.runOnMainSync(() -> {
            ImageView imageView = new ImageView(mContext);
            imageView.layout(0, 0, 100, 100);
            view.set(imageView);
            Glide.with(mContext.getApplicationContext())
                    .load(R.drawable.typing_three_dots_android)
                    .into(imageView);
        });

        long end = System.currentTimeMillis() + 10000;
        while (true) {
            AtomicReference<Drawable> drawable = new AtomicReference<>();
            mInstrumentation.runOnMainSync(() -> drawable.set(view.get().getDrawable()));
            if (drawable.get() instanceof GifDrawable) {
                // A view that's on screen does this. The GIF runs if Glide has started it.
                mInstrumentation.runOnMainSync(() -> drawable.get().setVisible(true, false));
                assertTrue(((GifDrawable) drawable.get()).isRunning());
                return;
            }
            if (System.currentTimeMillis() > end) {
                fail("drawable is " + drawable.get());
            }
            Thread.sleep(50);
        }
    }

    private Result share(Post post) throws InterruptedException {
        Result result = new Result();
        mInstrumentation.runOnMainSync(() -> post.getShareUri(mContext, result));
        assertTrue(result.done.await(15, TimeUnit.SECONDS));
        return result;
    }

    private File writeImage(String name, int width, int height, int color) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        File file = new File(mContext.getCacheDir(), name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return file;
    }

    // Only the server's JSON sets this
    private static void setUserImage(Post post, String image) throws Exception {
        Field field = Post.class.getDeclaredField("mUserImage");
        field.setAccessible(true);
        field.set(post, image);
    }

    private static class Result implements BaseFeedItem.OnUriReadyListener {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Uri> uri = new AtomicReference<>();
        final AtomicReference<Exception> error = new AtomicReference<>();

        @Override
        public void onUriReady(Uri ready) {
            uri.set(ready);
            done.countDown();
        }

        @Override
        public void onUriProgress(int progress) {
        }

        @Override
        public void onUriFail(Exception e) {
            error.set(e);
            done.countDown();
        }
    }
}
