package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Wallpaper Picker Activity - Select from built-in cinematic wallpapers
 */
public class WallpaperPickerActivity extends Activity {

    private static final String PREFS = "tvhome";
    private static final String PREF_WALLPAPER = "wallpaper";
    private static final float FOCUS_SCALE = 1.08f;
    private static final float CORNER_RATIO = 0.15f;

    private RecyclerView wallpaperGrid;
    private TextView applyButton;
    private ImageView previewBackground;
    private WallpaperAdapter adapter;
    private int selectedPosition = -1;
    private int currentWallpaper = 0;

    // Built-in wallpapers (using existing time-of-day photos + saver photos)
    private static final int[] WALLPAPERS = {
            R.drawable.bg_morning, R.drawable.bg_afternoon, R.drawable.bg_evening, R.drawable.bg_night,
            R.drawable.saver_01, R.drawable.saver_02, R.drawable.saver_03, R.drawable.saver_04,
            R.drawable.saver_05, R.drawable.saver_06, R.drawable.saver_07, R.drawable.saver_08,
            R.drawable.saver_09, R.drawable.saver_10, R.drawable.saver_11, R.drawable.saver_12
    };

    private static final String[] WALLPAPER_NAMES = {
            "Morning", "Afternoon", "Evening", "Night",
            "Manhattan 1", "Manhattan 2", "Manhattan 3", "Manhattan 4",
            "Manhattan 5", "Manhattan 6", "Manhattan 7", "Manhattan 8",
            "Manhattan 9", "Manhattan 10", "Manhattan 11", "Manhattan 12"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallpaper_picker);

        wallpaperGrid = findViewById(R.id.wallpaperGrid);
        applyButton = findViewById(R.id.btnApplyWallpaper);
        previewBackground = findViewById(R.id.previewBackground);

        // Load current wallpaper preference
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        currentWallpaper = prefs.getInt(PREF_WALLPAPER, 0);
        selectedPosition = currentWallpaper;
        previewBackground.setImageResource(WALLPAPERS[currentWallpaper]);

        // Setup grid
        int columns = getResources().getInteger(R.integer.columns);
        GridLayoutManager glm = new GridLayoutManager(this, columns);
        wallpaperGrid.setLayoutManager(glm);
        wallpaperGrid.setHasFixedSize(true);
        wallpaperGrid.setItemAnimator(null);
        wallpaperGrid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        wallpaperGrid.addItemDecoration(new GridSpacingDecoration(columns, getResources().getDisplayMetrics().density));

        adapter = new WallpaperAdapter(this, new WallpaperAdapter.WallpaperListener() {
            @Override
            public void onWallpaperFocused(int position, View card) {
                selectedPosition = position;
                previewBackground.setImageResource(WALLPAPERS[position]);
                applyButton.setVisibility(View.VISIBLE);
            }

            @Override
            public void onWallpaperClick(int position) {
                // Click handled by adapter
            }
        });
        wallpaperGrid.setAdapter(adapter);

        // Apply button
        applyButton.setOnClickListener(v -> {
            saveWallpaper(selectedPosition);
            Toast.makeText(this, "Wallpaper applied", Toast.LENGTH_SHORT).show();
            finish();
        });

        // Back button
        findViewById(R.id.btnWallpaperBack).setOnClickListener(v -> finish());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void saveWallpaper(int position) {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putInt(PREF_WALLPAPER, position).apply();
        // Notify MainActivity to update background
        // This will take effect on next launch or resume
    }

    // ===== WALLPAPER ADAPTER =====

    static class WallpaperAdapter extends RecyclerView.Adapter<WallpaperAdapter.Holder> {

        interface WallpaperListener {
            void onWallpaperFocused(int position, View card);
            void onWallpaperClick(int position);
        }

        private final Context ctx;
        private final WallpaperListener listener;
        private int selectedPosition = -1;

        WallpaperAdapter(Context ctx, WallpaperListener listener) {
            this.ctx = ctx;
            this.listener = listener;
        }

        void setSelectedPosition(int position) {
            int old = selectedPosition;
            selectedPosition = position;
            if (old >= 0) notifyItemChanged(old);
            notifyItemChanged(position);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_wallpaper, parent, false);
            return new Holder(v, listener);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            h.preview.setImageResource(WALLPAPERS[position]);
            h.label.setText(WALLPAPER_NAMES[position]);
            boolean selected = position == selectedPosition;
            h.selectionOverlay.setVisibility(selected ? View.VISIBLE : View.GONE);
            h.checkmark.setVisibility(selected ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onWallpaperClick(position);
            });
        }

        @Override
        public int getItemCount() {
            return WALLPAPERS.length;
        }

        static class Holder extends RecyclerView.ViewHolder {
            final View card;
            final ImageView preview;
            final View selectionOverlay;
            final ImageView checkmark;
            final TextView label;

            Holder(View v, WallpaperListener listener) {
                super(v);
                card = v.findViewById(R.id.card);
                preview = v.findViewById(R.id.preview);
                selectionOverlay = v.findViewById(R.id.selectionOverlay);
                checkmark = v.findViewById(R.id.checkmark);
                label = v.findViewById(R.id.label);

                final float density = v.getResources().getDisplayMetrics().density;
                card.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        float radius = Math.min(view.getWidth(), view.getHeight()) * CORNER_RATIO;
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                    }
                });
                card.setClipToOutline(true);

                card.setOnFocusChangeListener((view, focused) -> {
                    float s = focused ? FOCUS_SCALE : 1.0f;
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 260 : 180)
                            .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f))
                            .start();
                    if (focused && listener != null) {
                        listener.onWallpaperFocused(getBindingAdapterPosition(), view);
                    }
                });
            }
        }
    }
}