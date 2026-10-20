#!/bin/bash
# 屏幕译 构建脚本（不依赖 Gradle）：aapt2 → javac → d8 → zipalign → apksigner
# 可用环境变量（都可选）：
#   JAVA_HOME        JDK 17+ 路径（默认：PATH 里的 javac）
#   ANDROID_HOME     Android SDK 根目录（也认 ANDROID_SDK_ROOT）
#   BUILD_TOOLS      build-tools 目录（含 aapt2/d8/zipalign/apksigner；默认 $ANDROID_HOME/build-tools 下最新版）
#   ANDROID_JAR      android.jar（默认 $ANDROID_HOME/platforms 下最新的 android-*/android.jar，需 API 34+）
#   KEYSTORE         签名证书（默认 ./keystore.jks；不存在时自动生成一个本地调试证书）
#   KEYSTORE_PASS    证书库密码（用自己的证书时必填；默认的 ./keystore.jks 本地证书为 localdebug）
#   KEY_ALIAS        别名（默认 key）
#   KEY_PASS         私钥密码（默认同 KEYSTORE_PASS）
#   OUT              输出 APK（默认 out/screen-translator.apk）
#   DEEPSEEK_API_KEY 若设置，会被写进 APK（BuildConfig.KEY）。这样的 APK 只能自己用，切勿公开发布！
set -eo pipefail; cd "$(dirname "$0")"
PKG_DIR=io/github/locker95/screentranslator
die(){ echo "错误: $*" >&2; exit 1; }

# ---- JDK ----
if [ -z "$JAVA_HOME" ]; then
  command -v javac >/dev/null 2>&1 && JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
fi
[ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ] || die "找不到 JDK（javac）。请安装 JDK 17+ 并设置 JAVA_HOME"
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

# ---- Android SDK ----
SDK=${ANDROID_HOME:-$ANDROID_SDK_ROOT}
if [ -z "$BUILD_TOOLS" ]; then
  [ -n "$SDK" ] && [ -d "$SDK/build-tools" ] && BUILD_TOOLS=$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)
fi
BUILD_TOOLS=${BUILD_TOOLS%/}
for t in aapt2 d8 zipalign apksigner; do [ -n "$BUILD_TOOLS" ] && [ -x "$BUILD_TOOLS/$t" ] || die "BUILD_TOOLS 里缺少 $t（当前: ${BUILD_TOOLS:-未设置}）。请设置 ANDROID_HOME 或 BUILD_TOOLS"; done
if [ -z "$ANDROID_JAR" ]; then
  [ -n "$SDK" ] && ANDROID_JAR=$(ls "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)
fi
[ -n "$ANDROID_JAR" ] && [ -f "$ANDROID_JAR" ] || die "找不到 android.jar（需 API 34+）。请设置 ANDROID_HOME 或 ANDROID_JAR"
BT=$BUILD_TOOLS; AJ=$ANDROID_JAR
echo "JDK: $JAVA_HOME"; echo "build-tools: $BT"; echo "android.jar: $AJ"

# ---- 签名证书 ----
KEYSTORE=${KEYSTORE:-./keystore.jks}; KEY_ALIAS=${KEY_ALIAS:-key}
if [ ! -f "$KEYSTORE" ]; then
  export KEYSTORE_PASS=${KEYSTORE_PASS:-localdebug}
  echo "未找到 $KEYSTORE，生成一个新的本地调试证书（它与官方 Release 的证书不同，不能覆盖安装官方 APK）"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass:env KEYSTORE_PASS -keypass:env KEYSTORE_PASS -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=screen-translator local debug"
fi
[ "$KEYSTORE" = ./keystore.jks ] && KEYSTORE_PASS=${KEYSTORE_PASS:-localdebug}  # 自动生成的本地证书
[ -n "$KEYSTORE_PASS" ] || die "使用证书 $KEYSTORE 时必须设置 KEYSTORE_PASS"
export KEYSTORE_PASS KEY_PASS=${KEY_PASS:-$KEYSTORE_PASS}
OUT=${OUT:-out/screen-translator.apk}

# ---- 构建 ----
rm -rf build; rm -f "$OUT"; mkdir -p build/gen/$PKG_DIR build/cls build/dex "$(dirname "$OUT")"
python3 - "$PKG_DIR" <<'PY'
import os,json,sys
k=os.environ.get("DEEPSEEK_API_KEY","")
print("BuildConfig.KEY:", "已内置（个人版，勿公开）" if k else "空（Release 版，需在 App 内填写 Key）")
open("build/gen/%s/BuildConfig.java"%sys.argv[1],"w").write("package io.github.locker95.screentranslator;\npublic final class BuildConfig { public static final String KEY = %s; }\n"%json.dumps(k))
PY
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$AJ" --manifest AndroidManifest.xml -A assets -o build/base.apk build/res.zip --java build/gen
javac --release 8 -nowarn -cp "$AJ" -d build/cls $(find src build/gen -name '*.java') > build/javac.log 2>&1 || { grep -v "^warning\|^Note" build/javac.log >&2; die "javac 编译失败"; }
grep -v "^warning\|^Note" build/javac.log || true
"$BT/d8" --min-api 26 --lib "$AJ" --output build/dex $(find build/cls -name '*.class')
cp build/base.apk build/u.apk; python3 -c "import zipfile;zipfile.ZipFile('build/u.apk','a').write('build/dex/classes.dex','classes.dex')"
"$BT/zipalign" -f 4 build/u.apk build/a.apk
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass env:KEYSTORE_PASS --key-pass env:KEY_PASS --ks-key-alias "$KEY_ALIAS" --out "$OUT" build/a.apk
"$BT/apksigner" verify -v "$OUT" | head -5
rm -rf build/gen
echo "输出: $OUT"
