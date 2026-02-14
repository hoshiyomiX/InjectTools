# ==========================================
# InjectTools ProGuard/R8 Configuration
# ==========================================
# Optimized for security, size, and compatibility
# Last updated: 2026
#
# For more details, see:
#   http://developer.android.com/guide/developing/tools/proguard.html

# ==========================================
# GENERAL OPTIMIZATION SETTINGS
# ==========================================
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Optimization settings
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable

# Keep debugging info for crash reports
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Exception,Throws

# ==========================================
# CRITICAL FIX: TypeToken Generic Type Preservation
# ==========================================
# R8 removes generic type info, causing "TypeToken must be created with a type argument"
# This keeps the anonymous TypeToken subclass with its generic signature

# Keep all anonymous TypeToken subclasses (the `object : TypeToken<...>() {}` pattern)
-keep class * extends com.google.gson.reflect.TypeToken {
    <init>(...);
}

# Keep the generic signature of any class that uses TypeToken
-keepclassmembers class * {
    *** *(com.google.gson.reflect.TypeToken);
}

# Keep HistoryStorage class and its TypeToken usage
-keep class com.hoshiyomix.injecttools.HistoryStorage { *; }
-keepclassmembers class com.hoshiyomix.injecttools.HistoryStorage {
    *** loadHistoryDebug(...);
    *** loadHistory(...);
    *** saveHistory(...);
}

# ==========================================
# GSON SERIALIZATION - CRITICAL FOR HISTORY
# ==========================================
# Keep all data classes used with Gson serialization
-keep class com.hoshiyomix.injecttools.Scanner$ScanResult { *; }
-keep class com.hoshiyomix.injecttools.Crtsh$CrtShEntry { *; }
-keep class com.hoshiyomix.injecttools.MenuTile { *; }

# Keep all fields in data classes for Gson
-keepclassmembers class com.hoshiyomix.injecttools.Scanner$ScanResult {
    <fields>;
}
-keepclassmembers class com.hoshiyomix.injecttools.Crtsh$CrtShEntry {
    <fields>;
}

# Gson requires these attributes for reflection
-keepattributes SourceFile,LineNumberTable

# Gson generic type resolution
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ==========================================
# KOTLIN SUPPORT
# ==========================================
# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }
-keep class kotlin.Unit { *; }

# Keep Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ==========================================
# ANDROID COMPONENTS
# ==========================================
# --- OPTIMIZATION FIX ---
# REMOVED: -keep class androidx.compose.** { *; }
# Why: This rule forces R8 to keep ALL Compose classes, including 
# the entire material-icons-extended library (~50MB+).
# Compose has its own consumer proguard rules that handle this automatically.

# ==========================================
# RETROFIT & OKHTTP
# ==========================================
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Keep reflection-accessed members (if any) or JNI
# But generally we should let R8 shrink as much as possible.
# Keeping specific objects used in UI/Reflection
-keep class com.hoshiyomix.injecttools.Scanner { *; }
-keep class com.hoshiyomix.injecttools.Crtsh { *; }
-keep class com.hoshiyomix.injecttools.NetworkUtils { *; }

# ==========================================
# GSON LIBRARY
# ==========================================
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# Prevent Gson from being obfuscated
-keep,allowobfuscation,allowshrinking class com.google.gson.Gson
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.TypeAdapter

# ==========================================
# SECURITY ENHANCEMENTS
# ==========================================
# Remove logging in release builds (security + performance)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Obfuscate string literals containing sensitive patterns (advanced)
# Note: Be careful with this as it may break functionality
# -adaptclassstrings com.hoshiyomix.injecttools.**

# Keep sensitive methods from being removed
-keepclassmembers class com.hoshiyomix.injecttools.Scanner {
    public static *** isCloudflareIp(...);
    public static *** isPrivateIp(...);
}

# Network security - keep SSL/TLS related classes
-keep class javax.net.ssl.** { *; }
-keep class java.security.** { *; }

# ==========================================
# OKHTTP & RETROFIT (Updated for OkHttp 4.x)
# ==========================================
# OkHttp platform-specific code
-dontwarn okhttp3.internal.platform.**
-keep class okhttp3.internal.platform.Platform { *; }

# Keep OkHttp connection specs for security
-keep class okhttp3.ConnectionSpec { *; }
-keep class okhttp3.CipherSuite { *; }

# Retrofit service interfaces
-keep,allowobfuscation,allowshrinking interface com.hoshiyomix.injecttools.Crtsh$CrtShApi

# ==========================================
# COROUTINES & FLOW
# ==========================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ==========================================
# DEPENDENCY INJECTION (if added later)
# ==========================================
# Hilt/Koin rules would go here

# ==========================================
# CRASHLYTICS (if added later)
# ==========================================
# Firebase Crashlytics rules would go here
