package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhome.data.api.TmdbApiClient;
import com.gathin.tvhome.data.model.MediaItem;
import com.gathin.tvhome.data.repository.ContentRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Global search activity across TMDB movies, TV series, YouTube trailers,
 * and installed Leanback TV applications.
 */
public class SearchActivity extends Activity {

    private EditText queryInput;
    private TextView resultsHeader;
    private RecyclerView resultsGrid;
    private ContentAdapter searchAdapter;

    private ContentRepository repository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private final List<MediaItem> allSearchPool = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        repository = ContentRepository.getInstance(this);

        queryInput = findViewById(R.id.searchQueryInput);
        resultsHeader = findViewById(R.id.searchResultsHeader);
        resultsGrid = findViewById(R.id.searchResultsGrid);
        View btnBack = findViewById(R.id.btnSearchBack);

        btnBack.setOnClickListener(v -> finish());

        resultsGrid.setLayoutManager(new GridLayoutManager(this, 4));
        searchAdapter = new ContentAdapter(this, new ContentAdapter.ContentListener() {
            @Override
            public void onContentFocused(int position, View card) {}

            @Override
            public void onContentClick(int position, View card) {
                MediaItem item = searchAdapter.getItemAt(position);
                if (item != null) {
                    Intent intent = new Intent(SearchActivity.this, MediaDetailsActivity.class);
                    intent.putExtra(MediaDetailsActivity.EXTRA_MEDIA_ITEM, item);
                    startActivity(intent);
                }
            }

            @Override
            public boolean onContentKey(int position, int keyCode) {
                return false;
            }
        });
        resultsGrid.setAdapter(searchAdapter);

        // Populate local catalog first
        repository.loadContent(rows -> {
            allSearchPool.clear();
            for (ContentRepository.CategoryRow row : rows) {
                allSearchPool.addAll(row.items);
            }
            performSearch("");
        });

        queryInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (searchRunnable != null) handler.removeCallbacks(searchRunnable);
                searchRunnable = () -> performSearch(s != null ? s.toString().trim() : "");
                handler.postDelayed(searchRunnable, 350);
            }
        });

        queryInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (resultsGrid != null && resultsGrid.getChildCount() > 0) {
                    resultsGrid.getChildAt(0).requestFocus();
                    return true;
                }
            }
            return false;
        });

        btnBack.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (resultsGrid != null && resultsGrid.getChildCount() > 0) {
                    resultsGrid.getChildAt(0).requestFocus();
                    return true;
                }
            }
            return false;
        });

        queryInput.requestFocus();
    }

    private void performSearch(String query) {
        List<MediaItem> matches = new ArrayList<>();
        if (query.isEmpty()) {
            resultsHeader.setText("DISCOVER TRENDING & POPULAR");
            // Show first 12 items as discovery recommendations
            int count = Math.min(allSearchPool.size(), 12);
            for (int i = 0; i < count; i++) {
                matches.add(allSearchPool.get(i));
            }
            searchAdapter.setMediaItems(matches);
            return;
        }

        String qLower = query.toLowerCase(Locale.ROOT);
        for (MediaItem item : allSearchPool) {
            if (item.title.toLowerCase(Locale.ROOT).contains(qLower)
                    || item.overview.toLowerCase(Locale.ROOT).contains(qLower)
                    || item.genres.toLowerCase(Locale.ROOT).contains(qLower)) {
                matches.add(item);
            }
        }

        // Search installed apps and wrap as MediaItem
        PackageManager pm = getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(probe, 0);
        for (ResolveInfo ri : apps) {
            String label = String.valueOf(ri.loadLabel(pm));
            if (label.toLowerCase(Locale.ROOT).contains(qLower)) {
                matches.add(new MediaItem(
                        -1,
                        label,
                        "Installed Application • " + ri.activityInfo.packageName,
                        "",
                        "",
                        "App",
                        0.0,
                        0,
                        "app",
                        "Installed Application",
                        "",
                        "",
                        new ArrayList<>()
                ));
            }
        }

        // Also query live TMDB if API key is active
        if (repository.getTmdbClient().hasApiKey()) {
            repository.getTmdbClient().searchMulti(query, new TmdbApiClient.Callback<List<MediaItem>>() {
                @Override
                public void onSuccess(List<MediaItem> result) {
                    for (MediaItem liveItem : result) {
                        boolean duplicate = false;
                        for (MediaItem existing : matches) {
                            if (existing.id == liveItem.id && existing.id > 0) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) matches.add(liveItem);
                    }
                    searchAdapter.setMediaItems(matches);
                    resultsHeader.setText("SEARCH RESULTS (" + matches.size() + ")");
                }

                @Override
                public void onError(Exception e) {
                    searchAdapter.setMediaItems(matches);
                    resultsHeader.setText("SEARCH RESULTS (" + matches.size() + ")");
                }
            });
        } else {
            searchAdapter.setMediaItems(matches);
            resultsHeader.setText("SEARCH RESULTS (" + matches.size() + ")");
        }
    }
}
