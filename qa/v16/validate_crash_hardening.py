from pathlib import Path
R=Path(__file__).resolve().parents[2]
def read(p): return (R/p).read_text(encoding="utf-8")
reader=read("app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt")
main=read("app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt")
down=read("app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt")
handler=read("app/src/main/java/eu/kanade/tachiyomi/crash/GlobalExceptionHandler.kt")
checks={
"reader launch extras safe": 'intent.extras!!.getLong("manga"' not in reader and 'intent.getLongExtra("manga", -1L)' in reader,
"translation chapter checked": "val chapter = viewModel.state.value.viewerChapters?.currChapter ?: run {" in reader and "translateChapter(chapter, languages[selected]" in reader,
"reader mode nullable-safe": "viewModel.manga?.readingModeType!!" not in reader,
"tracking bitmap config nullable-safe": "bitmap.config!!" not in reader and "bitmap.config ?: Bitmap.Config.ARGB_8888" in reader,
"browser manga nullable-safe": "viewModel.manga!!.title" not in reader and "val mangaTitle = viewModel.manga?.title ?: return" in reader,
"submenu empty-safe": "oldSubMenu.children.first().groupId" not in main and "firstOrNull()?.groupId" in main,
"download filenames nullable-safe": 'it.name!!.endsWith(".tmp")' not in down and 'it.name!!' not in down,
"download URL nullable-safe": "page.imageUrl!!" not in down,
"missing split image recoverable": "throw Error(context.getString(R.string.download_notifier_split_page_not_found" not in down and "throw IOException(context.getString(R.string.download_notifier_split_page_not_found" in down,
"fatal handler always delegates": "finally {\n            defaultHandler.uncaughtException(thread, exception)" in handler,
}
for k,v in checks.items(): print(("PASS" if v else "FAIL")+" "+k)
if not all(checks.values()): raise SystemExit("Crash hardening QA failed")
print(f"PASS {len(checks)} crash hardening checks")
