#!/usr/bin/env bash
# EXCISE (SHW-103) — vyrobí PODEPSANOU ostrou verzi appky „Slovo" na Zenbooku a nahraje ji na náš
# server na VLASTNÍ OTA kanál `slovo` (jellyfin-uploader /api/appupdate/slovo). Klon channel-release.sh.
#
# Použití:  ./scripts/channel-release-filmy.sh "Changelog text pro tuto verzi"
# Verze se bere z app-slovo/build.gradle.kts (versionCode/versionName) — bumpni JI PŘED během.
#
# Předpoklady (Zenbook yellman, ssh -p 2222):
#   ~/.showlyfin-build.env  = export SIGNING_* + TRAKT_*/TMDB_API_KEY  (Slovo sdílí keystore showlyfinu;
#                             jiný applicationId → jeden klíč OK)
#   ~/showlyfin-release.jks = release keystore
#   ~/Android/Sdk           = Android SDK
set -euo pipefail

ZEN_SSH="ssh -p 2222 yellman@localhost"
SRC="/root/projects/showlyfin"
ZSRC="/home/yellman/showlyfin"
TEMP="/mnt/HC_Volume_105053122/upload_temp"   # host → kontejner /upload_temp (TEMP_DIR)
NOTES="${1:-Nová verze Slovo}"

VCODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$SRC/app-slovo/build.gradle.kts" | head -1)
VNAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$SRC/app-slovo/build.gradle.kts" | head -1)
echo "==> CHANNEL release: Slovo v$VNAME (versionCode $VCODE)"

echo "==> 1/4 sync zdrojů na Zenbook…"
rsync -az --delete \
  --exclude 'local.properties' --exclude '.git/' \
  --exclude 'build/' --exclude '*/build/' --exclude '.gradle/' \
  -e 'ssh -p 2222' "$SRC/" "yellman@localhost:$ZSRC/"

echo "==> 2/4 assembleRelease (podpis vlastním keystorem, safe-build proti brown-outu)…"
$ZEN_SSH "cd $ZSRC && ./gradlew --stop >/dev/null 2>&1 || true; source ~/.showlyfin-build.env && ANDROID_HOME=\$HOME/Android/Sdk ~/skripty/safe-build.sh :app-slovo:assembleRelease --no-daemon --no-configuration-cache"

echo "==> 3/4 stahuju podepsaný APK na server…"
APK="$TEMP/slovo-release.apk"
scp -P 2222 "yellman@localhost:$ZSRC/app-slovo/build/outputs/apk/release/app-slovo-release.apk" "$APK"
SIZE=$(stat -c %s "$APK")

echo "==> 4/4 zapisuju manifest…"
python3 - "$TEMP/slovo_update.json" "$VCODE" "$VNAME" "$NOTES" "$SIZE" <<'PY'
import json, sys
path, vc, vn, notes, size = sys.argv[1:6]
json.dump({"versionCode": int(vc), "versionName": vn, "notes": notes, "size": int(size)},
          open(path, "w", encoding="utf-8"), ensure_ascii=False)
PY

echo "==> Hotovo. Manifest:"
cat "$TEMP/slovo_update.json"; echo
echo "==> Ověření živého endpointu:"
curl -s https://upload.jankoran.cz/api/appupdate/slovo; echo
echo "==> APK: $(du -h "$APK" | cut -f1)  md5: $(md5sum "$APK" | cut -d' ' -f1)"
