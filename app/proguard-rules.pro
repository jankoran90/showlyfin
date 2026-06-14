-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class com.github.jankoran90.showlyfin.** { *; }
# TEMPO Fáze C: NextLib FFmpeg dekodér používá JNI/native metody — R8 je nesmí odstranit/přejmenovat.
-keep class io.github.anilbeesetti.nextlib.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
