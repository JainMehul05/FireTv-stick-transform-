package com.gathin.tvhome;

import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhome.data.model.AppEntry;
import com.gathin.tvhome.data.repository.AppRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the horizontal "Your Apps" shelf on the home screen.
 * Displays all installed discoverable applications with squircle tiles.
 */
public class InstalledAppsAdapter extends RecyclerView.Adapter<InstalledAppsAdapter.Holder> {

    public interface AppClickListener {
        void onAppClick(AppEntry app);
        void onAppMenu(AppEntry app, View v);
        boolean onAppKey(int position, int keyCode);
    }

    private final Context context;
    private final List<AppEntry> items = new ArrayList<>();
    private AppClickListener listener;

    public InstalledAppsAdapter(Context context, AppClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setApps(List<AppEntry> apps) {
        items.clear();
        if (apps != null) {
            items.addAll(apps);
        }
        notifyDataSetChanged();
    }

    public AppEntry getItemAt(int position) {
        if (position >= 0 && position < items.size()) {
            return items.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_app_tile, parent, false);
        return new Holder(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppEntry app = items.get(position);
        h.label.setText(app.label);
        if (app.icon != null) {
            h.icon.setImageDrawable(app.icon);
        } else {
            h.icon.setImageResource(R.drawable.ic_system);
        }

        h.root.setOnClickListener(v -> {
            if (listener != null) listener.onAppClick(app);
            else AppRepository.launchApp(context, app);
        });

        h.root.setOnLongClickListener(v -> {
            if (listener != null) listener.onAppMenu(app, v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View root;
        final FrameLayout iconBox;
        final ImageView icon;
        final TextView label;

        Holder(@NonNull View v, AppClickListener listener) {
            super(v);
            root = v.findViewById(R.id.appTileRoot);
            iconBox = v.findViewById(R.id.appTileIconBox);
            icon = v.findViewById(R.id.appTileIcon);
            label = v.findViewById(R.id.appTileLabel);

            root.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? 1.08f : 1.0f;
                float lift = focused ? -4f : 0f;
                iconBox.animate().scaleX(s).scaleY(s).translationY(lift)
                        .setDuration(focused ? 180 : 120)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                label.setTextColor(focused ? 0xFFFFFFFF : 0x99FFFFFF);
            });

            root.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int pos = getBindingAdapterPosition();
                if (listener != null && pos != RecyclerView.NO_POSITION) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return listener.onAppKey(pos, keyCode);
                    }
                }
                return false;
            });
        }
    }
}
