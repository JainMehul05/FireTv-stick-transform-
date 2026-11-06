package com.gathin.tvhome;

import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the main vertical content RecyclerView.
 * Sections:
 *   0: Hero foreground (Title, Metadata, Play/Info buttons)
 *   1: Floating Apple TV dock platter
 *   2..N: Horizontal content discovery rows
 *   Last: All Apps discovery banner
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
        void onHeroUpKey();
        void onHeroDownKey();
        void onHeroPlayClick();
        void onHeroInfoClick();
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

    private HeroHolder activeHeroHolder;
    private DockHolder activeDockHolder;

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
        notifyDataSetChanged();
    }

    void addContentRow(ContentRow row) {
        contentRows.add(row);
        notifyItemInserted(2 + contentRows.size() - 1);
    }

    void setAppsRowCallback(AppsRowCallback callback) {
        this.appsRowCallback = callback;
    }

    public HeroHolder getHeroHolder() {
        return activeHeroHolder;
    }

    public DockHolder getDockHolder() {
        return activeDockHolder;
    }

    public RecyclerView getDockRecyclerView() {
        return activeDockHolder != null ? activeDockHolder.dockRecyclerView : null;
    }

    public int getContentRowCount() {
        return contentRows.size();
    }

    public ContentRow getContentRow(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < contentRows.size()) {
            return contentRows.get(rowIndex);
        }
        return null;
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
        if (type == TYPE_HERO) {
            activeHeroHolder = (HeroHolder) holder;
            activeHeroHolder.bind(listener);
        } else if (type == TYPE_DOCK) {
            activeDockHolder = (DockHolder) holder;
            activeDockHolder.bind(dockAdapter, ctx);
        } else if (type == TYPE_CONTENT_ROW) {
            ContentRowHolder h = (ContentRowHolder) holder;
            ContentRow row = contentRows.get(position - 2);
            h.bind(row, position - 2, contentRows.size());
        } else if (type == TYPE_APPS_ROW) {
            AppsRowHolder h = (AppsRowHolder) holder;
            h.bind();
        }
    }

    @Override
    public int getItemCount() {
        return 2 + contentRows.size() + 1; // hero + dock + content rows + apps row
    }

    // ===== VIEW HOLDERS =====

    public static class HeroHolder extends RecyclerView.ViewHolder {
        public final TextView heroCategory;
        public final TextView heroTitle;
        public final TextView heroYear;
        public final TextView heroGenre;
        public final TextView heroDuration;
        public final TextView heroPlayButton;
        public final TextView heroInfoButton;
        public final View heroActions;

        HeroHolder(View v) {
            super(v);
            heroCategory = v.findViewById(R.id.heroCategory);
            heroTitle = v.findViewById(R.id.heroTitle);
            heroYear = v.findViewById(R.id.heroYear);
            heroGenre = v.findViewById(R.id.heroGenre);
            heroDuration = v.findViewById(R.id.heroDuration);
            heroPlayButton = v.findViewById(R.id.heroPlayButton);
            heroInfoButton = v.findViewById(R.id.heroInfoButton);
            heroActions = v.findViewById(R.id.heroActions);
        }

        void bind(MainContentListener listener) {
            if (heroPlayButton != null) {
                heroPlayButton.setOnClickListener(v -> {
                    if (listener != null) listener.onHeroPlayClick();
                });
                heroPlayButton.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (listener != null) listener.onHeroUpKey();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (listener != null) listener.onHeroDownKey();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (heroInfoButton != null) heroInfoButton.requestFocus();
                        return true;
                    }
                    return false;
                });
            }

            if (heroInfoButton != null) {
                heroInfoButton.setOnClickListener(v -> {
                    if (listener != null) listener.onHeroInfoClick();
                });
                heroInfoButton.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (listener != null) listener.onHeroUpKey();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (listener != null) listener.onHeroDownKey();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (heroPlayButton != null) heroPlayButton.requestFocus();
                        return true;
                    }
                    return false;
                });
            }
        }
    }

    public static class DockHolder extends RecyclerView.ViewHolder {
        public final RecyclerView dockRecyclerView;
        public final TextView heroName;
        public final View dockDot;

        DockHolder(View v) {
            super(v);
            dockRecyclerView = v.findViewById(R.id.dockRecyclerView);
            heroName = v.findViewById(R.id.heroName);
            dockDot = v.findViewById(R.id.dockDot);
        }

        void bind(DockAdapter adapter, Context ctx) {
            if (dockRecyclerView.getLayoutManager() == null) {
                LinearLayoutManager lm = new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false);
                dockRecyclerView.setLayoutManager(lm);
                dockRecyclerView.setHasFixedSize(true);
                dockRecyclerView.setItemAnimator(null);
                dockRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            }
            if (dockRecyclerView.getAdapter() != adapter) {
                dockRecyclerView.setAdapter(adapter);
            }
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
        }

        void bind(ContentRow row, int rowIndex, int totalRows) {
            categoryTitle.setText(row.title);
            categoryIcon.setImageResource(row.iconRes);

            if (contentRecyclerView.getLayoutManager() == null) {
                LinearLayoutManager lm = new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false);
                contentRecyclerView.setLayoutManager(lm);
                contentRecyclerView.setHasFixedSize(true);
                contentRecyclerView.setItemAnimator(null);
                contentRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            }

            contentRecyclerView.setAdapter(row.adapter);

            row.adapter.setListener(new ContentAdapter.ContentListener() {
                @Override
                public void onContentFocused(int position, View card) {
                    if (listener != null) {
                        listener.onContentItemFocused(rowIndex, position, card);
                    }
                }

                @Override
                public void onContentClick(int position, View card) {
                    if (listener != null) {
                        listener.onContentItemClick(rowIndex, position, card);
                    }
                }

                @Override
                public boolean onContentKey(int position, int keyCode) {
                    if (listener != null) {
                        return listener.onContentItemKey(rowIndex, position, keyCode);
                    }
                    return false;
                }
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

            appsRow.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (listener != null && !contentRows.isEmpty()) {
                        int lastRow = contentRows.size() - 1;
                        listener.onContentItemKey(lastRow, 0, keyCode);
                        return true;
                    }
                }
                return false;
            });
        }
    }
}
