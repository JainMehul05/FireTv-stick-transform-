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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for horizontal content rows (movies, TV shows, continue watching).
 * Each row is a horizontal RecyclerView with focusable content cards.
 */
public class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.Holder> {

    interface ContentListener {
        void onContentFocused(int position, View card);
        void onContentClick(int position, View card);
        boolean onContentKey(int position, int keyCode);
    }

    static final class ContentItem {
        final String title;
        final String subtitle;
        final Drawable artwork;
        final int position;

        ContentItem(String title, String subtitle, Drawable artwork, int position) {
            this.title = title;
            this.subtitle = subtitle;
            this.artwork = artwork;
            this.position = position;
        }
    }

    static final float FOCUS_SCALE = 1.12f;
    static final float CORNER_RATIO = 0.18f;

    private final Context ctx;
    private ContentListener listener;
    private final List<ContentItem> items = new ArrayList<>();

    ContentAdapter(Context ctx, ContentListener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    void setListener(ContentListener listener) {
        this.listener = listener;
    }

    void setItems(List<ContentItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    void appendItems(List<ContentItem> newItems) {
        int start = items.size();
        items.addAll(newItems);
        notifyItemRangeInserted(start, newItems.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_content_card, parent, false);
        return new Holder(v, listener, this);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ContentItem item = items.get(position);
        h.title.setText(item.title);
        h.subtitle.setText(item.subtitle);
        h.artwork.setImageDrawable(item.artwork);
        h.card.setOnClickListener(v -> {
            if (listener != null) listener.onContentClick(position, v);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView artwork;
        final TextView title;
        final TextView subtitle;
        final ImageView focusBadge;

        Holder(View v, ContentListener listener, ContentAdapter owner) {
            super(v);
            card = v.findViewById(R.id.card);
            artwork = v.findViewById(R.id.artwork);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            focusBadge = v.findViewById(R.id.focusBadge);

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
                if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                return listener.onContentKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                float lift = focused ? 8f * density : 0f;
                view.animate().scaleX(s).scaleY(s).translationY(-lift)
                        .setDuration(focused ? 280 : 200)
                        .setInterpolator(new OvershootInterpolator(focused ? 1.5f : 0f))
                        .start();
                focusBadge.setVisibility(focused ? View.VISIBLE : View.GONE);
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onContentFocused(pos, view);
                }
            });
        }
    }
}