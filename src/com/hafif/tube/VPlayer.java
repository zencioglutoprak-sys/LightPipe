package com.hafif.tube;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Video oynatıcı bileşeni: poster, dev oynat düğmesi, kontroller, çift dokunuşla ±10 sn, tam ekran düğmesi. */
final class VPlayer extends FrameLayout implements SyncPlayer.Cb, SurfaceHolder.Callback {
    private final Activity act;
    private final SurfaceView sv;
    private final ImageView poster;
    private final Spin spin;
    private final FrameLayout center;
    private final Ic centerIc;
    private final View ring;
    private final LinearLayout bar, top;
    private final TextView tCur, tTot, topTitle, flashL, flashR, stageTx;
    private final Seek seek;
    private final Ic fsIc;
    private final Handler h = new Handler(Looper.getMainLooper());

    private SyncPlayer sp;
    private Uri pv, pa;
    private boolean controls = true, loading, ended, errored;
    private int vw, vh, dur, startAt;
    private Runnable onFull, onFail, onBack;
    private ObjectAnimator ringAnim;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (sp != null && sp.ready()) {
                int p = sp.pos();
                if (!seek.dragging()) {
                    seek.setFrac(dur > 0 ? p / (float) dur : 0f);
                    tCur.setText(Ui.time(p));
                }
            }
            if (stageTx.getVisibility() == VISIBLE && !stageBase.isEmpty()) {
                long sec = (android.os.SystemClock.elapsedRealtime() - stageT0) / 1000;
                stageTx.setText(stageBase + "  " + sec + " sn");
            }
            h.postDelayed(this, 250);
        }
    };

    private final Runnable autoHide = new Runnable() {
        @Override public void run() { if (sp != null && sp.isPlaying() && !seek.dragging()) showControls(false); }
    };

    VPlayer(Activity a) {
        super(a);
        act = a;
        setBackgroundColor(0xff000000);

        sv = new SurfaceView(a);
        sv.getHolder().addCallback(this);
        addView(sv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        poster = new ImageView(a);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(poster, new FrameLayout.LayoutParams(-1, -1));

        // üst bar (yerel video için: geri + başlık)
        top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xCC000000, 0x00000000}));
        top.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 22));
        FrameLayout backBtn = new FrameLayout(a);
        backBtn.addView(new Ic(a, Ic.BACK, 22, 0xffffffff), new FrameLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22), Gravity.CENTER));
        Ui.press(backBtn, 0.88f);
        backBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { if (onBack != null) onBack.run(); }
        });
        top.addView(backBtn, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        topTitle = Ui.text(a, "", 15, 0xffffffff, true);
        topTitle.setSingleLine(true);
        topTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(topTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        top.setVisibility(GONE);
        addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // ortadaki büyük düğme + nabız halkası
        ring = new View(a);
        GradientDrawable rg = new GradientDrawable();
        rg.setShape(GradientDrawable.OVAL);
        rg.setColor(0);
        rg.setStroke(Ui.dp(a, 2), Ui.RED);
        ring.setBackground(rg);
        addView(ring, new FrameLayout.LayoutParams(Ui.dp(a, 68), Ui.dp(a, 68), Gravity.CENTER));

        center = new FrameLayout(a);
        center.setBackground(Ui.redCircle());
        center.setElevation(Ui.dp(a, 8));
        centerIc = new Ic(a, Ic.PLAY, 30, 0xffffffff);
        center.addView(centerIc, new FrameLayout.LayoutParams(Ui.dp(a, 30), Ui.dp(a, 30), Gravity.CENTER));
        addView(center, new FrameLayout.LayoutParams(Ui.dp(a, 68), Ui.dp(a, 68), Gravity.CENTER));
        Ui.press(center, 0.9f);
        center.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { toggle(); }
        });

        spin = new Spin(a, 48);
        spin.setVisibility(GONE);
        addView(spin, new FrameLayout.LayoutParams(Ui.dp(a, 48), Ui.dp(a, 48), Gravity.CENTER));

        stageTx = Ui.text(a, "", 12, 0xffffffff, true);
        stageTx.setBackground(Ui.box(a, 0x99000000, 14));
        stageTx.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
        stageTx.setVisibility(GONE);
        FrameLayout.LayoutParams stp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        stp.topMargin = Ui.dp(a, 70);
        addView(stageTx, stp);

        // çift dokunuş balonları
        flashL = Ui.text(a, "◀◀  10", 14, 0xffffffff, true);
        flashR = Ui.text(a, "10  ▶▶", 14, 0xffffffff, true);
        for (TextView t : new TextView[]{flashL, flashR}) {
            t.setBackground(Ui.box(a, 0x88000000, 30));
            t.setPadding(Ui.dp(a, 18), Ui.dp(a, 12), Ui.dp(a, 18), Ui.dp(a, 12));
            t.setAlpha(0f);
        }
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.LEFT);
        fl.leftMargin = Ui.dp(a, 36);
        addView(flashL, fl);
        FrameLayout.LayoutParams fr = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        fr.rightMargin = Ui.dp(a, 36);
        addView(flashR, fr);

        // alt kontrol çubuğu
        bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xDD000000, 0x00000000}));
        bar.setPadding(Ui.dp(a, 10), Ui.dp(a, 22), Ui.dp(a, 6), Ui.dp(a, 2));
        tCur = Ui.text(a, "0:00", 12, 0xffffffff, true);
        tTot = Ui.text(a, "0:00", 12, 0xffcccccc, true);
        seek = new Seek(a);
        bar.addView(tCur);
        bar.addView(seek, new LinearLayout.LayoutParams(0, Ui.dp(a, 34), 1f));
        bar.addView(tTot);
        FrameLayout fsBtn = new FrameLayout(a);
        fsIc = new Ic(a, Ic.FULL, 20, 0xffffffff);
        fsBtn.addView(fsIc, new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20), Gravity.CENTER));
        Ui.press(fsBtn, 0.85f);
        fsBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { if (onFull != null) onFull.run(); }
        });
        bar.addView(fsBtn, new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 42)));
        addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        seek.setListener(new Seek.Listener() {
            @Override public void onSeek(float f, boolean done) {
                tCur.setText(Ui.time((int) (f * dur)));
                if (live) { if (done) scheduleHide(); return; }
                if (done && sp != null) { sp.seekTo((int) (f * dur)); scheduleHide(); }
            }
        });

        final GestureDetector gd = new GestureDetector(a, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (sp == null) { toggle(); return true; }
                showControls(!controls);
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (live) return true; // canlı yayında ileri/geri sarma akışı bozar
                if (sp == null || !sp.ready()) return true;
                boolean right = e.getX() > getWidth() / 2f;
                int p = sp.pos() + (right ? 10000 : -10000);
                sp.seekTo(Math.max(0, Math.min(dur, p)));
                flash(right ? flashR : flashL);
                return true;
            }
        });
        setOnTouchListener(new OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) { return gd.onTouchEvent(e); }
        });
        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != or - ol || b - t != ob - ot) fit();
            }
        });
        h.post(tick);
        startRing();
    }

    // ------------------------------------------------------------------ dış API

    private boolean live;
    /** Canlı yayında sarma kapatılır (HLS canlıda seek akışı bozuyordu). */
    void setLive(boolean on) { live = on; }

    void setHandlers(Runnable fullscreen, Runnable fail) { onFull = fullscreen; onFail = fail; }

    void topBar(String title, Runnable back) {
        topTitle.setText(title);
        onBack = back;
        top.setVisibility(VISIBLE);
    }

    private String lastErr = "";
    String lastError() { return lastErr; }

    boolean started() { return sp != null && sp.ready(); }

    void setFullIcon(boolean full) { fsIc.setType(full ? Ic.CLOSE : Ic.FULL); }

    /** Kaynakları hazırla. autoplay: true ise hemen akışı başlatır. */
    void load(String posterUrl, Uri video, Uri audio, int startMs, boolean autoplay) {
        release0();
        pv = video;
        pa = audio;
        startAt = startMs;
        ended = false;
        errored = false;
        if (posterUrl != null) {
            poster.setVisibility(VISIBLE);
            poster.setAlpha(1f);
            Img.load(poster, posterUrl, act);
        }
        if (autoplay) start();
        else showIdle();
    }

    /** Bilgi/akış beklerken dönen halka ve küçük durum yazısı. */
    void busy(boolean on, String text) {
        if (on) { setLoading(true); stage(text); }
        else { stage(null); if (sp == null) showIdle(); }
    }

    private String stageBase = "";
    private long stageT0;

    void stage(String s) {
        if (s == null || s.isEmpty()) { stageBase = ""; stageTx.setVisibility(GONE); return; }
        if (!s.equals(stageBase)) { stageBase = s; stageT0 = android.os.SystemClock.elapsedRealtime(); }
        stageTx.setText(s);
        stageTx.setVisibility(VISIBLE);
    }

    void pause() { if (sp != null) { sp.pause(); syncIcon(); } }
    boolean isPlaying() { return sp != null && sp.isPlaying(); }
    int pos() { return sp == null ? 0 : sp.pos(); }
    boolean hasSource() { return pv != null; }

    void release() {
        h.removeCallbacksAndMessages(null);
        if (ringAnim != null) ringAnim.cancel();
        release0();
    }

    private void release0() {
        if (sp != null) { sp.release(); sp = null; }
    }

    // ------------------------------------------------------------------ iç

    private void startRing() {
        ringAnim = ObjectAnimator.ofFloat(ring, "alpha", 0.0f, 0.8f, 0.0f);
        ringAnim.setDuration(1600);
        ringAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        ringAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                float f = a.getAnimatedFraction();
                float s = 1f + 0.55f * f;
                ring.setScaleX(s);
                ring.setScaleY(s);
            }
        });
        ringAnim.start();
    }

    private void showIdle() {
        loading = false;
        spin.setVisibility(GONE);
        center.setVisibility(VISIBLE);
        ring.setVisibility(VISIBLE);
        bar.setVisibility(GONE);
        centerIc.setType(Ic.PLAY);
        Ui.popIn(center, 100);
    }

    private void start() {
        if (pv == null) return;
        if (sp != null) return;
        setLoading(true);
        sp = new SyncPlayer(this);
        if (sv.getHolder().getSurface().isValid()) sp.setSurface(sv.getHolder());
        Music.pause();
        stage("Akış bağlanıyor…");
        sp.open(act, pv, pa, startAt, true);
    }

    private void setLoading(boolean on) {
        loading = on;
        spin.setVisibility(on ? VISIBLE : GONE);
        if (!on) stageTx.setVisibility(GONE);
        if (on) {
            ring.setVisibility(GONE);
            center.setVisibility(GONE);
        }
    }

    private void toggle() {
        if (sp == null) { start(); return; }
        if (!sp.ready()) return;
        if (sp.isPlaying()) { sp.pause(); showControls(true); }
        else { ended = false; sp.play(); scheduleHide(); }
        syncIcon();
        center.setScaleX(0.7f);
        center.setScaleY(0.7f);
        Ui.an(center, 320, 0, new OvershootInterpolator(2.4f)).scaleX(1f).scaleY(1f).start();
    }

    private void syncIcon() {
        centerIc.setType(sp != null && sp.isPlaying() ? Ic.PAUSE : Ic.PLAY);
    }

    private void showControls(boolean on) {
        if (loading && on) return;
        controls = on;
        final View[] vs = top.getVisibility() == VISIBLE ? new View[]{bar, center, top} : new View[]{bar, center};
        for (final View v : vs) {
            if (on) {
                v.setVisibility(VISIBLE);
                Ui.an(v, 200, 0, new DecelerateInterpolator()).alpha(1f).start();
            } else {
                Ui.an(v, 260, 0, new DecelerateInterpolator()).alpha(0f).withEndAction(new Runnable() {
                    @Override public void run() { if (!controls) v.setVisibility(INVISIBLE); }
                }).start();
            }
        }
        ring.setVisibility(on && sp == null ? VISIBLE : GONE);
        if (on) scheduleHide();
    }

    private void scheduleHide() {
        h.removeCallbacks(autoHide);
        h.postDelayed(autoHide, 3200);
    }

    private void flash(final TextView t) {
        t.animate().cancel();
        t.setScaleX(0.7f);
        t.setScaleY(0.7f);
        t.setAlpha(1f);
        Ui.an(t, 260, 0, new OvershootInterpolator(2f)).scaleX(1f).scaleY(1f).withEndAction(new Runnable() {
            @Override public void run() { Ui.an(t, 320, 200, new DecelerateInterpolator()).alpha(0f).start(); }
        }).start();
    }

    private void fit() {
        if (vw <= 0 || vh <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
        float s = Math.min(getWidth() / (float) vw, getHeight() / (float) vh);
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) sv.getLayoutParams();
        int w = Math.round(vw * s), hh = Math.round(vh * s);
        if (p.width != w || p.height != hh) {
            p.width = w;
            p.height = hh;
            sv.setLayoutParams(p);
        }
    }

    // ------------------------------------------------------------------ SurfaceHolder.Callback
    @Override public void surfaceCreated(SurfaceHolder holder) { if (sp != null) sp.setSurface(holder); }
    @Override public void surfaceChanged(SurfaceHolder holder, int f, int w, int hh) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) { if (sp != null) sp.setSurface(null); }

    // ------------------------------------------------------------------ SyncPlayer.Cb
    @Override public void onPrepared(int d) {
        dur = d;
        tTot.setText(Ui.time(d));
        setLoading(false);
        bar.setVisibility(VISIBLE);
        center.setVisibility(VISIBLE);
        syncIcon();
        showControls(true);
        Ui.an(poster, 450, 250, new DecelerateInterpolator()).alpha(0f).withEndAction(new Runnable() {
            @Override public void run() { poster.setVisibility(GONE); }
        }).start();
    }

    @Override public void onSize(int w, int hh) { vw = w; vh = hh; fit(); }

    @Override public void onBuffering(boolean b) {
        if (sp == null || !sp.ready()) return;
        spin.setVisibility(b ? VISIBLE : GONE);
    }

    @Override public void onComplete() {
        ended = true;
        centerIc.setType(Ic.PLAY);
        showControls(true);
    }

    @Override public void onError(String msg) {
        lastErr = msg == null ? "" : msg;
        release0();
        setLoading(false);
        errored = true;
        poster.setVisibility(VISIBLE);
        poster.setAlpha(1f);
        showIdle();
        if (onFail != null) onFail.run(); else Ui.snack(act, msg);
    }
}
