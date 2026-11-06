package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhome.data.model.MediaItem;
import com.gathin.tvhome.data.repository.ContentRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Premium Cinematic Fire TV Launcher - Main Activity.
 * Complete architectural reconstruction:
 *  - Fixed Top Bar (Live Greeting, TextClock, Date, Inputs, Wi-Fi, Quick Settings)
 *  - Full-screen cinematic backdrop with smooth ambient scrims
 *  - Dynamic Hero section updated by card focus
 *  - Apple TV floating glass application dock
 *  - 6 Rich Content Discovery Rows (Continue Watching, Recommended Movies, Popular TV Shows,
 *    Action &amp; Thriller, Recently Added, Browse All Apps)
 *  - Deterministic remote D-pad navigation with zero focus traps
 *  - Ultra-lightweight memory profile optimized for 1GB RAM Fire TV Stick Lite
 */
public class MainActivity extends Activity
        implements MainContentAdapter.MainContentListener,
                   DockAdapter.DockListener,
                   GridAdapter.GridListener {

    private static final String TAG = "MainActivity";
    private static final long MINUTE_MS = 60_000L;
    private static Context appContext;

    public static Context getAppContext() {
        return appContext;
    }

    // UI Root & Background Layers
    private FrameLayout root;
    private ImageView heroBackground;
    private View heroSideScrim;
    private View heroScrim;
    private View ambientGlow;
    private GradientDrawable ambientGradient;

    // Scrolling Main Vertical Container
    private RecyclerView mainContentRecyclerView;

    // Fixed Top Navigation Bar
    private LinearLayout topBar;
    private TextView greeting;
    private View btnSearch;
    private View btnStore;
    private TextView btnInputs;
    private TextView btnWifi;
    private TextView btnSettings;
    private MediaItem currentFeaturedItem;

    // App Library Sheet & Glass Highlights
    private RecyclerView grid;
    private FrameLayout gridContainer;
    private GlassView glass;

    // Adapters
    private MainContentAdapter mainContentAdapter;
    private DockAdapter dockAdapter;
    private GridAdapter gridAdapter;
    private List<DockAdapter.Entry> allApps = new ArrayList<>();

    // 5 Content Rows
    private ContentAdapter continueWatchingAdapter;
    private ContentAdapter featuredMoviesAdapter;
    private ContentAdapter featuredTvAdapter;
    private ContentAdapter actionThrillerAdapter;
    private ContentAdapter recentlyAddedAdapter;

    // Springs & Animations
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    private int ambientColor;
    private float density;

    // Navigation state
    private int lastDockFocused = 0;
    private int lastGridFocused = 0;
    private boolean isInGrid = false;
    private int currentContentRow = -1;
    private String currentHeroTitle = "Dune: Part Two";
    private String currentHeroSubtitle = "2024 • Sci-Fi / Adventure • 2h 46m";

    // Connectivity
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(@NonNull Network n) { handler.post(MainActivity.this::updateWifiLabel); }
        @Override public void onLost(@NonNull Network n) { handler.post(MainActivity.this::updateWifiLabel); }
        @Override public void onCapabilitiesChanged(@NonNull Network n, @NonNull NetworkCapabilities c) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
        @Override public void onLinkPropertiesChanged(@NonNull Network n, @NonNull LinkProperties p) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };

    // Time ticker
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            updateGreeting();
            updateBackground();
            updateWifiLabel();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    private static final int[] WIFI_ICONS = {R.drawable.ic_wifi_1, R.drawable.ic_wifi_2, R.drawable.ic_wifi};
    private static final int COLOR_WARN = 0xFFFFB340;
    private static final int COLOR_ERROR = 0xFFFF5A52;

    private static final int[] TIME_BACKGROUNDS = {
            R.drawable.bg_morning, R.drawable.bg_afternoon, R.drawable.bg_evening, R.drawable.bg_night
    };
    private static final int[] WALLPAPERS = {
            R.drawable.saver_01, R.drawable.saver_02, R.drawable.saver_03, R.drawable.saver_04,
            R.drawable.saver_05, R.drawable.saver_06, R.drawable.saver_07, R.drawable.saver_08,
            R.drawable.saver_09, R.drawable.saver_10, R.drawable.saver_11, R.drawable.saver_12
    };
    private int currentBgRes = 0;
    private String liveTvPkg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appContext = this;
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        liveTvPkg = getString(R.string.live_tv_package).trim();

        // 1. Root & background views
        root = findViewById(R.id.root);
        heroBackground = findViewById(R.id.heroBackground);
        heroSideScrim = findViewById(R.id.heroSideScrim);
        heroScrim = findViewById(R.id.heroScrim);
        ambientGlow = findViewById(R.id.ambientGlow);
        mainContentRecyclerView = findViewById(R.id.mainContentRecyclerView);
        grid = findViewById(R.id.grid);
        gridContainer = findViewById(R.id.gridContainer);
        glass = findViewById(R.id.glass);

        // 2. Fixed Top Bar views
        topBar = findViewById(R.id.topBar);
        greeting = findViewById(R.id.greeting);
        btnSearch = findViewById(R.id.btnSearch);
        btnStore = findViewById(R.id.btnStore);
        btnInputs = findViewById(R.id.btnInputs);
        btnWifi = findViewById(R.id.btnWifi);
        btnSettings = findViewById(R.id.btnSettings);

        setupTopBar();

        // 3. Ambient Glow gradient
        ambientGradient = new GradientDrawable();
        ambientGradient.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGradient.setGradientCenter(0.5f, 0.5f);
        ambientGlow.setBackground(ambientGradient);
        ambientGlow.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGradient.setGradientRadius((r - l) * 0.60f));

        // 4. App Library Grid inside container
        final int columns = Math.max(1, getResources().getInteger(R.integer.columns));
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, columns);
        grid.setLayoutManager(gridLayoutManager);
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.addItemDecoration(new GridSpacingDecoration(columns, density));

        // 5. Glass highlight springs
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        // 6. Load apps and content rows
        loadAllApps();
        setupContentRows();
        setupMainContentAdapter();
    }

    /**
     * Wire up Top Bar utility and hub buttons and their remote D-pad focus graph.
     */
    private void setupTopBar() {
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
            btnSearch.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusHeroActions();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (btnStore != null) btnStore.requestFocus();
                    return true;
                }
                return false;
            });
        }

        if (btnStore != null) {
            btnStore.setOnClickListener(v -> startActivity(new Intent(this, AppStoreActivity.class)));
            btnStore.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusHeroActions();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (btnSearch != null) btnSearch.requestFocus();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (btnInputs != null) btnInputs.requestFocus();
                    return true;
                }
                return false;
            });
        }

        if (btnInputs != null) {
            btnInputs.setOnClickListener(v -> showInputs());
            btnInputs.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusHeroActions();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (btnStore != null) btnStore.requestFocus();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (btnWifi != null) btnWifi.requestFocus();
                    return true;
                }
                return false;
            });
        }

        if (btnWifi != null) {
            btnWifi.setOnClickListener(v -> openWifiSettings());
            btnWifi.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusHeroActions();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (btnInputs != null) btnInputs.requestFocus();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (btnSettings != null) btnSettings.requestFocus();
                    return true;
                }
                return false;
            });
        }

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> openQuickSettings());
            btnSettings.setOnLongClickListener(v -> {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            });
            btnSettings.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusHeroActions();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (btnWifi != null) btnWifi.requestFocus();
                    return true;
                }
                return false;
            });
        }
    }

    private void openQuickSettings() {
        startActivity(new Intent(this, QuickSettingsActivity.class));
    }

    private void openWifiSettings() {
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Wi-Fi settings not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadAllApps() {
        PackageManager pm = getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        List<DockAdapter.Entry> fresh = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> hidden = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));

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
            String squareName = DockAdapter.DOCK_ICONS.get(pkg);
            int square = squareName == null ? 0
                    : getResources().getIdentifier(squareName, "drawable", getPackageName());
            fresh.add(new DockAdapter.Entry(label, new ComponentName(pkg, ri.activityInfo.name),
                    square != 0 ? getDrawable(square) : null, icon));
        }
        Collections.sort(fresh, (a, b) -> a.label.compareToIgnoreCase(b.label));
        allApps = fresh;

        // Initialize dock & grid adapters
        final int columns = getResources().getInteger(R.integer.columns);
        Set<String> dockedPackages = new HashSet<>();
        dockAdapter = new DockAdapter(this, this, columns);
        dockAdapter.reload(allApps);
        for (DockAdapter.Entry e : dockAdapter.getItems()) dockedPackages.add(e.pkg);

        gridAdapter = new GridAdapter(this, this, hidden, dockedPackages, columns);
        gridAdapter.reload();
    }

    /**
     * Set up Entertainment Content Discovery Rows via ContentRepository (TMDB & Offline Cache).
     */
    private void setupContentRows() {
        ContentRepository repo = ContentRepository.getInstance(this);
        repo.loadContent(rows -> {
            List<MainContentAdapter.ContentRow> adapterRows = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                ContentRepository.CategoryRow catRow = rows.get(i);
                ContentAdapter adapter = new ContentAdapter(this, null);
                adapter.setMediaItems(catRow.items);
                int iconRes = getRowIcon(catRow.id);
                adapterRows.add(new MainContentAdapter.ContentRow(catRow.title, iconRes, adapter, i));
            }
            mainContentAdapter.setContentRows(adapterRows);

            if (!rows.isEmpty() && !rows.get(0).items.isEmpty()) {
                currentFeaturedItem = rows.get(0).items.get(0);
                updateHeroWithMediaItem("FEATURED", currentFeaturedItem);
            }
        });
    }

    private int getRowIcon(String rowId) {
        if ("continue_watching".equals(rowId)) return R.drawable.ic_streaming;
        if ("trending".equals(rowId)) return R.drawable.ic_entertainment;
        if ("popular_movies".equals(rowId)) return R.drawable.ic_streaming;
        if ("popular_tv".equals(rowId)) return R.drawable.ic_entertainment;
        if ("action_thriller".equals(rowId)) return R.drawable.ic_play;
        if ("watchlist".equals(rowId)) return R.drawable.ic_pin;
        return R.drawable.ic_info;
    }

    /**
     * Configure MainContentAdapter with the Hero section, Apple TV Dock, and Content Discovery Rows.
     */
    private void setupMainContentAdapter() {
        mainContentAdapter = new MainContentAdapter(this, this, dockAdapter);
        mainContentAdapter.setAppsRowCallback(() -> startActivity(new Intent(this, AppLibraryActivity.class)));

        LinearLayoutManager lm = new LinearLayoutManager(this);
        mainContentRecyclerView.setLayoutManager(lm);
        mainContentRecyclerView.setHasFixedSize(false);
        mainContentRecyclerView.setItemAnimator(null);
        mainContentRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mainContentRecyclerView.setAdapter(mainContentAdapter);

        // Smooth top bar collapse on scroll to prevent any text overlap with content rows
        mainContentRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private int totalDy = 0;
            private boolean isCollapsed = false;

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                totalDy += dy;
                if (totalDy > 60 && !isCollapsed) {
                    isCollapsed = true;
                    if (topBar != null) {
                        topBar.animate().translationY(-topBar.getHeight()).alpha(0f).setDuration(220).start();
                    }
                } else if (totalDy <= 30 && isCollapsed) {
                    isCollapsed = false;
                    if (topBar != null) {
                        topBar.animate().translationY(0f).alpha(1f).setDuration(220).start();
                    }
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        glassShown = false;
        glass.setAlpha(0f);
        ambientGlow.setAlpha(0f);

        dockAdapter.reload(allApps);
        gridAdapter.reload();

        updateGreeting();
        updateBackground();
        updateWifiLabel();

        // Restore focus to dock
        mainContentRecyclerView.post(() -> {
            View focused = getCurrentFocus();
            if (focused != null && isViewInRecyclerView(focused, mainContentRecyclerView)) return;
            focusDockItem(lastDockFocused);
        });

        handler.postDelayed(minuteTick, MINUTE_MS);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    @Override
    public void onBackPressed() {
        if (isInGrid) {
            hideGrid();
            mainContentRecyclerView.requestFocus();
        } else {
            // If scrolled down into content rows, scroll back to dock
            int firstVisible = ((LinearLayoutManager) mainContentRecyclerView.getLayoutManager()).findFirstVisibleItemPosition();
            if (firstVisible > 1) {
                mainContentRecyclerView.smoothScrollToPosition(1);
                focusDockItem(lastDockFocused);
            }
        }
    }

    // ===== MAINCONTENT & DOCK LISTENERS =====

    @Override
    public void onDockItemFocused(int position, View card) {
        lastDockFocused = position;
        isInGrid = false;
        currentContentRow = -1;
        magnifyDock(position);
        showHeroTooltip(position, card);
        updateAmbientGlow(dockAdapter.ambientColorAt(position));
    }

    @Override
    public boolean onDockItemKey(int position, int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            focusHeroActions();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            focusFirstContentRow();
            return true;
        }
        return false;
    }

    @Override
    public boolean onDockItemVerticalKey(int position, int keyCode) {
        return onDockItemKey(position, keyCode);
    }

    @Override
    public boolean onDockItemHorizontalKey(int position, int keyCode) {
        return false; // Let RecyclerView handle horizontal navigation naturally
    }

    @Override
    public void onDockItemMenu(int position, View card) {
        showDockMenu(position, card);
    }

    @Override
    public void onDockItemClick(int position, View card) {
        // App launch handled in adapter
    }

    @Override
    public void onHeroUpKey() {
        if (topBar != null) {
            topBar.animate().translationY(0f).alpha(1f).setDuration(150).start();
        }
        if (btnSearch != null) {
            btnSearch.requestFocus();
        } else if (btnSettings != null) {
            btnSettings.requestFocus();
        }
    }

    @Override
    public void onHeroDownKey() {
        focusDockItem(lastDockFocused);
    }

    @Override
    public void onHeroPlayClick() {
        if (currentFeaturedItem != null) {
            Intent intent = new Intent(this, MediaDetailsActivity.class);
            intent.putExtra(MediaDetailsActivity.EXTRA_MEDIA_ITEM, currentFeaturedItem);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Playing: " + currentHeroTitle, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onHeroInfoClick() {
        if (currentFeaturedItem != null) {
            Intent intent = new Intent(this, MediaDetailsActivity.class);
            intent.putExtra(MediaDetailsActivity.EXTRA_MEDIA_ITEM, currentFeaturedItem);
            startActivity(intent);
        } else {
            Toast.makeText(this, currentHeroTitle + " • " + currentHeroSubtitle, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onContentItemFocused(int rowIndex, int position, View card) {
        currentContentRow = rowIndex;
        isInGrid = false;
        magnifyDock(-1);
        hideHeroTooltip();

        MainContentAdapter.ContentRow row = mainContentAdapter.getContentRow(rowIndex);
        if (row == null || row.adapter == null) return;
        MediaItem item = row.adapter.getItemAt(position);
        if (item == null) return;

        currentFeaturedItem = item;
        updateHeroWithMediaItem(row.title, item);
        updateAmbientGlow(0xFF2A3858);
    }

    @Override
    public void onContentItemClick(int rowIndex, int position, View card) {
        MainContentAdapter.ContentRow row = mainContentAdapter.getContentRow(rowIndex);
        if (row != null && row.adapter != null) {
            MediaItem item = row.adapter.getItemAt(position);
            if (item != null) {
                Intent intent = new Intent(this, MediaDetailsActivity.class);
                intent.putExtra(MediaDetailsActivity.EXTRA_MEDIA_ITEM, item);
                startActivity(intent);
            }
        }
    }

    @Override
    public boolean onContentItemKey(int rowIndex, int position, int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (rowIndex == 0) {
                // Up from first content row goes to dock
                focusDockItem(lastDockFocused);
                return true;
            } else {
                focusContentItem(rowIndex - 1, position);
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (rowIndex == mainContentAdapter.getContentRowCount() - 1) {
                // Down from last content row goes to apps row
                int appsRowPos = mainContentAdapter.getItemCount() - 1;
                mainContentRecyclerView.smoothScrollToPosition(appsRowPos);
                mainContentRecyclerView.postDelayed(() -> {
                    RecyclerView.ViewHolder vh = mainContentRecyclerView.findViewHolderForAdapterPosition(appsRowPos);
                    if (vh != null) vh.itemView.requestFocus();
                }, 100);
                return true;
            } else {
                focusContentItem(rowIndex + 1, position);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onAppsRowClick() {
        startActivity(new Intent(this, AppLibraryActivity.class));
    }

    // ===== HERO DYNAMIC UPDATE =====

    private void updateHeroWithMediaItem(String category, MediaItem item) {
        if (item == null) return;
        currentHeroTitle = item.title;
        currentHeroSubtitle = item.genres;

        if (heroBackground != null && item.backdropUrl != null && !item.backdropUrl.isEmpty()) {
            ImageLoader.getInstance().loadUrl(item.backdropUrl, heroBackground, 1920, 1080, R.drawable.bg_night);
        }

        MainContentAdapter.HeroHolder h = mainContentAdapter.getHeroHolder();
        if (h != null) {
            if (h.heroCategory != null) {
                h.heroCategory.setText(category != null ? category.toUpperCase(Locale.ROOT) : "FEATURED");
            }
            if (h.heroTitle != null) {
                h.heroTitle.setText(item.title);
            }
            if (h.heroYear != null) {
                h.heroYear.setText(!item.releaseYear.isEmpty() ? item.releaseYear : "2024");
            }
            if (h.heroGenre != null) {
                h.heroGenre.setText(!item.genres.isEmpty() ? item.genres : "Entertainment");
            }
            if (h.heroDuration != null) {
                if (item.voteAverage > 0) {
                    h.heroDuration.setText(item.getFormattedRating());
                } else if (!item.duration.isEmpty()) {
                    h.heroDuration.setText(item.duration);
                } else {
                    h.heroDuration.setText("Feature");
                }
            }
        }
    }

    // ===== FOCUS HELPERS =====

    private void focusHeroActions() {
        mainContentRecyclerView.smoothScrollToPosition(0);
        mainContentRecyclerView.postDelayed(() -> {
            MainContentAdapter.HeroHolder h = mainContentAdapter.getHeroHolder();
            if (h != null && h.heroPlayButton != null) {
                h.heroPlayButton.requestFocus();
            } else {
                focusDockItem(lastDockFocused);
            }
        }, 60);
    }

    private void focusFirstContentRow() {
        focusContentItem(0, 0);
    }

    private void focusDockItem(int position) {
        RecyclerView dockRv = findDockRecyclerView();
        if (dockRv == null) {
            mainContentRecyclerView.scrollToPosition(1);
            mainContentRecyclerView.postDelayed(() -> focusDockItem(position), 80);
            return;
        }

        int count = dockAdapter.getDockCount();
        if (count == 0) return;
        final int pos = Math.max(0, Math.min(position, count - 1));
        lastDockFocused = pos;

        RecyclerView.ViewHolder vh = dockRv.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            if (card != null) {
                card.requestFocus();
                return;
            }
        }
        dockRv.smoothScrollToPosition(pos);
        dockRv.postDelayed(() -> {
            RecyclerView.ViewHolder h = dockRv.findViewHolderForAdapterPosition(pos);
            View card = h != null ? h.itemView.findViewById(R.id.card)
                    : (dockRv.getChildCount() > 0 ? dockRv.getChildAt(0).findViewById(R.id.card) : null);
            if (card != null) card.requestFocus();
        }, 60);
    }

    private void focusContentItem(int rowIndex, int position) {
        int adapterPosition = rowIndex + 2; // Hero(0) + Dock(1) + rows
        mainContentRecyclerView.smoothScrollToPosition(adapterPosition);
        mainContentRecyclerView.postDelayed(() -> {
            RecyclerView.ViewHolder vh = mainContentRecyclerView.findViewHolderForAdapterPosition(adapterPosition);
            if (vh instanceof MainContentAdapter.ContentRowHolder) {
                MainContentAdapter.ContentRowHolder rowHolder = (MainContentAdapter.ContentRowHolder) vh;
                RecyclerView contentRv = rowHolder.contentRecyclerView;
                if (contentRv != null && contentRv.getAdapter() != null) {
                    int count = contentRv.getAdapter().getItemCount();
                    if (count > 0) {
                        int targetPos = Math.max(0, Math.min(position, count - 1));
                        contentRv.scrollToPosition(targetPos);
                        contentRv.post(() -> {
                            RecyclerView.ViewHolder cardVh = contentRv.findViewHolderForAdapterPosition(targetPos);
                            if (cardVh != null) {
                                View c = cardVh.itemView.findViewById(R.id.card);
                                if (c != null) c.requestFocus();
                            } else if (contentRv.getChildCount() > 0) {
                                View c = contentRv.getChildAt(0).findViewById(R.id.card);
                                if (c != null) c.requestFocus();
                            }
                        });
                    }
                }
            }
        }, 80);
    }

    private RecyclerView findDockRecyclerView() {
        return mainContentAdapter != null ? mainContentAdapter.getDockRecyclerView() : null;
    }

    private ContentAdapter getContentAdapter(int rowIndex) {
        switch (rowIndex) {
            case 0: return continueWatchingAdapter;
            case 1: return featuredMoviesAdapter;
            case 2: return featuredTvAdapter;
            case 3: return actionThrillerAdapter;
            case 4: return recentlyAddedAdapter;
            default: return null;
        }
    }

    // ===== DOCK MAGNIFICATION & TOOLTIP =====

    private void magnifyDock(int focused) {
        RecyclerView dockRv = findDockRecyclerView();
        if (dockRv == null || dockRv.getLayoutManager() == null) return;
        int count = dockAdapter.getDockCount();
        for (int i = 0; i < count; i++) {
            View item = dockRv.getLayoutManager().findViewByPosition(i);
            View card = item != null ? item.findViewById(R.id.card) : null;
            if (card == null) continue;
            int d = focused < 0 ? Integer.MAX_VALUE : Math.abs(i - focused);
            float s = d == 0 ? DockAdapter.FOCUS_SCALE : d == 1 ? DockAdapter.NEIGHBOUR_SCALE : 1f;
            float lift = d == 0 ? 10f : d == 1 ? 4f : 0f;
            card.setPivotX(card.getWidth() / 2f);
            card.setPivotY(card.getHeight());
            card.animate().scaleX(s).scaleY(s).translationY(-lift * density)
                    .setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
            View shadow = item.findViewById(R.id.shadow);
            if (shadow != null) {
                shadow.setPivotX(shadow.getWidth() / 2f);
                shadow.setPivotY(card.getBottom() - shadow.getTop());
                shadow.animate().scaleX(s).scaleY(s).translationY(-lift * density)
                        .setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
            }
        }
    }

    private void showHeroTooltip(int position, View card) {
        MainContentAdapter.DockHolder holder = mainContentAdapter.getDockHolder();
        if (holder == null || holder.heroName == null) return;
        String label = dockAdapter.labelAt(position);
        if (label.isEmpty()) {
            holder.heroName.animate().alpha(0f).setDuration(120).start();
            return;
        }
        holder.heroName.setText(label);
        holder.heroName.animate().alpha(1f).setDuration(180).start();
        if (holder.dockDot != null) {
            holder.dockDot.animate().alpha(1f).setDuration(180).start();
        }
    }

    private void hideHeroTooltip() {
        MainContentAdapter.DockHolder holder = mainContentAdapter.getDockHolder();
        if (holder != null) {
            if (holder.heroName != null) holder.heroName.animate().alpha(0f).setDuration(120).start();
            if (holder.dockDot != null) holder.dockDot.animate().alpha(0f).setDuration(120).start();
        }
    }

    // ===== AMBIENT GLOW =====

    private void updateAmbientGlow(int color) {
        if (color == ambientColor) return;
        if (ambientColor == 0) {
            setAmbient(color);
            ambientGlow.animate().alpha(1f).setDuration(400).start();
        } else {
            ambientGlow.animate().alpha(0.35f).setDuration(150).withEndAction(() -> {
                setAmbient(color);
                ambientGlow.animate().alpha(1f).setDuration(300).start();
            }).start();
        }
    }

    private void setAmbient(int color) {
        ambientColor = color;
        int core = Color.argb(120, Color.red(color), Color.green(color), Color.blue(color));
        int mid = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color));
        ambientGradient.setColors(new int[]{core, mid, 0x00000000});
    }

    // ===== GRID (ALL APPS SHEET) =====

    private void showGrid() {
        if (gridContainer.getVisibility() == View.VISIBLE) return;
        isInGrid = true;
        gridContainer.setVisibility(View.VISIBLE);
        gridContainer.setAlpha(0f);
        gridContainer.animate().alpha(1f).setDuration(220).withEndAction(() -> {
            focusGridItem(lastGridFocused);
        }).start();
    }

    private void hideGrid() {
        if (gridContainer.getVisibility() != View.VISIBLE) return;
        isInGrid = false;
        gridContainer.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            gridContainer.setVisibility(View.GONE);
        }).start();
        hideGlass();
    }

    private void focusGridItem(int position) {
        int count = gridAdapter.getItemCount();
        if (count == 0) return;
        final int pos = Math.max(0, Math.min(position, count - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            if (card != null) card.requestFocus();
            return;
        }
        grid.smoothScrollToPosition(pos);
        grid.postDelayed(() -> {
            RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
            View target = h != null ? h.itemView.findViewById(R.id.card)
                    : (grid.getChildCount() > 0 ? grid.getChildAt(0).findViewById(R.id.card) : null);
            if (target != null) target.requestFocus();
        }, 50);
    }

    @Override
    public void onGridItemFocused(int position, View card) {
        lastGridFocused = position;
        isInGrid = true;
        magnifyDock(-1);
        hideHeroTooltip();
        updateGlassHighlight(position, card);
        updateAmbientGlow(gridAdapter.ambientColorAt(position));
    }

    @Override
    public boolean onGridItemKey(int position, int keyCode) {
        int count = gridAdapter.getItemCount();
        int columns = gridAdapter.getColumns();
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < columns) {
                hideGrid();
                mainContentRecyclerView.requestFocus();
                return true;
            }
            focusGridItem(position - columns);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int lastRowStart = ((count - 1) / columns) * columns;
            if (position >= lastRowStart) return true;
            focusGridItem(Math.min(position + columns, count - 1));
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (position % columns == 0) return true;
            focusGridItem(position - 1);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (position % columns == columns - 1 || position == count - 1) return true;
            focusGridItem(position + 1);
            return true;
        }
        return false;
    }

    @Override
    public void onGridItemMenu(int position, View card) {
        showGridMenu(position, card);
    }

    @Override
    public void onGridItemClick(int position, View card) {
        // App launch handled in adapter
    }

    // ===== GLASS HIGHLIGHT =====

    private void updateGlassHighlight(int position, View card) {
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;
        float w = card.getWidth() * GridAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * GridAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(Math.min(card.getWidth(), card.getHeight()) * GridAdapter.CORNER_RATIO * GridAdapter.FOCUS_SCALE + pad);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        View item = (View) card.getParent();
        Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
        grid.requestChildRectangleOnScreen(item, want, false);
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ===== MENUS & DIALOGS =====

    private void showDockMenu(int position, View card) {
        String pkg = dockAdapter.pkgAt(position);
        if (pkg.isEmpty()) return;
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = new PopupWindow(panel, (int) (220 * density), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);

        addInputRow(rows, popup, R.drawable.ic_unpin, getString(R.string.unpin_from_dock), -1, () -> {
            dockAdapter.unpin(pkg, allApps);
            focusAfterDockChange(pkg);
        });

        if (position > 0) {
            addInputRow(rows, popup, R.drawable.ic_left, getString(R.string.move_left), -1, () -> {
                dockAdapter.move(pkg, -1, allApps);
                focusAfterDockChange(pkg);
            });
        }
        if (position < dockAdapter.getDockCount() - 1) {
            addInputRow(rows, popup, R.drawable.ic_right, getString(R.string.move_right), -1, () -> {
                dockAdapter.move(pkg, 1, allApps);
                focusAfterDockChange(pkg);
            });
        }
        addInputRow(rows, popup, R.drawable.ic_info, getString(R.string.app_info), -1, () -> openAppInfo(pkg));

        popup.showAsDropDown(card, 0, (int) (-120 * density), Gravity.CENTER_HORIZONTAL);
        rows.getChildAt(0).requestFocus();
    }

    private void showGridMenu(int position, View card) {
        String pkg = gridAdapter.pkgAt(position);
        if (pkg.isEmpty()) return;
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = new PopupWindow(panel, (int) (220 * density), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);

        boolean docked = gridAdapter.isDocked(position);
        if (docked) {
            addInputRow(rows, popup, R.drawable.ic_unpin, getString(R.string.unpin_from_dock), -1, () -> {
                dockAdapter.unpin(pkg, allApps);
                gridAdapter.setDocked(pkg, false);
            });
        } else {
            addInputRow(rows, popup, R.drawable.ic_pin, getString(R.string.pin_to_dock), -1, () -> {
                if (dockAdapter.pin(pkg, allApps)) {
                    gridAdapter.setDocked(pkg, true);
                } else {
                    Toast.makeText(this, R.string.dock_full, Toast.LENGTH_SHORT).show();
                }
            });
        }
        addInputRow(rows, popup, R.drawable.ic_info, getString(R.string.app_info), -1, () -> openAppInfo(pkg));

        popup.showAsDropDown(card, 0, (int) (-120 * density), Gravity.CENTER_HORIZONTAL);
        rows.getChildAt(0).requestFocus();
    }

    private void focusAfterDockChange(String pkg) {
        hideHeroTooltip();
        dockAdapter.reload(allApps);
        dockAdapter.notifyDataSetChanged();
        mainContentAdapter.notifyItemChanged(1);
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:" + pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Can't show info for " + pkg, Toast.LENGTH_SHORT).show();
        }
    }

    // ===== INPUTS POPUP =====

    private void showInputs() {
        View anchor = btnInputs != null ? btnInputs : root;
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int width = (int) (240 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }

        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTv));
        }

        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        popup.showAsDropDown(anchor, 0, (int) (8 * density), Gravity.START);
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x15FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    // ===== TIME, GREETING & BACKGROUND =====

    private static final int[] GREETINGS = {
            R.string.good_morning, R.string.good_afternoon, R.string.good_evening, R.string.good_night
    };

    private static int dayPart() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 5) return 3;
        if (hour < 12) return 0;
        if (hour < 17) return 1;
        if (hour < 22) return 2;
        return 3;
    }

    private void updateGreeting() {
        if (greeting != null) {
            String text = getString(GREETINGS[dayPart()]);
            String name = getString(R.string.user_name).trim();
            greeting.setText(name.isEmpty() ? text : text + ", " + name);
        }
    }

    private void updateBackground() {
        SharedPreferences prefs = getSharedPreferences("tvhome", Context.MODE_PRIVATE);
        int chosenWallpaper = prefs.getInt("wallpaper", -1);
        int res;
        if (chosenWallpaper >= 0 && chosenWallpaper < WALLPAPERS.length) {
            res = WALLPAPERS[chosenWallpaper];
        } else {
            res = TIME_BACKGROUNDS[dayPart()];
        }

        if (res == currentBgRes) return;
        currentBgRes = res;
        heroBackground.setImageResource(res);
    }

    private void updateWifiLabel() {
        if (btnWifi == null) return;
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network net = cm != null ? cm.getActiveNetwork() : null;
        NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
        if (caps == null) {
            setWifiPill(R.drawable.ic_wifi_off, getString(R.string.offline), COLOR_ERROR);
            return;
        }
        boolean online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            setWifiPill(R.drawable.ic_ethernet, online ? getString(R.string.ethernet) : getString(R.string.no_internet),
                    online ? 0 : COLOR_WARN);
            return;
        }
        WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
        WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
        int bars = info != null ? WifiManager.calculateSignalLevel(info.getRssi(), WIFI_ICONS.length) : 0;
        if (!online) {
            setWifiPill(WIFI_ICONS[bars], getString(R.string.no_internet), COLOR_WARN);
            return;
        }
        setWifiPill(WIFI_ICONS[bars], getString(R.string.wifi), 0);
    }

    private void setWifiPill(int icon, String label, int alertColor) {
        btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        btnWifi.setContentDescription(label);
        boolean iconOnly = alertColor == 0;
        ViewGroup.LayoutParams lp = btnWifi.getLayoutParams();
        lp.width = iconOnly ? (int) (42 * density) : ViewGroup.LayoutParams.WRAP_CONTENT;
        btnWifi.setLayoutParams(lp);
        btnWifi.setPaddingRelative((int) ((iconOnly ? 10 : 16) * density), 0, (int) ((iconOnly ? 10 : 20) * density), 0);
        btnWifi.setCompoundDrawablePadding(iconOnly ? 0 : (int) (8 * density));
        if (iconOnly) {
            btnWifi.setText("");
            return;
        }
        SpannableString text = new SpannableString("\u25CF  " + label);
        text.setSpan(new ForegroundColorSpan(alertColor), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        btnWifi.setText(text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isViewInRecyclerView(View view, RecyclerView rv) {
        if (view == null || rv == null) return false;
        View p = view;
        while (p != null) {
            if (p == rv) return true;
            if (p.getParent() instanceof View) {
                p = (View) p.getParent();
            } else {
                break;
            }
        }
        return false;
    }
}
