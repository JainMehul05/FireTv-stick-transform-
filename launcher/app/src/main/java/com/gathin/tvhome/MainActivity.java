package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.res.TypedArray;

/**
 * Premium Cinematic Fire TV Launcher - Main Activity
 * Features: Hero section, floating dock, cinematic content rows, app library grid, glass highlight, ambient glow
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

    // UI Components
    private FrameLayout root;
    private ImageView heroBackground;
    private View heroScrim;
    private View ambientGlow;
    private GradientDrawable ambientGradient;
    private RecyclerView mainContentRecyclerView;
    private RecyclerView grid;
    private FrameLayout gridContainer;
    private GlassView glass;

    // Top bar buttons (for focus/click handling)
    private TextView btnInputs;
    private TextView btnWifi;
    private TextView btnSettings;

    // Adapters
    private MainContentAdapter mainContentAdapter;
    private DockAdapter dockAdapter;
    private GridAdapter gridAdapter;
    private List<DockAdapter.Entry> allApps = new ArrayList<>();

    // Content Adapters
    private ContentAdapter featuredMoviesAdapter;
    private ContentAdapter featuredTvAdapter;
    private ContentAdapter continueWatchingAdapter;

    // Animation
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    private int ambientColor;
    private float density;

    // Focus tracking
    private int lastDockFocused = 0;
    private int lastGridFocused = 0;
    private boolean isInGrid = false;
    private int currentContentRow = -1;

    // Network
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

    // Time updates
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

    // Hero backgrounds (time-of-day)
    private static final int[] BACKGROUNDS = {
            R.drawable.bg_morning, R.drawable.bg_afternoon, R.drawable.bg_evening, R.drawable.bg_night
    };
    private int bgRes = 0;

    // Live TV package
    private String liveTvPkg;

    // Dock UI elements (accessed via dock section)
    private View dockDot;
    private TextView heroName;

    // Hero artwork (for cinematic hero)
    private ImageView heroArtwork;
    private View heroGradientOverlay;
    private View heroSideVignette;
    private TextView heroTitle;
    private TextView heroCategory;
    private TextView heroYear;
    private TextView heroDuration;
    private View heroPlayButton;
    private View heroInfoButton;
    private View heroActions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appContext = this;
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        liveTvPkg = getString(R.string.live_tv_package).trim();

        // Find views
        root = findViewById(R.id.root);
        heroBackground = findViewById(R.id.heroBackground);
        heroScrim = findViewById(R.id.heroScrim);
        ambientGlow = findViewById(R.id.ambientGlow);
        mainContentRecyclerView = findViewById(R.id.mainContentRecyclerView);
        grid = findViewById(R.id.grid);
        gridContainer = findViewById(R.id.gridContainer);
        glass = findViewById(R.id.glass);

        // Setup top bar buttons and hero views (will be found in onResume when HeroHolder is created)
        setupTopBarButtons();

        // Setup ambient glow
        ambientGradient = new GradientDrawable();
        ambientGradient.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGradient.setGradientCenter(0.32f, 0.42f);
        ambientGlow.setBackground(ambientGradient);
        ambientGlow.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGradient.setGradientRadius((r - l) * 0.62f));

        // Setup grid RecyclerView
        final int columns = Math.max(1, getResources().getInteger(R.integer.columns));
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, columns);
        grid.setLayoutManager(gridLayoutManager);
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.addItemDecoration(new GridSpacingDecoration(columns, density));

        // Keep glass synced with grid scroll
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Glass highlight springs
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        // Initial load
        loadAllApps();
        setupContentRows();
        setupMainContentAdapter();
    }

    /**
     * Setup click listeners for top bar utility buttons.
     * These buttons are in the HeroHolder, so we find them when the holder is created.
     */
    private void setupTopBarButtons() {
        mainContentRecyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                // Check if this is the hero section (position 0)
                RecyclerView.ViewHolder vh = mainContentRecyclerView.getChildViewHolder(view);
                if (vh instanceof MainContentAdapter.HeroHolder) {
                    // Find top bar buttons
                    btnInputs = view.findViewById(R.id.btnInputs);
                    btnWifi = view.findViewById(R.id.btnWifi);
                    btnSettings = view.findViewById(R.id.btnSettings);

                    // Wire up click listeners
                    if (btnInputs != null) {
                        btnInputs.setOnClickListener(v -> showInputs());
                        btnInputs.setOnKeyListener((v, keyCode, event) -> {
                            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                    focusFirstDockItem();
                                    return true;
                                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                    if (btnWifi != null) btnWifi.requestFocus();
                                    return true;
                                }
                            }
                            return false;
                        });
                    }
                    if (btnWifi != null) {
                        btnWifi.setOnClickListener(v -> openWifiSettings());
                        btnWifi.setOnKeyListener((v, keyCode, event) -> {
                            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                    focusFirstDockItem();
                                    return true;
                                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                    if (btnInputs != null) btnInputs.requestFocus();
                                    return true;
                                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                    if (btnSettings != null) btnSettings.requestFocus();
                                    return true;
                                }
                            }
                            return false;
                        });
                    }
                    if (btnSettings != null) {
                        btnSettings.setOnClickListener(v -> openSettings());
                        btnSettings.setOnKeyListener((v, keyCode, event) -> {
                            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                    // Down from settings goes to hero actions first
                                    focusHeroActions();
                                    return true;
                                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                    if (btnWifi != null) btnWifi.requestFocus();
                                    return true;
                                }
                            }
                            return false;
                        });
                    }

                    // Find hero cinematic views
                    heroArtwork = view.findViewById(R.id.heroArtwork);
                    heroGradientOverlay = view.findViewById(R.id.heroGradientOverlay);
                    heroSideVignette = view.findViewById(R.id.heroSideVignette);
                    heroTitle = view.findViewById(R.id.heroTitle);
                    heroCategory = view.findViewById(R.id.heroCategory);
                    heroYear = view.findViewById(R.id.heroYear);
                    heroDuration = view.findViewById(R.id.heroDuration);
                    heroPlayButton = view.findViewById(R.id.heroPlayButton);
                    heroInfoButton = view.findViewById(R.id.heroInfoButton);
                    heroActions = view.findViewById(R.id.heroActions);

                    // Setup hero action buttons
                    if (heroPlayButton != null) {
                        heroPlayButton.setOnClickListener(v -> {
                            // Launch featured app or show toast
                            Toast.makeText(MainActivity.this, "Play feature not yet implemented", Toast.LENGTH_SHORT).show();
                        });
                        heroPlayButton.setOnKeyListener((v, keyCode, event) -> {
                            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                    // Up from hero actions goes to top bar (settings button)
                                    if (btnSettings != null) btnSettings.requestFocus();
                                    return true;
                                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                    // Down from hero actions goes to dock
                                    focusFirstDockItem();
                                    return true;
                                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                    if (heroInfoButton != null) heroInfoButton.requestFocus();
                                    return true;
                                }
                            }
                            return false;
                        });
                    }
                    if (heroInfoButton != null) {
                        heroInfoButton.setOnClickListener(v -> {
                            Toast.makeText(MainActivity.this, "More info feature not yet implemented", Toast.LENGTH_SHORT).show();
                        });
                        heroInfoButton.setOnKeyListener((v, keyCode, event) -> {
                            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                    // Up from hero actions goes to top bar (settings button)
                                    if (btnSettings != null) btnSettings.requestFocus();
                                    return true;
                                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                    // Down from hero actions goes to dock
                                    focusFirstDockItem();
                                    return true;
                                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                    if (heroPlayButton != null) heroPlayButton.requestFocus();
                                    return true;
                                }
                            }
                            return false;
                        });
                    }

                    // Update wifi label now that we have the button
                    updateWifiLabel();
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
                // Clean up if needed
            }
        });
    }

    private void focusFirstDockItem() {
        focusDockItem(0);
    }

    private void focusLastDockItem() {
        focusDockItem(dockAdapter.getDockCount() - 1);
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

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
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

        // Initialize adapters
        final int columns = getResources().getInteger(R.integer.columns);
        Set<String> dockedPackages = new HashSet<>();
        dockAdapter = new DockAdapter(this, this, columns);
        dockAdapter.reload(allApps);
        for (DockAdapter.Entry e : dockAdapter.getItems()) dockedPackages.add(e.pkg);

        gridAdapter = new GridAdapter(this, this, hidden, dockedPackages, columns);
        gridAdapter.reload();
    }

    private void setupContentRows() {
        // Featured Movies
        featuredMoviesAdapter = new ContentAdapter(this, null);
        List<ContentAdapter.ContentItem> movies = new ArrayList<>();
        String[] movieTitles = getResources().getStringArray(R.array.featured_movie_titles);
        String[] movieSubtitles = getResources().getStringArray(R.array.featured_movie_subtitles);
        TypedArray movieArtwork = getResources().obtainTypedArray(R.array.featured_movie_artwork);
        for (int i = 0; i < movieTitles.length; i++) {
            movies.add(new ContentAdapter.ContentItem(
                    movieTitles[i], movieSubtitles[i], movieArtwork.getDrawable(i), i));
        }
        movieArtwork.recycle();
        featuredMoviesAdapter.setItems(movies);

        // Featured TV Shows
        featuredTvAdapter = new ContentAdapter(this, null);
        List<ContentAdapter.ContentItem> tvShows = new ArrayList<>();
        String[] tvTitles = getResources().getStringArray(R.array.featured_tv_titles);
        String[] tvSubtitles = getResources().getStringArray(R.array.featured_tv_subtitles);
        TypedArray tvArtwork = getResources().obtainTypedArray(R.array.featured_tv_artwork);
        for (int i = 0; i < tvTitles.length; i++) {
            tvShows.add(new ContentAdapter.ContentItem(
                    tvTitles[i], tvSubtitles[i], tvArtwork.getDrawable(i), i));
        }
        tvArtwork.recycle();
        featuredTvAdapter.setItems(tvShows);

        // Continue Watching
        continueWatchingAdapter = new ContentAdapter(this, null);
        List<ContentAdapter.ContentItem> continueItems = new ArrayList<>();
        String[] cwTitles = getResources().getStringArray(R.array.continue_watching_titles);
        String[] cwSubtitles = getResources().getStringArray(R.array.continue_watching_subtitles);
        TypedArray cwArtwork = getResources().obtainTypedArray(R.array.continue_watching_artwork);
        for (int i = 0; i < cwTitles.length; i++) {
            continueItems.add(new ContentAdapter.ContentItem(
                    cwTitles[i], cwSubtitles[i], cwArtwork.getDrawable(i), i));
        }
        cwArtwork.recycle();
        continueWatchingAdapter.setItems(continueItems);
        
        // Set up content row listeners for hero updates
        setupContentRowListeners();
    }
    
    private void setupContentRowListeners() {
        // Featured Movies - update hero on focus
        featuredMoviesAdapter.setListener(new ContentAdapter.ContentListener() {
            @Override
            public void onContentFocused(int position, View card) {
                if (mainContentAdapter != null) {
                    // Update hero with movie data
                    updateHeroForMovie(position);
                }
            }

            @Override
            public void onContentClick(int position, View card) {
                // Handle content click
            }

            @Override
            public boolean onContentKey(int position, int keyCode) {
                return onContentItemKey(0, position, keyCode);
            }
        });
        
        // Featured TV Shows
        featuredTvAdapter.setListener(new ContentAdapter.ContentListener() {
            @Override
            public void onContentFocused(int position, View card) {
                if (mainContentAdapter != null) {
                    updateHeroForTVShow(position);
                }
            }

            @Override
            public void onContentClick(int position, View card) {
            }

            @Override
            public boolean onContentKey(int position, int keyCode) {
                return onContentItemKey(1, position, keyCode);
            }
        });
        
        // Continue Watching
        continueWatchingAdapter.setListener(new ContentAdapter.ContentListener() {
            @Override
            public void onContentFocused(int position, View card) {
                if (mainContentAdapter != null) {
                    updateHeroForContinueWatching(position);
                }
            }

            @Override
            public void onContentClick(int position, View card) {
            }

            @Override
            public boolean onContentKey(int position, int keyCode) {
                return onContentItemKey(2, position, keyCode);
            }
        });
    }
    
    private void updateHeroForMovie(int position) {
        if (heroArtwork == null) return;
        String[] titles = getResources().getStringArray(R.array.featured_movie_titles);
        String[] subtitles = getResources().getStringArray(R.array.featured_movie_subtitles);
        TypedArray artwork = getResources().obtainTypedArray(R.array.featured_movie_artwork);
        
        if (position >= 0 && position < titles.length) {
            heroArtwork.setImageDrawable(artwork.getDrawable(position));
            heroTitle.setText(titles[position]);
            
            // Parse subtitle for metadata
            String subtitle = subtitles[position];
            String[] parts = subtitle.split(" • ");
            if (parts.length >= 3) {
                heroYear.setText(parts[0]);
                heroCategory.setText(parts[1]);
                heroDuration.setText(parts[2]);
            } else {
                heroYear.setText("");
                heroCategory.setText(subtitle);
                heroDuration.setText("");
            }
        }
        artwork.recycle();
    }
    
    private void updateHeroForTVShow(int position) {
        if (heroArtwork == null) return;
        String[] titles = getResources().getStringArray(R.array.featured_tv_titles);
        String[] subtitles = getResources().getStringArray(R.array.featured_tv_subtitles);
        TypedArray artwork = getResources().obtainTypedArray(R.array.featured_tv_artwork);
        
        if (position >= 0 && position < titles.length) {
            heroArtwork.setImageDrawable(artwork.getDrawable(position));
            heroTitle.setText(titles[position]);
            
            String subtitle = subtitles[position];
            String[] parts = subtitle.split(" • ");
            if (parts.length >= 3) {
                heroYear.setText(parts[0]);
                heroCategory.setText(parts[1]);
                heroDuration.setText(parts[2]);
            } else {
                heroYear.setText("");
                heroCategory.setText(subtitle);
                heroDuration.setText("");
            }
        }
        artwork.recycle();
    }
    
    private void updateHeroForContinueWatching(int position) {
        if (heroArtwork == null) return;
        String[] titles = getResources().getStringArray(R.array.continue_watching_titles);
        String[] subtitles = getResources().getStringArray(R.array.continue_watching_subtitles);
        TypedArray artwork = getResources().obtainTypedArray(R.array.continue_watching_artwork);
        
        if (position >= 0 && position < titles.length) {
            heroArtwork.setImageDrawable(artwork.getDrawable(position));
            heroTitle.setText(titles[position]);
            heroCategory.setText("CONTINUE WATCHING");
            heroYear.setText("");
            heroDuration.setText(subtitles[position]);
        }
        artwork.recycle();
    }

    private void setupMainContentAdapter() {
        mainContentAdapter = new MainContentAdapter(this, this, dockAdapter);

        // Add content rows
        List<MainContentAdapter.ContentRow> rows = new ArrayList<>();
        rows.add(new MainContentAdapter.ContentRow(
                getString(R.string.movies), R.drawable.ic_streaming, featuredMoviesAdapter, 0));
        rows.add(new MainContentAdapter.ContentRow(
                getString(R.string.tv_shows), R.drawable.ic_entertainment, featuredTvAdapter, 1));
        rows.add(new MainContentAdapter.ContentRow(
                getString(R.string.continue_watching), R.drawable.ic_music, continueWatchingAdapter, 2));
        mainContentAdapter.setContentRows(rows);

        // Apps row callback
        mainContentAdapter.setAppsRowCallback(() -> showGrid());

        // Setup main content RecyclerView
        LinearLayoutManager lm = new LinearLayoutManager(this);
        mainContentRecyclerView.setLayoutManager(lm);
        mainContentRecyclerView.setHasFixedSize(false);
        mainContentRecyclerView.setItemAnimator(null);
        mainContentRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mainContentRecyclerView.setAdapter(mainContentAdapter);

        // Add content listener
        featuredMoviesAdapter.setListener(new ContentAdapter.ContentListener() {
            @Override
            public void onContentFocused(int position, View card) {
                if (mainContentAdapter != null) {
                    // Notify via main content adapter listener
                }
            }

            @Override
            public void onContentClick(int position, View card) {
                // Handle content click
            }

            @Override
            public boolean onContentKey(int position, int keyCode) {
                return onContentItemKey(0, position, keyCode);
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
        
        // Set initial hero content (first movie)
        mainContentRecyclerView.post(() -> {
            updateHeroForMovie(0);
        });
        
        handler.postDelayed(minuteTick, MINUTE_MS);

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);

        // Restore focus to main content
        mainContentRecyclerView.post(() -> {
            View focused = getCurrentFocus();
            if (focused != null && isViewInRecyclerView(focused, mainContentRecyclerView)) return;
            if (focused != null && isViewInRecyclerView(focused, grid)) return;
            // Focus first dock item
            focusDockItem(lastDockFocused);
        });
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
            // Exit grid back to main content
            hideGrid();
            mainContentRecyclerView.requestFocus();
        } else {
            // Home screen: Back does nothing
        }
    }

    // ===== MAINCONTENT LISTENER =====

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
            // Up from dock goes to top bar (settings button)
            focusTopBarSettings();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            // Down from dock goes to first content row
            focusFirstContentRow();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // Handled by RecyclerView
            return false;
        }
        return false;
    }
    
    private void focusTopBarSettings() {
        mainContentRecyclerView.post(() -> {
            RecyclerView.ViewHolder vh = mainContentRecyclerView.findViewHolderForAdapterPosition(0);
            if (vh instanceof MainContentAdapter.HeroHolder) {
                View heroView = vh.itemView;
                View settingsBtn = heroView.findViewById(R.id.btnSettings);
                if (settingsBtn != null) {
                    settingsBtn.requestFocus();
                }
            }
        });
    }

    @Override
    public boolean onDockItemHorizontalKey(int position, int keyCode) {
        int count = dockAdapter.getDockCount();
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (position == 0) {
                // At first item, go to hero actions
                focusHeroActions();
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (position == count - 1) {
                // At last item, stay
                return true;
            }
        }
        return false; // Let RecyclerView handle
    }

    @Override
    public boolean onContentItemKey(int rowIndex, int position, int keyCode) {
        ContentAdapter adapter = getContentAdapter(rowIndex);
        if (adapter == null) return false;
        int count = adapter.getItemCount();
        
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (position == 0) {
                // At first item of content row - go left
                if (rowIndex == 0) {
                    // First content row: go to hero actions
                    focusHeroActions();
                    return true;
                } else {
                    // Go to previous content row, last item
                    ContentAdapter prevAdapter = getContentAdapter(rowIndex - 1);
                    if (prevAdapter != null) {
                        focusContentItem(rowIndex - 1, prevAdapter.getItemCount() - 1);
                        return true;
                    }
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (position == count - 1) {
                // At last item of content row - go right
                if (rowIndex == 2) { // Last content row (Continue Watching)
                    // Focus apps row - let RecyclerView handle
                    return false;
                } else {
                    // Go to next content row, first item
                    focusContentItem(rowIndex + 1, 0);
                    return true;
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (rowIndex == 0) {
                // First content row: go to dock
                focusDockItem(lastDockFocused);
                return true;
            } else {
                // Go to previous content row, same position
                ContentAdapter prevAdapter = getContentAdapter(rowIndex - 1);
                if (prevAdapter != null) {
                    int newPos = Math.min(position, prevAdapter.getItemCount() - 1);
                    focusContentItem(rowIndex - 1, newPos);
                    return true;
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (rowIndex == 2) { // Last content row
                // Go to apps row - let RecyclerView handle
                return false;
            } else {
                // Go to next content row, same position
                ContentAdapter nextAdapter = getContentAdapter(rowIndex + 1);
                if (nextAdapter != null) {
                    int newPos = Math.min(position, nextAdapter.getItemCount() - 1);
                    focusContentItem(rowIndex + 1, newPos);
                    return true;
                }
            }
        }
        return false;
    }
    
    private void focusHeroActions() {
        mainContentRecyclerView.post(() -> {
            RecyclerView.ViewHolder vh = mainContentRecyclerView.findViewHolderForAdapterPosition(0);
            if (vh instanceof MainContentAdapter.HeroHolder) {
                View heroView = vh.itemView;
                View actions = heroView.findViewById(R.id.heroActions);
                if (actions != null) {
                    actions.requestFocus();
                }
            }
        });
    }

    @Override
    public void onDockItemMenu(int position, View card) {
        showDockMenu(position, card);
    }

    @Override
    public void onDockItemClick(int position, View card) {
        // Click handled in adapter
    }

    @Override
    public void onContentItemFocused(int rowIndex, int position, View card) {
        currentContentRow = rowIndex;
        isInGrid = false;
        magnifyDock(-1);
        hideHeroTooltip();
        updateGlassHighlightForContent(rowIndex, position, card);
        // Ambient glow from content item
        ContentAdapter adapter = getContentAdapter(rowIndex);
        if (adapter != null) {
            // Use a default ambient color for content
            updateAmbientGlow(0xFF7C4DFF);
        }
    }

    @Override
    public void onContentItemClick(int rowIndex, int position, View card) {
        // Handle content click - could launch detail view
    }

    @Override
    public void onAppsRowClick() {
        startActivity(new Intent(this, AppLibraryActivity.class));
    }

    // ===== DOCK LISTENER =====

    @Override
    public boolean onDockItemVerticalKey(int position, int keyCode) {
        // Delegate to main content listener
        return onDockItemKey(position, keyCode);
    }

    // ===== GRID LISTENER =====

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
                // Top row: go back to main content
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
        // Click handled in adapter
    }

    // ===== HELPER METHODS =====

    private ContentAdapter getContentAdapter(int rowIndex) {
        switch (rowIndex) {
            case 0: return featuredMoviesAdapter;
            case 1: return featuredTvAdapter;
            case 2: return continueWatchingAdapter;
            default: return null;
        }
    }

    private void focusFirstContentRow() {
        // Scroll main content to first content row and focus first item
        mainContentRecyclerView.post(() -> {
            RecyclerView.ViewHolder vh = mainContentRecyclerView.findViewHolderForAdapterPosition(2); // First content row
            if (vh instanceof MainContentAdapter.ContentRowHolder) {
                MainContentAdapter.ContentRowHolder rowHolder = (MainContentAdapter.ContentRowHolder) vh;
                RecyclerView contentRv = rowHolder.contentRecyclerView;
                if (contentRv.getAdapter() != null && contentRv.getAdapter().getItemCount() > 0) {
                    View firstItem = contentRv.getLayoutManager().findViewByPosition(0);
                    if (firstItem != null) {
                        firstItem.findViewById(R.id.card).requestFocus();
                    }
                }
            }
        });
    }

    private void focusContentItem(int rowIndex, int position) {
        mainContentRecyclerView.post(() -> {
            int adapterPosition = rowIndex + 2; // Hero(0) + Dock(1) + content rows
            RecyclerView.ViewHolder vh = mainContentRecyclerView.findViewHolderForAdapterPosition(adapterPosition);
            if (vh instanceof MainContentAdapter.ContentRowHolder) {
                MainContentAdapter.ContentRowHolder rowHolder = (MainContentAdapter.ContentRowHolder) vh;
                RecyclerView contentRv = rowHolder.contentRecyclerView;
                if (contentRv.getAdapter() != null && contentRv.getAdapter().getItemCount() > 0) {
                    int targetPos = Math.min(position, contentRv.getAdapter().getItemCount() - 1);
                    View targetItem = contentRv.getLayoutManager().findViewByPosition(targetPos);
                    if (targetItem != null) {
                        targetItem.findViewById(R.id.card).requestFocus();
                    }
                }
            }
        });
    }

    // ===== DOCK MAGNIFICATION =====

    private void magnifyDock(int focused) {
        // Find dockRecyclerView in the dock section
        RecyclerView dockRv = findDockRecyclerView();
        if (dockRv == null) return;
        
        RecyclerView.LayoutManager lm = dockRv.getLayoutManager();
        if (lm == null) return;
        int count = dockAdapter.getDockCount();
        for (int i = 0; i < count; i++) {
            View item = lm.findViewByPosition(i);
            View card = item != null ? item.findViewById(R.id.card) : null;
            if (card == null) continue;
            int d = focused < 0 ? Integer.MAX_VALUE : Math.abs(i - focused);
            float s = d == 0 ? DockAdapter.FOCUS_SCALE : d == 1 ? DockAdapter.NEIGHBOUR_SCALE : 1f;
            float lift = d == 0 ? 12f : d == 1 ? 5f : 0f;
            card.setPivotX(card.getWidth() / 2f);
            card.setPivotY(card.getHeight());
            card.animate().scaleX(s).scaleY(s).translationY(-lift * density)
                    .setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
            View shadow = item.findViewById(R.id.shadow);
            if (shadow != null) {
                shadow.setPivotX(shadow.getWidth() / 2f);
                shadow.setPivotY(card.getBottom() - shadow.getTop());
                shadow.animate().scaleX(s).scaleY(s).translationY(-lift * density)
                        .setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
            }
        }
    }

    private RecyclerView findDockRecyclerView() {
        // Search through main content RecyclerView for dock section
        RecyclerView.ViewHolder vh = mainContentRecyclerView.findViewHolderForAdapterPosition(1);
        if (vh instanceof MainContentAdapter.DockHolder) {
            return ((MainContentAdapter.DockHolder) vh).dockRecyclerView;
        }
        return null;
    }

    // ===== HERO TOOLTIP =====

    private void showHeroTooltip(int position, View card) {
        if (heroName == null) {
            // Find heroName in dock section
            RecyclerView.ViewHolder vh = mainContentRecyclerView.findViewHolderForAdapterPosition(1);
            if (vh instanceof MainContentAdapter.DockHolder) {
                View dockView = vh.itemView;
                heroName = dockView.findViewById(R.id.heroName);
                dockDot = dockView.findViewById(R.id.dockDot);
            }
        }
        if (heroName == null) return;

        heroName.setText(dockAdapter.labelAt(position));
        heroName.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float iconTop = r.bottom - r.height() * DockAdapter.FOCUS_SCALE - 12 * density;
        heroName.setX(r.exactCenterX() - heroName.getMeasuredWidth() / 2f);
        heroName.setY(iconTop - 8 * density - heroName.getMeasuredHeight());
        if (dockDot != null) {
            dockDot.setX(r.exactCenterX() - dockDot.getWidth() / 2f);
            dockDot.setY(r.bottom + 3 * density);
            dockDot.animate().alpha(1f).setDuration(160).start();
        }
        heroName.animate().alpha(1f).setDuration(160).start();
    }

    private void hideHeroTooltip() {
        if (heroName == null) return;
        heroName.animate().alpha(0f).setDuration(160).start();
        if (dockDot != null) {
            dockDot.animate().alpha(0f).setDuration(160).start();
        }
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

    private void updateGlassHighlightForContent(int rowIndex, int position, View card) {
        // Similar to grid highlight but for content cards
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;
        float w = card.getWidth() * ContentAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * ContentAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(Math.min(card.getWidth(), card.getHeight()) * ContentAdapter.CORNER_RATIO * ContentAdapter.FOCUS_SCALE + pad);
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
        // Find the content RecyclerView
        RecyclerView.ViewHolder vh = mainContentRecyclerView.findViewHolderForAdapterPosition(rowIndex + 2);
        if (vh instanceof MainContentAdapter.ContentRowHolder) {
            RecyclerView contentRv = ((MainContentAdapter.ContentRowHolder) vh).contentRecyclerView;
            contentRv.requestChildRectangleOnScreen(item, want, false);
        }
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ===== AMBIENT GLOW =====

    private void updateAmbientGlow(int color) {
        if (color == ambientColor) return;
        if (ambientColor == 0) {
            setAmbient(color);
            ambientGlow.animate().alpha(1f).setDuration(600).start();
        } else {
            ambientGlow.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                setAmbient(color);
                ambientGlow.animate().alpha(1f).setDuration(500).start();
            }).start();
        }
        ambientColor = color;
    }

    private void setAmbient(int color) {
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        ambientGradient.setColors(new int[]{0x26000000 | c, 0x0D000000 | c, 0x00000000 | c});
    }

    // ===== GRID VISIBILITY =====

    private void showGrid() {
        if (gridContainer.getVisibility() == View.VISIBLE) return;
        isInGrid = true;
        gridContainer.setAlpha(0f);
        gridContainer.setVisibility(View.VISIBLE);
        gridContainer.animate().alpha(1f).setDuration(300).setInterpolator(new DecelerateInterpolator()).start();
        hideHeroTooltip();
        magnifyDock(-1);
        hideGlass();
        focusGridItem(lastGridFocused);
    }

    private void hideGrid() {
        if (gridContainer.getVisibility() != View.VISIBLE) return;
        isInGrid = false;
        gridContainer.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            gridContainer.setVisibility(View.GONE);
        }).start();
        hideGlass();
    }

    // ===== FOCUS HELPERS =====

    private void focusDockItem(int position) {
        RecyclerView dockRv = findDockRecyclerView();
        if (dockRv == null) return;
        
        int count = dockAdapter.getDockCount();
        if (count == 0) return;
        final int pos = Math.max(0, Math.min(position, count - 1));
        RecyclerView.ViewHolder vh = dockRv.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.findViewById(R.id.card).requestFocus();
            return;
        }
        dockRv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        dockRv.smoothScrollToPosition(pos);
    }

    private void focusGridItem(int position) {
        int count = gridAdapter.getItemCount();
        if (count == 0) return;
        final int pos = Math.max(0, Math.min(position, count - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.findViewById(R.id.card).requestFocus();
            return;
        }
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    private boolean isViewInRecyclerView(View view, RecyclerView rv) {
        View parent = (View) view.getParent();
        while (parent != null && parent != rv) {
            parent = (View) parent.getParent();
        }
        return parent == rv;
    }

    // ===== MENUS =====

    private void showDockMenu(int position, View card) {
        String pkg = dockAdapter.pkgAt(position);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int width = (int) (220 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        addInputRow(rows, popup, R.drawable.ic_unpin, getString(R.string.unpin_from_dock), -1, () -> {
            dockAdapter.unpin(pkg, allApps);
            focusAfterDockChange(pkg);
        });
        if (position > 0) addInputRow(rows, popup, R.drawable.ic_left, getString(R.string.move_left), -1, () -> {
            dockAdapter.move(pkg, -1, allApps);
            focusAfterDockChange(pkg);
        });
        if (position < dockAdapter.getDockCount() - 1) addInputRow(rows, popup, R.drawable.ic_right, getString(R.string.move_right), -1, () -> {
            dockAdapter.move(pkg, 1, allApps);
            focusAfterDockChange(pkg);
        });
        addInputRow(rows, popup, R.drawable.ic_info, getString(R.string.app_info), -1, () ->
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))));

        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        float top = r.bottom - r.height() * DockAdapter.FOCUS_SCALE - 12 * density;
        int x = (int) (r.exactCenterX() - width / 2f);
        int y = (int) (top - 12 * density - panel.getMeasuredHeight());
        heroName.animate().alpha(0f).setDuration(120).start();
        popup.setOnDismissListener(() -> {
            if (dockAdapter.isDocked(lastDockFocused)) heroName.animate().alpha(1f).setDuration(160).start();
        });
        panel.setAlpha(0f);
        panel.setTranslationY(8 * density);
        popup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
        panel.animate().alpha(1f).translationY(0f).setDuration(200)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void showGridMenu(int position, View card) {
        String pkg = gridAdapter.pkgAt(position);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int width = (int) (220 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        int dockMax = getResources().getInteger(R.integer.columns);
        if (dockAdapter.getDockCount() < dockMax) {
            addInputRow(rows, popup, R.drawable.ic_pin, getString(R.string.pin_to_dock), -1, () -> {
                if (dockAdapter.pin(pkg, allApps)) focusAfterDockChange(pkg);
                else Toast.makeText(this, R.string.dock_full, Toast.LENGTH_SHORT).show();
            });
        }
        addInputRow(rows, popup, R.drawable.ic_info, getString(R.string.app_info), -1, () ->
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))));

        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int x = (int) (r.exactCenterX() - width / 2f);
        int y = (int) (r.top - 12 * density - panel.getMeasuredHeight());
        panel.setAlpha(0f);
        panel.setTranslationY(8 * density);
        popup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
        panel.animate().alpha(1f).translationY(0f).setDuration(200)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void focusAfterDockChange(String pkg) {
        hideHeroTooltip();
        dockAdapter.reload(allApps);
        dockAdapter.notifyDataSetChanged();
        mainContentAdapter.notifyItemChanged(1);
        mainContentRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mainContentRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int pos = dockAdapter.positionOf(pkg);
                if (pos >= 0) focusDockItem(pos);
            }
        });
    }

    // ===== INPUTS MENU =====

    private void showInputs() {
        // Find btnInputs in hero section
        RecyclerView.ViewHolder vh = mainContentRecyclerView.findViewHolderForAdapterPosition(0);
        View anchor = null;
        if (vh instanceof MainContentAdapter.HeroHolder) {
            anchor = vh.itemView.findViewById(R.id.btnInputs);
        }
        if (anchor == null) return;

        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
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

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
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

    // ===== HOME SCREEN MENU =====

    private void showHomeScreenMenu(View anchor) {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int width = (int) (290 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        boolean on = HomeRedirectService.isEnabled(this);
        addInputRow(rows, popup, R.drawable.ic_settings, getString(on ? R.string.use_amazon_home : R.string.use_this_home), -1, () -> {
            if (!HomeRedirectService.setEnabled(this, !on)) {
                Toast.makeText(this, R.string.redirect_needs_permission, Toast.LENGTH_LONG).show();
            }
        });
        popup.showAsDropDown(anchor, anchor.getWidth() - width, (int) (10 * density), Gravity.START);
        rows.getChildAt(0).requestFocus();
    }

    // ===== TIME & BACKGROUND =====

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
        // Find greeting in hero section
        RecyclerView.ViewHolder vh = mainContentRecyclerView.findViewHolderForAdapterPosition(0);
        if (vh instanceof MainContentAdapter.HeroHolder) {
            TextView greeting = vh.itemView.findViewById(R.id.greeting);
            if (greeting != null) {
                String text = getString(GREETINGS[dayPart()]);
                String name = getString(R.string.user_name).trim();
                greeting.setText(name.isEmpty() ? text : text + ", " + name);
            }
        }
    }

    private void updateBackground() {
        int res = BACKGROUNDS[dayPart()];
        if (res == bgRes) return;
        boolean first = bgRes == 0;
        bgRes = res;
        if (first) {
            heroBackground.setImageResource(res);
            heroBackground.setAlpha(1f);
            return;
        }
        heroBackground.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            heroBackground.setImageResource(res);
            heroBackground.animate().alpha(1f).setDuration(900).start();
        });
    }

    private void updateWifiLabel() {
        // Use direct reference (set up in setupTopBarButtons)
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
        btnWifi.setCompoundDrawablePadding(iconOnly ? 0 : (int) (9 * density));
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
}