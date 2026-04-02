# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep model classes
-keep class com.croakvpn.model.** { *; }

# Keep service
-keep class com.croakvpn.service.** { *; }
