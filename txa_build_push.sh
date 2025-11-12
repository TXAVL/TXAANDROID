#!/usr/bin/env bash
# ============================================
# TXA Android Auto Build & Push Script (v2)
# Auto re-authenticate GitHub if push fails
# ============================================

set -e

# --- Config ---
RELEASE_DIR="releases"
APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
APK_DEST_NAME="TXA_ANDROID_$(date +%Y%m%d_%H%M%S)-debug.apk"
GIT_REPO="https://github.com/TXAVL/TXAANDROID.git"

echo "🔧 Building TXA Android APK..."
./gradlew assembleDebug

if [ ! -f "$APK_SRC" ]; then
  echo "❌ APK not found: $APK_SRC"
  exit 1
fi

mkdir -p "$RELEASE_DIR"
cp "$APK_SRC" "$RELEASE_DIR/$APK_DEST_NAME"

echo "📦 Committing APK to Git..."
git add -f "$RELEASE_DIR/$APK_DEST_NAME"
git commit -m "Auto-build: Add $APK_DEST_NAME" || true

echo "☁️ Pushing to GitHub..."
if ! git push origin main; then
  echo "⚠️ Push failed. Trying to re-authorize..."
  read -p "👉 Enter your GitHub username: " GH_USER
  read -s -p "🔑 Enter your GitHub personal access token: " GH_TOKEN
  echo ""
  
  NEW_URL="https://${GH_USER}:${GH_TOKEN}@github.com/TXAVL/TXAANDROID.git"
  git remote set-url origin "$NEW_URL"

  echo "🔁 Retrying push..."
  if git push origin main; then
    echo "✅ Push successful after re-authentication."
  else
    echo "❌ Push still failed. Please check your token or network."
    exit 1
  fi
else
  echo "✅ Push successful."
fi

echo "🎉 Done! APK uploaded to: releases/$APK_DEST_NAME"
