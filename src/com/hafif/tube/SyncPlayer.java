package com.hafif.tube;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Anında oynatma: ayrı görüntü ve ses akışlarını indirmeden, HTTP üzerinden iki MediaPlayer ile eşzamanlı oynatır.
 * Ses yoksa (birleşik akış / yerel dosya) tek oynatıcıdır.
 */
final class SyncPlayer {
    interface Cb {
        void onPrepared(int durMs);
        void onSize(int w, int h);
        void onBuffering(boolean b);
        void onComplete();
        void onError(String msg);
    }

    private final Cb cb;
    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaPlayer v, a;
    private boolean vReady, aReady, hasAudio, want, released, buffering, done;
    private int startAt;
    private SurfaceHolder holder;

    SyncPlayer(Cb cb) { this.cb = cb; }

    void setSurface(SurfaceHolder sh) {
        holder = sh;
        try { if (v != null) v.setDisplay(sh); } catch (Exception ignored) {}
    }

    void open(Context c, Uri video, Uri audio, int startMs, boolean autoplay) {
        startAt = startMs;
        want = autoplay;
        hasAudio = audio != null;
        String vua = Innertube.uaFor(video.toString());
        boolean isHls = video.toString().contains("m3u8") || video.toString().contains("/hls");
        if (!isHls) video = Uri.parse(StreamProxy.wrap(video.toString(), vua));
        if (audio != null) audio = Uri.parse(StreamProxy.wrap(audio.toString(), Innertube.uaFor(audio.toString())));
        Dbg.log("oynatıcı açılıyor: " + (video.toString().startsWith("http://127") ? "vekil" : isHls ? "HLS" : "doğrudan"));
        Map<String, String> hd = new HashMap<>();
        hd.put("User-Agent", vua);
        try {
            v = new MediaPlayer();
            v.setDataSource(c, video, hd);
            if (holder != null) v.setDisplay(holder);
            v.setScreenOnWhilePlaying(true);
            v.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer m) { vReady = true; maybeReady(); }
            });
            v.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override public void onVideoSizeChanged(MediaPlayer m, int w, int hh) { if (!released) cb.onSize(w, hh); }
            });
            v.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer m) {
                    done = true;
                    want = false;
                    stopTick();
                    try { if (a != null && aReady && a.isPlaying()) a.pause(); } catch (Exception ignored) {}
                    if (!released) cb.onComplete();
                }
            });
            v.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override public boolean onInfo(MediaPlayer m, int what, int extra) {
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) setBuf(true);
                    else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END
                            || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) setBuf(false);
                    return false;
                }
            });
            v.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer m, int what, int extra) {
                    if (!released) cb.onError("Görüntü akışı oynatılamadı (" + what + "/" + extra + ")");
                    return true;
                }
            });
            v.prepareAsync();
            h.postDelayed(prepWd, PREPARE_MS);
        } catch (Exception e) {
            cb.onError("Başlatılamadı: " + e.getMessage());
            return;
        }
        if (hasAudio) { audioUri = audio; audioCtx = c; openAudio(0); }
    }

    private Uri audioUri;
    private Context audioCtx;

    private void openAudio(final int attempt) {
        try {
            if (a != null) { try { a.release(); } catch (Exception ignored) {} a = null; }
            aReady = false;
            final MediaPlayer mp = new MediaPlayer();
            a = mp;
            Map<String, String> ha = new HashMap<>();
            ha.put("User-Agent", Innertube.uaFor(audioUri.toString()));
            mp.setDataSource(audioCtx, audioUri, ha);
            mp.setVolume(1f, 1f);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer m) {
                    if (released || a != mp) return;
                    aReady = true;
                    Dbg.log("ses hazır");
                    try {
                        if (started && v != null && v.isPlaying() && !buffering) { mp.seekTo(v.getCurrentPosition()); mp.start(); }
                    } catch (Exception ignored) {}
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer m, int what, int extra) {
                    if (released || a != mp) return true;
                    Dbg.log("ses hatası " + what + "/" + extra + " deneme " + attempt);
                    if (attempt < 1) { openAudio(attempt + 1); return true; }
                    dropAudio();
                    // sessiz oynamak yerine sesi olan (tek akış) plana geç
                    if (!released) cb.onError("Ses akışı çalınamadı (" + what + "/" + extra + ")");
                    return true;
                }
            });
            mp.prepareAsync();
        } catch (Exception e) {
            Dbg.log("ses açılamadı: " + e);
            if (attempt < 1) { openAudio(attempt + 1); return; }
            dropAudio();
            if (!released) cb.onError("Ses akışı açılamadı");
        }
    }

    private void dropAudio() {
        hasAudio = false;
        aReady = false;
        try { if (a != null) a.release(); } catch (Exception ignored) {}
        a = null;
    }

    private void setBuf(boolean b) {
        if (released || b == buffering) return;
        buffering = b;
        try {
            if (a != null && aReady) {
                if (b) { if (a.isPlaying()) a.pause(); }
                else if (want && !a.isPlaying()) {
                    if (Math.abs(v.getCurrentPosition() - a.getCurrentPosition()) > 400) a.seekTo(v.getCurrentPosition());
                    a.start();
                }
            }
        } catch (Exception ignored) {}
        cb.onBuffering(b);
    }

    private boolean started;
    private long lastSync;

    /** MediaPlayer.prepareAsync()'in kendi zaman aşımı yok; sunucu takılırsa sonsuza dek beklerdi. */
    private static final int PREPARE_MS = 9000;
    private final Runnable prepWd = new Runnable() {
        @Override public void run() {
            if (!released && !started) cb.onError("Akış " + (PREPARE_MS / 1000) + " sn içinde başlamadı (zaman aşımı)");
        }
    };

    private void maybeReady() {
        if (released || started) return;
        if (!vReady) return;
        started = true;
        h.removeCallbacks(prepWd);
        try {
            if (startAt > 0) v.seekTo(startAt);
        } catch (Exception ignored) {}
        cb.onPrepared(duration());
        if (want) play();
    }

    int duration() { try { return v == null || !vReady ? 0 : v.getDuration(); } catch (Exception e) { return 0; } }
    int pos() { try { return v == null || !vReady ? 0 : v.getCurrentPosition(); } catch (Exception e) { return 0; } }
    boolean isPlaying() { try { return v != null && vReady && v.isPlaying(); } catch (Exception e) { return false; } }
    boolean ready() { return started; }

    void play() {
        want = true;
        if (!started || released) return;
        try {
            if (done) { done = false; v.seekTo(0); if (a != null && aReady) a.seekTo(0); }
            v.start();
            if (a != null && aReady) { a.seekTo(v.getCurrentPosition()); a.start(); }
        } catch (Exception ignored) {}
        startTick();
    }

    void pause() {
        want = false;
        if (!started || released) return;
        try {
            if (v.isPlaying()) v.pause();
            if (a != null && aReady && a.isPlaying()) a.pause();
        } catch (Exception ignored) {}
        stopTick();
    }

    void seekTo(int ms) {
        if (!started || released) return;
        done = false;
        try {
            v.seekTo(ms);
            if (a != null && aReady) a.seekTo(ms);
        } catch (Exception ignored) {}
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (released) return;
            try {
                if (a != null && aReady && v.isPlaying() && !buffering) {
                    int dv = v.getCurrentPosition(), da = a.getCurrentPosition();
                    long now = android.os.SystemClock.elapsedRealtime();
                    // Her küçük kaymada seek yapmak sesi kesiyordu: yalnızca belirgin kaymada, en çok 3 sn'de bir
                    if (Math.abs(dv - da) > 700 && now - lastSync > 3000) { a.seekTo(dv); lastSync = now; }
                    if (!a.isPlaying()) a.start();
                }
            } catch (Exception ignored) {}
            h.postDelayed(this, 700);
        }
    };

    private void startTick() { h.removeCallbacks(tick); if (hasAudio) h.postDelayed(tick, 700); }
    private void stopTick() { h.removeCallbacks(tick); }

    void release() {
        released = true;
        Dbg.log("oynatıcı bırakılıyor");
        h.removeCallbacks(prepWd);
        stopTick();
        // MediaPlayer.release() ağ okuması takılıysa saniyelerce bloklanır: ana iş parçacığını dondurmasın
        final MediaPlayer v0 = v, a0 = a;
        v = null;
        a = null;
        try { if (v0 != null) v0.setDisplay(null); } catch (Exception ignored) {}
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try { if (v0 != null) v0.release(); } catch (Exception ignored) {}
                try { if (a0 != null) a0.release(); } catch (Exception ignored) {}
                Dbg.log("oynatıcı bırakıldı");
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
