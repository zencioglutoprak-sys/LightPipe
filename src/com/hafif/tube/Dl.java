package com.hafif.tube;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/** İndirme + (gerekirse) ses/video birleştirme. Dosyalar Download/LightPipe altına düşer. */
public final class Dl {
    private Dl() {}

    public interface Status { void on(String s); }
    public interface Done { void ok(File f); void fail(String msg); }

    /** Sürmekte olan (ve hata verip kullanıcı onayını bekleyen) indirmeler: iş no -> "başlık\ndurum". */
    public static final ConcurrentHashMap<Long, String> ACTIVE = new ConcurrentHashMap<>();
    private interface Prog { void on(long done, long total); }

    /** Çıktı türleri: ham dosya, MP4'e / WEBM'e / 3GP'ye birleştir. */
    public static final int RAW = 0, MP4 = 1, WEBM = 2, GP3 = 3;

    // Büyük tek istekler yavaşlatılabiliyor; ~9MB'lık Range parçalarıyla çekiyoruz.
    private static final long CHUNK = 9_000_000L;

    public static String safe(String s) {
        String t = s.replaceAll("[^\\p{L}\\p{N} ._-]", "_").trim();
        if (t.length() > 80) t = t.substring(0, 80);
        return t.isEmpty() ? "video" : t;
    }

    /** audio == null ise v tek dosya olarak iner; değilse v + audio indirilip out türünde birleştirilir. */
    public static void start(final Context ctx, final String title, final Innertube.Fmt v,
                             final Innertube.Fmt audio, final int out, final Status cb) {
        final long job = System.nanoTime();
        final String shortTitle = title.length() > 40 ? title.substring(0, 40) + "…" : title;
        ACTIVE.put(job, shortTitle + "\nBaşlıyor…");
        final long[] last = {0};
        final Status status = new Status() {
            @Override public void on(String s) {
                long now = SystemClock.elapsedRealtime();
                boolean fin = s.startsWith("✓") || s.startsWith("Hata") || s.startsWith("Birle");
                if (!fin && now - last[0] < 250) return; // ilerleme çubuğu yağmurunu seyrelt
                last[0] = now;
                ACTIVE.put(job, shortTitle + "\n" + s);
                if (cb != null) cb.on(s);
            }
        };
        new Thread(new Runnable() {
            @Override public void run() {
                File tv = null, ta = null;
                try {
                    String label = v.hasVideo ? v.h + "p" + (v.fps > 30 ? String.valueOf(v.fps) : "")
                            : "ses " + (v.bitrate / 1000) + "k";
                    String ext, mime;
                    if (out == MP4) { ext = "mp4"; mime = "video/mp4"; }
                    else if (out == WEBM) { ext = "webm"; mime = "video/webm"; }
                    else if (out == GP3) { ext = "3gp"; mime = "video/3gpp"; }
                    else { ext = v.ext(); mime = v.mimeBase(); }
                    String name = safe(title) + " " + label + "." + ext;

                    if (audio == null || out == RAW) {
                        Uri uri = newOut(ctx, name, mime);
                        OutputStream os = ctx.getContentResolver().openOutputStream(uri);
                        try {
                            fetch(v.url, os, new Prog() {
                                @Override public void on(long d, long t) { status.on(pct("İndiriliyor", d, t)); }
                            });
                        } finally { os.close(); }
                    } else {
                        long id = System.nanoTime();
                        tv = new File(ctx.getCacheDir(), "v" + id + ".tmp");
                        ta = new File(ctx.getCacheDir(), "a" + id + ".tmp");
                        OutputStream o1 = new FileOutputStream(tv);
                        try {
                            fetch(v.url, o1, new Prog() {
                                @Override public void on(long d, long t) { status.on(pct("Görüntü", d, t)); }
                            });
                        } finally { o1.close(); }
                        OutputStream o2 = new FileOutputStream(ta);
                        try {
                            fetch(audio.url, o2, new Prog() {
                                @Override public void on(long d, long t) { status.on(pct("Ses", d, t)); }
                            });
                        } finally { o2.close(); }
                        status.on("Birleştiriliyor…");
                        Uri uri = newOut(ctx, name, mime);
                        ParcelFileDescriptor pfd = ctx.getContentResolver().openFileDescriptor(uri, "rw");
                        try {
                            mux(tv, ta, pfd.getFileDescriptor(), out);
                        } finally { pfd.close(); }
                    }
                    status.on("✓ Bitti → Download/LightPipe\n" + name);
                } catch (Exception e) {
                    Dbg.err("indirme", e);
                    status.on("Hata: " + e.getMessage());
                } finally {
                    if (tv != null) tv.delete();
                    if (ta != null) ta.delete();
                    String cur = ACTIVE.get(job);
                    if (cur != null && !cur.contains("Hata:")) ACTIVE.remove(job); // başarılıysa dosya listede görünür
                }
            }
        }).start();
    }

