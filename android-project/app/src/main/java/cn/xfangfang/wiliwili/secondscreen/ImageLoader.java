package cn.xfangfang.wiliwili.secondscreen;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared async image downloader + memory cache for the second screen.
 * Concurrent requests for the same URL share one download and every caller is
 * notified on the main thread.
 */
final class ImageLoader {
    interface Callback {
        void onLoaded(String key, Bitmap bitmap);
    }

    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(MAX_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, List<Callback>> PENDING = new HashMap<>();

    private ImageLoader() {
    }

    /** Normalized cache key: protocol-relative URLs fixed, optional size suffix applied. */
    static String key(String url, String suffix) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        String key = url.startsWith("//") ? "https:" + url : url;
        if (suffix != null && !suffix.isEmpty() && !key.contains("@")) {
            key += suffix;
        }
        return key;
    }

    static void load(String url, Callback callback) {
        if (url == null || url.isEmpty()) {
            return;
        }
        final String key = url.startsWith("//") ? "https:" + url : url;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            callback.onLoaded(key, cached);
            return;
        }
        synchronized (PENDING) {
            List<Callback> callbacks = PENDING.get(key);
            if (callbacks != null) {
                callbacks.add(callback);
                return;
            }
            callbacks = new ArrayList<>(2);
            callbacks.add(callback);
            PENDING.put(key, callbacks);
        }
        POOL.execute(() -> {
            final Bitmap bitmap = download(key);
            if (bitmap != null) {
                CACHE.put(key, bitmap);
            }
            final List<Callback> callbacks;
            synchronized (PENDING) {
                callbacks = PENDING.remove(key);
            }
            MAIN.post(() -> {
                if (callbacks == null || bitmap == null) {
                    return;
                }
                for (Callback listener : callbacks) {
                    listener.onLoaded(key, bitmap);
                }
            });
        });
    }

    private static Bitmap download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "wiliwili");
            connection.setRequestProperty("Referer", "https://www.bilibili.com/");
            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
