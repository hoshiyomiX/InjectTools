# InjectTools ProGuard Rules

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep InjectToolsNative class
-keep class com.hoshiyomi.injecttools.core.InjectToolsNative {
    *;
}

# Keep data classes for serialization
-keep class com.hoshiyomi.injecttools.data.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Compose
-keep class androidx.compose.** { *; }
-keep class androidx.navigation.** { *; }

# General Android
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
