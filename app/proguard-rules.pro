# ProGuard / R8 Rules for FinMoney Production Release

# Keep Room database and DAO classes
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class androidx.room.RoomDatabase { *; }
-keep class * implements com.example.data.local.FinMoneyDao { *; }

# Keep all data models, entities, and UI state classes
-keep class com.example.data.model.** { *; }
-keep class com.example.data.remote.** { *; }
-keep class com.example.model.** { *; }
-keep class com.example.util.** { *; }
-keep class com.example.ui.FinMoneyViewModel { *; }
-keep class com.example.ui.**UiState* { *; }
-keep class com.example.ui.CategorySummary { *; }

# Firebase Firestore & Auth
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.firebase.firestore.* <fields>;
}

# Moshi rules for JSON serialization/deserialization
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }

# Retrofit / OkHttp
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-keepattributes Exceptions
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Coil Compose
-dontwarn coil.**
-keep class coil.** { *; }

# Preserve line numbers for release crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
