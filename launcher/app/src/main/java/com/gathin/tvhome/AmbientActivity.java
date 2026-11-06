package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Premium Ambient & Standby screen for Fire TV.
 * Features Apple TV-inspired large typography, gentle Ken Burns photographic slideshow,
 * and zero-lag remote wake-up.
 */
public class AmbientActivity extends Activity {

    private static final int[] WALLPAPERS = {
            R.drawable.saver_01, R.drawable.saver_02, R.drawable.saver_03, R.drawable.saver_04,
            R.drawable.saver_05, R.drawable.saver_06, R.drawable.saver_07, R.drawable.saver_08,
            R.drawable.saver_09, R.drawable.saver_10, R.drawable.saver_11, R.drawable.saver_12
    };

    private static final long SLIDE_DURATION_MS = 20_000;
    private static final long CROSSFADE_DURATION_MS = 2_500;

    private ImageView ambientFront;
    private ImageView ambientBack;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> wallpaperOrder = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isDestroyed = false;

    private final Runnable advanceSlide = new Runnable() {
        @Override
        public void run() {
            if (isDestroyed) return;
            transitionToNextWallpaper();
            handler.postDelayed(this, SLIDE_DURATION_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ambient);

        ambientFront = findViewById(R.id.ambientFront);
        ambientBack = findViewById(R.id.ambientBack);

        for (int res : WALLPAPERS) {
            wallpaperOrder.add(res);
        }
        Collections.shuffle(wallpaperOrder);

        // Initial wallpaper
        ambientFront.setImageResource(wallpaperOrder.get(0));
        startKenBurnsAnimation(ambientFront);

        handler.postDelayed(advanceSlide, SLIDE_DURATION_MS);
    }

    private void transitionToNextWallpaper() {
        currentIndex = (currentIndex + 1) % wallpaperOrder.size();
        int nextRes = wallpaperOrder.get(currentIndex);

        // Prepare back image view
        ambientBack.setImageResource(nextRes);
        ambientBack.setAlpha(1.0f);
        startKenBurnsAnimation(ambientBack);

        // Crossfade front out
        ambientFront.animate()
                .alpha(0f)
                .setDuration(CROSSFADE_DURATION_MS)
                .withEndAction(() -> {
                    // Swap front and back
                    ambientFront.setImageResource(nextRes);
                    ambientFront.setAlpha(1.0f);
                    ambientFront.setScaleX(ambientBack.getScaleX());
                    ambientFront.setScaleY(ambientBack.getScaleY());
                    ambientBack.setAlpha(0f);
                })
                .start();
    }

    private void startKenBurnsAnimation(ImageView view) {
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.animate()
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(SLIDE_DURATION_MS + CROSSFADE_DURATION_MS)
                .setInterpolator(new LinearInterpolator())
                .start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Any remote key press immediately exits ambient mode and returns to home screen
        finishWithFade();
        return true;
    }

    private void finishWithFade() {
        if (isDestroyed) return;
        isDestroyed = true;
        handler.removeCallbacks(advanceSlide);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        handler.removeCallbacks(advanceSlide);
        if (ambientFront != null) ambientFront.animate().cancel();
        if (ambientBack != null) ambientBack.animate().cancel();
    }
}
