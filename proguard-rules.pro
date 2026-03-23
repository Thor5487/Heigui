-dontwarn **
-ignorewarnings

-dontoptimize
-dontshrink

-keep class com.iq200.heigui.Heigui {
    *;
}

-keep class com.iq200.mixin.** {
    *;
}
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

-keepclassmembers class * {
    @com.iq200.heigui.events.core.Subscribe <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * extends java.lang.Enum {
    <fields>;
}

-keep class com.iq200.heigui.你的資料類別所在的資料夾路徑.** {
    *;
}