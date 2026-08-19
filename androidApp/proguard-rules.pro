# Compose + kotlinx.serialization keep rules.
# Minification is off by default for release builds (see build.gradle.kts);
# these rules exist so turning it on stays a one-line change.
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.mcguidesigner.**$$serializer { *; }
-keepclassmembers class com.mcguidesigner.** {
    *** Companion;
}
-keepclasseswithmembers class com.mcguidesigner.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.jetbrains.skia.**
-dontwarn org.jetbrains.skiko.**
