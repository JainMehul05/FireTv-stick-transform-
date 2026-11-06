package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
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
 * Adapter for the floating dock (horizontal RecyclerView).
 * Handles pinned apps with premium visual treatment.
 */
public class DockAdapter extends RecyclerView.Adapter<DockAdapter.Holder> {

    interface DockListener {
        void onDockItemFocused(int position, View card);
        boolean onDockItemVerticalKey(int position, int keyCode);
        void onDockItemMenu(int position, View card);
        void onDockItemClick(int position, View card);
        boolean onDockItemHorizontalKey(int position, int keyCode);
    }

    // Optional square icons for specific packages
    public static final Map<String, String> DOCK_ICONS = new HashMap<>();
    static {
        DOCK_ICONS.put("com.amazon.firetv.youtube", "dock_youtube");
        DOCK_ICONS.put("com.netflix.ninja", "dock_netflix");
        DOCK_ICONS.put("com.amazon.firebat", "dock_prime");
        DOCK_ICONS.put("com.apple.airplay.amazon.airplay", "dock_airplay");
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable square;
        final Drawable icon;
        int ambient;

        Entry(String label, ComponentName component, Drawable square, Drawable icon) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.square = square;
            this.icon = icon;
        }
    }

    static final float FOCUS_SCALE = 1.25f;
    static final float NEIGHBOUR_SCALE = 1.10f;
    static final float CORNER_RATIO = 0.225f;

    private final Context ctx;
    private final DockListener listener;
    private final int maxDock;
    private final List<String> pinned = new ArrayList<>();
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();

    DockAdapter(Context ctx, DockListener listener, int maxDock) {
        this.ctx = ctx;
        this.listener = listener;
        this.maxDock = maxDock;
        String saved = ctx.getSharedPreferences("tvhome", Context.MODE_PRIVATE).getString("dock", null);
        pinned.addAll(saved != null
                ? (saved.isEmpty() ? Collections.emptyList() : Arrays.asList(saved.split(",")))
                : Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps)));
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
    }

    void reload(List<Entry> allApps) {
        // Filter allApps to only pinned ones, in pinned order
        items.clear();
        for (String pkg : pinned) {
            for (Entry e : allApps) {
                if (e.pkg.equals(pkg)) {
                    items.add(e);
                    break;
                }
            }
        }
        // Pre-compute ambient colors on background thread to avoid main thread bitmap operations
        computeAmbientColorsAsync();
        notifyDataSetChanged();
    }

    int getDockCount() {
        return items.size();
    }

    String pkgAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).pkg : "";
    }

    int positionOf(String pkg) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).pkg.equals(pkg)) return i;
        return -1;
    }

    boolean pin(String pkg, List<Entry> allApps) {
        if (pinned.contains(pkg)) return true;
        if (items.size() >= maxDock) return false;
        for (Entry e : allApps) {
            if (e.pkg.equals(pkg)) {
                pinned.add(pkg);
                save();
                reload(allApps);
                return true;
            }
        }
        return false;
    }

    void unpin(String pkg, List<Entry> allApps) {
        pinned.remove(pkg);
        save();
        reload(allApps);
    }

    void move(String pkg, int delta, List<Entry> allApps) {
        int from = pinned.indexOf(pkg), to = from + delta;
        if (from < 0 || to < 0 || to >= pinned.size()) return;
        Collections.swap(pinned, from, to);
        save();
        reload(allApps);
    }

    boolean isDocked(int position) {
        return position >= 0 && position < items.size();
    }

    List<Entry> getItems() {
        return items;
    }

    private void save() {
        ctx.getSharedPreferences("tvhome", Context.MODE_PRIVATE).edit()
                .putString("dock", String.join(",", pinned)).apply();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dock, parent, false);
        return new Holder(v, listener, this);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.label);
        h.name.setVisibility(View.INVISIBLE); // dock uses tooltip instead
        h.name.setAlpha(0f);
        h.card.setScaleX(1f);
        h.card.setScaleY(1f);
        h.card.setTranslationY(0f);
        h.shadow.setVisibility(View.VISIBLE);
        h.shadow.setScaleX(1f);
        h.shadow.setScaleY(1f);
        h.shadow.setTranslationY(0f);
        h.label.setVisibility(View.GONE);
        boolean art = e.square != null;
        h.card.setBackground(art ? null : ctx.getDrawable(R.drawable.bg_dock_item));
        h.banner.setImageDrawable(e.square);
        h.banner.setVisibility(art ? View.VISIBLE : View.GONE);
        h.icon.setImageDrawable(art ? null : e.icon);
        h.icon.setVisibility(art ? View.GONE : View.VISIBLE);
        h.card.setOnClickListener(v -> {
            if (listener != null) listener.onDockItemClick(position, v);
            launch(e);
        });
        h.card.setOnLongClickListener(v -> {
            if (listener != null) listener.onDockItemMenu(position, v);
            return true;
        });
    }

    private void launch(Entry e) {
        PackageManager pm = ctx.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(e.pkg);
        if (i == null) {
            i = pm.getLaunchIntentForPackage(e.pkg);
        }
        if (i == null && e.component != null) {
            i = new Intent(Intent.ACTION_MAIN).setComponent(e.component);
        }
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                ctx.startActivity(i);
                return;
            } catch (Exception ex) {
                // fallback
            }
        }
        Toast.makeText(ctx, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        // If not computed yet, compute synchronously (fallback)
        if (e.ambient == 0) {
            e.ambient = computeVividColor(e.square != null ? e.square : e.icon);
        }
        return e.ambient;
    }

    private static int computeVividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.RGB_565); // Use RGB_565 for memory efficiency
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    private void computeAmbientColorsAsync() {
        new Thread(() -> {
            for (Entry e : items) {
                if (e.ambient == 0) {
                    e.ambient = computeVividColor(e.square != null ? e.square : e.icon);
                }
            }
        }).start();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final View shadow;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        Holder(View v, DockListener listener, DockAdapter owner) {
            super(v);
            card = v.findViewById(R.id.card);
            shadow = v.findViewById(R.id.shadow);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    float radius = Math.min(view.getWidth(), view.getHeight()) * CORNER_RATIO;
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int pos = getBindingAdapterPosition();
                if (listener == null || pos == RecyclerView.NO_POSITION) return false;
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    listener.onDockItemMenu(pos, view);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    return listener.onDockItemVerticalKey(pos, keyCode);
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return listener.onDockItemHorizontalKey(pos, keyCode);
                }
                return false;
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                float lift = focused ? 12f * density : 0f;
                view.animate().scaleX(s).scaleY(s).translationY(-lift)
                        .setDuration(focused ? 220 : 180)
                        .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f))
                        .start();
                // Shadow follows
                shadow.setPivotX(shadow.getWidth() / 2f);
                shadow.setPivotY(card.getBottom() - shadow.getTop());
                shadow.animate().scaleX(s).scaleY(s).translationY(-lift)
                        .setDuration(focused ? 220 : 180)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                name.animate().alpha(focused ? 1f : 0f)
                        .translationY(focused ? density * 4f : -density * 6f)
                        .setDuration(220).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onDockItemFocused(pos, view);
                }
            });
        }
    }
}