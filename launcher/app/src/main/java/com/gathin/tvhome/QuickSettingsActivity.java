package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Lightweight, remote-friendly Quick Settings panel.
 * Anchored to the top-right corner with dim background.
 * Inspired by Free My TV design principles: zero heavy graphics, fast opening/closing,
 * graceful fallback for Fire OS restrictions.
 */
public final class QuickSettingsActivity extends Activity {

    private static final int PANEL_BG = 0xE60D1424; // Deep midnight navy with 90% opacity
    private static final int ITEM_BG = 0x1AFFFFFF;
    private static final int ITEM_BG_FOCUSED = 0x3300D4FF;
    private static final int STROKE_FOCUSED = 0xFF00D4FF;
    private static final int STROKE_NORMAL = 0x22FFFFFF;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0x99FFFFFF;
    private static final int ACCENT_BLUE = 0xFF00D4FF;

    private static class QuickTile {
        final String title;
        final String subtitle;
        final int iconRes;
        final Runnable action;

        QuickTile(String title, String subtitle, int iconRes, Runnable action) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconRes = iconRes;
            this.action = action;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0.35f);
        w.setGravity(Gravity.END | Gravity.TOP);
        w.setBackgroundDrawable(shape(PANEL_BG, 20, 0x33FFFFFF, 1));
        setFinishOnTouchOutside(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(24), dp(20), dp(20));

        // Header Title
        TextView header = new TextView(this);
        header.setText(getString(R.string.quick_settings));
        header.setTextSize(20);
        header.setTextColor(TEXT_PRIMARY);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.bottomMargin = dp(16);
        headingParams.leftMargin = dp(4);
        panel.addView(header, headingParams);

        // Quick settings items
        QuickTile[] tiles = new QuickTile[] {
            new QuickTile("Display &amp; Sound", "Picture mode, audio output", R.drawable.ic_av,
                    () -> openSetting(Settings.ACTION_DISPLAY_SETTINGS)),
            new QuickTile("Network &amp; Wi-Fi", "Internet connection", R.drawable.ic_wifi,
                    () -> openSetting(Settings.ACTION_WIFI_SETTINGS)),
            new QuickTile("Bluetooth &amp; Remotes", "Pair remotes and game controllers", R.drawable.ic_settings,
                    () -> openSetting(Settings.ACTION_BLUETOOTH_SETTINGS)),
            new QuickTile("Wallpaper Gallery", "Change home cinematic backdrop", R.drawable.ic_wallpaper,
                    () -> startActivity(new Intent(this, WallpaperPickerActivity.class))),
            new QuickTile("Screensaver", "Preview Manhattan screensaver", R.drawable.ic_music,
                    this::previewScreensaver),
            new QuickTile("All Settings", "Full system and launcher preferences", R.drawable.ic_settings,
                    () -> startActivity(new Intent(this, SettingsActivity.class)))
        };

        View[] tileViews = new View[tiles.length];
        for (int i = 0; i < tiles.length; i++) {
            final int index = i;
            QuickTile tile = tiles[i];

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setFocusable(true);
            item.setClickable(true);
            item.setPadding(dp(14), dp(10), dp(14), dp(10));
            item.setBackground(shape(ITEM_BG, 12, STROKE_NORMAL, 1));
            item.setId(View.generateViewId());

            ImageView icon = new ImageView(this);
            icon.setImageResource(tile.iconRes);
            icon.setColorFilter(ACCENT_BLUE);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
            iconParams.rightMargin = dp(14);
            item.addView(icon, iconParams);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView titleView = new TextView(this);
            titleView.setText(tile.title.replace("&amp;", "&"));
            titleView.setTextSize(15);
            titleView.setTextColor(TEXT_PRIMARY);
            titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            textCol.addView(titleView);

            TextView subView = new TextView(this);
            subView.setText(tile.subtitle.replace("&amp;", "&"));
            subView.setTextSize(12);
            subView.setTextColor(TEXT_SECONDARY);
            textCol.addView(subView);

            item.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));

            item.setOnClickListener(v -> {
                tile.action.run();
                finish();
            });

            item.setOnFocusChangeListener((v, focused) -> {
                if (focused) {
                    v.setBackground(shape(ITEM_BG_FOCUSED, 12, STROKE_FOCUSED, 2));
                    v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(150).start();
                } else {
                    v.setBackground(shape(ITEM_BG, 12, STROKE_NORMAL, 1));
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            });

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) rowParams.topMargin = dp(8);
            panel.addView(item, rowParams);
            tileViews[i] = item;
        }

        // D-pad Navigation graph inside panel
        for (int i = 0; i < tileViews.length; i++) {
            tileViews[i].setNextFocusLeftId(tileViews[i].getId());
            tileViews[i].setNextFocusRightId(tileViews[i].getId());
            tileViews[i].setNextFocusUpId(tileViews[Math.max(0, i - 1)].getId());
            tileViews[i].setNextFocusDownId(tileViews[Math.min(tileViews.length - 1, i + 1)].getId());
        }

        setContentView(panel);

        // Window size & position
        w.setLayout(dp(320), WindowManager.LayoutParams.WRAP_CONTENT);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.x = dp(24);
        lp.y = dp(24);
        w.setAttributes(lp);

        tileViews[0].requestFocus();
    }

    private void openSetting(String action) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Setting unavailable on this Fire TV", Toast.LENGTH_SHORT).show();
        }
    }

    private void previewScreensaver() {
        Intent intent = new Intent(this, PhotoDream.class);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Screensaver preview not available", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable shape(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) {
            d.setStroke(dp(strokeWidth), strokeColor);
        }
        return d;
    }
}
