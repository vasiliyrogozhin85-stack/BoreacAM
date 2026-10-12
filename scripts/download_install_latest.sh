#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="vasiliyrogozhin85-stack/BoreacAM"
TARGET="$HOME/storage/downloads/BoreacAM-debug.apk"
WORK="$HOME/.cache/boreacam-apk"

termux-setup-storage >/dev/null 2>&1 || true
pkg install -y gh unzip

gh auth status >/dev/null 2>&1 || gh auth login

RUN_ID="$(gh run list --repo "$REPO" --workflow build-apk.yml --status success --limit 1 --json databaseId --jq '.[0].databaseId')"
rm -rf "$WORK"
mkdir -p "$WORK"

gh run download "$RUN_ID" --repo "$REPO" --name BoreacAM-debug --dir "$WORK"
APK="$(find "$WORK" -type f -name '*.apk' | head -n 1)"
cp "$APK" "$TARGET"

echo "APK загружен: $TARGET"
termux-open --view "$TARGET" || true
