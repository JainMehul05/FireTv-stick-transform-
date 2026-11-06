package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Memory-efficient image loader with LRU caching and inSampleSize downsampling.
 * Optimized for Fire TV Stick Lite (1GB RAM) - keeps memory usage under 48MB for bitmaps.
 */
public final class ImageLoader {

    private static final int MAX_BITMAP_MEMORY_MB = 48; // 48MB max for bitmap cache
    private static final int DEFAULT_IN_SAMPLE_SIZE = 1;

    private final LruCache<String, Bitmap> bitmapCache;
    private final Resources resources;
    private final long maxCacheSize;

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

    /**
     * Initialize the ImageLoader with application context.
     * Must be called once during application startup.
     */
    public static void init(Context context) {
        // Force initialization of singleton
        Holder.INSTANCE.getClass();
    }

    /**
     * Load a drawable resource as a Bitmap with downsampling.
     * @param resId Resource ID of the drawable
     * @param reqWidth Target width in pixels (0 for no constraint)
     * @param reqHeight Target height in pixels (0 for no constraint)
     * @return Bitmap or null if failed
     */
    @Nullable
    public Bitmap loadBitmap(int resId, int reqWidth, int reqHeight) {
        String key = "res:" + resId + "@" + reqWidth + "x" + reqHeight;
        
        // Check cache first
        Bitmap cached = bitmapCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        // Decode with inSampleSize for memory efficiency
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(resources, resId, options);

        if (reqWidth > 0 && reqHeight > 0) {
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        }

        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565; // 50% memory vs ARGB_8888
        options.inMutable = true;

        try {
            Bitmap bitmap = BitmapFactory.decodeResource(resources, resId, options);
            if (bitmap != null) {
                bitmapCache.put(key, bitmap);
                return bitmap;
            }
        } catch (OutOfMemoryError e) {
            // Fallback: try with larger inSampleSize
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

    /**
     * Load a drawable resource as a Drawable with downsampling.
     */
    @Nullable
    public Drawable loadDrawable(int resId, int reqWidth, int reqHeight) {
        Bitmap bitmap = loadBitmap(resId, reqWidth, reqHeight);
        if (bitmap != null) {
            return new BitmapDrawable(resources, bitmap);
        }
        // Fallback to direct resource drawable
        return resources.getDrawable(resId, null);
    }

    /**
     * Calculate optimal inSampleSize for downsampling.
     */
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

    /**
     * Clear the bitmap cache.
     */
    public void clearCache() {
        bitmapCache.evictAll();
    }

    /**
     * Get current cache size in bytes.
     */
    public long getCacheSize() {
        return bitmapCache.size();
    }

    /**
     * Get max cache size in bytes.
     */
    public long getMaxCacheSize() {
        return maxCacheSize;
    }
}