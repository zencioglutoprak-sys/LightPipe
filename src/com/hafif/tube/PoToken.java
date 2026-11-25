package com.hafif.tube;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YouTube PO Token üretici. NewPipe'ın PoTokenWebView + po_token.html kodunun birebir Java uyarlaması:
 * BotGuard gizli WebView'da çalışır, integrityToken alınır, her kimlik (identifier) için token üretilir.
 *  - akış tokenı (&pot=): ziyaretçi kimliğine (visitorData) bağlı
 *  - oynatıcı tokenı (serviceIntegrityDimensions.poToken): video kimliğine bağlı
 */
final class PoToken {
    private static final String REQUEST_KEY = "O43z0dpjhgX20SCx4KAo";
    private static final String API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw";
    private static final String BASE = "https://www.youtube.com/api/jnn/v1/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3";

    static volatile String token;        // akış tokenı (websafe base64)
    static volatile String visitorData;  // tokenın bağlı olduğu kimlik
    private static volatile long expires;
    private static volatile boolean busy;
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<String, String> MINTED = new ConcurrentHashMap<String, String>();

    // NewPipe'ın app/src/main/assets/po_token.html dosyasından
    private static final String HTML = "<!DOCTYPE html><html lang=\"en\"><head><title></title><script>\n"
            + "function loadBotGuard(challengeData) {\n"
            + "  this.vm = this[challengeData.globalName];\n"
            + "  this.program = challengeData.program;\n"
            + "  this.vmFunctions = {};\n"
            + "  this.syncSnapshotFunction = null;\n"
            + "  if (!this.vm) throw new Error('[BotGuardClient]: VM not found in the global object');\n"
            + "  if (!this.vm.a) throw new Error('[BotGuardClient]: Could not load program');\n"
            + "  const vmFunctionsCallback = function (asyncSnapshotFunction, shutdownFunction, passEventFunction, checkCameraFunction) {\n"
            + "    this.vmFunctions = { asyncSnapshotFunction: asyncSnapshotFunction, shutdownFunction: shutdownFunction, passEventFunction: passEventFunction, checkCameraFunction: checkCameraFunction };\n"
            + "  };\n"
            + "  this.syncSnapshotFunction = this.vm.a(this.program, vmFunctionsCallback, true, this.userInteractionElement, function () {}, [ [], [] ])[0]\n"
            + "  return new Promise(function (resolve, reject) {\n"
            + "    i = 0\n"
            + "    refreshIntervalId = setInterval(function () {\n"
            + "      if (!!this.vmFunctions.asyncSnapshotFunction) { resolve(this); clearInterval(refreshIntervalId); }\n"
            + "      if (i >= 10000) { reject(\"asyncSnapshotFunction is null even after 10 seconds\"); clearInterval(refreshIntervalId); }\n"
            + "      i += 1;\n"
            + "    }, 1);\n"
            + "  })\n"
            + "}\n"
            + "function snapshot(args) {\n"
            + "  return new Promise(function (resolve, reject) {\n"
            + "    if (!this.vmFunctions.asyncSnapshotFunction) return reject(new Error('[BotGuardClient]: Async snapshot function not found'));\n"
            + "    this.vmFunctions.asyncSnapshotFunction(function (response) { resolve(response) }, [args.contentBinding, args.signedTimestamp, args.webPoSignalOutput, args.skipPrivacyBuffer]);\n"
            + "  });\n"
            + "}\n"
            + "function runBotGuard(challengeData) {\n"
            + "  const interpreterJavascript = challengeData.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue;\n"
            + "  if (interpreterJavascript) { new Function(interpreterJavascript)(); } else throw new Error('Could not load VM');\n"
            + "  const webPoSignalOutput = [];\n"
            + "  return loadBotGuard({ globalName: challengeData.globalName, globalObj: this, program: challengeData.program }).then(function (botguard) {\n"
            + "    return botguard.snapshot({ webPoSignalOutput: webPoSignalOutput })\n"
            + "  }).then(function (botguardResponse) {\n"
            + "    return { webPoSignalOutput: webPoSignalOutput, botguardResponse: botguardResponse }\n"
            + "  })\n"
            + "}\n"
            + "function obtainPoToken(webPoSignalOutput, integrityToken, identifier) {\n"
            + "  const getMinter = webPoSignalOutput[0];\n"
            + "  if (!getMinter) throw new Error('PMD:Undefined');\n"
            + "  const mintCallback = getMinter(integrityToken);\n"
            + "  if (!(mintCallback instanceof Function)) throw new Error('APF:Failed');\n"
            + "  const result = mintCallback(identifier);\n"
            + "  if (!result) throw new Error('YNJ:Undefined');\n"
            + "  if (!(result instanceof Uint8Array)) throw new Error('ODM:Invalid');\n"
            + "  return result;\n"
            + "}\n"
            + "</script></head><body></body></html>";

