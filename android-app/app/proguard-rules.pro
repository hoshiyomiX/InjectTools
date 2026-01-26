# Add project specific ProGuard rules here.

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep InjectToolsNative
-keep class com.hoshiyomi.injecttools.core.InjectToolsNative {
    *;
}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes
-keep,allowobfuscation,allowshrinking class com.hoshiyomi.injecttools.** {
    <fields>;
}

# Compose
-keep class androidx.compose.** { *; }
