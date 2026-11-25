package com.hafif.tube;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/** Müzik motoru: tek MediaPlayer, çalma listesi, tekrar (kapalı/tümü/tek) ve karıştır. */
final class Music {
    private Music() {}

    static final class Track {
        final Uri uri;
        final String name;
        Track(Uri u, String n) { uri = u; name = n; }
    }

    interface Listener { void onChange(); }

    static final int OFF = 0, ALL = 1, ONE = 2;

    private static final List<Listener> L = new CopyOnWriteArrayList<>();
    private static final List<Track> list = new ArrayList<>();
    private static final Random RND = new Random();
    private static MediaPlayer mp;
    private static Context app;
    private static int index = -1;
    private static boolean prepared;
    static int repeat = OFF;
    static boolean shuffle;
    static String error;

    static void listen(Listener l) { if (!L.contains(l)) L.add(l); }
    static void unlisten(Listener l) { L.remove(l); }

    private static void fire() { for (Listener l : L) l.onChange(); }

    static List<Track> tracks() { return list; }
    static int index() { return index; }
    static boolean has() { return index >= 0 && index < list.size(); }
    static Track current() { return has() ? list.get(index) : null; }
    static boolean isPrepared() { return prepared; }
    static boolean playing() { try { return mp != null && prepared && mp.isPlaying(); } catch (Exception e) { return false; } }
    static int pos() { try { return mp != null && prepared ? mp.getCurrentPosition() : 0; } catch (Exception e) { return 0; } }
    static int dur() { try { return mp != null && prepared ? mp.getDuration() : 0; } catch (Exception e) { return 0; } }

    static void loadPrefs(Context c) {
        android.content.SharedPreferences p = Api.prefs(c);
        repeat = p.getInt("repeat", OFF);
        shuffle = p.getBoolean("shuffle", false);
    }

    /** Listeyi verip i. parçadan çalmaya başlar; servis (bildirim) de başlatılır. */
    static void play(Context c, List<Track> tracks, int i) {
        app = c.getApplicationContext();
        loadPrefs(app);
        list.clear();
        list.addAll(tracks);
        try {
            app.startForegroundService(new Intent(app, MusicService.class));
        } catch (Exception ignored) {}
        open(i);
    }

    static void open(int i) {
        if (list.isEmpty() || app == null) return;
        if (i < 0 || i >= list.size()) i = 0;
        index = i;
        error = null;
        prepared = false;
        try {
            if (mp == null) {
                mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA).build());
                mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
                mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override public void onPrepared(MediaPlayer m) {
                        prepared = true;
                        m.setLooping(repeat == ONE);
                        m.start();
                        fire();
                    }
                });
                mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override public void onCompletion(MediaPlayer m) { ended(); }
                });
                mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override public boolean onError(MediaPlayer m, int what, int extra) {
                        error = "Bu dosya çalınamadı";
                        prepared = false;
                        fire();
                        return true;
                    }
                });
            } else {
                mp.reset();
            }
            mp.setDataSource(app, list.get(index).uri);
            mp.prepareAsync();
        } catch (Exception e) {
            error = "Bu dosya çalınamadı";
            prepared = false;
        }
        fire();
    }

    private static void ended() {
        if (repeat == ONE) { try { mp.seekTo(0); mp.start(); } catch (Exception ignored) {} return; }
        int n = list.size();
        if (shuffle && n > 1) {
            int j;
            do { j = RND.nextInt(n); } while (j == index);
            open(j);
        } else if (index + 1 < n) {
            open(index + 1);
        } else if (repeat == ALL) {
            open(0);
        } else {
            try { mp.seekTo(0); } catch (Exception ignored) {}
            fire(); // liste bitti, başa sarıldı, duraklı
        }
    }

    static void toggle() {
        if (mp == null || !prepared) return;
        try {
            if (mp.isPlaying()) mp.pause(); else mp.start();
        } catch (Exception ignored) {}
        fire();
    }

    static void pause() {
        try { if (mp != null && prepared && mp.isPlaying()) { mp.pause(); fire(); } } catch (Exception ignored) {}
    }

    static void next() {
        int n = list.size();
        if (n == 0) return;
        if (shuffle && n > 1) {
            int j;
            do { j = RND.nextInt(n); } while (j == index);
            open(j);
        } else {
            open((index + 1) % n);
        }
    }

    static void prev() {
        int n = list.size();
        if (n == 0) return;
        if (pos() > 3000) { seek(0); return; }
        open((index - 1 + n) % n);
    }

    static void seek(int ms) {
        try { if (mp != null && prepared) mp.seekTo(ms); } catch (Exception ignored) {}
        fire();
    }

    static void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        try { if (mp != null && prepared) mp.setLooping(repeat == ONE); } catch (Exception ignored) {}
        if (app != null) Api.prefs(app).edit().putInt("repeat", repeat).apply();
        fire();
    }

    static void toggleShuffle() {
        shuffle = !shuffle;
        if (app != null) Api.prefs(app).edit().putBoolean("shuffle", shuffle).apply();
        fire();
    }

    static void stop() {
        try {
            if (mp != null) { mp.release(); }
        } catch (Exception ignored) {}
        mp = null;
        prepared = false;
        index = -1;
        list.clear();
        fire();
        if (app != null) {
            try { app.stopService(new Intent(app, MusicService.class)); } catch (Exception ignored) {}
        }
    }
}
