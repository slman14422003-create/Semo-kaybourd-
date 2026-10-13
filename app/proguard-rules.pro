-keep class com.semo.keyboard.ime.** { *; }
-keep class com.semo.keyboard.ui.MainActivity { *; }
-keep class androidx.core.content.FileProvider { *; }
-keepclassmembers enum com.semo.keyboard.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.jponge.**
-dontwarn org.objectweb.asm.**
-dontwarn javax.annotation.**
-dontwarn java.lang.module.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.lang3.**

-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
}
