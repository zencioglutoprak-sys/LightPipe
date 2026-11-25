package com.hafif.tube;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Video kartları. */
final class Views {
    private Views() {}

    static String thumb(String id) { return "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg"; }

    static void open(Activity a, Innertube.Item it, boolean dl) {
        Intent i = new Intent(a, VideoActivity.class).putExtra("id", it.id)
                .putExtra("title", it.title).putExtra("author", it.author).putExtra("dl", dl);
        a.startActivity(i);
    }

    private static String sub(Innertube.Item it) {
        StringBuilder sb = new StringBuilder(it.author);
        if (!it.views.isEmpty()) sb.append(" · ").append(it.views);
        if (!it.published.isEmpty()) sb.append(" · ").append(it.published);
        return sb.toString();
    }

    private static TextView badge(Activity a, FrameLayout fr) {
        TextView t = Ui.text(a, "", 11, 0xffffffff, true);
        t.setBackground(Ui.box(a, 0xCC000000, 8));
        t.setPadding(Ui.dp(a, 7), Ui.dp(a, 4), Ui.dp(a, 7), Ui.dp(a, 4));
        t.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, Ui.dp(a, 8), Ui.dp(a, 8));
        fr.addView(t, lp);
        return t;
    }

    private static void setBadge(TextView t, String d) {
        boolean show = d != null && !d.isEmpty() && !d.equals("short");
        t.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) t.setText(d);
    }

    private static final class H {
        ImageView iv;
        TextView title, ch, dur;
        View dl;
        Innertube.Item item;
    }

    /** Büyük kart: 16:9 küçük resim, başlık, kanal, indirme kısayolu. */
    static View newCard(final Activity a, int width) {
        final H h = new H();
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.box(a, Ui.PANEL, Ui.STROKE, 20));
        Ui.clipRound(card, Ui.dp(a, 20));

        FrameLayout fr = new FrameLayout(a);
        h.iv = new ImageView(a);
        h.iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        h.iv.setBackgroundColor(Ui.PANEL2);
        fr.addView(h.iv, new FrameLayout.LayoutParams(-1, -1));
        h.dur = badge(a, fr);
        card.addView(fr, new LinearLayout.LayoutParams(-1, width * 9 / 16));

        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        h.title = Ui.text(a, "", 15, Ui.TEXT, true);
        h.title.setMaxLines(2);
        h.title.setEllipsize(TextUtils.TruncateAt.END);
        h.title.setLineSpacing(0, 1.1f);
        h.ch = Ui.text(a, "", 12, Ui.SUB, false);
        h.ch.setSingleLine(true);
        h.ch.setEllipsize(TextUtils.TruncateAt.END);
        h.ch.setPadding(0, Ui.dp(a, 5), 0, 0);
        col.addView(h.title);
        col.addView(h.ch);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        FrameLayout dl = new FrameLayout(a);
        dl.setBackground(Ui.box(a, Ui.REDSOFT, 16));
        dl.addView(new Ic(a, Ic.DOWNLOAD, 20, Ui.RED_HI), new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20), Gravity.CENTER));
        Ui.press(dl, 0.86f);
        dl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (h.item != null) open(a, h.item, true); }
        });
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 42));
        dp.leftMargin = Ui.dp(a, 10);
        row.addView(dl, dp);
        h.dl = dl;
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
        card.setTag(h);
        Ui.press(card, 0.97f);
        return card;
    }

    static void bindCard(Activity a, View card, Innertube.Item it) {
        H h = (H) card.getTag();
        h.item = it;
        h.title.setText(it.title);
        h.ch.setText(sub(it));
        setBadge(h.dur, it.duration);
        Img.load(h.iv, thumb(it.id), a);
    }

    /** Yüklenirken nabız atan iskelet kart. */
    static View skeleton(Activity a, int width) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.box(a, Ui.PANEL, Ui.STROKE, 20));
        View t = new View(a);
        t.setBackgroundColor(Ui.PANEL2);
        card.addView(t, new LinearLayout.LayoutParams(-1, width * 9 / 16));
        View l1 = new View(a);
        l1.setBackground(Ui.box(a, Ui.PANEL2, 6));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(-1, Ui.dp(a, 14));
        p1.setMargins(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 50), 0);
        card.addView(l1, p1);
        View l2 = new View(a);
        l2.setBackground(Ui.box(a, Ui.PANEL2, 6));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(Ui.dp(a, 120), Ui.dp(a, 11));
        p2.setMargins(Ui.dp(a, 14), Ui.dp(a, 10), 0, Ui.dp(a, 16));
        card.addView(l2, p2);
        Ui.clipRound(card, Ui.dp(a, 20));
        return card;
    }

    /** Küçük yatay kart (ilgili videolar). */
    static View compact(final Activity a, final Innertube.Item it) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackground(Ui.box(a, Ui.PANEL, Ui.STROKE, 16));
        root.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));

        FrameLayout fr = new FrameLayout(a);
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(Ui.PANEL2);
        fr.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        setBadge(badge(a, fr), it.duration);
        Ui.clipRound(fr, Ui.dp(a, 12));
        int w = Ui.dp(a, 132);
        root.addView(fr, new LinearLayout.LayoutParams(w, w * 9 / 16));
        Img.load(iv, thumb(it.id), a);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(Ui.dp(a, 12), 0, 0, 0);
        TextView title = Ui.text(a, it.title, 14, Ui.TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView ch = Ui.text(a, sub(it), 11, Ui.SUB, false);
        ch.setSingleLine(true);
        ch.setEllipsize(TextUtils.TruncateAt.END);
        ch.setPadding(0, Ui.dp(a, 5), 0, 0);
        col.addView(title);
        col.addView(ch);
        root.addView(col, new LinearLayout.LayoutParams(0, -1, 1f));
        Ui.press(root, 0.97f);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(a, it, false); }
        });
        return root;
    }
}
