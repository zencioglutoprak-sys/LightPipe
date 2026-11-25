package com.hafif.tube;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Kanal sayfası: ad, abone sayısı, açıklama ve videolar. */
public class ChannelActivity extends Activity {
    private LinearLayout page, list;
    private TextView name, subs, avatar, state;
    private LinearLayout descCard;
    private TextView descTx;
    private String cid, nm;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Themes.init(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        cid = getIntent().getStringExtra("cid");
        nm = getIntent().getStringExtra("name");
        if (cid == null) { finish(); return; }
        int p = Ui.dp(this, 16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        FrameLayout back = new FrameLayout(this);
        back.addView(new Ic(this, Ic.BACK, 22, Ui.TEXT), new FrameLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22), Gravity.CENTER));
        Ui.press(back, 0.88f);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        top.addView(Ui.text(this, "Kanal", 18, Ui.TEXT, true));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(p, Ui.dp(this, 6), p, Ui.dp(this, 28));
        sc.addView(page, new ScrollView.LayoutParams(-1, -2));
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        avatar = Ui.text(this, nm == null || nm.isEmpty() ? "" : nm.substring(0, 1).toUpperCase(), 24, 0xffffffff, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.redCircle());
        head.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        name = Ui.text(this, nm == null ? "" : nm, 20, Ui.TEXT, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name);
        subs = Ui.text(this, "", 13, Ui.SUB, false);
        subs.setPadding(0, Ui.dp(this, 4), 0, 0);
        col.addView(subs);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = Ui.dp(this, 14);
        head.addView(col, cp);
        page.addView(head);
        Ui.rise(head, 80);

        descCard = new LinearLayout(this);
        descCard.setOrientation(LinearLayout.VERTICAL);
        descCard.setBackground(Ui.box(this, Ui.PANEL, Ui.STROKE, 16));
        descCard.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        descCard.setVisibility(View.GONE);
        descCard.addView(Ui.text(this, "Hakkında", 14, Ui.TEXT, true));
        descTx = Ui.text(this, "", 13, Ui.SUB, false);
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
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(-1, -2);
        dp1.topMargin = Ui.dp(this, 16);
        page.addView(descCard, dp1);

        TextView vh = Ui.text(this, "Videolar", 17, Ui.TEXT, true);
        vh.setPadding(Ui.dp(this, 2), Ui.dp(this, 20), 0, Ui.dp(this, 10));
        page.addView(vh);
        state = Ui.text(this, "Yükleniyor…", 13, Ui.DIM, false);
        page.addView(state);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list);
        setContentView(root);
        load();
    }

    private void load() {
        state.setText("Yükleniyor…");
        state.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                Innertube.Chan c = null;
                String err = null;
                try { c = Api.channel(cid); } catch (Exception e) { err = e.getMessage(); }
                final Innertube.Chan res = c;
                final String fe = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (res == null) {
                            state.setText("Kanal yüklenemedi" + (fe == null ? "" : ": " + fe));
                            return;
                        }
                        if (!res.name.isEmpty()) {
                            name.setText(res.name);
                            avatar.setText(res.name.substring(0, 1).toUpperCase());
                        }
                        subs.setText(res.subs);
                        if (!res.desc.trim().isEmpty()) {
                            descTx.setText(res.desc.trim());
                            descCard.setVisibility(View.VISIBLE);
                            Ui.rise(descCard, 0);
                        }
                        List<Innertube.Item> vs = res.videos;
                        state.setVisibility(vs.isEmpty() ? View.VISIBLE : View.GONE);
                        if (vs.isEmpty()) state.setText("Video bulunamadı");
                        for (int i = 0; i < vs.size() && i < 40; i++) {
                            View v = Views.compact(ChannelActivity.this, vs.get(i));
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                            lp.bottomMargin = Ui.dp(ChannelActivity.this, 10);
                            list.addView(v, lp);
                            Ui.rise(v, Math.min(i, 8) * 50L);
                        }
                    }
                });
            }
        }).start();
    }
}
