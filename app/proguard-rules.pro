# Strip noisy Log calls from release builds. Reduces APK size, prevents
# leaking session IDs / WS URLs / debug breadcrumbs through logcat on devices
# with userdebug ROMs or USB debugging enabled.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Keep kotlinx-serialization @Serializable classes (Retrofit body conversion
# breaks otherwise — R8 strips the @SerialName / @Serializable annotations
# without these rules).
-keepclassmembers,allowshrinking,allowobfuscation @kotlinx.serialization.Serializable class **
-keep,allowshrinking class kotlinx.serialization.Serializable
-keepclasseswithmembers class **$$serializer { *; }
-keep class **$Companion { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt — keep generated component / module entry points.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep @dagger.hilt.android.AndroidEntryPoint class *

# OkHttp — known minor R8 warnings; safe to ignore.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Retrofit — preserve generic interface info for response type parsing.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# AndroidX WebKit — WebViewAssetLoader path handlers reflectively touched.
-keep class androidx.webkit.** { *; }
