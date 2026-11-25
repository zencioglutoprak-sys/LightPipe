package com.hafif.tube;

import android.app.Activity;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Ayarlar: sunucular, oynatma, müzik, hakkında. */
final class SettingsPage extends Page {
    private static final int[] Q = {144, 240, 360, 480, 720, 1080};
    private static final String[] REP = {"Kapalı", "Tümü", "Tek parça"};

    private final LinearLayout col, rows;
    private final List<LinearLayout> cards = new ArrayList<>();
    private final TextView[] qChips = new TextView[Q.length];
    private final TextView[] rChips = new TextView[3];
    private Toggle land, shuf;

    SettingsPage(Activity act) {
        super(act);
        int p = Ui.dp(a, 16);
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root = sv;
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(p, Ui.dp(a, 6), p, Ui.dp(a, 24));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));

        // --- Hesap
        final boolean logged = Auth.logged(a);
        LinearLayout c0 = card("YouTube hesabı", logged
                ? "Hesap açık: öneriler ve abonelikler görünür. Oturum hatırlanır."
                : "Kendi hesabınla giriş yap: öneriler ve abonelikler gelir, oturum bir sonraki açılışta da hatırlanır.");
        TextView accBtn = logged ? Ui.ghostButton(a, "Çıkış yap") : Ui.redButton(a, "Giriş yap");
        accBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (logged) {
                    Auth.clear(a);
                    try {
                        android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
                        cm.removeAllCookies(null);
                        cm.flush();
                    } catch (Exception ignored) {}
                    a.recreate();
                } else {
                    a.startActivityForResult(new android.content.Intent(a, LoginActivity.class), 77);
                }
            }
        });
        c0.addView(accBtn, new LinearLayout.LayoutParams(-1, -2));

        // --- Tema
        LinearLayout ct = card("Tema", "10 renk teması. Seçince uygulama yenilenir ve renkler hemen değişir.");
        HorizontalScrollView ths = new HorizontalScrollView(a);
        ths.setHorizontalScrollBarEnabled(false);
        LinearLayout thr = new LinearLayout(a);
        final int curTheme = Themes.current();
        for (int i = 0; i < Themes.count(); i++) {
            final int ti = i;
            LinearLayout cell = new LinearLayout(a);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(Themes.swatch(i));
            dot.setStroke(Ui.dp(a, i == curTheme ? 3 : 1), i == curTheme ? Ui.TEXT : Ui.STROKE2);
            View dv = new View(a);
            dv.setBackground(dot);
            cell.addView(dv, new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)));
            TextView nm = Ui.text(a, Themes.NAMES[i], 11, i == curTheme ? Ui.TEXT : Ui.SUB, i == curTheme);
            nm.setGravity(Gravity.CENTER);
            nm.setPadding(0, Ui.dp(a, 6), 0, 0);
            cell.addView(nm, new LinearLayout.LayoutParams(-2, -2));
            Ui.press(cell);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (ti == Themes.current()) return;
                    Themes.save(a, ti);
                    a.recreate();
                }
            });
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.rightMargin = Ui.dp(a, 10);
            thr.addView(cell, tp);
        }
        ths.addView(thr);
        ct.addView(ths);

        // --- Dil
        LinearLayout cl = card("Dil", "Türkçe / English");
        LinearLayout lr = new LinearLayout(a);
        final TextView[] lc = new TextView[2];
        final String[] codes = {"tr", "en"};
        String[] lnames = {"Türkçe", "English"};
        boolean en = L.isEn(a);
        for (int i = 0; i < 2; i++) {
            final int k = i;
            lc[i] = Ui.chip(a, lnames[i], (i == 1) == en);
            lc[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (L.isEn(a) == (k == 1)) return;
                    L.set(a, codes[k]);
                    a.recreate();
                }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.rightMargin = Ui.dp(a, 8);
            lr.addView(lc[i], cp);
        }
        cl.addView(lr);

        // --- Sunucular
        LinearLayout c1 = card("Sunucular", "YouTube'a doğrudan bağlanmak yerine bu Invidious sunucuları kullanılır. "
                + "Biri çalışmazsa sıradakine geçilir.");
        rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        c1.addView(rows);
        for (String s : Api.instances(a)) addRow(s);
        LinearLayout ib = new LinearLayout(a);
        TextView add = Ui.ghostButton(a, "+ Sunucu ekle");
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { addRow(""); }
        });
        TextView reset = Ui.ghostButton(a, "Varsayılana dön");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                rows.removeAllViews();
                for (String s : Api.DEFAULT_INSTANCES) addRow(s);
                Ui.snack(a, "Varsayılan sunucular yüklendi");
            }
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1f);
        l1.rightMargin = Ui.dp(a, 8);
        ib.addView(add, l1);
        ib.addView(reset, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams ibp = new LinearLayout.LayoutParams(-1, -2);
        ibp.topMargin = Ui.dp(a, 6);
        c1.addView(ib, ibp);

        // --- Oynatma
        LinearLayout c2 = card("Oynatma", "Varsayılan kalite. Ayrı akış gerekiyorsa görüntü ve ses anında birlikte oynatılır. "
                + "İndirmede 144p'den 8K'ya her kalite seçilebilir.");
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout qr = new LinearLayout(a);
        int cur = Api.prefs(a).getInt("quality", 720);
        for (int i = 0; i < Q.length; i++) {
            final int k = i;
            qChips[i] = Ui.chip(a, Q[i] + "p", Q[i] == cur);
            qChips[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    for (int j = 0; j < Q.length; j++) Ui.setChip(a, qChips[j], j == k, true);
                    Api.prefs(a).edit().putInt("quality", Q[k]).apply();
                }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.rightMargin = Ui.dp(a, 8);
            qr.addView(qChips[i], cp);
        }
        hs.addView(qr);
        c2.addView(hs);

        // --- Akış istemcisi (Morphe/ReVanced "Video akışlarını taklit et" gibi)
        LinearLayout cc = card("Akış istemcisi", "Videonun hangi YouTube istemcisi taklit edilerek alınacağı. "
                + "Video 360p'de kalıyorsa başka bir istemci dene. Seçilen çalışmazsa otomatik moda düşer.");
        HorizontalScrollView hs2 = new HorizontalScrollView(a);
        hs2.setHorizontalScrollBarEnabled(false);
        LinearLayout cr = new LinearLayout(a);
        final int n = Innertube.CLIENT_NAMES.length;
        final TextView[] cChips = new TextView[n];
        int curC = Api.prefs(a).getInt("client", 0);
        for (int i = 0; i < n; i++) {
            final int k = i;
            cChips[i] = Ui.chip(a, Innertube.CLIENT_NAMES[i], i == curC);
            cChips[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    for (int j = 0; j < n; j++) Ui.setChip(a, cChips[j], j == k, true);
                    Api.prefs(a).edit().putInt("client", k).apply();
                    Innertube.clientPref = k;
                    Api.forgetAll();
                }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.rightMargin = Ui.dp(a, 8);
            cr.addView(cChips[i], cp);
        }
        hs2.addView(cr);
        cc.addView(hs2);
        land = new Toggle(a, Api.prefs(a).getBoolean("landscape", false));
        land.setListener(new Toggle.Listener() {
            @Override public void on(boolean v) { Api.prefs(a).edit().putBoolean("landscape", v).apply(); }
        });
        c2.addView(switchRow("Tam ekranda yatay zorla", land));

        // --- Müzik
        LinearLayout c3 = card("Müzik çalar", "İndirdiğin ses dosyaları uygulama içinde müzik gibi çalar, arka planda da devam eder.");
        Music.loadPrefs(a);
        LinearLayout rr = new LinearLayout(a);
        for (int i = 0; i < 3; i++) {
            final int k = i;
            rChips[i] = Ui.chip(a, REP[i], i == Music.repeat);
            rChips[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    for (int j = 0; j < 3; j++) Ui.setChip(a, rChips[j], j == k, true);
                    Api.prefs(a).edit().putInt("repeat", k).apply();
                    Music.loadPrefs(a);
                }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.rightMargin = Ui.dp(a, 8);
            rr.addView(rChips[i], cp);
        }
        TextView rl = Ui.text(a, "Tekrar", 13, Ui.SUB, true);
        rl.setPadding(0, 0, 0, Ui.dp(a, 8));
        c3.addView(rl);
        c3.addView(rr);
        shuf = new Toggle(a, Music.shuffle);
        shuf.setListener(new Toggle.Listener() {
            @Override public void on(boolean v) { Api.prefs(a).edit().putBoolean("shuffle", v).apply(); Music.loadPrefs(a); }
        });
        c3.addView(switchRow("Karıştır", shuf));

        // --- Loglar
        LinearLayout logCard = card("Loglar", "Uygulamanın tüm kaydı (akış, hata, çökme, indirme). Kopyalayıp bana gönderebilirsin.");
        final TextView logTx = Ui.text(a, Dbg.dump(60), 10.5f, Ui.SUB, false);
        logTx.setTextIsSelectable(true);
        logTx.setPadding(0, 0, 0, Ui.dp(a, 12));
        logCard.addView(logTx);
        LinearLayout logRow = new LinearLayout(a);
        TextView bCopy = Ui.redButton(a, "Kopyala");
        TextView bShare = Ui.ghostButton(a, "Paylaş");
        TextView bRef = Ui.ghostButton(a, "Yenile");
        TextView bClr = Ui.ghostButton(a, "Temizle");
        bCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("LightPipe log", Dbg.full()));
                    Ui.snack(a, "Log kopyalandı");
                } catch (Exception e) { Ui.snack(a, "Kopyalanamadı"); }
            }
        });
        bShare.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(android.content.Intent.EXTRA_TEXT, Dbg.full());
                    a.startActivity(android.content.Intent.createChooser(i, "Log"));
                } catch (Exception e) { Ui.snack(a, "Paylaşılamadı"); }
            }
        });
        bRef.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { logTx.setText(Dbg.dump(60)); }
        });
        bClr.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Dbg.clear(); logTx.setText(""); Ui.snack(a, "Log temizlendi"); }
        });
        for (TextView lbt : new TextView[]{bCopy, bShare, bRef, bClr}) {
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
            bp.rightMargin = Ui.dp(a, 8);
            logRow.addView(lbt, bp);
        }
        HorizontalScrollView logScroll = new HorizontalScrollView(a);
        logScroll.setHorizontalScrollBarEnabled(false);
        logScroll.addView(logRow);
        logCard.addView(logScroll);

        // --- Hakkında
        LinearLayout c4 = card("LightPipe 1.2.3", "Reklamsız, hesapsız, hafif YouTube istemcisi ve indirici. "
                + "Tüm kod sıfırdan yazıldı, hiçbir kütüphane yok.");
    }

    private LinearLayout card(String title, String sub) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.box(a, Ui.PANEL, Ui.STROKE, 22));
        c.setPadding(Ui.dp(a, 18), Ui.dp(a, 16), Ui.dp(a, 18), Ui.dp(a, 18));
        TextView t = Ui.text(a, title, 17, Ui.TEXT, true);
        c.addView(t);
        TextView s = Ui.text(a, sub, 12.5f, Ui.SUB, false);
        s.setLineSpacing(0, 1.15f);
        s.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 14));
        c.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(a, 14);
        col.addView(c, lp);
        cards.add(c);
        return c;
    }

    private View switchRow(String label, Toggle t) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(a, 16), 0, 0);
        r.addView(Ui.text(a, label, 15, Ui.TEXT, true), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(t);
        return r;
    }

    private void addRow(String s) {
        final LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.box(a, Ui.PANEL2, Ui.STROKE, 14));
        row.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 4), 0);
        EditText e = Ui.input(a, "https://sunucu.adresi");
        e.setText(s);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setTextSize(13);
        row.addView(e, new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1f));
        FrameLayout del = new FrameLayout(a);
        del.addView(new Ic(a, Ic.CLOSE, 18, Ui.SUB), new FrameLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18), Gravity.CENTER));
        Ui.press(del, 0.8f);
        del.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.an(row, 220, 0, new android.view.animation.AccelerateInterpolator()).alpha(0f)
                        .translationX(Ui.dp(a, 40)).withEndAction(new Runnable() {
                            @Override public void run() { rows.removeView(row); }
                        }).start();
            }
        });
        row.addView(del, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(a, 8);
        rows.addView(row, lp);
        if (rows.isShown()) Ui.popIn(row, 0);
    }

    private void saveInstances() {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++) {
            EditText e = (EditText) ((LinearLayout) rows.getChildAt(i)).getChildAt(0);
            String s = e.getText().toString().trim();
            if (s.isEmpty()) continue;
            s = Api.norm(s);
            if (!l.contains(s)) l.add(s);
        }
        Api.saveInstances(a, l);
    }

    @Override void resume() {
        for (int i = 0; i < cards.size(); i++) Ui.rise(cards.get(i), 60 + i * 80L);
    }

    @Override void pause() { saveInstances(); }
}
