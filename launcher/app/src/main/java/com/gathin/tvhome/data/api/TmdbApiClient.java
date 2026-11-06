package com.gathin.tvhome.data.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.gathin.tvhome.BuildConfig;
import com.gathin.tvhome.data.model.MediaItem;
import com.gathin.tvhome.data.model.ProviderInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client for The Movie Database (TMDB) API v3.
 * Supports Trending, Popular, Top Rated, Genre discovery, Search, and Watch Providers (India/Global).
 */
public class TmdbApiClient {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE_W780 = "https://image.tmdb.org/t/p/w780";
    private static final String IMAGE_BASE_W500 = "https://image.tmdb.org/t/p/w500";
    private static final String PREF_NAME = "tvhome_api_prefs";
    private static final String PREF_KEY_TMDB = "tmdb_api_key";
    private static final String PREF_KEY_YOUTUBE = "youtube_api_key";

    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public TmdbApiClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public String getApiKey() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String customKey = sp.getString(PREF_KEY_TMDB, "").trim();
        if (!customKey.isEmpty()) return customKey;
        return BuildConfig.TMDB_API_KEY != null ? BuildConfig.TMDB_API_KEY.trim() : "";
    }

    public void setApiKey(String key) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREF_KEY_TMDB, key != null ? key.trim() : "").apply();
    }

    public boolean hasApiKey() {
        return !getApiKey().isEmpty();
    }

    public void fetchTrending(Callback<List<MediaItem>> cb) {
        executeGet("/trending/all/day?language=en-US", json -> parseMediaList(json, "all"), cb);
    }

    public void fetchPopularMovies(Callback<List<MediaItem>> cb) {
        executeGet("/movie/popular?language=en-US&page=1", json -> parseMediaList(json, "movie"), cb);
    }

    public void fetchPopularTv(Callback<List<MediaItem>> cb) {
        executeGet("/tv/popular?language=en-US&page=1", json -> parseMediaList(json, "tv"), cb);
    }

    public void fetchTopRated(Callback<List<MediaItem>> cb) {
        executeGet("/movie/top_rated?language=en-US&page=1", json -> parseMediaList(json, "movie"), cb);
    }

    public void fetchActionThriller(Callback<List<MediaItem>> cb) {
        executeGet("/discover/movie?with_genres=28,53&sort_by=popularity.desc&language=en-US", json -> parseMediaList(json, "movie"), cb);
    }

    public void fetchSciFi(Callback<List<MediaItem>> cb) {
        executeGet("/discover/movie?with_genres=878,12&sort_by=popularity.desc&language=en-US", json -> parseMediaList(json, "movie"), cb);
    }

    public void fetchNewReleases(Callback<List<MediaItem>> cb) {
        executeGet("/movie/now_playing?language=en-US&page=1", json -> parseMediaList(json, "movie"), cb);
    }

    public void searchMulti(String query, Callback<List<MediaItem>> cb) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            executeGet("/search/multi?query=" + encoded + "&language=en-US&page=1", json -> parseMediaList(json, "all"), cb);
        } catch (Exception e) {
            cb.onError(e);
        }
    }

    public void fetchWatchProviders(int id, String mediaType, Callback<List<ProviderInfo>> cb) {
        String endpoint = "/" + mediaType + "/" + id + "/watch/providers";
        executeGet(endpoint, this::parseWatchProviders, cb);
    }

    public void fetchTrailer(int id, String mediaType, Callback<String> cb) {
        String endpoint = "/" + mediaType + "/" + id + "/videos?language=en-US";
        executeGet(endpoint, this::parseYoutubeTrailer, cb);
    }

    private interface Parser<T> {
        T parse(String jsonStr) throws Exception;
    }

    private <T> void executeGet(String path, Parser<T> parser, Callback<T> cb) {
        String apiKey = getApiKey();
        if (apiKey.isEmpty()) {
            mainHandler.post(() -> cb.onError(new IllegalStateException("No TMDB API key configured")));
            return;
        }

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String sep = path.contains("?") ? "&" : "?";
                String urlString = BASE_URL + path + sep + "api_key=" + apiKey;
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream in = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    T result = parser.parse(sb.toString());
                    mainHandler.post(() -> cb.onSuccess(result));
                } else {
                    mainHandler.post(() -> cb.onError(new Exception("TMDB HTTP " + code)));
                }
            } catch (Exception e) {
                mainHandler.post(() -> cb.onError(e));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public static List<MediaItem> parseMediaList(String jsonStr, String defaultType) throws Exception {
        List<MediaItem> list = new ArrayList<>();
        JSONObject root = new JSONObject(jsonStr);
        JSONArray results = root.optJSONArray("results");
        if (results == null) return list;

        for (int i = 0; i < results.length(); i++) {
            JSONObject obj = results.getJSONObject(i);
            int id = obj.optInt("id", 0);
            String title = obj.optString("title", obj.optString("name", ""));
            if (title.isEmpty()) continue;
            String overview = obj.optString("overview", "");
            String backdropPath = obj.optString("backdrop_path", "");
            String posterPath = obj.optString("poster_path", "");
            String releaseDate = obj.optString("release_date", obj.optString("first_air_date", ""));
            String year = releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "";
            double voteAvg = obj.optDouble("vote_average", 0.0);
            int voteCount = obj.optInt("vote_count", 0);
            String mediaType = obj.optString("media_type", defaultType);

            String backdropUrl = !backdropPath.isEmpty() && !backdropPath.equals("null") ? IMAGE_BASE_W780 + backdropPath : "";
            String posterUrl = !posterPath.isEmpty() && !posterPath.equals("null") ? IMAGE_BASE_W500 + posterPath : "";

            list.add(new MediaItem(id, title, overview, backdropUrl, posterUrl, year, voteAvg, voteCount,
                    mediaType, "Entertainment", "", "", new ArrayList<>()));
        }
        return list;
    }

    private List<ProviderInfo> parseWatchProviders(String jsonStr) throws Exception {
        List<ProviderInfo> providers = new ArrayList<>();
        JSONObject root = new JSONObject(jsonStr);
        JSONObject results = root.optJSONObject("results");
        if (results == null) return providers;

        // Check India first, then US as fallback
        JSONObject region = results.optJSONObject("IN");
        if (region == null) region = results.optJSONObject("US");
        if (region == null) return providers;

        JSONArray flatrate = region.optJSONArray("flatrate");
        if (flatrate != null) {
            for (int i = 0; i < flatrate.length(); i++) {
                JSONObject p = flatrate.getJSONObject(i);
                String name = p.optString("provider_name", "");
                String logo = p.optString("logo_path", "");
                String logoUrl = !logo.isEmpty() ? "https://image.tmdb.org/t/p/w154" + logo : "";
                providers.add(new ProviderInfo(name, logoUrl, "flatrate"));
            }
        }
        return providers;
    }

    private String parseYoutubeTrailer(String jsonStr) throws Exception {
        JSONObject root = new JSONObject(jsonStr);
        JSONArray results = root.optJSONArray("results");
        if (results == null) return "";

        for (int i = 0; i < results.length(); i++) {
            JSONObject vid = results.getJSONObject(i);
            String site = vid.optString("site", "");
            String type = vid.optString("type", "");
            if ("YouTube".equalsIgnoreCase(site) && ("Trailer".equalsIgnoreCase(type) || "Teaser".equalsIgnoreCase(type))) {
                return vid.optString("key", "");
            }
        }
        return "";
    }
}
