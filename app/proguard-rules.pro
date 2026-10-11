# خدمة لوحة المفاتيح يستدعيها النظام بالاسم من الـ Manifest
-keep class com.semo.keyboard.ime.** { *; }
-keep class com.semo.keyboard.ui.MainActivity { *; }

# مزوّد الملفات (صور الحافظة) يُنشأ بالاسم من الـ Manifest
-keep class androidx.core.content.FileProvider { *; }
# القيم المحفوظة بالإعدادات تُقرأ بالاسم (enum valueOf)
-keepclassmembers enum com.semo.keyboard.** { *; }
