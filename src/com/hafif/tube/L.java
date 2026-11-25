package com.hafif.tube;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dil: arayüz metinleri Türkçe yazıldı; "en" seçiliyse buradaki tablodan İngilizceye çevrilir. */
final class L {
    private L() {}

    private static boolean en, loaded;
    private static final Map<String, String> EXACT = new HashMap<>();
    private static final String[][] PREFIX = {
            {"İndiriliyor", "Downloading"}, {"Görüntü %", "Video %"}, {"Ses %", "Audio %"},
            {"Birleştiriliyor", "Merging"}, {"✓ Bitti", "✓ Done"}, {"Hata: ", "Error: "}, {"Hata", "Error"},
            {"Akış: ", "Stream: "}, {"Başlıyor", "Starting"},
            {"Oynatılabilir akış bulunamadı; indirmeyi dene · kaynak: ", "No playable stream found; try downloading · source: "},
            {"Video bilgisi alınıyor…", "Fetching video info…"}, {"Akış bağlanıyor…", "Connecting stream…"},
            {"video+ses · ", "video+audio · "}, {"ses · ", "audio · "},
    };
    private static final String[][] PHRASE = {
            {" · sessiz · ", " · silent · "}, {" · ayrı akış", " · separate stream"},
            {"YouTube'a doğrudan bağlanmak yerine bu Invidious sunucuları kullanılır. ", "These Invidious servers are used instead of connecting to YouTube directly. "},
            {"Biri çalışmazsa sıradakine geçilir.", "If one fails, the next one is tried."},
            {"Varsayılan kalite. Ayrı akış gerekiyorsa görüntü ve ses anında birlikte oynatılır. ", "Default quality. If separate streams are needed, video and audio play together instantly. "},
            {"İndirmede 144p'den 8K'ya her kalite seçilebilir.", "Downloads can use any quality from 144p to 8K."},
            {"İndirdiğin ses dosyaları uygulama içinde müzik gibi çalar, arka planda da devam eder.", "Downloaded audio files play like music inside the app and keep playing in the background."},
            {"Reklamsız, hesapsız, hafif YouTube istemcisi ve indirici. ", "Ad-free, light YouTube client and downloader. "},
            {"Tüm kod sıfırdan yazıldı, hiçbir kütüphane yok.", "All code written from scratch, no libraries."},
    };
    private static final Pattern SEC = Pattern.compile("^(.*?)\\s+(\\d+) sn$");
    private static final Pattern STATS = Pattern.compile("^[\\d.,]+\\S* izlenme.*|^\\d+ beğeni$");

