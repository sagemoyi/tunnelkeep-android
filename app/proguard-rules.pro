# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools.

# Keep WebView JavaScript interface
-keepclassmembers class dev.moyi.tunnelkeep.** {
    @android.webkit.JavascriptInterface <methods>;
}

# Retain generic signatures for coroutines
-keepattributes Signature
-keepattributes *Annotation*

# Kotlin metadata
-keepattributes InnerClasses
-keep class kotlin.Metadata { *; }
