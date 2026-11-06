package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhome.data.model.MediaItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for horizontal entertainment content rows (movies, TV shows, continue watching).
 * Each row is a horizontal RecyclerView with focusable cinematic 16:9 cards.
 */
public class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.Holder> {

    interface ContentListener {
        void onContentFocused(int position, View card);
        void onContentClick(int position, View card);
        boolean onContentKey(int position, int keyCode);
    }

    static final float FOCUS_SCALE = 1.08f;
    static final float CORNER_RATIO = 0.12f;

    private final Context ctx;
    private ContentListener listener;
    private final List<MediaItem> items = new ArrayList<>();

    ContentAdapter(Context ctx, ContentListener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    void setListener(ContentListener listener) {
        this.listener = listener;
    }

    void setMediaItems(List<MediaItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_content_card, parent, false);
        return new Holder(v, listener, this);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        MediaItem item = items.get(position);
        h.title.setText(item.title);

        String subtitleText = item.releaseYear;
        if (!item.genres.isEmpty()) {
            subtitleText = subtitleText.isEmpty() ? item.genres : subtitleText + " • " + item.genres;
        }
        if (item.voteAverage > 0) {
            subtitleText += " • " + item.getFormattedRating();
        }
        h.subtitle.setText(subtitleText);

        // Load real TMDB artwork asynchronously with LRU cache and downsampling
        if (item.backdropUrl != null && item.backdropUrl.startsWith("http")) {
            ImageLoader.getInstance().loadUrl(item.backdropUrl, h.artwork, 360, 220, R.drawable.bg_night);
        } else if (item.posterUrl != null && item.posterUrl.startsWith("http")) {
            ImageLoader.getInstance().loadUrl(item.posterUrl, h.artwork, 360, 220, R.drawable.bg_night);
        } else {
            h.artwork.setImageResource(R.drawable.bg_night);
        }

        if (h.progressBar != null) {
            if (item.progress >= 0) {
                h.progressBar.setVisibility(View.VISIBLE);
                h.progressBar.setProgress(item.progress);
            } else {
                h.progressBar.setVisibility(View.GONE);
            }
        }

        h.card.setOnClickListener(v -> {
            if (listener != null) listener.onContentClick(position, v);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public MediaItem getItemAt(int position) {
        if (position >= 0 && position < items.size()) {
            return items.get(position);
        }
        return null;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView artwork;
        final TextView title;
        final TextView subtitle;
        final ImageView focusBadge;
        final ProgressBar progressBar;

        Holder(View v, ContentListener listener, ContentAdapter owner) {
            super(v);
            card = v.findViewById(R.id.card);
            artwork = v.findViewById(R.id.artwork);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            focusBadge = v.findViewById(R.id.focusBadge);
            progressBar = v.findViewById(R.id.cardProgress);

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
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    return listener.onContentKey(pos, keyCode);
                }
                return false;
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                float lift = focused ? 6f * density : 0f;
                view.animate().scaleX(s).scaleY(s).translationY(-lift)
                        .setDuration(focused ? 200 : 150)
                        .setInterpolator(new OvershootInterpolator(focused ? 1.2f : 0f))
                        .start();
                if (focusBadge != null) {
                    focusBadge.setVisibility(focused ? View.VISIBLE : View.GONE);
                }
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onContentFocused(pos, view);
                }
            });
        }
    }
}