    static {
        String[] p = {
            "Keşfet", "Explore", "İndirilenler", "Downloads", "Ayarlar", "Settings",
            "Link ile indir", "Download by link", "Yapıştır", "Paste", "Videoyu aç ve indir", "Open video & download",
            "Çıkmak için tekrar geri bas", "Press back again to exit", "Popüler", "Trending",
            "Video bulunamadı", "No videos found", "Tekrar dene", "Try again",
            "Popüler videolar yüklenemedi", "Couldn't load trending videos",
            "Bağlantını kontrol et ya da yukarıdan arama yap.", "Check your connection or search above.",
            "Arama başarısız", "Search failed", "Başka bir şey ara.", "Try searching for something else.",
            "YouTube'da ara", "Search YouTube", "Müzik", "Music", "Oyun", "Gaming", "Haberler", "News",
            "Teknoloji", "Tech", "Komik", "Funny", "Film", "Movies", "Spor", "Sports",
            "Hepsi", "All", "Ses", "Audio", "▶  Sesleri çal", "▶  Play audio",
            "Henüz indirme yok", "No downloads yet", "Bir videoyu açıp İndir'e dokun.", "Open a video and tap Download.",
            "Müzik çalarda aç", "Open in music player", "Uygulama içinde oynat", "Play in app",
            "Paylaş", "Share", "Başka uygulamada aç", "Open in another app", "Bunu açacak uygulama yok", "No app can open this",
            "Sil", "Delete", "Dosya cihazdan silinir", "The file will be deleted from the device",
            "Silinsin mi?", "Delete this file?", "Evet, sil", "Yes, delete", "Vazgeç", "Cancel", "Silindi", "Deleted",
            "İndir", "Download", "İndir — format seç", "Download — choose format", "İndirme başladı", "Download started",
            "HAZIR · VİDEO + SES TEK DOSYA", "READY · VIDEO + AUDIO IN ONE FILE",
            "MP4 · H.264 + AAC (telefonda birleştirilir)", "MP4 · H.264 + AAC (merged on device)",
            "WEBM · VP9 + OPUS (8K'ya kadar)", "WEBM · VP9 + OPUS (up to 8K)",
            "3GP · ESKİ TELEFONLAR", "3GP · OLD PHONES", "SADECE SES · MÜZİK", "AUDIO ONLY · MUSIC",
            "SADECE GÖRÜNTÜ (sessiz)", "VIDEO ONLY (silent)",
            "Kalite", "Quality", "Otomatik", "Auto", "Ayarlardaki varsayılan kalite", "Default quality from Settings",
            "Yükleniyor…", "Loading…", "Video yüklenemedi", "Couldn't load video", "İlgili videolar", "Related videos",
            "Oynatma kalitesi", "Playback quality", "TEK AKIŞ · SES+VİDEO", "SINGLE STREAM · VIDEO+AUDIO",
            "AYRI AKIŞ · ANINDA BİRLEŞTİRİLİR", "SEPARATE STREAMS · COMBINED INSTANTLY",
            "video+ses birlikte", "video and audio together", "İndirilenler'i aç  ›", "Open Downloads  ›",
            "Video bilgisi yükleniyor…", "Loading video info…",
            "Video bilgisi yükleniyor, hemen açılacak…", "Loading video info, opening right away…",
            "Bu akış oynatılamadı. Kaliteyi değiştirmeyi dene.", "This stream couldn't be played. Try another quality.",
            "Bu video oynatılamadı", "Couldn't play this video",
            "ŞİMDİ ÇALIYOR", "NOW PLAYING", "SIRADAKİLER", "UP NEXT",
            "Karıştır: açık", "Shuffle: on", "Karıştır: kapalı", "Shuffle: off",
            "Tekrar: kapalı", "Repeat: off", "Tekrar: tüm liste", "Repeat: all", "Tekrar: tek parça", "Repeat: one",
            "Bu dosya çalınamadı", "This file couldn't be played",
            "Çalıyor", "Playing", "Duraklatıldı", "Paused", "Önceki", "Previous", "Çal", "Play", "Duraklat", "Pause",
            "Sonraki", "Next", "Kapat", "Close",
            "Sunucular", "Servers", "Oynatma", "Playback", "Müzik çalar", "Music player",
            "Tekrar", "Repeat", "Kapalı", "Off", "Tümü", "All", "Tek parça", "One track", "Karıştır", "Shuffle",
            "+ Sunucu ekle", "+ Add server", "Varsayılana dön", "Reset to default",
            "Varsayılan sunucular yüklendi", "Default servers restored", "Tam ekranda yatay zorla", "Force landscape in fullscreen",
            "YouTube videosu veya Shorts linkini yapıştır.", "Paste a YouTube video or Shorts link.",
            "Shorts yüklenemedi. Dokunup tekrar dene.", "Couldn't load Shorts. Tap to retry.",
            "Bu Short oynatılamadı, sonrakine geçiliyor", "This Short can't be played, skipping to the next",
            "Ayrı akış açılmadı, tek akışa geçildi", "Separate stream didn't open, switched to single stream",
            "Açıklama", "Description", "Yorumlar", "Comments", "Yorumlar yükleniyor…", "Loading comments…",
            "Yorumlar yüklenemedi", "Couldn't load comments", "Daha fazla yorum", "More comments",
            "Kanal bilgisi henüz yok", "Channel info not available yet", "Kanal", "Channel", "Kanallar", "Channels",
            "Hakkında", "About", "Videolar", "Videos", "Yükleniyor…", "Loading…", "Kanal yüklenemedi", "Couldn't load channel",
            "Video bulunamadı", "No videos found", "Kanallarını görmek için Ayarlar'dan giriş yap", "Sign in from Settings to see your channels",
            "Kanallar yüklenemedi", "Couldn't load channels", "Abone olunan kanal bulunamadı", "No subscribed channels found",
            "Dil", "Language", "Tema", "Theme", "10 renk teması. Seçince uygulama yenilenir ve renkler hemen değişir.", "10 color themes. Picking one refreshes the app and the colors change instantly.", "Kırmızı", "Red", "Okyanus", "Ocean", "Orman", "Forest", "Mor Gece", "Purple Night", "Gün Batımı", "Sunset", "Pembe", "Pink", "Turkuaz", "Teal", "Altın", "Gold", "Grafit", "Graphite", "YouTube hesabı", "YouTube account", "Giriş yap", "Sign in", "Çıkış yap", "Sign out",
            "Giriş yapıldı", "Signed in", "Giriş yapılmadı", "Not signed in",
            "Kendi hesabınla giriş yap: öneriler ve abonelikler gelir, oturum bir sonraki açılışta da hatırlanır.",
            "Sign in with your own account: recommendations and subscriptions appear, and the session is remembered next time.",
            "Hesap açık: öneriler ve abonelikler görünür. Oturum hatırlanır.", "Signed in: recommendations and subscriptions appear. The session is remembered.",
            "Giriş yapıldı, oturum kaydedildi", "Signed in, session saved", "Giriş tamamlanmadı", "Sign-in wasn't completed",
            "Bitti", "Done", "Abonelikler", "Subscriptions", "Önerilenler", "For you", "Giriş", "Sign in",
            "Abonelik akışı yüklenemedi", "Couldn't load your feed",
            "Oturum kapatıldı", "Signed out", "Hesap", "Account",
            "Önerilenler yüklenemedi, popüler gösteriliyor", "Couldn't load recommendations, showing trending",
            "Google giriş sayfası açılamazsa tarayıcıdan giriş yapmayı dene.", "If Google blocks the sign-in page, try again later.",
        };
        for (int i = 0; i + 1 < p.length; i += 2) EXACT.put(p[i], p[i + 1]);
    }

