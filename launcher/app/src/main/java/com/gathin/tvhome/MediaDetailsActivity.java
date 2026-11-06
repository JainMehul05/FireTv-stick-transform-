package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.tvhome.data.model.MediaItem;
import com.gathin.tvhome.data.model.ProviderInfo;
import com.gathin.tvhome.data.repository.ContentRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Premium cinematic media details screen.
 * Displays backdrop, poster, synopsis, rating, streaming availability,
 * and launches YouTube trailers or streaming provider applications.
 */
public class MediaDetailsActivity extends Activity {

    public static final String EXTRA_MEDIA_ITEM = "extra_media_item";

    private MediaItem item;
    private ContentRepository repository;
    private Button btnWatchlist;

    // Supported provider package mappings for Fire OS
    private static final Map<String, String> PROVIDER_PACKAGES = new HashMap<>();
    static {
        PROVIDER_PACKAGES.put("Netflix", "com.netflix.ninja");
        PROVIDER_PACKAGES.put("Amazon Prime Video", "com.amazon.amazonvideo.livingroom");
        PROVIDER_PACKAGES.put("Prime Video", "com.amazon.amazonvideo.livingroom");
        PROVIDER_PACKAGES.put("Disney+ Hotstar", "in.startv.hotstar");
        PROVIDER_PACKAGES.put("Hotstar", "in.startv.hotstar");
        PROVIDER_PACKAGES.put("JioCinema", "com.jio.media.ondemand");
        PROVIDER_PACKAGES.put("YouTube", "com.google.android.youtube.tv");
        PROVIDER_PACKAGES.put("SonyLIV", "com.sonyliv");
        PROVIDER_PACKAGES.put("Apple TV+", "com.apple.atve.amazon.appletv");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_details);

        repository = ContentRepository.getInstance(this);
        item = (MediaItem) getIntent().getSerializableExtra(EXTRA_MEDIA_ITEM);
        if (item == null) {
            finish();
            return;
        }

        setupViews();
    }

    private void setupViews() {
        ImageView backdrop = findViewById(R.id.detailsBackdrop);
        ImageView poster = findViewById(R.id.detailsPoster);
        TextView title = findViewById(R.id.detailsTitle);
        TextView rating = findViewById(R.id.detailsRating);
        TextView year = findViewById(R.id.detailsYear);
        TextView genres = findViewById(R.id.detailsGenres);
        TextView duration = findViewById(R.id.detailsDuration);
        TextView overview = findViewById(R.id.detailsOverview);
        TextView providerName = findViewById(R.id.detailsProviderName);

        Button btnWatchNow = findViewById(R.id.btnWatchNow);
        Button btnWatchTrailer = findViewById(R.id.btnWatchTrailer);
        btnWatchlist = findViewById(R.id.btnWatchlist);
        Button btnBack = findViewById(R.id.btnBack);

        title.setText(item.title);
        overview.setText(item.overview);
        year.setText(!item.releaseYear.isEmpty() ? item.releaseYear : "2024");
        rating.setText(item.getFormattedRating());
        genres.setText(!item.genres.isEmpty() ? item.genres : "Entertainment");
        duration.setText(!item.duration.isEmpty() ? item.duration : "Feature Film");

        // Load images
        if (item.backdropUrl != null && !item.backdropUrl.isEmpty()) {
            ImageLoader.getInstance().loadUrl(item.backdropUrl, backdrop, 960, 540, R.drawable.bg_night);
        }
        if (item.posterUrl != null && !item.posterUrl.isEmpty()) {
            ImageLoader.getInstance().loadUrl(item.posterUrl, poster, 220, 330, R.drawable.bg_night);
        }

        // Providers
        if (item.providers != null && !item.providers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < item.providers.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(item.providers.get(i).name);
            }
            providerName.setText(sb.toString());
        } else {
            providerName.setText("Check streaming availability in App Store");
        }

        // Action Buttons
        btnWatchNow.setOnClickListener(v -> launchProviderApp());
        btnWatchTrailer.setOnClickListener(v -> launchTrailer());
        updateWatchlistButtonText();
        btnWatchlist.setOnClickListener(v -> {
            repository.toggleWatchlist(item);
            updateWatchlistButtonText();
            boolean inWatchlist = repository.isInWatchlist(item.id);
            Toast.makeText(this, inWatchlist ? "Added to Watchlist" : "Removed from Watchlist", Toast.LENGTH_SHORT).show();
        });
        btnBack.setOnClickListener(v -> finish());

        btnWatchNow.requestFocus();
    }

    private void updateWatchlistButtonText() {
        if (btnWatchlist != null) {
            boolean inWatchlist = repository.isInWatchlist(item.id);
            btnWatchlist.setText(inWatchlist ? R.string.in_watchlist : R.string.add_to_watchlist);
        }
    }

    private void launchTrailer() {
        if (!item.hasTrailer()) {
            Toast.makeText(this, "Trailer not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Try launching YouTube app intent first
        Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + item.trailerYoutubeId));
        PackageManager pm = getPackageManager();
        if (appIntent.resolveActivity(pm) != null) {
            startActivity(appIntent);
            return;
        }

        // Fallback to web browser intent
        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + item.trailerYoutubeId));
        if (webIntent.resolveActivity(pm) != null) {
            startActivity(webIntent);
        } else {
            Toast.makeText(this, "No browser or YouTube player installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchProviderApp() {
        if (item.providers == null || item.providers.isEmpty()) {
            Toast.makeText(this, "Available on digital platforms", Toast.LENGTH_SHORT).show();
            return;
        }

        ProviderInfo primary = item.providers.get(0);
        String pkg = PROVIDER_PACKAGES.get(primary.name);
        if (pkg != null) {
            PackageManager pm = getPackageManager();
            Intent launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launchIntent == null) {
                launchIntent = pm.getLaunchIntentForPackage(pkg);
            }
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                return;
            }
        }

        // Deep link to Amazon Appstore to install provider
        if (pkg != null) {
            try {
                Intent storeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?p=" + pkg));
                storeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(storeIntent);
                return;
            } catch (Exception ignored) {
            }
        }

        Toast.makeText(this, "Available on " + primary.name, Toast.LENGTH_SHORT).show();
    }
}
