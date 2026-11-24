# 只重命名，不删代码、不优化（避免把靠反射走到的代码删掉或改坏）
-dontshrink
-dontoptimize
-keep class com.zframe.inject.ZInit { *; }
-keep class com.zframe.inject.Ed { int BUILD; }
-keepattributes *Annotation*,Signature,EnclosingMethod,InnerClasses
-dontwarn **
-dontnote **
