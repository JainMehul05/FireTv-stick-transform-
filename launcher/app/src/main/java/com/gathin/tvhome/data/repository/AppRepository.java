package com.gathin.tvhome.data.repository;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gathin.tvhome.R;
import com.gathin.tvhome.data.model.AppEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Universal Application Discovery & Favourites Repository for Fire TV.
 * Discovers Leanback and standard Android applications, resolving Spotify, YouTube,
 * Netflix, Hotstar, and all installed third-party and system tools.
 */
public class AppRepository {
    private static final String TAG = "AppRepository";
    private static final String PREF_NAME = "tvhome_app_prefs";
    private static final String KEY_PINNED_APPS = "pinned_apps_v3";
    private static final String KEY_HIDDEN_APPS = "hidden_apps_v3";

    private static AppRepository sInstance;

    public static synchronized AppRepository getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AppRepository(context.getApplicationContext());
        }
        return sInstance;
    }

    public interface AppLoadCallback {
        void onAppsLoaded(List<AppEntry> allApps, List<AppEntry> pinnedApps);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<AppEntry> cachedAllApps = new ArrayList<>();
    private final List<AppEntry> cachedPinnedApps = new ArrayList<>();
    private final List<String> pinnedPackageNames = new ArrayList<>();
    private final Set<String> hiddenPackageNames = new HashSet<>();

    private static final Map<String, String> CATEGORY_MAP = new HashMap<>();
    static {
        // Streaming
        CATEGORY_MAP.put("com.amazon.firetv.youtube", "Streaming");
        CATEGORY_MAP.put("com.netflix.ninja", "Streaming");
        CATEGORY_MAP.put("in.startv.hotstar", "Streaming");
        CATEGORY_MAP.put("com.amazon.amazonvideo.livingroom", "Streaming");
        CATEGORY_MAP.put("com.amazon.firebat", "Streaming");
        CATEGORY_MAP.put("com.jio.media.ondemand", "Streaming");
        CATEGORY_MAP.put("com.disney.disneyplus", "Streaming");
        CATEGORY_MAP.put("com.hbo.max", "Streaming");
        CATEGORY_MAP.put("tv.peacocktv.peacock", "Streaming");
        CATEGORY_MAP.put("com.apple.airplay.amazon.airplay", "Streaming");

        // Music
        CATEGORY_MAP.put("com.spotify.tv.android", "Music");
        CATEGORY_MAP.put("com.spotify.music", "Music");
        CATEGORY_MAP.put("com.amazon.music", "Music");
        CATEGORY_MAP.put("com.pandora.android", "Music");
        CATEGORY_MAP.put("com.tunein.radio", "Music");
        CATEGORY_MAP.put("com.jio.media.jiosaavn", "Music");

        // Entertainment
        CATEGORY_MAP.put("tv.twitch.android.app", "Entertainment");
        CATEGORY_MAP.put("org.videolan.vlc", "Entertainment");
        CATEGORY_MAP.put("org.xbmc.kodi", "Entertainment");
        CATEGORY_MAP.put("com.plexapp.android", "Entertainment");

        // Utilities
        CATEGORY_MAP.put("info.aftvnews.downloader", "Utilities");
        CATEGORY_MAP.put("com.amazon.bueller.photos", "Utilities");
        CATEGORY_MAP.put("com.android.documentsui", "Utilities");

        // Games
        CATEGORY_MAP.put("org.retroarch", "Games");
        CATEGORY_MAP.put("com.luna.controller", "Games");
    }

    private static final Set<String> DEFAULT_BLACKLIST = new HashSet<>(Arrays.asList(
            "com.amazon.tv.livetv",
            "com.mediatek.opapp",
            "com.amazon.smarthomemapviewapp",
            "com.amazon.spiderpork",
            "com.amazon.bueller.music",
            "com.amazon.tv.settings.v2.overlay.localisation.common",
            "com.amazon.tv.legal.notices.overlay.localisation.common",
            "com.amazon.tv.parentalcontrols.overlay.localisation.common",
            "com.amazon.spotify.mediabrowserservice"
    ));

    private AppRepository(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadPreferences();
    }

    private void loadPreferences() {
        pinnedPackageNames.clear();
        String savedPinned = prefs.getString(KEY_PINNED_APPS, null);
        if (savedPinned != null) {
            if (!savedPinned.trim().isEmpty()) {
                for (String p : savedPinned.split(",")) {
                    String clean = p.trim();
                    if (!clean.isEmpty() && !pinnedPackageNames.contains(clean)) {
                        pinnedPackageNames.add(clean);
                    }
                }
            }
        } else {
            // Default pinned priority on Fire TV
            pinnedPackageNames.add("com.amazon.firetv.youtube");
            pinnedPackageNames.add("com.netflix.ninja");
            pinnedPackageNames.add("com.spotify.tv.android");
            pinnedPackageNames.add("in.startv.hotstar");
            pinnedPackageNames.add("com.amazon.amazonvideo.livingroom");
        }

        hiddenPackageNames.clear();
        hiddenPackageNames.addAll(DEFAULT_BLACKLIST);
        String savedHidden = prefs.getString(KEY_HIDDEN_APPS, null);
        if (savedHidden != null && !savedHidden.trim().isEmpty()) {
            for (String h : savedHidden.split(",")) {
                String clean = h.trim();
                if (!clean.isEmpty()) hiddenPackageNames.add(clean);
            }
        }
    }

    private void savePinnedPreferences() {
        String serialized = TextUtils.join(",", pinnedPackageNames);
        prefs.edit().putString(KEY_PINNED_APPS, serialized).apply();
    }

    public void loadApps(@NonNull AppLoadCallback callback) {
        executor.execute(() -> {
            List<AppEntry> discovered = discoverAllAppsInternal();

            synchronized (this) {
                cachedAllApps.clear();
                cachedAllApps.addAll(discovered);

                cachedPinnedApps.clear();
                // Add pinned apps in their explicit user order
                for (String pinnedPkg : pinnedPackageNames) {
                    for (AppEntry app : cachedAllApps) {
                        if (app.packageName.equals(pinnedPkg)) {
                            app.isPinned = true;
                            cachedPinnedApps.add(app);
                            break;
                        }
                    }
                }

                // If user has zero pinned apps, automatically pin first 4 discovered apps
                if (cachedPinnedApps.isEmpty() && !cachedAllApps.isEmpty()) {
                    int count = Math.min(cachedAllApps.size(), 4);
                    for (int i = 0; i < count; i++) {
                        AppEntry app = cachedAllApps.get(i);
                        app.isPinned = true;
                        cachedPinnedApps.add(app);
                        pinnedPackageNames.add(app.packageName);
                    }
                    savePinnedPreferences();
                }
            }

            final List<AppEntry> allSnapshot = new ArrayList<>(cachedAllApps);
            final List<AppEntry> pinnedSnapshot = new ArrayList<>(cachedPinnedApps);
            mainHandler.post(() -> callback.onAppsLoaded(allSnapshot, pinnedSnapshot));
        });
    }

    private List<AppEntry> discoverAllAppsInternal() {
        PackageManager pm = context.getPackageManager();
        Map<String, AppEntry> appMap = new LinkedHashMap<>();

        // Query 1: LEANBACK_LAUNCHER intent activities
        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> leanbackApps = pm.queryIntentActivities(leanbackIntent, 0);
        if (leanbackApps != null) {
            for (ResolveInfo ri : leanbackApps) {
                processResolveInfo(pm, ri, true, appMap);
            }
        }

        // Query 2: Standard LAUNCHER intent activities
        Intent standardIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> standardApps = pm.queryIntentActivities(standardIntent, 0);
        if (standardApps != null) {
            for (ResolveInfo ri : standardApps) {
                processResolveInfo(pm, ri, false, appMap);
            }
        }

        // Query 3: Inspect known high-value Fire TV packages directly to guarantee zero omissions
        String[] criticalPackages = {
                "com.spotify.tv.android",
                "com.amazon.firetv.youtube",
                "com.netflix.ninja",
                "in.startv.hotstar",
                "com.amazon.amazonvideo.livingroom",
                "com.amazon.firebat",
                "com.jio.media.ondemand",
                "org.videolan.vlc",
                "org.xbmc.kodi",
                "info.aftvnews.downloader",
                "tv.twitch.android.app"
        };
        for (String pkg : criticalPackages) {
            if (!appMap.containsKey(pkg) && !hiddenPackageNames.contains(pkg) && !pkg.equals(context.getPackageName())) {
                try {
                    PackageInfo pi = pm.getPackageInfo(pkg, 0);
                    Intent launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg);
                    boolean isLeanback = true;
                    if (launchIntent == null) {
                        launchIntent = pm.getLaunchIntentForPackage(pkg);
                        isLeanback = false;
                    }
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                        String label = String.valueOf(pi.applicationInfo.loadLabel(pm));
                        Drawable icon = pi.applicationInfo.loadIcon(pm);
                        String category = resolveCategory(pkg, label);
                        boolean isPinned = pinnedPackageNames.contains(pkg);
                        boolean isSystem = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        String version = pi.versionName != null ? pi.versionName : "1.0";

                        appMap.put(pkg, new AppEntry(pkg, label, icon, launchIntent, category, isPinned, isLeanback, isSystem, version));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        List<AppEntry> result = new ArrayList<>(appMap.values());
        Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return result;
    }

    private void processResolveInfo(PackageManager pm, ResolveInfo ri, boolean isLeanback, Map<String, AppEntry> appMap) {
        if (ri.activityInfo == null || ri.activityInfo.packageName == null) return;
        String pkg = ri.activityInfo.packageName;

        if (pkg.equals(context.getPackageName()) || hiddenPackageNames.contains(pkg)) {
            return;
        }

        if (appMap.containsKey(pkg)) {
            // If already added via standard launcher, upgrade to Leanback if available
            if (isLeanback) {
                AppEntry existing = appMap.get(pkg);
                if (existing != null && !existing.isLeanback) {
                    Intent launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg);
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                        appMap.put(pkg, new AppEntry(pkg, existing.label, existing.icon, launchIntent, existing.category, existing.isPinned, true, existing.isSystem, existing.version));
                    }
                }
            }
            return;
        }

        Intent launchIntent = isLeanback ? pm.getLeanbackLaunchIntentForPackage(pkg) : pm.getLaunchIntentForPackage(pkg);
        if (launchIntent == null) {
            launchIntent = pm.getLaunchIntentForPackage(pkg);
        }
        if (launchIntent == null) {
            launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.setComponent(new ComponentName(pkg, ri.activityInfo.name));
            if (isLeanback) launchIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            else launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        String label = String.valueOf(ri.loadLabel(pm));
        Drawable icon = ri.loadIcon(pm);
        String category = resolveCategory(pkg, label);
        boolean isPinned = pinnedPackageNames.contains(pkg);
        boolean isSystem = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        String version = "1.0";
        try {
            PackageInfo pi = pm.getPackageInfo(pkg, 0);
            if (pi.versionName != null) version = pi.versionName;
        } catch (Exception ignored) {}

        appMap.put(pkg, new AppEntry(pkg, label, icon, launchIntent, category, isPinned, isLeanback, isSystem, version));
    }

    private String resolveCategory(String pkg, String label) {
        if (CATEGORY_MAP.containsKey(pkg)) {
            return CATEGORY_MAP.get(pkg);
        }
        String lower = (pkg + " " + label).toLowerCase(Locale.ROOT);
        if (lower.contains("music") || lower.contains("audio") || lower.contains("sound") || lower.contains("spotify") || lower.contains("radio") || lower.contains("podcast")) {
            return "Music";
        }
        if (lower.contains("stream") || lower.contains("tv") || lower.contains("video") || lower.contains("movie") || lower.contains("cinema") || lower.contains("hotstar") || lower.contains("netflix")) {
            return "Streaming";
        }
        if (lower.contains("game") || lower.contains("play") || lower.contains("arcade") || lower.contains("retro")) {
            return "Games";
        }
        if (lower.contains("tool") || lower.contains("file") || lower.contains("download") || lower.contains("manager") || lower.contains("setting") || lower.contains("util")) {
            return "Utilities";
        }
        return "Entertainment";
    }

    public synchronized List<AppEntry> getCachedAllApps() {
        return new ArrayList<>(cachedAllApps);
    }

    public synchronized List<AppEntry> getCachedPinnedApps() {
        return new ArrayList<>(cachedPinnedApps);
    }

    public synchronized boolean isPinned(String packageName) {
        return pinnedPackageNames.contains(packageName);
    }

    public synchronized void togglePinned(String packageName) {
        if (pinnedPackageNames.contains(packageName)) {
            pinnedPackageNames.remove(packageName);
        } else {
            pinnedPackageNames.add(packageName);
        }
        savePinnedPreferences();
        updatePinnedCache();
    }

    public synchronized void setPinned(String packageName, boolean pinned) {
        if (pinned && !pinnedPackageNames.contains(packageName)) {
            pinnedPackageNames.add(packageName);
            savePinnedPreferences();
            updatePinnedCache();
        } else if (!pinned && pinnedPackageNames.contains(packageName)) {
            pinnedPackageNames.remove(packageName);
            savePinnedPreferences();
            updatePinnedCache();
        }
    }

    private void updatePinnedCache() {
        cachedPinnedApps.clear();
        for (String pkg : pinnedPackageNames) {
            for (AppEntry app : cachedAllApps) {
                if (app.packageName.equals(pkg)) {
                    app.isPinned = true;
                    cachedPinnedApps.add(app);
                    break;
                }
            }
        }
        for (AppEntry app : cachedAllApps) {
            app.isPinned = pinnedPackageNames.contains(app.packageName);
        }
    }

    public static boolean launchApp(Context context, AppEntry app) {
        if (app == null || app.launchIntent == null) {
            Toast.makeText(context, "Cannot launch application", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            context.startActivity(app.launchIntent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error launching application: " + app.packageName, e);
            Toast.makeText(context, "Error opening " + app.label, Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
