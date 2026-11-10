# ProGuard & R8 rules for Time Capsule 2.0 Release

# Common Java/Kotlin reflection & annotations
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod,SourceFile,LineNumberTable
-dontnote kotlinx.serialization.AnnotationsKt

# 1. kotlinx.serialization & Time Capsule DTOs
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer();
}
-keep,allowobfuscation,allowoptimization class * {
    @kotlinx.serialization.Serializable class *;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class com.timecapsule.shared.** { *; }
-keepclassmembers class com.timecapsule.shared.** { *; }
-keepnames class kotlinx.serialization.json.** { *; }

# 2. Ktor Client & OkHttp
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# 3. AndroidX Credential Manager & GoogleId
-keep class androidx.credentials.** { *; }
-keep interface androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keepclassmembers class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# 4. AndroidX DataStore Preferences
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# 5. Coil Image Loading
-keep class coil.** { *; }
-dontwarn coil.**

# 6. Jetpack Compose
-keepclassmembers class * extends androidx.compose.runtime.State { *; }
-dontwarn androidx.compose.**

# 7. Strip debug logs from release binary
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
