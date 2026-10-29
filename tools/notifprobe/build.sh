#!/usr/bin/env bash
# Compila NotifProbe sin Gradle. Requiere JAVA_HOME (JDK 17+), ANDROID_HOME con
# platforms;android-35 y build-tools;35.0.0, y una keystore de depuración.
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?define ANDROID_HOME}"
BT="$ANDROID_HOME/build-tools/35.0.0"
J="$ANDROID_HOME/platforms/android-35/android.jar"
KS="${KEYSTORE:-$HOME/.android/debug.keystore}"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
rm -rf out && mkdir -p out/c out/gen out/dex
"$BT/aapt2" compile --dir res -o out/res.zip
"$BT/aapt2" link -I "$J" --manifest AndroidManifest.xml -o out/u.apk --java out/gen out/res.zip
"$JAVAC" -nowarn -source 17 -target 17 -cp "$J" -d out/c $(find src out/gen -name '*.java')
"$BT/d8" --lib "$J" --min-api 26 --output out/dex $(find out/c -name '*.class')
python3 -I -c "import zipfile,sys; z=zipfile.ZipFile(sys.argv[1],'a'); z.write(sys.argv[2],'classes.dex'); z.close()" out/u.apk out/dex/classes.dex
"$BT/zipalign" -f 4 out/u.apk out/a.apk
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --out out/notifprobe.apk out/a.apk
echo "APK: $(pwd)/out/notifprobe.apk"
