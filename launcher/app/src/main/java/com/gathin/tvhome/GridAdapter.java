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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for the app library grid (vertical RecyclerView with GridLayoutManager).
 * Shows all installed leanback apps alphabetically.
 */
public class GridAdapter extends RecyclerView.Adapter<GridAdapter.Holder> {

    interface GridListener {
        void onGridItemFocused(int position, View card);
        boolean onGridItemKey(int position, int keyCode);
        void onGridItemMenu(int position, View card);
        void onGridItemClick(int position, View card);
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable icon;
        int ambient;

        Entry(String label, ComponentName component, Drawable icon) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.icon = icon;
        }
    }

    static final float FOCUS_SCALE = 1.08f;
    static final float CORNER_RATIO = 0.18f;

    private final Context ctx;
    private final GridListener listener;
    private final Set<String> hidden;
    private final Set<String> dockedPackages;
    private final int columns;
    private final List<Entry> items = new ArrayList<>();

    GridAdapter(Context ctx, GridListener listener, Set<String> hidden, Set<String> dockedPackages, int columns) {
        this.columns = columns;
        this.ctx = ctx;
        this.listener = listener;
        this.hidden = hidden;
        this.dockedPackages = dockedPackages;
    }

    void reload() {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        List<Entry> fresh = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg) || dockedPackages.contains(pkg)) continue;
            Intent main = pm.getLeanbackLaunchIntentForPackage(pkg);
            ComponentName mainComponent = main != null ? main.getComponent() : null;
            if (mainComponent != null && !mainComponent.getClassName().equals(ri.activityInfo.name)) continue;
            if (!seen.add(pkg)) continue;
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name), icon));
        }
        Collections.sort(fresh, (a, b) -> a.label.compareToIgnoreCase(b.label));
        items.clear();
        items.addAll(fresh);
        // Pre-compute ambient colors on background thread to avoid main thread bitmap operations
        computeAmbientColorsAsync();
        notifyDataSetChanged();
    }

    String pkgAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).pkg : "";
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    int positionOf(String pkg) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).pkg.equals(pkg)) return i;
        return -1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grid, parent, false);
        return new Holder(v, listener, this);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.label);
        h.icon.setImageDrawable(e.icon);
        h.card.setOnClickListener(v -> {
            if (listener != null) listener.onGridItemClick(position, v);
            launch(e);
        });
        h.card.setOnLongClickListener(v -> {
            if (listener != null) listener.onGridItemMenu(position, v);
            return true;
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

    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        // If not computed yet, compute synchronously (fallback)
        if (e.ambient == 0) {
            e.ambient = computeVividColor(e.icon);
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
                    e.ambient = computeVividColor(e.icon);
                }
            }
        }).start();
    }

    List<Entry> getAllEntries() {
        return new ArrayList<>(items);
    }

    boolean isDocked(int position) {
        if (position >= 0 && position < items.size()) {
            return dockedPackages.contains(items.get(position).pkg);
        }
        return false;
    }

    void setDocked(String pkg, boolean docked) {
        if (docked) {
            dockedPackages.add(pkg);
        } else {
            dockedPackages.remove(pkg);
        }
        reload();
    }

    int getColumns() {
        return columns;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView icon;
        final TextView name;

        Holder(View v, GridListener listener, GridAdapter owner) {
            super(v);
            card = v.findViewById(R.id.card);
            icon = v.findViewById(R.id.icon);
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
                    listener.onGridItemMenu(pos, view);
                    return true;
                }
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return false;
                }
                return listener.onGridItemKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                view.animate().scaleX(s).scaleY(s)
                        .setDuration(focused ? 260 : 180)
                        .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f))
                        .start();
                name.animate().alpha(focused ? 1f : 0f)
                        .translationY(focused ? density * 4f : -density * 6f)
                        .setDuration(220).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onGridItemFocused(pos, view);
                }
            });
        }
    }
}