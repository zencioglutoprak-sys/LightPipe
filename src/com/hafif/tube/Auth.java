package com.hafif.tube;

import android.content.Context;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** YouTube hesabı: WebView girişinden gelen çerezler kayıtlı tutulur; istekler SAPISIDHASH ile imzalanır. */
final class Auth {
    private Auth() {}

    private static String cookie = "";
    private static boolean loaded;
    static final String ORIGIN = "https://www.youtube.com";

    static void init(Context c) {
        if (loaded) return;
        loaded = true;
        cookie = Api.prefs(c).getString("cookie", "");
    }

    static boolean logged(Context c) {
        init(c);
        return sapisid() != null;
    }

    static void save(Context c, String ck) {
        cookie = ck == null ? "" : ck;
        loaded = true;
        Api.prefs(c).edit().putString("cookie", cookie).apply();
    }

    static void clear(Context c) { save(c, ""); }

    private static String value(String name) {
        for (String part : cookie.split(";")) {
            String p = part.trim();
            if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
        }
        return null;
    }

    private static String sapisid() {
        String v = value("SAPISID");
        if (v == null) v = value("__Secure-3PAPISID");
        return v;
    }

    private static String sha1(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    /** Girişliyse Cookie + Authorization başlıkları, değilse boş harita. */
    static Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        String sid = sapisid();
        if (sid == null) return h;
        try {
            long ts = System.currentTimeMillis() / 1000;
            h.put("Authorization", "SAPISIDHASH " + ts + "_" + sha1(ts + " " + sid + " " + ORIGIN));
            h.put("Cookie", cookie);
            h.put("X-Goog-AuthUser", "0");
            h.put("X-Origin", ORIGIN);
        } catch (Exception ignored) {}
        return h;
    }
}
