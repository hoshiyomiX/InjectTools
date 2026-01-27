# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===== InjectTools Core Classes =====
-keep class com.hoshiyomi.injecttools.core.** { *; }
-keep class com.hoshiyomi.injecttools.ui.viewmodels.** { *; }
-keepclassmembers class com.hoshiyomi.injecttools.** { *; }

# ===== Kotlin Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ===== Kotlin Serialization =====
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.hoshiyomi.injecttools.**$$serializer { *; }
-keepclassmembers class com.hoshiyomi.injecttools.** {
    *** Companion;
}
-keepclasseswithmembers class com.hoshiyomi.injecttools.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== OkHttp =====
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ===== SSL/TLS =====
-keep class javax.net.ssl.** { *; }
-keep class java.security.** { *; }
-dontwarn javax.net.ssl.**

# ===== Android Components =====
-keep class androidx.lifecycle.** { *; }
-keep class androidx.compose.** { *; }
-keep class androidx.navigation.** { *; }

# ===== Keep all @Serializable classes =====
-keep @kotlinx.serialization.Serializable class * { *; }

# ===== Prevent stripping of used classes =====
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}
