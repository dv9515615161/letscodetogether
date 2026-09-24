# RideScore keeps everything local; nothing here is reflective except the
# AccessibilityService / Service entry points, which the Android framework
# instantiates by name from the manifest.
-keep class com.ridescore.app.accessibility.RideScoreAccessibilityService { *; }
-keep class com.ridescore.app.ocr.ScreenCaptureService { *; }

# ML Kit text recognition ships its own consumer rules; this keeps the
# optional-model lookup from being stripped on release builds.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
