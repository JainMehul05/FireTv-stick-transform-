package com.gathin.tvhome.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * Verified viewing history record for smart resume and continue-watching tracking.
 * Strictly separates confirmed watch activity from metadata-only catalogue data.
 */
public class ViewingHistoryItem implements Serializable {
    public static final String SOURCE_VERIFIED_PROVIDER = "VERIFIED_PROVIDER_DATA";
    public static final String SOURCE_LOCAL_PLAYBACK = "LOCAL_PLAYBACK_DATA";
    public static final String SOURCE_USER_MANAGED = "USER_MANAGED_DATA";
    public static final String SOURCE_METADATA_ONLY = "METADATA_ONLY";

    public static final String RESUME_EXACT_POSITION = "EXACT_POSITION";
    public static final String RESUME_CONTENT_PAGE = "CONTENT_PAGE";
    public static final String RESUME_APP_LAUNCH = "APP_LAUNCH";

    @NonNull public final String contentId;
    @NonNull public final String contentTitle;
    @NonNull public final String contentType;       // "movie", "tv", "video", "local"
    @NonNull public final String artworkUrl;
    @NonNull public final String provider;          // "Netflix", "Prime Video", "Disney+ Hotstar", "YouTube", "Local"
    @NonNull public final String applicationPackage;
    public final long playbackPosition;             // in milliseconds
    public final long duration;                     // in milliseconds
    public final int progressPercentage;            // 0 - 100
    public final long lastWatchedTimestamp;         // System.currentTimeMillis()
    @NonNull public final String playbackState;     // "PLAYING", "PAUSED", "COMPLETED"
    @NonNull public final String resumeCapability;  // EXACT_POSITION, CONTENT_PAGE, APP_LAUNCH
    @NonNull public final String dataSource;        // VERIFIED_PROVIDER_DATA, LOCAL_PLAYBACK_DATA, USER_MANAGED_DATA
    public final float confidence;                  // 0.0 to 1.0
    public final long lastUpdated;

    public ViewingHistoryItem(@NonNull String contentId,
                              @NonNull String contentTitle,
                              @NonNull String contentType,
                              @NonNull String artworkUrl,
                              @NonNull String provider,
                              @NonNull String applicationPackage,
                              long playbackPosition,
                              long duration,
                              int progressPercentage,
                              long lastWatchedTimestamp,
                              @NonNull String playbackState,
                              @NonNull String resumeCapability,
                              @NonNull String dataSource,
                              float confidence,
                              long lastUpdated) {
        this.contentId = contentId;
        this.contentTitle = contentTitle;
        this.contentType = contentType;
        this.artworkUrl = artworkUrl;
        this.provider = provider;
        this.applicationPackage = applicationPackage;
        this.playbackPosition = playbackPosition;
        this.duration = duration;
        this.progressPercentage = Math.max(0, Math.min(100, progressPercentage));
        this.lastWatchedTimestamp = lastWatchedTimestamp;
        this.playbackState = playbackState;
        this.resumeCapability = resumeCapability;
        this.dataSource = dataSource;
        this.confidence = confidence;
        this.lastUpdated = lastUpdated;
    }

    public boolean isVerifiedOrUserManaged() {
        return !SOURCE_METADATA_ONLY.equals(dataSource);
    }

    public JSONObject toJsonObject() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("contentId", contentId);
            obj.put("contentTitle", contentTitle);
            obj.put("contentType", contentType);
            obj.put("artworkUrl", artworkUrl);
            obj.put("provider", provider);
            obj.put("applicationPackage", applicationPackage);
            obj.put("playbackPosition", playbackPosition);
            obj.put("duration", duration);
            obj.put("progressPercentage", progressPercentage);
            obj.put("lastWatchedTimestamp", lastWatchedTimestamp);
            obj.put("playbackState", playbackState);
            obj.put("resumeCapability", resumeCapability);
            obj.put("dataSource", dataSource);
            obj.put("confidence", (double) confidence);
            obj.put("lastUpdated", lastUpdated);
        } catch (JSONException ignored) {}
        return obj;
    }

    @Nullable
    public static ViewingHistoryItem fromJsonObject(JSONObject obj) {
        if (obj == null) return null;
        String id = obj.optString("contentId", "");
        String title = obj.optString("contentTitle", "");
        if (id.isEmpty() || title.isEmpty()) return null;

        return new ViewingHistoryItem(
                id,
                title,
                obj.optString("contentType", "movie"),
                obj.optString("artworkUrl", ""),
                obj.optString("provider", ""),
                obj.optString("applicationPackage", ""),
                obj.optLong("playbackPosition", 0),
                obj.optLong("duration", 0),
                obj.optInt("progressPercentage", 0),
                obj.optLong("lastWatchedTimestamp", System.currentTimeMillis()),
                obj.optString("playbackState", "PAUSED"),
                obj.optString("resumeCapability", RESUME_APP_LAUNCH),
                obj.optString("dataSource", SOURCE_USER_MANAGED),
                (float) obj.optDouble("confidence", 1.0),
                obj.optLong("lastUpdated", System.currentTimeMillis())
        );
    }

    /**
     * Helper to wrap as a generic MediaItem for Leanback / RecyclerView presentation.
     */
    public MediaItem toMediaItem() {
        return new MediaItem(
                contentId.hashCode(),
                contentTitle,
                "Resume playback on " + provider,
                artworkUrl,
                artworkUrl,
                contentType,
                0.0,
                0,
                contentType,
                provider,
                "",
                "",
                new java.util.ArrayList<>()
        );
    }
}
