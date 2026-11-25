package com.hafif.tube;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

/** Keşfet: arama kutusu, hızlı konu hapları, kart listesi. */
final class HomePage extends Page {
    private final List<Innertube.Item> items = new ArrayList<>();
    private BaseAdapter adapter;
    private ListView list;
    private EditText input;
    private Ic clear;
    private TextView section;
    private LinearLayout skel, emptyBox;
    private TextView emptyTitle, emptySub;
    private Ic emptyIc;
    private GradientDrawable fieldBg;
    private int itemW;
    private int lastAnimated = -1;
    private boolean searched, shown;
    private LinearLayout chipRow;

    private static final String[][] TOPICS = {
            {"Müzik", "müzik"}, {"Oyun", "oyun"}, {"Haberler", "haberler"},
            {"Teknoloji", "teknoloji"}, {"Komik", "komik videolar"}, {"Film", "fragman"}, {"Spor", "spor"}};

    HomePage(Activity act) {
        super(act);
        itemW = a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 32);
        int p = Ui.dp(a, 16);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        root = col;

        // --- arama alanı
        LinearLayout field = new LinearLayout(a);
        field.setGravity(Gravity.CENTER_VERTICAL);
        fieldBg = Ui.box(a, Ui.PANEL2, Ui.STROKE, 22);
        field.setBackground(fieldBg);
        field.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 6), 0);
        field.addView(new Ic(a, Ic.SEARCH, 20, Ui.SUB), new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)));
        input = Ui.input(a, "YouTube'da ara");
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, Ui.dp(a, 52), 1f);
        ip.leftMargin = Ui.dp(a, 10);
        field.addView(input, ip);
        clear = new Ic(a, Ic.CLOSE, 18, Ui.SUB);
        FrameLayout clearBox = new FrameLayout(a);
        clearBox.addView(clear, new FrameLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18), Gravity.CENTER));
        clearBox.setVisibility(View.GONE);
        field.addView(clearBox, new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 36)));
        final FrameLayout go = new FrameLayout(a);
        go.setBackground(Ui.redCircle());
        go.addView(new Ic(a, Ic.SEARCH, 18, 0xffffffff), new FrameLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18), Gravity.CENTER));
        Ui.press(go, 0.86f);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40));
        gp.leftMargin = Ui.dp(a, 2);
        field.addView(go, gp);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.setMargins(p, Ui.dp(a, 6), p, Ui.dp(a, 10));
        col.addView(field, fp);

        final FrameLayout clearRef = clearBox;
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                boolean has = s.length() > 0;
                if (has && clearRef.getVisibility() != View.VISIBLE) {
                    clearRef.setVisibility(View.VISIBLE);
                    Ui.popIn(clearRef, 0);
                } else if (!has) clearRef.setVisibility(View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        clearBox.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { input.setText(""); }
        });
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                ValueAnimator va = ValueAnimator.ofInt(0, 1);
                final int from = f ? Ui.STROKE : Ui.RED, to = f ? Ui.RED : Ui.STROKE;
                va.setDuration(240);
                va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator an) {
                        float t = an.getAnimatedFraction();
                        fieldBg.setStroke(Math.max(1, Ui.dp(a, f2(t))), blend(from, to, t));
                    }
                    float f2(float t) { return 1f + t; }
                });
                va.start();
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                if (id == EditorInfo.IME_ACTION_SEARCH) { go(v.getText().toString()); return true; }
                return false;
            }
        });
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { go(input.getText().toString()); }
        });

        // --- konu hapları
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chipRow = new LinearLayout(a);
        chipRow.setPadding(p, 0, p, 0);
        for (final String[] t : TOPICS) {
            TextView c = Ui.chip(a, t[0], false);
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { input.setText(t[1]); go(t[1]); }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.rightMargin = Ui.dp(a, 8);
            chipRow.addView(c, cp);
        }
        if (Auth.logged(a)) {
            TextView kc = Ui.chip(a, "Kanallar", false);
            kc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { a.startActivity(new android.content.Intent(a, ChannelsActivity.class)); }
            });
            LinearLayout.LayoutParams kcp = new LinearLayout.LayoutParams(-2, -2);
            kcp.rightMargin = Ui.dp(a, 8);
            chipRow.addView(kc, 0, kcp);
            final String[][] feeds = {{"Önerilenler", "FEwhat_to_watch"}, {"Abonelikler", "FEsubscriptions"}};
            for (int i = feeds.length - 1; i >= 0; i--) {
                final String[] f = feeds[i];
                TextView c = Ui.chip(a, f[0], false);
                c.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { loadFeed(f[1], f[0], f[1].equals("FEwhat_to_watch")); }
                });
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
                cp.rightMargin = Ui.dp(a, 8);
                chipRow.addView(c, 0, cp);
            }
        }
        hs.addView(chipRow, new FrameLayout.LayoutParams(-2, -2));
        col.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        section = Ui.text(a, "Popüler", 18, Ui.TEXT, true);
        section.setPadding(p + Ui.dp(a, 2), Ui.dp(a, 16), p, Ui.dp(a, 10));
        col.addView(section);

        // --- içerik alanı: liste + iskelet + boş durum üst üste
        FrameLayout body = new FrameLayout(a);
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        list = new ListView(a);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setPadding(p, 0, p, Ui.dp(a, 16));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup parent) {
                if (cv == null) {
                    LinearLayout wrap = new LinearLayout(a);
                    wrap.setPadding(0, 0, 0, Ui.dp(a, 14));
                    wrap.addView(Views.newCard(a, itemW), new LinearLayout.LayoutParams(-1, -2));
                    cv = wrap;
                }
                final Innertube.Item it = items.get(i);
                View card = ((ViewGroup) cv).getChildAt(0);
                Views.bindCard(a, card, it);
                Ui.reset(cv);
                if (i > lastAnimated) {
                    lastAnimated = i;
                    Ui.rise(cv, Math.min(i, 5) * 70L);
                }
                card.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { Views.open(a, it, false); }
                });
                return cv;
            }
        };
        list.setAdapter(adapter);
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));

        // aşağı çekerek yenile
        pullSpin = new Spin(a, 30);
        pullSpin.setAlpha(0f);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(Ui.dp(a, 30), Ui.dp(a, 30), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pp.topMargin = Ui.dp(a, 6);
        body.addView(pullSpin, pp);
        final int need = Ui.dp(a, 90);
        list.setOnTouchListener(new View.OnTouchListener() {
            float down; boolean pulling;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        down = e.getY();
                        pulling = atTop();
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        if (!pulling) { if (atTop()) { pulling = true; down = e.getY(); } break; }
                        float dy = e.getY() - down;
                        if (dy > 0) {
                            float f = Math.min(1f, dy / need);
                            pullSpin.setAlpha(f);
                            pullSpin.setTranslationY(Math.min(dy * 0.5f, need * 0.6f));
                            list.setTranslationY(Math.min(dy * 0.35f, need * 0.45f));
                        }
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        float dy = e.getY() - down;
                        boolean go = pulling && dy >= need && e.getAction() == MotionEvent.ACTION_UP;
                        pulling = false;
                        Ui.an(list, 200, 0, new android.view.animation.DecelerateInterpolator()).translationY(0f).start();
                        if (go) refresh();
                        else Ui.an(pullSpin, 200, 0, new android.view.animation.DecelerateInterpolator()).alpha(0f).translationY(0f).start();
                        break;
                    }
                }
                return false;
            }
        });

        skel = new LinearLayout(a);
        skel.setOrientation(LinearLayout.VERTICAL);
        skel.setPadding(p, 0, p, 0);
        for (int i = 0; i < 3; i++) {
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.bottomMargin = Ui.dp(a, 14);
            skel.addView(Views.skeleton(a, itemW), sp);
        }
        skel.setVisibility(View.GONE);
        body.addView(skel, new FrameLayout.LayoutParams(-1, -1));

        emptyBox = new LinearLayout(a);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyIc = new Ic(a, Ic.SEARCH, 56, Ui.DIM);
        emptyBox.addView(emptyIc, new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 56)));
        emptyTitle = Ui.text(a, "Video bulunamadı", 18, Ui.TEXT, true);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setPadding(0, Ui.dp(a, 16), 0, Ui.dp(a, 4));
        emptySub = Ui.text(a, "", 13, Ui.SUB, false);
        emptySub.setGravity(Gravity.CENTER);
        emptySub.setPadding(Ui.dp(a, 32), 0, Ui.dp(a, 32), Ui.dp(a, 16));
        emptyBox.addView(emptyTitle, new LinearLayout.LayoutParams(-2, -2));
        emptyBox.addView(emptySub, new LinearLayout.LayoutParams(-2, -2));
        TextView retry = Ui.redButton(a, "Tekrar dene");
        retry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (searched && input.getText().length() > 0) go(input.getText().toString()); else loadPopular();
            }
        });
        emptyBox.addView(retry, new LinearLayout.LayoutParams(-2, -2));
        emptyBox.setVisibility(View.GONE);
        body.addView(emptyBox, new FrameLayout.LayoutParams(-1, -1));

        loadPopular();
    }

    private static int blend(int a, int b, float t) {
        int ar = a >>> 24, rr = (a >> 16) & 255, gr = (a >> 8) & 255, br = a & 255;
        int ar2 = b >>> 24, rr2 = (b >> 16) & 255, gr2 = (b >> 8) & 255, br2 = b & 255;
        return ((int) (ar + (ar2 - ar) * t) << 24) | ((int) (rr + (rr2 - rr) * t) << 16)
                | ((int) (gr + (gr2 - gr) * t) << 8) | (int) (br + (br2 - br) * t);
    }

    @Override void resume() {
        if (!shown) {
            shown = true;
            for (int i = 0; i < chipRow.getChildCount(); i++) Ui.rise(chipRow.getChildAt(i), 120 + i * 45L);
        }
    }

    void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }

    @Override void pause() { hideKeyboard(); }

    private android.animation.ObjectAnimator skelPulse, bob;

    private void busy(boolean on) {
        if (on) {
            emptyBox.setVisibility(View.GONE);
            skel.setVisibility(View.VISIBLE);
            if (skelPulse == null) skelPulse = Ui.pulse(skel, 0.45f, 1f, 700);
            else skelPulse.start();
        } else {
            skel.setVisibility(View.GONE);
            if (skelPulse != null) skelPulse.cancel();
            skel.setAlpha(1f);
        }
    }

    private void setSection(final String s) {
        Ui.an(section, 140, 0, new DecelerateInterpolator()).alpha(0f).translationY(-Ui.dp(a, 6)).withEndAction(new Runnable() {
            @Override public void run() {
                section.setText(s);
                section.setTranslationY(Ui.dp(a, 8));
                Ui.an(section, 220, 0, new DecelerateInterpolator()).alpha(1f).translationY(0f).start();
            }
        }).start();
    }

    private Spin pullSpin;
    private long lastRefresh;

    private boolean atTop() {
        if (list.getFirstVisiblePosition() != 0) return false;
        View c = list.getChildAt(0);
        return c == null || c.getTop() >= list.getPaddingTop();
    }

    /** Ana sayfayı yeniler (aşağı çekince ya da Keşfet sekmesine tekrar dokununca). */
    void refresh() {
        if (System.currentTimeMillis() - lastRefresh < 1200) return;
        lastRefresh = System.currentTimeMillis();
        pullSpin.setAlpha(1f);
        pullSpin.setTranslationY(Ui.dp(a, 18));
        Ui.an(pullSpin, 200, 0, new android.view.animation.DecelerateInterpolator()).alpha(1f).translationY(Ui.dp(a, 18)).start();
        a.getWindow().getDecorView().postDelayed(new Runnable() {
            @Override public void run() {
                Ui.an(pullSpin, 250, 0, new android.view.animation.DecelerateInterpolator()).alpha(0f).translationY(0f).start();
            }
        }, 1400);
        Api.forgetAll();
        if (searched && input.getText().length() > 0) go(input.getText().toString()); else loadPopular();
    }

    private void setItems(List<Innertube.Item> r) {
        items.clear();
        for (Innertube.Item it : r) if (!it.isShort()) items.add(it);
        lastAnimated = -1;
        adapter.notifyDataSetChanged();
        list.setSelection(0);
    }

    private void showEmpty(String title, String sub) {
        emptyTitle.setText(title);
        emptySub.setText(sub);
        emptyBox.setVisibility(View.VISIBLE);
        Ui.popIn(emptyBox, 0);
        if (bob != null) bob.cancel();
        emptyIc.setTranslationY(0);
        bob = android.animation.ObjectAnimator.ofFloat(emptyIc, "translationY", 0f, -Ui.dp(a, 8));
        bob.setDuration(900);
        bob.setRepeatCount(ValueAnimator.INFINITE);
        bob.setRepeatMode(ValueAnimator.REVERSE);
        bob.start();
    }

    private void loadPopular() {
        if (Auth.logged(a)) { loadFeed("FEwhat_to_watch", "Önerilenler", true); return; }
        loadMix();
    }

    private static final String[] MIX = {
            "müzik", "yeni şarkılar", "oyun", "oyun oynanış", "teknoloji", "teknoloji inceleme", "komik videolar", "fragman",
            "spor özet", "futbol", "belgesel", "bilim", "yemek tarifi", "seyahat", "araba", "motor", "hayvanlar", "DIY yapımı",
            "podcast", "haberler", "kısa film", "mizah", "geometry dash", "minecraft", "elektronik", "yazılım", "tarih", "sanat"};

    /** Her yenilemede farklı konulardan karışık video: sabit "popüler" listesi yerine. */
    private void loadMix() {
        setSection("Keşfet");
        emptyBox.setVisibility(View.GONE);
        searched = false;
        if (items.isEmpty()) {
            List<Innertube.Item> cached = Api.cachedPopular(a);
            if (cached != null) setItems(cached); else busy(true);
        } else busy(true);
        final java.util.List<String> pool = new ArrayList<String>(java.util.Arrays.asList(MIX));
        java.util.Collections.shuffle(pool);
        final java.util.List<String> pick = pool.subList(0, 4);
        new Thread(new Runnable() {
            @Override public void run() {
                final java.util.concurrent.LinkedBlockingQueue<List<Innertube.Item>> q = new java.util.concurrent.LinkedBlockingQueue<List<Innertube.Item>>();
                for (final String topic : pick) {
                    new Thread(new Runnable() {
                        @Override public void run() {
                            List<Innertube.Item> r = null;
                            try { r = Api.search(a, topic); } catch (Exception ignored) {}
                            try { q.put(r == null ? new ArrayList<Innertube.Item>() : r); } catch (InterruptedException ignored) {}
                        }
                    }).start();
                }
                final List<Innertube.Item> all = new ArrayList<Innertube.Item>();
                java.util.Set<String> seen = new java.util.HashSet<String>();
                long end = System.currentTimeMillis() + 9000;
                for (int i = 0; i < pick.size(); i++) {
                    long left = end - System.currentTimeMillis();
                    List<Innertube.Item> r = null;
                    try { r = left <= 0 ? null : q.poll(left, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
                    if (r == null) break;
                    int n = 0;
                    for (Innertube.Item it : r) {
                        if (it.isShort() || !seen.add(it.id)) continue;
                        all.add(it);
                        if (++n >= 8) break;
                    }
                }
                java.util.Collections.shuffle(all);
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (searched) return;
                        busy(false);
                        if (!all.isEmpty()) setItems(all);
                        else loadTrending(); // arama hiç sonuç vermediyse eski popüler listeye dön
                    }
                });
            }
        }).start();
    }

    private void loadFeed(final String id, final String name, final boolean isDefault) {
        searched = !isDefault;
        emptyBox.setVisibility(View.GONE);
        busy(true);
        setItems(new ArrayList<Innertube.Item>());
        setSection(name);
        new Thread(new Runnable() {
            @Override public void run() {
                List<Innertube.Item> r = null;
                String err = null;
                try { r = Api.feed(a, id); } catch (Exception e) { err = e.getMessage(); }
                final List<Innertube.Item> res = r;
                final String fe = err;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        busy(false);
                        if (res != null) { setItems(res); return; }
                        if (isDefault) {
                            Ui.snack(a, "Önerilenler yüklenemedi, popüler gösteriliyor");
                            setSection("Popüler");
                            loadTrending();
                        } else {
                            showEmpty("Abonelik akışı yüklenemedi", fe == null ? "" : fe);
                        }
                    }
                });
            }
        }).start();
    }

    private void loadTrending() {
        List<Innertube.Item> cached = Api.cachedPopular(a);
        if (cached != null) setItems(cached); else busy(true);
        emptyBox.setVisibility(View.GONE);
        new Thread(new Runnable() {
            @Override public void run() {
                List<Innertube.Item> r = null;
                try { r = Api.popular(a); } catch (Exception ignored) {}
                final List<Innertube.Item> res = r;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (searched) return;
                        busy(false);
                        if (res != null) setItems(res);
                        else if (items.isEmpty()) showEmpty("Popüler videolar yüklenemedi", "Bağlantını kontrol et ya da yukarıdan arama yap.");
                    }
                });
            }
        }).start();
    }

    void go(final String q) {
        hideKeyboard();
        if (q.trim().isEmpty()) return;
        String id = Innertube.parseId(q);
        if (id != null && q.contains("/")) {
            Innertube.Item it = new Innertube.Item(id, "", "", "");
            Views.open(a, it, false);
            return;
        }
        searched = true;
        busy(true);
        setItems(new ArrayList<Innertube.Item>());
        setSection("“" + q.trim() + "”");
        new Thread(new Runnable() {
            @Override public void run() {
                List<Innertube.Item> r = null;
                String err = null;
                try { r = Api.search(a, q); } catch (Exception e) { err = e.getMessage(); }
                final List<Innertube.Item> res = r;
                final String fe = err;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        busy(false);
                        if (res != null && !res.isEmpty()) { setItems(res); return; }
                        showEmpty(fe != null ? "Arama başarısız" : "Video bulunamadı", fe != null ? fe : "Başka bir şey ara.");
                    }
                });
            }
        }).start();
    }

    boolean back() {
        if (searched) {
            searched = false;
            input.setText("");
            setSection("Popüler");
            emptyBox.setVisibility(View.GONE);
            loadPopular();
            return true;
        }
        return false;
    }

    @Override void destroy() {
        if (skelPulse != null) skelPulse.cancel();
        if (bob != null) bob.cancel();
    }
}
