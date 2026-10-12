# خدمة لوحة المفاتيح يستدعيها النظام بالاسم من الـ Manifest
-keep class com.semo.keyboard.ime.** { *; }
-keep class com.semo.keyboard.ui.MainActivity { *; }

# مزوّد الملفات (صور الحافظة) يُنشأ بالاسم من الـ Manifest
-keep class androidx.core.content.FileProvider { *; }
# القيم المحفوظة بالإعدادات تُقرأ بالاسم (enum valueOf)
-keepclassmembers enum com.semo.keyboard.** { *; }

# commons-compress: نستعمل فك bz2 فقط؛ باقي المضغوطات الاختيارية غير موجودة
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