    private static void ensure(Context c) {
        if (loaded || c == null) return;
        loaded = true;
        en = "en".equals(Api.prefs(c).getString("lang", "tr"));
    }

    static boolean isEn(Context c) { ensure(c); return en; }

    static void set(Context c, String lang) {
        Api.prefs(c).edit().putString("lang", lang).apply();
        en = "en".equals(lang);
        loaded = true;
    }

    static String t(Context c, String s) { ensure(c); return tr(s).toString(); }

    /** Çevirir; İngilizce kapalıysa ya da karşılık yoksa metni olduğu gibi döndürür. */
    static CharSequence tr(CharSequence in) {
        if (!en || in == null || in.length() == 0) return in;
        String s = in.toString();
        String x = EXACT.get(s);
        if (x != null) return x;
        String suffix = "";
        Matcher m = SEC.matcher(s);
        if (m.matches()) { s = m.group(1); suffix = "  " + m.group(2) + " s"; x = EXACT.get(s); if (x != null) return x + suffix; }
        if (STATS.matcher(s).matches()) return s.replace(" izlenme", " views").replace(" beğeni", " likes");
        for (String[] r : PREFIX) if (s.startsWith(r[0])) return r[1] + s.substring(r[0].length()) + suffix;
        if (s.length() > 8) for (String[] r : PHRASE) if (s.contains(r[0])) s = s.replace(r[0], r[1]);
        if (s.matches("^(H\\.264|VP9|AV1) \\+ ses$")) s = s.replace("ses", "audio");
        return s + suffix;
    }
}