    /**
     * Oynatma için: görüntü + sesi cache'e indirir, outFile'a MP4 olarak birleştirir (arka planda).
     * Birleşik (ses+video) akış olmayan videolarda kullanılır.
     */
    public static void prepare(final File cacheDir, final Innertube.Fmt v, final Innertube.Fmt audio,
                               final File outFile, final Status st, final Done done) {
        new Thread(new Runnable() {
            @Override public void run() {
                File tv = new File(cacheDir, "pv.tmp");
                File ta = new File(cacheDir, "pa.tmp");
                final long[] last = {0};
                try {
                    OutputStream o1 = new FileOutputStream(tv);
                    try {
                        fetch(v.url, o1, new Prog() {
                            @Override public void on(long d, long t) {
                                long now = SystemClock.elapsedRealtime();
                                if (now - last[0] < 300) return;
                                last[0] = now;
                                st.on(pct("Görüntü", d, t));
                            }
                        });
                    } finally { o1.close(); }
                    OutputStream o2 = new FileOutputStream(ta);
                    try {
                        fetch(audio.url, o2, new Prog() {
                            @Override public void on(long d, long t) {
                                long now = SystemClock.elapsedRealtime();
                                if (now - last[0] < 300) return;
                                last[0] = now;
                                st.on(pct("Ses", d, t));
                            }
                        });
                    } finally { o2.close(); }
                    st.on("Birleştiriliyor…");
                    if (outFile.exists()) outFile.delete();
                    RandomAccessFile raf = new RandomAccessFile(outFile, "rw");
                    try {
                        mux(tv, ta, raf.getFD(), MP4);
                    } finally { raf.close(); }
                    done.ok(outFile);
                } catch (Exception e) {
                    Dbg.err("indirme/ses", e);
                    done.fail(String.valueOf(e.getMessage()));
                } finally {
                    tv.delete();
                    ta.delete();
                }
            }
        }).start();
    }

    private static String pct(String label, long d, long t) {
        if (t > 0) return label + " %" + (d * 100 / t) + "  (" + (d / 1_000_000) + " / " + (t / 1_000_000) + " MB)";
        return label + " " + (d / 1_000_000) + " MB";
    }

    private static Uri newOut(Context ctx, String name, String mime) throws IOException {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
        cv.put(MediaStore.Downloads.MIME_TYPE, mime);
        cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/LightPipe");
        Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) throw new IOException("Dosya oluşturulamadı");
        return uri;
    }

    private static void fetch(String url, OutputStream out, Prog prog) throws IOException {
        long pos = 0, total = -1;
        byte[] buf = new byte[64 * 1024];
        while (true) {
            long end = pos + CHUNK - 1;
            int code = 0;
            HttpURLConnection c = null;
            for (int mode = 0; mode < 2; mode++) {
                // googlevideo ?range= parametresini Range başlığından daha az reddeder
                String u = mode == 0 ? url + (url.contains("?") ? "&" : "?") + "range=" + pos + "-" + end : url;
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", Innertube.uaFor(url));
                if (mode == 1) c.setRequestProperty("Range", "bytes=" + pos + "-" + end);
                code = c.getResponseCode();
                if (code == 200 || code == 206) break;
                c.disconnect();
            }
            if (code == 416 && pos > 0) break;
            if (code != 200 && code != 206) throw new IOException("HTTP " + code);
            boolean whole = code == 200; // sunucu Range'i yok saydı: tüm dosya tek seferde geliyor
            if (total < 0) {
                String cr = c.getHeaderField("Content-Range");
                if (cr != null && cr.indexOf('/') >= 0) {
                    try { total = Long.parseLong(cr.substring(cr.indexOf('/') + 1)); } catch (NumberFormatException ignored) {}
                }
                if (total < 0) {
                    java.util.regex.Matcher mm = java.util.regex.Pattern.compile("[?&]clen=(\\d+)").matcher(url);
                    if (mm.find()) total = Long.parseLong(mm.group(1));
                }
                if (total < 0 && !url.contains("range=")) total = c.getContentLengthLong();
            }
            long got = 0;
            InputStream in = c.getInputStream();
            try {
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    got += n;
                    prog.on(pos + got, total);
                }
            } finally { in.close(); }
            pos += got;
            c.disconnect();
            if (whole || got == 0 || (total > 0 && pos >= total)) break;
        }
    }

    private static void mux(File video, File audio, FileDescriptor out, int kind) throws IOException {
        int fmt = kind == WEBM ? MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                : kind == GP3 ? MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
                : MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
        MediaExtractor ev = new MediaExtractor();
        ev.setDataSource(video.getPath());
        MediaExtractor ea = new MediaExtractor();
        ea.setDataSource(audio.getPath());
        int vt = track(ev, "video/");
        int at = track(ea, "audio/");
        ev.selectTrack(vt);
        ea.selectTrack(at);
        MediaMuxer mm = new MediaMuxer(out, fmt);
        int vi = mm.addTrack(ev.getTrackFormat(vt));
        int ai = mm.addTrack(ea.getTrackFormat(at));
        mm.start();
        copy(ev, mm, vi);
        copy(ea, mm, ai);
        mm.stop();
        mm.release();
        ev.release();
        ea.release();
    }

    private static int track(MediaExtractor e, String prefix) throws IOException {
        for (int i = 0; i < e.getTrackCount(); i++) {
            String m = e.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith(prefix)) return i;
        }
        throw new IOException(prefix + " izi bulunamadı");
    }

    private static void copy(MediaExtractor e, MediaMuxer mm, int idx) {
        ByteBuffer buf = ByteBuffer.allocate(4 << 20); // 8K karelerde büyük örnekler olabiliyor
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int n = e.readSampleData(buf, 0);
            if (n < 0) break;
            int key = (e.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
            info.set(0, n, e.getSampleTime(), key);
            mm.writeSampleData(idx, buf, info);
            e.advance();
        }
    }
}
