# ==========================================
# InjectTools ProGuard/R8 Configuration
# ==========================================
# Maximum optimization for smallest APK
# Last updated: 2026

# ==========================================
# GENERAL OPTIMIZATION SETTINGS
# ==========================================
-optimizationpasses 7
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Aggressive optimization
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# Keep debugging info for crash reports
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes *Annotation*

# ==========================================
# GSON SERIALIZATION - CRITICAL FOR HISTORY
# ==========================================
# Keep all data classes used with Gson serialization
-keep class com.hoshiyomix.injecttools.Scanner$ScanResult { *; }
-keep class com.hoshiyomix.injecttools.ScanSession { *; }

# TypeToken preservation
-keep class * extends com.google.gson.reflect.TypeToken {
    <init>(...);
}

# Keep HistoryStorage class
-keep class com.hoshiyomix.injecttools.HistoryStorage { *; }

# Gson library
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# ==========================================
# KOTLIN SUPPORT
# ==========================================
-keep class kotlin.Metadata { *; }
-keep class kotlin.Unit { *; }

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ==========================================
# CORE CLASSES
# ==========================================
-keep class com.hoshiyomix.injecttools.Scanner { *; }
-keep class com.hoshiyomix.injecttools.SubdomainFetcher { *; }
-keep class com.hoshiyomix.injecttools.NetworkUtils { *; }

# ==========================================
# RETROFIT & OKHTTP
# ==========================================
-dontwarn okhttp3.**
-dontwarn retrofit2.**

-keep class okhttp3.internal.platform.Platform { *; }
-keep class okhttp3.ConnectionSpec { *; }
-keep class javax.net.ssl.** { *; }
-keep class java.security.** { *; }

# ==========================================
# SECURITY ENHANCEMENTS
# ==========================================
# Remove all logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Remove System.out.print
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# ==========================================
# COROUTINES
# ==========================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ==========================================
# COMPOSE
# ==========================================
# Let Compose handle its own shrinking rules
-dontwarn androidx.compose.**
