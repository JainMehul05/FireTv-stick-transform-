package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App Library Activity - Full screen app browser with categories
 */
public class AppLibraryActivity extends Activity {

    private static final float FOCUS_SCALE = 1.08f;
    private static final float CORNER_RATIO = 0.18f;

    private RecyclerView categoryTabs;
    private RecyclerView libraryGrid;
    private View emptyState;
    private ImageView libraryBackground;
    private CategoryAdapter categoryAdapter;
    private LibraryGridAdapter gridAdapter;
    private List<LibraryGridAdapter.Entry> allApps = new ArrayList<>();
    private List<LibraryGridAdapter.Entry> filteredApps = new ArrayList<>();
    private String currentCategory = "All";
    private int selectedCategoryPosition = 0;

    // Category data
    private static final String[] CATEGORIES = {"All", "Streaming", "Entertainment", "Music", "Games", "Utilities", "System"};
    private static final int[] CATEGORY_ICONS = {
            R.drawable.ic_system, R.drawable.ic_streaming, R.drawable.ic_entertainment,
            R.drawable.ic_music, R.drawable.ic_games, R.drawable.ic_utilities, R.drawable.ic_system
    };

    // Package to category mapping
    private static final Map<String, String> CATEGORY_MAP = new HashMap<>();
    static {
        // Streaming
        CATEGORY_MAP.put("com.amazon.firetv.youtube", "Streaming");
        CATEGORY_MAP.put("com.netflix.ninja", "Streaming");
        CATEGORY_MAP.put("com.amazon.firebat", "Streaming");
        CATEGORY_MAP.put("com.disney.disneyplus", "Streaming");
        CATEGORY_MAP.put("com.hbo.max", "Streaming");
        CATEGORY_MAP.put("tv.peacocktv.peacock", "Streaming");
        CATEGORY_MAP.put("com.apple.airplay.amazon.airplay", "Streaming");
        
        // Music
        CATEGORY_MAP.put("com.amazon.music", "Music");
        CATEGORY_MAP.put("com.spotify.music", "Music");
        CATEGORY_MAP.put("com.pandora.android", "Music");
        CATEGORY_MAP.put("com.tunein.radio", "Music");
        
        // Games
        CATEGORY_MAP.put("com.amazon.spiderpork", "Games");
        CATEGORY_MAP.put("com.luna.controller", "Games");
        
        // Utilities
        CATEGORY_MAP.put("com.amazon.smarthomemapviewapp", "Utilities");
        CATEGORY_MAP.put("com.amazon.bueller.music", "Utilities");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_library);

        categoryTabs = findViewById(R.id.categoryTabs);
        libraryGrid = findViewById(R.id.libraryGrid);
        emptyState = findViewById(R.id.emptyState);
        libraryBackground = findViewById(R.id.libraryBackground);

