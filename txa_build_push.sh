#!/usr/bin/env bash
# ============================================
# TXA Hub Auto Build & Push Script (Linux/Mac)
# Supports debug + release, auto auth GitHub
# ============================================

set -e

# --- Config ---
export TXA_KEY_PASS="your_password_here"
RELEASE_DIR="releases"
# Chỉ build mobile flavor
DEBUG_APK_SRC="app/build/outputs/apk/mobile/debug/app-mobile-debug.apk"
RELEASE_APK_SRC="app/build/outputs/apk/mobile/release/app-mobile-release.apk"
KEYSTORE="$(pwd)/txa-release-key.keystore"
ALIAS="txa_release"
STOREPASS=""
KEYPASS=""
GIT_REPO="https://github.com/TXAVL/TXAANDROID.git"

# --- Parse arguments ---
MODE="debug"
for arg in "$@"; do
  case $arg in
    --release)
      MODE="release"
      shift
      ;;
  esac
done

# --- Stop old Gradle daemons to free memory ---
echo "🧹 Stopping old Gradle daemons..."
./gradlew --stop >/dev/null 2>&1 || true

# --- Build ---
if [ "$MODE" = "release" ]; then
  echo "🔧 Building TXA Hub Mobile RELEASE APK..."
  
  # Kiểm tra keystore, nếu chưa có thì tạo mới
  if [ ! -f "$KEYSTORE" ]; then
    echo "📝 Keystore not found. Creating new keystore..."
    if [ -z "$STOREPASS" ]; then
      read -s -p "🔑 Enter keystore password (will be used for both store and key): " STOREPASS; echo ""
      KEYPASS="$STOREPASS"
    fi
    echo "📝 Creating keystore with alias: $ALIAS"
    keytool -genkey -v -keystore "$KEYSTORE" -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 -storepass "$STOREPASS" -keypass "$KEYPASS" -dname "CN=TXA Hub, OU=Development, O=TXA Hub, L=Ho Chi Minh, ST=Ho Chi Minh, C=VN"
    if [ $? -ne 0 ]; then
      echo "❌ Failed to create keystore!"
      exit 1
    fi
    echo "✅ Keystore created successfully!"
  else
    echo "✅ Keystore found: $KEYSTORE"
    if [ -z "$STOREPASS" ]; then
      read -s -p "🔑 Enter keystore password: " STOREPASS; echo ""
      read -s -p "🔑 Enter key password (if same, press Enter): " KEYPASS; echo ""
      [ -z "$KEYPASS" ] && KEYPASS="$STOREPASS"
    fi
  fi
  ./gradlew assembleMobileRelease -Pandroid.injected.signing.store.file="$KEYSTORE" \
                                   -Pandroid.injected.signing.store.password="$STOREPASS" \
                                   -Pandroid.injected.signing.key.alias="$ALIAS" \
                                   -Pandroid.injected.signing.key.password="$KEYPASS"
else
  echo "🔧 Building TXA Hub Mobile DEBUG APK..."
  ./gradlew assembleMobileDebug
fi

# --- Select built file ---
if [ "$MODE" = "release" ]; then
  APK_SRC="$RELEASE_APK_SRC"
else
  APK_SRC="$DEBUG_APK_SRC"
fi

# --- Check APK existence ---
if [ ! -f "$APK_SRC" ]; then
  echo "❌ APK not found: $APK_SRC"
  exit 1
fi

# --- Copy to releases ---
mkdir -p "$RELEASE_DIR"
APK_DEST_NAME="TXAHUB_APP_$(date +%Y%m%d_%H%M%S).apk"
cp "$APK_SRC" "$RELEASE_DIR/$APK_DEST_NAME"

# --- Git commit ---
echo "📦 Committing $MODE APK to Git..."
git add -f "$RELEASE_DIR/$APK_DEST_NAME"
git commit -m "Auto-build: Add $APK_DEST_NAME" || true

# --- Git push ---
echo "☁️ Pushing to GitHub..."
if ! git push origin main; then
  echo "⚠️ Push failed. Attempting re-authorization..."
  read -p "👉 Enter your GitHub username: " GH_USER
  read -s -p "🔑 Enter your GitHub personal access token: " GH_TOKEN; echo ""
  NEW_URL="https://${GH_USER}:${GH_TOKEN}@github.com/TXAVL/TXAANDROID.git"
  git remote set-url origin "$NEW_URL"
  echo "🔁 Retrying push..."
  if git push origin main; then
    echo "✅ Push successful after re-auth."
  else
    echo "❌ Push failed again. Check token or network."
    exit 1
  fi
else
  echo "✅ Push successful."
fi

echo "🎉 Done! APK uploaded: releases/$APK_DEST_NAME"
