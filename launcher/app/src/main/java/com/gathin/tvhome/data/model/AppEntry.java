package com.gathin.tvhome.data.model;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Universal application entry representing an installed TV or Android application.
 */
public class AppEntry {
    @NonNull public final String packageName;
    @NonNull public final String label;
    @Nullable public final Drawable icon;
    @Nullable public final Intent launchIntent;
    @NonNull public final String category;
    public boolean isPinned;
    public final boolean isLeanback;
    public final boolean isSystem;
    @NonNull public final String version;

    public AppEntry(@NonNull String packageName,
                    @NonNull String label,
                    @Nullable Drawable icon,
                    @Nullable Intent launchIntent,
                    @NonNull String category,
                    boolean isPinned,
                    boolean isLeanback,
                    boolean isSystem,
                    @NonNull String version) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
        this.launchIntent = launchIntent;
        this.category = category;
        this.isPinned = isPinned;
        this.isLeanback = isLeanback;
        this.isSystem = isSystem;
        this.version = version;
    }

    @Nullable
    public ComponentName getComponentName() {
        return launchIntent != null ? launchIntent.getComponent() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppEntry appEntry = (AppEntry) o;
        return packageName.equals(appEntry.packageName);
    }

    @Override
    public int hashCode() {
        return packageName.hashCode();
    }
}
