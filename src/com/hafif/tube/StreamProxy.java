package com.hafif.tube;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * Yerel parçalı akış vekili. MediaPlayer 127.0.0.1'e bağlanır; vekil YouTube'dan (googlevideo)
 * küçük Range parçaları halinde veri çeker. Tek parça, uzun istekler yavaşlatıldığı için
 * (NewPipe'ın da yaptığı gibi) bu, başlangıcı hızlandırır ve ileri sarmayı düzgün çalıştırır.
 */
final class StreamProxy {
    private static final int CHUNK = 1536 * 1024;
    private static ServerSocket server;
    private static int port;
    static volatile boolean enabled = true;
    static volatile long last403, fired403, real403;
    static volatile int f403;
    static volatile Runnable on403;
    static volatile int fails = 0;

    /** Gerçek akış adresini yerel adrese çevirir. Vekil açılamazsa asıl adresi döner. */
    static synchronized String wrap(String url, String ua) {
        if (!enabled) return url;
        try {
            if (server == null) {
                server = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
                port = server.getLocalPort();
                Thread t = new Thread(new Runnable() {
                    @Override public void run() { acceptLoop(); }
                }, "stream-proxy");
                t.setDaemon(true);
                t.start();
            }
            return "http://127.0.0.1:" + port + "/s?u=" + URLEncoder.encode(url, "UTF-8")
                    + "&ua=" + URLEncoder.encode(ua == null ? "" : ua, "UTF-8");
        } catch (Exception e) {
            return url;
        }
    }

    private static void acceptLoop() {
        while (true) {
            try {
                final Socket s = server.accept();
                Thread t = new Thread(new Runnable() {
                    @Override public void run() { serve(s); }
                });
                t.setDaemon(true);
                t.start();
            } catch (Exception e) {
                return;
            }
        }
    }

    private static void serve(Socket s) {
        try {
            s.setSoTimeout(30000);
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "ISO-8859-1"));
            String req = r.readLine();
            if (req == null) { s.close(); return; }
            long rs = 0, re = -1;
            boolean ranged = false;
            String line;
            while ((line = r.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("range:")) {
                    String v = line.substring(6).trim();
                    if (v.startsWith("bytes=")) {
                        v = v.substring(6);
                        int d = v.indexOf('-');
                        if (d >= 0) {
                            ranged = true;
                            String a = v.substring(0, d).trim(), b = v.substring(d + 1).trim();
                            if (!a.isEmpty()) rs = Long.parseLong(a);
                            if (!b.isEmpty()) re = Long.parseLong(b);
                        }
                    }
                }
            }
            String url = null, ua = "";
            int q = req.indexOf('?');
            if (q > 0) {
                String qs = req.substring(q + 1);
                int sp = qs.indexOf(' ');
                if (sp >= 0) qs = qs.substring(0, sp);
                for (String kv : qs.split("&")) {
                    int e = kv.indexOf('=');
                    if (e < 0) continue;
                    String k = kv.substring(0, e), val = URLDecoder.decode(kv.substring(e + 1), "UTF-8");
                    if (k.equals("u")) url = val; else if (k.equals("ua")) ua = val;
                }
            }
            OutputStream out = s.getOutputStream();
            if (url == null) { out.write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n".getBytes()); s.close(); return; }
            boolean head = req.startsWith("HEAD");
            Dbg.log("istek " + (ranged ? rs + "-" + (re < 0 ? "" : re) : "tamamı") + " " + (url.length() > 60 ? url.substring(8, 40) : url));
            long sent = pump(out, url, ua, rs, re, ranged, head);
            Dbg.log("gönderildi " + sent + " B");
            s.close();
        } catch (Exception e) {
            Dbg.log("vekil: " + e);
            if (String.valueOf(e.getMessage()).contains("403") || String.valueOf(e.getMessage()).contains("kısa okuma")) {
                long now = System.currentTimeMillis();
                last403 = now;
                if (++f403 >= 2 && on403 != null && now - fired403 > 8000) { fired403 = now; f403 = 0; on403.run(); }
            }
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private static HttpURLConnection open(String url, String ua, long a, long b, int mode) throws Exception {
        String u = mode == 1 ? url + (url.contains("?") ? "&" : "?") + "range=" + a + "-" + b : url;
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(9000);
        if (ua != null && !ua.isEmpty()) c.setRequestProperty("User-Agent", ua);
        if (mode == 0) c.setRequestProperty("Range", "bytes=" + a + "-" + b);
        c.setRequestProperty("Accept-Encoding", "identity");
        return c;
    }

    /** Bir parçayı iste: önce ?range= parametresi (NewPipe gibi), olmazsa Range başlığı; sırayla, en çok 4 deneme. */
    private static HttpURLConnection fetch(String url, String ua, long a, long b) throws Exception {
        Exception err = null;
        for (int t = 0; t < 4; t++) {
            int mode = t % 2 == 0 ? 1 : 0;
            try {
                HttpURLConnection x = open(url, ua, a, b, mode);
                int code = x.getResponseCode();
                if (code != 206 && code != 200) { x.disconnect(); if (code == 403) last403 = real403 = System.currentTimeMillis(); throw new Exception("HTTP " + code); }
                if (code == 200 && a > 0 && mode == 0) { x.disconnect(); throw new Exception("Range yok sayıldı"); }
                Dbg.log("parça " + a + "-" + b + " m" + mode + " -> " + code);
                if (code == 200 || code == 206) f403 = 0;
                return x;
            } catch (Exception e) {
                err = e;
                Dbg.log("parça " + a + " m" + mode + " hata: " + e.getMessage());
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            }
        }
        throw err;
    }

    private static long clen(String url) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]clen=(\\d+)").matcher(url);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    private static long pump(OutputStream out, String url, String ua, long rs, long re, boolean ranged, boolean head)
            throws Exception {
        HttpURLConnection c;
        long total = clen(url);
        try {
            long end = re >= 0 ? Math.min(re, rs + CHUNK - 1) : rs + CHUNK - 1;
            c = fetch(url, ua, rs, end);
            String cr = c.getHeaderField("Content-Range");
            if (total < 0 && cr != null && cr.indexOf('/') > 0) total = Long.parseLong(cr.substring(cr.indexOf('/') + 1).trim());
            else if (total < 0 && c.getResponseCode() == 200) total = c.getContentLength();
        } catch (Exception e) {
            out.write(("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n").getBytes());
            out.flush();
            throw e;
        }
        if (total < 0) total = Long.MAX_VALUE / 4;
        long last = re >= 0 ? Math.min(re, total - 1) : total - 1;
        long len = last - rs + 1;
        StringBuilder h = new StringBuilder();
        h.append(ranged ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n");
        h.append("Content-Type: ").append(c.getContentType() == null ? "video/mp4" : c.getContentType()).append("\r\n");
        h.append("Accept-Ranges: bytes\r\n");
        h.append("Content-Length: ").append(len).append("\r\n");
        if (ranged) h.append("Content-Range: bytes ").append(rs).append('-').append(last).append('/').append(total).append("\r\n");
        h.append("Connection: close\r\n\r\n");
        out.write(h.toString().getBytes());
        if (head) { out.flush(); c.disconnect(); return 0; }

        long pos = rs;
        int short_ = 0;
        long lastPos = rs;
        byte[] buf = new byte[32 * 1024];
        java.util.concurrent.FutureTask<HttpURLConnection> nextF = null;
        try {
            while (pos <= last) {
                if (c == null) {
                    if (nextF != null) {
                        try { c = nextF.get(); } catch (Exception e) { c = null; }
                        nextF = null;
                    }
                    if (c == null) c = fetch(url, ua, pos, Math.min(last, pos + CHUNK - 1));
                }
                final long chunkEnd = Math.min(last, pos + CHUNK - 1);
                // sıradaki parçayı şimdiden iste: parçalar arası boşluk (takılma) olmasın
                if (chunkEnd < last) {
                    final long ns = chunkEnd + 1, ne = Math.min(last, ns + CHUNK - 1);
                    final String fu = url, fua = ua;
                    nextF = new java.util.concurrent.FutureTask<HttpURLConnection>(new java.util.concurrent.Callable<HttpURLConnection>() {
                        @Override public HttpURLConnection call() throws Exception { return fetch(fu, fua, ns, ne); }
                    });
                    Thread nt = new Thread(nextF);
                    nt.setDaemon(true);
                    nt.start();
                }
                InputStream in = c.getInputStream();
                while (pos <= chunkEnd) {
                    int n;
                    try {
                        n = in.read(buf, 0, (int) Math.min(buf.length, chunkEnd - pos + 1));
                    } catch (java.io.IOException ste) {
                        // zaman aşımı / "unexpected end of stream": kaldığı yerden yeniden iste
                        Dbg.log("okuma kesildi @" + pos + ": " + ste.getMessage());
                        break;
                    }
                    if (n <= 0) break;
                    out.write(buf, 0, n); // yazma hatası = oynatıcı kapattı: dışarı fırlar
                    pos += n;
                }
                try { in.close(); } catch (Exception ignored) {}
                c.disconnect();
                c = null;
                if (pos > lastPos) { lastPos = pos; short_ = 0; } // ilerleme var: sayaç sıfırlanır
                if (pos <= chunkEnd) {
                    if (++short_ > 4) throw new Exception("kısa okuma @" + pos);
                    if (nextF != null) { // sıradaki ön istek yanlış konumda kaldı: iptal et
                        try { nextF.cancel(true); } catch (Exception ignored) {}
                        nextF = null;
                    }
                }
            }
            out.flush();
        } finally {
            if (nextF != null) {
                try { nextF.cancel(true); } catch (Exception ignored) {}
            }
        }
        return pos - rs;
    }
}
