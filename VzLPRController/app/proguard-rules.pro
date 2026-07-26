# NanoHTTPD
-keep class org.nanohttpd.** { *; }
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
# Media3
-keep class androidx.media3.** { *; }
# Room 生成代码
-keep class * extends androidx.room.RoomDatabase { *; }
