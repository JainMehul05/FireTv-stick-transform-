package com.gathin.tvhome.data.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gathin.tvhome.data.model.MediaItem;
import com.gathin.tvhome.data.model.ViewingHistoryItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unified Viewing History and Smart Resume Repository for Fire TV.
 * Manages verified provider watch history and user-initiated launcher playback activity.
 * Strictly guarantees that fabricated or unverified catalogue items never appear in Continue Watching.
 */
public class ViewingHistoryRepository {
    private static final String TAG = "ViewingHistoryRepo";
    private static final String PREF_NAME = "tvhome_viewing_history_v3";
    private static final String KEY_HISTORY_DATA = "history_json";

    private static ViewingHistoryRepository sInstance;

    public static synchronized ViewingHistoryRepository getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ViewingHistoryRepository(context.getApplicationContext());
        }
        return sInstance;
    }

    public interface HistoryCallback {
        void onHistoryLoaded(List<ViewingHistoryItem> items);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Map<String, ViewingHistoryItem> localHistoryMap = new LinkedHashMap<>();

    private ViewingHistoryRepository(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadLocalHistoryFromDisk();
    }

    private synchronized void loadLocalHistoryFromDisk() {
        localHistoryMap.clear();
        String jsonStr = prefs.getString(KEY_HISTORY_DATA, null);
        if (jsonStr == null || jsonStr.trim().isEmpty()) return;

        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                ViewingHistoryItem item = ViewingHistoryItem.fromJsonObject(obj);
                if (item != null && item.isVerifiedOrUserManaged()) {
                    localHistoryMap.put(item.contentId, item);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing saved viewing history", e);
        }
    }

    private synchronized void saveLocalHistoryToDisk() {
        try {
            JSONArray arr = new JSONArray();
            for (ViewingHistoryItem item : localHistoryMap.values()) {
                if (item.isVerifiedOrUserManaged()) {
                    arr.put(item.toJsonObject());
                }
            }
            prefs.edit().putString(KEY_HISTORY_DATA, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving viewing history to disk", e);
        }
    }

    /**
     * Loads unified viewing history asynchronously:
     * 1. Queries system TvContract.WatchNextPrograms (if populated by external TV apps).
     * 2. Merges with verified user-managed launcher playback entries.
     */
    public void loadViewingHistory(@NonNull HistoryCallback callback) {
        executor.execute(() -> {
            List<ViewingHistoryItem> unified = new ArrayList<>();

            // 1. Query System TvContract.WatchNextPrograms
            List<ViewingHistoryItem> systemPrograms = querySystemWatchNextPrograms();
            unified.addAll(systemPrograms);

            // 2. Add local user-initiated records
            synchronized (this) {
                for (ViewingHistoryItem item : localHistoryMap.values()) {
                    boolean duplicate = false;
                    for (ViewingHistoryItem existing : unified) {
                        if (existing.contentId.equals(item.contentId)
                                || (existing.contentTitle.equalsIgnoreCase(item.contentTitle)
                                && existing.provider.equalsIgnoreCase(item.provider))) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        unified.add(item);
                    }
                }
            }

            // Sort newest watched first
            Collections.sort(unified, (a, b) -> Long.compare(b.lastWatchedTimestamp, a.lastWatchedTimestamp));

            mainHandler.post(() -> callback.onHistoryLoaded(unified));
        });
    }

    private List<ViewingHistoryItem> querySystemWatchNextPrograms() {
        List<ViewingHistoryItem> result = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();

        try {
            String[] projection = new String[]{
                    TvContract.WatchNextPrograms._ID,
                    TvContract.WatchNextPrograms.COLUMN_TITLE,
                    TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                    TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                    TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
                    TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                    TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS
            };

            Cursor cursor = cr.query(TvContract.WatchNextPrograms.CONTENT_URI, projection, null, null, null);
            if (cursor != null) {
                try {
                    int idCol = cursor.getColumnIndex(TvContract.WatchNextPrograms._ID);
                    int titleCol = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_TITLE);
                    int pkgCol = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME);
                    int posterCol = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI);
                    int thumbCol = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI);
                    int durationCol = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS);
                    int engagedCol = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS);

                    while (cursor.moveToNext()) {
                        String id = String.valueOf(cursor.getLong(idCol));
                        String title = cursor.getString(titleCol);
                        String pkg = cursor.getString(pkgCol);
                        String poster = cursor.getString(posterCol);
                        String thumb = cursor.getString(thumbCol);
                        long duration = cursor.getLong(durationCol);
                        long engaged = cursor.getLong(engagedCol);

                        if (title != null && !title.isEmpty() && pkg != null) {
                            String art = thumb != null && !thumb.isEmpty() ? thumb : (poster != null ? poster : "");
                            String providerName = resolveProviderNameFromPackage(pkg);

                            result.add(new ViewingHistoryItem(
                                    "tvcontract_" + id,
                                    title,
                                    "movie",
                                    art,
                                    providerName,
                                    pkg,
                                    0,
                                    duration,
                                    0,
                                    engaged > 0 ? engaged : System.currentTimeMillis(),
                                    "PAUSED",
                                    ViewingHistoryItem.RESUME_APP_LAUNCH,
                                    ViewingHistoryItem.SOURCE_VERIFIED_PROVIDER,
                                    1.0f,
                                    System.currentTimeMillis()
                            ));
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception ignored) {
            // Permission or provider unavailable on standard Fire OS build
        }

        return result;
    }

    private String resolveProviderNameFromPackage(String pkg) {
        if ("com.netflix.ninja".equals(pkg)) return "Netflix";
        if ("com.amazon.amazonvideo.livingroom".equals(pkg)) return "Prime Video";
        if ("in.startv.hotstar".equals(pkg)) return "Disney+ Hotstar";
        if ("com.amazon.firetv.youtube".equals(pkg)) return "YouTube";
        if ("com.jio.media.ondemand".equals(pkg)) return "JioCinema";
        return "App";
    }

    /**
     * Records legitimate user watch engagement initiated from launcher (Play, Watch Now, Trailer).
     */
    public synchronized void recordPlaybackInitiated(@NonNull MediaItem item,
                                                     @NonNull String provider,
                                                     @NonNull String packageName) {
        String contentId = "user_" + item.id;
        ViewingHistoryItem historyItem = new ViewingHistoryItem(
                contentId,
                item.title,
                item.mediaType,
                item.backdropUrl.isEmpty() ? item.posterUrl : item.backdropUrl,
                provider,
                packageName,
                0,
                0,
                0,
                System.currentTimeMillis(),
                "PLAYING",
                ViewingHistoryItem.RESUME_APP_LAUNCH,
                ViewingHistoryItem.SOURCE_USER_MANAGED,
                1.0f,
                System.currentTimeMillis()
        );

        localHistoryMap.put(contentId, historyItem);
        saveLocalHistoryToDisk();
    }

    /**
     * Records local video playback progress with exact verified duration & timestamp.
     */
    public synchronized void recordLocalProgress(@NonNull String contentId,
                                                 @NonNull String title,
                                                 @NonNull String uriStr,
                                                 long positionMs,
                                                 long durationMs) {
        int progress = durationMs > 0 ? (int) ((positionMs * 100) / durationMs) : 0;
        ViewingHistoryItem item = new ViewingHistoryItem(
                contentId,
                title,
                "local",
                uriStr,
                "Local Player",
                context.getPackageName(),
                positionMs,
                durationMs,
                progress,
                System.currentTimeMillis(),
                positionMs >= durationMs ? "COMPLETED" : "PAUSED",
                ViewingHistoryItem.RESUME_EXACT_POSITION,
                ViewingHistoryItem.SOURCE_LOCAL_PLAYBACK,
                1.0f,
                System.currentTimeMillis()
        );

        localHistoryMap.put(contentId, item);
        saveLocalHistoryToDisk();
    }

    public synchronized void removeItem(String contentId) {
        localHistoryMap.remove(contentId);
        saveLocalHistoryToDisk();
    }

    public synchronized void clearHistory() {
        localHistoryMap.clear();
        saveLocalHistoryToDisk();
    }

    /**
     * Constructs a safe, honest launch or deep-link Intent to resume the given item.
     */
    @Nullable
    public Intent buildResumeIntent(@NonNull ViewingHistoryItem item) {
        PackageManager pm = context.getPackageManager();
        Intent intent = null;

        if (!item.applicationPackage.isEmpty()) {
            intent = pm.getLeanbackLaunchIntentForPackage(item.applicationPackage);
            if (intent == null) {
                intent = pm.getLaunchIntentForPackage(item.applicationPackage);
            }
        }

        // Provider-specific deep links when package matches
        if ("YouTube".equalsIgnoreCase(item.provider) || "com.amazon.firetv.youtube".equals(item.applicationPackage)) {
            if (item.contentId.startsWith("yt_")) {
                String videoId = item.contentId.substring(3);
                Intent ytIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + videoId));
                ytIntent.putExtra("VIDEO_ID", videoId);
                if (item.playbackPosition > 0) {
                    ytIntent.putExtra("time_position", (int) (item.playbackPosition / 1000));
                }
                if (ytIntent.resolveActivity(pm) != null) {
                    intent = ytIntent;
                }
            }
        }

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
        return intent;
    }
}
