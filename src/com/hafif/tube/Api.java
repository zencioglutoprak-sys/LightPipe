package com.hafif.tube;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * notPipe gibi: YouTube'a doğrudan bağlanmak yerine Invidious sunucularının API'sini kullanır
 * (bot doğrulamasına takılmaz). Hızlı olması için sunucular sırayla beklenmez: ilki hemen,
 * diğerleri STAGGER_MS arayla başlatılır, ilk başarılı yanıt kazanır. Çalışan sunucu listede öne alınır.
 * Hepsi başarısız olursa son çare olarak doğrudan YouTube (Innertube) denenir.
 */
public final class Api {
    private Api() {}

    /** Ayarlardan değiştirilebilir. Bu sunucuların API'sinin açık olduğu doğrulanmadı. */
    public static final String[] DEFAULT_INSTANCES = {
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://yt.chocolatemoo53.com",
            "https://invidious.tiekoetter.com",
            "https://invidious.f5.si",
            "https://inv.zoomerville.com",
    };

    public static class Video {
        public String id = "", title = "", author = "", description = "", source = "", channelId = "", hls = "";
        public boolean live;
        public int client;
        public long views, likes;
        public int seconds;
        public List<Innertube.Fmt> formats = new ArrayList<>();
        public List<Innertube.Item> related = new ArrayList<>();
    }

    private interface Parse<T> { T run(String base, String body) throws Exception; }

    private static final class Res<T> {
        final String base;
        final T val;
        Res(String base, T val) { this.base = base; this.val = val; }
    }

    private static final int CONNECT_MS = 5000;   // ölü sunucuyu çabuk ele
    private static final int STAGGER_MS = 1500;   // sıradaki sunucuyu başlatmadan önce bekleme
    private static final int DIRECT_HEAD_MS = 800; // doğrudan YouTube aramasına verilen ön avantaj
    private static final long FAIL_MS = 5 * 60 * 1000L; // çöken sunucuyu bu kadar süre sona at

    /** Çöken (bağlanamayan / API'si kapalı) sunucular: bir süre denemeyi sona bırakır. */
    private static final Map<String, Long> FAIL_UNTIL = Collections.synchronizedMap(new HashMap<String, Long>());

    /** Sunucu çökmedi, sadece sonuç boş: sunucuyu kötü işaretleme. */
    private static final class Soft extends IOException {
        Soft(String m) { super(m); }
    }

    // Yanıtı küçültür: sadece listede gerekli alanlar / videoda gerekli üst düzey alanlar.
    private static final String LIST_FIELDS = "type,videoId,title,author,lengthSeconds,viewCount,viewCountText,publishedText";
    private static final String VIDEO_FIELDS = "videoId,title,author,viewCount,likeCount,lengthSeconds,description,authorId,hlsUrl,liveNow,formatStreams,adaptiveFormats,recommendedVideos";

    private static final LruCache<String, List<Innertube.Item>> SEARCH_CACHE = new LruCache<>(12);

