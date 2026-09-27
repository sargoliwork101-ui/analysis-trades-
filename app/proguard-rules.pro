# kotlinx.serialization generates direct serializer references; retaining the whole data
# package or all annotation/inner-class metadata disables useful R8 shrinking.
-dontnote kotlinx.serialization.**

# Jsoup / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**
