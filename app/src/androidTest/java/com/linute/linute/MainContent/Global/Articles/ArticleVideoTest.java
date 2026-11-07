package com.linute.linute.MainContent.Global.Articles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.net.Uri;
import android.view.ContextThemeWrapper;
import android.widget.FrameLayout;

import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.linute.linute.R;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class ArticleVideoTest {

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    @Test
    public void loadsTheVideoInThePlayer() throws Exception {
        Context context = mInstrumentation.getTargetContext();

        // The name has "http" in it so the adapter uses the URL as it is
        File video = new File(context.getCacheDir(), "http-article.mp4");
        try (InputStream in = mInstrumentation.getContext().getAssets().open("sample.mp4")) {
            Files.copy(in, video.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        ArticleElementAdapter adapter = new ArticleElementAdapter(new Article(articleJson(video)));
        AtomicReference<Player> player = new AtomicReference<>();
        mInstrumentation.runOnMainSync(() -> {
            FrameLayout parent = new FrameLayout(new ContextThemeWrapper(context,
                    androidx.appcompat.R.style.Theme_AppCompat_Light));
            RecyclerView.ViewHolder holder =
                    adapter.onCreateViewHolder(parent, ArticleElement.ElementTypes.VIDEO);
            adapter.onBindViewHolder(holder, 1);
            PlayerView view = holder.itemView.findViewById(R.id.exo_player);
            player.set(view.getPlayer());
        });

        long end = System.currentTimeMillis() + 15000;
        AtomicInteger state = new AtomicInteger();
        while (state.get() != Player.STATE_READY) {
            if (System.currentTimeMillis() > end) {
                fail("player state " + state.get());
            }
            Thread.sleep(50);
            mInstrumentation.runOnMainSync(() -> state.set(player.get().getPlaybackState()));
        }

        AtomicInteger duration = new AtomicInteger();
        mInstrumentation.runOnMainSync(() -> {
            duration.set((int) (player.get().getDuration() / 1000));
            player.get().release();
        });
        assertEquals(2, duration.get());
    }

    private static JSONObject articleJson(File video) throws Exception {
        JSONObject element = new JSONObject()
                .put("type", ArticleElement.ElementTypes.VIDEO)
                .put("value", Uri.fromFile(video).toString())
                .put("size", new JSONObject().put("width", 720).put("height", 1280))
                .put("preloader", "")
                .put("attribution", "");
        return new JSONObject()
                .put("title", "title")
                .put("description", "description")
                .put("id", "article")
                .put("author", "author")
                .put("date", "2016-01-01T00:00:00.000Z")
                .put("content", new JSONArray().put(element))
                .put("event", new JSONObject()
                        .put("id", "event")
                        .put("numberOfLikes", 0)
                        .put("numberOfComments", 0)
                        .put("isLiked", false)
                        .put("numberOfImpressions", 0)
                        .put("numberOfShares", 0));
    }
}
