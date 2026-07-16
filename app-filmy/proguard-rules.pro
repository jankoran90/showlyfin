-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
# CELLULOID — vlastní namespace appky Filmy…
-keep class com.github.jankoran90.filmy.** { *; }
# …i všechny SDÍLENÉ moduly (core/ui-tv/feature/data), které žijí pod původním namespace showlyfin.
-keep class com.github.jankoran90.showlyfin.** { *; }
# TEMPO Fáze C: NextLib FFmpeg dekodér používá JNI/native metody — R8 je nesmí odstranit/přejmenovat.
-keep class io.github.anilbeesetti.nextlib.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
