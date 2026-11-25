package com.hafif.tube;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * YouTube'un dahili (Innertube) API'sine doğrudan konuşur. Dış kütüphane yok.
 * UYARI: Resmi API değil; YouTube istemci kurallarını değiştirirse
 * VR_VERSION / VR_UA sabitlerini güncellemek gerekebilir.
 */
public final class Innertube {
    private Innertube() {}

    public static class Item {
        public final String id, title, author, duration;
        public String views = "", published = "";
        public Item(String id, String title, String author, String duration) {
            this.id = id; this.title = title; this.author = author; this.duration = duration;
        }

        /** Shorts mu: Innertube "short" işaretledi ya da süre <= 60 sn (Invidious sonuçları işaretlemez). */
        public boolean isShort() {
            if ("short".equals(duration)) return true;
            if (duration == null || duration.isEmpty()) return false;
            String[] p = duration.split(":");
            if (p.length != 2) return false;
            try { return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]) <= 60; }
            catch (NumberFormatException e) { return false; }
        }
    }

    public static class Fmt {
        public final String url, mime;
        public final int w, h, fps, bitrate;
        public final long size;
        public final boolean hasVideo, hasAudio;
        public boolean hasTrack, orig, def, dub; // ses dili bilgisi (otomatik dublaj ayıklamak için)

        public Fmt(String url, String mime, int w, int h, int fps, int bitrate, long size,
                   boolean hasVideo, boolean hasAudio) {
            this.url = url; this.mime = mime; this.w = w; this.h = h; this.fps = fps;
            this.bitrate = bitrate; this.size = size; this.hasVideo = hasVideo; this.hasAudio = hasAudio;
        }

        public String mimeBase() {
            int i = mime.indexOf(';');
            return i < 0 ? mime : mime.substring(0, i);
        }

        public boolean isMp4() { return mime.contains("mp4"); }
        public boolean isAvc() { return mime.contains("avc1"); }
        public boolean isVp9() { return mime.contains("vp9") || mime.contains("vp09"); }
        public boolean isAv1() { return mime.contains("av01"); }
        public boolean isAac() { return mime.contains("mp4a"); }
        public boolean isOpus() { return mime.contains("opus"); }

        public String ext() {
            if (mime.contains("webm")) return "webm";
            if (mime.contains("mp4")) return hasVideo ? "mp4" : "m4a";
            return "bin";
        }

        /** MediaMuxer ile birleştirilebilir mi: H.264 video veya AAC ses (mp4 kabı). */
        public boolean muxable() {
            return isMp4() && (mime.contains("avc1") || mime.contains("mp4a"));
        }
    }

    public static class VInfo {
        public final String id, title, author;
        public final List<Fmt> formats;
        public String description = "", channelId = "", hls = "";
        public int client;
        public boolean live;
        public long views;
        public int seconds;
        public VInfo(String id, String title, String author, List<Fmt> formats) {
            this.id = id; this.title = title; this.author = author; this.formats = formats;
        }
    }

    private static final String BASE = "https://www.youtube.com/youtubei/v1";

    // Oynatma için ANDROID_VR istemcisi: şifreli imza olmadan doğrudan URL veriyor.
    private static final String VR_VERSION = "1.60.19";
    public static final String VR_UA = "com.google.android.apps.youtube.vr.oculus/" + VR_VERSION
            + " (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip";

    private static final String WEB_VERSION = "2.20250101.00.00";
    private static final String WEB_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";

    private static final Pattern ID_RE =
            Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/|live/)([A-Za-z0-9_-]{11})");
    private static final Pattern BARE_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    /** Link, shorts linki ya da çıplak ID'den video ID'sini çıkarır; bulamazsa null. */
    public static String parseId(String s) {
        String t = s.trim();
        Matcher m = ID_RE.matcher(t);
        if (m.find()) return m.group(1);
        if (BARE_ID.matcher(t).matches()) return t;
        return null;
    }

    private static JSONObject post(String path, JSONObject body, String ua, Map<String, String> headers)
            throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/" + path + "?prettyPrint=false").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(7000);
        c.setReadTimeout(12000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", ua);
        c.setRequestProperty("Accept-Encoding", "gzip");
        c.setRequestProperty("Origin", "https://www.youtube.com");
        for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();
        if (c.getResponseCode() != 200) throw new RuntimeException("HTTP " + c.getResponseCode());
        InputStream in = c.getInputStream();
        if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        in.close();
        return new JSONObject(out.toString("UTF-8"));
    }

    private static JSONObject dig(JSONObject o, String... keys) {
        for (String k : keys) {
            if (o == null) return null;
            o = o.optJSONObject(k);
        }
        return o;
    }

    private static String text(JSONObject o) throws Exception {
        if (o == null) return "";
        String s = o.optString("simpleText", "");
        if (!s.isEmpty()) return s;
        JSONArray runs = o.optJSONArray("runs");
        if (runs == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < runs.length(); i++) sb.append(runs.getJSONObject(i).optString("text"));
        return sb.toString();
    }

    private static JSONObject webClientBody() throws Exception {
        JSONObject client = new JSONObject()
                .put("clientName", "WEB").put("clientVersion", WEB_VERSION)
                .put("hl", "tr").put("gl", "TR");
        return new JSONObject().put("context", new JSONObject().put("client", client));
    }

    public static List<Item> search(String q) throws Exception {
        JSONObject body = webClientBody().put("query", q);
        return parseList(post("search", body, WEB_UA, Auth.headers()));
    }

    /** Hesap akışları: FEwhat_to_watch (öneriler), FEsubscriptions (abonelikler). Giriş gerekir. */
    public static List<Item> browse(String browseId) throws Exception {
        JSONObject body = webClientBody().put("browseId", browseId);
        return parseList(post("browse", body, WEB_UA, Auth.headers()));
    }

    private static List<Item> parseList(JSONObject j) throws Exception {
        List<Item> res = new ArrayList<>();
        collect(j, res, new java.util.HashSet<String>(), 0);
        return res;
    }

    /** Yanıtın her yerinde video kartı arar (YouTube düzeni değişse de çalışsın diye yerleşimden bağımsız). */
    private static void collect(Object node, List<Item> out, java.util.Set<String> seen, int depth) throws Exception {
        if (node == null || depth > 45) return;
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collect(a.opt(i), out, seen, depth + 1);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject o = (JSONObject) node;
        JSONObject v = o.optJSONObject("videoRenderer");
        if (v == null) v = o.optJSONObject("gridVideoRenderer");
        if (v == null) v = o.optJSONObject("compactVideoRenderer");
        if (v != null) {
            String id = v.optString("videoId");
            if (id.length() == 11 && seen.add(id)) {
                JSONObject owner = v.optJSONObject("ownerText");
                if (owner == null) owner = v.optJSONObject("longBylineText");
                if (owner == null) owner = v.optJSONObject("shortBylineText");
                Item it = new Item(id, text(v.optJSONObject("title")), text(owner), text(v.optJSONObject("lengthText")));
                it.views = text(v.optJSONObject("shortViewCountText"));
                if (it.views.isEmpty()) it.views = text(v.optJSONObject("viewCountText"));
                it.published = text(v.optJSONObject("publishedTimeText"));
                out.add(it);
            }
            return;
        }
        JSONObject l = o.optJSONObject("lockupViewModel");
        if (l != null) {
            lockup(l, out, seen);
            return;
        }
        JSONObject r = o.optJSONObject("reelItemRenderer");
        if (r != null) {
            String id = r.optString("videoId");
            if (id.length() == 11 && seen.add(id)) out.add(new Item(id, text(r.optJSONObject("headline")), "Shorts", "short"));
            return;
        }
        JSONObject sl = o.optJSONObject("shortsLockupViewModel");
        if (sl != null) {
            String id = findKey(sl, "videoId", 0);
            if (id != null && id.length() == 11 && seen.add(id)) {
                String title = "";
                JSONObject om = dig(sl, "overlayMetadata", "primaryText");
                if (om != null) title = om.optString("content", "");
                out.add(new Item(id, title, "Shorts", "short"));
            }
            return;
        }
        java.util.Iterator<String> keys = o.keys();
        while (keys.hasNext()) collect(o.opt(keys.next()), out, seen, depth + 1);
    }

    private static void lockup(JSONObject l, List<Item> out, java.util.Set<String> seen) throws Exception {
        if (!l.optString("contentType").contains("VIDEO")) return;
        String id = l.optString("contentId");
        if (id.length() != 11 || !seen.add(id)) return;
        JSONObject meta = dig(l, "metadata", "lockupMetadataViewModel");
        String title = "";
        List<String> parts = new ArrayList<>();
        if (meta != null) {
            JSONObject t = meta.optJSONObject("title");
            if (t != null) title = t.optString("content", "");
            JSONObject cm = dig(meta, "metadata", "contentMetadataViewModel");
            JSONArray rows = cm == null ? null : cm.optJSONArray("metadataRows");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    JSONArray ps = rows.getJSONObject(i).optJSONArray("metadataParts");
                    if (ps == null) continue;
                    for (int k = 0; k < ps.length(); k++) {
                        JSONObject tx = ps.getJSONObject(k).optJSONObject("text");
                        if (tx != null && !tx.optString("content", "").isEmpty()) parts.add(tx.optString("content"));
                    }
                }
            }
        }
        String dur = findDuration(l, 0);
        Item it = new Item(id, title, parts.size() > 0 ? parts.get(0) : "", dur == null ? "" : dur);
        if (parts.size() > 1) it.views = parts.get(1);
        if (parts.size() > 2) it.published = parts.get(2);
        out.add(it);
    }

    private static String findKey(Object node, String key, int depth) {
        if (node == null || depth > 20) return null;
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            String v = o.optString(key, "");
            if (!v.isEmpty() && !(o.opt(key) instanceof JSONObject)) return v;
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String r = findKey(o.opt(it.next()), key, depth + 1);
                if (r != null) return r;
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                String r = findKey(a.opt(i), key, depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static final Pattern DUR = Pattern.compile("^\\d{1,2}(:\\d{2}){1,2}$");

    /** Küçük resim rozetlerinde "3:45" gibi bir süre metni arar. */
    private static String findDuration(Object node, int depth) {
        if (node == null || depth > 20) return null;
        if (node instanceof String) return DUR.matcher((String) node).matches() ? (String) node : null;
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String r = findDuration(o.opt(it.next()), depth + 1);
                if (r != null) return r;
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                String r = findDuration(a.opt(i), depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** YouTube'un otomatik dublaj (örn. Arapça) ses yollarını ayıklar; orijinal/varsayılan dil kalır. */
    private static void pruneTracks(List<Fmt> fmts) {
        boolean anyOrig = false, anyDef = false, anyNonDub = false;
        for (Fmt f : fmts) {
            if (!f.hasAudio) continue;
            if (f.orig) anyOrig = true;
            if (f.hasTrack && f.def && !f.dub) anyDef = true;
            if (!f.dub) anyNonDub = true;
        }
        if (!anyNonDub && !anyOrig && !anyDef) return;
        List<Fmt> keep = new ArrayList<>();
        for (Fmt f : fmts) {
            if (!f.hasAudio) { keep.add(f); continue; }
            boolean ok;
            if (anyOrig) ok = f.orig || (!f.hasTrack && !f.dub);
            else if (anyDef) ok = !f.hasTrack || (f.def && !f.dub);
            else ok = !f.dub;
            if (ok) keep.add(f);
        }
        Dbg.log("ses yolu ayıklama: " + fmts.size() + " -> " + keep.size());
        fmts.clear();
        fmts.addAll(keep);
    }

    private static void addFormats(JSONArray arr, boolean muxed, List<Fmt> out, String pot) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject f = arr.getJSONObject(i);
            String url = f.optString("url", "");
            if (url.isEmpty()) continue; // imzalı (cipher) akışları şimdilik atla
            if (pot != null) url = url + "&pot=" + java.net.URLEncoder.encode(pot, "UTF-8");
            String mime = f.optString("mimeType", "");
            boolean isV = mime.startsWith("video");
            long size = 0;
            try { size = Long.parseLong(f.optString("contentLength", "0")); } catch (NumberFormatException ignored) {}
            Fmt nf = new Fmt(url, mime, f.optInt("width"), f.optInt("height"), f.optInt("fps"),
                    f.optInt("averageBitrate", f.optInt("bitrate")), size,
                    isV, muxed || mime.startsWith("audio"));
            JSONObject at = f.optJSONObject("audioTrack");
            String xt = f.optString("xtags", "");
            if (at != null) {
                nf.hasTrack = true;
                nf.def = at.optBoolean("audioIsDefault", false);
                String did = at.optString("id", "") + " " + at.optString("displayName", "");
                nf.dub = xt.contains("dubbed") || did.toLowerCase().contains("dubbed");
            }
            nf.orig = xt.contains("acont=original") || xt.contains("acont%3Doriginal");
            out.add(nf);
        }
    }

    /** Seçilebilir istemciler (Morphe/ReVanced "Video akışlarını taklit et" listesi gibi). 0 = Otomatik. */
    public static final String[] CLIENT_NAMES = {
            "Otomatik", "Android VR", "TV", "TV Simply", "Android Music (SDK'sız)", "iOS", "visionOS"};
    public static volatile int clientPref = 0;
    static volatile android.content.Context appCtx;

    private static int maxH(VInfo v) {
        int m = 0;
        for (Fmt f : v.formats) if (f.hasVideo && f.h > m) m = f.h;
        return m;
    }

    /** Seçili istemci önce denenir; başarısız olursa Otomatik'e düşer. Otomatik: yarışır, ama 720p+ veren kazanır. */
    private static final java.util.Map<String, java.util.Set<Integer>> BAD =
            new java.util.concurrent.ConcurrentHashMap<String, java.util.Set<Integer>>();

    /** Bu istemcinin adresleri 403 verdi: bu video için bir daha seçilmesin. */
    /** Bu oturumda doğrudan YouTube akışları 403 / kesik verdi: sonraki videolarda yedek sunucular öne alınır. */
    public static volatile long directBadUntil;

    public static boolean directBroken() { return System.currentTimeMillis() < directBadUntil; }

    public static void markBad(String id, int client) {
        java.util.Set<Integer> st = BAD.get(id);
        if (st == null) { st = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Integer, Boolean>()); BAD.put(id, st); }
        st.add(client);
    }

    public static int badCount(String id) { java.util.Set<Integer> st = BAD.get(id); return st == null ? 0 : st.size(); }

    public static VInfo player(final String id) throws Exception {
        final java.util.Set<Integer> bad = BAD.get(id);
        if (clientPref > 0 && (bad == null || !bad.contains(clientPref))) {
            try {
                VInfo v = playerWith(id, clientPref);
                if (maxH(v) > 0) return v;
            } catch (Exception ignored) {}
        }
        int[] all = {1, 2, 3, 7, 5, 6, 4};
        java.util.List<Integer> okl = new ArrayList<Integer>();
        for (int c : all) if (bad == null || !bad.contains(c)) okl.add(c);
        if (okl.isEmpty()) { BAD.remove(id); for (int c : all) okl.add(c); }
        final int[] order = new int[okl.size()];
        for (int i = 0; i < order.length; i++) order[i] = okl.get(i);
        final java.util.concurrent.LinkedBlockingQueue<Object> q = new java.util.concurrent.LinkedBlockingQueue<Object>();
        for (int k = 0; k < order.length; k++) {
            final int kk = order[k];
            new Thread(new Runnable() {
                @Override public void run() {
                    try { q.put(playerWith(id, kk)); } catch (Exception e) { try { q.put(e); } catch (InterruptedException ignored) {} }
                }
            }).start();
        }
        // iOS akışları (5) doğrulama jetonu isteyince yarıda kesiliyor; jeton istemeyen istemciler (TV, VR, Müzik) tercih edilir.
        Exception first = null;
        VInfo best = null;
        long start = System.currentTimeMillis(), deadline = start + 15000;
        int got = 0;
        while (got < order.length) {
            long left = deadline - System.currentTimeMillis();
            // iyi bir sonuç varsa en fazla 4 sn daha tercih edilen istemcileri bekle
            if (best != null) left = Math.min(left, Math.max(0, start + 4000 - System.currentTimeMillis()));
            Object o = left <= 0 ? null : q.poll(left, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (o == null) break;
            got++;
            if (o instanceof VInfo) {
                VInfo v = (VInfo) o;
                if (best == null || rank(curPri(), v.client) < rank(curPri(), best.client)) best = v;
                if (rank(curPri(), v.client) == 0 && maxH(v) >= 720) return v; // en tercih edilen: hemen başla
            } else if (first == null) first = (Exception) o;
        }
        if (best != null) { Dbg.log("seçilen istemci: " + best.client); return best; }
        throw first != null ? first : new RuntimeException("Akış bulunamadı");
    }

    private static final int[] PRI_POT = {5, 2, 3, 7, 1, 4, 6}, PRI_NO = {2, 3, 7, 1, 4, 6, 5};

    private static int[] curPri() { return PoToken.ready() ? PRI_POT : PRI_NO; }

    private static int rank(int[] pri, int k) {
        for (int i = 0; i < pri.length; i++) if (pri[i] == k) return i;
        return pri.length;
    }

    /** Hızlı mod (Shorts): en çok 3 istemci yarışır, oynatılabilir akış veren ilk istemci kazanır. Çözünürlük beklenmez. */
    public static VInfo playerFast(final String id, int pref) throws Exception {
        int[] order = pref > 0 && pref != 1 && pref != 5
                ? new int[]{pref, 1, 5} : pref == 5 ? new int[]{5, 1, 3} : new int[]{1, 5, 3};
        final java.util.concurrent.LinkedBlockingQueue<Object> q = new java.util.concurrent.LinkedBlockingQueue<Object>();
        for (int k = 0; k < order.length; k++) {
            final int kk = order[k];
            new Thread(new Runnable() {
                @Override public void run() {
                    try { q.put(playerWith(id, kk)); } catch (Exception e) { try { q.put(e); } catch (InterruptedException ignored) {} }
                }
            }).start();
        }
        Exception first = null;
        long deadline = System.currentTimeMillis() + 10000;
        for (int i = 0; i < order.length; i++) {
            long left = deadline - System.currentTimeMillis();
            Object o = left <= 0 ? null : q.poll(left, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (o == null) break;
            if (o instanceof VInfo) { if (maxH((VInfo) o) > 0) return (VInfo) o; }
            else if (first == null) first = (Exception) o;
        }
        throw first != null ? first : new RuntimeException("Akış bulunamadı");
    }

    private static final Map<String, String> UA_OF = new java.util.concurrent.ConcurrentHashMap<String, String>();
    /** Akış URL'sini üreten istemcinin User-Agent'ı (googlevideo aynı UA'yı bekler). */
    public static String uaFor(String url) {
        String u = UA_OF.get(url);
        return u == null ? VR_UA : u;
    }

    private static final Map<Integer, String> INFO = new java.util.concurrent.ConcurrentHashMap<Integer, String>();

    /** Ziyaretçi kimliği (PO token buna bağlanır). */
    static String visitorData() throws Exception {
        try {
            JSONObject j = post("visitor_id", webClientBody(), WEB_UA, new HashMap<String, String>());
            String v = findKey(j, "visitorData", 0);
            if (v != null && !v.isEmpty()) return v;
        } catch (Exception ignored) {}
        JSONObject j = post("browse", webClientBody().put("browseId", "FEwhat_to_watch"), WEB_UA, new HashMap<String, String>());
        return findKey(j, "visitorData", 0);
    }

    /** Tanılama: her istemciyi dener, akış adresine küçük bir test isteği (ilk bayt + 2. MB) atar; sonuç satırları döner. */
    public static List<String> diagnose(final String id) {
        final String[] names = {"", "VR", "TV", "TVsimply", "Music", "iOS", "visionOS", "TVgömülü"};
        final String[] res = new String[8];
        List<Thread> ts = new ArrayList<>();
        for (int k = 1; k <= 7; k++) {
            final int kk = k;
            Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    StringBuilder sb = new StringBuilder("tanı " + names[kk] + ": ");
                    try {
                        VInfo v = playerWith0(id, kk);
                        Fmt pick = null;
                        for (Fmt f : v.formats) if (f.hasVideo && f.hasAudio) { pick = f; break; }
                        if (pick == null) for (Fmt f : v.formats) if (f.hasVideo && f.isAvc() && (pick == null || f.h < pick.h)) pick = f;
                        sb.append("tamam url=").append(v.formats.size()).append(' ').append(INFO.get(kk));
                        if (pick != null) {
                            String ua = uaFor(pick.url);
                            sb.append(" | ").append(pick.h).append("p ilk=").append(probe(pick.url, ua, 0, 1023))
                              .append(" 2MB=").append(probe(pick.url, ua, 2000000, 2001023));
                            if (kk == 5) {
                                String np = pick.url.replaceAll("&pot=[^&]*", "");
                                sb.append(" | potsuz ilk=").append(probe(np, ua, 0, 1023))
                                  .append(" 2MB=").append(probe(np, ua, 2000000, 2001023))
                                  .append(" | potlu 1MB=").append(probe(pick.url, ua, 1000000, 1001023))
                                  .append(" 3MB=").append(probe(pick.url, ua, 3000000, 3001023))
                                  .append(" 3.5MB=").append(probe(pick.url, ua, 3500000, 3501023))
                                  .append(" 6MB=").append(probe(pick.url, ua, 6000000, 6001023));
                                StringBuilder keys = new StringBuilder();
                                int q = pick.url.indexOf('?');
                                if (q > 0) for (String kv : pick.url.substring(q + 1).split("&")) { int e2 = kv.indexOf('='); keys.append(e2 > 0 ? kv.substring(0, e2) : kv).append(','); }
                                Dbg.log("iOS adres anahtarları: " + keys);
                            }
                        } else if (!v.hls.isEmpty()) sb.append(" | yalnız HLS");
                    } catch (Exception e) {
                        String m = String.valueOf(e.getMessage());
                        sb.append("hata ").append(m.length() > 40 ? m.substring(0, 40) : m);
                    }
                    res[kk] = sb.toString();
                }
            });
            t.setDaemon(true);
            t.start();
            ts.add(t);
        }
        long end = System.currentTimeMillis() + 14000;
        for (Thread t : ts) {
            try { t.join(Math.max(1, end - System.currentTimeMillis())); } catch (InterruptedException ignored) {}
        }
        List<String> out = new ArrayList<>();
        for (int k = 1; k <= 7; k++) out.add(res[k] == null ? "tanı " + names[k] + ": zaman aşımı" : res[k]);
        return out;
    }

    private static String probe(String url, String ua, long a, long b) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url + "&range=" + a + "-" + b).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(6000);
            c.setRequestProperty("User-Agent", ua);
            int code = c.getResponseCode();
            long got = 0;
            if (code == 200 || code == 206) {
                InputStream in = c.getInputStream();
                byte[] buf = new byte[4096];
                int n;
                try { while ((n = in.read(buf)) > 0) got += n; } catch (Exception e) { return code + "/kesildi@" + got; }
                in.close();
                return code + "/" + got + "B";
            }
            return String.valueOf(code);
        } catch (Exception e) {
            return "hata";
        }
    }

    /** Bot doğrulaması istenirse (oturum açın) işaretlenir; arayüz giriş yapmayı önerir. */
    public static volatile boolean botCheck = false;

    private static VInfo playerWith(String id, int k) throws Exception {
        long t0 = System.currentTimeMillis();
        try {
            VInfo v = playerWith0(id, k);
            v.client = k;
            int mux = 0;
            for (Fmt f : v.formats) if (f.hasVideo && f.hasAudio) mux++;
            Dbg.log("istemci " + k + " tamam " + (System.currentTimeMillis() - t0) + "ms, " + v.formats.size() + " akış, " + mux + " tek parça");
            return v;
        } catch (Exception e) {
            String em = String.valueOf(e.getMessage()).toLowerCase();
            if (em.contains("oturum") || em.contains("bot") || em.contains("sign in")) botCheck = true;
            Dbg.log("istemci " + k + " hata " + (System.currentTimeMillis() - t0) + "ms: " + e.getMessage());
            throw e;
        }
    }

    private static VInfo playerWith0(String id, int k) throws Exception {
        JSONObject client = new JSONObject();
        String ua, num, ver;
        if (k == 1) { // Android VR
            ver = VR_VERSION; ua = VR_UA; num = "28";
            client.put("clientName", "ANDROID_VR").put("clientVersion", ver)
                    .put("deviceMake", "Oculus").put("deviceModel", "Quest 3")
                    .put("osName", "Android").put("osVersion", "12L").put("androidSdkVersion", 32);
        } else if (k == 2) { // TV
            ver = "7.20250312.16.00"; num = "7";
            ua = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version";
            client.put("clientName", "TVHTML5").put("clientVersion", ver);
        } else if (k == 3) { // TV Simply
            ver = "1.0"; num = "75";
            ua = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15";
            client.put("clientName", "TVHTML5_SIMPLY").put("clientVersion", ver);
        } else if (k == 4) { // Android Music, androidSdkVersion yok
            ver = "7.29.52"; num = "21";
            ua = "com.google.android.apps.youtube.music/" + ver + " (Linux; U; Android 11) gzip";
            client.put("clientName", "ANDROID_MUSIC").put("clientVersion", ver)
                    .put("osName", "Android").put("osVersion", "11");
        } else if (k == 5) { // iOS
            ver = "20.10.4"; num = "5";
            ua = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)";
            client.put("clientName", "IOS").put("clientVersion", ver)
                    .put("deviceMake", "Apple").put("deviceModel", "iPhone16,2")
                    .put("osName", "iPhone").put("osVersion", "18.3.2.22D82");
        } else if (k == 7) { // TV gömülü
            ver = "2.0"; num = "85";
            ua = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15";
            client.put("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER").put("clientVersion", ver);
        } else { // visionOS
            ver = "0.1"; num = "101";
            ua = "com.google.ios.youtube/0.1 (Apple Vision Pro; U; CPU iOS 17_4 like Mac OS X)";
            client.put("clientName", "VISIONOS").put("clientVersion", ver)
                    .put("deviceMake", "Apple").put("deviceModel", "RealityDevice14,1")
                    .put("osName", "iOS").put("osVersion", "17.4");
        }
        client.put("hl", "tr").put("gl", "TR");
        String pot = null;
        if (k == 5) {
            pot = appCtx == null ? null : PoToken.await(appCtx, 12000);
            if (pot != null) client.put("visitorData", PoToken.visitorData);
            else Dbg.log("pot yok: iOS akışı jetonsuz");
        }
        JSONObject ctx = new JSONObject().put("client", client);
        String playerPot = null;
        if (k == 5 && pot != null) {
            playerPot = PoToken.mintFor(id, 6000); // oynatıcı tokenı: video kimliğine bağlı
            Dbg.log("oynatıcı pot: " + (playerPot == null ? "yok" : playerPot.length() + " karakter"));
        }
        if (k == 7) ctx.put("thirdParty", new JSONObject().put("embedUrl", "https://www.youtube.com/"));
        JSONObject body = new JSONObject()
                .put("context", ctx)
                .put("videoId", id).put("contentCheckOk", true).put("racyCheckOk", true);
        if (playerPot != null) body.put("serviceIntegrityDimensions", new JSONObject().put("poToken", playerPot));
        Map<String, String> h = new HashMap<>();
        h.put("X-YouTube-Client-Name", num);
        h.put("X-YouTube-Client-Version", ver);
        if (k == 5 && pot != null && PoToken.visitorData != null) h.put("X-Goog-Visitor-Id", PoToken.visitorData);
        if (k == 2 || k == 3 || k == 7) h.putAll(Auth.headers()); // girişli hesap: TV istemcisi bot kontrolünü geçer
        JSONObject j = post("player", body, ua, h);

        JSONObject st = j.optJSONObject("playabilityStatus");
        if (st == null || !"OK".equals(st.optString("status"))) {
            throw new RuntimeException(st == null ? "Video oynatılamıyor" : st.optString("reason", "Video oynatılamıyor"));
        }
        JSONObject sd = j.optJSONObject("streamingData");
        if (sd == null) throw new RuntimeException("Akış verisi yok");
        int cipher = 0;
        for (String an : new String[]{"formats", "adaptiveFormats"}) {
            JSONArray ar = sd.optJSONArray(an);
            if (ar != null) for (int ci = 0; ci < ar.length(); ci++) {
                JSONObject fo = ar.optJSONObject(ci);
                if (fo != null && fo.optString("url", "").isEmpty()) cipher++;
            }
        }
        INFO.put(k, "cipher/urlsiz=" + cipher + " sabr=" + (sd.has("serverAbrStreamingUrl") ? "var" : "yok")
                + " hls=" + (sd.optString("hlsManifestUrl", "").isEmpty() ? "yok" : "var"));
        List<Fmt> fmts = new ArrayList<>();
        addFormats(sd.optJSONArray("formats"), true, fmts, pot);
        addFormats(sd.optJSONArray("adaptiveFormats"), false, fmts, pot);
        String hls = sd.optString("hlsManifestUrl", "");
        if (fmts.isEmpty() && hls.isEmpty()) throw new RuntimeException("Akış bulunamadı");
        if (!hls.isEmpty()) UA_OF.put(hls, ua);
        pruneTracks(fmts);
        for (Fmt ff : fmts) UA_OF.put(ff.url, ua);
        JSONObject vd = j.optJSONObject("videoDetails");
        VInfo vi = new VInfo(id, vd == null ? id : vd.optString("title", id),
                vd == null ? "" : vd.optString("author", ""), fmts);
        vi.hls = hls;
        if (vd != null) {
            vi.live = vd.optBoolean("isLive", false) || (vd.optBoolean("isLiveContent", false) && !hls.isEmpty());
            Dbg.log("canlı=" + vi.live + " hls=" + (hls.isEmpty() ? "yok" : "var") + " akış=" + fmts.size());
            vi.description = vd.optString("shortDescription", "");
            vi.channelId = vd.optString("channelId", "");
            try { vi.views = Long.parseLong(vd.optString("viewCount", "0")); } catch (NumberFormatException ignored) {}
            try { vi.seconds = Integer.parseInt(vd.optString("lengthSeconds", "0")); } catch (NumberFormatException ignored) {}
        }
        return vi;
    }

    /** İlgili videolar: "next" yanıtındaki video kartları. */
    public static List<Item> related(String videoId) throws Exception {
        return parseList(post("next", webClientBody().put("videoId", videoId), WEB_UA, Auth.headers()));
    }

    // ------------------------------------------------------------------ yorumlar

    public static class Cm {
        public String author = "", text = "", likes = "", time = "";
    }

    public static class Comments {
        public final List<Cm> list = new ArrayList<>();
        public String next = ""; // sonraki sayfa belirteci
    }

    /** İlk sayfa: "next" ile yorum belirtecini bul, sonra yorumları çek. token verilirse doğrudan o sayfa. */
    public static Comments comments(String videoId, String token) throws Exception {
        if (token == null || token.isEmpty()) {
            JSONObject j = post("next", webClientBody().put("videoId", videoId), WEB_UA, Auth.headers());
            token = commentToken(j);
            if (token == null) throw new RuntimeException("Yorumlar kapalı ya da bulunamadı");
        }
        JSONObject j = post("next", webClientBody().put("continuation", token), WEB_UA, Auth.headers());
        Comments c = new Comments();
        // yeni düzen: varlık güncellemeleri
        JSONObject fu = j.optJSONObject("frameworkUpdates");
        JSONObject eb = fu == null ? null : fu.optJSONObject("entityBatchUpdate");
        JSONArray mu = eb == null ? null : eb.optJSONArray("mutations");
        if (mu != null) {
            for (int i = 0; i < mu.length(); i++) {
                JSONObject p = dig(mu.optJSONObject(i), "payload", "commentEntityPayload");
                if (p == null) continue;
                Cm m = new Cm();
                JSONObject pr = p.optJSONObject("properties");
                if (pr != null) {
                    JSONObject ct = pr.optJSONObject("content");
                    m.text = ct == null ? "" : ct.optString("content", "");
                    m.time = pr.optString("publishedTime", "");
                }
                JSONObject au = p.optJSONObject("author");
                m.author = au == null ? "" : au.optString("displayName", "");
                JSONObject tb = p.optJSONObject("toolbar");
                m.likes = tb == null ? "" : tb.optString("likeCountNotliked", "");
                if (m.likes.equals("0")) m.likes = "";
                if (!m.text.isEmpty()) {
                    JSONObject pp = pr;
                    String lvl = pp == null ? "0" : pp.optString("replyLevel", "0");
                    if (lvl.equals("0")) c.list.add(m); // yalnızca üst düzey yorumlar
                }
            }
        }
        if (c.list.isEmpty()) collectOldComments(j, c.list, 0);
        List<String> toks = new ArrayList<>();
        collectTokens(j, toks, 0);
        if (!toks.isEmpty()) c.next = toks.get(toks.size() - 1);
        if (c.list.isEmpty()) throw new RuntimeException("Yorum yok");
        return c;
    }

    private static String commentToken(JSONObject j) {
        JSONObject sec = findSection(j, "sectionIdentifier", "comment-item-section", 0);
        if (sec != null) { String t = findKey(sec, "token", 0); if (t != null) return t; }
        JSONObject pan = findSection(j, "panelIdentifier", "engagement-panel-comments-section", 0);
        if (pan != null) { String t = findKey(pan, "token", 0); if (t != null) return t; }
        return null;
    }

    private static JSONObject findSection(Object node, String key, String val, int depth) {
        if (node == null || depth > 40) return null;
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) { JSONObject r = findSection(a.opt(i), key, val, depth + 1); if (r != null) return r; }
            return null;
        }
        if (!(node instanceof JSONObject)) return null;
        JSONObject o = (JSONObject) node;
        if (val.equals(o.optString(key, null))) return o;
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) { JSONObject r = findSection(o.opt(it.next()), key, val, depth + 1); if (r != null) return r; }
        return null;
    }

    private static void collectTokens(Object node, List<String> out, int depth) {
        if (node == null || depth > 40) return;
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collectTokens(a.opt(i), out, depth + 1);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject o = (JSONObject) node;
        JSONObject ci = o.optJSONObject("continuationItemRenderer");
        if (ci != null) { String t = findKey(ci, "token", 0); if (t != null) out.add(t); return; }
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) collectTokens(o.opt(it.next()), out, depth + 1);
    }

    private static void collectOldComments(Object node, List<Cm> out, int depth) {
        if (node == null || depth > 40) return;
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collectOldComments(a.opt(i), out, depth + 1);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject o = (JSONObject) node;
        JSONObject cr = o.optJSONObject("commentRenderer");
        if (cr != null) {
            try {
                Cm m = new Cm();
                m.author = text(cr.optJSONObject("authorText"));
                m.text = text(cr.optJSONObject("contentText"));
                m.time = text(cr.optJSONObject("publishedTimeText"));
                m.likes = text(cr.optJSONObject("voteCount"));
                if (!m.text.isEmpty()) out.add(m);
            } catch (Exception ignored) {}
            return;
        }
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) collectOldComments(o.opt(it.next()), out, depth + 1);
    }

    // ------------------------------------------------------------------ kanallar

    public static class Chan {
        public String id = "", name = "", subs = "", desc = "";
        public List<Item> videos = new ArrayList<>();
    }

    /** Kanal sayfası (Videolar sekmesi). */
    public static Chan channel(String channelId) throws Exception {
        JSONObject body = webClientBody().put("browseId", channelId).put("params", "EgZ2aWRlb3PyBgQKAjoA");
        JSONObject j = post("browse", body, WEB_UA, Auth.headers());
        Chan c = new Chan();
        c.id = channelId;
        JSONObject md = dig(j, "metadata", "channelMetadataRenderer");
        if (md != null) { c.name = md.optString("title", ""); c.desc = md.optString("description", ""); }
        c.subs = findSubs(j, 0);
        List<Item> raw = parseList(j);
        for (Item it : raw) {
            if (it.isShort()) continue;
            Item n = new Item(it.id, it.title, c.name, it.duration);
            n.views = it.views; n.published = it.published;
            c.videos.add(n);
        }
        if (c.name.isEmpty()) {
            String t = findKey(dig(j, "header") == null ? j : dig(j, "header"), "title", 0);
            if (t != null) c.name = t;
        }
        return c;
    }

    private static String findSubs(Object node, int depth) {
        if (node == null || depth > 30) return "";
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) { String r = findSubs(a.opt(i), depth + 1); if (!r.isEmpty()) return r; }
            return "";
        }
        if (!(node instanceof JSONObject)) return "";
        JSONObject o = (JSONObject) node;
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = o.opt(k);
            if (v instanceof String) {
                String sv = (String) v;
                if (sv.length() < 40 && (sv.contains("abone") || sv.toLowerCase().contains("subscriber"))) return sv;
            } else {
                String r = findSubs(v, depth + 1);
                if (!r.isEmpty()) return r;
            }
        }
        return "";
    }

    /** Giriş yapılmış hesabın abone olduğu kanallar. */
    public static List<Chan> subscriptions() throws Exception {
        JSONObject j = post("browse", webClientBody().put("browseId", "FEchannels"), WEB_UA, Auth.headers());
        List<Chan> out = new ArrayList<>();
        collectChans(j, out, new java.util.HashSet<String>(), 0);
        return out;
    }

    private static void collectChans(Object node, List<Chan> out, java.util.Set<String> seen, int depth) throws Exception {
        if (node == null || depth > 40) return;
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collectChans(a.opt(i), out, seen, depth + 1);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject o = (JSONObject) node;
        JSONObject cr = o.optJSONObject("channelRenderer");
        if (cr == null) cr = o.optJSONObject("gridChannelRenderer");
        if (cr != null) {
            String id = cr.optString("channelId");
            if (id.startsWith("UC") && seen.add(id)) {
                Chan c = new Chan();
                c.id = id;
                c.name = text(cr.optJSONObject("title"));
                c.subs = text(cr.optJSONObject("subscriberCountText"));
                if (c.subs.isEmpty()) c.subs = text(cr.optJSONObject("videoCountText"));
                out.add(c);
            }
            return;
        }
        JSONObject l = o.optJSONObject("lockupViewModel");
        if (l != null && l.optString("contentId", "").startsWith("UC")) {
            String id = l.optString("contentId");
            if (seen.add(id)) {
                Chan c = new Chan();
                c.id = id;
                JSONObject t = dig(l, "metadata", "lockupMetadataViewModel", "title");
                c.name = t == null ? "" : t.optString("content", "");
                out.add(c);
            }
            return;
        }
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) collectChans(o.opt(it.next()), out, seen, depth + 1);
    }
}
