-dontobfuscate
-keepattributes SourceFile, LineNumberTable
-dontwarn arc.**
-dontwarn mindustry.**

-dontwarn floodcompat.EditDrawers$*

-keep class floodcompat.FloodCompat { *; }
-keep class floodcompat.SoundUtils { *; }
-keep class floodcompat.SettingCache { *; }
-keep class floodcompat.EditDrawers { *; }
-keep class floodcompat.EditDrawers$* { *; }