    private static final ExecutorService POOL = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "api");
            t.setDaemon(true);
            return t;
        }
    });

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("hafif", Context.MODE_PRIVATE);
    }

    static String norm(String u) {
        u = u.trim();
        if (!u.startsWith("http")) u = "https://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    public static List<String> instances(Context c) {
        String s = prefs(c).getString("instances", null);
        List<String> r = new ArrayList<>();
        if (s == null) { r.addAll(Arrays.asList(DEFAULT_INSTANCES)); return r; }
        for (String l : s.split("\n")) {
            l = l.trim();
            if (!l.isEmpty()) r.add(norm(l));
        }
        return r;
    }

    public static void saveInstances(Context c, List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) sb.append(s).append('\n');
        prefs(c).edit().putString("instances", sb.toString()).apply();
        SEARCH_CACHE.evictAll();
    }

    private static String get(String url, int readMs, List<HttpURLConnection> conns) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        conns.add(c);
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(readMs);
        c.setRequestProperty("User-Agent", "LightPipe/0.5");
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            Dbg.log("api " + host(url.replaceAll("/api/.*","")) + " HTTP " + code);
            throw new IOException("HTTP " + code);
        }
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private static String host(String base) { return base.replace("https://", "").replace("http://", ""); }

    private static <T> T tryAll(Context c, String path, int readMs, Parse<T> p) throws Exception {
        return tryAll(c, path, readMs, p, null, STAGGER_MS, DIRECT_HEAD_MS);
    }

    private static <T> T tryAll(Context c, String path, int readMs, Parse<T> p, Callable<T> direct) throws Exception {
        return tryAll(c, path, readMs, p, direct, STAGGER_MS, DIRECT_HEAD_MS);
    }

    /**
     * Gecikmeli-paralel deneme. direct varsa (doğrudan YouTube) hemen başlar; Invidious sunucuları
     * sağlıklı olanlar önde olacak şekilde, ilki DIRECT_HEAD_MS sonra, diğerleri STAGGER_MS arayla
     * (ya da öncekiler hata verir vermez) başlar. İlk başarılı yanıt kazanır, kalan istekler kesilir.
     */
    private static <T> T tryAll(final Context c, final String path, final int readMs,
                                final Parse<T> p, final Callable<T> direct,
                                final int stagger, final int head) throws Exception {
        final List<String> list = instances(c);
        final List<String> order = new ArrayList<>();
        final List<String> bad = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        for (String s : list) {
            Long until = FAIL_UNTIL.get(s);
            if (until != null && until > t0) bad.add(s); else order.add(s);
        }
        order.addAll(bad); // yakın zamanda çökenler en sona
        final int n = order.size();
        if (n == 0 && direct == null) throw new IOException("Sunucu listesi boş (Ayarlar'dan ekle)");
        final int total = n + (direct != null ? 1 : 0);

        final List<HttpURLConnection> conns = Collections.synchronizedList(new ArrayList<HttpURLConnection>());
        ExecutorCompletionService<Res<T>> cs = new ExecutorCompletionService<>(POOL);
        long deadline = System.currentTimeMillis() + CONNECT_MS + readMs + 4000L;
        int submitted = 0, finished = 0;
        boolean directSent = direct == null;
        StringBuilder errs = new StringBuilder();
        try {
            while (finished < total && System.currentTimeMillis() < deadline) {
                long wait;
                if (!directSent) {
                    directSent = true;
                    cs.submit(new Callable<Res<T>>() {
                        @Override public Res<T> call() throws Exception {
                            try {
                                return new Res<T>(null, direct.call());
                            } catch (Exception e) {
                                throw new Exception("YouTube: " + e.getMessage());
                            }
                        }
                    });
                    wait = n > 0 ? head : 400;
                } else if (submitted < n) {
                    final String base = order.get(submitted++);
                    cs.submit(new Callable<Res<T>>() {
                        @Override public Res<T> call() throws Exception {
                            try {
                                return new Res<T>(base, p.run(base, get(base + path, readMs, conns)));
                            } catch (Exception e) {
                                // 404 genelde "bu uç nokta/içerik yok" demektir (ör. yorumlar); sunucu sağlığı sayılmaz, yoksa çalışan sunucu sona itilir
                                if (!(e instanceof Soft) && !String.valueOf(e.getMessage()).contains("HTTP 404")) FAIL_UNTIL.put(base, System.currentTimeMillis() + FAIL_MS);
                                throw new Exception(host(base) + ": " + e.getMessage());
                            }
                        }
                    });
                    wait = submitted < n ? stagger : 400;
                } else {
                    wait = 400;
                }
                Future<Res<T>> f = cs.poll(wait, TimeUnit.MILLISECONDS);
                if (f == null) continue;
                finished++;
                try {
                    Res<T> r = f.get();
                    if (r.base != null) {
                        FAIL_UNTIL.remove(r.base);
                        int idx = list.indexOf(r.base);
                        if (idx > 0) { // çalışanı öne al
                            list.remove(idx);
                            list.add(0, r.base);
                            prefs(c).edit().putString("instances", join(list)).apply();
                        }
                    }
                    return r.val;
                } catch (ExecutionException e) {
                    Throwable t = e.getCause();
                    errs.append(t != null ? t.getMessage() : e.getMessage()).append('\n');
                }
            }
        } finally {
            synchronized (conns) {
                for (HttpURLConnection h : conns) {
                    try { h.disconnect(); } catch (Exception ignored) {}
                }
            }
        }
        throw new IOException(errs.length() > 0 ? errs.toString().trim() : "Zaman aşımı");
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) sb.append(s).append('\n');
        return sb.toString();
    }

    // ---------------------------------------------------------------- arama / popüler

    public static List<Innertube.Item> search(Context c, final String q) throws Exception {
        Auth.init(c);
        String key = q.trim().toLowerCase();
        List<Innertube.Item> hit = SEARCH_CACHE.get(key);
        if (hit != null) return hit;
        String path = "/api/v1/search?type=video&fields=" + LIST_FIELDS + "&q=" + URLEncoder.encode(q, "UTF-8");
        // Doğrudan YouTube araması (genelde <1 sn) baştan paralel başlar; sunucular yedek.
        Callable<List<Innertube.Item>> direct = new Callable<List<Innertube.Item>>() {
            @Override public List<Innertube.Item> call() throws Exception {
                List<Innertube.Item> r = Innertube.search(q);
                if (r.isEmpty()) throw new Soft("sonuç yok");
                return r;
            }
        };
        List<Innertube.Item> r = tryAll(c, path, 10000, new Parse<List<Innertube.Item>>() {
            @Override public List<Innertube.Item> run(String base, String body) throws Exception {
                List<Innertube.Item> l = parseItems(body);
                if (l.isEmpty()) throw new Soft("sonuç yok");
                return l;
            }
        }, direct);
        SEARCH_CACHE.put(key, r);
        return r;
    }

    /** Hesap akışı (giriş gerekir): FEwhat_to_watch, FEsubscriptions. */
    public static List<Innertube.Item> feed(Context c, String browseId) throws Exception {
        Auth.init(c);
        List<Innertube.Item> r = Innertube.browse(browseId);
        if (r.isEmpty()) throw new IOException("boş");
        return r;
    }

    /** Son başarılı popüler listesi (diskte); açılışta anında göstermek için. Yoksa null. */
    public static List<Innertube.Item> cachedPopular(Context c) {
        try {
            String s = prefs(c).getString("popular", null);
            if (s == null) return null;
            List<Innertube.Item> r = parseItems(s);
            return r.isEmpty() ? null : r;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Innertube.Item> popular(final Context c) throws Exception {
        return tryAll(c, "/api/v1/popular?fields=" + LIST_FIELDS, 8000, new Parse<List<Innertube.Item>>() {
            @Override public List<Innertube.Item> run(String base, String body) throws Exception {
                List<Innertube.Item> r = parseItems(body);
                if (r.isEmpty()) throw new Soft("boş");
                prefs(c).edit().putString("popular", body).apply();
                return r;
            }
        });
    }

    private static List<Innertube.Item> parseItems(String body) throws Exception {
        body = body.trim();
        if (!body.startsWith("[")) {
            throw new IOException(body.startsWith("{") ? new JSONObject(body).optString("error", "API kapalı") : "geçersiz yanıt");
        }
        JSONArray a = new JSONArray(body);
        List<Innertube.Item> r = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Innertube.Item it = item(a.getJSONObject(i));
            if (it != null) r.add(it);
        }
        return r;
    }

    private static Innertube.Item item(JSONObject o) {
        if (!"video".equals(o.optString("type", "video"))) return null;
        String id = o.optString("videoId");
        if (id.isEmpty()) return null;
        Innertube.Item it = new Innertube.Item(id, o.optString("title"), o.optString("author"),
                dur(o.optInt("lengthSeconds")));
        String vt = o.optString("viewCountText", "");
        if (vt.isEmpty() && o.has("viewCount")) vt = views(o.optLong("viewCount"));
        it.views = vt;
        it.published = o.optString("publishedText", "");
        return it;
    }

    // ---------------------------------------------------------------- video

    private static final LruCache<String, Video> VIDEO_CACHE = new LruCache<>(8);

    public static final int BOTH = 0, ONLY_DIRECT = 1, ONLY_SERVERS = 2;

    /** Oynatılamayan videonun önbelleğini sil (yoksa "Tekrar dene" aynı bozuk akışı verir). */
    public static void forget(String id) { VIDEO_CACHE.remove(id); }
    public static void forgetAll() { VIDEO_CACHE.evictAll(); }

    public static Video video(Context c, final String id) throws Exception { return video(c, id, BOTH); }

    /** Video bilgisi + akışlar. BOTH: doğrudan YouTube ve sunucular yarışır; ilk gelen kazanır. */
    public static Video video(Context c, final String id, int mode) throws Exception {
        if (mode == BOTH) {
            Video hit = VIDEO_CACHE.get(id);
            if (hit != null) return hit;
        }
        Innertube.clientPref = prefs(c).getInt("client", 0);
        Callable<Video> direct = new Callable<Video>() {
            @Override public Video call() throws Exception {
                Innertube.VInfo i = Innertube.player(id);
                Video v = new Video();
                v.id = i.id; v.title = i.title; v.author = i.author; v.formats = i.formats; v.source = "youtube";
                v.hls = i.hls; v.live = i.live; v.client = i.client; v.description = i.description; v.channelId = i.channelId; v.views = i.views; v.seconds = i.seconds;
                return v;
            }
        };
        Parse<Video> parse = new Parse<Video>() {
            @Override public Video run(String base, String body) throws Exception {
                return parseVideo(base, body);
            }
        };
        String path = "/api/v1/videos/" + id + "?local=true&fields=" + VIDEO_FIELDS;
        Video v;
        if (mode == BOTH && Innertube.directBroken()) {
            try { v = tryAll(c, path, 9000, parse, null, 600, 0); }
            catch (Exception ex) { Dbg.log("yedek sunucular yok, doğrudan deneniyor"); v = direct.call(); }
        }
        else if (mode == ONLY_DIRECT) v = direct.call();
        else if (mode == ONLY_SERVERS) v = tryAll(c, path, 9000, parse, null, 600, 0);
        else v = tryAll(c, path, 9000, parse, direct, 120, 0);
        VIDEO_CACHE.put(id, v);
        return v;
    }

    /** Shorts için hızlı yol: sunucu yarışı yok, doğrudan YouTube'a 3 istemci; önbellek kullanılır. */
    public static Video videoFast(Context c, String id) throws Exception {
        Video hit = VIDEO_CACHE.get(id);
        if (hit != null) return hit;
        Innertube.VInfo i = Innertube.playerFast(id, prefs(c).getInt("client", 0));
        Video v = new Video();
        v.id = i.id; v.title = i.title; v.author = i.author; v.formats = i.formats; v.source = "youtube";
                v.hls = i.hls; v.live = i.live; v.description = i.description; v.channelId = i.channelId; v.views = i.views; v.seconds = i.seconds;
        VIDEO_CACHE.put(id, v);
        return v;
    }

    /** Yorumlar: doğrudan YouTube ve sunucular yarışır. token boşsa ilk sayfa. */
    public static Innertube.Comments comments(Context c, final String id, final String token) throws Exception {
        Callable<Innertube.Comments> direct = new Callable<Innertube.Comments>() {
            @Override public Innertube.Comments call() throws Exception { return Innertube.comments(id, token); }
        };
        // sunucular yalnızca ilk sayfada (belirteç biçimleri farklı)
        if (token != null && !token.isEmpty()) return direct.call();
        return tryAll(c, "/api/v1/comments/" + id, 12000, new Parse<Innertube.Comments>() {
            @Override public Innertube.Comments run(String base, String body) throws Exception {
                JSONObject j = new JSONObject(body);
                if (j.has("error")) throw new IOException(j.optString("error"));
                JSONArray a = j.optJSONArray("comments");
                if (a == null || a.length() == 0) throw new IOException("yorum yok");
                Innertube.Comments r = new Innertube.Comments();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Innertube.Cm m = new Innertube.Cm();
                    m.author = o.optString("author");
                    m.text = o.optString("content");
                    m.time = o.optString("publishedText");
                    long lk = o.optLong("likeCount");
                    m.likes = lk > 0 ? String.valueOf(lk) : "";
                    if (!m.text.isEmpty()) r.list.add(m);
                }
                if (r.list.isEmpty()) throw new IOException("yorum yok");
                return r; // Invidious'ta sonraki sayfa yok
            }
        }, direct, 120, 0);
    }

    public static Innertube.Chan channel(String id) throws Exception { return Innertube.channel(id); }

    private static Video parseVideo(String base, String body) throws Exception {
        JSONObject j = new JSONObject(body);
        if (j.has("error")) throw new IOException(j.optString("error"));
        Video v = new Video();
        v.id = j.optString("videoId");
        v.title = j.optString("title");
        v.author = j.optString("author");
        v.description = j.optString("description");
        v.channelId = j.optString("authorId");
        v.hls = j.optString("hlsUrl");
        v.live = j.optBoolean("liveNow", false);
        v.views = j.optLong("viewCount");
        v.likes = j.optLong("likeCount");
        v.seconds = j.optInt("lengthSeconds");
        v.source = host(base);
        addFormats(j.optJSONArray("formatStreams"), true, base, v.formats);
        addFormats(j.optJSONArray("adaptiveFormats"), false, base, v.formats);
        if (v.formats.isEmpty()) throw new IOException("akış yok");
        JSONArray rec = j.optJSONArray("recommendedVideos");
        if (rec != null) {
            for (int i = 0; i < rec.length(); i++) {
                Innertube.Item it = item(rec.getJSONObject(i));
                if (it != null) v.related.add(it);
            }
        }
        return v;
    }

    private static void addFormats(JSONArray arr, boolean muxed, String base, List<Innertube.Fmt> out) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject f = arr.getJSONObject(i);
            String url = f.optString("url", "");
            if (url.isEmpty()) continue;
            if (url.startsWith("/")) url = base + url;
            String type = f.optString("type", "");
            boolean isV = type.startsWith("video");
            int w = 0, h = 0;
            String sz = f.optString("size", "");
            if (sz.contains("x")) {
                try {
                    String[] p = sz.split("x");
                    w = Integer.parseInt(p[0]);
                    h = Integer.parseInt(p[1]);
                } catch (Exception ignored) {}
            }
            String ql = f.optString("qualityLabel", f.optString("resolution", ""));
            int d = 0;
            while (d < ql.length() && Character.isDigit(ql.charAt(d))) d++;
            if (d > 0) { try { h = Integer.parseInt(ql.substring(0, d)); } catch (Exception ignored) {} }
            int fps = f.optInt("fps");
            int br = 0;
            try { br = Integer.parseInt(f.optString("bitrate", "0")); } catch (Exception ignored) {}
            long size = 0;
            try { size = Long.parseLong(f.optString("clen", "0")); } catch (Exception ignored) {}
            out.add(new Innertube.Fmt(url, type, w, h, fps, br, size, isV, muxed || type.startsWith("audio")));
        }
    }

    // ---------------------------------------------------------------- yardımcılar

    public static String dur(int s) {
        if (s <= 0) return "";
        int h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }

    public static String views(long v) {
        if (v >= 1_000_000_000L) return String.format("%.1fMr izlenme", v / 1e9);
        if (v >= 1_000_000L) return String.format("%.1fM izlenme", v / 1e6);
        if (v >= 1_000L) return String.format("%.1fB izlenme", v / 1e3);
        return v + " izlenme";
    }
}
