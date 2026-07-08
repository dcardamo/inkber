# Keep Kotlin metadata and WebView-related classes.
-keep class com.dan.inkber.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}