        // Setup top bar buttons
        TextView btnBack = findViewById(R.id.btnLibraryBack);
        TextView btnSearch = findViewById(R.id.btnLibrarySearch);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
            btnBack.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        categoryTabs.requestFocus();
                        return true;
                    }
                }
                return false;
            });
        }
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> Toast.makeText(this, "Search coming soon", Toast.LENGTH_SHORT).show());
        }

        // Load background
        loadBackground();

        // Setup category tabs
        setupCategoryTabs();

        // Setup grid
        setupGrid();

        // Load apps
        loadApps();
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
        libraryBackground.setImageResource(res);
    }

    private void setupCategoryTabs() {
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        categoryTabs.setLayoutManager(lm);
        categoryTabs.setHasFixedSize(true);
        categoryTabs.setItemAnimator(null);
        categoryTabs.setOverScrollMode(View.OVER_SCROLL_NEVER);

        categoryAdapter = new CategoryAdapter(this, CATEGORIES, CATEGORY_ICONS, position -> {
            selectedCategoryPosition = position;
            currentCategory = CATEGORIES[position];
            categoryAdapter.setSelectedPosition(position);
            filterApps();
        });
        categoryTabs.setAdapter(categoryAdapter);
        
        // Set grid reference after grid is created (will be done in setupGrid)
    }
    
    private void linkCategoryTabsToGrid() {
        if (categoryAdapter != null) {
            categoryAdapter.setGridRef(libraryGrid);
        }
    }

    private void setupGrid() {
        int columns = getResources().getInteger(R.integer.columns);
        GridLayoutManager glm = new GridLayoutManager(this, columns);
        libraryGrid.setLayoutManager(glm);
        libraryGrid.setHasFixedSize(true);
        libraryGrid.setItemAnimator(null);
        libraryGrid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        libraryGrid.addItemDecoration(new GridSpacingDecoration(columns, getResources().getDisplayMetrics().density));

        gridAdapter = new LibraryGridAdapter(this, new LibraryGridAdapter.GridListener() {
            @Override
            public void onGridItemFocused(int position) {
                // Focus changed
            }

            @Override
            public boolean onGridItemKey(int position, int keyCode) {
                int count = gridAdapter.getItemCount();
                int cols = columns;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (position < cols) {
                        // Top row - focus category tabs
                        categoryTabs.requestFocus();
                        return true;
                    }
                    libraryGrid.getLayoutManager().smoothScrollToPosition(libraryGrid, null, position - cols);
                    libraryGrid.post(() -> libraryGrid.getChildAt(position - cols).requestFocus());
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    int lastRowStart = ((count - 1) / cols) * cols;
                    if (position >= lastRowStart) return true;
                    libraryGrid.getLayoutManager().smoothScrollToPosition(libraryGrid, null, position + cols);
                    libraryGrid.post(() -> libraryGrid.getChildAt(position + cols).requestFocus());
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (position % cols == 0) return true;
                    libraryGrid.getLayoutManager().smoothScrollToPosition(libraryGrid, null, position - 1);
                    libraryGrid.post(() -> libraryGrid.getChildAt(position - 1).requestFocus());
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (position % cols == cols - 1 || position == count - 1) return true;
                    libraryGrid.getLayoutManager().smoothScrollToPosition(libraryGrid, null, position + 1);
                    libraryGrid.post(() -> libraryGrid.getChildAt(position + 1).requestFocus());
                    return true;
                }
                return false;
            }

            @Override
            public void onGridItemClick(int position) {
                // Click handled in adapter
            }
        });
        gridAdapter.setColumns(columns);
        libraryGrid.setAdapter(gridAdapter);
        
        // Link category tabs to grid for navigation
        linkCategoryTabsToGrid();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        Set<String> hidden = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        Set<String> seen = new HashSet<>();

        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg)) continue;
            Intent main = pm.getLeanbackLaunchIntentForPackage(pkg);
            ComponentName mainComponent = main != null ? main.getComponent() : null;
            if (mainComponent != null && !mainComponent.getClassName().equals(ri.activityInfo.name)) continue;
            if (!seen.add(pkg)) continue;

            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            String category = CATEGORY_MAP.getOrDefault(pkg, "Entertainment");
            allApps.add(new LibraryGridAdapter.Entry(label, pkg, mainComponent, icon, category));
        }

        Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        filterApps();
    }

    private void filterApps() {
        filteredApps.clear();
        if ("All".equals(currentCategory)) {
            filteredApps.addAll(allApps);
        } else {
            for (LibraryGridAdapter.Entry e : allApps) {
                if (currentCategory.equals(e.category)) {
                    filteredApps.add(e);
                }
            }
        }
        gridAdapter.setItems(filteredApps);
        emptyState.setVisibility(filteredApps.isEmpty() ? View.VISIBLE : View.GONE);
        libraryGrid.setVisibility(filteredApps.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            // Move focus to category tabs if on first row
            View focused = getCurrentFocus();
            if (focused != null && isViewInGrid(focused)) {
                RecyclerView.ViewHolder vh = libraryGrid.findContainingViewHolder(focused);
                if (vh != null && vh.getBindingAdapterPosition() < getColumns()) {
                    categoryTabs.requestFocus();
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private int getColumns() {
        return getResources().getInteger(R.integer.columns);
    }

    private boolean isViewInGrid(View view) {
        View parent = (View) view.getParent();
        while (parent != null && parent != libraryGrid) {
            parent = (View) parent.getParent();
        }
        return parent == libraryGrid;
    }

    // ===== CATEGORY ADAPTER =====

    static class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {
        private final Context ctx;
        private final String[] categories;
        private final int[] icons;
        private final OnCategoryClickListener listener;
        private int selectedPosition = 0;
        private RecyclerView gridRef;

        interface OnCategoryClickListener {
            void onCategoryClick(int position);
        }

        CategoryAdapter(Context ctx, String[] categories, int[] icons, OnCategoryClickListener listener) {
            this.ctx = ctx;
            this.categories = categories;
            this.icons = icons;
            this.listener = listener;
        }

        void setGridRef(RecyclerView grid) {
            this.gridRef = grid;
        }

        void setSelectedPosition(int position) {
            int old = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(old);
            notifyItemChanged(position);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_category_tab, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            h.icon.setImageResource(icons[position]);
            h.title.setText(categories[position]);
            boolean selected = position == selectedPosition;
            h.itemView.setSelected(selected);
            h.itemView.setBackgroundResource(selected ? R.drawable.bg_category_tab_selected : R.drawable.bg_category_tab);
            h.itemView.setOnClickListener(v -> listener.onCategoryClick(position));
            h.itemView.setOnFocusChangeListener((v, focused) -> {
                if (focused) {
                    v.setSelected(true);
                    v.setBackgroundResource(R.drawable.bg_category_tab_selected);
                } else if (position != selectedPosition) {
                    v.setSelected(false);
                    v.setBackgroundResource(R.drawable.bg_category_tab);
                }
            });
            h.itemView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (gridRef != null) {
                        gridRef.requestFocus();
                        return true;
                    }
                    return false;
                }
                if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int count = categories.length;
                RecyclerView rv = (RecyclerView) v.getParent();
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (position > 0) {
                        rv.getLayoutManager().smoothScrollToPosition(rv, null, position - 1);
                        rv.post(() -> rv.getChildAt(position - 1).requestFocus());
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (position < count - 1) {
                        rv.getLayoutManager().smoothScrollToPosition(rv, null, position + 1);
                        rv.post(() -> rv.getChildAt(position + 1).requestFocus());
                        return true;
                    }
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return categories.length;
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.categoryIcon);
                title = v.findViewById(R.id.categoryTitle);
            }
        }
    }

    // ===== LIBRARY GRID ADAPTER =====

    static class LibraryGridAdapter extends RecyclerView.Adapter<LibraryGridAdapter.Holder> {

        interface GridListener {
            void onGridItemFocused(int position);
            boolean onGridItemKey(int position, int keyCode);
            void onGridItemClick(int position);
        }

        static class Entry {
            final String label;
            final String pkg;
            final ComponentName component;
            final Drawable icon;
            final String category;

            Entry(String label, String pkg, ComponentName component, Drawable icon, String category) {
                this.label = label;
                this.pkg = pkg;
                this.component = component;
                this.icon = icon;
                this.category = category;
            }
        }

        private final Context ctx;
        private final GridListener listener;
        private final List<Entry> items = new ArrayList<>();
        private int columns;

        LibraryGridAdapter(Context ctx, GridListener listener) {
            this.ctx = ctx;
            this.listener = listener;
        }

        void setColumns(int columns) {
            this.columns = columns;
        }

        void setItems(List<Entry> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_grid, parent, false);
            return new Holder(v, listener);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Entry e = items.get(position);
            h.name.setText(e.label);
            h.icon.setImageDrawable(e.icon);
            h.card.setOnClickListener(v -> {
                if (listener != null) listener.onGridItemClick(position);
                launch(e);
            });
        }

        private void launch(Entry e) {
            Intent i = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(e.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                ctx.startActivity(i);
            } catch (Exception ex) {
                Toast.makeText(ctx, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
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

            Holder(View v, GridListener listener) {
                super(v);
                card = v.findViewById(R.id.card);
                icon = v.findViewById(R.id.icon);
                name = v.findViewById(R.id.name);

                final float density = v.getResources().getDisplayMetrics().density;
                card.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        float radius = Math.min(view.getWidth(), view.getHeight()) * CORNER_RATIO;
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                    }
                });
                card.setClipToOutline(true);

                card.setOnKeyListener((view, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    int pos = getBindingAdapterPosition();
                    if (listener == null || pos == RecyclerView.NO_POSITION) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                            || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        return listener.onGridItemKey(pos, keyCode);
                    }
                    return false;
                });

                card.setOnFocusChangeListener((view, focused) -> {
                    float s = focused ? FOCUS_SCALE : 1.0f;
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 260 : 180)
                            .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f))
                            .start();
                    
                    // Animate shadow
                    View shadow = view.findViewById(R.id.shadow);
                    if (shadow != null) {
                        shadow.setPivotX(shadow.getWidth() / 2f);
                        shadow.setPivotY(shadow.getHeight());
                        shadow.animate().scaleX(s).scaleY(s)
                                .setDuration(focused ? 260 : 180)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    }
                    
                    name.animate().alpha(focused ? 1f : 0f)
                            .translationY(focused ? density * 4f : -density * 6f)
                            .setDuration(220).start();
                    int pos = getBindingAdapterPosition();
                    if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                        listener.onGridItemFocused(pos);
                    }
                });
            }
        }
    }
}