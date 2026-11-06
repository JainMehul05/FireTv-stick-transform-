package com.gathin.tvhome;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Premium Cinematic Screensaver: Full-screen artwork with Ken Burns zoom, smooth crossfades,
 * elegant clock/date overlay, and subtle vignette.
 * 
 * Selected with: settings put secure screensaver_components com.gathin.tvhome/com.gathin.tvhome.PhotoDream
 * Amazon default: settings put secure screensaver_components com.amazon.ftv.screensaver/.app.services.ScreensaverService
 */
public class PhotoDream extends DreamService {

    private static final int[] PHOTOS = {
            R.drawable.saver_01, R.drawable.saver_02, R.drawable.saver_03, R.drawable.saver_04,
            R.drawable.saver_05, R.drawable.saver_06, R.drawable.saver_07, R.drawable.saver_08,
            R.drawable.saver_09, R.drawable.saver_10, R.drawable.saver_11, R.drawable.saver_12};
    private static final long SLIDE_MS = 25_000;
    private static final long FADE_MS = 3_000;
    private static final float ZOOM = 1.12f;
    private static final float ZOOM_START = 1.0f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> order = new ArrayList<>();
    private ImageView front, back;
    private View vignette;
    private TextClock clock;
    private TextClock date;
    private int next;

    private final Runnable advance = new Runnable() {
        @Override
        public void run() {
            showNext();
            handler.postDelayed(this, SLIDE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Vignette overlay for cinematic look
        vignette = new View(this);
        vignette.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        vignette.setBackgroundResource(R.drawable.bg_vignette);
        root.addView(vignette);

        // Photo layers
        front = photoView();
        back = photoView();
        root.addView(back);
        root.addView(front);

        // Clock and date
        clock = clock();
        date = dateView();
        root.addView(clock);
        root.addView(date);

        setContentView(root);

        for (int p : PHOTOS) order.add(p);
        Collections.shuffle(order);
        front.setAlpha(0f);
        back.setAlpha(0f);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        handler.post(advance);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(advance);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(advance);
        super.onDetachedFromWindow();
    }

    /** Fade the next photo in over the current one, and start its slow zoom. */
    private void showNext() {
        ImageView incoming = back, outgoing = front;
        incoming.setImageResource(order.get(next));
        next = (next + 1) % order.size();
        incoming.animate().cancel();
        incoming.setScaleX(ZOOM_START);
        incoming.setScaleY(ZOOM_START);
        incoming.setAlpha(0f);
        incoming.bringToFront();
        incoming.animate().alpha(1f).setDuration(FADE_MS).start();
        incoming.animate().scaleX(ZOOM).scaleY(ZOOM).setDuration(SLIDE_MS + FADE_MS).start();
        outgoing.animate().alpha(0f).setStartDelay(FADE_MS).setDuration(1).start();
        front = incoming;
        back = outgoing;
        clock.bringToFront();
        date.bringToFront();
        vignette.bringToFront();
    }

    private ImageView photoView() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return v;
    }

    private TextClock clock() {
        TextClock c = new TextClock(this);
        c.setFormat12Hour("h:mm");
        c.setFormat24Hour("HH:mm");
        c.setTextColor(0xFFFFFFFF);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        c.setTypeface(getResources().getFont(R.font.outfit));
        c.setFontVariationSettings("'wght' 300");
        c.setLetterSpacing(-0.04f);
        c.setShadowLayer(16f, 0f, 4f, 0xCC000000);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        float dp = getResources().getDisplayMetrics().density;
        lp.setMargins(0, 0, (int) (60 * dp), (int) (100 * dp));
        c.setLayoutParams(lp);
        return c;
    }

    private TextClock dateView() {
        TextClock c = new TextClock(this);
        c.setFormat12Hour("EEEE, MMMM d");
        c.setFormat24Hour("EEEE, MMMM d");
        c.setTextColor(0xCCFFFFFF);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        c.setTypeface(getResources().getFont(R.font.open_sans));
        c.setFontVariationSettings("'wght' 400");
        c.setLetterSpacing(0.04f);
        c.setShadowLayer(8f, 0f, 2f, 0x99000000);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        float dp = getResources().getDisplayMetrics().density;
        lp.setMargins(0, 0, (int) (60 * dp), (int) (50 * dp));
        c.setLayoutParams(lp);
        return c;
    }
}