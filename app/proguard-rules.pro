# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep data classes for GSON serialization
-keep class com.hoshiyomix.injecttools.Scanner$ScanResult { *; }
-keep class com.hoshiyomix.injecttools.Crtsh$CrtShEntry { *; }
-keep class com.hoshiyomix.injecttools.MenuTile { *; }

# --- OPTIMIZATION FIX ---
# REMOVED: -keep class androidx.compose.** { *; }
# Why: This rule forces R8 to keep ALL Compose classes, including 
# the entire material-icons-extended library (~50MB+).
# Compose has its own consumer proguard rules that handle this automatically.

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep coroutines fields
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Retrofit & OkHttp
# We don't need to keep entire retrofit package, just attributes for reflection
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Keep reflection-accessed members (if any) or JNI
# But generally we should let R8 shrink as much as possible.
# Keeping specific objects used in UI/Reflection
-keep class com.hoshiyomix.injecttools.Scanner { *; }
-keep class com.hoshiyomix.injecttools.Crtsh { *; }
-keep class com.hoshiyomix.injecttools.Logger { *; }
-keep class com.hoshiyomix.injecttools.NetworkUtils { *; }

# GSON specific
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
