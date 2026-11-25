#!/data/data/com.termux/files/usr/bin/bash
# LightPipe'ı GitHub'a ÖN SÜRÜM olarak yükler. Önce: bash build.sh  ve  gh auth login
set -e
cd "$(dirname "$0")"
REPO="${1:-LightPipe}"
VER=$(grep -o 'version-name [0-9.]*' build.sh | awk '{print $2}')
[ -f build/LightPipe.apk ] || { echo "Önce bash build.sh çalıştır"; exit 1; }
[ -f LICENSE ] || curl -L -o LICENSE https://www.gnu.org/licenses/gpl-3.0.txt
[ -d .git ] || git init -b main
git add -A
git commit -m "LightPipe $VER" || true
gh repo view "$REPO" >/dev/null 2>&1 || gh repo create "$REPO" --public --source=. --push
git push -u origin main 2>/dev/null || git push
cp build/LightPipe.apk "build/LightPipe-$VER.apk"
gh release create "v$VER" "build/LightPipe-$VER.apk" --prerelease \
  --title "LightPipe $VER (ön sürüm)" \
  --notes "Ön sürüm: çalışıyor ama henüz yoğun test edilmedi. Sorun olursa Ayarlar → Loglar → Kopyala ile logu Issue'ya yapıştır. Ayrıntılar: CHANGELOG.md"
echo "✓ Yüklendi: v$VER (pre-release)"
