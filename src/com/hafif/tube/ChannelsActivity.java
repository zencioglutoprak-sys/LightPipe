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

/** Abone olunan kanallar (giriş gerekir). */
public class ChannelsActivity extends Activity {
    private LinearLayout list;
    private TextView state;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Themes.init(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        Auth.init(this);
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
        top.addView(Ui.text(this, "Kanallar", 18, Ui.TEXT, true));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        page.setPadding(p, Ui.dp(this, 6), p, Ui.dp(this, 28));
        sc.addView(page, new ScrollView.LayoutParams(-1, -2));
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
        state = Ui.text(this, "Yükleniyor…", 13, Ui.DIM, false);
        page.addView(state);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list);
        setContentView(root);
        if (!Auth.logged(this)) { state.setText("Kanallarını görmek için Ayarlar'dan giriş yap"); return; }
        new Thread(new Runnable() {
            @Override public void run() {
                List<Innertube.Chan> c = null;
                String err = null;
                try { c = Innertube.subscriptions(); } catch (Exception e) { err = e.getMessage(); }
                final List<Innertube.Chan> res = c;
                final String fe = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (res == null || res.isEmpty()) {
                            state.setText(res == null ? "Kanallar yüklenemedi" + (fe == null ? "" : ": " + fe) : "Abone olunan kanal bulunamadı");
                            return;
                        }
                        state.setVisibility(View.GONE);
                        for (int i = 0; i < res.size(); i++) {
                            View r = row(res.get(i));
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                            lp.bottomMargin = Ui.dp(ChannelsActivity.this, 10);
                            list.addView(r, lp);
                            Ui.rise(r, Math.min(i, 10) * 40L);
                        }
                    }
                });
            }
        }).start();
    }

    private View row(final Innertube.Chan c) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.box(this, Ui.PANEL, Ui.STROKE, 16));
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        TextView av = Ui.text(this, c.name.isEmpty() ? "" : c.name.substring(0, 1).toUpperCase(), 17, 0xffffffff, true);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Ui.redCircle());
        row.addView(av, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView n = Ui.text(this, c.name, 15, Ui.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(n);
        if (!c.subs.isEmpty()) {
            TextView s = Ui.text(this, c.subs, 12, Ui.SUB, false);
            col.addView(s);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = Ui.dp(this, 12);
        row.addView(col, cp);
        Ui.press(row, 0.98f);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(ChannelsActivity.this, ChannelActivity.class)
                        .putExtra("cid", c.id).putExtra("name", c.name));
            }
        });
        return row;
    }
}
