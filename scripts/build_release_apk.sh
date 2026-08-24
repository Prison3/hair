#!/usr/bin/env bash
# 编译 Android release APK，复制到 server/downloads/hairclinic.apk，并上传到服务器
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLIENT="$ROOT/android"
OUT="$ROOT/server/downloads"
APK_NAME="hairclinic.apk"
REMOTE_HOST="${HAIR_CLINIC_UPLOAD_HOST:-root@cn}"
REMOTE_DIR="${HAIR_CLINIC_UPLOAD_DIR:-/root/hair-clinic/server/downloads}"
REMOTE="${REMOTE_HOST}:${REMOTE_DIR}/${APK_NAME}"

find_gradle() {
  if [[ -x "$CLIENT/gradlew" ]]; then
    echo "$CLIENT/gradlew"
    return
  fi
  local bin=""
  bin="$(find "$HOME/.gradle/wrapper/dists/gradle-8.14.5-bin" -path '*/bin/gradle' -type f 2>/dev/null | head -1 || true)"
  if [[ -z "$bin" ]]; then
    bin="$(find "$HOME/.gradle/wrapper/dists" -path '*/bin/gradle' -type f 2>/dev/null | sort | tail -1 || true)"
  fi
  if [[ -n "$bin" ]]; then
    echo "$bin"
    return
  fi
  if command -v gradle >/dev/null 2>&1; then
    command -v gradle
    return
  fi
  return 1
}

cd "$CLIENT"
GRADLE_BIN="$(find_gradle)" || {
  echo "找不到 Gradle，请先用 Android Studio 同步工程，或安装 Gradle Wrapper" >&2
  exit 1
}

if [[ "$GRADLE_BIN" == "$CLIENT/gradlew" ]]; then
  "$GRADLE_BIN" :app:assembleRelease || {
    FALLBACK="$(find "$HOME/.gradle/wrapper/dists" -path '*/bin/gradle' -type f 2>/dev/null | sort | tail -1 || true)"
    if [[ -n "$FALLBACK" ]]; then
      "$FALLBACK" :app:assembleRelease
    else
      exit 1
    fi
  }
else
  "$GRADLE_BIN" :app:assembleRelease
fi

BUILT="$(find "$CLIENT/app/build/outputs/apk/release" -name '*.apk' -type f | head -1)"
if [[ -z "$BUILT" ]]; then
  echo "未找到 release APK" >&2
  exit 1
fi

mkdir -p "$OUT"
cp "$BUILT" "$OUT/$APK_NAME"

VERSION_CODE="$(git -C "$ROOT" rev-list --count HEAD)"
VERSION_NAME="${VERSION_CODE} - $(git -C "$ROOT" describe --tags --always)"
cat > "$OUT/app_version.json" <<EOF
{"version_code": ${VERSION_CODE}, "version_name": "${VERSION_NAME}"}
EOF

echo "Release APK: $OUT/$APK_NAME (v${VERSION_NAME})"
ls -lh "$OUT/$APK_NAME"

if [[ "${HAIR_CLINIC_UPLOAD:-1}" != "0" ]]; then
  echo "上传到 $REMOTE ..."
  ssh "$REMOTE_HOST" "mkdir -p '$REMOTE_DIR'"
  scp "$OUT/$APK_NAME" "$OUT/app_version.json" "$REMOTE_HOST:$REMOTE_DIR/"
  echo "上传完成"
fi