    private static WebView wv;
    private static String vdNow;
    private static volatile boolean jsReady;

    /** Geçerli token varsa true. */
    static boolean ready() { return token != null && System.currentTimeMillis() < expires; }

    /** Arka planda token üretimini başlatır (zaten hazır/üretiliyorsa bir şey yapmaz). */
    static void warm(Context c) {
        final Context app = c.getApplicationContext();
        Innertube.appCtx = app;
        synchronized (LOCK) {
            if (busy || (ready() && expires - System.currentTimeMillis() > 10 * 60 * 1000)) return;
            busy = true;
        }
        new Thread(new Runnable() {
            @Override public void run() { start(app); }
        }).start();
    }

    /** Token hazır olana kadar (en çok ms) bekler; yoksa null. */
    static String await(Context c, long ms) {
        if (ready()) return token;
        warm(c);
        long end = System.currentTimeMillis() + ms;
        synchronized (LOCK) {
            while (!ready() && busy) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) break;
                try { LOCK.wait(left); } catch (InterruptedException e) { break; }
            }
        }
        return ready() ? token : null;
    }

    /** Verilen kimlik (ör. video id) için token üretir; WebView hazır değilse null. */
    static String mintFor(String ident, long ms) {
        if (!jsReady || wv == null) return null;
        final String id = ident;
        MINTED.remove(id);
        final String csv = u8csv(id);
        MAIN.post(new Runnable() {
            @Override public void run() {
                try {
                    if (wv == null) { MINTED.put(id, "ERR:webview yok"); wake(); return; }
                    wv.evaluateJavascript("try {\n"
                            + "identifier = " + JSONObject.quote(id) + "\n"
                            + "u8Identifier = new Uint8Array([" + csv + "])\n"
                            + "poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)\n"
                            + "poTokenU8String = \"\"\n"
                            + "for (i = 0; i < poTokenU8.length; i++) { if (i != 0) poTokenU8String += \",\"; poTokenU8String += poTokenU8[i] }\n"
                            + "PoBridge.onPotResult(identifier, poTokenU8String)\n"
                            + "} catch (error) { PoBridge.onPotError(identifier, error + \"\\n\" + error.stack) }", null);
                } catch (Exception e) { MINTED.put(id, "ERR:" + e); wake(); }
            }
        });
        long end = System.currentTimeMillis() + ms;
        synchronized (LOCK) {
            while (!MINTED.containsKey(id)) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) break;
                try { LOCK.wait(left); } catch (InterruptedException e) { break; }
            }
        }
        String r = MINTED.remove(id);
        if (r == null || r.startsWith("ERR:")) { Dbg.log("pot (" + (id.length() > 12 ? id.substring(0, 12) : id) + ") alınamadı: " + r); return null; }
        return r;
    }

    private static void wake() { synchronized (LOCK) { LOCK.notifyAll(); } }

    private static String u8csv(String s) {
        try {
            byte[] b = s.getBytes("UTF-8");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < b.length; i++) { if (i > 0) sb.append(','); sb.append(b[i] & 255); }
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private static String csvToB64(String csv) {
        String[] p = csv.split(",");
        byte[] b = new byte[p.length];
        for (int i = 0; i < p.length; i++) b[i] = (byte) Integer.parseInt(p[i].trim());
        return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_WRAP);
    }

    private static byte[] b64Decode(String s) {
        s = s.replace('-', '+').replace('_', '/').replace('.', '=');
        return Base64.decode(s, Base64.DEFAULT);
    }

    private static void finish(String err) {
        if (err != null) Dbg.log("pot hata: " + err);
        synchronized (LOCK) {
            busy = false;
            LOCK.notifyAll();
        }
        if (err != null) {
            jsReady = false;
            MAIN.post(new Runnable() {
                @Override public void run() {
                    try { if (wv != null) { wv.destroy(); wv = null; } } catch (Exception ignored) {}
                }
            });
        }
    }

    /** NewPipe'ın JavaScriptUtil.parseChallengeData işlevi. */
    private static String parseChallenge(String raw) throws Exception {
        JSONArray d = new JSONArray(raw);
        JSONArray c;
        if (d.length() > 1 && d.get(1) instanceof String) {
            byte[] b = b64Decode(d.getString(1));
            for (int i = 0; i < b.length; i++) b[i] = (byte) (b[i] + 97);
            c = new JSONArray(new String(b, "UTF-8"));
        } else {
            c = d.getJSONArray(0);
        }
        JSONArray ij = c.getJSONArray(1);
        String script = null;
        for (int i = 0; i < ij.length(); i++) {
            if (!ij.isNull(i) && ij.get(i) instanceof String) { script = ij.getString(i); break; }
        }
        if (script == null) throw new Exception("BotGuard betiği yok");
        JSONObject o = new JSONObject();
        o.put("interpreterJavascript", new JSONObject().put("privateDoNotAccessOrElseSafeScriptWrappedValue", script));
        o.put("program", c.getString(4));
        o.put("globalName", c.getString(5));
        return o.toString();
    }

    private static void start(final Context app) {
        try {
            Dbg.log("pot: üretiliyor…");
            jsReady = false;
            vdNow = Innertube.visitorData();
            if (vdNow == null || vdNow.isEmpty()) { finish("visitorData alınamadı"); return; }
            Dbg.log("pot: visitorData " + vdNow.substring(0, Math.min(12, vdNow.length())) + "… (" + vdNow.length() + ")");
            final String challenge = parseChallenge(postJson("Create", "[ \"" + REQUEST_KEY + "\" ]"));
            MAIN.post(new Runnable() {
                @Override public void run() {
                    try {
                        if (wv != null) { try { wv.destroy(); } catch (Exception ignored) {} wv = null; }
                        wv = new WebView(app);
                        WebSettings s = wv.getSettings();
                        s.setJavaScriptEnabled(true);
                        s.setUserAgentString(USER_AGENT);
                        wv.addJavascriptInterface(new Bridge(), "PoBridge");
                        wv.setWebChromeClient(new WebChromeClient() {
                            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                                if (m.message().contains("Uncaught")) Dbg.log("pot JS: " + m.message() + " (" + m.lineNumber() + ")");
                                return super.onConsoleMessage(m);
                            }
                        });
                        wv.setWebViewClient(new WebViewClient() {
                            @Override public void onPageFinished(WebView v, String url) {
                                v.evaluateJavascript("try {\n"
                                        + "data = " + challenge + "\n"
                                        + "runBotGuard(data).then(function (result) {\n"
                                        + "  this.webPoSignalOutput = result.webPoSignalOutput\n"
                                        + "  PoBridge.onBg(result.botguardResponse)\n"
                                        + "}, function (error) { PoBridge.onErr(error + \"\\n\" + error.stack) })\n"
                                        + "} catch (error) { PoBridge.onErr(error + \"\\n\" + error.stack) }", null);
                            }
                        });
                        wv.loadDataWithBaseURL("https://www.youtube.com", HTML, "text/html", "utf-8", null);
                    } catch (Exception e) {
                        finish("webview: " + e);
                    }
                }
            });
            // 30 sn güvenlik zaman aşımı
            MAIN.postDelayed(new Runnable() {
                @Override public void run() { if (busy) finish("zaman aşımı"); }
            }, 30000);
        } catch (Exception e) {
            finish("başlatma: " + e);
        }
    }

    private static final class Bridge {
        @JavascriptInterface public void onBg(final String resp) {
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        String r = postJson("GenerateIT", "[ \"" + REQUEST_KEY + "\", \"" + resp + "\" ]");
                        JSONArray a = new JSONArray(r);
                        byte[] it = b64Decode(a.getString(0));
                        long ttl = a.optLong(1, 3600);
                        expires = System.currentTimeMillis() + Math.max(600, ttl - 600) * 1000;
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < it.length; i++) { if (i > 0) sb.append(','); sb.append(it[i] & 255); }
                        final String js = "this.integrityToken = new Uint8Array([" + sb + "])";
                        MAIN.post(new Runnable() {
                            @Override public void run() {
                                if (wv == null) return;
                                wv.evaluateJavascript(js, new android.webkit.ValueCallback<String>() {
                                    @Override public void onReceiveValue(String v) {
                                        jsReady = true;
                                        // akış tokenı: ziyaretçi kimliği ile
                                        new Thread(new Runnable() {
                                            @Override public void run() {
                                                String t = mintFor(vdNow, 8000);
                                                if (t == null) { finish("akış tokenı üretilemedi"); return; }
                                                visitorData = vdNow;
                                                token = t;
                                                Dbg.log("pot: hazır (" + t.length() + " karakter, " + t.substring(0, Math.min(8, t.length())) + "…)");
                                                finish(null);
                                            }
                                        }).start();
                                    }
                                });
                            }
                        });
                    } catch (Exception e) {
                        finish("integrity: " + e.getMessage());
                    }
                }
            }).start();
        }

        @JavascriptInterface public void onPotResult(String ident, String csv) {
            try { MINTED.put(ident, csvToB64(csv)); } catch (Exception e) { MINTED.put(ident, "ERR:" + e); }
            wake();
        }

        @JavascriptInterface public void onPotError(String ident, String err) {
            MINTED.put(ident, "ERR:" + err);
            wake();
        }

        @JavascriptInterface public void onErr(String m) { finish(m); }
    }

    private static String postJson(String call, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + call).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json+protobuf");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("x-goog-api-key", API_KEY);
        c.setRequestProperty("x-user-agent", "grpc-web-javascript/0.1");
        c.setRequestProperty("User-Agent", USER_AGENT);
        OutputStream os = c.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();
        int code = c.getResponseCode();
        if (code != 200) throw new Exception(call + " HTTP " + code);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }
}
