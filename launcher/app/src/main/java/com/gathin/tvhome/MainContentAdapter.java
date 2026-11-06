package com.gathin.tvhome;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the main vertical content RecyclerView.
 * Contains: Hero section (header), Dock (horizontal), Content Rows (horizontal).
 */
public class MainContentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface MainContentListener {
        void onDockItemFocused(int position, View card);
        boolean onDockItemKey(int position, int keyCode);
        void onDockItemMenu(int position, View card);
        void onDockItemClick(int position, View card);
        void onContentItemFocused(int rowIndex, int position, View card);
        void onContentItemClick(int rowIndex, int position, View card);
        boolean onContentItemKey(int rowIndex, int position, int keyCode);
        void onAppsRowClick();
    }

    private static final int TYPE_HERO = 0;
    private static final int TYPE_DOCK = 1;
    private static final int TYPE_CONTENT_ROW = 2;
    private static final int TYPE_APPS_ROW = 3;

    private final Context ctx;
    private final MainContentListener listener;
    private final DockAdapter dockAdapter;
    private final List<ContentRow> contentRows = new ArrayList<>();
    private AppsRowCallback appsRowCallback;

    static class ContentRow {
        final String title;
        final int iconRes;
        final ContentAdapter adapter;
        final int rowIndex;

        ContentRow(String title, int iconRes, ContentAdapter adapter, int rowIndex) {
            this.title = title;
            this.iconRes = iconRes;
            this.adapter = adapter;
            this.rowIndex = rowIndex;
        }
    }

    interface AppsRowCallback {
        void onAppsRowClick();
    }

    MainContentAdapter(Context ctx, MainContentListener listener, DockAdapter dockAdapter) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockAdapter = dockAdapter;
    }

    void setContentRows(List<ContentRow> rows) {
        contentRows.clear();
        contentRows.addAll(rows);
        notifyItemRangeChanged(2, contentRows.size() + 1); // +1 for apps row
    }

    void addContentRow(ContentRow row) {
        contentRows.add(row);
        notifyItemInserted(2 + contentRows.size());
    }

    void setAppsRowCallback(AppsRowCallback callback) {
        this.appsRowCallback = callback;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return TYPE_HERO;
        if (position == 1) return TYPE_DOCK;
        if (position == getItemCount() - 1) return TYPE_APPS_ROW;
        return TYPE_CONTENT_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(ctx);
        switch (viewType) {
            case TYPE_HERO:
                return new HeroHolder(inflater.inflate(R.layout.item_hero_section, parent, false));
            case TYPE_DOCK:
                return new DockHolder(inflater.inflate(R.layout.item_dock_section, parent, false));
            case TYPE_CONTENT_ROW:
                return new ContentRowHolder(inflater.inflate(R.layout.item_content_row, parent, false));
            case TYPE_APPS_ROW:
                return new AppsRowHolder(inflater.inflate(R.layout.item_apps_row, parent, false));
            default:
                throw new IllegalArgumentException("Unknown view type: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int type = getItemViewType(position);
        if (type == TYPE_CONTENT_ROW) {
            ContentRowHolder h = (ContentRowHolder) holder;
            ContentRow row = contentRows.get(position - 2);
            h.bind(row);
        } else if (type == TYPE_DOCK) {
            DockHolder h = (DockHolder) holder;
            h.bind(dockAdapter);
        } else if (type == TYPE_APPS_ROW) {
            AppsRowHolder h = (AppsRowHolder) holder;
            h.bind();
        }
        // Hero doesn't need binding - it's static
    }

    @Override
    public int getItemCount() {
        return 2 + contentRows.size() + 1; // hero + dock + content rows + apps row
    }

    // ===== VIEW HOLDERS =====

    static class HeroHolder extends RecyclerView.ViewHolder {
        HeroHolder(View v) {
            super(v);
        }
    }

    class DockHolder extends RecyclerView.ViewHolder {
        final RecyclerView dockRecyclerView;

        DockHolder(View v) {
            super(v);
            dockRecyclerView = v.findViewById(R.id.dockRecyclerView);
        }

        void bind(DockAdapter adapter) {
            LinearLayoutManager lm = new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false);
            dockRecyclerView.setLayoutManager(lm);
            dockRecyclerView.setHasFixedSize(true);
            dockRecyclerView.setItemAnimator(null);
            dockRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            dockRecyclerView.setAdapter(adapter);
        }
    }

    class ContentRowHolder extends RecyclerView.ViewHolder {
        final TextView categoryTitle;
        final ImageView categoryIcon;
        final RecyclerView contentRecyclerView;

        ContentRowHolder(View v) {
            super(v);
            categoryTitle = v.findViewById(R.id.categoryTitle);
            categoryIcon = v.findViewById(R.id.categoryIcon);
            contentRecyclerView = v.findViewById(R.id.contentRecyclerView);
            // Make the row focusable for vertical navigation between rows
            v.setFocusable(true);
            v.setFocusableInTouchMode(true);
        }

        void bind(ContentRow row) {
            categoryTitle.setText(row.title);
            categoryIcon.setImageResource(row.iconRes);

            LinearLayoutManager lm = new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false);
            contentRecyclerView.setLayoutManager(lm);
            contentRecyclerView.setHasFixedSize(true);
            contentRecyclerView.setItemAnimator(null);
            contentRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            contentRecyclerView.setAdapter(row.adapter);

            // Set listener for content items
            row.adapter.setListener(new ContentAdapter.ContentListener() {
                @Override
                public void onContentFocused(int position, View card) {
                    if (listener != null) {
                        listener.onContentItemFocused(row.rowIndex, position, card);
                    }
                }

                @Override
                public void onContentClick(int position, View card) {
                    if (listener != null) {
                        listener.onContentItemClick(row.rowIndex, position, card);
                    }
                }

                @Override
                public boolean onContentKey(int position, int keyCode) {
                    if (listener != null) {
                        return listener.onContentItemKey(row.rowIndex, position, keyCode);
                    }
                    return false;
                }
            });

            // Handle vertical navigation between content rows
            itemView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (listener == null) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    // UP from first content row goes to dock
                    if (row.rowIndex == 0) {
                        return listener.onDockItemKey(dockAdapter.getDockCount() - 1, keyCode);
                    }
                    // UP from other content rows goes to previous content row
                    return listener.onContentItemKey(row.rowIndex - 1, 
                            contentRows.get(row.rowIndex - 1).adapter.getItemCount() - 1, 
                            keyCode);
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    // DOWN from last content row goes to apps row
                    if (row.rowIndex == contentRows.size() - 1) {
                        // Focus apps row
                        // This will be handled by the main content RecyclerView
                        return false; // Let RecyclerView handle focus movement
                    }
                    // DOWN to next content row
                    return listener.onContentItemKey(row.rowIndex + 1, 0, keyCode);
                }
                return false;
            });
        }
    }

    class AppsRowHolder extends RecyclerView.ViewHolder {
        private final View appsRow;

        AppsRowHolder(View v) {
            super(v);
            appsRow = v.findViewById(R.id.appsRow);
        }

        void bind() {
            appsRow.setOnClickListener(v -> {
                if (appsRowCallback != null) appsRowCallback.onAppsRowClick();
                if (listener != null) listener.onAppsRowClick();
            });
            appsRow.setOnFocusChangeListener((v, focused) -> {
                if (focused) {
                    // Could add focus animation here
                }
            });
            appsRow.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (listener == null) return false;
                // Handle UP from Apps row - go to last content row
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    return listener.onContentItemKey(contentRows.size() - 1, 
                            contentRows.isEmpty() ? 0 : contentRows.get(contentRows.size() - 1).adapter.getItemCount() - 1, 
                            keyCode);
                }
                // Handle DOWN - stay on Apps row
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    return true;
                }
                return false;
            });
        }
    }
}