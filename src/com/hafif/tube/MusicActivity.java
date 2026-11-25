package com.hafif.tube;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/** Müzik çalar: dönen plak, arama çubuğu, tekrar (kapalı/tümü/tek), karıştır, sıradaki parçalar. */
public class MusicActivity extends Activity {
    private Disc disc;
    private View glow;
    private TextView title, sub, tCur, tTot, queueHead;
    private Seek seek;
    private Ic playIc, repIc, shufIc;
    private FrameLayout playBtn;
    private BaseAdapter queue;
    private ObjectAnimator glowAnim;
    private int lastIndex = -2;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Music.Listener ml = new Music.Listener() {
        @Override public void onChange() { refresh(); }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (Music.has() && !seek.dragging()) {
                int d = Music.dur(), p = Music.pos();
                seek.setFrac(d > 0 ? p / (float) d : 0f);
                tCur.setText(Ui.time(p));
                tTot.setText(Ui.time(d));
            }
            h.postDelayed(this, 300);
        }
    };

    private FrameLayout btn(Ic ic, int sizeDp, int boxDp) {
        FrameLayout f = new FrameLayout(this);
        f.addView(ic, new FrameLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp), Gravity.CENTER));
        Ui.press(f, 0.85f);
        return f;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Themes.init(this);
        getWindow().setStatusBarColor(Ui.STATUSTOP);
        getWindow().setNavigationBarColor(Ui.BG);
        if (!Music.has()) { finish(); return; }
        int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        int discDp = (int) (Math.min(sw * 0.60f, sh * 0.27f) / getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Ui.TOPTINT, Ui.BG, Ui.BG}));

        // üst bar
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), 0);
        FrameLayout back = btn(new Ic(this, Ic.BACK, 22, Ui.TEXT), 22, 44);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        TextView lab = Ui.text(this, "ŞİMDİ ÇALIYOR", 12, Ui.RED_HI, true);
        lab.setLetterSpacing(0.15f);
        lab.setGravity(Gravity.CENTER);
        top.addView(lab, new LinearLayout.LayoutParams(0, -2, 1f));
        FrameLayout stop = btn(new Ic(this, Ic.CLOSE, 20, Ui.SUB), 20, 44);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Music.stop(); finish(); }
        });
        top.addView(stop, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // plak + parıltı
        FrameLayout stage = new FrameLayout(this);
        glow = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Ui.alpha(Ui.RED, 0x55));
        glow.setBackground(gd);
        int gs = Ui.dp(this, discDp + 34);
        stage.addView(glow, new FrameLayout.LayoutParams(gs, gs, Gravity.CENTER));
        disc = new Disc(this, discDp);
        disc.setElevation(Ui.dp(this, 14));
        stage.addView(disc, new FrameLayout.LayoutParams(Ui.dp(this, discDp), Ui.dp(this, discDp), Gravity.CENTER));
        root.addView(stage, new LinearLayout.LayoutParams(-1, Ui.dp(this, discDp + 50)));
        glowAnim = Ui.pulse(glow, 0.25f, 0.8f, 1300);

        title = Ui.text(this, "", 21, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(this, 28), Ui.dp(this, 6), Ui.dp(this, 28), 0);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        sub = Ui.text(this, "", 13, Ui.SUB, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        // arama çubuğu
        LinearLayout sr = new LinearLayout(this);
        sr.setGravity(Gravity.CENTER_VERTICAL);
        sr.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        tCur = Ui.text(this, "0:00", 12, Ui.SUB, true);
        tTot = Ui.text(this, "0:00", 12, Ui.SUB, true);
        seek = new Seek(this);
        sr.addView(tCur);
        sr.addView(seek, new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1f));
        sr.addView(tTot);
        root.addView(sr, new LinearLayout.LayoutParams(-1, -2));
        seek.setListener(new Seek.Listener() {
            @Override public void onSeek(float f, boolean done) {
                tCur.setText(Ui.time((int) (f * Music.dur())));
                if (done) Music.seek((int) (f * Music.dur()));
            }
        });

        // kontroller
        LinearLayout ctl = new LinearLayout(this);
        ctl.setGravity(Gravity.CENTER);
        ctl.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 10));
        shufIc = new Ic(this, Ic.SHUFFLE, 24, Ui.SUB);
        FrameLayout shuf = btn(shufIc, 24, 48);
        shuf.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Music.toggleShuffle();
                Ui.snack(MusicActivity.this, Music.shuffle ? "Karıştır: açık" : "Karıştır: kapalı");
            }
        });
        FrameLayout prev = btn(new Ic(this, Ic.PREV, 28, Ui.TEXT), 28, 56);
        prev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Music.prev(); } });
        playIc = new Ic(this, Ic.PAUSE, 32, 0xffffffff);
        playBtn = new FrameLayout(this);
        playBtn.setBackground(Ui.redCircle());
        playBtn.setElevation(Ui.dp(this, 10));
        playBtn.addView(playIc, new FrameLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32), Gravity.CENTER));
        Ui.press(playBtn, 0.9f);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Music.toggle();
                playBtn.setScaleX(0.8f);
                playBtn.setScaleY(0.8f);
                Ui.an(playBtn, 360, 0, new OvershootInterpolator(3f)).scaleX(1f).scaleY(1f).start();
            }
        });
        FrameLayout next = btn(new Ic(this, Ic.NEXT, 28, Ui.TEXT), 28, 56);
        next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Music.next(); } });
        repIc = new Ic(this, Ic.REPEAT, 24, Ui.SUB);
        FrameLayout rep = btn(repIc, 24, 48);
        rep.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Music.cycleRepeat();
                Ui.snack(MusicActivity.this, Music.repeat == Music.OFF ? "Tekrar: kapalı"
                        : Music.repeat == Music.ALL ? "Tekrar: tüm liste" : "Tekrar: tek parça");
                repIc.setScaleX(0.6f);
                repIc.setScaleY(0.6f);
                Ui.an(repIc, 380, 0, new OvershootInterpolator(3f)).scaleX(1f).scaleY(1f).start();
            }
        });
        ctl.addView(shuf, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        ctl.addView(prev, new LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 58)));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72));
        pp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        ctl.addView(playBtn, pp);
        ctl.addView(next, new LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 58)));
        ctl.addView(rep, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        root.addView(ctl, new LinearLayout.LayoutParams(-1, -2));

        // sıra
        queueHead = Ui.text(this, "SIRADAKİLER", 11, Ui.DIM, true);
        queueHead.setLetterSpacing(0.15f);
        queueHead.setPadding(Ui.dp(this, 24), Ui.dp(this, 4), 0, Ui.dp(this, 6));
        root.addView(queueHead);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 12));
        lv.setClipToPadding(false);
        lv.setVerticalScrollBarEnabled(false);
        queue = new BaseAdapter() {
            @Override public int getCount() { return Music.tracks().size(); }
            @Override public Object getItem(int i) { return Music.tracks().get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup parent) {
                if (cv == null) {
                    LinearLayout r = new LinearLayout(MusicActivity.this);
                    r.setGravity(Gravity.CENTER_VERTICAL);
                    r.setPadding(Ui.dp(MusicActivity.this, 12), Ui.dp(MusicActivity.this, 10), Ui.dp(MusicActivity.this, 12), Ui.dp(MusicActivity.this, 10));
                    FrameLayout slot = new FrameLayout(MusicActivity.this);
                    r.addView(slot, new LinearLayout.LayoutParams(Ui.dp(MusicActivity.this, 26), Ui.dp(MusicActivity.this, 26)));
                    TextView num = Ui.text(MusicActivity.this, "", 12, Ui.DIM, true);
                    num.setGravity(Gravity.CENTER);
                    slot.addView(num, new FrameLayout.LayoutParams(-1, -1));
                    Eq eq = new Eq(MusicActivity.this, 18, Ui.RED_HI);
                    slot.addView(eq, new FrameLayout.LayoutParams(Ui.dp(MusicActivity.this, 18), Ui.dp(MusicActivity.this, 18), Gravity.CENTER));
                    TextView t = Ui.text(MusicActivity.this, "", 14, Ui.TEXT, true);
                    t.setSingleLine(true);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
                    tp.leftMargin = Ui.dp(MusicActivity.this, 10);
                    r.addView(t, tp);
                    r.setTag(new View[]{num, eq, t});
                    Ui.press(r, 0.98f);
                    cv = r;
                }
                View[] p = (View[]) cv.getTag();
                Music.Track tr = Music.tracks().get(i);
                boolean curr = i == Music.index();
                ((TextView) p[0]).setText(String.valueOf(i + 1));
                p[0].setVisibility(curr ? View.GONE : View.VISIBLE);
                p[1].setVisibility(curr ? View.VISIBLE : View.GONE);
                ((Eq) p[1]).setActive(Music.playing());
                TextView tv = (TextView) p[2];
                tv.setText(Lib.title(tr.name));
                tv.setTextColor(curr ? Ui.RED_HI : Ui.TEXT);
                cv.setBackground(curr ? Ui.box(MusicActivity.this, Ui.REDSOFT, 14) : null);
                return cv;
            }
        };
        lv.setAdapter(queue);
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) { Music.open(pos); }
        });
        root.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        // giriş animasyonları
        View[] ins = {stage, title, sub, sr, ctl, queueHead};
        for (int i = 0; i < ins.length; i++) Ui.rise(ins[i], 100 + i * 70L);
        disc.setScaleX(0.5f);
        disc.setScaleY(0.5f);
        Ui.an(disc, 700, 120, new OvershootInterpolator(1.6f)).scaleX(1f).scaleY(1f).start();
        refresh();
    }

    private void refresh() {
        if (!Music.has()) { finish(); return; }
        Music.Track t = Music.current();
        boolean pl = Music.playing();
        String ti = Lib.title(t.name);
        if (Music.index() != lastIndex) {
            boolean first = lastIndex == -2;
            lastIndex = Music.index();
            title.setText(ti);
            sub.setText(Music.error != null ? Music.error : Lib.tag(t.name) + " · " + (Music.index() + 1) + "/" + Music.tracks().size());
            if (!first) {
                Ui.rise(title, 0);
                Ui.fadeIn(sub, 80);
                disc.setScaleX(0.8f);
                disc.setScaleY(0.8f);
                Ui.an(disc, 520, 0, new OvershootInterpolator(2.2f)).scaleX(1f).scaleY(1f).start();
            }
        } else if (Music.error != null) {
            sub.setText(Music.error);
        }
        playIc.setType(pl ? Ic.PAUSE : Ic.PLAY);
        disc.setSpinning(pl);
        glow.setVisibility(pl ? View.VISIBLE : View.INVISIBLE);
        repIc.setType(Music.repeat == Music.ONE ? Ic.REPEAT1 : Ic.REPEAT);
        repIc.tint(Music.repeat == Music.OFF ? Ui.SUB : Ui.RED_HI, 220);
        shufIc.tint(Music.shuffle ? Ui.RED_HI : Ui.SUB, 220);
        queue.notifyDataSetChanged();
    }

    @Override protected void onResume() {
        super.onResume();
        Music.listen(ml);
        h.post(tick);
        if (Music.has()) refresh();
    }

    @Override protected void onPause() {
        h.removeCallbacks(tick);
        Music.unlisten(ml);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (glowAnim != null) glowAnim.cancel();
        super.onDestroy();
    }
}
