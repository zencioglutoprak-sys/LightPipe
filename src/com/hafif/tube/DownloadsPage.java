package com.hafif.tube;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** İndirilenler: sürmekte olan indirmeler + dosyalar. Dosyalar uygulamanın içinde açılır. */
final class DownloadsPage extends Page {
    private static final class Row {
        Lib.F f;
        long job;
        String active;
    }

    private static final class H {
        TextView title, meta;
        FrameLayout tile;
        Ic icon;
        Eq eq;
        Bar bar;
        View more;
        long job = -1;
    }

    private final List<Lib.F> files = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private BaseAdapter adapter;
    private LinearLayout emptyBox;
    private TextView[] chips = new TextView[3];
    private TextView playAll, count;
    private int filter = 0, lastAnimated = -1, lastActive = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean on;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            int n = Dl.ACTIVE.size();
            if (n < lastActive) reload();
            lastActive = n;
            rebuild();
            if (on) handler.postDelayed(this, 1000);
        }
    };

    private final Music.Listener ml = new Music.Listener() {
        @Override public void onChange() { adapter.notifyDataSetChanged(); }
    };

    DownloadsPage(Activity act) {
        super(act);
        int p = Ui.dp(a, 16);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        root = col;

        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(p, Ui.dp(a, 6), p, Ui.dp(a, 10));
        String[] names = {"Hepsi", "Video", "Ses"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            chips[i] = Ui.chip(a, names[i], i == 0);
            chips[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { setFilter(k); }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.rightMargin = Ui.dp(a, 8);
            top.addView(chips[i], cp);
        }
        View sp = new View(a);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        playAll = Ui.text(a, "▶  Sesleri çal", 13, 0xffffffff, true);
        playAll.setBackground(Ui.redBox(a, 20));
        playAll.setPadding(Ui.dp(a, 16), Ui.dp(a, 9), Ui.dp(a, 16), Ui.dp(a, 9));
        Ui.press(playAll, 0.92f);
        playAll.setVisibility(View.GONE);
        playAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playAudio(null); }
        });
        top.addView(playAll);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout body = new FrameLayout(a);
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        ListView lv = new ListView(a);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setPadding(p, 0, p, Ui.dp(a, 16));
        lv.setClipToPadding(false);
        lv.setVerticalScrollBarEnabled(false);
        lv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public int getViewTypeCount() { return 1; }
            @Override public View getView(int i, View cv, ViewGroup parent) {
                if (cv == null) cv = newRow();
                bind(cv, rows.get(i), i);
                return cv;
            }
        };
        lv.setAdapter(adapter);
        body.addView(lv, new FrameLayout.LayoutParams(-1, -1));

        emptyBox = new LinearLayout(a);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyBox.addView(new Ic(a, Ic.DOWNLOAD, 56, Ui.DIM), new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 56)));
        TextView t = Ui.text(a, "Henüz indirme yok", 18, Ui.TEXT, true);
        t.setPadding(0, Ui.dp(a, 16), 0, Ui.dp(a, 4));
        t.setGravity(Gravity.CENTER);
        emptyBox.addView(t, new LinearLayout.LayoutParams(-2, -2));
        TextView es = Ui.text(a, "Bir videoyu açıp İndir'e dokun.", 13, Ui.SUB, false);
        es.setGravity(Gravity.CENTER);
        emptyBox.addView(es, new LinearLayout.LayoutParams(-2, -2));
        body.addView(emptyBox, new FrameLayout.LayoutParams(-1, -1));

        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> pr, View v, int pos, long id) {
                Row r = rows.get(pos);
                if (r.active != null) {
                    if (r.active.contains("Hata:")) { Dl.ACTIVE.remove(r.job); rebuild(); }
                    return;
                }
                openFile(r.f);
            }
        });
    }

    private View newRow() {
        final H h = new H();
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.box(a, Ui.PANEL, Ui.STROKE, 18));
        row.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12));

        h.tile = new FrameLayout(a);
        h.tile.setBackground(Ui.box(a, Ui.REDSOFT, 14));
        h.icon = new Ic(a, Ic.VIDEO, 24, Ui.RED_HI);
        h.tile.addView(h.icon, new FrameLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24), Gravity.CENTER));
        h.eq = new Eq(a, 24, Ui.RED_HI);
        h.eq.setVisibility(View.GONE);
        h.tile.addView(h.eq, new FrameLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24), Gravity.CENTER));
        row.addView(h.tile, new LinearLayout.LayoutParams(Ui.dp(a, 50), Ui.dp(a, 50)));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        h.title = Ui.text(a, "", 14, Ui.TEXT, true);
        h.title.setMaxLines(2);
        h.title.setEllipsize(TextUtils.TruncateAt.END);
        h.meta = Ui.text(a, "", 12, Ui.SUB, false);
        h.meta.setPadding(0, Ui.dp(a, 4), 0, 0);
        h.bar = new Bar(a);
        col.addView(h.title);
        col.addView(h.meta);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 5));
        bp.topMargin = Ui.dp(a, 8);
        col.addView(h.bar, bp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = Ui.dp(a, 12);
        row.addView(col, cp);

        FrameLayout more = new FrameLayout(a);
        more.addView(new Ic(a, Ic.MORE, 20, Ui.SUB), new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20), Gravity.CENTER));
        Ui.press(more, 0.8f);
        row.addView(more, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));
        h.more = more;

        LinearLayout wrap = new LinearLayout(a);
        wrap.setPadding(0, 0, 0, Ui.dp(a, 10));
        wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
        wrap.setTag(h);
        Ui.press(row, 0.97f);
        return wrap;
    }

    private void bind(View cv, final Row r, int i) {
        H h = (H) cv.getTag();
        Ui.reset(cv);
        if (i > lastAnimated) {
            lastAnimated = i;
            Ui.rise(cv, Math.min(i, 6) * 55L);
        }
        if (r.active != null) {
            String[] part = r.active.split("\n", 2);
            boolean err = r.active.contains("Hata:");
            h.title.setText(part[0]);
            h.meta.setText(part.length > 1 ? part[1] : "");
            h.meta.setTextColor(err ? Ui.RED_HI : Ui.SUB);
            h.icon.setType(err ? Ic.CLOSE : Ic.DOWNLOAD);
            h.icon.setVisibility(View.VISIBLE);
            h.eq.setVisibility(View.GONE);
            h.bar.setVisibility(err ? View.GONE : View.VISIBLE);
            int pc = Ui.pct(r.active);
            boolean fresh = h.job != r.job;
            h.job = r.job;
            h.bar.set(pc < 0 ? -1f : pc / 100f, !fresh);
            h.more.setVisibility(View.GONE);
        } else {
            h.job = -1;
            h.title.setText(Lib.title(r.f.name));
            h.meta.setText(Lib.tag(r.f.name) + " · " + Ui.size(r.f.size) + " · "
                    + DateFormat.format("dd.MM HH:mm", r.f.date * 1000L));
            h.meta.setTextColor(Ui.SUB);
            h.bar.setVisibility(View.GONE);
            Music.Track cur = Music.current();
            boolean playing = r.f.audio() && cur != null && cur.uri.equals(r.f.uri);
            h.icon.setType(r.f.audio() ? Ic.MUSIC : Ic.VIDEO);
            h.icon.setVisibility(playing ? View.GONE : View.VISIBLE);
            h.eq.setVisibility(playing ? View.VISIBLE : View.GONE);
            h.eq.setActive(Music.playing());
            h.more.setVisibility(View.VISIBLE);
            h.more.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { menu(r.f); }
            });
        }
    }

    // ------------------------------------------------------------------ veri

    @Override void resume() {
        on = true;
        reload();
        lastActive = Dl.ACTIVE.size();
        lastAnimated = -1;
        Music.listen(ml);
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override void pause() {
        on = false;
        handler.removeCallbacks(tick);
        Music.unlisten(ml);
    }

    private void reload() {
        files.clear();
        files.addAll(Lib.query(a));
        rebuild();
    }

    private void setFilter(int k) {
        if (k == filter) return;
        Ui.setChip(a, chips[filter], false, true);
        Ui.setChip(a, chips[k], true, true);
        filter = k;
        lastAnimated = -1;
        rebuild();
    }

    private List<Lib.F> audios() {
        List<Lib.F> l = new ArrayList<>();
        for (Lib.F f : files) if (f.audio()) l.add(f);
        return l;
    }

    private void rebuild() {
        rows.clear();
        if (filter == 0) {
            for (Map.Entry<Long, String> e : new TreeMap<>(Dl.ACTIVE).entrySet()) {
                Row r = new Row();
                r.job = e.getKey();
                r.active = e.getValue();
                rows.add(r);
            }
        }
        for (Lib.F f : files) {
            if (filter == 1 && !f.video()) continue;
            if (filter == 2 && !f.audio()) continue;
            Row r = new Row();
            r.f = f;
            rows.add(r);
        }
        adapter.notifyDataSetChanged();
        boolean empty = rows.isEmpty();
        if (empty && emptyBox.getVisibility() != View.VISIBLE) { emptyBox.setVisibility(View.VISIBLE); Ui.popIn(emptyBox, 0); }
        else if (!empty) emptyBox.setVisibility(View.GONE);
        boolean hasAudio = !audios().isEmpty();
        if (hasAudio && playAll.getVisibility() != View.VISIBLE) { playAll.setVisibility(View.VISIBLE); Ui.popIn(playAll, 0); }
        else if (!hasAudio) playAll.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ açma / menü

    private void playAudio(Lib.F start) {
        List<Lib.F> au = audios();
        if (au.isEmpty()) return;
        List<Music.Track> tl = new ArrayList<>();
        int idx = 0;
        for (int i = 0; i < au.size(); i++) {
            Lib.F f = au.get(i);
            tl.add(new Music.Track(f.uri, f.name));
            if (start != null && f.id == start.id) idx = i;
        }
        Music.loadPrefs(a);
        if (start == null && Music.shuffle) idx = (int) (Math.random() * tl.size());
        Music.play(a, tl, idx);
        a.startActivity(new Intent(a, MusicActivity.class));
    }

    private void openFile(Lib.F f) {
        if (f.audio()) { playAudio(f); return; }
        a.startActivity(new Intent(a, PlayActivity.class).putExtra("uri", f.uri.toString()).putExtra("name", Lib.title(f.name)));
    }

    private void menu(final Lib.F f) {
        final Sheet s = new Sheet(a, Lib.title(f.name));
        s.item(Ic.PLAY, f.audio() ? "Müzik çalarda aç" : "Uygulama içinde oynat", Lib.tag(f.name) + " · " + Ui.size(f.size), new Runnable() {
            @Override public void run() { openFile(f); }
        });
        s.item(Ic.SHARE, "Paylaş", null, new Runnable() {
            @Override public void run() {
                Intent i = new Intent(Intent.ACTION_SEND).setType(f.mime.isEmpty() ? "*/*" : f.mime)
                        .putExtra(Intent.EXTRA_STREAM, f.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                a.startActivity(Intent.createChooser(i, "Paylaş"));
            }
        });
        s.item(Ic.LINK, "Başka uygulamada aç", null, new Runnable() {
            @Override public void run() {
                try {
                    a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(f.uri, f.mime.isEmpty() ? "*/*" : f.mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                } catch (ActivityNotFoundException e) {
                    Ui.snack(a, "Bunu açacak uygulama yok");
                }
            }
        });
        s.item(Ic.TRASH, "Sil", "Dosya cihazdan silinir", new Runnable() {
            @Override public void run() { confirmDelete(f); }
        });
        s.show();
    }

    private void confirmDelete(final Lib.F f) {
        final Sheet s = new Sheet(a, "Silinsin mi?");
        s.item(Ic.TRASH, "Evet, sil", Lib.title(f.name), new Runnable() {
            @Override public void run() {
                try {
                    a.getContentResolver().delete(f.uri, null, null);
                    Ui.snack(a, "Silindi");
                } catch (Exception e) {
                    Ui.snack(a, "Silinemedi: " + e.getMessage());
                }
                reload();
            }
        });
        s.item(Ic.CLOSE, "Vazgeç", null, null);
        s.show();
    }
}
