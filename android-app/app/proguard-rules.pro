# TCM Admin Proguard / R8 Optimization Rules

# Preserve Kotlin Reflection & Serialization metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Navigation Routes for Navigation Compose & Kotlinx Serialization
-keep class com.tcm.admin.Route** { *; }
-keepclassmembers class com.tcm.admin.Route** {
    *** Companion;
    *** $serializer;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Hilt & Dagger
-dontwarn dagger.hilt.**
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }

# MLKit Barcode Scanning & CameraX
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn androidx.camera.**

# Apache Commons Compress (used by BsPatch)
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
