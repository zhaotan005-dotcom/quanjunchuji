#!/bin/bash
set -e
cd "$(dirname "$0")"
E=/workspace/work/rkenv
export JAVA_HOME=$E/jdk-21.0.13+9; export PATH=$JAVA_HOME/bin:$PATH
AAPT2=/workspace/work/tools/android-16/aapt2
BT=/workspace/work/tools/android-16
AJ=/workspace/work/tools/android-36/android.jar
APKSIG=/workspace/work/tools/android-16/lib/apksigner.jar
VER=$(grep -o 'versionName="[^"]*"' AndroidManifest.xml | cut -d'"' -f2)
rm -rf build && mkdir -p build/classes build/dex build/inject build/res
javac --release 8 -encoding UTF-8 -nowarn -classpath $AJ:$APKSIG -d build/classes $(find src -name '*.java') 2>&1 | grep -v -E "^Note|warning: \[options\]|^[0-9]+ warnings?" || true
[ -f build/classes/com/zframe/MainActivity.class ]
D8="java -cp $BT/lib/d8.jar com.android.tools.r8.D8 --release --min-api 26 --lib $AJ"
# 注入到游戏里的 dex（游戏菜单 + 日志）
$D8 --output build/inject $(find build/classes/com/zframe/inject -name '*.class') build/classes/com/zframe/ui/Ui*.class
cp build/inject/classes.dex assets/inject.dex
# App 本体 dex（含 apksig 签名库）
$D8 --output build/dex $(find build/classes -name '*.class') $APKSIG
$AAPT2 compile --dir res -o build/res/res.zip
$AAPT2 link -o /tmp/zf_base.apk --manifest AndroidManifest.xml -I $AJ -R build/res/res.zip -A assets --auto-add-overlay --min-sdk-version 26 --target-sdk-version 36
cp /tmp/zf_base.apk build/base.apk; python3 repack.py build/base.apk build/dex/classes.dex build/unsigned.apk
java -jar $BT/lib/apksigner.jar sign --ks zframe.jks --ks-pass pass:zframe123 --min-sdk-version 26 --out ZFrame-v$VER.apk build/unsigned.apk
rm -f ZFrame-v$VER.apk.idsig
java -jar $BT/lib/apksigner.jar verify ZFrame-v$VER.apk && ls -la ZFrame-v$VER.apk
