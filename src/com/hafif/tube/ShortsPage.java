package com.hafif.tube;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shorts sekmesi: tam ekran dikey video, yukarı/aşağı kaydırarak gezinme. */
final class ShortsPage extends Page {
    private static final String[] QUERIES = {"#shorts", "shorts komik", "shorts müzik", "shorts oyun", "shorts yemek", "shorts spor", "shorts hayvanlar"};

    /** Tek bir tam ekran Short. */
    private final class Cell extends FrameLayout implements SyncPlayer.Cb {
        final SurfaceView sv;
        final ImageView poster;
        final TextView title, author;
        final Spin spin;
        final Ic pauseIc;
        Innertube.Item item;
        SyncPlayer sp;
        boolean surfaceOk, wantPlay, current;
        int token;

        Cell() {
            super(ShortsPage.this.a);
            setBackgroundColor(Color.BLACK);
            sv = new SurfaceView(a);
            addView(sv, new FrameLayout.LayoutParams(-1, -1));
            sv.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override public void surfaceCreated(SurfaceHolder h) {
                    surfaceOk = true;
                    if (sp != null) sp.setSurface(h);
                    if (wantPlay && sp == null) startPlayer();
                }
                @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}
                @Override public void surfaceDestroyed(SurfaceHolder h) { surfaceOk = false; }
            });
            poster = new ImageView(a);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(poster, new FrameLayout.LayoutParams(-1, -1));
            View shade = new View(a);
            shade.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00000000, 0xCC000000}));
            addView(shade, new FrameLayout.LayoutParams(-1, Ui.dp(a, 220), Gravity.BOTTOM));
            spin = new Spin(a, 40);
            addView(spin, new FrameLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40), Gravity.CENTER));
            pauseIc = new Ic(a, Ic.PLAY, 64, 0xccffffff);
            pauseIc.setVisibility(GONE);
            addView(pauseIc, new FrameLayout.LayoutParams(Ui.dp(a, 64), Ui.dp(a, 64), Gravity.CENTER));
            LinearLayout info = new LinearLayout(a);
            info.setOrientation(LinearLayout.VERTICAL);
            author = Ui.text(a, "", 15, 0xffffffff, true);
            title = Ui.text(a, "", 14, 0xeeffffff, false);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(author);
            info.addView(title);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            ip.setMargins(Ui.dp(a, 16), 0, Ui.dp(a, 84), Ui.dp(a, 22));
            addView(info, ip);
            // sağ butonlar
            LinearLayout side = new LinearLayout(a);
            side.setOrientation(LinearLayout.VERTICAL);
            side.setGravity(Gravity.CENTER_HORIZONTAL);
            side.addView(sideBtn(Ic.DOWNLOAD, new Runnable() {
                @Override public void run() {
                    if (item != null) a.startActivity(new Intent(a, VideoActivity.class).putExtra("id", item.id).putExtra("dl", true));
                }
            }));
            side.addView(sideBtn(Ic.SHARE, new Runnable() {
                @Override public void run() {
                    if (item == null) return;
                    Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "https://youtube.com/shorts/" + item.id);
                    a.startActivity(Intent.createChooser(s, "Paylaş"));
                }
            }));
            FrameLayout.LayoutParams sp2 = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
            sp2.setMargins(0, 0, Ui.dp(a, 12), Ui.dp(a, 30));
            addView(side, sp2);
        }

        View sideBtn(int ic, final Runnable r) {
            FrameLayout f = new FrameLayout(a);
            f.setBackground(Ui.circle(0x66000000));
            f.addView(new Ic(a, ic, 24, 0xffffffff), new FrameLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24), Gravity.CENTER));
            Ui.press(f, 0.86f);
            f.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 48), Ui.dp(a, 48));
            lp.topMargin = Ui.dp(a, 12);
            return wrap(f, lp);
        }

        private View wrap(View v, LinearLayout.LayoutParams lp) { v.setLayoutParams(lp); return v; }

        void bind(Innertube.Item it) {
            stop();
            item = it;
            author.setText(it == null ? "" : it.author.equals("Shorts") ? "" : it.author);
            title.setText(it == null ? "" : it.title);
            poster.setVisibility(VISIBLE);
            poster.setAlpha(1f);
            pauseIc.setVisibility(GONE);
            if (it != null) Img.load(poster, "https://i.ytimg.com/vi/" + it.id + "/hq2.jpg", a);
        }

        void play() {
            wantPlay = true;
            if (item == null) return;
            if (sp != null) { sp.play(); pauseIc.setVisibility(GONE); return; }
            spin.setVisibility(VISIBLE);
            if (surfaceOk) startPlayer();
        }

        void startPlayer() {
            if (sp != null || item == null) return;
            final int my = ++token;
            final String id = item.id;
            spin.setVisibility(VISIBLE);
            new Thread(new Runnable() {
                @Override public void run() {
                    Api.Video v = null;
                    try { v = Api.videoFast(a, id); } catch (Exception ignored) {}
                    final Api.Video fv = v;
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (my != token || !wantPlay || item == null || !id.equals(item.id)) return;
                            begin(fv);
                        }
                    });
                }
            }).start();
        }

        private int res(Innertube.Fmt f) { return Math.min(f.w > 0 ? f.w : f.h, f.h > 0 ? f.h : f.w); }

        private Innertube.Fmt closest(List<Innertube.Fmt> l) {
            Innertube.Fmt best = null;
            for (Innertube.Fmt f : l) if (best == null || Math.abs(res(f) - 720) < Math.abs(res(best) - 720)) best = f;
            return best;
        }

        void begin(Api.Video v) {
            Innertube.Fmt mux = null, vid = null, aud = null;
            if (v != null) {
                List<Innertube.Fmt> muxL = new ArrayList<>(), avc = new ArrayList<>(), vp9 = new ArrayList<>();
                for (Innertube.Fmt f : v.formats) {
                    if (f.hasVideo && f.hasAudio) muxL.add(f);
                    else if (f.hasVideo) { if (f.isAvc()) avc.add(f); else if (f.isVp9()) vp9.add(f); }
                    else if (f.isAac() && (aud == null || Math.abs(f.bitrate - 128000) < Math.abs(aud.bitrate - 128000))) aud = f;
                }
                // ayrı akış (VR istemcisi gibi) daha net: önce H.264 + AAC, yoksa tek akış, en son VP9
                Innertube.Fmt a1 = closest(avc), m1 = closest(muxL), p1 = closest(vp9);
                // hız öncelikli: tek akış varsa onu kullan (anında başlar)
                if (m1 != null) mux = m1;
                else if (a1 != null && aud != null) vid = a1;
                else if (p1 != null && aud != null) vid = p1;
            }
            if (mux == null && vid == null) {
                spin.setVisibility(GONE);
                ShortsPage.this.skipBroken(this);
                return;
            }
            sp = new SyncPlayer(this);
            if (surfaceOk) sp.setSurface(sv.getHolder());
            if (mux != null) sp.open(a, Uri.parse(mux.url), null, 0, true);
            else sp.open(a, Uri.parse(vid.url), Uri.parse(aud.url), 0, true);
        }

        void togglePause() {
            if (sp == null) return;
            if (sp.isPlaying()) { sp.pause(); pauseIc.setVisibility(VISIBLE); Ui.popIn(pauseIc, 0); }
            else { sp.play(); pauseIc.setVisibility(GONE); }
        }

        void pauseNow() { wantPlay = false; if (sp != null) sp.pause(); }

        void stop() {
            wantPlay = false;
            token++;
            if (sp != null) { sp.release(); sp = null; }
            spin.setVisibility(GONE);
        }

        @Override public void onPrepared(int durMs) {}
        @Override public void onSize(int w, int h) {
            // dikey değilse (yatay) ortala-sığdır
            if (w > 0 && h > 0) {
                float r = (float) w / h;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sv.getLayoutParams();
                int cw = getWidth(), ch = getHeight();
                if (cw > 0 && ch > 0) {
                    if (r >= cw / (float) ch) { lp.width = cw; lp.height = (int) (cw / r); }
                    else { lp.height = ch; lp.width = (int) (ch * r); }
                    lp.gravity = Gravity.CENTER;
                    sv.setLayoutParams(lp);
                }
            }
            poster.animate().alpha(0f).setDuration(220).setStartDelay(0).setListener(null).start();
            spin.setVisibility(GONE);
            fails = 0;
        }
        @Override public void onBuffering(boolean b) { spin.setVisibility(b ? VISIBLE : GONE); }
        @Override public void onComplete() { if (sp != null) { sp.seekTo(0); sp.play(); } }
        @Override public void onError(String msg) {
            if (sp != null) { sp.release(); sp = null; }
            spin.setVisibility(GONE);
            ShortsPage.this.skipBroken(this);
        }
    }

    /** Dikey kaydırma kabı: 3 hücre (önceki / şimdiki / sonraki), sürükleyince kayar. */
    private final class Pager extends FrameLayout {
        final Cell[] cells = new Cell[3];
        int idx = 0, H;
        float downY, lastY, drag;
        boolean dragging, moved;
        VelocityTracker vt;
        final int slop;
        boolean animating;

        Pager() {
            super(ShortsPage.this.a);
            slop = ViewConfiguration.get(a).getScaledTouchSlop();
            for (int i = 0; i < 3; i++) { cells[i] = new Cell(); addView(cells[i], new FrameLayout.LayoutParams(-1, -1)); }
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { H = h; layoutCells(0); }

        /** cells[1] = şimdiki. */
        void layoutCells(float off) {
            if (H == 0) H = getHeight();
            cells[0].setTranslationY(-H + off);
            cells[1].setTranslationY(off);
            cells[2].setTranslationY(H + off);
        }

        void bindAll() {
            cells[0].bind(idx > 0 && idx - 1 < list.size() ? list.get(idx - 1) : null);
            cells[1].bind(idx < list.size() ? list.get(idx) : null);
            cells[2].bind(idx + 1 < list.size() ? list.get(idx + 1) : null);
            cells[0].setVisibility(cells[0].item == null ? INVISIBLE : VISIBLE);
            cells[2].setVisibility(cells[2].item == null ? INVISIBLE : VISIBLE);
            layoutCells(0);
            if (active) cells[1].play();
            if (idx + 1 < list.size()) prefetch(list.get(idx + 1).id);
            if (idx + 4 >= list.size()) loadMore();
        }

        void advance(int dir) {
            idx += dir;
            Cell t;
            if (dir == 1) { t = cells[0]; cells[0] = cells[1]; cells[1] = cells[2]; cells[2] = t; }
            else { t = cells[2]; cells[2] = cells[1]; cells[1] = cells[0]; cells[0] = t; }
            cells[0].pauseNow();
            if (dir == 1) t.bind(idx + 1 < list.size() ? list.get(idx + 1) : null);
            else t.bind(idx > 0 ? list.get(idx - 1) : null);
            // eski şimdiki hücrenin oynatıcısını bırak (yanlış videoyu çalmasın)
            if (dir == 1) cells[0].stop(); else cells[2].stop();
            cells[0].setVisibility(cells[0].item == null ? INVISIBLE : VISIBLE);
            cells[2].setVisibility(cells[2].item == null ? INVISIBLE : VISIBLE);
            layoutCells(0);
            if (active) cells[1].play();
            if (idx + 1 < list.size()) prefetch(list.get(idx + 1).id);
            if (idx + 4 >= list.size()) loadMore();
        }

        void settle(final int dir) { // dir: +1 sonraki, -1 önceki, 0 geri
            if (dir == 1 && idx + 1 >= list.size()) { settle(0); return; }
            if (dir == -1 && idx == 0) { settle(0); return; }
            animating = true;
            final float target = -dir * H;
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(drag, target);
            va.setDuration(dir == 0 ? 200 : 240);
            va.setInterpolator(new DecelerateInterpolator(1.6f));
            va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(android.animation.ValueAnimator an) {
                    drag = (Float) an.getAnimatedValue();
                    layoutCells(drag);
                }
            });
            va.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator an) {
                    animating = false;
                    drag = 0;
                    if (dir != 0) advance(dir); else layoutCells(0);
                }
            });
            va.start();
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (animating) return true;
            if (e.getAction() == MotionEvent.ACTION_DOWN) { downY = lastY = e.getY(); moved = false; }
            else if (e.getAction() == MotionEvent.ACTION_MOVE && Math.abs(e.getY() - downY) > slop) return true;
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (animating) return true;
            if (vt == null) vt = VelocityTracker.obtain();
            vt.addMovement(e);
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downY = lastY = e.getY(); moved = false; return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = e.getY() - downY;
                    if (Math.abs(dy) > slop) moved = true;
                    if (moved) {
                        drag = dy;
                        if ((idx == 0 && drag > 0) || (idx + 1 >= list.size() && drag < 0)) drag *= 0.3f;
                        layoutCells(drag);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    vt.computeCurrentVelocity(1000);
                    float vy = vt.getYVelocity();
                    vt.recycle(); vt = null;
                    if (!moved) { if (e.getAction() == MotionEvent.ACTION_UP) cells[1].togglePause(); return true; }
                    if (drag < -H / 6f || vy < -900) settle(1);
                    else if (drag > H / 6f || vy > 900) settle(-1);
                    else settle(0);
                    return true;
                }
            }
            return true;
        }
    }

    private final List<Innertube.Item> list = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private Pager pager;
    private TextView empty;
    private Spin firstSpin;
    private int qi = 0;
    private boolean loading, active;
    private TextView titleTop;

    ShortsPage(Activity a) {
        super(a);
        FrameLayout r = new FrameLayout(a);
        r.setBackgroundColor(Color.BLACK);
        pager = new Pager();
        r.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        titleTop = Ui.text(a, "Shorts", 18, 0xffffffff, true);
        titleTop.setShadowLayer(6, 0, 2, 0xff000000);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        tp.setMargins(Ui.dp(a, 16), Ui.dp(a, 12), 0, 0);
        r.addView(titleTop, tp);
        firstSpin = new Spin(a, 44);
        r.addView(firstSpin, new FrameLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44), Gravity.CENTER));
        empty = Ui.text(a, "Shorts yüklenemedi. Dokunup tekrar dene.", 14, Ui.SUB, false);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        empty.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                empty.setVisibility(View.GONE);
                fails = 0;
                if (list.isEmpty()) { firstSpin.setVisibility(View.VISIBLE); loadMore(); }
                else { Api.forgetAll(); pager.cells[1].stop(); pager.cells[1].play(); }
            }
        });
        r.addView(empty, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root = r;
    }

    private void prefetch(final String id) {
        new Thread(new Runnable() { @Override public void run() { try { Api.videoFast(a, id); } catch (Exception ignored) {} } }).start();
    }

    private int fails = 0;

    private void skipBroken(Cell c) {
        if (c != pager.cells[1] || pager.animating) return;
        fails++;
        if (fails >= 3) { // üst üste 3 hata: kaydırıp durma, dur ve kullanıcıya söyle
            fails = 0;
            empty.setText("Shorts oynatılamıyor. Dokunup tekrar dene.");
            empty.setVisibility(View.VISIBLE);
            return;
        }
        if (pager.idx + 1 < list.size()) { Ui.snack(a, "Bu Short oynatılamadı, sonrakine geçiliyor"); pager.settle(1); }
    }

    private void loadMore() {
        if (loading) return;
        loading = true;
        final String q = QUERIES[qi++ % QUERIES.length];
        new Thread(new Runnable() {
            @Override public void run() {
                List<Innertube.Item> r = null;
                try { r = Api.search(a, q); } catch (Exception ignored) {}
                final List<Innertube.Item> res = r;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        loading = false;
                        boolean had = !list.isEmpty();
                        if (res != null) for (Innertube.Item it : res) if (it.isShort() && seen.add(it.id)) list.add(it);
                        firstSpin.setVisibility(View.GONE);
                        if (list.isEmpty()) {
                            if (qi < QUERIES.length) loadMore(); else empty.setVisibility(View.VISIBLE);
                            return;
                        }
                        if (!had) pager.bindAll();
                        else {
                            if (pager.idx + 1 < list.size() && pager.cells[2].item == null) {
                                pager.cells[2].bind(list.get(pager.idx + 1));
                                pager.cells[2].setVisibility(View.VISIBLE);
                            }
                        }
                    }
                });
            }
        }).start();
    }

    @Override void resume() {
        active = true;
        if (list.isEmpty()) { firstSpin.setVisibility(View.VISIBLE); loadMore(); }
        else pager.cells[1].play();
    }

    @Override void pause() {
        active = false;
        pager.cells[1].pauseNow();
    }

    @Override void destroy() {
        for (Cell c : pager.cells) c.stop();
    }
}
