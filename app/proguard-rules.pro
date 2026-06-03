# Retrofit2
-keepattributes Signature
-keepattributes *Annotation*
-keep class cross.stick.data.model.** { *; }
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
