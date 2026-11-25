package com.hafif.tube;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Video sayfası: anında başlayan oynatıcı, indirme/paylaş/kalite, ilgili videolar. */
public class VideoActivity extends Activity {
    private static final class Plan {
        Innertube.Fmt v, a;
        int retries;
        String label() {
            return v.h == 0 ? "Otomatik (HLS)" : v.h + "p" + (a == null ? "" : " · ayrı akış");
        }
    }

    private String id, extraTitle, extraAuthor;
    private Api.Video video;
    private VPlayer vp;
    private ScrollView scroll;
    private LinearLayout page, relatedBox, statusCard, errBox;
    private TextView title, meta, chanName, avatar, info, statusTx, qualityBtn, errTx, dlBtn;
    private Bar statusBar;
    private boolean fullscreen, pendingDl, statusShown;
    private boolean triedDirect, triedServers;
    private final java.util.Set<String> bad = new java.util.HashSet<>();
    private Plan plan;
    private TextView dbg;
    private boolean diagRan;
    private final android.os.Handler th = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (isFinishing() || dbg == null) return;
            dbg.setText(Dbg.dump(16));
            if (vp != null && vp.started()) StreamProxy.fails = 0;
            th.postDelayed(this, 1500);
        }
    };
    private LinearLayout descCard, cmBox, loginCard;
    private TextView descTx, cmMore, cmHead, cmState;
    private String cmToken = "";
    private boolean cmLoading, cmStarted;
    private android.animation.ObjectAnimator metaPulse;

    private int frameH() { return getResources().getDisplayMetrics().widthPixels * 9 / 16; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Themes.init(this);
        Dbg.init(this);
        StreamProxy.on403 = new Runnable() {
            @Override public void run() {
                runOnUiThread(new Runnable() { @Override public void run() { if (!isFinishing()) { Dbg.log("oynatma 403 ile takıldı"); onStreamFail(); } } });
            }
        };
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        Auth.init(this);
        PoToken.warm(this);
        StreamProxy.enabled = true;
        Dbg.log("hesap: " + (Auth.logged(this) ? "giriş yapılmış" : "giriş YOK"));
        id = getIntent().getStringExtra("id");
        if (id == null && getIntent().getData() != null) id = Innertube.parseId(getIntent().getData().toString());
        if (id == null) { finish(); return; }
        extraTitle = getIntent().getStringExtra("title");
        extraAuthor = getIntent().getStringExtra("author");
        pendingDl = getIntent().getBooleanExtra("dl", false);
        int p = Ui.dp(this, 16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        vp = new VPlayer(this);
        vp.topBar("", new Runnable() { @Override public void run() { onBackPressed(); } });
        vp.setHandlers(new Runnable() { @Override public void run() { setFull(!fullscreen); } },
                new Runnable() { @Override public void run() { onStreamFail(); } });
        root.addView(vp, new LinearLayout.LayoutParams(-1, frameH()));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(p, Ui.dp(this, 16), p, Ui.dp(this, 28));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        title = Ui.text(this, extraTitle == null || extraTitle.isEmpty() ? "Yükleniyor…" : extraTitle, 19, Ui.TEXT, true);
        title.setLineSpacing(0, 1.12f);
        page.addView(title);
        meta = Ui.text(this, "", 12.5f, Ui.SUB, false);
        meta.setPadding(0, Ui.dp(this, 8), 0, 0);
        page.addView(meta);

        // kanal satırı
        LinearLayout ch = new LinearLayout(this);
        ch.setGravity(Gravity.CENTER_VERTICAL);
        ch.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        avatar = Ui.text(this, "", 16, 0xffffffff, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.redCircle());
        ch.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
        chanName = Ui.text(this, extraAuthor == null ? "" : extraAuthor, 14, Ui.TEXT, true);
        chanName.setSingleLine(true);
        chanName.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = Ui.dp(this, 12);
        ch.addView(chanName, cp);
        page.addView(ch);
        setAvatar(extraAuthor);
        Ui.press(ch, 0.98f);
        ch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openChannel(); }
        });

        // eylemler
        LinearLayout act = new LinearLayout(this);
        dlBtn = Ui.redButton(this, "İndir");
        dlBtn.setCompoundDrawablePadding(Ui.dp(this, 8));
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(0, -2, 1.5f);
        dp1.rightMargin = Ui.dp(this, 8);
        act.addView(dlBtn, dp1);
        qualityBtn = Ui.ghostButton(this, "Kalite");
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(0, -2, 1f);
        qp.rightMargin = Ui.dp(this, 8);
        act.addView(qualityBtn, qp);
        TextView sh = Ui.ghostButton(this, "Paylaş");
        act.addView(sh, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(act);

        dlBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { download(); }
        });
        qualityBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { qualitySheet(); }
        });
        sh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/" + id);
                startActivity(Intent.createChooser(i, "Paylaş"));
            }
        });

        // indirme durum kartı
        statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackground(Ui.box(this, Ui.PANEL, Ui.STROKE2, 18));
        statusCard.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 16));
        statusCard.setVisibility(View.GONE);
        statusTx = Ui.text(this, "", 13, Ui.TEXT, true);
        statusCard.addView(statusTx);
        statusBar = new Bar(this);
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 6));
        sbp.topMargin = Ui.dp(this, 10);
        statusCard.addView(statusBar, sbp);
        TextView open = Ui.text(this, "İndirilenler'i aç  ›", 12, Ui.RED_HI, true);
        open.setPadding(0, Ui.dp(this, 10), 0, 0);
        statusCard.addView(open);
        statusCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(VideoActivity.this, MainActivity.class).putExtra("tab", 2).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
            }
        });
        Ui.press(statusCard, 0.98f);
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(-1, -2);
        scp.topMargin = Ui.dp(this, 14);
        page.addView(statusCard, scp);

        info = Ui.text(this, "", 12, Ui.DIM, false);
        info.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), 0, 0);
        page.addView(info);
        loginCard = new LinearLayout(this);
        loginCard.setOrientation(LinearLayout.VERTICAL);
        loginCard.setBackground(Ui.box(this, Ui.PANEL, Ui.RED, 18));
        loginCard.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        loginCard.setVisibility(View.GONE);
        loginCard.addView(Ui.text(this, "YouTube bu bağlantıdan giriş istiyor", 15, Ui.TEXT, true));
        TextView lt = Ui.text(this, "Videoların açılması için hesabınla giriş yapman gerekiyor. Giriş yapınca bu videolar sorunsuz açılır, oturum hatırlanır.", 12.5f, Ui.SUB, false);
        lt.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 12));
        loginCard.addView(lt);
        TextView lb = Ui.redButton(this, "Giriş yap");
        lb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivityForResult(new Intent(VideoActivity.this, LoginActivity.class), 77); }
        });
        loginCard.addView(lb);
        LinearLayout.LayoutParams lcp = new LinearLayout.LayoutParams(-1, -2);
        lcp.topMargin = Ui.dp(this, 14);
        page.addView(loginCard, lcp);
        dbg = Ui.text(this, "", 10.5f, Ui.DIM, false);
        dbg.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), 0, 0);
        dbg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("log", Dbg.dump(150)));
                Ui.snack(VideoActivity.this, "Kayıt kopyalandı");
            }
        });
        page.addView(dbg);
        tick.run();

        errBox = new LinearLayout(this);
        errBox.setOrientation(LinearLayout.VERTICAL);
        errBox.setBackground(Ui.box(this, Ui.PANEL, Ui.RED, 18));
        errBox.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        errBox.setVisibility(View.GONE);
        errBox.addView(Ui.text(this, "Video yüklenemedi", 16, Ui.TEXT, true));
        errTx = Ui.text(this, "", 12.5f, Ui.SUB, false);
        errTx.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 12));
        errBox.addView(errTx);
        TextView retry = Ui.redButton(this, "Tekrar dene");
        retry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { errBox.setVisibility(View.GONE); Api.forget(id); vp.busy(true, "Video bilgisi alınıyor…"); fetch(); }
        });
        errBox.addView(retry);
        LinearLayout.LayoutParams ebp = new LinearLayout.LayoutParams(-1, -2);
        ebp.topMargin = Ui.dp(this, 14);
        page.addView(errBox, ebp);

        // açıklama kartı
        descCard = new LinearLayout(this);
        descCard.setOrientation(LinearLayout.VERTICAL);
        descCard.setBackground(Ui.box(this, Ui.PANEL, Ui.STROKE, 16));
        descCard.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        descCard.setVisibility(View.GONE);
        TextView dh = Ui.text(this, "Açıklama", 14, Ui.TEXT, true);
        descCard.addView(dh);
        descTx = Ui.text(this, "", 13, Ui.SUB, false);
        descTx.setLineSpacing(0, 1.15f);
        descTx.setMaxLines(3);
        descTx.setEllipsize(TextUtils.TruncateAt.END);
        descTx.setPadding(0, Ui.dp(this, 6), 0, 0);
        descCard.addView(descTx);
        descCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean open = descTx.getMaxLines() > 3;
                descTx.setMaxLines(open ? 3 : 1000);
                descTx.setEllipsize(open ? TextUtils.TruncateAt.END : null);
            }
        });
        Ui.press(descCard, 0.985f);
        LinearLayout.LayoutParams dcp = new LinearLayout.LayoutParams(-1, -2);
        dcp.topMargin = Ui.dp(this, 14);
        page.addView(descCard, dcp);

        // yorumlar
        cmHead = Ui.text(this, "Yorumlar", 17, Ui.TEXT, true);
        cmHead.setPadding(Ui.dp(this, 2), Ui.dp(this, 22), 0, Ui.dp(this, 10));
        cmHead.setVisibility(View.GONE);
        page.addView(cmHead);
        cmBox = new LinearLayout(this);
        cmBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(cmBox);
        cmState = Ui.text(this, "", 13, Ui.DIM, false);
        cmState.setVisibility(View.GONE);
        page.addView(cmState);
        cmMore = Ui.ghostButton(this, "Daha fazla yorum");
        cmMore.setVisibility(View.GONE);
        cmMore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadComments(); }
        });
        LinearLayout.LayoutParams cmp = new LinearLayout.LayoutParams(-1, -2);
        cmp.topMargin = Ui.dp(this, 6);
        page.addView(cmMore, cmp);

        TextView rh = Ui.text(this, "İlgili videolar", 17, Ui.TEXT, true);
        rh.setPadding(Ui.dp(this, 2), Ui.dp(this, 22), 0, Ui.dp(this, 10));
        rh.setVisibility(View.GONE);
        rh.setTag("rh");
        page.addView(rh);
        relatedBox = new LinearLayout(this);
        relatedBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(relatedBox);
        setContentView(root);

        // oynatıcı poster + büyük oynat düğmesi (bilgi gelene dek)
        vp.load(Views.thumb(id).replace("mqdefault", "hqdefault"), null, null, 0, false);
        vp.busy(true, "Video bilgisi alınıyor…");

        // giriş animasyonları
        View[] ins = {title, meta, ch, act};
        for (int i = 0; i < ins.length; i++) Ui.rise(ins[i], 120 + i * 80L);
        metaPulse = Ui.pulse(meta, 0.3f, 1f, 650);
        meta.setText("▮▮▮▮▮▮▮▮▮▮");
        meta.setTextColor(Ui.PANEL2);
        fetch();
    }

    private void setAvatar(String author) {
        avatar.setText(author == null || author.isEmpty() ? "" : author.substring(0, 1).toUpperCase());
    }

    private void fetch() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Api.Video v = Api.video(VideoActivity.this, id);
                    runOnUiThread(new Runnable() { @Override public void run() { show(v); } });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (metaPulse != null) metaPulse.cancel();
                            vp.busy(false, null);
                            meta.setAlpha(1f);
                            meta.setText("");
                            errTx.setText(String.valueOf(e.getMessage()));
                            errBox.setVisibility(View.VISIBLE);
                            Ui.popIn(errBox, 0);
                        }
                    });
                }
            }
        }).start();
    }

    private void show(Api.Video v) {
        video = v;
        bad.clear();
        triedDirect = "youtube".equals(v.source);
        triedServers = !triedDirect;
        if (metaPulse != null) metaPulse.cancel();
        meta.setAlpha(1f);
        meta.setTextColor(Ui.SUB);
        title.setText(v.title);
        vp.topBar(v.title, new Runnable() { @Override public void run() { onBackPressed(); } });
        String vs = v.views > 0 ? Api.views(v.views) : "";
        if (v.likes > 0) vs += (vs.isEmpty() ? "" : " · ") + v.likes + " beğeni";
        meta.setText(vs);
        Ui.fadeIn(meta, 0);
        chanName.setText(v.author);
        setAvatar(v.author);
        if (v.description != null && !v.description.trim().isEmpty()) {
            descTx.setText(v.description.trim());
            descCard.setVisibility(View.VISIBLE);
            Ui.rise(descCard, 260);
        }
        if (!cmStarted) { cmStarted = true; cmHead.setVisibility(View.VISIBLE); loadComments(); }
        if (v.related.isEmpty()) loadRelated();
        List<Innertube.Item> rel = v.related;
        View rh = page.findViewWithTag("rh");
        if (rh != null && !rel.isEmpty()) { rh.setVisibility(View.VISIBLE); Ui.rise(rh, 300); }
        for (int i = 0; i < rel.size(); i++) {
            View c = Views.compact(this, rel.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.dp(this, 10);
            relatedBox.addView(c, lp);
            Ui.rise(c, 350 + Math.min(i, 8) * 60L);
        }

        plan = defaultPlan();
        refreshQualityLabel();
        if (plan != null) {
            info.setText("Akış: " + plan.label() + " · kaynak: " + v.source);
            startPlan(plan, 0);
        } else {
            vp.busy(false, null);
            info.setText("Oynatılabilir akış bulunamadı; indirmeyi dene · kaynak: " + v.source);
        }
        if (pendingDl) { pendingDl = false; download(); }
    }

    // ------------------------------------------------------------------ kanal, yorumlar

    private void openChannel() {
        if (video == null || video.channelId == null || video.channelId.isEmpty()) {
            Ui.snack(this, "Kanal bilgisi henüz yok");
            return;
        }
        startActivity(new Intent(this, ChannelActivity.class)
                .putExtra("cid", video.channelId).putExtra("name", video.author));
    }

    private void loadRelated() {
        new Thread(new Runnable() {
            @Override public void run() {
                List<Innertube.Item> r = null;
                try { r = Innertube.related(id); } catch (Exception ignored) {}
                final List<Innertube.Item> res = r;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || res == null || res.isEmpty() || relatedBox.getChildCount() > 0) return;
                        View rh = page.findViewWithTag("rh");
                        if (rh != null) { rh.setVisibility(View.VISIBLE); Ui.rise(rh, 0); }
                        int n = 0;
                        for (Innertube.Item it : res) {
                            if (it.isShort() || it.id.equals(id)) continue;
                            View c = Views.compact(VideoActivity.this, it);
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                            lp.bottomMargin = Ui.dp(VideoActivity.this, 10);
                            relatedBox.addView(c, lp);
                            if (++n >= 20) break;
                        }
                    }
                });
            }
        }).start();
    }

    private void loadComments() {
        if (cmLoading) return;
        cmLoading = true;
        cmMore.setVisibility(View.GONE);
        cmState.setText("Yorumlar yükleniyor…");
        cmState.setVisibility(View.VISIBLE);
        final String tok = cmToken;
        new Thread(new Runnable() {
            @Override public void run() {
                Innertube.Comments c = null;
                String err = null;
                try { c = Api.comments(VideoActivity.this, id, tok); } catch (Exception e) { err = e.getMessage(); }
                final Innertube.Comments res = c;
                final String fe = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        cmLoading = false;
                        if (isFinishing()) return;
                        if (res == null) {
                            cmState.setText(cmBox.getChildCount() == 0 ? "Yorumlar yüklenemedi" + (fe == null ? "" : " (" + fe + ")") : "");
                            Dbg.log("yorum hatası: " + fe);
                            cmState.setVisibility(cmBox.getChildCount() == 0 ? View.VISIBLE : View.GONE);
                            if (cmBox.getChildCount() == 0) {
                                cmMore.setText("Tekrar dene");
                                cmMore.setVisibility(View.VISIBLE);
                            }
                            return;
                        }
                        cmState.setVisibility(View.GONE);
                        cmMore.setText("Daha fazla yorum");
                        for (Innertube.Cm m : res.list) {
                            View row = commentRow(m);
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                            lp.bottomMargin = Ui.dp(VideoActivity.this, 12);
                            cmBox.addView(row, lp);
                        }
                        cmToken = res.next == null ? "" : res.next;
                        cmMore.setVisibility(cmToken.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                });
            }
        }).start();
    }

    private View commentRow(Innertube.Cm m) {
        LinearLayout row = new LinearLayout(this);
        row.setBackground(Ui.box(this, Ui.PANEL, Ui.STROKE, 14));
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        TextView av = Ui.text(this, m.author.isEmpty() ? "" : m.author.replace("@", "").substring(0, Math.min(1, m.author.replace("@", "").length())).toUpperCase(), 13, 0xffffffff, true);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Ui.redCircle());
        row.addView(av, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        String head = m.author + (m.time.isEmpty() ? "" : "  ·  " + m.time);
        TextView h = Ui.text(this, head, 12, Ui.SUB, true);
        h.setSingleLine(true);
        h.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(h);
        TextView t = Ui.text(this, m.text, 13.5f, Ui.TEXT, false);
        t.setLineSpacing(0, 1.12f);
        t.setPadding(0, Ui.dp(this, 3), 0, 0);
        col.addView(t);
        if (!m.likes.isEmpty()) {
            TextView lk = Ui.text(this, "♥ " + m.likes, 12, Ui.RED_HI, false);
            lk.setPadding(0, Ui.dp(this, 4), 0, 0);
            col.addView(lk);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = Ui.dp(this, 10);
        row.addView(col, cp);
        return row;
    }

    // ------------------------------------------------------------------ akış seçimi

    private List<Innertube.Fmt> muxed() {
        List<Innertube.Fmt> r = new ArrayList<>();
        if (video != null) for (Innertube.Fmt f : video.formats) if (f.hasVideo && f.hasAudio) r.add(f);
        sortDesc(r);
        return r;
    }

    /** Ayrı görüntü akışları (ses yok): önce H.264, yoksa VP9. AV1 atlanır. */
    private List<Innertube.Fmt> split() {
        List<Innertube.Fmt> avc = new ArrayList<>(), vp9 = new ArrayList<>();
        if (video != null) for (Innertube.Fmt f : video.formats) {
            if (!f.hasVideo || f.hasAudio) continue;
            if (f.isAvc()) avc.add(f); else if (f.isVp9()) vp9.add(f);
        }
        sortDesc(avc);
        sortDesc(vp9);
        return avc.isEmpty() ? vp9 : avc;
    }

    private static void sortDesc(List<Innertube.Fmt> l) {
        Collections.sort(l, new Comparator<Innertube.Fmt>() {
            @Override public int compare(Innertube.Fmt a, Innertube.Fmt b) { return b.h - a.h; }
        });
    }

    private Innertube.Fmt pickAudio() {
        Innertube.Fmt best = null;
        for (Innertube.Fmt f : video.formats) {
            if (f.hasVideo || !f.isAac()) continue;
            int d = Math.abs(f.bitrate - 128000);
            if (best == null || d < Math.abs(best.bitrate - 128000)) best = f;
        }
        if (best != null) return best;
        for (Innertube.Fmt f : video.formats) if (!f.hasVideo && (best == null || f.bitrate < best.bitrate)) best = f;
        return best;
    }

    @Override protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == 77 && res == RESULT_OK) { Api.forgetAll(); recreate(); }
    }

    private static boolean isHlsPlan(Plan p) { return p.v != null && p.v.h == 0; }

    private Plan hlsPlan() {
        if (video == null || video.hls == null || video.hls.isEmpty()) return null;
        Plan p = new Plan();
        p.v = new Innertube.Fmt(video.hls, "application/x-mpegURL", 0, 0, 0, 0, 0, true, true);
        return p;
    }

    private Plan defaultPlan() {
        if (video != null && video.live) { Plan hp = hlsPlan(); if (hp != null) return hp; }
        if (video != null && video.formats.isEmpty()) return hlsPlan();
        int pref = Api.prefs(this).getInt("quality", 720);
        Innertube.Fmt m = null;
        for (Innertube.Fmt f : muxed()) { if (m == null || f.h <= pref) { m = f; if (f.h <= pref) break; } }
        Innertube.Fmt s = null;
        List<Innertube.Fmt> sp = split();
        for (Innertube.Fmt f : sp) { s = f; if (f.h <= pref) break; }
        Plan pl = new Plan();
        Innertube.Fmt au0 = (s != null) ? pickAudio() : null;
        // ayrı akış daha yüksek çözünürlük veriyorsa onu seç (muxed çoğu zaman sadece 360p)
        boolean splitBetter = s != null && au0 != null && (m == null || s.h > m.h);
        if (m != null && !splitBetter) { pl.v = m; return pl; }
        if (s != null) {
            Innertube.Fmt a = pickAudio();
            pl.v = s;
            pl.a = a;
            return pl;
        }
        return null;
    }

    private void startPlan(Plan pl, int startMs) {
        plan = pl;
        vp.setLive(video != null && video.live);
        wd.removeCallbacksAndMessages(null);
        Uri a = pl.a == null ? null : Uri.parse(pl.a.url);
        vp.load(Views.thumb(id).replace("mqdefault", "hqdefault"), Uri.parse(pl.v.url), a, startMs, true);
        info.setText("Akış: " + pl.label() + " · kaynak: " + (video == null ? "" : video.source)
                + " · " + pl.v.mimeBase() + (pl.a == null ? "" : " + " + pl.a.mimeBase()));
        refreshQualityLabel();
        final Plan mine = pl;
        if (pl.a != null) {
            // ayrı akış 8 sn içinde başlamazsa tek akışa (muxed) düş
            wd.removeCallbacksAndMessages(null);
            wd.postDelayed(new Runnable() {
                @Override public void run() {
                    if (plan == mine && !vp.started() && !isFinishing()) fallbackMuxed();
                }
            }, 8000);
        }
    }

    private final android.os.Handler wd = new android.os.Handler(android.os.Looper.getMainLooper());

    private boolean fallbackMuxed() {
        List<Innertube.Fmt> m = muxed();
        if (m.isEmpty() || (plan != null && plan.a == null)) return false;
        Plan p = new Plan();
        p.v = m.get(0);
        Ui.snack(this, "Ayrı akış açılmadı, tek akışa geçildi");
        startPlan(p, vp.pos());
        return true;
    }

    private void refreshQualityLabel() {
        qualityBtn.setText(plan == null ? "Kalite" : plan.v.h == 0 ? "Otomatik" : plan.v.h + "p");
    }

    /** Akış açılmadı/takıldı: önce aynı kaynaktaki diğer akışlar, sonra diğer kaynak (YouTube <-> sunucular). */
    private int clientHops;

    private void onStreamFail() {
        if (isFinishing()) return;
        String err = vp.lastError();
        Dbg.log("oynatma hatası: " + err);
        if (!diagRan) {
            diagRan = true;
            Dbg.log("tanılama başlıyor (14 sn)…");
            new Thread(new Runnable() {
                @Override public void run() {
                    for (String s : Innertube.diagnose(id)) Dbg.log(s);
                }
            }).start();
        }
        if (!Auth.logged(this) && loginCard.getVisibility() != View.VISIBLE) {
            loginCard.setVisibility(View.VISIBLE);
            Ui.popIn(loginCard, 0);
        }
        // Bu istemcinin adresleri 403 veriyorsa aynı adreslerle uğraşma: istemciyi ele, başka istemciyle yeniden al
        if (video != null && video.client > 0 && "youtube".equals(video.source) && clientHops < 6
                && System.currentTimeMillis() - StreamProxy.last403 < 15000) {
            clientHops++;
            Innertube.markBad(id, video.client);
            // yalnızca gerçek 403'te (canlı yayın kesilmesinde değil) doğrudan yolu 5 dk boyunca geri plana al
            if (!video.live && System.currentTimeMillis() - StreamProxy.real403 < 15000)
                Innertube.directBadUntil = System.currentTimeMillis() + 5 * 60 * 1000;
            Dbg.log("istemci " + video.client + " 403 verdi, başka istemci deneniyor (" + clientHops + ")");
            vp.busy(true, "Başka akış kaynağı deneniyor…");
            Api.forget(id);
            bad.clear();
            if (plan != null) plan.retries = 0;
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        // Doğrudan akışlar 403 veriyor: yedek sunucular (kendi jetonunu üretir) çoğu zaman çalışır, önce onları dene
                        Api.Video v0 = null;
                        try { v0 = Api.video(VideoActivity.this, id, Api.ONLY_SERVERS); } catch (Exception ex) { Dbg.log("yedek sunucu: " + ex.getMessage()); }
                        if (v0 == null) v0 = Api.video(VideoActivity.this, id, Api.ONLY_DIRECT);
                        final Api.Video v = v0;
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (isFinishing()) return;
                                video = v;
                                Plan p = defaultPlan();
                                if (p == null) { giveUp("403"); return; }
                                StreamProxy.last403 = 0;
                                startPlan(p, 0);
                            }
                        });
                    } catch (final Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() { if (!isFinishing()) giveUp("403 / " + e.getMessage()); }
                        });
                    }
                }
            }).start();
            return;
        }
        StreamProxy.enabled = true; // doğrudan bağlantı YouTube'da takılıyor; vekil hiçbir zaman kapatılmaz
        if (plan != null && plan.retries < 1 && !isHlsPlan(plan)) {
            plan.retries++; // geçici takılma olabilir: aynı akışı bir kez daha dene
            Ui.snack(this, "Akış yeniden deneniyor…");
            startPlan(plan, 0);
            return;
        }
        if (plan != null) bad.add(plan.v.url);
        // tek akışlı HLS çoğu zaman ayrı akışlardan daha sorunsuz açılır: önce onu dene
        Plan hp = hlsPlan();
        if (hp != null && !bad.contains(video.hls) && (plan == null || plan.v.h != 0)) {
            Ui.snack(this, "Tek akışlı yayın deneniyor…");
            startPlan(hp, 0);
            return;
        }
        Plan next = nextPlan();
        if (next != null) {
            Ui.snack(this, "Akış açılmadı, başka akış deneniyor…");
            startPlan(next, 0);
            return;
        }
        if (!triedDirect || !triedServers) { refetchOther(err); return; }
        giveUp(err);
    }

    private Plan nextPlan() {
        Plan p = nextPlan0();
        if (p == null && video != null && video.hls != null && !video.hls.isEmpty() && !bad.contains(video.hls)) return hlsPlan();
        return p;
    }

    private Plan nextPlan0() {
        if (video == null) return null;
        for (Innertube.Fmt f : muxed()) {
            if (bad.contains(f.url)) continue;
            Plan p = new Plan();
            p.v = f;
            return p;
        }
        Innertube.Fmt au = pickAudio();
        if (au == null) return null;
        Innertube.Fmt any = null, ok = null;
        for (Innertube.Fmt f : split()) {
            if (bad.contains(f.url)) continue;
            if (any == null) any = f;
            if (f.h <= 720) { ok = f; break; }
        }
        Innertube.Fmt pick = ok != null ? ok : any;
        if (pick == null) return null;
        Plan p = new Plan();
        p.v = pick;
        p.a = au;
        return p;
    }

    private void refetchOther(final String err) {
        final boolean direct = !triedDirect;
        if (direct) triedDirect = true; else triedServers = true;
        vp.busy(true, direct ? "YouTube'dan doğrudan deneniyor…" : "Yedek sunucular deneniyor…");
        Api.forget(id);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Api.Video v = Api.video(VideoActivity.this, id, direct ? Api.ONLY_DIRECT : Api.ONLY_SERVERS);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing()) return;
                            video = v;
                            Plan p = defaultPlan();
                            if (p == null) { onStreamFailNoMore(err); return; }
                            startPlan(p, 0);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing()) return;
                            if (!triedDirect || !triedServers) refetchOther(err + " | " + e.getMessage());
                            else giveUp(err + " | " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void onStreamFailNoMore(String err) {
        if (!triedDirect || !triedServers) refetchOther(err); else giveUp(err);
    }

    private void giveUp(String err) {
        vp.busy(false, null);
        errTx.setText("Hiçbir akış oynatılamadı.\n" + (err == null ? "" : err));
        errBox.setVisibility(View.VISIBLE);
        Ui.popIn(errBox, 0);
        info.setText("Oynatılamadı");
    }

    private void qualitySheet() {
        if (video == null) { Ui.snack(this, "Video bilgisi yükleniyor…"); return; }
        final Sheet s = new Sheet(this, "Oynatma kalitesi");
        s.item(Ic.QUALITY, "Otomatik", "Ayarlardaki varsayılan kalite", new Runnable() {
            @Override public void run() {
                Plan p = defaultPlan();
                if (p != null) startPlan(p, vp.pos());
            }
        });
        List<Innertube.Fmt> m = muxed();
        if (!m.isEmpty()) s.header("TEK AKIŞ · SES+VİDEO");
        for (final Innertube.Fmt f : m) {
            s.item(Ic.VIDEO, f.h + "p · " + f.ext().toUpperCase(), "video+ses birlikte", new Runnable() {
                @Override public void run() { Plan p = new Plan(); p.v = f; startPlan(p, vp.pos()); }
            });
        }
        final Innertube.Fmt au = pickAudio();
        List<Innertube.Fmt> sp = split();
        if (!sp.isEmpty() && au != null) s.header("AYRI AKIŞ · ANINDA BİRLEŞTİRİLİR");
        if (au != null) for (final Innertube.Fmt f : sp) {
            if (f.h > 1440) continue;
            s.item(Ic.VIDEO, Choices.res(f), (f.isAvc() ? "H.264" : "VP9") + " + ses", new Runnable() {
                @Override public void run() { Plan p = new Plan(); p.v = f; p.a = au; startPlan(p, vp.pos()); }
            });
        }
        s.show();
    }

    private void download() {
        if (video == null) {
            pendingDl = true;
            Ui.snack(this, "Video bilgisi yükleniyor, hemen açılacak…");
            return;
        }
        if (video.live) { Ui.snack(this, "Canlı yayın indirilemez"); return; }
        Choices.show(this, video, new Dl.Status() {
            @Override public void on(final String s) {
                runOnUiThread(new Runnable() { @Override public void run() { setStatus(s); } });
            }
        });
    }

    private void setStatus(String s) {
        if (!statusShown) {
            statusShown = true;
            statusCard.setVisibility(View.VISIBLE);
            Ui.rise(statusCard, 0);
            scroll.post(new Runnable() { @Override public void run() { scroll.smoothScrollTo(0, 0); } });
        }
        boolean done = s.startsWith("✓"), err = s.startsWith("Hata");
        statusTx.setText(s.replace("\n", " · "));
        statusTx.setTextColor(err ? Ui.RED_HI : done ? Ui.GREEN : Ui.TEXT);
        statusBar.setBad(err);
        int pc = Ui.pct(s);
        statusBar.set(done ? 1f : err ? 0f : pc < 0 ? -1f : pc / 100f, true);
    }

    // ------------------------------------------------------------------ tam ekran

    private void setFull(boolean f) {
        fullscreen = f;
        vp.setFullIcon(f);
        boolean land = Api.prefs(this).getBoolean("landscape", false);
        View d = getWindow().getDecorView();
        if (f) {
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            scroll.setVisibility(View.GONE);
            vp.getLayoutParams().height = -1;
            if (land && getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            d.setSystemUiVisibility(0);
            scroll.setVisibility(View.VISIBLE);
            vp.getLayoutParams().height = frameH();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        vp.requestLayout();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        boolean land = c.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (land && !fullscreen) setFull(true);
        else if (!land && fullscreen) setFull(false);
        else if (!fullscreen) { vp.getLayoutParams().height = frameH(); vp.requestLayout(); }
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) { setFull(false); return; }
        super.onBackPressed();
    }

    @Override protected void onPause() {
        vp.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        StreamProxy.on403 = null;
        Dbg.log("video sayfası kapanıyor");
        if (metaPulse != null) metaPulse.cancel();
        vp.release();
        super.onDestroy();
    }
}
