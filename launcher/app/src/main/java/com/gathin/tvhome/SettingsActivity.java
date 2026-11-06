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

        // Launcher preferences section
        items.add(new SettingItem("Launcher", "Wallpaper", "Choose background image",
                R.drawable.ic_wallpaper, () -> startActivity(new Intent(this, WallpaperPickerActivity.class))));
        items.add(new SettingItem("Launcher", "Screensaver", "Configure screensaver settings",
                R.drawable.ic_music, () -> openScreensaverSettings()));
        items.add(new SettingItem("Launcher", "Home screen redirect", "Make this the default home screen",
                R.drawable.ic_settings, () -> toggleHomeRedirect()));

        // System shortcuts section
        items.add(new SettingItem("System", "Wi-Fi", "Network settings",
                R.drawable.ic_wifi, () -> openSettings(Settings.ACTION_WIFI_SETTINGS)));
        items.add(new SettingItem("System", "Display", "Display settings",
                R.drawable.ic_music, () -> openSettings(Settings.ACTION_DISPLAY_SETTINGS)));
        items.add(new SettingItem("System", "Bluetooth", "Bluetooth settings",
                R.drawable.ic_av, () -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)));
        items.add(new SettingItem("System", "Applications", "Manage installed apps",
                R.drawable.ic_system, () -> openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        items.add(new SettingItem("System", "Storage", "Storage settings",
                R.drawable.ic_utilities, () -> openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));
        items.add(new SettingItem("System", "Date & Time", "Time zone and format",
                R.drawable.ic_av, () -> openSettings(Settings.ACTION_DATE_SETTINGS)));
        items.add(new SettingItem("System", "Accessibility", "Accessibility settings",
                R.drawable.ic_info, () -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        // About section
        items.add(new SettingItem("About", "Version", "1.0.0 (Premium Cinematic)",
                R.drawable.ic_info, null));
        items.add(new SettingItem("About", "Open Source Licenses", "View licenses",
                R.drawable.ic_info, () -> showLicenses()));
        items.add(new SettingItem("About", "Privacy Policy", "View privacy policy",
                R.drawable.ic_info, () -> showPrivacyPolicy()));

        adapter = new SettingsAdapter(this, items);
        settingsList.setAdapter(adapter);
    }

    private void openScreensaverSettings() {
        Intent intent = new Intent(android.service.dreams.DreamService.SERVICE_INTERFACE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Screensaver settings not available", Toast.LENGTH_SHORT).show();
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

    static class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.Holder> {

        private final Context ctx;
        private final List<SettingItem> items;

        SettingsAdapter(Context ctx, List<SettingItem> items) {
            this.ctx = ctx;
            this.items = items;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_setting, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            SettingItem item = items.get(position);
            h.icon.setImageResource(item.iconRes);
            h.title.setText(item.title);
            h.summary.setText(item.summary);
            if (item.section != null && (position == 0 || !item.section.equals(items.get(position - 1).section))) {
                // Could add section header here
            }
            h.itemView.setOnClickListener(v -> {
                if (item.action != null) item.action.run();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView summary;

            Holder(View v) {
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