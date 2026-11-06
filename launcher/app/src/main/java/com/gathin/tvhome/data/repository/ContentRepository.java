package com.gathin.tvhome.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.gathin.tvhome.data.api.TmdbApiClient;
import com.gathin.tvhome.data.model.MediaItem;
import com.gathin.tvhome.data.model.ProviderInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single source of truth for entertainment content rows, live TMDB feeds, and user Watchlist.
 */
public class ContentRepository {

    public static class CategoryRow {
        public final String id;
        public final String title;
        public final String iconName;
        public final List<MediaItem> items;

        public CategoryRow(String id, String title, String iconName, List<MediaItem> items) {
            this.id = id;
            this.title = title;
            this.iconName = iconName;
            this.items = items != null ? items : new ArrayList<>();
        }
    }

    public interface RepositoryCallback {
        void onDataLoaded(List<CategoryRow> rows);
    }

    private static ContentRepository sInstance;

    public static synchronized ContentRepository getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ContentRepository(context.getApplicationContext());
        }
        return sInstance;
    }

    private static final String PREF_WATCHLIST = "tvhome_watchlist_pref";
    private static final String KEY_WATCHLIST_DATA = "watchlist_json";

    private final Context context;
    private final TmdbApiClient tmdbClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Map<String, CategoryRow> cachedRows = new LinkedHashMap<>();
    private final List<MediaItem> watchlistItems = new ArrayList<>();

    private ContentRepository(Context context) {
        this.context = context;
        this.tmdbClient = new TmdbApiClient(context);
        loadWatchlistFromDisk();
    }

    public TmdbApiClient getTmdbClient() {
        return tmdbClient;
    }

    public void loadContent(RepositoryCallback callback) {
        executor.execute(() -> {
            if (cachedRows.isEmpty()) {
                loadFromAssetCache();
            }

            // Sync verified continue watching row (only if verified or user-managed history exists)
            updateContinueWatchingRow();

            // Always update watchlist row
            updateWatchlistCategoryRow();

            List<CategoryRow> snapshot = getRowsSnapshot();
            mainHandler.post(() -> callback.onDataLoaded(snapshot));

            // If API key is available, attempt background live refresh
            if (tmdbClient.hasApiKey()) {
                refreshLiveContent(callback);
            }
        });
    }

    private void updateContinueWatchingRow() {
        ViewingHistoryRepository historyRepo = ViewingHistoryRepository.getInstance(context);
        historyRepo.loadViewingHistory(items -> {
            if (items == null || items.isEmpty()) {
                cachedRows.remove("continue_watching");
                return;
            }
            List<MediaItem> mediaList = new ArrayList<>();
            for (com.gathin.tvhome.data.model.ViewingHistoryItem vhi : items) {
                MediaItem mi = vhi.toMediaItem();
                mi.progress = vhi.progressPercentage;
                if (vhi.duration > 0 && vhi.playbackPosition > 0) {
                    long remainingMs = vhi.duration - vhi.playbackPosition;
                    long remMin = remainingMs / 60000;
                    mi.resumeText = remMin > 0 ? (remMin + "m left") : "Resume";
                } else {
                    mi.resumeText = "Resume";
                }
                mediaList.add(mi);
            }
            Map<String, CategoryRow> reordered = new LinkedHashMap<>();
            reordered.put("continue_watching", new CategoryRow("continue_watching", "Continue Watching", "ic_streaming", mediaList));
            for (Map.Entry<String, CategoryRow> entry : cachedRows.entrySet()) {
                if (!"continue_watching".equals(entry.getKey())) {
                    reordered.put(entry.getKey(), entry.getValue());
                }
            }
            cachedRows.clear();
            cachedRows.putAll(reordered);
        });
    }

    private List<CategoryRow> getRowsSnapshot() {
        List<CategoryRow> list = new ArrayList<>();
        for (CategoryRow row : cachedRows.values()) {
            if (!row.items.isEmpty()) {
                list.add(new CategoryRow(row.id, row.title, row.iconName, new ArrayList<>(row.items)));
            }
        }
        return list;
    }

    private void loadFromAssetCache() {
        try {
            InputStream in = context.getAssets().open("tmdb_cache.json");
            int size = in.available();
            byte[] buffer = new byte[size];
            in.read(buffer);
            in.close();

            String jsonStr = new String(buffer, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(jsonStr);
            JSONArray categories = root.optJSONArray("categories");
            if (categories == null) return;

            for (int i = 0; i < categories.length(); i++) {
                JSONObject catObj = categories.getJSONObject(i);
                String id = catObj.optString("id", "row_" + i);
                String title = catObj.optString("title", "");
                String icon = catObj.optString("icon", "ic_entertainment");

                List<MediaItem> items = new ArrayList<>();
                JSONArray itemsArr = catObj.optJSONArray("items");
                if (itemsArr != null) {
                    for (int j = 0; j < itemsArr.length(); j++) {
                        JSONObject itemObj = itemsArr.getJSONObject(j);
                        MediaItem item = parseMediaItem(itemObj);
                        if (item != null) items.add(item);
                    }
                }
                cachedRows.put(id, new CategoryRow(id, title, icon, items));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private MediaItem parseMediaItem(JSONObject obj) {
        int id = obj.optInt("id", 0);
        String title = obj.optString("title", "");
        if (title.isEmpty()) return null;
        String overview = obj.optString("overview", "");
        String backdrop = obj.optString("backdropUrl", "");
        String poster = obj.optString("posterUrl", "");
        String year = obj.optString("releaseYear", "");
        double rating = obj.optDouble("voteAverage", 0.0);
        int voteCount = obj.optInt("voteCount", 0);
        String mediaType = obj.optString("mediaType", "movie");
        String genres = obj.optString("genres", "Entertainment");
        String duration = obj.optString("duration", "");
        String trailerId = obj.optString("trailerYoutubeId", "");

        List<ProviderInfo> providers = new ArrayList<>();
        JSONArray pArr = obj.optJSONArray("providers");
        if (pArr != null) {
            for (int k = 0; k < pArr.length(); k++) {
                JSONObject pObj = pArr.optJSONObject(k);
                if (pObj != null) {
                    providers.add(new ProviderInfo(
                            pObj.optString("name", ""),
                            pObj.optString("logoUrl", ""),
                            pObj.optString("type", "flatrate")
                    ));
                }
            }
        }

        MediaItem item = new MediaItem(id, title, overview, backdrop, poster, year, rating,
                voteCount, mediaType, genres, duration, trailerId, providers);
        item.progress = obj.optInt("progress", -1);
        item.resumeText = obj.optString("resumeText", "");
        return item;
    }

    private void refreshLiveContent(RepositoryCallback callback) {
        tmdbClient.fetchTrending(new TmdbApiClient.Callback<List<MediaItem>>() {
            @Override
            public void onSuccess(List<MediaItem> result) {
                if (!result.isEmpty()) {
                    cachedRows.put("trending", new CategoryRow("trending", "Trending Now", "ic_entertainment", result));
                    mainHandler.post(() -> callback.onDataLoaded(getRowsSnapshot()));
                }
            }
            @Override public void onError(Exception e) {}
        });

        tmdbClient.fetchPopularMovies(new TmdbApiClient.Callback<List<MediaItem>>() {
            @Override
            public void onSuccess(List<MediaItem> result) {
                if (!result.isEmpty()) {
                    cachedRows.put("popular_movies", new CategoryRow("popular_movies", "Popular Movies", "ic_entertainment", result));
                    mainHandler.post(() -> callback.onDataLoaded(getRowsSnapshot()));
                }
            }
            @Override public void onError(Exception e) {}
        });
    }

    // ===== WATCHLIST MANAGEMENT =====

    public synchronized boolean isInWatchlist(int id) {
        for (MediaItem item : watchlistItems) {
            if (item.id == id) return true;
        }
        return false;
    }

    public synchronized void toggleWatchlist(MediaItem item) {
        if (item == null) return;
        if (isInWatchlist(item.id)) {
            for (int i = 0; i < watchlistItems.size(); i++) {
                if (watchlistItems.get(i).id == item.id) {
                    watchlistItems.remove(i);
                    break;
                }
            }
        } else {
            watchlistItems.add(0, item);
        }
        saveWatchlistToDisk();
        updateWatchlistCategoryRow();
    }

    public synchronized List<MediaItem> getWatchlist() {
        return new ArrayList<>(watchlistItems);
    }

    private void updateWatchlistCategoryRow() {
        if (!watchlistItems.isEmpty()) {
            cachedRows.put("watchlist", new CategoryRow("watchlist", "Your Watchlist", "ic_entertainment", new ArrayList<>(watchlistItems)));
        } else {
            cachedRows.remove("watchlist");
        }
    }

    private void saveWatchlistToDisk() {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                for (MediaItem item : watchlistItems) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", item.id);
                    obj.put("title", item.title);
                    obj.put("overview", item.overview);
                    obj.put("backdropUrl", item.backdropUrl);
                    obj.put("posterUrl", item.posterUrl);
                    obj.put("releaseYear", item.releaseYear);
                    obj.put("voteAverage", item.voteAverage);
                    obj.put("voteCount", item.voteCount);
                    obj.put("mediaType", item.mediaType);
                    obj.put("genres", item.genres);
                    obj.put("duration", item.duration);
                    obj.put("trailerYoutubeId", item.trailerYoutubeId);
                    arr.put(obj);
                }
                context.getSharedPreferences(PREF_WATCHLIST, Context.MODE_PRIVATE)
                        .edit().putString(KEY_WATCHLIST_DATA, arr.toString()).apply();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void loadWatchlistFromDisk() {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_WATCHLIST, Context.MODE_PRIVATE);
            String jsonStr = sp.getString(KEY_WATCHLIST_DATA, "");
            if (jsonStr.isEmpty()) return;

            JSONArray arr = new JSONArray(jsonStr);
            watchlistItems.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                MediaItem item = parseMediaItem(obj);
                if (item != null) watchlistItems.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
