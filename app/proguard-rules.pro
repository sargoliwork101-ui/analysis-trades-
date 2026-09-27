# kotlinx.serialization generates direct serializer references; retaining the whole data
# package disabled much of R8's member shrinking and unnecessarily enlarged the APK.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Jsoup / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**
