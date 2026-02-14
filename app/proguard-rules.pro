# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ==========================================
# CRITICAL FIX: TypeToken Generic Type Preservation
# ==========================================
# R8 removes generic type info, causing "TypeToken must be created with a type argument"
# This keeps the anonymous TypeToken subclass with its generic signature

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

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
