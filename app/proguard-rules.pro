# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ==========================================
# GSON SERIALIZATION - CRITICAL FOR HISTORY
# ==========================================
# Keep all data classes used with Gson serialization
-keep class com.hoshiyomix.injecttools.Scanner$ScanResult { *; }
-keep class com.hoshiyomix.injecttools.Crtsh$CrtShEntry { *; }
-keep class com.hoshiyomix.injecttools.MenuTile { *; }

# Keep HistoryStorage for reliable file-based persistence
-keep class com.hoshiyomix.injecttools.HistoryStorage { *; }

# Gson requires these attributes for reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Gson generic type resolution
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep all fields in data classes for Gson
-keepclassmembers class com.hoshiyomix.injecttools.Scanner$ScanResult {
    <fields>;
}
-keepclassmembers class com.hoshiyomix.injecttools.Crtsh$CrtShEntry {
    <fields>;
}

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
