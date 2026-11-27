#!/usr/bin/env bash
# Muse YouTube Patches (.mpp) build script — no Gradle.
# Same hand-build pipeline as Muse Patches (X): see ../muse-patches-project/build.sh.
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${VERSION:-0.1.0}"

JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk17}"
GRADLE_LIB="${GRADLE_LIB:-$HOME/tools/gradle-8.13/lib}"
MORPHE_JAR="${MORPHE_JAR:-$HOME/workspace/piko-build/morphe.jar}"
ANDROID_JAR="${ANDROID_JAR:-$HOME/android-sdk/platforms/android-36/android.jar}"
D8="${D8:-$HOME/android-sdk/build-tools/36.0.0/d8}"
ASSETS="${ASSETS:-$HOME/workspace/muse-patches-project/assets}"

for f in "$MORPHE_JAR" "$ANDROID_JAR" "$GRADLE_LIB/kotlin-compiler-embeddable-2.0.21.jar"; do
  [ -f "$f" ] || { echo "missing dependency: $f" >&2; exit 1; }
done
export PATH="$JAVA_HOME/bin:$PATH"

OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/classes-ext" "$OUT/dex-ext" "$OUT/dex-patch" "$OUT/pkg/extensions"

# --- 1) Extension (Java) -> extensions/muse-patches.dex ---
"$JAVA_HOME/bin/javac" -encoding UTF-8 -cp "$ANDROID_JAR" -d "$OUT/classes-ext" $(find src-java -name '*.java')
"$JAVA_HOME/bin/jar" --create --file "$OUT/ext.jar" -C "$OUT/classes-ext" .
"$D8" --output "$OUT/dex-ext" "$OUT/ext.jar"
cp "$OUT/dex-ext/classes.dex" "$OUT/pkg/extensions/muse-patches.dex"

# --- 2) Patches (Kotlin) -> patches.jar ---
KOTLINC_CP="$GRADLE_LIB/kotlin-compiler-embeddable-2.0.21.jar"
KOTLINC_CP="$KOTLINC_CP:$GRADLE_LIB/kotlin-stdlib-2.0.21.jar"
KOTLINC_CP="$KOTLINC_CP:$GRADLE_LIB/kotlinx-coroutines-core-jvm-1.6.4.jar"
KOTLINC_CP="$KOTLINC_CP:$GRADLE_LIB/trove4j-1.0.20200330.jar"
KOTLINC_CP="$KOTLINC_CP:$GRADLE_LIB/annotations-24.0.1.jar"
"$JAVA_HOME/bin/java" -cp "$KOTLINC_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -cp "$MORPHE_JAR:$GRADLE_LIB/kotlin-stdlib-2.0.21.jar" \
  -Xcontext-receivers -Xskip-metadata-version-check -Xskip-prerelease-check \
  -jvm-target 11 \
  -d "$OUT/patches.jar" $(find src-kt -name '*.kt')

# --- 3) Bundle-root classes.dex ---
"$D8" --output "$OUT/dex-patch" "$OUT/patches.jar"
cp "$OUT/dex-patch/classes.dex" "$OUT/pkg/classes.dex"

# --- 4) Package the .mpp (plain zip) ---
(cd "$OUT/pkg" && "$JAVA_HOME/bin/jar" xf ../patches.jar && rm -f META-INF/MANIFEST.MF)
mkdir -p "$OUT/pkg/META-INF"
cat > "$OUT/pkg/META-INF/MANIFEST.MF" <<EOF
Manifest-Version: 1.0
Name: YouTube追加パッチ
Description: Standalone YouTube patch: Hide status bar on scroll
Version: $VERSION
Author: Muse patches
License: GNU General Public License v3.0
Patcher-Version: 2.0.1
EOF
cp "$ASSETS/LICENSE" "$ASSETS/NOTICE" "$OUT/pkg/"
(cd "$OUT/pkg" && zip -qr "../muse-youtube-patches-$VERSION.mpp" .)

echo "built: $OUT/muse-youtube-patches-$VERSION.mpp"
