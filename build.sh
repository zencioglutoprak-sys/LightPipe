#!/data/data/com.termux/files/usr/bin/bash
# LightPipe — Gradle'sız build: aapt2 + javac + d8 + zipalign + apksigner
set -e
cd "$(dirname "$0")"
APP=LightPipe

# android.jar'ı bul (ANDROID_JAR ile elle de verilebilir)
AJ="${ANDROID_JAR:-$(find "$HOME/android-sdk/platforms" -name android.jar 2>/dev/null | sort | tail -1)}"
if [ ! -f "$AJ" ]; then
  echo "android.jar bulunamadı. Önce şunu çalıştır:"
  echo '  sdkmanager --sdk_root=$HOME/android-sdk "platforms;android-34"'
  exit 1
fi
echo "android.jar: $AJ"

rm -rf build && mkdir -p build/classes build/gen

echo "[1/6] aapt2 compile (kaynaklar)"
aapt2 compile --dir res -o build/res.zip

echo "[2/6] aapt2 link (manifest + kaynaklar + R.java)"
aapt2 link -o build/app.unsigned.apk -I "$AJ" --manifest AndroidManifest.xml \
  --java build/gen --min-sdk-version 29 --target-sdk-version 34 \
  --version-code 40 --version-name 1.2.3 build/res.zip

echo "[3/6] javac"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
  -bootclasspath "$AJ" -d build/classes $(find src build/gen -name '*.java')

echo "[4/6] d8 (dex)"
d8 --release --min-api 29 --lib "$AJ" --output build $(find build/classes -name '*.class')

echo "[5/6] dex'i APK'ya ekle + zipalign"
( cd build && aapt add app.unsigned.apk classes.dex >/dev/null )
zipalign -f -p 4 build/app.unsigned.apk build/app.aligned.apk

echo "[6/6] imzala"
KS="$HOME/.hafif.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias hafif -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=LightPipe"
fi
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "build/$APP.apk" build/app.aligned.apk

ls -la "build/$APP.apk"
if [ -d "$HOME/storage/downloads" ]; then
  cp "build/$APP.apk" "$HOME/storage/downloads/$APP.apk"
  echo "✓ Downloads klasörüne kopyalandı: $APP.apk"
elif [ -d /sdcard/Download ]; then
  cp "build/$APP.apk" "/sdcard/Download/$APP.apk"
  echo "✓ /sdcard/Download'a kopyalandı: $APP.apk"
else
  echo "APK: $(pwd)/build/$APP.apk"
fi
