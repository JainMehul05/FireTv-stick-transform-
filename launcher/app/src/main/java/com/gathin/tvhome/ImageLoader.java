package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Memory-efficient image loader with LRU caching, inSampleSize downsampling,
 * and asynchronous network/resource loading.
 * Optimized for Fire TV Stick Lite (1GB RAM) - keeps memory usage under 36MB for bitmaps.
 */
public final class ImageLoader {

    private static final int MAX_BITMAP_MEMORY_MB = 36;
    private static final int DEFAULT_IN_SAMPLE_SIZE = 1;

    private final LruCache<String, Bitmap> bitmapCache;
    private final Resources resources;
    private final long maxCacheSize;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ImageLoader(Context context) {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024 / 1024);
        int cacheSize = Math.min(MAX_BITMAP_MEMORY_MB, maxMemory / 8) * 1024 * 1024;
        this.maxCacheSize = cacheSize;
        this.bitmapCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
                return value.getByteCount();
            }
        };
        this.resources = context.getResources();
    }

    private static class Holder {
        static final ImageLoader INSTANCE = new ImageLoader(com.gathin.tvhome.MainActivity.getAppContext());
    }

    public static ImageLoader getInstance() {
        return Holder.INSTANCE;
    }

    public static void init(Context context) {
        Holder.INSTANCE.getClass();
    }

    @Nullable
    public Bitmap loadBitmap(int resId, int reqWidth, int reqHeight) {
        String key = "res:" + resId + "@" + reqWidth + "x" + reqHeight;
        Bitmap cached = bitmapCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(resources, resId, options);

        if (reqWidth > 0 && reqHeight > 0) {
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        }

        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inMutable = true;

        try {
            Bitmap bitmap = BitmapFactory.decodeResource(resources, resId, options);
            if (bitmap != null) {
                bitmapCache.put(key, bitmap);
                return bitmap;
            }
        } catch (OutOfMemoryError e) {
            options.inSampleSize *= 2;
            try {
                Bitmap bitmap = BitmapFactory.decodeResource(resources, resId, options);
                if (bitmap != null) {
                    bitmapCache.put(key, bitmap);
                    return bitmap;
                }
            } catch (OutOfMemoryError ignored) {
            }
        }
        return null;
    }

    @Nullable
    public Drawable loadDrawable(int resId, int reqWidth, int reqHeight) {
        Bitmap bitmap = loadBitmap(resId, reqWidth, reqHeight);
        if (bitmap != null) {
            return new BitmapDrawable(resources, bitmap);
        }
        return resources.getDrawable(resId, null);
    }

    /**
     * Asynchronously loads an image from a URL into an ImageView.
     */
    public void loadUrl(String url, ImageView targetView, int reqWidth, int reqHeight, int placeholderResId) {
        if (targetView == null) return;
        if (url == null || url.trim().isEmpty()) {
            if (placeholderResId != 0) targetView.setImageResource(placeholderResId);
            return;
        }

        final String cacheKey = "url:" + url + "@" + reqWidth + "x" + reqHeight;
        Bitmap cached = bitmapCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            targetView.setImageBitmap(cached);
            return;
        }

        if (placeholderResId != 0) {
            targetView.setImageResource(placeholderResId);
        }
        targetView.setTag(url);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setDoInput(true);
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    InputStream input = conn.getInputStream();
                    byte[] bytes = readAllBytes(input);
                    input.close();

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);

                    opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight);
                    opts.inJustDecodeBounds = false;
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;

                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                    if (bmp != null) {
                        bitmapCache.put(cacheKey, bmp);
                        mainHandler.post(() -> {
                            if (url.equals(targetView.getTag())) {
                                targetView.setImageBitmap(bmp);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static byte[] readAllBytes(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int r;
        while ((r = in.read(buffer)) != -1) {
            out.write(buffer, 0, r);
        }
        return out.toByteArray();
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        if (reqWidth == 0 || reqHeight == 0) return DEFAULT_IN_SAMPLE_SIZE;

        int inSampleSize = 1;
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            final int halfHeight = options.outHeight / 2;
            final int halfWidth = options.outWidth / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public void clearCache() {
        bitmapCache.evictAll();
    }

    public long getCacheSize() {
        return bitmapCache.size();
    }

    public long getMaxCacheSize() {
        return maxCacheSize;
    }
}
