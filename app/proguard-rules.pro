# Compose rules
-keepattributes *Annotation*
-dontwarn androidx.compose.**

# Google Play Services Auth
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
