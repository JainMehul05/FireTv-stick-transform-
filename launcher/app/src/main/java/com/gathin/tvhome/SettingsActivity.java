package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings Activity - Launcher preferences and system shortcuts
 */
public class SettingsActivity extends Activity {

    private RecyclerView settingsList;
    private ImageView settingsBackground;
    private SettingsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsList = findViewById(R.id.settingsList);
        settingsBackground = findViewById(R.id.settingsBackground);

        // Setup back button
        TextView btnBack = findViewById(R.id.btnSettingsBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
            btnBack.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        settingsList.requestFocus();
                        return true;
                    }
                }
                return false;
            });
        }

        // Load background
        loadBackground();

        // Setup settings list
        setupSettingsList();
    }

    private void loadBackground() {
        int[] backgrounds = {R.drawable.bg_morning, R.drawable.bg_afternoon, R.drawable.bg_evening, R.drawable.bg_night};
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        int dayPart;
        if (hour < 5) dayPart = 3;
        else if (hour < 12) dayPart = 0;
        else if (hour < 17) dayPart = 1;
        else if (hour < 22) dayPart = 2;
        else dayPart = 3;
        int res = backgrounds[dayPart];
        settingsBackground.setImageResource(res);
    }

    private void setupSettingsList() {
        LinearLayoutManager lm = new LinearLayoutManager(this);
        settingsList.setLayoutManager(lm);
        settingsList.setHasFixedSize(true);
        settingsList.setItemAnimator(null);
        settingsList.setOverScrollMode(View.OVER_SCROLL_NEVER);

        List<SettingItem> items = new ArrayList<>();

        // Personalization section
        items.add(new SettingItem("Personalization", "Wallpaper", "Choose cinematic background image",
                R.drawable.ic_wallpaper, () -> startActivity(new Intent(this, WallpaperPickerActivity.class))));
        items.add(new SettingItem("Personalization", "Screensaver", "Preview Manhattan cinematic screensaver",
                R.drawable.ic_music, this::openScreensaverSettings));
        items.add(new SettingItem("Personalization", "Home screen redirect", "Make this the default home screen",
                R.drawable.ic_settings, this::toggleHomeRedirect));

        // Content & API Integrations
        String tmdbKey = com.gathin.tvhome.data.repository.ContentRepository.getInstance(this).getTmdbClient().getApiKey();
        String tmdbStatus = tmdbKey.isEmpty() ? "Offline TMDB Catalog (Click to add API key)" : "Active (" + maskKey(tmdbKey) + ")";
        items.add(new SettingItem("Content & APIs", "The Movie Database (TMDB)", tmdbStatus,
                R.drawable.ic_streaming, this::configureTmdbKey));
        items.add(new SettingItem("Content & APIs", "Clear Image Cache", "Free memory used by cached backdrops and posters",
                R.drawable.ic_utilities, this::clearImageCache));

        // System shortcuts section
        items.add(new SettingItem("System", "Wi-Fi & Network", "Manage wireless and ethernet connections",
                R.drawable.ic_wifi, () -> openSettings(Settings.ACTION_WIFI_SETTINGS)));
        items.add(new SettingItem("System", "Display & Sound", "Resolution, audio output, match dynamic range",
                R.drawable.ic_music, () -> openSettings(Settings.ACTION_DISPLAY_SETTINGS)));
        items.add(new SettingItem("System", "Bluetooth & Remotes", "Pair remotes, gamepads, and headphones",
                R.drawable.ic_av, () -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)));
        items.add(new SettingItem("System", "Applications", "Manage installed apps and clear cache",
                R.drawable.ic_system, () -> openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        items.add(new SettingItem("System", "Storage", "Internal storage capacity and free space",
                R.drawable.ic_utilities, () -> openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));
        items.add(new SettingItem("System", "Date & Time", "Network time sync, 24-hour clock format",
                R.drawable.ic_av, () -> openSettings(Settings.ACTION_DATE_SETTINGS)));
        items.add(new SettingItem("System", "Accessibility", "Subtitles, high contrast, text-to-speech",
                R.drawable.ic_info, () -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        // About section
        String versionName = "2.0";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}
        items.add(new SettingItem("About", "Version", versionName + " (Cinematic Glass Edition)",
                R.drawable.ic_info, null));
        items.add(new SettingItem("About", "Device Hardware", android.os.Build.MODEL + " (" + android.os.Build.DEVICE + ")",
                R.drawable.ic_info, null));
        items.add(new SettingItem("About", "Operating System", "Fire OS 7 (Android " + android.os.Build.VERSION.RELEASE + ", API " + android.os.Build.VERSION.SDK_INT + ")",
                R.drawable.ic_info, null));
        items.add(new SettingItem("About", "Open Source Licenses", "View licenses and attributions",
                R.drawable.ic_info, this::showLicenses));
        items.add(new SettingItem("About", "Privacy Policy", "View local privacy commitments",
                R.drawable.ic_info, this::showPrivacyPolicy));

        adapter = new SettingsAdapter(this, items);
        settingsList.setAdapter(adapter);
    }

    private void configureTmdbKey() {
        android.widget.EditText input = new android.widget.EditText(this);
        String current = com.gathin.tvhome.data.repository.ContentRepository.getInstance(this).getTmdbClient().getApiKey();
        input.setText(current);
        input.setTextColor(android.graphics.Color.WHITE);
        input.setHint("Enter TMDB API Key v3");
        input.setHintTextColor(android.graphics.Color.GRAY);
        input.setPadding(40, 30, 40, 30);

        new android.app.AlertDialog.Builder(this)
                .setTitle("TMDB API Key")
                .setMessage("Enter your TMDB API Key for live trending movies and TV discovery.\n\nLeave empty to use the built-in authentic offline catalog.")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String val = input.getText().toString().trim();
                    com.gathin.tvhome.data.repository.ContentRepository.getInstance(this).getTmdbClient().setApiKey(val);
                    Toast.makeText(this, "TMDB Key saved", Toast.LENGTH_SHORT).show();
                    setupSettingsList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearImageCache() {
        ImageLoader.getInstance().clearCache();
        Toast.makeText(this, "Image memory cache cleared", Toast.LENGTH_SHORT).show();
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "••••";
        return key.substring(0, 4) + "••••" + key.substring(key.length() - 4);
    }

    private void openScreensaverSettings() {
        try {
            Intent intent = new Intent(this, PhotoDream.class);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Screensaver preview unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleHomeRedirect() {
        boolean enabled = HomeRedirectService.isEnabled(this);
        if (HomeRedirectService.setEnabled(this, !enabled)) {
            Toast.makeText(this, enabled ? "Home redirect disabled" : "Home redirect enabled", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Requires WRITE_SECURE_SETTINGS permission (grant via ADB)", Toast.LENGTH_LONG).show();
        }
    }

    private void openSettings(String action) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLicenses() {
        Toast.makeText(this, "Licenses: Open Sans (OFL), Outfit (OFL), AndroidX (Apache 2.0)", Toast.LENGTH_LONG).show();
    }

    private void showPrivacyPolicy() {
        Toast.makeText(this, "No personal data collected. Local preferences only.", Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ===== SETTINGS ADAPTER =====

    static class SettingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        private final Context ctx;
        private final List<SettingItem> items;

        SettingsAdapter(Context ctx, List<SettingItem> items) {
            this.ctx = ctx;
            this.items = items;
        }

        @Override
        public int getItemViewType(int position) {
            SettingItem item = items.get(position);
            boolean isHeader = position == 0 || !item.section.equals(items.get(position - 1).section);
            return isHeader ? TYPE_HEADER : TYPE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(ctx);
            if (viewType == TYPE_HEADER) {
                View v = inflater.inflate(R.layout.item_setting_header, parent, false);
                return new HeaderHolder(v);
            } else {
                View v = inflater.inflate(R.layout.item_setting, parent, false);
                return new ItemHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SettingItem item = items.get(position);
            if (holder instanceof HeaderHolder) {
                HeaderHolder h = (HeaderHolder) holder;
                boolean isHeader = position == 0 || !item.section.equals(items.get(position - 1).section);
                if (isHeader) {
                    h.title.setText(item.section);
                    h.itemView.setVisibility(View.VISIBLE);
                } else {
                    h.itemView.setVisibility(View.GONE);
                    h.itemView.setLayoutParams(new LinearLayout.LayoutParams(0, 0));
                }
            } else if (holder instanceof ItemHolder) {
                ItemHolder h = (ItemHolder) holder;
                h.icon.setImageResource(item.iconRes);
                h.title.setText(item.title);
                h.summary.setText(item.summary);
                h.itemView.setOnClickListener(v -> {
                    if (item.action != null) item.action.run();
                });
                h.itemView.setOnFocusChangeListener((v, focused) -> {
                    if (focused) {
                        v.setBackgroundResource(R.drawable.bg_settings_item_focused);
                    } else {
                        v.setBackgroundResource(R.drawable.bg_settings_item);
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class HeaderHolder extends RecyclerView.ViewHolder {
            final TextView title;
            HeaderHolder(View v) {
                super(v);
                title = v.findViewById(R.id.sectionTitle);
            }
        }

        static class ItemHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView summary;
            ItemHolder(View v) {
                super(v);
                icon = v.findViewById(R.id.settingIcon);
                title = v.findViewById(R.id.settingTitle);
                summary = v.findViewById(R.id.settingSummary);
            }
        }
    }

    static class SettingItem {
        final String section;
        final String title;
        final String summary;
        final int iconRes;
        final Runnable action;

        SettingItem(String section, String title, String summary, int iconRes, Runnable action) {
            this.section = section;
            this.title = title;
            this.summary = summary;
            this.iconRes = iconRes;
            this.action = action;
        }
    }
}