-dontobfuscate
-keepattributes SourceFile, LineNumberTable
-dontwarn arc.**
-dontwarn mindustry.**

-dontwarn floodcompat.EditDrawers$1
-dontwarn floodcompat.EditDrawers$2


-keep class floodcompat.FloodCompat { *; }
-keep class floodcompat.EditDrawers { *; }
-keep class floodcompat.EditDrawers$1 { *; }
-keep class floodcompat.EditDrawers$2 { *; }
-keep class floodcompat.EditDrawers$Data { *; }
