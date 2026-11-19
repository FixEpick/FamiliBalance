# FamiliBalance — ProGuard/R8 rules

# =====================
# Kotlinx Serialization
# =====================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.familibalance.**$$serializer { *; }
-keepclassmembers class com.example.familibalance.** { *** Companion; }
-keepclasseswithmembers class com.example.familibalance.** { kotlinx.serialization.KSerializer serializer(...); }

# =====================
# Kotlin
# =====================
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.Metadata
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# R8 full mode repackages kotlin-stdlib multifile classes (StringsKt__*)
# into the default package. Package-private kotlin.text.StringsKt__IndentKt
# then becomes inaccessible to its subclass -> IllegalAccessError on startup.
-keep class kotlin.** { *; }

# =====================
# Coroutines
# =====================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-dontwarn kotlinx.coroutines.**

# =====================
# Supabase / Ktor / OkHttp
# Классы используются через рефлексию (kotlinx.serialization ищет
# $$serializer по имени, Ktor — плагины/ServiceLoader). Без keep
# R8 вырезает сериализаторы auth/postgrest/realtime-моделей и
# приложение падает с SerializationException при входе в админку.
# =====================
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.squareup.okhttp3.** { *; }
-keepattributes Signature, Exceptions, InnerClasses, EnclosingMethod
-dontwarn io.github.jan.supabase.**
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Сериализаторы Supabase/Ktor-моделей (вложенные $$serializer классы)
-keep class io.github.jan.supabase.**$$serializer { *; }
-keep class io.ktor.**$$serializer { *; }
-keep,includedescriptorclasses class io.github.jan.supabase.**$$serializer { *; }
-keep,includedescriptorclasses class io.ktor.**$$serializer { *; }

# =====================
# BuildConfig (поля SUPABASE_URL/ANON_KEY/GITHUB_REPO используются)
# =====================
-keep class com.example.familibalance.BuildConfig { *; }

# =====================
# Строки для Gson/JSON (на случай legacy)
# =====================
-keepattributes Signature
-keepattributes *Annotation*
