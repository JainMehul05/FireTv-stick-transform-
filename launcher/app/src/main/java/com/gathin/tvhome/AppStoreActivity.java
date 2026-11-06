package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated Application Discovery Hub for Fire TV.
 * Integrates installed application status, Amazon Appstore deep-linking,
 * and user-initiated package sideloading.
 */
public class AppStoreActivity extends Activity {

    public static class AppStoreItem {
        public final String name;
        public final String packageName;
        public final String category;
        public final String description;
        public final int iconRes;

        public AppStoreItem(String name, String packageName, String category, String description, int iconRes) {
            this.name = name;
            this.packageName = packageName;
            this.category = category;
            this.description = description;
            this.iconRes = iconRes;
        }
    }

    private RecyclerView appsGrid;
    private StoreAdapter adapter;
    private final List<AppStoreItem> allStoreApps = new ArrayList<>();
    private final List<AppStoreItem> filteredApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_store);

        initStoreCatalogue();

        appsGrid = findViewById(R.id.storeAppsGrid);
        appsGrid.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new StoreAdapter(this, filteredApps, this::handleAppAction);
        appsGrid.setAdapter(adapter);

        findViewById(R.id.btnStoreBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnOpenAmazonAppstore).setOnClickListener(v -> launchAmazonAppstoreHome());
        findViewById(R.id.btnSideloadApk).setOnClickListener(v -> showSideloadDialog());

        setupCategoryTabs();
        filterCategory("ALL");
    }

    private void initStoreCatalogue() {
        allStoreApps.add(new AppStoreItem("Netflix", "com.netflix.ninja", "STREAMING", "Watch popular movies, TV shows and award-winning originals.", R.drawable.ic_streaming));
        allStoreApps.add(new AppStoreItem("Prime Video", "com.amazon.amazonvideo.livingroom", "STREAMING", "Stream Amazon Originals, popular movies and live sports.", R.drawable.ic_streaming));
        allStoreApps.add(new AppStoreItem("YouTube", "com.google.android.youtube.tv", "STREAMING", "Watch music videos, tutorials, news and trailers on TV.", R.drawable.ic_streaming));
        allStoreApps.add(new AppStoreItem("Disney+ Hotstar", "in.startv.hotstar", "STREAMING", "Blockbuster movies, Indian specials and live cricket.", R.drawable.ic_streaming));
        allStoreApps.add(new AppStoreItem("JioCinema", "com.jio.media.ondemand", "STREAMING", "Movies, TV series, HBO shows and live sports coverage.", R.drawable.ic_streaming));
        allStoreApps.add(new AppStoreItem("Spotify", "com.spotify.tv.android", "ENTERTAINMENT", "Music and podcasts for every mood on the big screen.", R.drawable.ic_music));
        allStoreApps.add(new AppStoreItem("Twitch", "tv.twitch.android.app", "ENTERTAINMENT", "Live gaming broadcasts, esports tournaments and chat.", R.drawable.ic_games));
        allStoreApps.add(new AppStoreItem("VLC for Android", "org.videolan.vlc", "UTILITIES", "Universal media player for all video and audio formats.", R.drawable.ic_utilities));
        allStoreApps.add(new AppStoreItem("Kodi", "org.xbmc.kodi", "UTILITIES", "Open-source entertainment center for home theaters.", R.drawable.ic_utilities));
        allStoreApps.add(new AppStoreItem("Plex", "com.plexapp.android", "UTILITIES", "Stream your personal media, free movies and live TV.", R.drawable.ic_utilities));
        allStoreApps.add(new AppStoreItem("Downloader", "info.aftvnews.downloader", "UTILITIES", "Easily download files and APKs directly from the internet.", R.drawable.ic_utilities));
        allStoreApps.add(new AppStoreItem("RetroArch", "org.retroarch", "GAMES", "Multi-system game engine and classic retro console emulator.", R.drawable.ic_games));
    }

    private void setupCategoryTabs() {
        TextView tabAll = findViewById(R.id.tabAll);
        TextView tabStreaming = findViewById(R.id.tabStreaming);
        TextView tabEntertainment = findViewById(R.id.tabEntertainment);
        TextView tabUtilities = findViewById(R.id.tabUtilities);

        tabAll.setOnClickListener(v -> filterCategory("ALL"));
        tabStreaming.setOnClickListener(v -> filterCategory("STREAMING"));
        tabEntertainment.setOnClickListener(v -> filterCategory("ENTERTAINMENT"));
        tabUtilities.setOnClickListener(v -> filterCategory("UTILITIES"));

        tabAll.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) filterCategory("ALL"); });
        tabStreaming.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) filterCategory("STREAMING"); });
        tabEntertainment.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) filterCategory("ENTERTAINMENT"); });
        tabUtilities.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) filterCategory("UTILITIES"); });

        View.OnKeyListener downListener = (v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (appsGrid != null && appsGrid.getChildCount() > 0) {
                    appsGrid.getChildAt(0).requestFocus();
                    return true;
                }
            }
            return false;
        };
        tabAll.setOnKeyListener(downListener);
        tabStreaming.setOnKeyListener(downListener);
        tabEntertainment.setOnKeyListener(downListener);
        tabUtilities.setOnKeyListener(downListener);
    }

    private void filterCategory(String category) {
        filteredApps.clear();
        for (AppStoreItem item : allStoreApps) {
            if ("ALL".equalsIgnoreCase(category) || item.category.equalsIgnoreCase(category)) {
                filteredApps.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void handleAppAction(AppStoreItem item) {
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLeanbackLaunchIntentForPackage(item.packageName);
        if (launchIntent == null) {
            launchIntent = pm.getLaunchIntentForPackage(item.packageName);
        }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
            return;
        }

        // Deep link to Amazon Appstore
        try {
            Intent storeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?p=" + item.packageName));
            storeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (storeIntent.resolveActivity(pm) != null) {
                startActivity(storeIntent);
                return;
            }
        } catch (Exception ignored) {
        }

        // Fallback web link
        try {
            Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.amazon.com/gp/mas/dl/android?p=" + item.packageName));
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(webIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Amazon Appstore is not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchAmazonAppstoreHome() {
        PackageManager pm = getPackageManager();
        Intent appstoreIntent = pm.getLeanbackLaunchIntentForPackage("com.amazon.venezia");
        if (appstoreIntent == null) {
            appstoreIntent = pm.getLaunchIntentForPackage("com.amazon.venezia");
        }
        if (appstoreIntent != null) {
            startActivity(appstoreIntent);
            return;
        }

        try {
            Intent storeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?s="));
            storeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(storeIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Opening Amazon Appstore...", Toast.LENGTH_SHORT).show();
        }
    }

    // ===== USER-INITIATED SIDELOAD WORKFLOW =====

    private void showSideloadDialog() {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File[] files = downloadDir != null ? downloadDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".apk")) : null;

        List<File> apkList = new ArrayList<>();
        if (files != null) {
            for (File f : files) apkList.add(f);
        }

        if (apkList.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.sideload_apk)
                    .setMessage("No .apk packages found in /sdcard/Download.\n\nTo install an application via sideload, copy an APK to your Download folder or use Downloader.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] apkNames = new String[apkList.size()];
        for (int i = 0; i < apkList.size(); i++) {
            apkNames[i] = apkList.get(i).getName();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select APK to Install")
                .setItems(apkNames, (dialog, which) -> confirmAndInstallApk(apkList.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmAndInstallApk(File apkFile) {
        PackageManager pm = getPackageManager();
        PackageInfo pi = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_PERMISSIONS);
        String label = apkFile.getName();
        String pkgId = "";
        String version = "";

        if (pi != null) {
            pkgId = pi.packageName;
            version = pi.versionName;
            try {
                pi.applicationInfo.sourceDir = apkFile.getAbsolutePath();
                pi.applicationInfo.publicSourceDir = apkFile.getAbsolutePath();
                label = String.valueOf(pi.applicationInfo.loadLabel(pm));
            } catch (Exception ignored) {}
        }

        String msg = "Application: " + label + "\n" +
                "Package: " + pkgId + "\n" +
                "Version: " + version + "\n\n" +
                getString(R.string.sideload_warning);

        new AlertDialog.Builder(this)
                .setTitle(R.string.sideload_title)
                .setMessage(msg)
                .setPositiveButton(R.string.install, (dialog, which) -> executeInstallApk(apkFile))
                .setNegativeButton(R.string.back, null)
                .show();
    }

    private void executeInstallApk(File apkFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Intent unknownAppIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(unknownAppIntent);
                Toast.makeText(this, "Enable Unknown Apps permission to continue installation", Toast.LENGTH_LONG).show();
                return;
            }
        }

        try {
            Uri apkUri = Uri.fromFile(apkFile);
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(installIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Error launching package installer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ===== ADAPTER =====

    private interface ActionCallback {
        void onAction(AppStoreItem item);
    }

    private static class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.Holder> {
        private final Context context;
        private final List<AppStoreItem> items;
        private final ActionCallback callback;
        private final PackageManager pm;

        StoreAdapter(Context context, List<AppStoreItem> items, ActionCallback callback) {
            this.context = context;
            this.items = items;
            this.callback = callback;
            this.pm = context.getPackageManager();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_store_card, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            AppStoreItem item = items.get(position);
            h.name.setText(item.name);
            h.desc.setText(item.description);
            h.category.setText(item.category);
            h.icon.setImageResource(item.iconRes);

            boolean isInstalled = isPackageInstalled(item.packageName);
            h.action.setText(isInstalled ? "OPEN" : "GET");
            h.action.setBackgroundResource(isInstalled ? R.drawable.bg_badge_4k : R.drawable.bg_top_pill_button);

            h.card.setOnClickListener(v -> callback.onAction(item));
        }

        private boolean isPackageInstalled(String pkg) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final View card;
            final ImageView icon;
            final TextView name;
            final TextView category;
            final TextView desc;
            final TextView action;

            Holder(View v) {
                super(v);
                card = v.findViewById(R.id.storeCard);
                icon = v.findViewById(R.id.storeAppIcon);
                name = v.findViewById(R.id.storeAppName);
                category = v.findViewById(R.id.storeAppCategory);
                desc = v.findViewById(R.id.storeAppDesc);
                action = v.findViewById(R.id.storeAppAction);

                card.setOnFocusChangeListener((view, focused) -> {
                    float s = focused ? 1.05f : 1.0f;
                    view.animate().scaleX(s).scaleY(s).setDuration(180).start();
                });
            }
        }
    }
}
