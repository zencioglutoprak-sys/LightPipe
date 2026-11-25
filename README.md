# LightPipe

Çok hafif, reklamsız bir Android YouTube istemcisi ve indirici. Saf Java, **kütüphane yok, Gradle yok**: APK yaklaşık 230 KB.

> **Ön sürüm (pre-release).** Çalışıyor ama henüz yoğun test edilmedi.

## Özellikler
- Arama, ana sayfa önerileri (yenileme ile), Shorts sekmesi (dikey kaydırma)
- Video, canlı yayın, yorumlar, açıklama, kanal sayfaları
- YouTube hesabıyla giriş (öneriler ve abonelikler; oturum hatırlanır)
- Orijinal ses kanalı (otomatik dublaj yerine)
- İndirme: 144p'den 8K'ya kadar, MP4 / M4A / MP3
- Çift dokunarak 10 sn ileri/geri (canlı yayında kapalı)
- 10 renk teması, Türkçe / İngilizce
- Ayarlar'da kopyalanabilir uygulama logu (sorun bildirmek için)

## Nasıl çalışır
YouTube'un iç API'sini (Innertube) kullanır. Akışlar için gereken **PO token**, gizli bir WebView içinde Google BotGuard çalıştırılarak üretilir. Bu kısım [NewPipe](https://github.com/TeamNewPipe/NewPipe)'ın `po_token.html` ve `PoTokenWebView` koduna dayanır. Akışlar yerel bir vekil sunucudan (parçalı, önden okumalı) oynatıcıya verilir. Doğrudan akış çalışmazsa Invidious tabanlı yedek sunuculara geçer.

## Derleme (Termux)
```
# gerekli araçlar: openjdk, aapt2, d8, apksigner, zipalign (Termux paketleri / Android SDK build-tools)
sdkmanager --sdk_root=$HOME/android-sdk "platforms;android-34"
bash build.sh
```
APK `build/LightPipe.apk` olarak çıkar ve Downloads klasörüne kopyalanır.

## İzinler
İnternet, bildirim, arka planda ses (foreground service), WAKE_LOCK. Rehber, konum, kamera, mikrofon, SMS ya da depolama izni **yoktur**. Giriş çerezleri yalnızca cihazda tutulur.

## Sorun bildirme
Ayarlar → Loglar → **Kopyala**, metni bir Issue'ya yapıştır. Log; sürümü, cihazı ve hataları içerir.

## Yasal uyarı
Bu proje YouTube ile bağlantılı değildir ve resmi API kullanmaz. YouTube kullanım koşullarına aykırı olabilir; kullanım tamamen kendi sorumluluğundadır. Yalnızca kişisel kullanım için.

## Lisans
GPL-3.0. PO token bölümü NewPipe'tan (GPL-3.0) uyarlanmıştır. Bkz. `LICENSE`.
