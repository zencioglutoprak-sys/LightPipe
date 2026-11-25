package com.hafif.tube;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

/** İndirilenler kitaplığı: Download/LightPipe (+ eski HafifTube) dosyaları. */
final class Lib {
    private Lib() {}

    static final class F {
        long id, size, date;
        String name = "", mime = "";
        Uri uri;
        boolean audio() {
            String n = name.toLowerCase();
            return mime.startsWith("audio/") || n.endsWith(".m4a") || n.endsWith(".mp3") || n.endsWith(".opus")
                    || n.endsWith(".weba") || n.endsWith(".aac");
        }
        boolean video() { return !audio(); }
    }

    static List<F> query(Context c) {
        List<F> out = new ArrayList<>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME,
                            MediaStore.Downloads.SIZE, MediaStore.Downloads.MIME_TYPE, MediaStore.Downloads.DATE_ADDED},
                    MediaStore.Downloads.RELATIVE_PATH + " LIKE ? OR " + MediaStore.Downloads.RELATIVE_PATH + " LIKE ?",
                    new String[]{"Download/LightPipe%", "Download/HafifTube%"},
                    MediaStore.Downloads.DATE_ADDED + " DESC");
            while (cur != null && cur.moveToNext()) {
                F f = new F();
                f.id = cur.getLong(0);
                f.name = cur.getString(1) == null ? "" : cur.getString(1);
                f.size = cur.getLong(2);
                f.mime = cur.getString(3) == null ? "" : cur.getString(3);
                f.date = cur.getLong(4);
                f.uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, f.id);
                out.add(f);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toUpperCase();
    }

    private static String base(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? name : name.substring(0, i);
    }

    /** "Şarkı 720p.mp4" -> "Şarkı". */
    static String title(String name) {
        String b = base(name);
        return b.replaceAll("\\s(\\d{3,4}p\\d*|ses \\d+k)$", "").trim();
    }

    /** "Şarkı 720p.mp4" -> "MP4 · 720p". */
    static String tag(String name) {
        String b = base(name);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\s(\\d{3,4}p\\d*|ses \\d+k)$").matcher(b);
        String q = m.find() ? m.group(1) : "";
        return ext(name) + (q.isEmpty() ? "" : " · " + q);
    }
}
