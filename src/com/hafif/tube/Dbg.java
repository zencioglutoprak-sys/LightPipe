package com.hafif.tube;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;

/** Uygulama geneli kayıt: bellekte son 3000 satır + dosyada kalıcı (çökme sonrası da okunur). */
final class Dbg {
    private static final ArrayDeque<String> LOG = new ArrayDeque<>();
    private static final long T0 = System.currentTimeMillis();
    private static final int MAX = 3000;
    private static File file;
    private static boolean inited;

    static synchronized void init(Context c) {
        if (inited) return;
        inited = true;
        try {
            file = new File(c.getApplicationContext().getFilesDir(), "log.txt");
            if (file.exists() && file.length() > 400000) file.delete();
            logRaw("=== uygulama başladı " + new java.text.SimpleDateFormat("dd.MM HH:mm:ss", java.util.Locale.US).format(new java.util.Date())
                    + " · Android " + android.os.Build.VERSION.RELEASE + " · " + android.os.Build.MODEL + " ===");
        } catch (Exception ignored) {}
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
                java.util.List<android.app.ApplicationExitInfo> l = am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 3);
                for (android.app.ApplicationExitInfo x : l) {
                    logRaw("önceki çıkış: neden=" + x.getReason() + " (1=çıkış 2=sinyal 3=ANR 4=Java çökme 5=NATIVE çökme 6=bellek 7=izin 8=diğer 10=kullanıcı 13=bellek yetersiz) durum=" + x.getStatus()
                            + " açıklama=" + x.getDescription() + " zaman=" + new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date(x.getTimestamp())));
                }
            }
        } catch (Throwable ignored) {}
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                logRaw("ÇÖKME [" + t.getName() + "] " + sw);
                if (old != null) old.uncaughtException(t, e);
            }
        });
    }

    static void log(String s) { logRaw(((System.currentTimeMillis() - T0) / 1000) + "s " + s); }

    static void err(String where, Throwable e) { log(where + ": " + e); }

    private static synchronized void logRaw(String s) {
        LOG.addLast(s);
        while (LOG.size() > MAX) LOG.removeFirst();
        if (file != null) {
            try {
                FileOutputStream o = new FileOutputStream(file, true);
                o.write((s + "\n").getBytes("UTF-8"));
                o.close();
            } catch (Exception ignored) {}
        }
    }

    static synchronized String dump(int last) {
        StringBuilder sb = new StringBuilder();
        int skip = Math.max(0, LOG.size() - last), i = 0;
        for (String s : LOG) { if (i++ >= skip) sb.append(s).append('\n'); }
        return sb.toString().trim();
    }

    /** Dosyadaki tüm kayıt (önceki oturumlar ve çökmeler dahil). */
    static synchronized String full() {
        try {
            if (file != null && file.exists()) {
                java.io.FileInputStream in = new java.io.FileInputStream(file);
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) >= 0) b.write(buf, 0, n);
                in.close();
                return b.toString("UTF-8").trim();
            }
        } catch (Exception ignored) {}
        return dump(MAX);
    }

    static synchronized void clear() {
        LOG.clear();
        try { if (file != null) file.delete(); } catch (Exception ignored) {}
    }
}
