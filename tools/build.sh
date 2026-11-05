#!/bin/bash
# Usage: build.sh <project dir> <versionCode> <versionName> <out.apk> [minSdk]
set -e
T=$(cd "$(dirname "$0")" && pwd)
P=$(cd "$1" && pwd); VC=$2; VN=$3; OUT=$4; MIN=${5:-24}
B="$P/build"; rm -rf "$B"; mkdir -p "$B/classes"
"$T/aapt2" compile --dir "$P/res" -o "$B/res.zip"
ASSETS=""; [ -d "$P/assets" ] && ASSETS="-A $P/assets"
"$T/aapt2" link -I "$T/android.jar" --manifest "$P/AndroidManifest.xml" $ASSETS --min-sdk-version $MIN --target-sdk-version 35 \
  -0 ttf --version-code $VC --version-name "$VN" --java "$B/gen" "$B/res.zip" -o "$B/base.apk"
if [ -d "$P/src" ]; then
find -L "$P/src" "$B/gen" -name '*.java' > "$B/src.txt"
javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$T/android.jar" -classpath "$T/android.jar" -d "$B/classes" @"$B/src.txt" 2>&1 | grep -v "^warning\|JAVA_TOOL_OPTIONS\|^Note:" || true
[ -n "$(find "$B/classes" -name '*.class' | head -1)" ] || { echo "javac failed"; exit 1; }
java -jar "$T/dx.jar" --dex --min-sdk-version=$MIN --output="$B/classes.dex" "$B/classes" 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
DEX="$B/classes.dex"
else
DEX="-"
fi
python3 "$T/signapk.py" "$B/base.apk" "$DEX" "$T/watchman.p12" "${KS_PASS:-watchman}" "$OUT"
"$T/aapt2" dump badging "$OUT" | head -4
