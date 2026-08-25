#!/usr/bin/env bash
# 编译 Android release APK，复制到 server/downloads/hairclinic.apk，并上传到服务器
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLIENT="$ROOT/android"
OUT="$ROOT/server/downloads"
APK_NAME="hairclinic.apk"
REMOTE_HOST="${HAIR_CLINIC_UPLOAD_HOST:-root@S1}"
REMOTE_DIR="${HAIR_CLINIC_UPLOAD_DIR:-/root/hair-clinic/server/downloads}"
REMOTE="${REMOTE_HOST}:${REMOTE_DIR}/${APK_NAME}"
CLEAN="${HAIR_CLINIC_CLEAN:-1}"

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

# 关闭本机代理，避免 Gradle 走 127.0.0.1:7897 失败
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY all_proxy ALL_PROXY ALL_PROXY no_proxy NO_PROXY
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttp.proxyHost= -Dhttps.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyPort="

GRADLE_BIN="$(find_gradle)" || {
  echo "找不到 Gradle，请先用 Android Studio 同步工程，或安装 Gradle Wrapper" >&2
  exit 1
}

VERSION_CODE="$(git -C "$ROOT" rev-list --count HEAD)"
VERSION_NAME="${VERSION_CODE} - $(git -C "$ROOT" describe --tags --always)"
HEAD_SHA="$(git -C "$ROOT" rev-parse --short HEAD)"
echo "准备编译 HEAD=${HEAD_SHA} versionCode=${VERSION_CODE} versionName=${VERSION_NAME}"
echo "使用 Gradle: $GRADLE_BIN"

# 默认先 clean，避免增量编译漏掉资源/代码变更
GRADLE_TASKS=()
if [[ "$CLEAN" != "0" ]]; then
  GRADLE_TASKS+=(:app:clean)
fi
GRADLE_TASKS+=(:app:assembleRelease)

run_gradle() {
  local bin="$1"
  shift
  "$bin" "$@"
}

if [[ "$GRADLE_BIN" == "$CLIENT/gradlew" ]]; then
  run_gradle "$GRADLE_BIN" "${GRADLE_TASKS[@]}" || {
    FALLBACK="$(find "$HOME/.gradle/wrapper/dists" -path '*/bin/gradle' -type f 2>/dev/null | sort | tail -1 || true)"
    if [[ -n "$FALLBACK" ]]; then
      echo "gradlew 失败，改用 $FALLBACK" >&2
      run_gradle "$FALLBACK" "${GRADLE_TASKS[@]}"
    else
      exit 1
    fi
  }
else
  run_gradle "$GRADLE_BIN" "${GRADLE_TASKS[@]}"
fi

RELEASE_DIR="$CLIENT/app/build/outputs/apk/release"
BUILT="$(find "$RELEASE_DIR" -name '*.apk' -type f -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -1 || true)"
if [[ -z "$BUILT" || ! -f "$BUILT" ]]; then
  echo "未找到 release APK（目录: $RELEASE_DIR）" >&2
  exit 1
fi

META="$RELEASE_DIR/output-metadata.json"
if [[ -f "$META" ]]; then
  BUILT_CODE="$(python3 -c "import json; print(json.load(open('$META'))['elements'][0]['versionCode'])" 2>/dev/null || true)"
  BUILT_NAME="$(python3 -c "import json; print(json.load(open('$META'))['elements'][0]['versionName'])" 2>/dev/null || true)"
  echo "Gradle 产出: $BUILT"
  echo "  versionCode=${BUILT_CODE:-?} versionName=${BUILT_NAME:-?}"
  if [[ -n "${BUILT_CODE:-}" && "$BUILT_CODE" != "$VERSION_CODE" ]]; then
    echo "警告: APK versionCode(${BUILT_CODE}) 与当前 git 提交数(${VERSION_CODE}) 不一致" >&2
  fi
  if [[ -n "${BUILT_NAME:-}" && "$BUILT_NAME" != *"$HEAD_SHA"* ]]; then
    echo "警告: APK versionName(${BUILT_NAME}) 未包含当前 HEAD(${HEAD_SHA})，可能不是本次代码" >&2
  fi
fi

mkdir -p "$OUT"
cp -f "$BUILT" "$OUT/$APK_NAME"
# 确保下载文件时间戳更新，避免 CDN/缓存误判
touch "$OUT/$APK_NAME"

cat > "$OUT/app_version.json" <<EOF
{"version_code": ${VERSION_CODE}, "version_name": "${VERSION_NAME}"}
EOF

SHA="$(shasum -a 256 "$OUT/$APK_NAME" | awk '{print $1}')"
echo "本地 Release APK 已就绪:"
echo "  路径: $OUT/$APK_NAME"
echo "  版本: v${VERSION_NAME}"
echo "  SHA256: $SHA"
ls -lh "$OUT/$APK_NAME"

if [[ "${HAIR_CLINIC_UPLOAD:-1}" != "0" ]]; then
  echo "上传到 $REMOTE ..."
  if ssh "$REMOTE_HOST" "mkdir -p '$REMOTE_DIR'" \
    && scp "$OUT/$APK_NAME" "$OUT/app_version.json" "$REMOTE_HOST:$REMOTE_DIR/"; then
    echo "上传完成: $REMOTE"
  else
    echo "上传失败（本地 APK 已生成）。可稍后重试，或设置 HAIR_CLINIC_UPLOAD=0 仅本地构建。" >&2
    exit 2
  fi
else
  echo "已跳过上传（HAIR_CLINIC_UPLOAD=0）"
fi
