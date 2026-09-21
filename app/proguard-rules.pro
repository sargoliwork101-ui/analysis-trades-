# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.pulse.market.data.** { *; }
-keep class com.pulse.market.data.** { *; }

# Jsoup / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**
