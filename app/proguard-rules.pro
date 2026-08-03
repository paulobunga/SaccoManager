# Project-specific ProGuard/R8 rules for SACCO Manager

# Keep entry points and Application class
-keep public class com.litesails.saccomanager.SaccoApp { *; }
-keep public class com.litesails.saccomanager.MainActivity { *; }

# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep class **$* { *; }

# Retrofit + Moshi
-keep class com.squareup.retrofit2.** { *; }
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers class ** {
    @com.squareup.moshi.Json <fields>;
}
-keepclassmembers class * {
    @com.squareup.moshi.Json <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Clerk
-keep class com.clerk.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }

# General
-keepattributes Signature
-keepattributes *Annotation*
