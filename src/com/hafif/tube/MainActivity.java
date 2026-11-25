package com.hafif.tube;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ana kabuk: başlık, 3 sayfa (Keşfet / İndirilenler / Ayarlar), kayan göstergeli alt menü, mini müzik çalar, açılış animasyonu. */
public class MainActivity extends Activity {
    private final Page[] pages = new Page[4];
    private FrameLayout host, nav, splash;
    private View indicator;
    private final Ic[] tabIc = new Ic[4];
    private final TextView[] tabTx = new TextView[4];
    private TextView badge;
    private LinearLayout mini;
    private TextView miniTitle;
    private Ic miniPlay;
    private Disc miniDisc;
    private int cur = -1, tabW;
    private long lastBack;
    private boolean miniShown;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            int n = Dl.ACTIVE.size();
            if (n > 0) {
                badge.setText(String.valueOf(n));
                if (badge.getVisibility() != View.VISIBLE) { badge.setVisibility(View.VISIBLE); Ui.popIn(badge, 0); }
            } else badge.setVisibility(View.GONE);
            handler.postDelayed(this, 1000);
        }
    };

    private final Music.Listener ml = new Music.Listener() {
        @Override public void onChange() { syncMini(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Themes.init(this);
        Dbg.init(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        Auth.init(this);
        PoToken.warm(this);

        FrameLayout rootF = new FrameLayout(this);
        rootF.setBackgroundColor(Ui.BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        rootF.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // ---------------- başlık
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 6));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.main_icon);
        head.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        SpannableString ss = new SpannableString("LightPipe");
        ss.setSpan(new ForegroundColorSpan(Ui.TEXT), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(Ui.RED), 5, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView name = Ui.text(this, ss, 22, Ui.TEXT, true);
        name.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
        np.leftMargin = Ui.dp(this, 10);
        head.addView(name, np);
        FrameLayout linkBtn = new FrameLayout(this);
        linkBtn.setBackground(Ui.box(this, Ui.PANEL2, Ui.STROKE, 20));
        linkBtn.addView(new Ic(this, Ic.LINK, 22, Ui.RED_HI), new FrameLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22), Gravity.CENTER));
        Ui.press(linkBtn, 0.88f);
        linkBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { linkSheet(); }
        });
        head.addView(linkBtn, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        host = new FrameLayout(this);
        col.addView(host, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---------------- mini müzik çalar
        mini = new LinearLayout(this);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setBackground(Ui.box(this, Ui.PANEL2, Ui.STROKE2, 20));
        mini.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        mini.setVisibility(View.GONE);
        miniDisc = new Disc(this, 38);
        mini.addView(miniDisc, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
        miniTitle = Ui.text(this, "", 14, Ui.TEXT, true);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        miniTitle.setMarqueeRepeatLimit(-1);
        miniTitle.setSelected(true);
        LinearLayout.LayoutParams mt = new LinearLayout.LayoutParams(0, -2, 1f);
        mt.leftMargin = Ui.dp(this, 12);
        mini.addView(miniTitle, mt);
        FrameLayout pb = new FrameLayout(this);
        pb.setBackground(Ui.redCircle());
        miniPlay = new Ic(this, Ic.PAUSE, 18, 0xffffffff);
        pb.addView(miniPlay, new FrameLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER));
        Ui.press(pb, 0.86f);
        pb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Music.toggle(); }
        });
        mini.addView(pb, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        FrameLayout xb = new FrameLayout(this);
        xb.addView(new Ic(this, Ic.CLOSE, 18, Ui.SUB), new FrameLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER));
        Ui.press(xb, 0.8f);
        xb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Music.stop(); }
        });
        mini.addView(xb, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
        mini.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, MusicActivity.class)); }
        });
        Ui.press(mini, 0.98f);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.setMargins(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4));
        col.addView(mini, mp);

        // ---------------- alt menü
        nav = new FrameLayout(this);
        nav.setBackground(Ui.box(this, Ui.PANEL, Ui.STROKE, 28));
        int np2 = Ui.dp(this, 6);
        nav.setPadding(np2, np2, np2, np2);
        indicator = new View(this);
        indicator.setBackground(Ui.box(this, Ui.REDSOFT, 22));
        nav.addView(indicator, new FrameLayout.LayoutParams(Ui.dp(this, 80), Ui.dp(this, 56)));
        LinearLayout tabs = new LinearLayout(this);
        String[] tn = {"Keşfet", "Shorts", "İndirilenler", "Ayarlar"};
        int[] ti = {Ic.HOME, Ic.VIDEO, Ic.DOWNLOAD, Ic.SETTINGS};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER);
            FrameLayout icBox = new FrameLayout(this);
            tabIc[i] = new Ic(this, ti[i], 24, Ui.SUB);
            icBox.addView(tabIc[i], new FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24), Gravity.CENTER));
            if (i == 2) {
                badge = Ui.text(this, "1", 10, 0xffffffff, true);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(Ui.redCircle());
                badge.setVisibility(View.GONE);
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16), Gravity.TOP | Gravity.RIGHT);
                icBox.addView(badge, bp);
            }
            t.addView(icBox, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 28)));
            tabTx[i] = Ui.text(this, tn[i], 11, Ui.SUB, true);
            tabTx[i].setGravity(Gravity.CENTER);
            tabTx[i].setPadding(0, Ui.dp(this, 2), 0, 0);
            t.addView(tabTx[i], new LinearLayout.LayoutParams(-2, -2));
            Ui.press(t, 0.92f);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (k == cur && k == 0 && pages[0] instanceof HomePage) ((HomePage) pages[0]).refresh();
                    else show(k);
                }
            });
            tabs.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1f));
        }
        nav.addView(tabs, new FrameLayout.LayoutParams(-1, -2));
        nav.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int bt, int ol, int ot, int or, int ob) {
                int w = (r - l - 2 * Ui.dp(MainActivity.this, 6)) / 4;
                if (w != tabW && w > 0) {
                    tabW = w;
                    FrameLayout.LayoutParams ip = (FrameLayout.LayoutParams) indicator.getLayoutParams();
                    ip.width = w;
                    indicator.setLayoutParams(ip);
                    indicator.setTranslationX(Math.max(cur, 0) * w);
                }
            }
        });
        LinearLayout.LayoutParams navp = new LinearLayout.LayoutParams(-1, -2);
        navp.setMargins(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 12));
        col.addView(nav, navp);

        // ---------------- açılış perdesi
        splash = new FrameLayout(this);
        splash.setBackgroundColor(Ui.BG);
        splash.setClickable(true);
        LinearLayout sc = new LinearLayout(this);
        sc.setOrientation(LinearLayout.VERTICAL);
        sc.setGravity(Gravity.CENTER);
        final ImageView sl = new ImageView(this);
        sl.setImageResource(R.drawable.main_icon);
        sc.addView(sl, new LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96)));
        final SpannableString s2 = new SpannableString("LightPipe");
        s2.setSpan(new ForegroundColorSpan(Ui.TEXT), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s2.setSpan(new ForegroundColorSpan(Ui.RED), 5, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        final TextView st = Ui.text(this, s2, 28, Ui.TEXT, true);
        st.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 22));
        st.setGravity(Gravity.CENTER);
        sc.addView(st);
        final Spin sp = new Spin(this, 30);
        sc.addView(sp, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
        splash.addView(sc, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        rootF.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        setContentView(rootF);

        // ilk sayfa
        show(0);
        Intent in = getIntent();
        handleIntent(in);
        if (Build.VERSION.SDK_INT >= 33 && !Api.prefs(this).getBoolean("askedNotif", false)) {
            Api.prefs(this).edit().putBoolean("askedNotif", true).apply();
            try { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1); } catch (Exception ignored) {}
        }

        // splash animasyonu
        sl.setScaleX(0.4f); sl.setScaleY(0.4f); sl.setAlpha(0f);
        Ui.an(sl, 650, 0, new OvershootInterpolator(2.2f)).scaleX(1f).scaleY(1f).alpha(1f).start();
        st.setAlpha(0f);
        st.setTranslationY(Ui.dp(this, 20));
        Ui.an(st, 500, 250, new DecelerateInterpolator(2f)).alpha(1f).translationY(0f).start();
        sp.setAlpha(0f);
        Ui.an(sp, 400, 400, new DecelerateInterpolator()).alpha(1f).start();
        head.setAlpha(0f);
        nav.setAlpha(0f);
        nav.setTranslationY(Ui.dp(this, 60));
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                Ui.an(splash, 450, 0, new AccelerateInterpolator()).alpha(0f).scaleX(1.08f).scaleY(1.08f)
                        .withEndAction(new Runnable() {
                            @Override public void run() { splash.setVisibility(View.GONE); }
                        }).start();
                Ui.an(head, 500, 100, new DecelerateInterpolator(2f)).alpha(1f).start();
                Ui.an(nav, 600, 150, new OvershootInterpolator(1.3f)).alpha(1f).translationY(0f).start();
            }
        }, 1050);
    }

    private void handleIntent(Intent in) {
        if (in != null && in.hasExtra("tab")) show(in.getIntExtra("tab", 0));
        if (in != null && Intent.ACTION_SEND.equals(in.getAction())) {
            String t = in.getStringExtra(Intent.EXTRA_TEXT);
            String id = t == null ? null : Innertube.parseId(t);
            if (id != null) startActivity(new Intent(this, VideoActivity.class).putExtra("id", id));
        }
    }

    @Override protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == 77 && res == RESULT_OK) recreate(); // giriş yapıldı: ekranlar hesaba göre yenilensin
    }

    @Override protected void onNewIntent(Intent in) {
        super.onNewIntent(in);
        handleIntent(in);
    }

    // ------------------------------------------------------------------ sayfalar

    private Page page(int i) {
        if (pages[i] == null) {
            pages[i] = i == 0 ? new HomePage(this) : i == 1 ? new ShortsPage(this) : i == 2 ? new DownloadsPage(this) : new SettingsPage(this);
            host.addView(pages[i].root, new FrameLayout.LayoutParams(-1, -1));
            pages[i].root.setVisibility(View.GONE);
        }
        return pages[i];
    }

    private void show(int i) {
        if (i == cur) return;
        final int old = cur;
        int dir = i > old ? 1 : -1;
        Page np = page(i);
        View nv = np.root;
        boolean first = old < 0;
        if (!first) {
            final View ov = pages[old].root;
            pages[old].pause();
            Ui.an(ov, 220, 0, new AccelerateInterpolator()).alpha(0f).translationX(-dir * Ui.dp(this, 48))
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            if (cur != old) { ov.setVisibility(View.GONE); ov.setTranslationX(0f); ov.setAlpha(1f); }
                        }
                    }).start();
            nv.setTranslationX(dir * Ui.dp(this, 48));
            nv.setAlpha(0f);
        }
        nv.setVisibility(View.VISIBLE);
        if (!first) Ui.an(nv, 340, 60, new DecelerateInterpolator(2f)).alpha(1f).translationX(0f).start();
        cur = i;
        selectTab(i, !first);
        np.resume();
    }

    private void selectTab(int i, boolean animate) {
        for (int k = 0; k < 4; k++) {
            boolean sel = k == i;
            if (animate) {
                tabIc[k].tint(sel ? Ui.RED_HI : Ui.SUB, 220);
                Ui.tweenText(tabTx[k], sel ? Ui.SUB : Ui.TEXT, sel ? Ui.TEXT : Ui.SUB, 220);
                if (sel) {
                    tabIc[k].setScaleX(0.7f);
                    tabIc[k].setScaleY(0.7f);
                    Ui.an(tabIc[k], 420, 60, new OvershootInterpolator(3f)).scaleX(1.12f).scaleY(1.12f).start();
                } else {
                    Ui.an(tabIc[k], 200, 0, new DecelerateInterpolator()).scaleX(1f).scaleY(1f).start();
                }
            } else {
                tabIc[k].setColor(sel ? Ui.RED_HI : Ui.SUB);
                tabTx[k].setTextColor(sel ? Ui.TEXT : Ui.SUB);
                tabIc[k].setScaleX(sel ? 1.12f : 1f);
                tabIc[k].setScaleY(sel ? 1.12f : 1f);
            }
        }
        float x = i * tabW;
        if (animate) Ui.an(indicator, 420, 0, new OvershootInterpolator(1.15f)).translationX(x).start();
        else indicator.setTranslationX(x);
    }

    // ------------------------------------------------------------------ mini oynatıcı

    private void syncMini() {
        boolean has = Music.has();
        if (has) {
            miniTitle.setText(Lib.title(Music.current().name));
            miniPlay.setType(Music.playing() ? Ic.PAUSE : Ic.PLAY);
            miniDisc.setSpinning(Music.playing());
        }
        if (has && !miniShown) {
            miniShown = true;
            mini.setVisibility(View.VISIBLE);
            mini.setTranslationY(Ui.dp(this, 70));
            mini.setAlpha(0f);
            Ui.an(mini, 420, 0, new OvershootInterpolator(1.4f)).translationY(0f).alpha(1f).start();
        } else if (!has && miniShown) {
            miniShown = false;
            Ui.an(mini, 260, 0, new AccelerateInterpolator()).translationY(Ui.dp(this, 70)).alpha(0f)
                    .withEndAction(new Runnable() {
                        @Override public void run() { if (!miniShown) mini.setVisibility(View.GONE); }
                    }).start();
        }
    }

    // ------------------------------------------------------------------ link sayfası

    private void linkSheet() {
        final Sheet s = new Sheet(this, "Link ile indir");
        TextView hint = Ui.text(this, "YouTube videosu veya Shorts linkini yapıştır.", 13, Ui.SUB, false);
        hint.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), Ui.dp(this, 10));
        s.add(hint);
        final LinearLayout field = new LinearLayout(this);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setBackground(Ui.box(this, Ui.PANEL2, Ui.RED, 18));
        field.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 6), 0);
        field.addView(new Ic(this, Ic.LINK, 20, Ui.RED_HI), new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        final EditText e = Ui.input(this, "https://youtu.be/…");
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f);
        ep.leftMargin = Ui.dp(this, 10);
        field.addView(e, ep);
        TextView paste = Ui.text(this, "Yapıştır", 12, Ui.RED_HI, true);
        paste.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        Ui.press(paste, 0.9f);
        field.addView(paste);
        s.add(field);
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData cd = cm == null ? null : cm.getPrimaryClip();
            if (cd != null && cd.getItemCount() > 0) {
                CharSequence t = cd.getItemAt(0).getText();
                if (t != null && Innertube.parseId(t.toString()) != null) e.setText(t);
            }
        } catch (Exception ignored) {}
        paste.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    ClipData cd = cm == null ? null : cm.getPrimaryClip();
                    if (cd != null && cd.getItemCount() > 0 && cd.getItemAt(0).getText() != null) e.setText(cd.getItemAt(0).getText());
                } catch (Exception ignored) {}
            }
        });
        TextView go = Ui.redButton(this, "Videoyu aç ve indir");
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.setMargins(Ui.dp(this, 2), Ui.dp(this, 14), Ui.dp(this, 2), Ui.dp(this, 6));
        s.list.addView(go, gp);
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String id = Innertube.parseId(e.getText().toString());
                if (id == null) { Ui.shake(field); return; }
                s.close();
                startActivity(new Intent(MainActivity.this, VideoActivity.class).putExtra("id", id).putExtra("dl", true));
            }
        });
        s.show();
    }

    // ------------------------------------------------------------------ yaşam döngüsü

    @Override protected void onResume() {
        super.onResume();
        Music.listen(ml);
        syncMini();
        handler.removeCallbacks(tick);
        handler.post(tick);
        if (cur >= 0 && pages[cur] != null) pages[cur].resume();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(tick);
        Music.unlisten(ml);
        if (cur >= 0 && pages[cur] != null) pages[cur].pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        for (Page p : pages) if (p != null) p.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (cur == 0 && pages[0] instanceof HomePage && ((HomePage) pages[0]).back()) return;
        if (cur != 0) { show(0); return; }
        long now = SystemClock.elapsedRealtime();
        if (now - lastBack < 2000) { super.onBackPressed(); return; }
        lastBack = now;
        Ui.snack(this, "Çıkmak için tekrar geri bas");
    }
}
