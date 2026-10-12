#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO_OWNER="vasiliyrogozhin85-stack"
REPO_NAME="BoreacAM"
REPO="${REPO_OWNER}/${REPO_NAME}"
PROJECT_DIR="${1:-$(pwd)}"
DOWNLOAD_DIR="$HOME/storage/downloads/BoreacAM"
APK_NAME="BoreacAM-debug.apk"

echo "== BoreacAM: подготовка Termux =="
termux-setup-storage >/dev/null 2>&1 || true
pkg update -y
pkg install -y git gh unzip

if ! gh auth status >/dev/null 2>&1; then
  echo
  echo "Выполняется вход в GitHub."
  echo "Выберите: GitHub.com -> HTTPS -> Login with a web browser"
  gh auth login
fi

cd "$PROJECT_DIR"

git config --global user.name "${GIT_USER_NAME:-vasiliyrogozhin85-stack}"
git config --global user.email "${GIT_USER_EMAIL:-vasiliyrogozhin85-stack@users.noreply.github.com}"

if [ ! -d .git ]; then
  git init
  git branch -M main
fi

if gh repo view "$REPO" >/dev/null 2>&1; then
  if git remote get-url origin >/dev/null 2>&1; then
    git remote set-url origin "https://github.com/${REPO}.git"
  else
    git remote add origin "https://github.com/${REPO}.git"
  fi
else
  echo "Репозиторий ${REPO} не найден. Создаю публичный репозиторий..."
  gh repo create "$REPO" --public --description "BoreacAM acoustic file transfer for Android" --source=. --remote=origin
fi

git add .
if ! git diff --cached --quiet; then
  git commit -m "BoreacAM Android app"
fi

# Integrate an existing README/initial commit without destroying the project.
git pull origin main --allow-unrelated-histories --no-rebase || true
git push -u origin main

echo "== Запуск GitHub Actions =="
gh workflow run build-apk.yml --repo "$REPO"

sleep 5
RUN_ID="$(gh run list --repo "$REPO" --workflow build-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
if [ -z "$RUN_ID" ] || [ "$RUN_ID" = "null" ]; then
  echo "Не удалось определить ID сборки."
  exit 1
fi

gh run watch "$RUN_ID" --repo "$REPO" --exit-status

rm -rf "$DOWNLOAD_DIR"
mkdir -p "$DOWNLOAD_DIR"

gh run download "$RUN_ID" --repo "$REPO" --name BoreacAM-debug --dir "$DOWNLOAD_DIR"

APK_PATH="$(find "$DOWNLOAD_DIR" -type f -name '*.apk' | head -n 1)"
if [ -z "$APK_PATH" ]; then
  echo "APK не найден в артефакте."
  exit 1
fi

cp "$APK_PATH" "$HOME/storage/downloads/$APK_NAME"

echo
echo "Готовый APK:"
echo "$HOME/storage/downloads/$APK_NAME"
echo
echo "Открываю установщик Android..."
termux-open --view "$HOME/storage/downloads/$APK_NAME" || true
