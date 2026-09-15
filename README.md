# iOS 26 Keyboard — Android IME

لوحة مفاتيح Android مبنية بالكامل بـ **Kotlin + Jetpack Compose**، بتصميم مستوحى من لغة iOS البصرية (ألوان، انحناءات، حركة ضغط المفاتيح، دعم الوضع الداكن).

## المعمارية

المشروع مقسّم على طبقات واضحة (Clean-ish MVVM):

```
com.ios26.keyboard
├── ime/            → IOSKeyboardService: خدمة InputMethodService، تربط Compose بحقل الإدخال الفعلي
├── ui/
│   ├── MainActivity.kt      → شاشة تفعيل اللوحة والإعدادات
│   ├── theme/               → ألوان وأبعاد وأشكال بأسلوب iOS
│   └── keyboard/
│       ├── KeyboardScreen.kt        → واجهة Compose (العرض فقط، بلا منطق)
│       ├── KeyboardViewModel.kt     → حالة اللوحة (الصفحة، Shift) ومعالجة الأحداث
│       └── KeyboardViewModelFactory.kt
├── domain/
│   ├── model/KeyModels.kt           → نماذج بيانات نقية (KeyAction, KeyDefinition, حالة اللوحة)
│   └── logic/KeyboardLayoutProvider.kt → منطق بناء الصفوف حسب الصفحة (حروف/أرقام/رموز/إيموجي)
└── data/SettingsRepository.kt       → تخزين التفضيلات عبر DataStore (ثيم، صوت، اهتزاز)
```

الفصل بين `domain` (منطق بحت بلا Android) و`ui` (Compose) و`data` (تخزين) و`ime` (نقطة الدمج مع نظام Android)
يسمح باختبار منطق التخطيط ومنطق الحالة بمعزل عن أي واجهة رسومية.

## الخصائص المطبّقة

- تخطيط حروف QWERTY كامل مع Shift (مؤقت / قفل)، أرقام ورموز بصفحتين، ولوحة إيموجي.
- تصميم بصري بأسلوب iOS: مفاتيح بيضاء/رمادية بحواف دائرية ناعمة، مفتاح Enter/Shift بلون أزرق النظام، دعم كامل للوضع الداكن.
- حركة ضغط ناعمة (تصغير المفتاح عند اللمس) عبر Compose animation.
- زر تبديل لوحات (🌐) واستدعاء `switchToNextInputMethod`.
- شاشة إعدادات لتفعيل اللوحة، اختيارها كلوحة نشطة، والتحكم بالثيم والصوت والاهتزاز (محفوظة عبر DataStore وتنعكس فورًا على اللوحة).

## معالج الإعداد الأول (Onboarding)

عند أول فتح للتطبيق (`ui/onboarding/OnboardingScreen.kt` + التوجيه في `MainActivity.kt`) يمر المستخدم بأربع خطوات:

1. **ترحيب** بالتطبيق وشرح مختصر.
2. **تفعيل اللوحة** — زر يفتح `Settings.ACTION_INPUT_METHOD_SETTINGS` مباشرة، مع شارة حالة حية (مفعّلة / لسا لأ).
3. **اختيارها كلوحة نشطة** — زر يفتح منتقي لوحات المفاتيح (`InputMethodManager.showInputMethodPicker`)، مع شارة حالة حية أيضًا.
4. **تأكيد الانتهاء** — يُسجَّل هذا في `SettingsRepository` (مفتاح `onboarding_completed` عبر DataStore) حتى لا يظهر المعالج مرة ثانية تلقائيًا.

الحالة (مفعّلة/مختارة) تُفحص فعليًا من نظام أندرويد عبر `util/KeyboardStatusHelper.kt`، وتُعاد قراءتها تلقائيًا كل مرة يرجع فيها المستخدم للتطبيق من الإعدادات (بمراقبة `Lifecycle.Event.ON_RESUME`). يمكن إعادة فتح المعالج يدويًا بزر "إعادة عرض دليل الإعداد" داخل شاشة الإعدادات.

## الأذونات المطلوبة

مضافة داخل `AndroidManifest.xml`:

| العنصر | الغرض |
|---|---|
| `<uses-feature android:name="android.software.input_methods" required="true">` | يعلن رسميًا أن التطبيق يوفّر لوحة مفاتيح (مطلوب لأي IME، خصوصًا عند النشر على Play Store) |
| `android.permission.VIBRATE` | الاهتزاز الخفيف عند الضغط على مفتاح |
| `android:permission="android.permission.BIND_INPUT_METHOD"` على `<service>` | صلاحية إلزامية من النظام نفسه تمنع أي تطبيق غير النظام من ربط خدمة الـ IME (حماية أمنية قياسية، وليست صلاحية تُطلب من المستخدم) |

لا تحتاج اللوحة أي صلاحية وقت التشغيل (Runtime Permission) بحالتها الحالية لأنها لا تصل الإنترنت ولا الميكروفون ولا الملفات؛ إذا أضفت لاحقًا كتابة صوتية أو تنبؤ كلمات يحتاج شبكة، ستحتاج وقتها `INTERNET` و`RECORD_AUDIO` وطلب "Full Access" الظاهر تلقائيًا من نظام أندرويد لأي لوحة بديلة.

## البناء التلقائي عبر GitHub Actions

الملف `.github/workflows/build.yml` يبني نسخة Debug تلقائيًا عند كل `push`/`pull_request` على `main`، أو يدويًا من تبويب **Actions → Run workflow**. الخطوات:

1. تثبيت JDK 17.
2. تثبيت Gradle 8.7 عبر `gradle/actions/setup-gradle` (بدون الاعتماد على ملف `gradlew` لأن الـ wrapper jar ثنائي ولم يُولَّد في هذه البيئة).
3. `gradle assembleDebug` لبناء الـ APK.
4. رفع الـ APK الناتج كـ **Artifact** باسم `iOS26Keyboard-debug-apk` (يظهر بأسفل صفحة تشغيل الـ workflow، جاهز للتنزيل مباشرة بدون الحاجة لبناء محلي).
5. تشغيل `lintDebug` ورفع تقريره (لا يوقف البناء إذا فشل).

> إذا بدك تشغّل `./gradlew` محليًا بـ Android Studio بدل الاعتماد على الأمر `gradle` المثبت يدويًا، شغّل مرة وحدة الأمر `gradle wrapper --gradle-version 8.7` داخل مجلد المشروع (لما يكون عندك إنترنت) لتوليد `gradlew` و`gradlew.bat` و`gradle-wrapper.jar` تلقائيًا — Android Studio بيعمل هذا لحاله بأول فتح أيضًا.

## طريقة البناء يدويًا (على جهازك)

1. افتح المجلد في **Android Studio** (Hedgehog أو أحدث).
2. دع Gradle يزامن التبعيات (يتطلب اتصال إنترنت لأول مرة).
3. شغّل `Build > Build Bundle(s) / APK(s) > Build APK(s)`، أو ببساطة اضغط Run على جهاز/محاكي.
4. عند أول تشغيل ستظهر شاشة الإعداد تلقائيًا وتمشي معك خطوة بخطوة.
5. افتح أي تطبيق فيه حقل كتابة وستظهر اللوحة الجديدة.

## أفكار للتوسعة لاحقًا

- إضافة صوت نقر حقيقي (SoundPool) بدل الاهتزاز فقط.
- دعم السحب على المسافة لتحريك المؤشر (مثل iOS).
- التنبؤ بالكلمات (Predictive text) عبر طبقة `domain` جديدة.
- دعم تخطيط عربي كامل كصفحة إضافية في `KeyboardLayoutProvider`.
