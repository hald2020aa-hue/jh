import eu.kanade.tachiyomi.extension.util.selectExtensionPackages
import eu.kanade.tachiyomi.data.database.models.isManhwaTagText
import eu.kanade.tachiyomi.data.database.models.isManhuaTagText
import eu.kanade.tachiyomi.ui.reader.viewer.hasAvifBrand
import eu.kanade.tachiyomi.ui.reader.viewer.isAvifContainer
import java.io.ByteArrayInputStream
import java.io.IOException
import eu.kanade.tachiyomi.extension.api.readLimitedBytes
import eu.kanade.tachiyomi.data.download.writeStoredZipEntry
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

import eu.kanade.tachiyomi.data.library.LibrarySelectionStore
import eu.kanade.tachiyomi.util.system.readAtMost
import eu.kanade.tachiyomi.ui.library.search.DuplicateCandidate
import eu.kanade.tachiyomi.ui.library.search.crossSourceDuplicateIds
import eu.kanade.tachiyomi.ui.library.search.libraryTitleContains
import eu.kanade.tachiyomi.ui.library.search.normalizeLibraryTitle
import eu.kanade.tachiyomi.ui.library.search.QueryNode
import eu.kanade.tachiyomi.ui.library.search.FieldQueryNode
import eu.kanade.tachiyomi.ui.library.search.MangaField
import eu.kanade.tachiyomi.ui.library.search.NotNode
import eu.kanade.tachiyomi.ui.library.search.AndNode
import java.nio.file.Files
import java.io.InputStream
private data class Ext(val pkg: String, val version: Int, val shared: Boolean)
private var passed = 0
private fun test(name: String, block: () -> Unit) {
    block(); println("PASS: $name"); passed++
}
private fun select(shared: List<Ext>, private: List<Ext>): List<Ext> =
    selectExtensionPackages(shared, private, { it.pkg }) { a, b ->
        when {
            a == null -> b
            b == null -> a
            a.version >= b.version -> a
            else -> b
        }
    }

