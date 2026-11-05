#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
SDK="$ANDROID_HOME/platforms/android-35/android.jar"
BUILD="$ANDROID_HOME/build-tools/35.0.0"
test -f "$SDK"
test -x "$BUILD/aapt2"
test -x "$BUILD/d8"
mkdir -p build/classes build/dex build/intermediate build/output
base64 -d app/src/main/res/drawable/cajuz_icon.png.b64 > app/src/main/res/drawable/cajuz_icon.png
"$BUILD/aapt2" compile -o build/intermediate/resources.zip app/src/main/res/drawable/cajuz_icon.png
"$BUILD/aapt2" link -o build/intermediate/unsigned.apk -I "$SDK" --manifest app/src/main/AndroidManifest.xml -A app/src/main/assets --min-sdk-version 26 --target-sdk-version 35 --version-code 3 --version-name 0.2.1 -R build/intermediate/resources.zip --auto-add-overlay
mapfile -t sources < <(find app/src/main/java -name '*.java' -type f | sort)
javac -encoding UTF-8 --release 8 -classpath "$SDK" -d build/classes "${sources[@]}"
mapfile -t classes < <(find build/classes -name '*.class' -type f | sort)
"$BUILD/d8" --lib "$SDK" --min-api 26 --output build/dex "${classes[@]}"
(cd build/dex && zip -q ../intermediate/unsigned.apk classes.dex)
"$BUILD/zipalign" -f 4 build/intermediate/unsigned.apk build/intermediate/aligned.apk
keytool -genkeypair -keystore build/intermediate/test.keystore -storepass android -keypass android -alias cajuz-dev -keyalg RSA -keysize 3072 -validity 3650 -dname "CN=Cajuz APK Test, O=Cajuz" -noprompt
"$BUILD/apksigner" sign --ks build/intermediate/test.keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias cajuz-dev --out build/output/Cajuz-Android-0.2.1.apk build/intermediate/aligned.apk
"$BUILD/apksigner" verify --verbose build/output/Cajuz-Android-0.2.1.apk
"$BUILD/aapt2" dump badging build/output/Cajuz-Android-0.2.1.apk | head -8
