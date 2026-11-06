# Proguard rules for TV Reader
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.luthpunk.tvreader.data.model.** { *; }