fun main() {
    val shared = Ext("com.test.manga", 1, true)
    val private = Ext("com.test.manga", 2, false)
    test("private newer wins") { check(select(listOf(shared), listOf(private)).single() == private) }
    test("shared newer wins") { check(select(listOf(shared.copy(version=3)), listOf(private)).single().shared) }
    test("shared wins equal versions") { check(select(listOf(shared.copy(version=2)), listOf(private)).single().shared) }
    test("shared alone remains") { check(select(listOf(shared), emptyList()) == listOf(shared)) }
    test("private alone remains") { check(select(emptyList(), listOf(private)) == listOf(private)) }
    test("duplicate shared produces one") { check(select(listOf(shared, shared), emptyList()).size == 1) }
    test("duplicate private falls back to shared") { check(select(listOf(shared), listOf(private, private)).single() == shared) }
    test("large catalog processes materialized packages once") {
        val s = (1..250).map { Ext("pkg.$it", 1, true) }
        val p = (1..202).map { Ext("pkg.$it", 2, false) }
        var selections = 0
        val actual = selectExtensionPackages(s, p, { it.pkg }) { a,b ->
            selections++
            if (b != null && (a == null || b.version > a.version)) b else a
        }
        check(actual.size == 250 && selections == 250)
        check(actual.count { !it.shared } == 202)
    }
    test("correctly spelled manhwa") { check(isManhwaTagText("manhwa")) }
    test("misspelled Manwha case-insensitive") { check(isManhwaTagText(" Manwha ")) }
    test("Korean-prefixed label") { check(isManhwaTagText("Korean fantasy")) }
    test("Cyrillic manhwa label") { check(isManhwaTagText("манхва")) }
    test("not classifying manhua as manhwa") { check(!isManhwaTagText("manhua")) }
    test("not classifying manga as manhwa") { check(!isManhwaTagText("manga")) }
    test("long strip old behavior") { check(isManhwaTagText("long strip")) }
    test("empty lists safe with drop") { check(emptyList<Int>().drop(1).isEmpty()) }
    test("one item leaves empty tail") { check(listOf(7).drop(1).isEmpty()) }
    test("all except first kept") { check(listOf(7,8,9).drop(1) == listOf(8,9)) }
    test("traditional manhua tag") { check(isManhuaTagText("manhua")) }
    test("Chinese tags case-insensitive") { check(isManhuaTagText("ZH-Hans") && isManhuaTagText("ZH-hant")) }
    test("Chinese language genre") { check(isManhuaTagText("Chinese original")) }
    test("manhwa is not manhua") { check(!isManhuaTagText("Manwha")) }
    fun ftyp(major: String, minor: String = "0000", compatible: String = "mif1"): ByteArray =
        byteArrayOf(0, 0, 0, 24) + "ftyp".toByteArray() + major.toByteArray() + minor.toByteArray() + compatible.toByteArray()
    test("AVIF major brand detected") { check(hasAvifBrand(ftyp("avif"))) }
    test("animated AVIS brand detected") { check(hasAvifBrand(ftyp("avis"))) }
    test("AVIF compatible brand detected") { check(hasAvifBrand(ftyp("mif1", compatible="avif"))) }
    test("JPEG not classified as AVIF") { check(!hasAvifBrand(byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0))) }
    test("HEIF not classified as AVIF") { check(!hasAvifBrand(ftyp("heic", compatible="mif1"))) }
    test("truncated header is rejected") { check(!hasAvifBrand(ftyp("avif"), 6)) }
    test("stream sniff does not consume bytes") {
        val bytes = ftyp("avif")
        val stream = ByteArrayInputStream(bytes)
        check(isAvifContainer(stream) && stream.read() == 0)
    }
    test("non-AVIF stream retains position") {
        val bytes = ftyp("heic")
        val stream = ByteArrayInputStream(bytes)
        check(!isAvifContainer(stream) && stream.read() == 0)
    }
    test("huge catalog selections are linear in package count") {
        val s = (1..4000).map { Ext("pkg.$it",1,true) }
        val p = (1..4000).map { Ext("pkg.$it",2,false) }
        var calls = 0
        val result = selectExtensionPackages(s, p, {it.pkg}) { left, right ->
            calls++
            if(right != null && (left == null || right.version > left.version)) right else left
        }
        check(calls == 4000 && result.size == 4000)
    }
    test("bounded index read exact limit") { check(ByteArrayInputStream(ByteArray(1024)).readLimitedBytes(1024).size == 1024) }
    test("bounded index read refuses oversized body") {
        var rejected = false
        try { ByteArrayInputStream(ByteArray(1025)).readLimitedBytes(1024) }
        catch (_: IOException) { rejected = true }
        check(rejected)
    }
    test("bounded index rejects invalid limit") {
        var rejected = false
        try { ByteArrayInputStream(byteArrayOf()).readLimitedBytes(0) }
        catch (_: IllegalArgumentException) { rejected = true }
        check(rejected)
    }
    test("streaming CBZ entry CRC and size are correct") {
        val original = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            writeStoredZipEntry(zip, "001.png") { ByteArrayInputStream(original) }
        }
        ZipInputStream(ByteArrayInputStream(archive.toByteArray())).use { input ->
            check(input.nextEntry.name == "001.png")
            check(input.readBytes().contentEquals(original))
            check(input.nextEntry == null)
        }
    }
    test("CBZ streaming rejects empty pages") {
        var failed = false
        try {
            ZipOutputStream(ByteArrayOutputStream()).use { zip ->
                writeStoredZipEntry(zip, "001.jpg") { ByteArrayInputStream(byteArrayOf()) }
            }
        } catch (_: IOException) { failed = true }
        check(failed)
    }
    test("multiple zip entries can be read") {
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            writeStoredZipEntry(zip, "001.jpg") { ByteArrayInputStream(byteArrayOf(1,2,3)) }
            writeStoredZipEntry(zip, "ComicInfo.xml") { ByteArrayInputStream("<ComicInfo/>".toByteArray()) }
        }
        ZipInputStream(ByteArrayInputStream(archive.toByteArray())).use { input ->
            check(input.nextEntry.name == "001.jpg")
            check(input.readBytes().contentEquals(byteArrayOf(1,2,3)))
            check(input.nextEntry.name == "ComicInfo.xml")
            check(input.readBytes().decodeToString() == "<ComicInfo/>")
        }
    }
    // Release 1: durable library selections and bounded restoration.
    val selectionDir = Files.createTempDirectory("j2k-selection-tests").toFile()
    try {
        val store = LibrarySelectionStore(selectionDir)
        test("selection survives creating a second store instance") {
            val filename = store.save((1L..10_000L).toList())
            val restored = LibrarySelectionStore(selectionDir).loadAndDelete(filename)
            check(restored.size == 10_000 && restored.first() == 1L && restored.last() == 10_000L)
        }
        test("selection remains available for worker retry until explicit cleanup") {
            val name = store.save(listOf(100L, 200L))
            check(store.load(name) == listOf(100L, 200L))
            check(LibrarySelectionStore(selectionDir).load(name) == listOf(100L, 200L))
            check(store.delete(name))
            check(runCatching { store.load(name) }.isFailure)
        }
        test("selection reads once only") {
            val name = store.save(listOf(1L))
            check(store.loadAndDelete(name) == listOf(1L))
            check(runCatching { store.loadAndDelete(name) }.isFailure)
        }
        test("selection deduplicates IDs") {
            val name = store.save(listOf(9L, 9L, 10L))
            check(store.loadAndDelete(name) == listOf(9L, 10L))
        }
        test("selection accepts signed IDs") {
            val name = store.save(listOf(-5L, 0L, 2L))
            check(store.loadAndDelete(name) == listOf(-5L, 0L, 2L))
        }
        test("selection rejects traversal") {
            check(runCatching { store.loadAndDelete("../../my-data") }.isFailure)
        }
        test("selection rejects empty list") {
            check(runCatching { store.save(emptyList()) }.isFailure)
        }
        test("selection rejects too many ids") {
            check(runCatching { store.save((1L..50_001L).toList()) }.isFailure)
        }
        test("selection corrupt content does not return unrestricted library") {
            val name = store.save(listOf(2L))
            java.io.File(selectionDir, name).writeText("not-an-id\n")
            check(runCatching { store.loadAndDelete(name) }.isFailure)
            check(!java.io.File(selectionDir, name).exists())
        }
        test("stale selection cleaned without deleting fresh one") {
            val old = store.save(listOf(1L))
            val new = store.save(listOf(2L))
            val now = System.currentTimeMillis()
            check(java.io.File(selectionDir, old).setLastModified(now - 40L * 24 * 60 * 60 * 1000))
            store.deleteStale(now)
            check(!java.io.File(selectionDir, old).exists() && java.io.File(selectionDir, new).exists())
            check(store.loadAndDelete(new) == listOf(2L))
        }
    } finally {
        selectionDir.deleteRecursively()
    }
    test("bounded backup read accepts precise limit") {
        check(ByteArrayInputStream(ByteArray(8192)).readAtMost(8192).size == 8192)
    }
    test("bounded backup read rejects oversize") {
        check(runCatching { ByteArrayInputStream(ByteArray(8193)).readAtMost(8192) }.isFailure)
    }
    test("bounded backup read accepts empty") {
        check(ByteArrayInputStream(byteArrayOf()).readAtMost(8192).isEmpty())
    }
    test("bounded backup read rejects zero maximum") {
        check(runCatching { ByteArrayInputStream(byteArrayOf()).readAtMost(0) }.isFailure)
    }
    test("bounded backup read handles zero-length intermediate reads") {
        val input = object : InputStream() {
            private val delegate = ByteArrayInputStream(byteArrayOf(1,2,3))
            private var zero = true
            override fun read(): Int = delegate.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                if (zero) { zero = false; 0 } else delegate.read(b, off, len)
        }
        check(input.readAtMost(3).contentEquals(byteArrayOf(1,2,3)))
    }
    // Release 3: normalized, user-controlled, cross-source duplicate search.
    test("library title normalization handles Latin punctuation") {
        check(normalizeLibraryTitle("ONE-PIECE") == normalizeLibraryTitle("One Piece"))
    }
    test("library title normalization handles Arabic diacritics and hamza") {
        check(normalizeLibraryTitle("أَبْطَالُ") == normalizeLibraryTitle("ابطال"))
    }
    test("library title search remains case and diacritic insensitive") {
        check(libraryTitleContains("أبطال الساحة", "ابطال") && libraryTitleContains("One-Piece", "one piece"))
    }
    test("library title search does not match unrelated terms") {
        check(!libraryTitleContains("One Piece", "Naruto"))
    }
    test("duplicate finder finds entries from separate source IDs") {
        val set = crossSourceDuplicateIds(listOf(DuplicateCandidate(1,"ONE-PIECE",1), DuplicateCandidate(2,"One Piece",2)))
        check(set == setOf(1L,2L))
    }
    test("duplicate finder ignores same source repeated across categories") {
        val set = crossSourceDuplicateIds(listOf(DuplicateCandidate(1,"Manga",1), DuplicateCandidate(1,"Manga",1), DuplicateCandidate(2,"Manga",1)))
        check(set.isEmpty())
    }
    test("duplicate finder ignores empty titles") {
        val set = crossSourceDuplicateIds(listOf(DuplicateCandidate(1," ",1), DuplicateCandidate(2,"---",2)))
        check(set.isEmpty())
    }
    test("duplicate finder does not merge distinct manga") {
        val set = crossSourceDuplicateIds(listOf(DuplicateCandidate(1,"One Piece",1), DuplicateCandidate(2,"One Punch Man",2)))
        check(set.isEmpty())
    }
    test("duplicate finder handles 4000 records and 200 cross-source titles") {
        val items = (1..4000).map { DuplicateCandidate(it.toLong(), "Story $it", 1L) } +
            (1..200).map { DuplicateCandidate((5000+it).toLong(), "Story $it", 2L) }
        val ids = crossSourceDuplicateIds(items)
        check(ids.size == 400 && 5001L in ids && 1L in ids && 201L !in ids)
    }
    test("search parser recognizes duplicate yes/no field") {
        val node = QueryNode.from("dup:yes")
        check(node is FieldQueryNode && node.field == MangaField.DUPLICATE && node.value == "yes")
    }
    test("search parser recognizes negated duplicate filter") {
        val node = QueryNode.from("-dup:yes")
        check(node is FieldQueryNode && node.negated)
    }
    test("search parser combines title and duplicate field") {
        val node = QueryNode.from("title:one dup:no")
        check(node is AndNode && node.children.size == 2)
    }
    println("REGRESSION TESTS: $passed / $passed PASSED")
}
