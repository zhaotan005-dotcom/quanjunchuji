#!/bin/bash
# v2.13+：一次打三个版本。用法：./build3.sh <旧版本> <新版本> <旧vc> <新vc>   （旧 APK = ../releases/ZFrame-v<旧>-单机.apk 或 ZFrame-v<旧>.apk）
set -e
O=$1; N=$2; OC=$3; NC=$4
export JAVA_HOME=/workspace/work/rkenv/jdk-21.0.12.1+1 PATH=/workspace/work/rkenv/jdk-21.0.12.1+1/bin:$PATH
AJ=/workspace/work/tools/android-36/android.jar; BT=/workspace/work/tools/android-16; APKSIG=$BT/lib/apksigner.jar
D8="java -cp $BT/lib/d8.jar com.android.tools.r8.D8 --release --min-api 26 --lib $AJ"
BASE=../releases/ZFrame-v$O-单机.apk; [ -f "$BASE" ] || BASE=../releases/ZFrame-v$O.apk
sed -i "s/VER = \"v[0-9.]*\"/VER = \"v$N\"/" src/com/zframe/inject/GameOverlay.java
sed -i "s/ZFrame v[0-9.]* \" + Ed.name()/ZFrame v$N \" + Ed.name()/" src/com/zframe/inject/AvatarLog.java
sed -i "s/CORE_VER = [0-9]*;/CORE_VER = $NC;/" src/com/zframe/inject/ZInit.java
for e in 1 2 0; do
 n=$([ $e = 0 ] && echo 单机 || ([ $e = 1 ] && echo 服务端 || echo 客户端))
 sed -i "s/static final int ED = [0-9];/static final int ED = $e;/" src/com/zframe/inject/Ed.java
 rm -rf build/classes build/dex build/inject; mkdir -p build/classes build/dex build/inject
 javac --release 8 -encoding UTF-8 -nowarn -classpath $AJ:$APKSIG -d build/classes $(find src -name '*.java') 2>&1 | grep -v -E "^Note|warning: \[options\]|^[0-9]+ warnings?" || true
 $D8 --output build/inject $(find build/classes/com/zframe/inject -name '*.class') build/classes/com/zframe/ui/Ui*.class
 cp build/inject/classes.dex assets/inject.dex
 $D8 --output build/dex $(find build/classes -name '*.class') $APKSIG
 python3 rel.py "$BASE" build/unsigned.apk $O $N $OC $NC $n >/dev/null
 java -jar $APKSIG sign --ks zframe.jks --ks-pass pass:zframe123 --min-sdk-version 26 --out "../releases/ZFrame-v$N-$n.apk" build/unsigned.apk
 echo "built $n"
done
rm -f ../releases/*.idsig
