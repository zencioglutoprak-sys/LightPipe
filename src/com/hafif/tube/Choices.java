package com.hafif.tube;

import android.app.Activity;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** İndirme seçenekleri: bilerek çok fazla. Her çözünürlük, her codec, her ses formatı. */
final class Choices {
    private Choices() {}

    static class Choice {
        String label, sub;
        Innertube.Fmt v, a;
        int out, icon;
        boolean header;
    }

    private static final Comparator<Innertube.Fmt> BEST_FIRST = new Comparator<Innertube.Fmt>() {
        @Override public int compare(Innertube.Fmt x, Innertube.Fmt y) {
            int sx = x.hasVideo ? x.h * 1000 + x.fps : x.bitrate / 1000;
            int sy = y.hasVideo ? y.h * 1000 + y.fps : y.bitrate / 1000;
            return sy - sx;
        }
    };

    static String res(Innertube.Fmt f) {
        String r = f.h + "p" + (f.fps > 30 ? String.valueOf(f.fps) : "");
        if (f.h >= 4320) r += " · 8K";
        else if (f.h >= 2160) r += " · 4K";
        else if (f.h >= 1440) r += " · 2K";
        return r;
    }

    private static String mb(long s) { return s > 0 ? Ui.size(s) : ""; }

    private static void head(List<Choice> l, String t) {
        Choice c = new Choice();
        c.label = t;
        c.header = true;
        l.add(c);
    }

    private static void add(List<Choice> l, int icon, String label, String sub, Innertube.Fmt v, Innertube.Fmt a, int out) {
        Choice c = new Choice();
        c.icon = icon; c.label = label; c.sub = sub;
        c.v = v; c.a = a; c.out = out;
        l.add(c);
    }

    static List<Choice> build(Api.Video vid) {
        List<Innertube.Fmt> muxed = new ArrayList<>(), avc = new ArrayList<>(), vp9 = new ArrayList<>(),
                av1 = new ArrayList<>(), audios = new ArrayList<>();
        Innertube.Fmt bestAac = null, bestOpus = null;
        for (Innertube.Fmt f : vid.formats) {
            if (f.hasVideo && f.hasAudio) muxed.add(f);
            else if (f.hasVideo) {
                if (f.isVp9()) vp9.add(f);
                else if (f.isAv1()) av1.add(f);
                else avc.add(f);
            } else {
                audios.add(f);
                if (f.isAac() && (bestAac == null || f.bitrate > bestAac.bitrate)) bestAac = f;
                if (f.isOpus() && (bestOpus == null || f.bitrate > bestOpus.bitrate)) bestOpus = f;
            }
        }
        Collections.sort(muxed, BEST_FIRST);
        Collections.sort(avc, BEST_FIRST);
        Collections.sort(vp9, BEST_FIRST);
        Collections.sort(av1, BEST_FIRST);
        Collections.sort(audios, BEST_FIRST);

        List<Choice> l = new ArrayList<>();
        if (!muxed.isEmpty()) {
            head(l, "HAZIR · VİDEO + SES TEK DOSYA");
            for (Innertube.Fmt f : muxed) add(l, Ic.VIDEO, f.ext().toUpperCase() + " " + res(f), "video+ses · " + mb(f.size), f, null, Dl.RAW);
        }
        if (bestAac != null && !avc.isEmpty()) {
            head(l, "MP4 · H.264 + AAC (telefonda birleştirilir)");
            for (Innertube.Fmt f : avc) add(l, Ic.VIDEO, "MP4 " + res(f), "H.264 + AAC · " + mb(f.size + bestAac.size), f, bestAac, Dl.MP4);
        }
        if (bestOpus != null && !vp9.isEmpty()) {
            head(l, "WEBM · VP9 + OPUS (8K'ya kadar)");
            for (Innertube.Fmt f : vp9) add(l, Ic.VIDEO, "WEBM " + res(f), "VP9 + Opus · " + mb(f.size + bestOpus.size), f, bestOpus, Dl.WEBM);
        }
        if (bestAac != null) {
            boolean any = false;
            for (Innertube.Fmt f : avc) {
                if (f.h > 360) continue;
                if (!any) { head(l, "3GP · ESKİ TELEFONLAR"); any = true; }
                add(l, Ic.VIDEO, "3GP " + res(f), "H.264 + AAC · " + mb(f.size + bestAac.size), f, bestAac, Dl.GP3);
            }
        }
        if (!audios.isEmpty()) {
            head(l, "SADECE SES · MÜZİK");
            for (Innertube.Fmt f : audios) {
                String kind = f.isOpus() ? "WEBM · Opus" : "M4A · AAC";
                add(l, Ic.MUSIC, kind + " " + (f.bitrate / 1000) + " kbps", "ses · " + mb(f.size), f, null, Dl.RAW);
            }
        }
        if (!avc.isEmpty() || !vp9.isEmpty() || !av1.isEmpty()) {
            head(l, "SADECE GÖRÜNTÜ (sessiz)");
            for (Innertube.Fmt f : avc) add(l, Ic.VIDEO, "MP4 " + res(f), "H.264 · sessiz · " + mb(f.size), f, null, Dl.RAW);
            for (Innertube.Fmt f : vp9) add(l, Ic.VIDEO, "WEBM " + res(f), "VP9 · sessiz · " + mb(f.size), f, null, Dl.RAW);
            for (Innertube.Fmt f : av1) add(l, Ic.VIDEO, "MP4 " + res(f), "AV1 · sessiz · " + mb(f.size), f, null, Dl.RAW);
        }
        return l;
    }

    static void show(final Activity act, final Api.Video vid, final Dl.Status st) {
        final Sheet sh = new Sheet(act, "İndir — format seç");
        for (final Choice c : build(vid)) {
            if (c.header) { sh.header(c.label); continue; }
            sh.item(c.icon, c.label, c.sub, new Runnable() {
                @Override public void run() {
                    Ui.snack(act, "İndirme başladı");
                    Dl.start(act.getApplicationContext(), vid.title, c.v, c.a, c.out, st);
                }
            });
        }
        sh.show();
    }
}
