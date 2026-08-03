# Riddle's Diary ProGuard Rules

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep JSON
-keep class org.json.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
