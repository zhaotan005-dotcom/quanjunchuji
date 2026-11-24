#!/bin/bash
set -e
export JAVA_HOME=/workspace/work/rkenv/jdk-21.0.13+9; export PATH="$JAVA_HOME/bin:$PATH"
AJ=/workspace/work/tools/android-36/android.jar; BT=/workspace/work/tools/android-16; APKSIG=$BT/lib/apksigner.jar
R=/workspace/work/quanjunchuji; Z=$R/zframe; O=/workspace/work/full; MAPS=$R/notes/obf-maps
mkdir -p $O $MAPS
cd $Z
# 版本：CORE_VER 329->330, VER v4.27->v4.28
sed -i 's/CORE_VER = 329;/CORE_VER = 330;/' src/com/zframe/inject/ZInit.java
sed -i 's/VER = "v4.27"/VER = "v4.28"/' src/com/zframe/inject/GameOverlay.java
for e in 1 2 0; do
 n=$([ $e = 1 ] && echo 服务端 || ([ $e = 0 ] && echo 单机 || echo 客户端))
 sed -i "s/static final int BUILD = [0-9];/static final int BUILD = $e;/" src/com/zframe/inject/Ed.java
 rm -rf build/classes build/obf; mkdir -p build/classes build/obf
 javac --release 8 -encoding UTF-8 -nowarn -classpath $AJ:$APKSIG -d build/classes $(find src -name '*.java') 2>&1 | grep -v -E "warning|^Note|^[0-9]+ warn" || true
 INJ=$(find build/classes/com/zframe/inject -name '*.class'); UI=$(ls build/classes/com/zframe/ui/Ui*.class)
 java -cp $BT/lib/d8.jar com.android.tools.r8.R8 --release --min-api 26 --pg-conf /workspace/work/hostpatch/zf.pro --lib $AJ --output build/obf --pg-map-output $MAPS/map-b$e-core330.txt $INJ $UI 2>&1 | grep -viE "^Warning|Note:" || true
 cp build/obf/classes.dex $R/core/b$e.dex   # 本地产物；是否推线上稍后决定
 echo "obf b$e core330 $(wc -c < build/obf/classes.dex) bytes"
done
# 用服务端(b1)/客户端(b2) 打整包
for e in 1 2; do
 n=$([ $e = 1 ] && echo 服务端 || echo 客户端)
 rm -rf /workspace/work/hostpatch/owd$e
 java -Xmx3g -cp "/workspace/work/hostpatch/cls:$AJ:$APKSIG" Host /workspace/work/game_apk/game.apk $R/core/b$e.dex "$O/全军出击-ZFrame-v4.28-$n-整包-混淆.apk" /workspace/work/hostpatch/owd$e $Z/assets/gkey.pk8 $Z/assets/gcert.der
done
ls -la $O/*v4.28*
