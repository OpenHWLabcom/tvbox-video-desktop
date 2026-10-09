#!/bin/bash
# 构建「桌面」APK (com.openhwlab.desktop)
set -e
cd "$(dirname "$0")"
AB="${ANDROID_BUILD:-$HOME/android-build}"   # 你的工具链目录(jdk8/aapt/d8/android.jar)
export JAVA_HOME=$AB/jdk8u504-b01
export PATH=$JAVA_HOME/bin:$PATH
export LD_LIBRARY_PATH=$AB/aapt81/usr/lib/aarch64-linux-gnu:$AB/aapt81/usr/lib/aarch64-linux-gnu/android
AAPT=$AB/aapt81/usr/bin/aapt
AJ=$AB/plat/android-4.4.2/android.jar
D8=$AB/bt/android-11/lib/d8.jar

rm -rf classes out desktop.unsigned.apk 桌面.apk
mkdir -p classes out

echo "[1/5] javac"
javac -source 7 -target 7 -bootclasspath "$AJ" -d classes src/com/openhwlab/desktop/*.java

echo "[2/5] d8"
java -cp "$D8" com.android.tools.r8.D8 --min-api 19 --lib "$AJ" \
     --output out classes/com/openhwlab/desktop/*.class

echo "[3/5] aapt package"
$AAPT package -f -M AndroidManifest.xml -I "$AJ" -A assets -F desktop.unsigned.apk

echo "[4/5] add classes.dex"
( cd out && $AAPT add ../desktop.unsigned.apk classes.dex )

echo "[5/5] sign"
if [ ! -f desktop.keystore ]; then
  keytool -genkeypair -keystore desktop.keystore -alias k -keyalg RSA -keysize 2048 \
      -validity 10000 -storepass android -keypass android \
      -dname "CN=OpenHW Lab, OU=dev, O=OpenHW Lab, L=SG, S=SG, C=SG"
fi
jarsigner -keystore desktop.keystore -storepass android -keypass android desktop.unsigned.apk k
cp -f desktop.unsigned.apk 桌面.apk
echo "=== OK ==="
ls -l 桌面.apk
$AAPT dump badging 桌面.apk 2>/dev/null | head -6
