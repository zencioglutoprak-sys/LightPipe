package com.hafif.tube;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Küçük resim yükleyici: 6 iş parçacığı + bellek önbelleği. Kaydırmada ekrandan çıkan satırın isteği atlanır. */
final class Img {
    private Img() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(80);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(6);

    static void load(final ImageView iv, final String url, final Activity act) {
        iv.setTag(url);
        Bitmap hit = CACHE.get(url);
        if (hit != null) { iv.setImageBitmap(hit); return; }
        iv.setImageDrawable(null);
        POOL.execute(new Runnable() {
            @Override public void run() {
                if (!url.equals(iv.getTag())) return; // satır başka videoya geçti, boşuna indirme
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(10000);
                    InputStream in = c.getInputStream();
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inPreferredConfig = Bitmap.Config.RGB_565; // yarı bellek, daha hızlı çözme
                    final Bitmap bm = BitmapFactory.decodeStream(in, null, o);
                    in.close();
                    if (bm == null) return;
                    CACHE.put(url, bm);
                    act.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (url.equals(iv.getTag())) iv.setImageBitmap(bm);
                        }
                    });
                } catch (Exception ignored) {}
            }
        });
    }
}
