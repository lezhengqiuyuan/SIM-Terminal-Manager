# WebView 相关保持
-keep public class * extends android.webkit.WebViewClient
-keep public class * extends android.webkit.WebChromeClient
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
