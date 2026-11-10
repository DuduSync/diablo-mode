#!/bin/sh
# Build DiabloMode.apk without Gradle/Android Studio.
# Usage: ./build.sh <android.jar> <kotlin-stdlib.jar>
#
# Needs: aapt2, d8, apksigner, zipalign, kotlinc, JDK 17+.
# Tested on Termux (aarch64) with the Android build-tools and openjdk.

set -e

AJ="${1:?path to android.jar (an SDK platform, API 26+)}"
SL="${2:?path to kotlin-stdlib.jar}"

OUT=build
TABLE=keystore.jks
ALIAS=diablomode
PASS=diablomode

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/res"

echo "== compile resources =="
aapt2 compile -o "$OUT/res.zip" app/res/drawable/icon.png

echo "== link (manifest + assets + icon) =="
aapt2 link -o "$OUT/base.apk" \
  -I "$AJ" \
  --manifest app/AndroidManifest.xml \
  -A app/assets \
  -R "$OUT/res.zip" --auto-add-overlay \
  --min-sdk-version 26 --target-sdk-version 34

echo "== kotlinc =="
kotlinc -cp "$AJ" -jvm-target 1.8 -d "$OUT/classes" \
  app/src/main/java/com/dudusync/diablomode/MainActivity.kt \
  app/src/main/java/com/dudusync/diablomode/BarView.kt

echo "== d8 =="
d8 --release --min-api 26 --lib "$AJ" --output "$OUT" \
  "$SL" $(find "$OUT/classes" -name '*.class')

echo "== package + align =="
( cd "$OUT" && zip -q base.apk classes.dex )
zipalign -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "== sign =="
if [ ! -f "$TABLE" ]; then
  keytool -genkeypair -keystore "$TABLE" -alias "$ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$PASS" -keypass "$PASS" \
    -dname "CN=Diablo Mode, O=DuduSync, C=BR"
fi
apksigner sign --ks "$TABLE" --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
  --out DiabloMode.apk "$OUT/aligned.apk"

echo "== done: DiabloMode.apk =="
ls -l DiabloMode.apk
