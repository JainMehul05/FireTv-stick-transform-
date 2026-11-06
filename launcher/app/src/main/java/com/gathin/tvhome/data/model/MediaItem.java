package com.gathin.tvhome.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Entertainment media metadata representation for movies, TV series, and trailers.
 */
public class MediaItem implements Serializable {
    public final int id;
    public final String title;
    public final String overview;
    public final String backdropUrl;
    public final String posterUrl;
    public final String releaseYear;
    public final double voteAverage;
    public final int voteCount;
    public final String mediaType; // "movie" or "tv"
    public final String genres;
    public final String duration;
    public final String trailerYoutubeId;
    public final List<ProviderInfo> providers;
    public int progress; // 0-100 or -1 if not in continue watching
    public String resumeText;

    public MediaItem(int id, String title, String overview, String backdropUrl,
                     String posterUrl, String releaseYear, double voteAverage,
                     int voteCount, String mediaType, String genres,
                     String duration, String trailerYoutubeId, List<ProviderInfo> providers) {
        this.id = id;
        this.title = title != null ? title : "";
        this.overview = overview != null ? overview : "";
        this.backdropUrl = backdropUrl != null ? backdropUrl : "";
        this.posterUrl = posterUrl != null ? posterUrl : "";
        this.releaseYear = releaseYear != null ? releaseYear : "";
        this.voteAverage = voteAverage;
        this.voteCount = voteCount;
        this.mediaType = mediaType != null ? mediaType : "movie";
        this.genres = genres != null ? genres : "";
        this.duration = duration != null ? duration : "";
        this.trailerYoutubeId = trailerYoutubeId != null ? trailerYoutubeId : "";
        this.providers = providers != null ? providers : new ArrayList<>();
        this.progress = -1;
        this.resumeText = "";
    }

    public String getFormattedRating() {
        if (voteAverage <= 0.0) return "";
        return String.format(java.util.Locale.US, "★ %.1f", voteAverage);
    }

    public String getPrimaryProviderName() {
        if (providers != null && !providers.isEmpty()) {
            return providers.get(0).name;
        }
        return "";
    }

    public boolean hasTrailer() {
        return trailerYoutubeId != null && !trailerYoutubeId.trim().isEmpty();
    }
}
