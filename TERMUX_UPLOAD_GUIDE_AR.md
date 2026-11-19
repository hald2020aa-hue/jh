# دليل Termux لرفع V18 إلى GitHub

هذا الدليل مخصص بعد تنزيل `jh-v18-final-ready-for-github.zip` إلى مجلد Download.

## 1) فك الحزمة فوق مجلد V18 الحالي

```bash
cd "$HOME" || exit 1
unzip -o "$HOME/storage/shared/Download/jh-v18-final-ready-for-github.zip" -d "$HOME"
cd "$HOME/jh-v18-unified-neon-all-sources-20261119" || exit 1
```

الأرشيف يحتوي على ملفات المشروع فقط ولا يحتوي على `.git`. لذلك الاستخراج فوق المجلد الموجود يستبدل ملفات المصدر المطابقة ويُبقي بيانات Git الحالية. لا تحذف المجلد ولا تنفذ `git init`.

## 2) فحص الاتصال والفرع قبل الرفع

```bash
echo "===== CURRENT BRANCH ====="
git branch --show-current

echo "===== REMOTE ====="
git remote -v

echo "===== DIFF CHECK ====="
git diff --check

echo "===== STATUS ====="
git status --short --branch
```

تأكد أن الفرع هو:
`feature/jh-v18-unified-neon-all-sources-20261119`

وتأكد أن `origin` يشير إلى مستودع GitHub الخاص بك. إذا اختلف اسم الفرع أو عنوان المستودع، توقف وأرسل صورة النتيجة قبل تنفيذ الرفع.

## 3) فحص الملف الغريب الذي ظهر في الفحص السابق

```bash
find . -maxdepth 1 -type f -name '--files --others --exclude-standard' -print
```

إذا ظهر هذا الاسم، لا تضفه إلى Git. أرسل صورة النتيجة أولًا حتى نتأكد أنه الملف العرضي نفسه.

## 4) تجهيز الرفع — لا تنفّذ هذه المرحلة إلا بعد مراجعة الحالة

بعد التأكد من الفرع و`origin` وخلو الملفات المعدة للرفع من أي أسرار:

```bash
git add -A
git diff --cached --check
git diff --cached --stat
```

راجع قائمة الملفات قبل المتابعة. لا ترفع أي ملف توقيع أو `local.properties` أو APK/AAB.

## 5) Commit وPush

بعد مراجعة الملفات المضافة فقط:

```bash
git commit -m "Finalize V18 unified neon and quality gate"
git push -u origin HEAD
```

## 6) البناء على GitHub

افتح مستودع GitHub ثم Actions ثم `Quality gate (independent fork)`. انتظر الوظيفتين:
- ARM64 Dev Debug
- Universal Dev Debug

لا تعتمد APK إلا إذا نجحت الوظيفتان، واكتمل فحص سلامة APK والتوقيع واسم الحزمة وABI. احتفظ بصورة صفحة Actions والنتائج وأرسلها للمراجعة.
